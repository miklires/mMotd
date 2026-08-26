<div align="center">
  <h1>mMotd</h1>
  <p>Cached, scheduled server-list MOTDs for modern Minecraft servers.</p>
  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Folia" height="56" src="https://raw.githubusercontent.com/miklires/mCommand/main/docs/assets/folia-available.png"></a>
  </p>
  <p>
    <a href="https://github.com/miklires/mMotd"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
    <a href="https://modrinth.com/project/mmotd"><img alt="Modrinth" src="https://tr7zw.github.io/uikit/social_buttons_icon/Modrinth-Button-64.png"></a>
    <a href="https://discord.gg/pes25cnWKy"><img alt="Discord" src="https://tr7zw.github.io/uikit/social_buttons_icon/Discord-Button-64.png"></a>
  </p>
  <p>
    <a href="https://bstats.org/plugin/bukkit/mMotd/33358"><img alt="bStats" src="https://img.shields.io/badge/bStats-33358-2F9BE6?style=for-the-badge"></a>
    <a href="https://github.com/miklires/mMotd/releases"><img alt="Release" src="https://img.shields.io/github/v/release/miklires/mMotd?style=for-the-badge"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

## What it does

- Renders two-line MiniMessage MOTDs from a one-second server-state cache.
- Selects random entries or scheduled entries by weekday and time, including overnight ranges.
- Provides maintenance mode with a custom list message, kick message, permission bypass, and IP whitelist.
- Controls favicon, hover sample, displayed protocol version, and visible player counts.

Ping handling never reads files, calls a database, or invokes PlaceholderAPI.

## Requirements

- Java 25
- Paper, Purpur, or Folia 26.2

## Install

1. Put `mMotd-1.0.0.jar` in `plugins`.
2. Start the server once.
3. Edit `plugins/mMotd/config.yml`, then run `/mmotd reload`.

The favicon must be a valid 64×64 PNG in the mMotd data directory. Invalid or missing files are skipped safely.

## Placeholders

`{online}`, `{max}`, `{tps}`, `{version}`, `{time}`, `{date}`, `{unique_players}`, and `{motd_line}` are available in MOTD and hover text.

## Commands and permissions

- `/mmotd reload` — `mmotd.command.reload`
- `/mmotd preview` — `mmotd.command.preview`
- `/mmotd maintenance <on|off>` — `mmotd.command.maintenance`
- Maintenance bypass — `mmotd.maintenance.bypass`
- `mmotd.admin` grants every permission above.

## Telemetry and updates

mMotd uses anonymous [bStats metrics](https://bstats.org/plugin/bukkit/mMotd/33358). Disable them with `metrics.enabled: false`. No IP addresses, MOTD text, or player identifiers are collected.

The update checker only reads public Modrinth version metadata. Disable it separately with `updates.enabled: false`; it never downloads or replaces JAR files.

## Build

```bash
./gradlew clean build
```

The release JAR is written to `build/libs/mMotd-1.0.0.jar`.

Licensed under the MIT License.
