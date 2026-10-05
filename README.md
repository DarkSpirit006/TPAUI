# TPAUI

[![Build](https://img.shields.io/github/actions/workflow/status/DarkSpirit006/TPAUI/build.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=Build)](https://github.com/DarkSpirit006/TPAUI/actions/workflows/build.yml)
[![CodeFactor](https://img.shields.io/codefactor/grade/github/darkspirit006/tpaui?style=for-the-badge&logo=codefactor&logoColor=white&label=Code%20Quality)](https://www.codefactor.io/repository/github/darkspirit006/tpaui)
[![Release](https://img.shields.io/github/v/release/DarkSpirit006/TPAUI?style=for-the-badge&logo=github&logoColor=white&label=Release)](https://github.com/DarkSpirit006/TPAUI/releases/latest)
[![Downloads](https://img.shields.io/modrinth/dt/tpaui?style=for-the-badge&logo=modrinth&logoColor=white&label=Downloads)](https://modrinth.com/plugin/tpaui)
[![Stars](https://img.shields.io/github/stars/DarkSpirit006/TPAUI?style=for-the-badge&logo=github&logoColor=white&label=Stars)](https://github.com/DarkSpirit006/TPAUI/stargazers)
[![Java](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FDarkSpirit006%2FTPAUI%2Frefs%2Fheads%2Fmain%2Fbuild.gradle&search=sourceCompatibility%5Cs%2A%3D%5Cs%2AJavaVersion%5C.VERSION_1_%28%5Cd%2B%29&replace=%241&style=for-the-badge&logo=openjdk&logoColor=white&label=Java&color=f89820)](https://adoptium.net/temurin/)
[![Gradle](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FDarkSpirit006%2FTPAUI%2Frefs%2Fheads%2Fmain%2Fgradle%2Fwrapper%2Fgradle-wrapper.properties&search=gradle-%28%5B0-9.%5D%2B%29-bin&replace=%241&style=for-the-badge&logo=gradle&logoColor=white&label=Gradle&color=0f6b78)](https://gradle.org/)
[![Spigot API](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FDarkSpirit006%2FTPAUI%2Frefs%2Fheads%2Fmain%2Fbuild.gradle&search=spigot-api%3A%28%5B0-9.%5D%2B%29-R&replace=%241&style=for-the-badge&label=Spigot%20API&color=33b5e5)](https://hub.spigotmc.org/)
[![License](https://img.shields.io/github/license/DarkSpirit006/TPAUI?style=for-the-badge&logo=opensourceinitiative&logoColor=white&label=License)](LICENSE)

[![bStats Statistics](https://bstats.org/signatures/bukkit/TPAUI.svg)](https://bstats.org/plugin/bukkit/TPAUI/34518)

A simple TPA and TPAHERE player selector for Bukkit-compatible Minecraft servers.

With EssentialsX installed, EssentialsX keeps control of teleport requests. TPAUI adds the selector opened by `/tpa` with no player name. Without EssentialsX, TPAUI can handle requests on its own.

## Features

- Java players choose TPA with a left-click and TPAHERE with a right-click.
- Bedrock players can use Geyser forms to choose a request type and player.
- Bedrock incoming request forms keep Accept/Deny buttons and open a separate native-switch form for future pop-ups. When off, requests arrive as chat instructions instead.
- Java players can use request-specific Accept, Deny, and Cancel chat actions. Bedrock players do not receive Java chat-click actions or an addon cancel button.
- Anonymous bStats metrics are enabled by default; disable them with `settings.bstats-enabled: false` in `plugins/TPAUI/config.yml`.
- Configurable menu and request sounds; mute all TPAUI sounds with `settings.sounds-enabled: false`.

## Requirements

- Spigot, Paper, Purpur, or another Bukkit-compatible server.
- Minecraft 1.8.8–26.3 is the intended range; not every server version has been runtime-tested.
- The plugin targets Java 8 bytecode; your server still needs the Java version required by its Minecraft release.
- EssentialsX is optional. Geyser and its Cumulus forms API are optional for Bedrock forms.
- Folia is not supported.

## Install

1. Download the JAR from [Modrinth](https://modrinth.com/plugin/tpaui) or [GitHub Releases](https://github.com/DarkSpirit006/TPAUI/releases).
2. Remove any older TPAUI JAR from the server's `plugins` folder.
3. Put the new JAR there and restart.
4. Edit `plugins/TPAUI/config.yml` if needed.

## Commands

| Command | What it does |
| --- | --- |
| `/tpa` | Opens the player selector. |
| `/tpa <player>` | Sends a TPA request. EssentialsX handles it when installed. |
| `/tpahere <player>` | Asks a player to teleport to you. |
| `/tpaccept [player]` | Accepts an incoming request. |
| `/tpdeny [player]` | Denies an incoming request. |
| `/tpacancel [player]` | Cancels an outgoing request. |
| `/tpaui help` | Shows TPAUI commands. |
| `/tpaui reload` | Reloads the configuration. |
| `/tpaui version` | Shows the installed version and checks Modrinth for updates. |

There is no `/tpcancel` alias. With EssentialsX installed, it handles `/tpa <player>`, `/tpahere`, and the request response commands; TPAUI only intercepts bare `/tpa` to open the selector.

## Bedrock request pop-ups

With Geyser/Cumulus forms enabled, the incoming request form keeps its original **Accept** and **Deny** buttons and adds **Incoming pop-up settings**. That opens a separate form with the native **Show incoming TPA request pop-ups** switch; its Submit action applies the setting and returns to the current request. To re-enable pop-ups later, open `/tpa` and choose **Incoming pop-up settings**. The preference is saved per player. When pop-ups are off or forms are unavailable, future requests arrive as chat instructions for `/tpaccept` and `/tpdeny`.

## Permissions

TPAUI's permissions use the `tpaui.*` prefix:

- `tpaui.menu` — open the no-argument `/tpa` selector; default `true`.
- `tpaui.admin` — use `/tpaui reload`; default `op`.
- `tpaui.tpa`, `tpaui.tpahere`, `tpaui.accept`, `tpaui.deny`, and `tpaui.cancel` — standalone request permissions; default `true`.

With `settings.check-permissions: true` (the default), TPAUI checks these nodes; non-admin plugin permissions are enabled by default. Set it to `false` to let everyone use TPAUI features, including reload. EssentialsX still checks its own commands when installed. Permission nodes can be changed in the `permissions` section of the config.

## Updates and settings

`/tpaui version` checks for stable releases on [Modrinth](https://modrinth.com/plugin/tpaui). It does not download or install updates. Settings and messages are in `plugins/TPAUI/config.yml`; use `/tpaui reload` after editing it.

TPAUI is licensed under the [MIT License](LICENSE).
