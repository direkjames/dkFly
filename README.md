# dkFly

Timed flight for our server, built on dkCore. Author: **direk james**.
Server: **Purpur 26.3**, Java **25**. Package: `com.direk.dkfly`.

> **All Rights Reserved.** See [LICENSE](LICENSE). No one may use, copy or modify this code.

## Build

1. Publish dkCore first: in the dkCore project run `gradlew publishToMavenLocal`.
2. Open this folder in IntelliJ IDEA. Set Project SDK and Gradle JVM to **Java 25**.
3. `./gradlew build` → `build/libs/dkFly-1.1.0.jar`.
4. Put the jar in `plugins/` next to `dkCore-1.1.1.jar`.

The dkCore version is set in `gradle.properties` (`dkCoreVersion`).

## Features

- `/fly` toggle; time is charged to the millisecond, only while actually in the air
- Toggle and flight persist through logout and restarts (no falling on join)
- Boss bar and/or action bar timer (`display.mode`), countdown warnings with sound
- Land-first rule: players can't turn fly off themselves high in the air
- Landing rules per reason (`landing`): out of time, restricted area, combat, admin
- World blacklist, configurable speed with permission tiers, unlimited permission
- GUI fly shop in `gui.yml` with custom heads (minecraft-heads.com), decorations and close button
- Transfers with confirm, cooldown and tax; combat tag; admin give/take/set/check
- WorldGuard flag `dkfly`, PlaceholderAPI, AFK pause (DirekAntiAFK or Purpur)
- Works in any game mode (survival, skyblock/OneBlock...); island fly is left to BentoBox's own addon
- Purchases, transfers and admin changes are saved instantly
- Startup banner showing version, hooks and settings (`startup-banner: false` to hide)

## Files

| File | What's in it |
|---|---|
| `config.yml` | Rules, display, landing, transfers, placeholders |
| `messages.yml` | Every message, boss bar and action bar text |
| `gui.yml` | Fly shop layout, packages, decorations, heads |

Upgrading from 1.0.0: the old shop section in config.yml is moved into gui.yml automatically,
and replaced settings (`landing.mode`, `actionbar-while-flying`, `bentobox`) are removed.

## Commands

| Command | Permission |
|---|---|
| `/fly` | dkfly.use |
| `/fly <player>` | dkfly.others |
| `/flytime` | dkfly.use |
| `/flytime shop` | dkfly.shop |
| `/flytime pay <player> <time>` / `confirm` | dkfly.transfer |
| `/flytime check <player>` | dkfly.check.others |
| `/flytime give/take/set <player> <time>` | dkfly.admin |
| `/flytime reload` | dkfly.admin |

Other permissions: `dkfly.unlimited`, `dkfly.bypass.world`, `dkfly.bypass.region`,
`dkfly.bypass.combat`, `dkfly.bypass.cooldown`, plus speed permissions from `speed.permissions`.

## Placeholders

`%dkfly_time%`, `%dkfly_time_seconds%`, `%dkfly_enabled%`, `%dkfly_status%`, `%dkfly_unlimited%`
