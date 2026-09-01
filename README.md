<div align="center">
  <h1>mMotd</h1>
  <p>Fast, cached server-list profiles with schedules, maintenance controls, and privacy-first analytics.</p>
  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Folia" height="56" src="https://raw.githubusercontent.com/miklires/mCommand/main/docs/assets/folia-available.png"></a>
  </p>
  <p>
    <a href="https://github.com/miklires/mMotd"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
    <a href="https://modrinth.com/plugin/mmotd"><img alt="Modrinth" src="https://tr7zw.github.io/uikit/social_buttons_icon/Modrinth-Button-64.png"></a>
    <a href="https://discord.gg/pes25cnWKy"><img alt="Discord" src="https://tr7zw.github.io/uikit/social_buttons_icon/Discord-Button-64.png"></a>
  </p>
  <p>
    <a href="https://bstats.org/plugin/bukkit/mMotd/33358"><img alt="bStats" src="https://img.shields.io/badge/bStats-33358-2F9BE6?style=for-the-badge"></a>
    <a href="https://github.com/miklires/mMotd/releases"><img alt="Release" src="https://img.shields.io/github/v/release/miklires/mMotd?style=for-the-badge"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

## Features

- Two-line MiniMessage MOTDs with gradients and a one-second immutable render cache.
- Weighted random entries and priority-based weekday/time schedules, including overnight windows.
- Profiles for exact or wildcard hostnames, client protocol ranges, first-time pings, and returning pings.
- Maintenance MOTD, kick message, permission bypass, exact IP whitelist, and persistent on/off command.
- Automatic whitelist MOTD while the built-in server whitelist is enabled.
- Local 64×64 favicons per profile, with path and symlink escape protection.
- Actual, offset, fixed, or hidden player counts; custom hover sample and protocol/version label.
- Event countdown placeholders with an explicit time zone.
- Optional in-memory ping-to-join analytics with TTL and a strict address limit.
- English by default, with complete bundled `en_US` and `ru_RU` language files.

The ping handler never reads files, queries a database, resolves DNS, invokes PlaceholderAPI, or reads world state. It only selects an already-rendered immutable entry and updates an optional bounded in-memory counter.

## Requirements and installation

- Java 25
- Paper, Purpur, or Folia 26.2

1. Put `mMotd-1.1.0.jar` in the server's `plugins` directory.
2. Start the server once.
3. Edit `plugins/mMotd/config.yml`.
4. Run `/mmotd reload`.

English is the default. Set `language: ru_RU` and reload to switch all command feedback to Russian. Existing language files automatically receive newly added default keys.

## Profiles

```yaml
motd:
  entries:
    - id: default
      weight: 3
      audience: ANY
      hosts: []
      protocol: { min: -1, max: -1 }
      line1: "<gradient:#00FF87:#60EFFF><bold>SERVER</bold></gradient>"
      line2: "<gray>Online: <white>{online}/{max}</white> · Event in {countdown}"
      text: "Welcome"

    - id: events
      weight: 1
      audience: RETURNING
      hosts: ["events.example.com", "*.event.example.com"]
      protocol: { min: -1, max: -1 }
      line1: "<gold><bold>EVENT NETWORK</bold>"
      line2: "<gray>Join the next event in <white>{countdown}</white>"

  schedule:
    - days: [FRIDAY, SATURDAY]
      time: "18:00-02:00"
      entry: events
      priority: 100
```

A global `ANY` entry without hostname or protocol limits is required as a safe fallback. Invalid entries and schedules are reported individually; a reload with no valid fallback is rejected without replacing the live cache.

Supported placeholders are `{online}`, `{max}`, `{tps}`, `{version}`, `{time}`, `{date}`, `{unique_players}`, `{motd_line}`, `{countdown}`, `{countdown_days}`, `{countdown_hours}`, `{countdown_minutes}`, and `{countdown_seconds}`.

Set `countdown.target` to an ISO-8601 value with an offset, for example `2026-12-31T18:00:00+03:00`. Set `time-zone` to `system` or an IANA zone such as `Europe/Moscow`.

## Icons, player display, and safety

Icon paths are relative to `plugins/mMotd`, must resolve inside that directory even through symlinks, and must point to valid 64×64 PNG files. Missing or invalid files are skipped while the rest of the profile remains usable.

Configuration input is bounded: entries, schedules, hover lines, text length, weights, protocol ranges, hostname patterns, IP whitelist, and analytics cache size all have safe limits. Dynamic command error text is inserted as unparsed MiniMessage data.

`{unique_players}` is deliberately refreshed less often than online count and TPS because loading the offline-player list can be expensive. Change `cache.unique-player-refresh-seconds` if needed.

## Commands and permissions

| Command | Permission | Description |
|---|---|---|
| `/mmotd preview` | `mmotd.command.preview` | Show the current fallback preview |
| `/mmotd reload` | `mmotd.command.reload` | Validate configuration and atomically replace the cache |
| `/mmotd maintenance on\|off` | `mmotd.command.maintenance` | Persistently toggle maintenance mode |
| `/mmotd stats` | `mmotd.command.stats` | Show local ping and conversion statistics |

`mmotd.admin` grants every command permission and `mmotd.maintenance.bypass`.

## Local analytics and telemetry

Local ping analytics are disabled by default. When enabled, client addresses are immediately transformed into salted SHA-256 hashes, retained only in memory, bounded by `analytics.max-unique-addresses`, and removed after `analytics.address-ttl-minutes`. Raw addresses and hashes are never persisted or sent externally. Restarting the server resets the salt and all local statistics.

mMotd separately uses anonymous [bStats metrics](https://bstats.org/plugin/bukkit/mMotd/33358). No IP addresses, hashes, MOTD text, hostnames, or player identifiers are collected. Disable bStats with `metrics.enabled: false`.

The update checker only reads public Modrinth version metadata. It never downloads or replaces JAR files and can be disabled with `updates.enabled: false`.

## Build

```bash
./gradlew clean build
```

Licensed under the MIT License.
