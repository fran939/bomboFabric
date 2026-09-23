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
| **Mod ID & Current Version** | `bomboaddons` (legit) + `bomboclient` (cheat), both v`26.2.28.36` |
| **Build Flavors** | `bomboaddons` (legit) and `bomboclient` (cheat), built together — see Section 2.B |
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
  ./gradlew compileClientJava          # compiles src/client/java AND src/cheat/java
  ./gradlew build -x test              # BOTH jars:
                                       #   build/libs/bomboaddons-<version>.jar (legit)
                                       #   build/libs/bomboclient-<version>.jar (cheat)
  ```
- One build, both flavors. `-Pflavor=cheat` is a no-op kept for old scripts.
- Both guards run as part of `check` / `build` and must never be removed or weakened:
  - `assertFlavorIntegrity` — the legit jar must contain **no** cheat class, and the cheat jar **must** contain its classes + `bomboaddons.flavor` marker (positive control).
  - `assertNoCheatReferences` — shared code must not reference a cheat package; only the reflective string lookup in `Flavor.java` is allowed.
- `sweepStaleArtifacts` deletes stale `*-sources.jar` / `*-dev.jar` from `build/libs`. There are no sources jars any more (a sources jar would publish cheat sources).
- Known environment quirks (both are file locks, not code problems):
  - `Failed to clean up stale outputs` on `processResources` / `processClientResources` — re-run the command.
  - `Unable to delete directory build/classes/java/client` — the Antigravity IDE's Java language server (or a running dev client) holds it. Deleting that one directory from Git Bash, or stopping the JVM holding it, clears it. **Do not** work around it by changing the source/output layout.

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

The mod is currently on version **`26.2.28.36`**.

> [!NOTE]
> ### ✅ SHIPPED in v26.2.28.36 (all untested in-game)
>
> 1. **Sequence runtime shared.** The executor moved from `src/cheat/.../CheatAutoExecutor` to shared `features/auto/AutoSequenceExecutor` (both jars). This was why sequences did nothing when triggered. `CHEAT_ONLY_CLASSES` reduced to `CheatFlavor.class`; `hasRuntime()` returns true unconditionally.
> 2. **Click Slot one-field targeting.** Item name or slot number (`5`, `slot 5`) in one box; new optional `AutoAction.guiMatcher` gates the step by container title (parser in `AutoSequenceExecutor.findSlotMatching`).
> 3. **Sequence editor UX.** Ctrl+X cut in all fields; focused single-line fields scroll horizontally (`fieldScrollOffset` in `ConfigCustomWidgets`) so long text stays inside the box; action builder one row taller.
> 4. **`/b cmd` stdin.** Input while a process runs goes to its stdin (`runningProcessStdin` + `WORKERS` list); Ctrl+C interrupts; auto-follow scroll (disengages on scroll-up, End re-arms).
> 5. **AutoCroesus debug summary** once per GUI, all chests with hover breakdown; `DungeonChestProfitHud.onScreenRender` was orphaned — now called from `AbstractContainerScreenMixin` render tail; debug mode forces the panel on.
> 6. **EggAuth real aaron handshake.** When Skyblocker is absent: new `MinecraftAccessor` mixin exposes `profileKeyPairManager`; `EggAuth.authenticateWithAaron` does prepareKeyPair → sign random 16 bytes → POST to `hysky.de/api/aaron/authenticate` with `mod=skyblocker`, `modVersion=6.10.4`; token refresh 5 min before expiry; failures retry 15 min.
> 7. **Playtime sync UA.** `PlaytimeTracker.sendPlaytimeDataToCloud` sends `User-Agent: BomboAddons/<ver>` + explicit timeouts (endpoint verified 200).
> 8. **Attribution race fixed.** `executeTracked` snapshots `OUTGOING_TRIGGER` before `mc.execute` and re-applies it inside the lambda (`executeTrackedInner`), so keybind-fired sends keep their trigger even when the send is deferred past the handler's `finally`.

> [!NOTE]
> ### ✅ SHIPPED in v26.2.28.35 (all untested in-game)
>
> 1. **Outgoing attribution.** Keybind-fired commands carry a `Keybind <name>` trigger stamped via `ChatHistoryTracker.OUTGOING_TRIGGER` (ThreadLocal) before execution; typed messages are flagged `OUTGOING_PLAYER_INPUT` in `ClientPacketListenerMixin` when a ChatScreen is open. No more "created by null".
> 2. **Readable key names.** `AutoSequenceManager.describeKeyCode` routes through `ClickLogic.getKeyDisplayName`; `ClickLogic` gained a special-keys switch (Tab, arrows, PgUp/PgDn, Home/End, Insert/Delete, CapsLock); `CustomBindsProcessor.getKeyNameForGlfwCode` maps GLFW 256-269/280 to `key_NNN` names.
> 3. **Sequences category visible.** `FeatureOrganizerManager.initDefaults` merges `BASE_CATEGORIES` into the saved organizer file (user order preserved, new categories appended) and re-homes orphaned features to `Uncategorized`. Display name: `CATEGORY_DISPLAY_NAMES` in `ConfigRegistry` maps `Auto` → `Sequences`.
> 4. **`/b cmd` fixed.** A duplicate `literal("cmd")` registered later on the tree was winning Brigadier conflict resolution (bare `/b cmd` ran the legacy last-command path). Removed; `CmdScreen` session state (buffer/history/input/running process) is static and survives close/reopen; `Ctrl+L` clears.
> 5. **Mod-ID hiding always on.** `Stealth.isBrandHidden()` returns `true`; the config toggle was replaced by a header line. `modIdHider` field kept for schema compat only.
> 6. **Updater cross-line guard.** `ModUpdater.sameMinecraftLine` compares the first two version segments; a 26.1.x install refuses 26.2.x candidates with an explicit message.
> 7. **Modifier-combo keybind capture.** `ConfigCustomWidgets.captureKeyStep` (shared by all four capture widgets and the config screen's `activeKeybindItem` path): a modifier opens a live `[CTRL + ...]` prefix instead of committing; a normal key commits the combo; ESC cancels.
> 8. **Perf scoping.** PestESP render gated on `SkyblockUtils.isInGarden()`; CritterCapsuleArc render AND tick gated on `SafariLocation.inSafari()`.
> 9. **AutoCroesus.** `/b ac debug` → `startDebugSimulation` (highlight-only). RandomStuff ports: `ALWAYS_BUY_ITEMS` / `WORTHLESS_ITEMS` sets, `isAlwaysBuy` claim priority, worthless=0 price classification (exempt from the 0-price safety abort), `[Lvl 1] <Pet>` parsing with rarity from color code. Reference source in `bombotest/autocroesus`.
> 10. **EggFinder handshake.** `Authorization: Bearer <token>` (prefix was missing) and `User-Agent: Skyblocker/6.10.4 (<mc>)` (was stale 6.9.1+26.1.2). Payload envelope already matched Skyblocker's. Reference source in `bombotest/skyblocker`.
> 11. **Account swap race.** `AccountManager.selectAccount(acc)` applies the cached session synchronously at selection (all three call sites: switcher screen, switcher widget, DisconnectedScreenMixin reconnect); `isSessionFor` guard helper exists; partial reflection failures now warn in chat.

> [!NOTE]
> ### ✅ SHIPPED in v26.2.28.34
>
> 1. **Chat history corrected.** Real attribution (server / player / mod, no more everything-credited-to-us), `[BomboAddons]` chat lines merged into their event row so `Trigger:` is on hover, opens at the newest message, and the requested tabs (`All`, `BomboAddons`, `Mods Only`, `Normal Chat`, `Blocked Only`, `Outgoing`) plus an `Events` chip and a `Source` column.
> 2. **Auto sequencer rebuilt as a real feature.** Shared editor in *both* builds (only the executor is cheat-only), new **Click NPC** step (matcher + radius + button), per-step **Repeat**, per-sequence **jitter %** applied to every delay and the loop cooldown, and full chat editing via `/b auto add|remove|run|stop|list|toggle|loop|jitter|key`.
> 3. **One build command, two jars.** `./gradlew build` emits `bomboaddons-<ver>.jar` and `bomboclient-<ver>.jar`; no sources jars; stale artifacts swept; `assertNoCheatReferences` added next to `assertFlavorIntegrity`.
> 4. **Separate mod ids per build** (`bomboaddons` / `bomboclient`) plus a both-installed-at-once warning.
> 5. **Hide Mod ID On Join** (`modIdHider`, both builds, default **on**) - the anti-blacklist countermeasure; the old cheat-only brand mixin was folded into it.
> 6. **No Obfuscate** (`noObfuscate`, `/b noobfuscate`): strips `§k` from chat and item lore.
> 7. **`/b cmd`**: in-game terminal, `/b cmd ping 1.1.1.1` runs immediately.
> 8. **Updater fixed** for the "No releases or update jars found" dead-end.

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
> - `/b chathistory` (now the v26.2.28.34 version): attribution, tabs, boots-at-bottom, event merging, `Trigger:` on hover. **v26.2.28.35 adds:** outgoing rows distinguish Player Input vs keybind-fired (with `Trigger: Keybind <name>`), and key names are readable (`Tab`, not `key 258`).
> - Auto sequencer: the Auto category being visible in **both** builds, the new Click NPC step, Repeat, jitter ranges, and every `/b auto ...` subcommand.
> - The safety halts (close a container mid-sequence to trigger one).
> - The `bomboclient` jar loading at all with its own mod id (`/b hide` / `/b stealth` only exist there), and the both-flavors-installed warning.
> - `FlavorMigration` against a real legacy config (back up `config/bomboaddons/` before testing).
> - **Hide Mod ID On Join**: the `ClientBrandRetriever` mixin now ships in *both* jars, so a failed mixin apply would affect every user - check the log after first launch.
> - **No Obfuscate** (`§k` chat + lore) and **`/b cmd`** (real shell execution, the `nb on|off` built-ins).
> - The updater's new "update exists but no artifact for this build" message.

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
| [`ChatMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ChatMixin.java) & [`ChatScreenMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ChatScreenMixin.java) | `ChatComponent`, `ChatScreen` | Chat peeking, compact message history rendering, stack trace inspection for mod attribution, and the No Obfuscate `ModifyVariable` rewrite. |
| [`ClientBrandRetrieverMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ClientBrandRetrieverMixin.java) | `ClientBrandRetriever` | **Both builds**: hides the mod brand on join (always on since v26.2.28.35). |

