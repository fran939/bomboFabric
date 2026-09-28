# COMPREHENSIVE REPOSITORY KNOWLEDGE & HANDOFF GUIDE
**Mod Version:** `26.2.28.51` | **Target MC:** `26.2` | **Branch:** `26.2`

This document serves as the complete knowledge reservoir for AI agents continuing work on BomboAddons / BomboClient. It captures architecture details, feature implementations, testing states, remote server setup, and operational quirks.

---

## 1. Build & Flavors Architecture

BomboAddons is built as two distinct flavors from a single unified Gradle build:
- **`bomboaddons-<version>.jar` (Legit Flavor):**
  - Distributed publicly via `https://bombo.dpdns.org/mod/version`.
  - Stripped of all automation, macros, and cheat classes.
  - Mod ID: `bomboaddons`.
- **`bomboclient-<version>.jar` (Cheat Flavor):**
  - Kept strictly for local use or private deployment. **NEVER** deploy to `/home/ubuntu/bomboapi/releases/`.
  - Contains cheat features (AutoCroesus, AutoSequenceExecutor runtime, cheat mixins).
  - Mod ID: `bomboclient`.

### How Flavors Work:
1. `src/client/java`: Shared code between legit and cheat flavors.
2. `src/cheat/java`: Cheat-only implementation code (e.g. `CheatFlavor.java`, `CheatAutoExecutor.java`).
3. `FlavorBridge.java` & `Flavor.java`: Shared code accesses flavor-specific logic dynamically through reflection (`Class.forName("me.bombo.bomboaddons.cheat.CheatFlavor")`). If not present, falls back cleanly to `LegitFlavor.java`.
4. Build Guards in `build.gradle`:
   - `assertFlavorIntegrity`: Validates that `bomboaddons` jar contains zero cheat classes, and `bomboclient` jar contains cheat classes plus the flavor marker.
   - `assertNoCheatReferences`: Validates that shared code does not statically import or reference any cheat classes.

---

## 2. Remote Server Infrastructure (`bomboapi`)

The remote backend server runs on `ssh.bombo.dpdns.org` (port 22, SSH key `C:\Users\frand\.ssh\id_ed25519`, user `ubuntu`).
Process manager: **PM2** (`pm2 status`, service name `bomboapi`).
Base path: `/home/ubuntu/bomboapi/`.

### Key Directories & Files:
- `/home/ubuntu/bomboapi/releases/`: Holds all published `.jar` files. The API automatically scans this folder to determine available versions.
- `/home/ubuntu/bomboapi/data/changelog.json`: Directly served by `https://api.bombo.dpdns.org/mod/changelog` (queried by in-game `/b changelog`).
- `/home/ubuntu/bomboapi/data/profits.json`: Stores player dungeon profit records synced from `/b profit` and `AutoCroesus`.
- `/home/ubuntu/bomboapi/src/profits.js`: Express routes for `/api/v1/profits` and `/profit/:user`.
  - Authenticates via `X-Player-UUID` and Bombo API key (or `Authorization: Bearer`).
  - Returns 200 with empty list `{"runs": []}` if player has no recorded runs (fixed from old 404 behavior).
- `/home/ubuntu/bomboapi/src/index.js`: Express entry point.
  - Proxies Hypixel player profile lookups (`GET /data/:user`). Returns `all_profiles` and accepts `?profile=` query parameter.

### API Endpoints:
| Endpoint | Method | Description |
| :--- | :--- | :--- |
| `https://bombo.dpdns.org/mod/latest` | GET | Redirects (302) to the latest **FULL** version `.jar` (never a beta). |
| `https://api.bombo.dpdns.org/mod/latest` | GET | Returns JSON metadata for latest FULL version. |
| `https://api.bombo.dpdns.org/mod/version` | GET | Full catalog JSON (or HTML releases page if opened in browser). |
| `https://api.bombo.dpdns.org/mod/version/beta` | GET | Catalog JSON of beta releases; `latest` field indicates latest beta. |
| `https://api.bombo.dpdns.org/mod/version/full` | GET | Catalog JSON of full milestone releases. |
| `https://api.bombo.dpdns.org/mod/version/:version` | GET | Direct download of `.jar` file for given version. |
| `https://api.bombo.dpdns.org/mod/changelog` | GET | Changelog JSON array. |
| `https://api.bombo.dpdns.org/mod/auth` | POST | Mojang public key signature validation for mod authentication. |
| `https://api.bombo.dpdns.org/api/v1/profits` | POST | Syncs dungeon runs (requires `X-Player-UUID` + API key / Bearer). |
| `https://api.bombo.dpdns.org/profit/:user` | GET | Returns recorded profit runs for given player IGN. |

