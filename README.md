# Unseen

**Apps can't watch what they can't see.**

Unseen blocks the trackers hidden inside your Android apps — with one tap, and
without turning off any app permissions, so your apps keep working.

## How it works

Unseen runs a small VPN **on your phone only**. It does not send your traffic
to any server. It only looks at DNS lookups (the "address book" requests apps
make before they connect):

- Request for a known tracker (for example `app-measurement.com`, `appsflyer.com`)
  → answered with `0.0.0.0`, so the app can't reach it.
- Anything else → passed to a normal DNS server (1.1.1.1, then 8.8.8.8).

All other internet traffic goes out directly, as usual.

## Features (v0.1)

- One-tap protection on/off
- "Trackers blocked today" counter
- Which apps tried to reach trackers the most
- Pause protection for a single app if it stops working
- Turns itself back on after a reboot

## Known limits

- If **Private DNS** is set to a specific provider in Android settings, Unseen
  can't see DNS. The app shows a warning; set Private DNS to *Automatic* or *Off*.
- Only one VPN can run at a time on Android.
- Apps that use their own encrypted DNS can bypass DNS blocking.
- Per-app counts reset when the app process restarts.

## Build

Open the folder in Android Studio and press Run, or:

```
./gradlew assembleDebug
```

The APK is in `app/build/outputs/apk/debug/`. Every push to `main` also builds
the APK on GitHub Actions (see the **Actions** tab → latest run → *Artifacts*).

Requires Android 10 or newer.

## Privacy

Unseen has no analytics, no ads and no servers. Nothing leaves your phone.

## Blocklist

`app/src/main/assets/blocklist.txt` — one domain per line; subdomains are
blocked too.
