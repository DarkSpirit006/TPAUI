# Configuration

The complete default file is [`src/main/resources/config.yml`](../src/main/resources/config.yml). Edit `plugins/TPAUI/config.yml` and run `/tpaui reload` to apply changes.

## Settings

| Key | Default | Description |
| --- | --- | --- |
| `settings.intercept-bare-tpa` | `true` | Makes bare `/tpa` open the selector. In standalone mode, `false` lets another plugin handle it. |
| `settings.check-permissions` | `true` | Checks the configured permission nodes. Set `false` to bypass TPAUI's permission checks for all players. EssentialsX still enforces its own commands. |
| `settings.menu-permission` | `tpaui.menu` | Permission for the no-argument `/tpa` selector. |
| `settings.use-geyser-forms` | `true` | Enables optional Bedrock forms where Geyser/Cumulus APIs are available. When disabled, Bedrock receivers see plain command instructions, never clickable chat actions. |
| `settings.bstats-enabled` | `true` | Enables anonymous bStats metrics for project ID `34518`. Set `false` to disable TPAUI reporting; bStats' global server setting is also respected. |
| `settings.sounds-enabled` | `true` | Enables TPAUI's menu and request sounds. Set `false` to mute all TPAUI sounds. |
| `settings.inventory-title` | `&8Teleport Menu` | Java-style selector title. Legacy inventory titles are truncated to 32 characters for older clients. |
| `settings.inventory-size` | `54` | Inventory size (18–54, rounded down to a multiple of 9). One row is reserved for controls. |
| `settings.inventory-players-per-page` | `45` | Player slots per page, clamped to the available slots. |
| `settings.geyser-players-per-page` | `15` | Player buttons per Bedrock form page (clamped to 5–30). |
| `settings.player-item-material` | `PLAYER_HEAD` | Preferred player icon material. |
| `settings.player-item-data` | `0` | Preferred player icon data value for legacy APIs. |
| `settings.player-item-legacy-material` | `SKULL_ITEM` | Fallback icon material, used on older Bukkit versions. |
| `settings.player-item-legacy-data` | `3` | Legacy player-head data value. |
| `settings.filler-material` | `GRAY_STAINED_GLASS_PANE` | Preferred filler material. |
| `settings.filler-legacy-material` | `STAINED_GLASS_PANE` | Legacy filler fallback. |
| `settings.filler-data` | `7` | Legacy filler data/durability. |
| `settings.previous-item-material` / `settings.previous-item-legacy-material` | `ARROW` | Preferred and fallback material for Previous. |
| `settings.close-item-material` / `settings.close-item-legacy-material` | `BARRIER` / `REDSTONE` | Preferred and fallback material for Close. |
| `settings.next-item-material` / `settings.next-item-legacy-material` | `ARROW` | Preferred and fallback material for Next. |
| `settings.request-button-delay-ticks` | `1` | Delay before TPAUI displays additional action buttons after an EssentialsX request event. |
| `settings.fallback-request-expiration-seconds` | `60` | Standalone request lifetime (capped at 24 hours). |
| `settings.fallback-request-cooldown-seconds` | `3` | Standalone sender cooldown; set `0` to disable. |
| `settings.fallback-max-pending-outgoing` | `5` | Standalone maximum outgoing requests; set `0` for unlimited. |

Material names are case-insensitive Bukkit `Material` names. If the preferred material and legacy fallback are unavailable, TPAUI uses a paper item as a last-resort icon.

The Modrinth request runs only when someone uses `/tpaui version`; it is not made during startup. TPAUI checks stable releases from the [TPAUI Modrinth project](https://modrinth.com/plugin/tpaui), shares a concurrent request between callers, and caches a successful result for six hours. The request identifies the project slug and TPAUI version in its User-Agent; it does not include player names, UUIDs, or server configuration.

## Sounds

Set `settings.sounds-enabled` to `false` to mute all TPAUI sounds. Otherwise, customize the Bukkit sound names under `sounds`; defaults are compatible with modern servers and TPAUI falls back to legacy Bukkit enum names on older versions. Set one event sound to `none` to mute only that event. `sounds.volume` is clamped to `0–1`, and `sounds.pitch` to `0.5–2`. Events include menu opening, requests sent/received, accept, deny, cancel, and expiry.

## Bedrock request pop-ups

Incoming Bedrock request forms retain Accept/Deny buttons and add an `Incoming pop-up settings` button. That opens a separate CustomForm with a native `Show incoming TPA request pop-ups` switch; submitting it applies the setting and returns to the current request. Players can also reach the same switch from `/tpa` → `Incoming pop-up settings` to re-enable forms. The preference is saved per player in `plugins/TPAUI/bedrock-preferences.properties` and survives restarts. When forms are disabled or unavailable, future requests arrive in chat with `/tpaccept` and `/tpdeny` instructions. The `messages.form-request-*` and `messages.bedrock-request-popups-*` entries configure form text and feedback.

## Permissions

The `permissions.essentials` and `permissions.standalone` sections configure which nodes TPAUI checks in each mode. Plugin-owned nodes use the `tpaui.*` prefix. The bundled menu and standalone nodes default to `true`; `tpaui.admin` defaults to `op`. Set `settings.check-permissions` to `false` to bypass every TPAUI permission check. EssentialsX continues to enforce its own command permissions and request policy when present.

## Messages and appearance

All user-facing labels and messages are under `messages`. Legacy `&` color codes are supported in chat and inventory text. The `messages.chat-*-color` values accept Bungee `ChatColor` names such as `GREEN`, `RED`, `GRAY`, or `AQUA`; action labels may also include `&` color codes. Geyser form text should be plain text.

Player-name placeholders use `%player%`; page content supports `%mode%` and `%page%`; admin version text supports `%version%`; count/cooldown messages support `%count%` or `%seconds%`.

## Upgrading

TPAUI reads settings from `plugins/TPAUI/config.yml`. Copy any settings you want to keep into that file; missing keys use the documented defaults. Run `/tpaui reload` after editing.
