# Changelog

All notable changes to PrivacyDroid are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Full tracker connections screen — open the dashboard tracker card to see every
  app → domain connection with category, count, last seen, DNS verification and
  data volume, plus filters (All/Ads/Tracker/CDN, Today/Week/Month), search and
  per-domain detail popups.
- Blocking statistics detail screen — Blocked / Allowed tabs with infinite scroll
  (Paging 3), search, top-blocked-domains summary, "why was this blocked?"
  explanations and an in-place "Allow" action that adds an app exception.
- Report engine (Phase 3):
  - Shareable per-app text report and clipboard copy.
  - On-device PDF export via Android's built-in `PdfDocument` (no third-party
    libraries), saved to `Documents/PrivacyDroid/` via MediaStore.
  - Auto-filled GDPR/KVKK request template per app.
  - Device-wide privacy report (app risk distribution, tracker stats,
    recommendations) shareable as text or PDF.

### Changed
- Dashboard tracker card and blocking-statistics card are now tappable and open
  their respective detail screens.

## [0.1.0-alpha] - 2026-06-07

### Added
- Permission monitor — tracks camera, microphone, location, contacts and other
  sensitive permission accesses via AppOpsManager (no root).
- Timestamped access log with background/night highlighting.
- Real-time notifications for suspicious background or night-time sensor access.
- Per-app privacy score with a risk breakdown (background, night, diversity,
  frequency).
- App inventory with critical-permission filtering and embedded-SDK detection.
- Local VPN DNS capture for tracker detection — no traffic leaves the device.
- DNS-based ad & tracker blocking with Off / Balanced / Aggressive modes and
  per-app exceptions.
- In-app notification center and crash-log viewer.
- Fully on-device storage (Room); no analytics, crash reporting, or cloud sync.

[Unreleased]: https://github.com/your-org/privacydroid/compare/v0.1.0-alpha...HEAD
[0.1.0-alpha]: https://github.com/your-org/privacydroid/releases/tag/v0.1.0-alpha
