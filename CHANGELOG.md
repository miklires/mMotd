# Changelog

## 1.1.0 - 2026-09-01

### Added

- Weighted profiles for hostname wildcards, protocol ranges, and first/returning ping audiences.
- Priority schedules, whitelist MOTD, event countdown placeholders, and configurable time zones.
- Optional privacy-first in-memory ping-to-join analytics with `/mmotd stats`.
- Atomic reload reports, bounded configuration, cached stable hover identities, and complete English/Russian messages.

### Fixed

- Rejected icon paths and symlinks that escape the plugin data directory.
- Prevented invalid MiniMessage or configuration from replacing the working ping cache.
- Removed recursive placeholder expansion and reduced expensive offline-player scans.
- Added a global fallback requirement and validation for duplicate IDs, missing schedule targets, host patterns, protocols, weights, and IP addresses.

## 1.0.0 - 2026-08-26

### Added

- Cached MiniMessage MOTDs with placeholders, random selection, and schedules.
- Maintenance mode with permission and IP bypasses.
- Player-count, version-label, hover-sample, and favicon controls.
- English and Russian messages, bStats metrics, and update checks.
