package com.mauripros.customer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

/**
 * MauriPros customer app (one template for every restaurant).
 *
 * - "Order updates" notification channel (the Hub sends to channel_id "order_updates").
 * - A page answer from the website (404 "Page not found", 429, 500 …) shows the
 *   website's own page. Capacitor 8 would show the offline screen for ANY server
 *   error; only real network / certificate problems show it here (same fix as
 *   the MauriPros Owner app, lessons.md 2026-11-17).
 */
public class MainActivity extends BridgeActivity {

    private static final String TAG = "MauriPros";
    public static final String CHANNEL_ORDERS = "order_updates";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Before the first page loads: a "wp_" cookie makes the restaurant's hosting
        // (WordPress.com page cache) send fresh pages, so the app always runs the
        // newest website code (order updates etc.), never an old cached copy.
        try {
            String site = getString(R.string.site_url);
            if (site != null && site.startsWith("https://")) {
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setCookie(site, "wp_mpr_app=1; Path=/; Max-Age=31536000; Secure; SameSite=Lax");
                cm.flush();
            }
        } catch (Exception e) {
            Log.w(TAG, "cookie: " + e.getMessage());
        }
        super.onCreate(savedInstanceState);
        createChannels();
        Bridge b = getBridge();
        if (b != null) {
            b.setWebViewClient(new AppWebViewClient(b));
        }
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ORDERS,
            getString(R.string.channel_orders),
            NotificationManager.IMPORTANCE_HIGH
        );
        ch.setDescription(getString(R.string.channel_orders_text));
        nm.createNotificationChannel(ch);
    }

    static class AppWebViewClient extends BridgeWebViewClient {

        private final Bridge appBridge;

        AppWebViewClient(Bridge bridge) {
            super(bridge);
            this.appBridge = bridge;
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            // Log only: the website's own error page stays on screen.
            if (request != null && request.isForMainFrame() && errorResponse != null) {
                Log.w(TAG, "HTTP " + errorResponse.getStatusCode() + " " + request.getUrl());
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request == null || !request.isForMainFrame()) {
                return;
            }
            String why = error != null ? ("net " + error.getErrorCode() + " " + error.getDescription()) : "net";
            showOffline(view, why, String.valueOf(request.getUrl()));
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel(); // Never continue on a bad certificate.
            showOffline(view, "ssl " + (error != null ? error.getPrimaryError() : ""), error != null ? error.getUrl() : "");
        }

        private void showOffline(WebView view, String why, String url) {
            String page = appBridge.getErrorUrl();
            if (page == null) {
                return;
            }
            view.loadUrl(page + "#why=" + Uri.encode(why) + "&url=" + Uri.encode(url));
        }
    }
}
