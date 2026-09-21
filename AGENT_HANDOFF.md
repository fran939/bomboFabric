# AGENT HANDOFF: BomboAddons (Fabric 26.2)

> [!IMPORTANT]
> **CRITICAL ONBOARDING PROTOCOL FOR EVERY NEW AGENT:**
> **DO NOT TOUCH ANY CODE** before thoroughly reviewing this document (`AGENT_HANDOFF.md`) and the repository knowledge graph in [`graphify-out/GRAPH_REPORT.md`](file:///e:/Users/frand/Documents/bomboaddons-26.2/graphify-out/GRAPH_REPORT.md).
>
> You are **REQUIRED** on **EVERY PROMPT FINISH** to strictly follow the **Mandatory Release & Versioning Protocol** detailed below in Section 2.

---

## 1. Minecraft & Loader Specifications

| Parameter | Version / Specification |
| :--- | :--- |
| **Target Minecraft Version** | `26.2` |
| **Mod Loader** | `Fabric Loader 0.19.3` |
| **Loom Version** | `1.17.11` |
| **Fabric API Version** | `0.152.1+26.2` |
| **Java Toolchain** | `Java 25` (source/client bytecode compatibility target Java 21/25) |
| **Mod ID & Current Version** | `bomboaddons` v`26.2.28.31` |
| **Git Target Branch** | `26.2` (`origin/26.2`) |

---

## 2. Mandatory Release & Versioning Protocol (EVERY PROMPT FINISH)

Every time an AI agent finishes a task or prompt, the agent **MUST** complete all of the following steps:

### A. Version Bumping Convention: `(mc version).(mod version).(mod subversion)`
- **Format:** `26.2.<mod_version>.<subversion>`
  - **Subversion (Beta):** Has 4 components (e.g., `26.2.28.30`, `26.2.28.31`). The 4th number is the subversion. Subversions are automatically marked as **BETA**.
  - **Full Release:** Has 3 components (e.g., `26.2.28`, `26.2.29`). Full releases are marked as **FULL**.
- **Action:** Bump `mod_version` in [`gradle.properties`](file:///e:/Users/frand/Documents/bomboaddons-26.2/gradle.properties). For example, after `26.2.28.30`, bump to `26.2.28.31`. When finalizing a milestone, bump to `26.2.29`.

### B. Verify Clean Compilation
- Run:
  ```powershell
  ./gradlew compileClientJava
  ./gradlew build -x test
  ```
- Make sure there are `0 errors` and the build outputs the target `.jar` in `build/libs/bomboaddons-<version>.jar`.

### C. Maintain Full Changelog
- **Local Files:** Update [`CHANGELOG.md`](file:///e:/Users/frand/Documents/bomboaddons-26.2/CHANGELOG.md) and [`data/changelog.json`](file:///e:/Users/frand/Documents/bomboaddons-26.2/data/changelog.json).
- **In-Game GUI (`/b changelog`):** The mod's [`ChangelogScreen`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/ChangelogScreen.java) queries `https://api.bombo.dpdns.org/mod/changelog`, which directly serves `/home/ubuntu/bomboapi/data/changelog.json`.
- **Deploy Changelog to Server:**
  ```powershell
  scp -i "C:\Users\frand\.ssh\id_ed25519" data\changelog.json ubuntu@ssh.bombo.dpdns.org:/home/ubuntu/bomboapi/data/changelog.json
  ```

### D. Deploy Jar to Remote Server
- **Server:** `ubuntu@ssh.bombo.dpdns.org` (port 22, SSH key `C:\Users\frand\.ssh\id_ed25519`).
- **Destination:** `/home/ubuntu/bomboapi/releases/`
- **Deploy Command:**
  ```powershell
  scp -i "C:\Users\frand\.ssh\id_ed25519" build\libs\bomboaddons-<version>.jar ubuntu@ssh.bombo.dpdns.org:/home/ubuntu/bomboapi/releases/
  ```

### E. Commit & Push to GitHub
- Commit all code modifications, handoff docs, and updated build properties.
- Push directly to the `26.2` branch:
  ```powershell
  git add .
  git commit -m "Release v<version>: <summary of changes>"
  git push origin 26.2
  ```

---

## 3. Server Endpoints & Architecture (`bomboapi`)

The backend API running on the server (`ssh.bombo.dpdns.org:3000` via PM2 `bomboapi`) dynamically scans `/home/ubuntu/bomboapi/releases` and exposes:

1. **`https://bombo.dpdns.org/mod/latest` & `https://api.bombo.dpdns.org/mod/latest`:**
   - **CRITICAL:** ALWAYS resolves to the **LATEST FULL VERSION** (3 components, e.g. `26.2.28` or `26.2.29`).
   - It will **NEVER** put a beta subversion (e.g. `26.2.28.31`) into `/latest`.
   - Browser requests to `bombo.dpdns.org/mod/latest` redirect (`302`) directly to the `.jar` download.
   - API requests to `api.bombo.dpdns.org/mod/latest` return JSON with metadata (`version`, `download_url`, `isBeta: false`).
2. **`https://api.bombo.dpdns.org/mod/version`:**
   - JSON API catalog listing all available versions, download links, `latestFull`, and `latestBeta`.
   - Browser requests to `bombo.dpdns.org/mod/version` render the styled HTML releases page with `FULL` vs `BETA` badges.
3. **`https://api.bombo.dpdns.org/mod/version/full`:**
   - JSON API catalog returning only complete/full releases and the current `latest` full version.
4. **`https://api.bombo.dpdns.org/mod/version/beta`:**
   - JSON API catalog returning only beta/subversion releases and the current `latest` beta version.
5. **`https://api.bombo.dpdns.org/mod/version/:version`:**
   - Serves the compiled `.jar` file directly with `Content-Disposition: attachment`.
6. **`https://api.bombo.dpdns.org/mod/changelog`:**
   - Serves the JSON changelog array for the in-game `/b changelog` command.

---

## 4. Current Implementation State & Untested Features

The mod is currently on version **`26.2.28.31`**.

> [!WARNING]
> ### ⚠️ UNTESTED FEATURES (Implemented in v26.2.28.30)
> The user has **NOT YET TESTED** the following 6 features in-game:
>
> 1. **Missing Vanilla Textures (lead etc.)**
>    - **File:** [`TotemAnimationManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/TotemAnimationManager.java)
>    - **Details:** Guarded custom SkyBlock item creation with `SkyblockItemManager.getInfo(upperId) != null`. Vanilla items such as lead now cleanly fall back to `BuiltInRegistries.ITEM` (`Items.LEAD`, `minecraft:lead`) instead of displaying as missing stone textures.
>
> 2. **Search Bar & Text Fields (Ctrl + A, Ctrl + Z, Word Deletions)**
>    - **Files:** [`InventorySlotColorScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/InventorySlotColorScreen.java), [`InventoryButtonsScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/InventoryButtonsScreen.java)
>    - **Details:**
>      - *Select All & Undo/Redo:* Fixed in `InventorySlotColorScreen.java` so `Ctrl + A` selects all text (with a selection highlight) rather than erasing it. Added full `Ctrl + Z` undo and `Ctrl + Y` redo stack support.
>      - *Search Bar Shortcuts:* In `InventoryButtonsScreen.java`, added cursor position navigation, `Ctrl + A`, `Ctrl + Delete` (delete word forward), `Ctrl + Backspace` (delete word backward), `Delete`, `Ctrl + C`, `Ctrl + V`, `Ctrl + Z`, and `Ctrl + Y`. Text fields inside the button editor now undo text modifications rather than deleting buttons.
>
> 3. **Aspect of the Void Texture Overrides**
>    - **File:** [`ItemModelResolverMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ItemModelResolverMixin.java)
>    - **Details:** Whenever the item is an `aspect_of_the_void`, it directly resolves the base `aspect_of_the_void` model, bypassing broken warped variant texture overrides.
>
> 4. **Compact Chat History & Accurate Mod Origin Detection**
>    - **Files:** [`ChatHistoryScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/ChatHistoryScreen.java), [`ChatHistoryTracker.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/ChatHistoryTracker.java), [`BomboaddonsClient.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/BomboaddonsClient.java)
>    - **Details:**
>      - *Compact Layout:* Changed row height to `13px` in `ChatHistoryScreen.java` for a dense layout matching SkyHanni `/shchathistory`.
>      - *Mod Caller Detection:* Updated `inspectCaller()` in `ChatHistoryTracker.java` to skip Mixin generated methods (`handler$*`, `wrapOperation$*`, `invoke$*`, etc.) and Minecraft forwarding methods. Correctly attributes messages to Devonian, BomboAddons, SkyHanni, BetterPV, NEU, Odin, and Skytils.
>      - *Blocked Messages:* Hooked Fabric API's `ClientReceiveMessageEvents.GAME_CANCELED` and `CHAT_CANCELED` in `BomboaddonsClient.java` so chat messages suppressed by SkyHanni or other mods are recorded and tagged as blocked.
>
> 5. **Clicker Trigger Keybind**
>    - **File:** [`ConfigCustomWidgets.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/config/ConfigCustomWidgets.java)
>    - **Details:** Replaced the text input for "Trigger Keybind" with an interactive button showing `[Press Key...]`. Pressing any key (e.g. Tab, R, F) or mouse button (e.g. Mouse 4, Mouse 5) instantly captures the key without typing.
>
> 6. **New "Auto" Automation Category & Sequencer**
>    - **Files:** [`ConfigRegistry.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/config/ConfigRegistry.java), [`AutoSequenceManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/AutoSequenceManager.java), [`ConfigCustomWidgets.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/config/ConfigCustomWidgets.java), [`KeyboardMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/KeyboardMixin.java), [`MouseMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/MouseMixin.java)
>    - **Details:**
>      - Added the `"Auto"` category to `ConfigRegistry.java`.
>      - Implemented `AutoSequenceManager.java` and its custom card builder in `ConfigCustomWidgets.java`.
>      - *Sequence Header:* Name, Trigger Keybind (with press-to-bind listener), Loop toggle (`Loop: ON/OFF`), and Loop delay (ms).
>      - *Step Builder:*
>        - **Click Slot / Item:** Click slot by index or item name matcher, with click type (`LEFT`, `RIGHT`, `SHIFT_LEFT`, `DROP`) and custom delay (ms).
>        - **Close GUI:** Automatically closes container screens with delay (ms).
>        - **Run Command:** Sends chat or slash command with delay (ms).
>        - **Click in World (NPC):** Performs right-click or left-click with delay (ms).
>        - **Wait:** Dedicated delay pause (ms).
>      - *Controls:* Live `[ON]/[OFF]` toggle, instant test run button `[▶ RUN]/[⏹ STOP]`, reordering buttons `[▲] / [▼]`, edit, and delete.
>      - Linked to client key and mouse listeners in `KeyboardMixin.java` and `MouseMixin.java`.

---

## 5. Core Architecture

### Entry Point
- [`BomboaddonsClient.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/BomboaddonsClient.java):
  - Client initialization (`ClientModInitializer.onInitializeClient()`).
  - Registers tick events (`ClientTickEvents.END_CLIENT_TICK`).
  - Hooks Fabric message cancellation listeners (`ClientReceiveMessageEvents.CHAT_CANCELED`, `GAME_CANCELED`).
  - Registers client commands (e.g. `/b`, `/b changelog`, `/b config`, `/b iq`).
  - Initializes configuration system (`ConfigRegistry`, `ConfigManager`), keybind managers, chat history, and auto sequence trackers.

### Key Mixins & Target Injections
| Mixin Class | Target Class | Purpose |
| :--- | :--- | :--- |
| [`ItemModelResolverMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ItemModelResolverMixin.java) | `ItemModelResolver` | Custom SkyBlock item models, FurfSky / custom texture pack resolutions, and Aspect of the Void model bypass. |
| [`KeyboardMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/KeyboardMixin.java) | `KeyboardHandler` | Global key capture for GUI shortcuts, clicker toggles, and AutoSequence trigger keys. |
| [`MouseMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/MouseMixin.java) | `MouseHandler` | Mouse button triggers (Mouse 3, 4, 5) for sequences and clicker automation. |
| [`AbstractContainerScreenMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/AbstractContainerScreenMixin.java) | `AbstractContainerScreen` | Slot coloring, custom inventory buttons, slot hovering, and container click interception. |
| [`GuiMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/GuiMixin.java) | `Gui` | In-game HUD element overlays (pads, mining timers, defense/ehp widgets). |
| [`ClientPacketListenerMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ClientPacketListenerMixin.java) | `ClientPacketListener` | S2C packet interception (titles, action bars, scoreboard changes, chat messages). |
| [`ChatMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ChatMixin.java) & [`ChatScreenMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ChatScreenMixin.java) | `ChatComponent`, `ChatScreen` | Chat peeking, compact message history rendering, and stack trace inspection for mod attribution. |

---

## 6. Next Direct Actions

1. **Live In-Game Verification:**
   - Test the 6 untested features from Section 4 in-game on Hypixel.
2. **AutoSequence Menu Guards:**
   - Add safety checks to handle unexpected server lag or container closures during sequence execution.
3. **Follow Section 2 Protocol on every prompt:**
   - Always bump version, compile, deploy jar to server, update changelog, and push to GitHub `26.2` branch.
