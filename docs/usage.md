# Usage

TPAUI exposes the same selector to Java and Bedrock players. EssentialsX is optional.

## Player commands

| Command | EssentialsX present | EssentialsX absent |
| --- | --- | --- |
| `/tpa` | Opens the TPAUI selector when the menu permission is allowed. | Opens the TPAUI selector. |
| `/tpa <player>` | Runs EssentialsX's direct TPA command. | Sends a standalone TPA request. |
| `/tpahere <player>` | Runs EssentialsX's TPAHERE command. | Sends a standalone TPAHERE request. |
| `/tpaccept [player]` | EssentialsX handles acceptance. | Accepts the named request, or the newest incoming request if no name is supplied. |
| `/tpdeny [player]` | EssentialsX handles denial. | Denies the named request, or the newest incoming request if no name is supplied. |
| `/tpacancel [player]` | EssentialsX handles cancellation. | Cancels the named outgoing request; without a name, cancels all outgoing requests. |

There is no `/tpcancel` alias. In standalone mode, tab completion suggests online players for `/tpa` and `/tpahere`, plus matching pending requesters or targets for `/tpaccept`, `/tpdeny`, and `/tpacancel`. When EssentialsX is installed, its own command completions remain in control.

## Selector

- Java inventory: left-click a player for TPA; right-click for TPAHERE.
- Bedrock: when Geyser/Cumulus forms are available and enabled, choose the request mode and player in forms. Open **Incoming pop-up settings** from the first form to access the native on/off switch.
- The inventory size, page size, icons, title, lore, messages, and button styling can be changed in `config.yml`.

## Request actions

Java recipients get clickable Accept/Deny chat actions when the EssentialsX request event is available, or when the standalone request handler is active. The requester can cancel a specific request from the Java Cancel action.

Bedrock request pop-ups keep the original Accept/Deny buttons and add an **Incoming pop-up settings** button. It opens a separate CustomForm with the native switch; submitting the setting returns to the current request form. Future requests use command instructions when pop-ups are off or forms are unavailable. Bedrock players never receive Java chat-click actions or an addon cancel form/button. In standalone mode, permitted requesters receive plain `/tpacancel <player>` guidance.

## Standalone mode notes

- Requests are stored in memory and disappear on restart.
- Requests expire after 60 seconds by default, and are removed if either player disconnects.
- A three-second request cooldown and five-outgoing-request limit are enabled by default. Set the limit to `0` for no limit; set the cooldown to `0` to disable it.
- Accepting a request teleports directly to the other player's current location. Standalone mode does not emulate EssentialsX safety checks, cooldown bypasses, or other EssentialsX teleport features.

## Permissions

### EssentialsX mode

TPAUI checks the EssentialsX TPA/TPAHERE/accept/deny/cancel nodes configured under `permissions.essentials` and lets EssentialsX enforce command permissions and request policy.

### Standalone mode

The default nodes are `tpaui.tpa`, `.tpahere`, `.accept`, `.deny`, and `.cancel`; they default to `true`. Change them in the `permissions.standalone` section or assign permissions in your permission manager.

`tpaui.menu` controls access to the no-argument `/tpa` selector and defaults to `true`. `tpaui.admin` (default `op`) is required for `/tpaui reload`.

## Admin command

- `/tpaui help`
- `/tpaui version` — shows the installed version and checks asynchronously for a stable release on the [TPAUI Modrinth project](https://modrinth.com/plugin/tpaui). The lookup runs only when asked; it does not happen at startup.
- `/tpaui reload` — reloads `plugins/TPAUI/config.yml`
