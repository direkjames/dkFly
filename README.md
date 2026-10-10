# dkFly

Timed flight for our server, built on dkCore. Author: **direk james**.
Server: **Purpur 26.3**, Java **25**. Package: `com.direk.dkfly`.

> **All Rights Reserved.** See [LICENSE](LICENSE). No one may use, copy or modify this code.

## Build

1. Publish dkCore first: in the dkCore project run `gradlew publishToMavenLocal`.
2. Open this folder in IntelliJ IDEA. Set Project SDK and Gradle JVM to **Java 25**.
3. `./gradlew build` → `build/libs/dkFly-1.0.0.jar`.
4. Put the jar in `plugins/` next to `dkCore-1.1.1.jar`.

The dkCore version is set in `gradle.properties` (`dkCoreVersion`).

## Features

- `/fly` toggle; time only drains while in the air
- Toggle and flight persist through logout and restarts (no falling on join)
- Countdown on the action bar with sound; safe landing (Slow Falling / no fall damage)
- World blacklist, configurable speed with permission tiers, unlimited permission
- GUI fly shop (Vault economy → dkBank), transfers with confirm, cooldown and tax
- Combat tag, admin give/take/set/check
- WorldGuard flag `dkfly`, PlaceholderAPI, AFK pause
- Works in any game mode (survival, skyblock/OneBlock, etc.); island fly is left to BentoBox's own addon
- Startup banner showing version, hooks and settings (`startup-banner: false` to hide)

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