---

## 3. Features Implemented in v26.2.28.47 -> v26.2.28.43

### v26.2.28.47: Chat Imgur Delete / Supr Fix
- **Root cause:** `EditBoxMixin.onInsertText` checked `if ((text == null || text.isEmpty()) && (Minecraft.getInstance().gui.screen() instanceof ChatScreen) && ClipboardImageUploader.hasClipboardImage())` and triggered `tryUploadClipboardImage`. When selecting text in chat and pressing `Delete` (Supr) or `Backspace`, Minecraft replaces the selection by calling `insertText("")`. If an image was present in the OS clipboard (e.g. from a past screenshot), deleting text was mistaken for an image paste, uploading the screenshot and inserting `$imgur`.
- **Fix:** Removed the empty `insertText` interception entirely. Real image pasting is strictly gated behind `Ctrl + V` key events on focused edit boxes (`EditBoxMixin.onKeyPressed` / `ChatScreenMixin.onKeyPressed`).

### v26.2.28.46: RTCA Boost Honesty Fix
Server-side (`/home/ubuntu/bomboapi/src/dungeons_service.js`) plus the mod tooltip renderer.
- **Root cause:** `computeClassAverage` had three unknowable boosts defaulted to their *maximum*: `hecatombLevel` -> `HECATOMB.length - 1` (10, +4% x2), `resolveScarf` -> `"grimoire"` (+6%), `graduateBonus` -> `GRADUATE_MAX` (0.20). The source even carried the comment `// Catacombs Graduate attribute; API never exposes it` while using it as the default. 10+4+6+20 = a flat +40% for every class.
- **Fix:** real lookups now count those boosts as 0 and return an `assumed[]` list naming them. Only the `/dungeons` HTML calculator opts in, explicitly, via `{ assumeBestCase: true }`.
- **Verified against live (cache-busted) Hypixel data for `bomboclas`:** essence perks 0.10 (real, all five perks are tier 5), hecatomb 0.04 -> 0, scarf 0.06 -> 0, graduate 0.20 -> 0, total +40% -> **+10%**. M7 XP/run 420,000 -> **330,000**. CA50 runs 1,070 -> **1,361**. `/dungeons` still reports 1,070 by design.
- **Mod side:** `RtcaChatFormatter.friendlyAssumed()` renders the trailing `Not counted: Hecatomb, Scarf accessory, Catacombs Graduate, Mayor` line so an honest `+10%` does not read as a bug.

### v26.2.28.45: Interactive RTCA Chat
- **No RTCA handler existed in the mod.** The line was a plain string BomboBot broadcast into game chat, e.g. `[Bombo] [DC] BomboBot: 🎯 [RTCA50] bomboclas on Cucumber (CA 48.00): 1,070 M7 runs to CA 50 | Archer 48 ...`.
- **Hook:** `ChatMixin.onAddMessage`, on the `addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)` chain that `processIncomingChat` transforms already flow through — slotted in right after the `RingManager` transform. The existing transform shape is `processIncomingChat(raw) -> Component`, the same one `RingManager` uses.
- **`RtcaChatFormatter.java` (new, shared code, both jars):** fetches `/command/rtca/<player>`, builds one `MutableComponent` per class. Records: `CacheEntry`, `ClassInfo(label, exactLevel, maxed, xpPerRun, runsToMax, xpForNextLevel, boostTotal, boostLabel, assumed)`, `RtcaData(player, profile, classAverage, totalRuns, floorLabel, targetLevel, classes)`. 60s per-player cache, daemon thread `Bombo-RtcaFetch`, `IN_FLIGHT` guard. Uses `BomboApiUrl.getApiUrl` + `HttpURLConnection` + `BomboApiKeyManager.getApiKey()` headers. Recorded to `ChatHistoryTracker` with tag `"RTCA"`.
- **Server model changes:** `exactLevel`, `xpToNextLevel` (remaining XP — `xpForNextLevel` is the level *total*, e.g. 93,000,000, so it is useless for a "how much left" tooltip), `xpPerRun`, `runsToMax`, per-class `boost {total, label}`, `classAverageExact` / `classAverageFormatted`. `commands/dungeons.js` pins `classAverage: summary.classAverageExact` on both `/command/rtca` and `/command/dungeons`.

