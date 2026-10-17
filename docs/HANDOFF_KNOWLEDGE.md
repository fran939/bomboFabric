# COMPREHENSIVE REPOSITORY KNOWLEDGE & HANDOFF GUIDE
**Mod Version:** `26.2.28.138` | **Target MC:** `26.2` | **Branch:** `26.2`

This document serves as the complete knowledge reservoir for AI agents continuing work on BomboAddons / BomboClient. It captures architecture details, feature implementations, testing states, remote server setup, and operational quirks.

---

## 1. Build & Flavors Architecture

BomboAddons is built as two distinct flavors from a single unified Gradle build:
- **`bomboaddons-<version>.jar` (Legit Flavor):**
  - Distributed publicly via `https://bomboclas.me/mod/version`.
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
Domain: `https://api.bomboclas.me` (API) & `https://bomboclas.me` (Web).

### Key Directories & Files:
- `/home/ubuntu/bomboapi/releases/`: Holds all published `.jar` files. The API automatically scans this folder to determine available versions.
- `/home/ubuntu/bomboapi/data/changelog.json`: Directly served by `https://api.bomboclas.me/mod/changelog` (queried by in-game `/b changelog`).
- `/home/ubuntu/bomboapi/data/profits.json`: Stores player dungeon profit records synced from `/b profit` and `AutoCroesus`.
- `/home/ubuntu/bomboapi/src/profits.js`: Express routes for `/api/v1/profits` and `/profit/:user`.
  - Authenticates via `X-Player-UUID` and Bombo API key (or `Authorization: Bearer`).
  - Returns 200 with empty list `{"runs": []}` if player has no recorded runs.
- `/home/ubuntu/bomboapi/src/index.js`: Express entry point.
  - Proxies Hypixel player profile lookups (`GET /data/:user`). Returns `all_profiles` and accepts `?profile=` query parameter.
- `/home/ubuntu/bomboapi/src/shards_service.js`: Attribute Shards Optimizer calculation and graph relaxation engine.

### API Endpoints:
| Endpoint | Method | Description |
| :--- | :--- | :--- |
| `https://bomboclas.me/mod/latest` | GET | Redirects (302) to the latest **FULL** version `.jar` (never a beta). |
| `https://api.bomboclas.me/mod/latest` | GET | Returns JSON metadata for latest FULL version. |
| `https://api.bomboclas.me/mod/version` | GET | Full catalog JSON (or HTML releases page if opened in browser). |
| `https://api.bomboclas.me/mod/version/beta` | GET | Catalog JSON of beta releases; `latest` field indicates latest beta. |
| `https://api.bomboclas.me/mod/version/full` | GET | Catalog JSON of full milestone releases. |
| `https://api.bomboclas.me/mod/version/:version` | GET | Direct download of `.jar` file for given version. |
| `https://api.bomboclas.me/mod/changelog` | GET | Changelog JSON array. |
| `https://api.bomboclas.me/mod/auth` | POST | Mojang public key signature validation for mod authentication. |
| `https://api.bomboclas.me/api/v1/profits` | POST | Syncs dungeon runs (requires `X-Player-UUID` + API key / Bearer). |
| `https://api.bomboclas.me/profit/:user` | GET | Returns recorded profit runs for given player IGN. |
| `https://bomboclas.me/dungeons/shards` | GET | Web dashboard for Attribute Shards Optimizer with class filters and crafting tree. |
| `https://api.bomboclas.me/api/v1/dungeons/shards` | GET | JSON API returning ranked attribute shard upgrades by damage/coin (`?player=&class=`). |

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

## 4. Features Implemented in v26.2.28.121 -> v26.2.28.126

### v26.2.28.126: RTCA Infinite Loop & Server Rate Limiter Fix
1. **Root Cause of Missing Hovers & 10k Request Flood:**
   - Older mod clients (`v120`–`v122`) intercepted `[RTCA50]` in `ChatMixin.onAddMessage`.
   - When the client's `fetchAsync` completed or errored, it invoked `sendSystemMessage` containing the exact same `[RTCA50]` tag.
   - `ChatMixin` intercepted that system message recursively, resulting in a self-perpetuating infinite loop where one trigger sent 10,000+ API calls to `bomboapi`.
   - Because `bomboapi` and `/command/rtca` were overwhelmed, requests timed out or failed (`if (data == null) return;`), meaning the rich interactive hover chips were never rendered and chat fell back to unformatted raw text.
