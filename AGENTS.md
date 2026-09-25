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
| **Mod ID & Current Version** | `bomboaddons` (legit) + `bomboclient` (cheat), both v`26.2.28.43` |
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

The mod is currently on version **`26.2.28.43`** (ready for in-game testing).

> [!NOTE]
> ### ✅ COMPLETED & COMPILED IN v26.2.28.43 (Ready for In-Game Testing)
>
> 1. **Camera & Spectator FOV Clamping:** Clamped camera FOV to options FOV (110) during `/b cam (user)` and freecam via `GameRendererMixin`, eliminating speed FOV distortion and dynamic lerp lag.
> 2. **Auto Croesus Instabuy/Instasell & Pricing:** Uses instabuy for keys/kismets and instasell for items; resolves live Bazaar prices for Bank, No Pain No Gain, and Jerry books without hardcoded fallbacks.
> 3. **Auto Croesus Profit Threshold Removal:** Removed profit threshold gate to claim all profitable and free chests unconditionally.
> 4. **Auto Croesus Profit HUD Redesign:** Redesigned chest profit HUD to clean text with shadows and no background box.
> 5. **Dungeon Profit Run Deduplication & Hover Tooltip:** Deduplicates runs on disk/load and renders detailed itemized hover tooltips with values and duration in `/b profit`.
> 6. **Decoupled Profit Sync Auth:** Profit syncing authenticates via `X-Player-UUID` and Bombo API key, completely decoupled from egg auth.
> 7. **Command Separation (`/b area` vs `/b subarea`):** `/b area` displays area and `/b subarea` displays subarea.
> 8. **Profile Viewer Shortcuts & Settings:** Added `/b pv1`, `/b pv2`, and `/b pvconfig` commands; added `⚙` settings button directly to the Profile Viewer header.
> 9. **Multi-Profile Support:** HypixelApiClient fetches all profiles via `all_profiles` and accepts `?profile=` query param.
> 10. **Storage Empty Slot Purging:** Automatically purges empty slots from `storageData` on container open, fixing phantom items (e.g. Divan's Drill in Backpack 10).
> 11. **Island Chests Tab & Localization:** Added dedicated "Island Chests" tab to `/b storage`; added Spanish and translatable container title recognition.
> 12. **Egg Finder Area Gating & Waypoints:** Explicitly unsubscribes from egg WebSocket on Private Island, Garden, Limbo, and Dungeons; removes only the closest waypoint on collection.
> 13. **Estimated Item Value Discrepancy Fixes:** Added reforge stone prices, blacksmith apply cost (5M for warped, 10k–1M by rarity), etherwarp conduit + merger (15M), power scroll (3M), and transmission tuners.
> 14. **Bazaar Mode Toggle & Clean Breakdown:** Added toggle for Insta Buy / Insta Sell / Both; reordered breakdown sections cleanly; suppressed HUD on InventoryScreen.
> 15. **Outbound Token Masking:** `/b apihistory` masks sensitive tokens (first 4 and last 4 chars shown, middle masked) in outbound headers and displays them in tooltips.
> 16. **Slider Numeric Entry & Brand:** Config sliders parse numeric values cleanly (e.g. typing into `300ms`); client brand cleanly reports mod ID (`Constants.MOD_ID`).
> 2. **Remove Flavor Switching Entirely:** Removed "Switch Flavor" button from Config GUI, removed `/b update switch`, removed `installOtherFlavor()`. Users must only run the jar they downloaded.
> 3. **Chat History `/locraw` Attribution:** Dispatched `/locraw` from `BomboaddonsClient.triggerLocraw()` is tagged `ChatHistoryTracker.OUTGOING_TRIGGER = "Locraw Tracker"`. In `ClientPacketListenerMixin`, not flagged as `OUTGOING_PLAYER_INPUT` if an internal trigger is set.
> 4. **Dungeon Chest Key Cost in AutoCroesus & HUD:** In `AutoCroesus.java` tooltip parsing, parses "Dungeon Chest Key". Checks spent state via `isSpentModifier(line)`. Adds `dungeonKeyCost = getLiveDungeonKeyPrice()` or manual threshold to total cost. Displays coin cost + key cost in `DungeonChestProfitHud`.
> 5. **Profit GUI on `/b profit`:** Rendered dedicated Profit GUI (`DungeonProfitScreen`) showing total profit, runs, average profit/run, kismets used, per-floor breakdown, and scrollable recent runs list.
> 6. **AutoCroesus Sync 404 Fix:** Removed call to legacy dead endpoint `https://api.bombo.dpdns.org/kuudra/profit/sync` in `AutoCroesus.java`. Uses `DungeonProfitLog.syncPending` exclusively.
> 7. **Profit Sync 401 Fix:** In `DungeonProfitLog.syncPending`, ensures Bombo token (`EggAuth.getBomboToken()`) is used when calling `POST /api/v1/profits`. Never sends Aaron token to Bombo API.
> 8. **Separate Egg Auth from Bombo Auth:** Skyblocker/Aaron token is strictly for `hysky.de` WebSocket/API. Bombo token is strictly for `api.bombo.dpdns.org`.
> 9. **`/b area` Floor & Subarea Detection:** Prints `Parent / Subarea` or `Catacombs / <Floor> (<phase>)`. Strips private use unicode characters (`\uE000`–`\uF8FF`) from scoreboard lines.
> 10. **`/b ac stats` Price Accuracy (Enchanted Books):** Resolves prices for ultimate enchantments (`ENCHANTMENT_ULTIMATE_*`).
> 11. **Storage Refresh & Chest Waypoints:** Waypoints refresh on container close. Box outlines render through walls (`RenderTypes.secondaryBlockOutline()`).
> 12. **Egg Finder Island Mapping:** Removed "lobby" -> "Hub" mapping in `EggFinder.java`. Maps Moonglade Marsh correctly to `"Moonglade Marsh"`.
> 13. **`/b cmd` Tree Kill, Selection & Copy/Paste:** In `CmdScreen.java`, kills process tree on Ctrl+C via `ProcessHandle.descendants().forEach(ProcessHandle::destroyForcibly)`. Mouse drag selection, copy on selection + right-click, paste on right-click without selection, suppressed mod keybinds while focused.
> 14. **API History Initial Scroll:** Opens `ApiHistoryScreen` scrolled to the bottom (newest items).
> 15. **Profile Viewer Bomboclas API:** Uses `User-Agent: BomboAddons/<version>` via `Constants.myVersion()`, logs to ApiHistory.
> 16. **Aspect of the Void & Player Heads:** Model fallback points to vanilla shovel (`minecraft:item/diamond_shovel`). Ported `PlayerHeadSpecialRendererMixin` from Detexturify so player head items retain skins.
> 17. **Item Value HUD & Tooltip Values:** Decoupled tooltip price additions from `lowestBin` toggle (`s.lowestBin || s.showEstimatedValue || s.bazaarBuySell || s.averageBin`).
> 18. **Remove Mod ID Hiding:** Removed `modIdHider`, removed brand spoofing from `ClientBrandRetrieverMixin`.
> 19. **Remove Stealth Mode:** Removed `Stealth.java`, `/b stealth`, stealth config options.
> 20. **Slider Manual Entry & Keybind Capture:** Sliders support manual text input with coin suffixes (e.g. 100K, 1.5M). Keybind listener properly resets on mouse button clicks.
> 21. **`/b item` Tab Completion:** Registered Brigadier suggestion provider using SkyBlock item catalog (`SkyblockItemManager.getItemCache().keySet()`).

> [!WARNING]
> ### ⚠️ ORDERED IN-GAME TEST QUEUE — next AI should follow this order
>
> Nothing below is verified in-game unless a later handoff explicitly says so. Do not mark an item tested just because compilation passed.
>
> **Phase 0 — .41 crash regression (highest priority; do this first)**
> 1. Hover a vanilla item with no Bombo additions and confirm the tooltip renders normally.
> 2. Hover an item that enables Supercraft/lore additions; confirm the additions appear and no `UnsupportedOperationException` is logged.
> 3. Test custom name/lore, leather armor color, Starts In, dungeon quality, creation date, museum status, and SkyBlock ID additions.
> 4. Test lowest-bin, Bazaar buy/sell, average-bin, and Garden Movement tooltip additions.
> 5. Enable inventory search and confirm highlight replacement still works after the copy.
> 6. Test with resource packs and the no-resource-pack toggle enabled/disabled.
>
> **Phase 1 — startup and dual-flavor safety**
> 7. Launch `bomboaddons-26.2.28.41.jar`; confirm the main menu, world entry, `/b`, config screen, and tooltip mixin all work.
> 8. Launch `bomboclient-26.2.28.41.jar`; confirm the separate mod ID, `/b hide`, `/b stealth`, and cheat mixins work.
> 9. Confirm no startup Mixin apply/injection errors in either build.
> 10. Confirm the legit build has no usable Switch Flavor button and `/b update switch` refuses to install the cheat artifact.
> 11. Confirm the cheat build can still use its intended flavor-switch path without installing the wrong artifact.
> 12. Confirm the both-flavors-installed warning appears when both jars are present.
>
> **Phase 2 — .40 freecam fixes**
> 13. Activate freecam, move far from the character, and confirm the player model remains rendered.
> 14. Move across several distances/chunks and rotate; confirm the body does not flicker or disappear unexpectedly.
> 15. Press F3; confirm XYZ, Block, Chunk, Facing, and Dimension describe the camera and include `(freecam camera)`.
> 16. Change freecam position/rotation; confirm F3 updates, then disable freecam and confirm normal player coordinates return without duplicated lines.
> 17. Test freecam movement, screen open/close, disconnect/reconnect, and toggling near terrain/entities.
>
> **Phase 3 — .39 highest-risk gameplay systems**
> 18. Run `/b chathistory`; verify tabs, attribution, blocked messages, outgoing input, readable key names, event rows, and row clicking.
> 19. Create/edit/run a sequence; test per-step ON/OFF, GUI-title matching, item/slot targeting, loop, jitter, wait, close GUI, command, and Click NPC steps.
> 20. Trigger every sequence safety halt: closed container, missing slot, leaving world, deleted sequence, no enabled steps, and three failed attempts.
> 21. Run `/b ac debug`; verify one summary per GUI, no alternating spam, strict chest-slot selection, click verification, worthless/always-buy classification, spent Redstone Torch modifiers, Kismet EV, and dungeon-key pricing.
> 22. Complete a Croesus run; verify `/b ac stats`, `/b profit`, itemized records, floor tags, net profit, Kismet count, and server sync.
> 23. Test F4/M4 and M7 area detection, `/b area`, and the M4 Etherwarp highlight.
> 24. Test `/b storage <query>`, backpack refresh, double-chest merge, count refresh, stale-position purge, and non-container filtering.
> 25. Test `/b apihistory`, `/b apihistory chat`, request failures, persistence, and log rotation.
> 26. Run `/b pv <invalid-name>`; confirm a real failure message and R retry, not the loading egg or fake-ban finale.
>
> **Phase 4 — .38/.37/.36 automation and authentication**
> 27. Test the Croesus tracker HUD's per-floor breakdown, move/scale/auto-dock behavior, and persistence.
> 28. Test `/b egg debug|auth|reconnect`; confirm `aaron auth OK` or `Bombo auth OK`, correct Bearer header/user-agent, reconnect, token refresh, and no `Incorrect argument`.
> 29. Find an egg and verify Hoppity publishing does not return 401; check the server log.
> 30. Test `/b cmd`: command execution, streamed output, stdin, Ctrl+C, Ctrl+L, history, restart persistence, scroll-up/End auto-follow, `nb on|off`, and keybind suppression.
> 31. Test sequence runtime behavior in the currently built artifacts, then note that the locked target architecture makes the sequencer cheat-only in the P0 remainder.
> 32. Test keybind capture with normal keys, modifiers, mouse buttons, special keys, and Escape cancellation.
>
> **Phase 5 — shared GUI, texture, value, and client regressions**
> 33. Test `/b noobfuscate` on chat and lore while preserving normal formatting and other mods' events.
> 34. Test Hide Mod ID On Join in both jars and inspect the Mixin log.
> 35. Test vanilla lead and Aspect of the Void item models, resource-pack toggles, and missing-texture fallbacks.
> 36. Test EIV source modes, stars/master stars, attributes, unavailable prices, and cache refresh.
> 37. Test config sliders, typed slider labels, Tab navigation, Ctrl+X, horizontal scrolling, modifier-combo capture, persistence, and sequence JSON preservation.
> 38. Test shared entity, armor-stand, particle, screen, Minecraft, container, slot-color, and inventory-button mixins for regressions.
>
> **Phase 6 — flavor-separation implementation after gameplay validation**
> 39. Split the remaining mixed mixins and route tick/render/container hooks through `FlavorBridge`.
> 40. Move cheat-only command subtrees into `CheatFlavor.registerCommands`; remove `LegitFlavor`'s direct `AutoSequenceExecutor` call.
> 41. Extract genuinely shared helpers (`SkullTextures`, hotbar slot helpers, `AutoCombine.getEnchantments`, `AutoCroesus.floorSortKey`/`getFallbackDungeonItemPrice`) into shared code.
> 42. Only after all shared-to-cheat references are removed: exclude `me/bombo/bomboaddons/cheat/**`, complete `CHEAT_ONLY_CLASSES`, and widen `assertNoCheatReferences` beyond the `flavor.cheat` literal.
> 43. Rebuild both jars, rerun every build guard, and inspect both jar contents before any deployment.
>
> **Do not deploy `bomboclient-*.jar` to `/home/ubuntu/bomboapi/releases/`.** The current `.41` artifacts are local only. Deployment, commit, and push remain pending user approval.
>
> [!NOTE]
> ### ✅ SHIPPED in v26.2.28.40 (build/deploy verified earlier, untested in-game) — "Freecam Fixes + Cheat-Tree Groundwork"
>
> **Config / sequences:** per-step `[ON]`/`[OFF]` (`AutoStep.enabled`, skipped by `AutoSequenceExecutor`); `TAB` from a step field to the GUI-title field; clicking a slider's numeric label turns it into a text field (`editingSliderValueItem`); new `ConfigItem.sliderCoins` renders 100K–3M. Chat history: `unlimitedChatHistory` (default false) + `persistHistoryAcrossServers` (default true) - every implicit clear is routed through `ChatHistoryTracker`'s session-divider path instead of wiping the buffer.
> **Dungeons:** `SkyblockUtils`/`DungeonBossManager` parse `The Catacombs (F4/M7)` and `[BOSS] <name>:` (Watcher excluded) → `/b area` prints `F4 (clear)` / `M7 (boss)`; `m4EtherwarpHelper` highlights `(27, 81, 18)` during the F4/M4 boss.
> **Auto Croesus:** strict chest-slot classification + click verification before `boughtCurrentChest` (this was the misclick *and* the false "no profitable chests"; a misclick used to fall straight into the close path); Redstone-Torch chest modifiers read greyed/strikethrough styles as "already spent"; `Auto` key mode prices `DUNGEON_CHEST_KEY` live; kismet EV via `features/dungeons/KismetEv` (matches bomboapi's `commands/kismet.js`, `GET /mod/kismet/<user>` with a local fallback).
> **Profit pipeline:** `features/dungeons/DungeonProfitLog` records itemised runs (items/counts/floor tag parsed from the container title/duration/net profit) and syncs `POST /api/v1/profits`; `DungeonChestProfitHud` gained NET_ONLY vs ITEMIZED; `/b profit [user]` reads local or remote (`/profit/:user[/:type]`).
> **Server:** new `bomboapi/src/profits.js` + `data/profits.json` (backup `src/index.js.bak-profits-*`), routes `POST /api/v1/profits` (Bearer token required), `GET /profits` (JSON/HTML), `GET /profit/:user[/:type]`. Verified live: 401 without a token, 400 on a bad ign, 404 for an unknown user, empty index OK.
> **Storage / eggs:** `StoragePreviewManager` refreshes backpacks on container open; `features/StorageChestWaypoints` backs `/b storage <query>` (double-chest merge, count refresh, purge of non-container positions). `EggFinder` keeps a working subscription when the island reading is unmappable (that was the `Active Subscription Area: None`), remembers collected spawn positions, and no longer depends on an allowlist of egg flavours.
> **API history:** `util/ApiHistory` (ring buffer + `config/bomboaddons/api_history.log`, rotation at 2 MB) fed by `LowestBinManager`, `PlaytimeTracker`, `ModUpdater`, `DungeonProfitLog`, `KismetEv`, `Bomboaddons.logApiRequest`; `/b apihistory` + `/b apihistory chat` + `gui/ApiHistoryScreen`.
> **Textures / PV / values:** `NoObfuscate` is line-aware (rarity-header padding kept, dummy markers revealed); `ItemModelResolverMixin` + `TextureToggleManager.vanillaItemModelId` give Aspect of the Void a valid vanilla fallback and make the no-resource-pack toggle short-circuit *before* a pack model is chosen; `ProfileViewerScreen` shows load failures with `R` to retry and no fake-ban finale (`LoadingEggFinale.abort()`), bigger window + configurable backdrop (`pvScale`/`pvBackgroundAlpha` in GUI Settings); `TabCompletionManager.suggestPlayerNames` replaces the raw loop (Hypixel's `!A-a` placeholders filtered) in `/b pv`, `/b profit`, `/b kuudra`, `/b ring`; EIV honours `priceSourceMode` and adds stars/master stars/attributes.

> [!NOTE]
> ### ✅ SHIPPED in v26.2.28.38 (untested in-game)
>
> 1. **Sequence steps are editable.** The pencil button on a step loads it back into the builder (`[✔ Save Step]`), so `Run Chat / Command` can be changed from `ah` to `/ah` without deleting the step. Type pills switch the action without dropping the edit index.
> 2. **Croesus profit tracker HUD** (`CroesusProfitTrackerHud`, shared): total profit / runs / average / kismets + **per-floor breakdown** (F1..F7, M1..M7, T1..T5) from the sidebar sub-area. New `floors` map in `ProfitRecord` (`bombo_croesus_profit.json`); `AutoCroesus.getCurrentFloorTag()` + `floorSortKey()`. Movable via `HudTarget.CROESUS_TRACKER`.
> 3. **Auto Croesus config category** (`ConfigRegistry` case `"Auto Croesus"`), added to `CHEAT_CATEGORIES` so it only exists in the `bomboclient` build: master switch, paid-cart buying, delays, kismet threshold/delay, reroll value, dungeon chest threshold, dungeon key value, three HUD toggles + Debug Highlight Mode.
> 4. **Chest value panel is movable/scalable** (`croesusProfitHudX/Y/Scale`, `HudTarget.CROESUS_PROFIT`); `x < 0` means "auto" (docked right of the container) and `HudMoveScreen.init` normalizes it so it can be dragged. `DungeonChestProfitHud.renderDummy` shows a sample panel.
> 5. **Simulation debug deduped**: `announcedSimulation` (slot|action set, cleared per container) replaces the last-pair check, so alternating decisions no longer spam chat every tick.
> 6. **`/b cmd` history persisted** to `config/bomboaddons/cmd_history.txt` (500 entries), and every Bombo keybind is suppressed while `CmdScreen` is open (`KeyboardMixin` early return).
> 7. **EggFinder**: `/b egg debug|auth|reconnect` implemented; `EggAuth.updateToken(boolean force)` bypasses the Hoppity-season gate for explicit requests; `EggWebSocket.pendingManualConnect` connects as soon as the async token arrives. The `MinecraftAccessor` mixin was **deleted** (ClassCastException source) — the key pair now comes from the public `Minecraft.getProfileKeyPairManager()`.

> [!NOTE]
> ### ✅ SHIPPED in v26.2.28.37 (server + mod, untested in-game)
>
> 1. **Bombo auth endpoint.** `POST https://api.bombo.dpdns.org/mod/auth` (aaron-compatible): verifies the client's Mojang profile-key-pair signature against Mojang's live public keys (`api.minecraftservices.com/publickeys`, 1h cache) + signed-data proof, issues an HMAC-signed 6h token. Implementation: `/home/ubuntu/bomboapi/src/auth_service.js`, wired into `src/index.js` (backup: `index.js.bak-auth-*`, `mod.js.bak-auth-*`). Token store: `data/auth_tokens.json` (regenerating its `secret` invalidates all tokens).
> 2. **Hoppity writes gated.** `POST/PUT /mod/hoppity` require `Authorization: Bearer <Bombo token>`; GET stays open. ⚠️ Older mod versions (≤26.2.28.36) will now fail their hoppity publishes - acceptable, they're days old.
> 3. **Mod fallback chain.** `EggAuth`: Skyblocker token → hysky aaron → `authenticateWithBombo` (same payload to our endpoint); Bombo token exposed via `EggAuth.getBomboToken()` and attached to hoppity publishes.
>
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

Follow the ordered queue in Section 4 before making more code changes. The next agent should first test the `.41` tooltip fix, then proceed through the phases in order. After gameplay validation, finish the P0 flavor separation: route remaining shared→cheat references through `FlavorBridge`, split mixed mixins, move cheat-only command/tick/render behavior behind the flavor SPI, and only then tighten `jar {}` exclusions, `CHEAT_ONLY_CLASSES`, and `assertNoCheatReferences`.

Deployment safety: **never upload `bomboclient-*.jar` to `/home/ubuntu/bomboapi/releases/`**. The `.41` artifacts are local only until explicitly approved for deployment.

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