#### Server bugs found and fixed while building this
1. `exactLevel` printed `85467410` — `classProgress(xp).into` is **remaining** XP, not a fraction. Correct form is `progress.level + (progress.into / progress.need)`.
2. Self-referential `c.boost.label` inside its own object literal -> `TypeError: Cannot read properties of undefined (reading 'label')` at `dungeons_service.js:542`, which took down `/api/v1/dungeons` (502). Fixed with a local `boostLabel` accumulator.
3. `classAverage` was integer-averaged (`48.00`). Added `classAverageExact` (`48.47`). **Still outstanding:** the top-level `classAverage` in `/api/v1/dungeons/class-average` still returns the integer `48`; only `/command/*` and `catacombs.classAverage` were switched.

#### Two false alarms — do not "fix" these
- A stale profile snapshot showed `cold_efficiency: 1`, which looked like a broken perk read. The **live** Hypixel API says `5`; all five essence perks really are tier 5, so `+10% perk` was correct all along.
- An apparent caching discrepancy resolved to the `assumeBestCase` code-path difference above, not a cache.

#### MC 26.2 API notes (bit me twice)
- `new Style()` has no no-arg constructor -> use `Style.EMPTY`.
- `ChatFormatting.getChar()` does not exist -> paired a `CLASS_COLORS[]` array with a literal `CLASS_COLOR_CODES[] = {"a","c","d","b","2"}`.

#### Editing method
Heredocs over SSH mangle quotes. Always `write_file` the patch script locally, `scp` the `.py` to `/tmp`, and run it there. Local backups of the server files and every patch script live in `E:/Users/frand/Documents/bomboserver/` (not a git repo).

### v26.2.28.44: Skyblocker Storage Overlay Port
1. **Multi-Grid Storage Interface (`StorageOverlayScreen.java`):**
   - Intercepts opening of `/storage`, ender chest, and backpack menus.
   - Renders all unlocked storage containers (ender chests 1–9, backpacks 1–18) simultaneously in a compact, scrollable grid.
   - Screen handler: `StorageOverlayScreenHandler.java` dynamically maps vanilla container slots into the overlay matrix.
2. **Real-Time Item Query Filtering (`SearchableGridWidget.java`):**
   - Search bar at the top of the overlay.
   - Real-time text search highlights matching items and dims non-matching items across every backpack and ender chest simultaneously.
3. **Configuration Options (`BomboConfig.java` & `ConfigRegistry.java`):**
   - `storageOverlay`: Master toggle (default `false`).
   - `storageOverlayStoragesPerRow`: 1 to 6 storages per row (default `3`).
   - `storageOverlayBackpackWidth`: 1 to 9 columns per storage (default `9`).
   - `storageOverlayRememberSearch`: Preserves search query when reopening storage.
   - `storageOverlayRememberOpened`: Remembers last opened backpack.
   - `storageOverlayDoNotResetCursor`: Preserves mouse cursor coordinates when switching between backpacks.
4. **Live Synchronization (`AbstractContainerMenuMixin.java` & `BackpackPreview.java`):**
   - Listens to `setItem` and `initializeContents` packet handlers.
   - Automatically updates cached container NBT and triggers live grid search re-filtering.
   - Custom player head skin textures deserialized via `StorageOverlayManager.parseItemStack`.
5. **Hover Slot Isolation (`AbstractContainerScreenMixin.java`):**
   - Injected into `isHovering(Slot, double, double)` to prevent ghost slot selection outside the active grid bounds.

### v26.2.28.43: Refinements & Bugfixes
1. **Camera FOV Clamping (`GameRendererMixin.java`):**
   - Clamps camera FOV to options FOV (110) during `/b cam (user)` and freecam, eliminating speed FOV distortion and dynamic lerp lag.
2. **AutoCroesus Pricing & Logic (`AutoCroesus.java`):**
   - Uses instabuy for keys/kismets and instasell for items.
   - Resolves live Bazaar prices for Bank, No Pain No Gain, and Jerry books without hardcoded fallbacks.
   - Removed chest profit threshold gate to claim all profitable and free chests unconditionally.
   - Redesigned chest profit HUD: clean text with shadows, no background box.
3. **Dungeon Profit Tracking (`DungeonProfitLog.java`, `DungeonProfitScreen.java`):**
   - Deduplicates runs on disk/load (purging old 7x/2x duplicate entries).
   - Renders detailed itemized hover tooltip with values and duration in `/b profit`.
   - Decoupled profit sync authentication completely from Hoppity egg auth (uses UUID + Bombo API key).