2. **Fixes Applied:**
   - **BomboBot Broadcast Tag Changed:** In `/home/ubuntu/bombot/index.js`, changed the bridge broadcast prefix from `[RTCA50]` to `[RTCA]`. Older clients only match `[RTCA50]`, so their recursive loop never triggers.
   - **Embedded Hover Tooltips in Bridge:** BomboBot now encodes rich base64 `[HOVER:text:b64:cmd]` payloads directly into its bridge broadcast for both Discord and in-game chat.
   - **Global Server Rate Limiter:** In `bomboapi/server.js`, added sliding-window IP rate limiting (120 req/min per IP across all routes), returning HTTP 429 to stop any rogue client loops before hitting Cloudflare edge limits.
   - **Client Compatibility:** `RtcaChatFormatter.java` supports both `[RTCA]` and `[RTCA50]`.

### Discord `/cmd` Slash Command Architecture
- **Purpose:** Allows authorized Discord operators to execute in-game commands on connected Minecraft clients via the IRC/WebSocket bridge (e.g. `/cmd command: /pv bomboclas user: bomboclas`).
- **Target Socket Isolation:** Evaluates active client sockets and filters specifically by player username (`client.ign.toLowerCase() === targetUser.toLowerCase()`). Only the intended player receives the command packet; other players never receive or execute it.
- **Client Execution ACK (`[CMD_ACK]`):** When the client receives and runs a command, it replies over the bridge socket with `[CMD_ACK]\u0002<ign>\u0002<cmd>\u0002<timestamp>`. The Discord bot listens for this ACK and edits its reply to:
  `✅ Executed Successfully! Player <ign> ran /<cmd> at <timestamp>`.
- **Visibility Control:** Commands are visible in Minecraft chat by default. An optional slash parameter `hide: true` silences the command output.

### v26.2.28.125: Shards Optimizer Pricing & Zero Price Fix
1. **Fusion Ingredient Zero Price Bug:**
   - `shards_service.js` priced fusion ingredients like Cod, Pangolin, Jormung, and Etherdrake as `0 coins` in Direct Purchases because direct purchases looked for raw shard IDs while craft recipes evaluated decomposed components.
   - Updated graph relaxation to store ingredient unit costs and sum estimated direct purchase costs.
2. **GUI Layout & Overlap Elimination (`ShardsOptimizerScreen.java`):**
   - Split the header into two independent rows:
     - Row 1: Class filter pills (`Archer`, `Berserk`, `Mage`, `Healer`, `Tank`, `All`) and `[Hide Maxed] / [Show Maxed]` toggle.
     - Row 2: Budget input field, quick budget buttons (`10M`, `50M`, `100M`, `250M`, `500M`), `[📌 HUD: ON/OFF]`, and `[⚡ Calculate]`.
3. **Catalog Summary Banner:**
   - Displays `Found Shards: X/324 • Maxed Shards: Y/324 • Total Shard Levels: Z/3240`.
4. **Rich SkyBlock-Style Hover Tooltips:**
   - Hovering any shard item in `/b shards` or the pinned HUD displays rarity colors, attribute family lore, buff stats, owned count vs required, target tier progress, and live Bazaar value.
5. **Waypoint Bobbing Drift Fix:**
   - Moved waypoint rendering from `AFTER_TRANSLUCENT_FEATURES` to `COLLECT_SUBMITS` and snapped to frame-exact camera pos, eliminating visual wobble when view bobbing is enabled.

### v26.2.28.124: Network Optimization & Deduplication
1. **Bandwidth Spikes Eliminated:**
   - Fixed uninitialized timer in `AlphaTrackerHud` polling `/mc/alpha.hypixel.net` every tick.
   - Throttled Spotify live reporting to 10s and Discord voice polling to 15s.
   - Added in-flight request deduplication and 60s failure cooldown to `ProfileFetcher.java`.
   - Enabled Gzip compression on Nginx reverse proxy.

### v26.2.28.122 & v26.2.28.123: GUI Input Lockup Fix & Domain Migration
1. **Main Menu / GUI Input Lockup Fix:**
   - Removed an invalid `@Inject(method = "mouseClicked")` on `AbstractSignEditScreen` that broke Mixin transformations and froze keyboard/mouse inputs across the client.
