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

## Features (v0.3)

**Protection**
- One-tap protection on/off, plus a Quick Settings tile
- Blocks trackers (built in), ads (~78,000 domains), scam/phishing/malware sites and,
  optionally, gambling sites. The lists update daily.
- Spots fake bKash / Nagad pages and warns you with a notification
- "Pause for 10 minutes" for banking apps; protection comes back by itself
- Pause protection for a single app; allow a single site if blocking it broke something
- bKash, Nagad and Rocket are left out by default (they refuse to run with a VPN on)

**Awareness**
- Today and last-7-days counts, with an estimate of mobile data saved
- For each app: which companies it tried to send data to, and why
- A–F privacy grade per app (tracker companies + sensitive permissions)
- Weekly report notification every Friday evening, and a "Share my week" image

**Safety**
- App lock: fingerprint/PIN before protection can be turned off or apps paused
- Alert and history entry if protection is turned off from phone settings
- Guide to Android's Always-on VPN

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

## Releases (signed APK)

Every published GitHub release gets a signed APK attached automatically
(`.github/workflows/release.yml`).

One-time setup, in **Settings → Secrets and variables → Actions**, add:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the release keystore, base64-encoded |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `unseen` |
| `KEY_PASSWORD` | key password |

To release: **Releases → Draft a new release →** new tag like `v0.3.3` → **Publish**.
After a few minutes `Unseen-v0.3.3.apk` appears on the release page.
The key itself is never stored in this repository.

## Privacy

Unseen has no analytics, no ads and no servers. It only downloads the public block
lists once a day; nothing about you or your apps is ever sent.

## Lists

- Trackers: `app/src/main/assets/blocklist.txt` (one domain per line; subdomains blocked too)
- Ads and gambling: [StevenBlack/hosts](https://github.com/StevenBlack/hosts) (MIT)
- Phishing and malware: [malware-filter](https://gitlab.com/malware-filter) by curben (CC BY-SA 4.0),
  built from OpenPhish, PhishTank and URLhaus data. OpenPhish's community feed is for
  non-commercial use: check this before charging money for the app.
