# MauriPros customer app — setup (Android)

One app template for every restaurant. Each restaurant gets its own app in Google Play
(own name, icon, colors, Firebase app), all built from this project.

The app shows the restaurant's MauriPros website (menu, checkout, Orders, Rewards, Account)
and receives **order updates** (accepted → preparing → ready → delivered).

## 1. Put the project on GitHub (once)
1. Unzip `mauripros-customer-app-1.0.0.zip` into `Downloads\mauripros-customer-app`.
2. GitHub Desktop → **File → Add local repository** → choose that folder → it says "not a git
   repository" → **create a repository** → **Create repository**.
3. **Publish repository** → Organization **MauriPros**, name `customer-app`, keep **private** ticked.

## 2. Codemagic (once)
1. codemagic.io → **Add application** → GitHub → `MauriPros/customer-app` → Flutter/other: choose
   **Other** → it finds `codemagic.yaml`.
2. Android signing: the app uses the same upload keystore as the Owner app, reference
   name `mauripros_upload` (Team settings → codemagic.yaml settings → Code signing identities →
   Android keystores). If it was added only to the Owner app, add the same `.jks` again here with
   the same name (passwords: KEYSTORE-DETAILS.txt).

## 3. Test build (APK)
Codemagic → customer-app → **Start new build** → workflow **Android — test APK** → Start.
After ~10 minutes download `burgertogo-1.0.0-N.apk` from the build page, send it to an Android
phone and install it (allow "install unknown apps").

## 4. Order updates (once)
1. MauriPros Hub → **Settings → Customer apps (order updates)** → paste the whole Firebase
   service-account JSON file of the customer apps' project → Save.
2. Restaurant website: MauriPros Restaurant Suite **1.31.0** or newer; Hub **1.24.0** or newer.

## 5. Try it
Open the app → order something → on the confirmation screen tap **Allow** for notifications →
accept the order in ProsOrders / the portal → the phone gets "Your order #… was accepted."

## Add another restaurant
1. Firebase (customer apps' project) → Add app → Android → package `com.mauripros.<name>` →
   download `google-services.json`.
2. Make a folder `restaurants/<name>/` with `config.json` (copy Burger To Go's and change it),
   `google-services.json`, `icon.png` (1024×1024 store icon) and `logo.png` (wide logo, optional).
3. Make the icons: `python3 tools/make-assets.py <name>` (Claude does this for you).
4. Codemagic → Start new build → change `RESTAURANT` to `<name>`.

## Files
- `restaurants/<name>/config.json` — appId, name, url (the menu page), brand, background, statusBar (dark|light), version
- `scripts/set-restaurant.js` — turns the template into one restaurant's app (run by Codemagic)
- `tools/make-assets.py` — icons + launch screen from icon.png / logo.png
- `android/app/src/main/java/com/mauripros/customer/MainActivity.java` — order updates channel; website error pages stay on screen (offline screen only when there is no internet)
- `www/offline.html` — "You are offline / Try again" (English / Arabic)