2. **Domain Migration:**
   - Migrated API and web domains from `bombo.dpdns.org` (blocked with HTTP 403 by Cloudflare free DNS ToS) to `bomboclas.me` (`https://api.bomboclas.me` and `https://bomboclas.me`).

---

## 5. Master Implementation & Testing State

### ✅ Implemented, Compiled & Deployed
- **Version:** `26.2.28.126` (Both legit and cheat jars deployed to `ubuntu@ssh.bombo.dpdns.org:/home/ubuntu/bomboapi/releases/`).
- **Build Guards:** `assertFlavorIntegrity` and `assertNoCheatReferences` verified.
- **Backend & Discord:** `bomboapi` rate limiter active; `bombot` updated with `[RTCA]` tag, ACK tracking, and target socket isolation.

### ⚠️ Untested In-Game (Requires Player Testing in Minecraft)

The player has not launched the client to test features since `v26.2.28.120`. When testing in-game, verify the following queues:

#### Queue 1: RTCA & Bridge Testing (`v26.2.28.126`)
- [ ] Run `!rtca` or `!cata` in party/guild/IRC chat.
- [ ] Verify BomboBot broadcasts `🎯 [RTCA] <player> on <profile>...` without older clients flooding requests.
- [ ] Hover over the `(Details)` or class name chips in chat: verify the tooltip displays runs left to CA 50, passive 20% XP rates, and per-class planned runs.
- [ ] From Discord, run `/cmd command: /pv bomboclas user: <ign>` and verify the client executes it and Discord receives a `✅ Executed Successfully!` confirmation with timestamp.

#### Queue 2: Attribute Shards Optimizer & Shopping HUD (`v26.2.28.125`, `v123`, `v121`)
- [ ] Run `/b shards` in-game:
  - Verify layout has zero widget overlap: Row 1 has Class pills + `[Hide Maxed] / [Show Maxed]`; Row 2 has Budget box, Quick Budget pills (`10M 50M 100M 250M 500M`), `[📌 HUD: ON/OFF]`, and `[⚡ Calculate]`.
  - Verify the 3-line catalog summary banner (`Found Shards: X/324 • Maxed: Y/324 • Levels: Z/3240`).
  - Verify fusion ingredient prices (e.g. `Jormung`, `Etherdrake`, `Cod`, `Pangolin`) show real Bazaar values (not `~0` coins).
  - Hover any shard in `/b shards`: verify rich SkyBlock tooltip (rarity color, lore, attribute effect, owned count vs max required, tier progression, Bazaar unit price, and estimated value).
  - Toggle `[📌 HUD: ON]`: verify `SHARDS_SHOPPING_HUD` renders on screen, item quantities can be copied, and hovering items with mouse cursor open shows the same rich tooltip.

#### Queue 3: Bazaar Sign & Galatea Fusion Box Helpers (`v26.2.28.121`)
- [ ] Open Bazaar custom purchase sign: verify the interactive `[ ⚡ Auto-Fill: <qty> (<shard>) ]` overlay button appears and 1-click inserts required quantities.
- [ ] Open Galatea's `Fusion Box` and `Shard Fusion` menus: verify needed fusion items, page arrows, and Repeat Previous Fusion buttons are highlighted.

#### Queue 4: Waypoints Stabilization (`v26.2.28.125`, `v121`)
- [ ] Enable View Bobbing in Minecraft Video Settings.
- [ ] Create a waypoint (`/b wp add`) or use garden/ordered waypoints and walk/sprint: verify waypoints no longer wobble, drift, or lag 1 frame behind world blocks.

---

## 6. Known Quirks & Troubleshooting

1. **Gradle Build File Lock: `Unable to delete directory build/classes/java/client`:**
   - **Cause:** Java Language Server or running client process holding class files.
   - **Fix:** Run `Remove-Item -Recurse -Force "build/classes/java/client"` in PowerShell, then re-run `./gradlew compileClientJava`.
2. **Gradle Build Quirk: `Failed to clean up stale outputs`:**
   - **Cause:** Transient file system lock during `processResources` or `processClientResources`.
   - **Fix:** Re-run `./gradlew build -x test`.
3. **Dual Jar Protocol:**
   - Both jars (`bomboaddons-*.jar` and `bomboclient-*.jar`) must be uploaded via SCP to `ubuntu@ssh.bombo.dpdns.org:/home/ubuntu/bomboapi/releases/`.
4. **Git Branch:**
   - Always commit and push directly to branch `26.2`.
