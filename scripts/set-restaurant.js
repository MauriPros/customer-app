// Usage: node scripts/set-restaurant.js burgertogo
// Turns the template into one restaurant's app (run before `npx cap sync android`):
//   restaurants/<slug>/config.json   app id, name, website (menu) address, colors, version
//   restaurants/<slug>/google-services.json   Firebase (Android app of this restaurant)
//   restaurants/<slug>/res/          icons + launch screen (python3 tools/make-assets.py <slug>)
const fs = require('fs');
const path = require('path');

const slug = process.argv[2] || process.env.RESTAURANT;
const root = path.join(__dirname, '..');
const dir = path.join(root, 'restaurants', String(slug || ''));
if (!slug || !/^[a-z0-9-]+$/.test(slug) || !fs.existsSync(path.join(dir, 'config.json'))) {
  console.error('Give a restaurant folder name, e.g. node scripts/set-restaurant.js burgertogo');
  process.exit(1);
}
const r = JSON.parse(fs.readFileSync(path.join(dir, 'config.json'), 'utf8'));
const must = ['appId', 'name', 'url', 'brand', 'background'];
for (const k of must) {
  if (!r[k]) { console.error('config.json needs "' + k + '"'); process.exit(1); }
}
if (!/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/.test(r.appId)) { console.error('appId looks wrong: ' + r.appId); process.exit(1); }
if (!/^https:\/\/[a-z0-9.-]+(\/.*)?$/i.test(r.url)) { console.error('url must be https: ' + r.url); process.exit(1); }
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/'/g, "\\'").replace(/"/g, '\\"');
const host = new URL(r.url).host;
const dark = r.statusBar !== 'light';

// 1) Capacitor config
const capFile = path.join(root, 'capacitor.config.json');
const cap = JSON.parse(fs.readFileSync(capFile, 'utf8'));
cap.appId = r.appId;
cap.appName = r.name;
cap.server.url = r.url;
cap.server.allowNavigation = [host, '*.stripe.com', '*.stripe.network'];
// WebView background = restaurant color: no white flash between pages.
cap.backgroundColor = r.background;
cap.android = Object.assign({}, cap.android, { backgroundColor: r.background });
cap.plugins.SplashScreen.backgroundColor = r.background;
cap.plugins.StatusBar.backgroundColor = r.background;
cap.plugins.StatusBar.style = dark ? 'DARK' : 'LIGHT';
fs.writeFileSync(capFile, JSON.stringify(cap, null, 2) + '\n');

// 2) Android texts and colors
const res = path.join(root, 'android', 'app', 'src', 'main', 'res');
fs.writeFileSync(path.join(res, 'values', 'strings.xml'),
`<?xml version='1.0' encoding='utf-8'?>
<!-- Written by scripts/set-restaurant.js (${slug}). -->
<resources>
    <string name="app_name">${esc(r.shortName || r.name)}</string>
    <string name="title_activity_main">${esc(r.name)}</string>
    <string name="package_name">${r.appId}</string>
    <string name="custom_url_scheme">${r.appId}</string>
    <string name="site_url">${new URL(r.url).origin}/</string>
    <string name="channel_orders">${esc(r.channelName || 'Order updates')}</string>
    <string name="channel_orders_text">${esc(r.channelText || 'When your order is accepted, ready or on its way')}</string>
</resources>
`);
fs.writeFileSync(path.join(res, 'values', 'colors.xml'),
`<?xml version="1.0" encoding="utf-8"?>
<!-- Written by scripts/set-restaurant.js (${slug}). -->
<resources>
    <color name="notification_color">${r.brand}</color>
    <color name="splash_background">${r.background}</color>
</resources>
`);
fs.writeFileSync(path.join(res, 'values', 'ic_launcher_background.xml'),
`<?xml version="1.0" encoding="utf-8"?>
<!-- Written by scripts/set-restaurant.js (${slug}). -->
<resources>
    <color name="ic_launcher_background">${r.background}</color>
</resources>
`);

// 3) Icons and launch screen
const copyDir = (from, to) => {
  for (const e of fs.readdirSync(from, { withFileTypes: true })) {
    const a = path.join(from, e.name), b = path.join(to, e.name);
    if (e.isDirectory()) { fs.mkdirSync(b, { recursive: true }); copyDir(a, b); } else { fs.copyFileSync(a, b); }
  }
};
if (!fs.existsSync(path.join(dir, 'res'))) { console.error('Missing restaurants/' + slug + '/res — run: python3 tools/make-assets.py ' + slug); process.exit(1); }
copyDir(path.join(dir, 'res'), res);

// 4) Firebase (Android)
const gs = path.join(dir, 'google-services.json');
if (fs.existsSync(gs)) {
  const g = JSON.parse(fs.readFileSync(gs, 'utf8'));
  const ok = (g.client || []).some((c) => c.client_info && c.client_info.android_client_info && c.client_info.android_client_info.package_name === r.appId);
  if (!ok) { console.error('google-services.json has no Android app "' + r.appId + '" — add it in Firebase and download the file again.'); process.exit(1); }
  fs.copyFileSync(gs, path.join(root, 'android', 'app', 'google-services.json'));
} else {
  console.warn('No google-services.json: the app works, but without order updates.');
}

// 5) Offline page
const off = path.join(root, 'www', 'offline.html');
let html = fs.readFileSync(off, 'utf8');
html = html.replace(/data-url="[^"]*"/, `data-url="${r.url}"`).replace(/data-name="[^"]*"/, `data-name="${esc(r.name)}"`)
  .replace(/--brand:[^;]*;/, `--brand:${r.brand};`).replace(/--bg:[^;]*;/, `--bg:${r.background};`)
  .replace(/<title>[^<]*<\/title>/, `<title>${esc(r.name)}</title>`);
fs.writeFileSync(off, html);

console.log(`Ready: ${r.name} (${r.appId}) → ${r.url}`);