---

## 6. Next Direct Actions

0. **Test v26.2.28.36 in-game (highest priority):** (a) sequence trigger key (e.g. N on "ah") actually runs the steps now; (b) `Click Slot / Item` with an item name and a GUI Title fires only inside that GUI; (c) `/b cmd` → `ssh host` → `ls` goes to ssh's stdin; Ctrl+C interrupts; output auto-follows; (d) `/b ac debug` prints one all-chests summary per GUI and the value panel renders; (e) EggFinder `/b egg debug` shows aaron auth OK (or explains why not); (f) pressing B for `/bz` shows `Trigger: Keybind B` not `Server`; (g) Ctrl+X and long-text scroll behave in sequence editor fields; (h) playtime sync stops erroring. Then the v26.2.28.35 list: Trade Max Pet `Ctrl+A` capture, `/b perf` scoping, account swap race, Sequences tab with a custom `/b order`.
1. **Live In-Game Verification:** test the untested list in Section 4 on Hypixel, starting with `/b chathistory` (pure client-side, zero risk) and a keybind-triggered sequence run.
2. **Migrate cheat module group 2** into `src/cheat/java`: `features/hider/**`, `FreecamManager`, `CameraMixin`, `EntityMixin`. Mixins that only serve cheats go into `bomboclient.client.mixins.json` (currently empty, deliberately kept as the slot), and every migrated class must be added to `CHEAT_ONLY_CLASSES` in `build.gradle` or the positive-control assertion will fail the build.
3. **Group 3 (ESP) and group 4 (automation)** per `docs/FEATURE_AUDIT.md`; ~55 files reference those modules, mostly single tick-hook call sites in `BomboaddonsClient`.
4. **Deployment caution:** `bomboapi` scans `/home/ubuntu/bomboapi/releases/` by filename. Do **not** upload `bomboclient-*.jar` into that directory until the catalog is confirmed to ignore it, or `/mod/latest` may resolve to a cheat artifact for every user. Verify with `curl https://api.bombo.dpdns.org/mod/version` immediately after any upload.
5. **Flavor-aware server catalog:** the updater currently rejects catalog entries whose filename does not match the running flavor's prefix. To actually *serve* `bomboclient` updates, `bomboapi` needs a flavor field / endpoint; until then `/b update switch` uses the GitHub release assets, so a cheat release must be published as a GitHub release asset to be reachable.
6. **Follow Section 2 Protocol on every prompt:** bump version, compile **both** flavors, deploy the legit jar, update changelogs, and push to the GitHub `26.2` branch.

---

## 7. Key new files (v26.2.28.34)

| File | Purpose |
| :--- | :--- |
| `gui/CmdScreen.java` | `/b cmd` in-game terminal: real shell execution, streamed output, scrollback, history. |
| `util/NoObfuscate.java` | Strips the `§k` style from chat messages and tooltips without disturbing formatting or events. |
| `mixin/ClientBrandRetrieverMixin.java` | Shared brand hider (was cheat-only; now both builds). |

### Still current from v26.2.28.33

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