4. **Commands & Profile Viewer:**
   - Separated `/b area` (displays parent area) and `/b subarea` (displays subarea).
   - Added shortcuts `/b pv1`, `/b pv2`, `/b pvconfig`.
   - Added `⚙` settings button directly to the Profile Viewer header.
   - Multi-profile support in `HypixelApiClient.java` via `all_profiles` and `?profile=` query param.
5. **Storage & Eggs:**
   - Automatically purges empty slots from `storageData` on container open, fixing phantom items (e.g. Divan's Drill in Backpack 10).
   - Added dedicated "Island Chests" tab to `/b storage`.
   - Added Spanish ("cofre", "cofre grande") and translatable container title recognition.
   - Egg Finder: unsubscribes from WebSocket on Private Island, Garden, Limbo, and Dungeons; removes only single closest waypoint on collection.
6. **Estimated Item Value (EIV):**
   - Added reforge stone prices, blacksmith apply cost (5M for warped, 10k–1M by rarity), etherwarp conduit + merger (15M), power scroll (3M), and transmission tuners.
   - Added toggle for Insta Buy / Insta Sell / Both.
   - Ordered HUD sections cleanly and suppressed HUD on `InventoryScreen`.
7. **UI & Input:**
   - Outbound token masking in `/b apihistory` (first 4 and last 4 chars shown, middle masked).
   - Sliders parse numeric values cleanly (e.g. typing into `300ms`).
   - Client brand cleanly reports mod ID (`Constants.MOD_ID`).
   - Flavor switching completely removed (no "Switch Flavor" button or `/b update switch`).
   - Stealth mode and brand spoofing removed.

---

## 4. Current Testing State: Tested vs. Untested

### ✅ TESTED & VERIFIED (Automated / Compile / Remote)
1. **Clean Compilation (Java 25):** Both `compileClientJava` and `build -x test` compile with zero errors.
2. **Build Guards:** Both `assertFlavorIntegrity` and `assertNoCheatReferences` pass cleanly.
3. **Remote Releases:** `bomboaddons-26.2.28.46.jar` deployed to remote server and verified via `GET https://api.bombo.dpdns.org/mod/version/beta`.
4. **Server RTCA model (v26.2.28.46):** `/command/rtca` and `/command/dungeons` verified live and cache-busted — `exactLevel`, fractional `classAverage`, `xpToNextLevel`, `xpPerRun`, `runsToMax`, per-class `boost`, and the `assumed[]` honesty list all confirmed correct for `bomboclas` (+10% real, 330,000 XP/run, 1,361 runs).
5. **Remote Changelog:** Deployed to `/home/ubuntu/bomboapi/data/changelog.json` and verified via `GET https://api.bombo.dpdns.org/mod/changelog`.
6. **Profit API Endpoint:** `POST /api/v1/profits` verified accepting `X-Player-UUID` and API key; `GET /profit/:user` verified returning 200 with empty array on fresh query.
7. **Hypixel Proxy API:** Verified `GET /data/:user` returning `all_profiles` and handling `?profile=` query param.

**Important distinction:** everything on this list is compile-time or HTTP-level verification. Not one line of it proves a line of Java behaves correctly at runtime, because no one has launched the game with these jars.

### ⚠️ UNTESTED IN-GAME (Requires Minecraft Client Testing)
**Nothing in this document has ever been confirmed by a human running the game.** Every feature below compiles and passes the build guards, and that is the entire extent of the verification. The user has not yet reported a pass or fail on any of it.

#### Priority 0: RTCA Chat Hover (`v26.2.28.46` / `v26.2.28.45`) — newest and least verified
- [ ] Trigger `!rtca` in game. Confirm the flat `[RTCA50]` line is *replaced* by the chip line, not duplicated.
- [ ] Hover a class chip: exact level (`Archer 48.92`), runs to max, XP remaining to next level, XP/run, boost breakdown.
- [ ] Confirm the boost total is the honest `+10%` (essence perks only) and the tooltip ends with `Not counted: Hecatomb, Scarf accessory, Catacombs Graduate, Mayor`.
- [ ] Confirm the class average renders fractionally (`CA 48.47`), not `48`.
- [ ] First hover may briefly show `loading class details...` while the daemon fetch runs — verify it fills in, and that repeat `!rtca` within 60s does not re-request.
- [ ] If nothing loads: check the log for thread `Bombo-RtcaFetch` and confirm the `BomboApiKeyManager.getApiKey()` header is on the `/command/rtca/<player>` request. The async fetch -> `sendSystemMessage` path and the first-load placeholder are the most likely things to need adjustment.

#### Priority 1: Storage Overlay (`v26.2.28.44`)
- [ ] Enable `Storage Overlay` in `/b` -> Storage settings.
- [ ] Run `/storage` (or open Ender Chest / Backpack) and verify the multi-grid screen opens.
- [ ] Test real-time item search: type item names (e.g. "drill", "gem") and verify instant filtering across all containers.
- [ ] Test layout options: change "Storages Per Row" (1–6) and "Backpack Columns" (1–9).
- [ ] Test "Preserve Cursor Position": switch between storages and verify cursor doesn't snap to screen center.
- [ ] Test slot interactions: clicking items into/out of backpack slots and moving items.

#### Priority 2: Camera & Spectator FOV (`v26.2.28.43`)
- [ ] Run `/b cam <player>` or activate freecam.
- [ ] Move at high speed / sprint and verify FOV does not stretch or distort (clamped to 110).

#### Priority 3: AutoCroesus & Dungeon Profits (`v26.2.28.43`)
- [ ] Open Croesus container in Catacombs.
- [ ] Verify chest profit HUD renders clean text with shadows and NO background box.
- [ ] Verify keys and kismets are priced using instabuy, and chest items use instasell.
- [ ] Verify `/b profit` displays deduplicated runs with itemized hover tooltips (item values, net profit, duration).
- [ ] Complete a dungeon run and verify profit sync succeeds without 401 or 404 errors.

#### Priority 4: Commands, Profile Viewer & UI (`v26.2.28.43`)
- [ ] Run `/b area` and verify it displays the parent area; run `/b subarea` and verify it displays the subarea.
- [ ] Test `/b pv1`, `/b pv2`, and `/b pvconfig` commands.
- [ ] Open Profile Viewer and click the `⚙` header button to open config.
- [ ] Open `/b apihistory` and verify authorization tokens in headers are masked (e.g. `eyJh...wxyz`).
- [ ] In `/b` config, click a slider value label (e.g. `300ms`), type a number, and press Enter to confirm it updates correctly.

#### Priority 5: Storage & Egg Finder (`v26.2.28.43`)
- [ ] Open `/b storage` and verify "Island Chests" tab appears and lists island chests.
- [ ] Verify empty slots are purged from storage data (no phantom items).
- [ ] Enter Private Island / Garden / Limbo and verify egg WebSocket unsubscribes.
- [ ] Collect a Hoppity egg and verify only the single closest waypoint is removed.

---

## 5. Known Quirks & Troubleshooting

1. **Gradle Build File Lock: `Unable to delete directory build/classes/java/client`:**
   - **Cause:** Java Language Server or running client process holding class files.
   - **Fix:** Run `Remove-Item -Recurse -Force "build/classes/java/client"` in PowerShell, then re-run `./gradlew compileClientJava`.
2. **Gradle Build Quirk: `Failed to clean up stale outputs`:**
   - **Cause:** Occasional transient file system lock during `processResources` or `processClientResources`.
   - **Fix:** Simply re-run `./gradlew build -x test`.
3. **Remote Release Rule:**
   - **NEVER** upload `bomboclient-*.jar` to remote server releases. Only upload `bomboaddons-*.jar`.
4. **Git Branch:**
   - Always commit and push directly to branch `26.2`.
5. **Git remote was renamed:** `fran939/bombofabric.git` -> `fran939/bomboFabric.git`. Pushes still work because GitHub redirects; do not be alarmed by the case change.
6. **`docs/CODE_STYLE.md` does not exist.** It has been asked for twice and is not in the repo — `docs/` holds only `FEATURE_AUDIT.md` and `HANDOFF_KNOWLEDGE.md`. Conventions are inferred from the code: **3-space indent**, `Component`/`MutableComponent` for chat, and the `processIncomingChat(raw) -> Component` transform shape already used by `RingManager`.
7. **Server-side tooling:** `rg` became unavailable partway through (`ENOENT ... rg.exe`). Use `grep` from the terminal instead.
8. **Server edits:** never heredoc a patch over SSH, it mangles quotes. `write_file` the `.py` locally, `scp` it to `/tmp`, run it there.
