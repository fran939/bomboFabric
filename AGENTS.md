# AGENT HANDOFF: BomboAddons (Fabric 26.2)

> [!IMPORTANT]
> **CRITICAL ONBOARDING PROTOCOL FOR EVERY NEW AGENT:**
> **DO NOT TOUCH ANY CODE** before thoroughly reviewing this document (`AGENTS.md`) and the repository knowledge graph in [`graphify-out/GRAPH_REPORT.md`](file:///e:/Users/frand/Documents/bomboaddons-26.2/graphify-out/GRAPH_REPORT.md).
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
| **Mod ID & Current Version** | `bomboaddons` v`26.2.28.33` |
| **Build Flavors** | `bomboaddons` (legit) and `bomboclient` (cheat) — see Section 2.B |
| **Git Target Branch** | `26.2` (`origin/26.2`) |

---

## 2. Mandatory Release & Versioning Protocol (EVERY PROMPT FINISH)

Every time an AI agent finishes a task or prompt, the agent **MUST** complete all of the following steps:

### A. Version Bumping Convention: `(mc version).(mod version).(mod subversion)`
- **Format:** `26.2.<mod_version>.<subversion>`
  - **Subversion (Beta):** Has 4 components (e.g., `26.2.28.30`, `26.2.28.31`). The 4th number is the subversion. Subversions are automatically marked as **BETA**.
  - **Full Release:** Has 3 components (e.g., `26.2.28`, `26.2.29`). Full releases are marked as **FULL**.
- **Action:** Bump `mod_version` in [`gradle.properties`](file:///e:/Users/frand/Documents/bomboaddons-26.2/gradle.properties). For example, after `26.2.28.30`, bump to `26.2.28.31`. When finalizing a milestone, bump to `26.2.29`.

### B. Verify Clean Compilation (BOTH flavors)
- Run:
  ```powershell
  ./gradlew compileClientJava
  ./gradlew build -x test                      # legit  -> build/libs/bomboaddons-<version>.jar
  ./gradlew compileClientJava -Pflavor=cheat
  ./gradlew build -x test -Pflavor=cheat       # cheat  -> build/libs/bomboclient-<version>.jar
  ```
- Make sure there are `0 errors` in **both** flavors. A plain `./gradlew build` does not compile `src/cheat/java`, so the cheat compile is a required step.
- `assertFlavorIntegrity` runs as part of `check` and **fails the build** if a cheat-only class leaks into the legit jar. Never remove or weaken it.
- Known environment quirk: switching flavors can hit `Failed to clean up stale outputs (AccessDeniedException ... build/resources/main/vypv)`. Re-running the command clears it; it is a Windows file lock on a pre-existing resource, not a code problem. Do not "fix" it by changing the resource pipeline.
- After a cheat build, re-run the legit build before deploying so `build/libs/bomboaddons-<version>.jar` is the current artifact.

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

The mod is currently on version **`26.2.28.33`**.

> [!NOTE]
> ### ✅ SHIPPED in v26.2.28.33
>
> 1. **`/b chathistory` fixed.** `ChatHistoryScreen` existed but was never instantiated and no command existed. Now: `/b chathistory`, `/b chathistory auto`, `/b chathistory all`, `chatHistoryKey` keybind, a config GUI button + history limit slider, and a fixed 2px render/click geometry mismatch.
> 2. **Auto sequence event tracking.** `ChatHistoryTracker.recordEvent(...)` + `Event` status bypass the `[BomboAddons]` filter; starts/stops/halts record their real trigger and show `Origin:` / `Feature:` / `Trigger:` in the tooltip, with an `Auto Sequences` filter tab. New `/b auto list|run|stop`.
> 3. **Auto sequence safety guards.** 3x retry then halt, with the reason recorded (container closed, slot not found, player left the world, sequence deleted, no steps).
> 4. **Dual flavor builds** (`bomboaddons` / `bomboclient`) with `src/cheat/java`, the reflective `FlavorBridge` SPI, `constants`-driven identity, and the `assertFlavorIntegrity` guard.
> 5. **Flavor-aware updater + `FlavorMigration`** (legacy `hideCheats=false`/automation profiles are detected and migrated; schema v2).
> 6. **Stealth mode** (cheat flavor): no bridge presence, no egg publishing, vanilla brand on join.
> 7. **`docs/FEATURE_AUDIT.md`** and the `.gitignore` fix that was hiding 5 build-critical sources from git.

> [!WARNING]
> ### ⚠️ UNTESTED FEATURES (all of the above are unverified in-game)
>
> - `/b chathistory` and its tabs/keybind/GUI button.
> - Auto sequence events + trigger tooltips; `/b auto ...` commands.
> - The safety halts (close a container mid-sequence to trigger one).
> - The `bomboclient` jar loading at all, and `/b hide` / `/b stealth` only existing there.
> - `FlavorMigration` against a real legacy config (back up `config/bomboaddons/` before testing).
> - The cheat-only brand mixin applying (a failed mixin apply shows up in the log, not the build).

> [!NOTE]
> ### Previously untested features from v26.2.28.30 (still unverified)
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

1. **Live In-Game Verification:** test the untested list in Section 4 on Hypixel, starting with `/b chathistory` (pure client-side, zero risk) and a keybind-triggered sequence run.
2. **Migrate cheat module group 2** into `src/cheat/java`: `features/hider/**`, `FreecamManager`, `CameraMixin`, `EntityMixin`. Mixins that only serve cheats go into `bomboclient.client.mixins.json`, which excludes them from the legit jar entirely. Add each migrated class to `CHEAT_ONLY_CLASSES` in `build.gradle` as you go.
3. **Group 3 (ESP) and group 4 (automation)** per `docs/FEATURE_AUDIT.md`; ~55 files reference those modules, mostly single tick-hook call sites in `BomboaddonsClient`.
4. **Deployment caution:** `bomboapi` scans `/home/ubuntu/bomboapi/releases/` by filename. Do **not** upload `bomboclient-*.jar` into that directory until the catalog is confirmed to ignore it, or `/mod/latest` may resolve to a cheat artifact for every user. Verify with `curl https://api.bombo.dpdns.org/mod/version` immediately after any upload.
5. **Flavor-aware server catalog:** the updater currently rejects catalog entries whose filename does not match the running flavor's prefix. To actually *serve* `bomboclient` updates, `bomboapi` needs a flavor field / endpoint; until then `/b update switch` uses the GitHub release assets, so a cheat release must be published as a GitHub release asset to be reachable.
6. **Follow Section 2 Protocol on every prompt:** bump version, compile **both** flavors, deploy the legit jar, update changelogs, and push to the GitHub `26.2` branch.

---

## 7. Key new files (v26.2.28.33)

| File | Purpose |
| :--- | :--- |
| `Constants.java` | Mod identity + flavor detection (reads the bundled `bomboaddons.flavor` marker) and artifact prefix. |
| `flavor/FlavorBridge.java` | The SPI shared code uses to reach flavor behaviour. |
| `flavor/Flavor.java` | Reflective locator; falls back to `LegitFlavor` when the cheat class is absent. |
| `flavor/LegitFlavor.java` | No-op legit implementation (present in both jars). |
| `flavor/FlavorMigration.java` | Startup reconciliation + one-time legacy detection. Schema version 2. |
| `util/Stealth.java` | Single gate used by all presence-publishing code. |
| `features/chat/ChatHistoryScreen.java` | Now wired: `/b chathistory`. Tabs, tooltips, copy/execute rows. |
| `features/auto/AutoSequenceManager.java` | Shared data model + persistence + running state. No automation runtime. |
| `src/cheat/.../CheatAutoExecutor.java` | Cheat-only executor: ticks, clicks, guards, event recording. |
| `src/cheat/.../CheatFlavor.java` | Cheat-only bridge impl: `init`, `/b hide`, `/b stealth`, `/b auto ...`. |
| `src/cheat/.../mixin/cheat/ClientBrandRetrieverMixin.java` | Cheat-only vanilla brand spoof under stealth. |
| `src/flavor/cheat/` | Cheat `fabric.mod.json`, `bomboclient.client.mixins.json`, marker. |
| `docs/FEATURE_AUDIT.md` | Reachability audit, measured numbers, no-deletion policy. |
