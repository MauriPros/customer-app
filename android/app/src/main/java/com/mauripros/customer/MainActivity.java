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
import org.json.JSONObject;

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
        private String pendingWhy = null;
        private String pendingUrl = null;

        AppWebViewClient(Bridge bridge) {
            super(bridge);
            this.appBridge = bridge;
        }

        /** The app's own offline page (or anything served by the app itself). */
        private boolean isAppPage(String url) {
            String page = appBridge.getErrorUrl();
            if (url == null) {
                return false;
            }
            if (page != null && url.startsWith(page)) {
                return true;
            }
            Uri u = Uri.parse(url);
            return "localhost".equalsIgnoreCase(u.getHost());
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
            String url = String.valueOf(request.getUrl());
            if (isAppPage(url)) {
                // Never loop: the offline page itself failing does not open it again.
                Log.w(TAG, "offline page failed: " + url);
                return;
            }
            String why = error != null ? ("net " + error.getErrorCode() + " " + error.getDescription()) : "net";
            showOffline(view, why, url);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel(); // Never continue on a bad certificate.
            String url = error != null ? error.getUrl() : "";
            if (isAppPage(url)) {
                return;
            }
            showOffline(view, "ssl " + (error != null ? error.getPrimaryError() : ""), url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // Tell the offline page why and where to go back (it is loaded at its exact address).
            if (pendingWhy != null && isAppPage(url)) {
                String js = "window.mprOffline && window.mprOffline(" + JSONObject.quote(pendingWhy) + "," + JSONObject.quote(pendingUrl == null ? "" : pendingUrl) + ");";
                view.evaluateJavascript(js, null);
                pendingWhy = null;
                pendingUrl = null;
            }
        }

        private void showOffline(WebView view, String why, String url) {
            String page = appBridge.getErrorUrl();
            if (page == null) {
                return;
            }
            pendingWhy = why;
            pendingUrl = (url != null && url.startsWith("https://") && !isAppPage(url)) ? url : null;
            // Exactly the error address: Capacitor serves it from the app only on an exact match.
            view.loadUrl(page);
        }
    }
}
