# PrivacyDroid

> See what your phone does in the background. Know who accesses your camera,
> microphone and location — and when. Prove it, report it, decide.

**If you can't stop the surveillance, make it visible.** When users know what is
happening, they can make informed decisions.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green.svg)
![Status](https://img.shields.io/badge/status-alpha-orange.svg)

---

## Why PrivacyDroid?

Why does a flashlight app need your microphone? Why does a banking app query your
location at 3 AM? Which app is quietly sending data to a tracking server?

PrivacyDroid answers these questions — entirely on-device, with no cloud, no
analytics, and no data ever leaving your phone.

---

## Features

- 📱 **Permission monitor** — see which apps use the camera, microphone, location,
  contacts and more, with timestamps ("who accessed it at 3 AM?").
- 🔔 **Real-time alerts** — get notified when an app touches a sensor in the
  background or at night.
- 📊 **Per-app privacy score** — a risk breakdown based on background access,
  night activity, permission diversity and frequency.
- 🌐 **Tracker detection** — a local VPN captures DNS queries (no traffic leaves
  the device) to reveal connections to known tracker domains.
- 🚫 **Ad & tracker blocking** — DNS-based blocking with per-app exceptions and
  Off / Balanced / Aggressive modes.
- 🛡️ **Blocking statistics** — full lists of blocked and allowed domains with
  search and infinite scroll, plus "why was this blocked?" explanations.
- 📄 **Reports** — shareable text reports, on-device PDF export (no third-party
  libraries) and an auto-generated GDPR/KVKK request template.

---

## Screenshots

> _Screenshots coming soon._

| Dashboard | App detail | Blocking stats | Report |
|-----------|-----------|----------------|--------|
| _(placeholder)_ | _(placeholder)_ | _(placeholder)_ | _(placeholder)_ |

---

## Installation

### Requirements
- Android 8.0 (API 26) or newer
- **No root required**

### F-Droid (recommended)
```
Coming soon
```

### Direct APK
1. Download the APK from the Releases page.
2. Settings → Security → enable installs from unknown sources.
3. Install the APK.

---

## Build from source

```bash
# Debug build
./gradlew assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk

# Run unit tests
./gradlew test

# Release build
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK (compileSdk 35).

---

## Architecture

- **Language:** Kotlin
- **UI:** Jetpack Compose + Navigation Compose
- **Pattern:** MVVM + Clean Architecture
- **DI:** Hilt
- **Persistence:** Room (everything stays on-device)
- **Async:** Coroutines + Flow
- **Background:** WorkManager

---

## Permissions

| Permission | Why |
|------------|-----|
| `PACKAGE_USAGE_STATS` | Read other apps' permission usage via AppOpsManager |
| `POST_NOTIFICATIONS` | Suspicious-activity alerts |
| `FOREGROUND_SERVICE` | Background monitoring service |
| `INTERNET` | **Only** so the local VPN can forward DNS queries to a real resolver |
| `BIND_VPN_SERVICE` | Local DNS-capture VPN for tracker detection |
| `QUERY_ALL_PACKAGES` | Inventory installed apps for privacy analysis |

**PrivacyDroid never sends any data off the device.** All logs are stored locally.

---

## Privacy policy

- ❌ No analytics
- ❌ No crash reporting
- ❌ No cloud sync
- ❌ No ads
- ✅ All data stays on your device
- ✅ Open source — every line is auditable

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for the
workflow, coding standards and pull-request process.

---

## License

PrivacyDroid is free software licensed under the **GNU General Public License
v3.0** — see [LICENSE](LICENSE). You may copy, modify and distribute it, provided
derivative works are also licensed under GPL-3.0.
