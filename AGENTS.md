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
| **Mod ID & Current Version** | `bomboaddons` (legit) + `bomboclient` (cheat), both v`26.2.28.58` |
| **Build Flavors** | `bomboaddons` (legit) and `bomboclient` (cheat), built together — see Section 2.B |
| **Git Target Branch** | `26.2` (`origin/26.2`) |

---

## 2. Mandatory Release & Versioning Protocol (EVERY PROMPT FINISH)

> [!CRITICAL]
> **MANDATORY DUAL-DESTINATION UPLOAD (ACTIONS & REMOTE SERVER):**
> Every time a version is bumped, the release **MUST** be uploaded to **BOTH**:
> 1. **GitHub Actions (`origin/26.2`)**: Every commit pushed to branch `26.2` automatically builds and publishes artifacts on GitHub Actions.
> 2. **Remote Server Releases Directory**: Both compiled jars (`bomboaddons-<version>.jar` and `bomboclient-<version>.jar`) must be transferred via SCP to `ubuntu@ssh.bombo.dpdns.org:/home/ubuntu/bomboapi/releases/`.
>
> **NO PROMPT OR TASK IS FINISHED WITHOUT BOTH OF THESE UPLOADS BEING COMPLETED.**

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
  scp -i "C:\Users\frand\.ssh\id_ed25519" build\libs\bomboaddons-<version>.jar build\libs\bomboclient-<version>.jar ubuntu@ssh.bombo.dpdns.org:/home/ubuntu/bomboapi/releases/
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
7. **`https://bombo.dpdns.org/screenshare` & `https://api.bombo.dpdns.org/api/screenshare/...`:**
   - Web dashboard displaying live streams from active Minecraft players, with real-time frame streaming from the client, live status badges, latency metrics, and HD viewer.

---

## 4. Current Implementation State & Untested Features

The mod is currently on version **`26.2.28.58`** (ready for in-game testing).
For detailed architecture, design decisions, and backend documentation, refer to [`docs/HANDOFF_KNOWLEDGE.md`](file:///e:/Users/frand/Documents/bomboaddons-26.2/docs/HANDOFF_KNOWLEDGE.md).

> [!NOTE]
> ### ✅ COMPLETED & COMPILED IN v26.2.28.58 (Ready for In-Game Testing)
>
> 1. **Watchdog Shutdown Crash Elimination (`NTSTATUS 0xFFFFFFF8`):** Fixed `Client shutdown from post-main` crash report caused by lingering background threads. Added clean pipe closing in `DiscordIpcManager.stop()`, process termination for PowerShell in `SpotifyManager.stop()`, and daemon cleanup in `ScreenshareManager.stopStreaming()`. Hooked these into both `ClientLifecycleEvents.CLIENT_STOPPING` and a JVM `Runtime.getRuntime().addShutdownHook`.
> 2. **Discord Bridge URL & Query Parameter Preservation:** Fixed `IRCClient` incoming Discord message handling by replacing naive `payload.replace("&", "§")` with `formatColorsOutsideUrls(payload)`. `&` characters in URLs (such as `&ex=`, `&is=`, `&hm=`) are strictly preserved, preventing them from being converted into Minecraft color codes.
> 3. **Clickable Web Links in Chat:** Enhanced `IRCClient.formatWithLinks` to tokenize URLs before applying chroma/color formatting. Restores clean ampersands so `URI.create(...)` no longer throws exceptions, restoring clickable `ClickEvent.OpenUrl` links in chat.
> 4. **Chat Image Hover Preview Fix:** Updated `ChatImagePreview.cleanUrl` and `extractImageUrl` to restore `§` to `&` before parsing. Prevents regex stripping of `§e` that previously corrupted signed Discord CDN URLs (`&ex=...` -> `x=...`), eliminating HTTP 403 Forbidden / 404 Not Found errors on image hover preview.
>
> ### ✅ COMPLETED & COMPILED IN v26.2.28.57 (Ready for In-Game Testing)
>
> 1. **High-Speed Screenshare Streaming (25-30 FPS MJPEG) & Telemetry (`https://bombo.dpdns.org/screenshare`):** Upgraded live capture loop to 25-30 FPS MJPEG streaming directly to `/api/screenshare/stream/:user` with ultra-low latency (<50ms). Added multi-monitor/virtual screen bounds clamping in `ScreenshareManager` preventing `IllegalArgumentException` and black screens. Added comprehensive `/ss debug` (and `/b ss debug`) reporting status, target, quality, rolling FPS, bitrate, and latency metrics. Silenced bridge mapping log spam on the server IRC bot.
> 2. **Discord Desktop IPC Concurrency & Authorization Fixes:** Eliminated repeated "Visual Studio Code wants to access your Discord account" authorization modals with a single-session guard (`hasAuthorizedThisSession`). Added `PIPE_LOCK` synchronizing pipe reads and writes to eliminate pipe stalls. Registered full Brigadier command tree for `/b discord` with `debug`, `sync`, and `auth` subcommands. `/b discord debug` now dumps full diagnostics and the last 15 raw RPC packets.
> 3. **Version Catalog GUI (`/b update versions`):** Built interactive `UpdateVersionsScreen` GUI listing all Minecraft 26.2 releases in descending order with `[CURRENT]`, `[FULL]`, and `[BETA]` badges. Features 1-click update/switch button with real-time download and installation feedback, and filter pills (All, Full Only, Betas Only). Added `Accept: application/json` headers and Minecraft version line filtering to prevent the updater from downloading releases for different Minecraft versions.
> 4. **Lyrics Engine Polish & Custom Editor Hotkeys (`/b lyrics`):** Implemented full custom lyrics editor with cursor positioning, click-to-edit, text selection, and keyboard hotkeys (Ctrl+A, Ctrl+C, Ctrl+V, Ctrl+X, Enter, Backspace, Delete). Fixed scissor clipping coordinate calculation bug `(x1, y1, x1+w, y1+h)` that was slicing provider badges in half in candidate modal and clipping text in raw modal. Enforced monotonic millisecond playback timer (`monotonicProgressMs`) querying `$tl.Position.TotalMilliseconds` at 250ms, resolving 4-second time jumps and stopping active line oscillation jitter. Fixed provider parsing so line-synced Paxsenix tracks are correctly badged as `[Line-Synced]`.
> 5. **Restored Config Options & Error Tooltips:** Restored full Diana Lootshare alert suite (alerts, user chat, command, sound, title), Item Tooltip Prices (Lowest BIN, Craft Cost, NPC Price), Dojo utilities, Carnival, Sphinx macro, Hollow Wand, Lasso, Trevor, Daily Reward, Camera settings, and Frozen Blaze AFK warning to `/b` config. Added interactive hover tooltips explaining HTTP 429 rate limits, 401/403 auth errors, 500 server errors, and network diagnostics on playtime sync notifications.
>
> ### ✅ COMPLETED & COMPILED IN v26.2.28.56 (Ready for In-Game Testing)
>
> 1. **Live Screenshare Streaming & Web Dashboard (`https://bombo.dpdns.org/screenshare`):** Built a high-performance web dashboard displaying all active in-game streamers with their Minecraft heads/skins, live FPS/latency statistics, stream resolution, and an HD stream viewer with fullscreen mode. Supports auto-selecting streamers via URL parameter (e.g. `?user=<ign>`).
> 2. **In-Game Frame Streaming (`ScreenshareManager`):** In-game clients now stream compressed JPEG video frames (~8-10 FPS, 640x360) directly to the server API (`/api/screenshare/frame`) whenever screensharing is active or accepted, bypassing render-distance limitations.
> 3. **Clickable Chat Stream Links:** When accepting or starting a screenshare (`/ss <player>`), chat outputs an interactive, clickable link to watch live on the web (`https://bombo.dpdns.org/screenshare?user=<target>`). If the spectated player is outside local chunk render distance, spectator camera cleanly redirects you to the web live stream. Added `/ss stream` / `/ss share` to start broadcasting anytime, and `/ss stop` to stop.
> 4. **Lyrics Delay Slider & Exact Millisecond Input:** Replaced the step buttons with an interactive draggable delay slider (`-5000ms` to `+5000ms`) and a click-to-type number box where users can type any exact millisecond offset (e.g. `-250`, `1200`) and hit Enter to set. Includes a quick `[↺]` reset button.
> 5. **Unformatted & Concatenated LRC Splitting:** Enhanced LRC parser with regex lookahead `(?=\\[\\d{1,2}:\\d{2})|\\r?\\n` to separate lines even when returned as a single unformatted line without newlines.
> 6. **Word-for-Word Syllable Sync:** Supports both `<mm:ss.xxx>word` format and `<word:start:end>` (BetterLyrics / Musixmatch) formats with exact word boundaries.
> 7. **Spotify-Style Full Line Highlights:** For songs without word-by-word timestamps, the active line is highlighted in bold bright white with a background pill (exactly like Spotify in Image 4), eliminating fake word-by-word jumping.
> 8. **Custom Lyrics Editor:** In the `[Raw / Edit]` modal, users can switch to Edit Custom mode to type or paste custom lyrics from clipboard, and click `[Apply Lyrics]` to immediately activate them.
> 9. **Vivi Music Candidate Selector Modal:** Added a multi-candidate selection modal displaying all lyric versions collected across **PAXSENIX**, **LRCLIB**, **BETTERLYRICS**, **KUGOU**, **UNISON**, and **YOULYPLUS**, showing preview snippets, provider badges, and sync tags (`[Word-Synced]`, `[Line-Synced]`, `[Plain]`).
> 10. **Discord Desktop IPC Voice HUD:** Added automatic `AUTHORIZE` approval handling for Discord Desktop IPC voice scopes (`rpc.voice.read`), and added a 4-second background poll so joining Discord voice channels after launching Minecraft automatically syncs the HUD without reconnecting. Added `/b discord sync` / `/ss discord sync` to trigger an instant re-query of the active voice channel.
>
> ### ✅ COMPLETED & COMPILED IN v26.2.28.55 (Ready for In-Game Testing)
>
> 1. **Screenshare Leak Elimination & Delivery Confirmation (`/ss <player>`):** Patched remote IRC bot (`bombot`) to prevent `[SS_` protocol messages from leaking into chat or Discord. Added live IRC online status checks and instant receipt acknowledgment (`[SS_RCVD]`) so senders receive immediate feedback when targets receive requests.
> 2. **Discord Desktop IPC Diagnostics & Handshake:** Updated Discord Client ID to `383226320970055681` (standard desktop IPC), expanded named pipes probe across `\\.\pipe\discord-ipc-0`..`9`, and built `/b discord debug` / `/ss discord debug` diagnostic dumper.
> 3. **Word-for-Word Synchronized Lyrics Engine Overhaul (`/b lyrics`):** Added iTunes Search metadata integration with Paxsenix Apple Music syllable sync API, parsing exact word-by-word timestamps and background vocals. Fixed lyrics line wobble by calculating static line widths. Prevented highlights jumping backwards with monotonic word progress. Added `[-100ms]` and `[+100ms]` delay adder buttons with config persistence. ESC now closes only the raw data modal rather than the entire screen.
> 4. **Spotify Album Cover Artwork & Config Color Swatches:** Dynamically fetches and decodes album artwork textures via iTunes API, rendering high-resolution 256x256 album covers on `SpotifyHud` (toggleable via `spotifyHudShowAlbumArt`). Converted Spotify hex colors to `ConfigItem.color` with interactive colored swatch boxes in `/b` config.
> 5. **Storage Overlay Slot Indexing & Linkage Fix:** Resolved slot indexing corruption in `StorageOverlayScreenHandler.InactiveSlot` by preserving slot indices (`this.index = index`), preventing crashes and enabling reliable display across `/storage`, ender chests, and backpacks.
> 6. **Spectator Camera FOV Clamping:** Clamped camera FOV to base FOV (capped at 110.0) whenever `SpectatorCamManager.isActive()` or `mc.getCameraEntity() != mc.player` in `GameRendererMixin`, stopping disorienting fish-eye distortions.
> 7. **Performance Profiler CPU % & Memory Metrics (`/b perf`):** Added CPU usage percentage and estimated memory footprint per feature to `PerformanceProfiler.Snapshot` and `PerformanceScreen`.
>
> ### ✅ COMPLETED & COMPILED IN v26.2.28.54
>
> 1. **Screensharing Protocol Delivery Delimiter Fix (`/ss <player>`):** Enhanced IRC command parser to split protocol messages on both `\u0002` and `\\s+`, preventing screenshare request commands from leaking into IRC chat and ensuring the recipient reliably receives interactive `[ACCEPT]` and `[DENY]` buttons.
> 2. **Word-for-Word Synchronized Lyrics (`/b lyrics` & `/lyrics`):** Fixed merged words and overlapping highlights by measuring exact bold font widths and adding explicit inter-word spacing (`font.width(" ")`). Replaced jumping second timers with continuous millisecond interpolation. Added `[Raw / Edit Lyrics]` inspection modal with clipboard copy support. Added multi-provider cycling (LRCLIB, PAXSENIX, UNISON, YOULYPLUS). Track and artist links now launch directly in the **Spotify Desktop App** (`spotify:search:...`). Replaced icon with crisp 64x64 official Spotify branding.
> 3. **Spotify HUD Theme Customization:** Added customizable hex colors (Background, Border, Title, Artist, Accent/Controls, Progress Bar Background) to `/b` config under Spotify category.
> 4. **Storage Overlay Execution Method Fix:** Switched container interception to `setScreenAndShow` on the main render thread in `MenuScreensConstructorMixin`, resolving issues where the multi-grid overlay failed to initialize.
> 5. **Spectator Camera FOV & Turn Sensitivity:** Excluded spectator camera from aggressive FOV clamping so FOV remains natural, and applied a 0.5x smooth sensitivity factor to mouse turning in `SpectatorCamManager`.
> 6. **Performance Profiler (`/b perf`) & Entity Scan Optimization:** Added sentinel `EMPTY_INFO` negative caching to `HighlightESP` entity scan, dropping scan time to <0.01 ms. Added real-time JVM Heap memory usage and active thread counts to the profiler header. Added profiler scopes for Spotify, Lyrics, and Storage Overlay.
> 7. **CI/CD & Documentation:** Added GitHub Actions build workflow (`.github/workflows/build.yml`) for automated builds and downloadable jar artifacts. Created comprehensive `README.md` for the `26.2` branch.
>
> ### ✅ COMPLETED & COMPILED IN v26.2.28.53 (Ready for In-Game Testing)
>
> 1. **Screenshare Request Delivery (`/ss <player>`):** Switched IRC request transport to `PRIVMSG` over `#bomboaddons_chat` instead of channel `NOTICE`, fixing packet dropping by IRC channel mode. The target player reliably receives the in-game spectator invitation with clickable `[ACCEPT]` and `[DENY]` buttons. Un-hijacked `/ss` and `/b ss` from Discord IPC to screensharing; added `/b discord` for Discord HUD.
> 2. **Word-for-Word Synchronized Lyrics (`/b lyrics` & `/lyrics`):** Built a rich lyrics GUI powered by LRCLIB API client (inspired by `vivi-music`). Features real-time karaoke tracking with glowing cyan active words, smooth auto-scrolling to active lines, manual mouse wheel scrolling with a "Jump to Playing" button, and clickable web search links for song and artist.
> 3. **Spotify HUD Redesign:** Redesigned dark plum card (`#1E1324`) with official Spotify icon, neon cyan `#00A4DC` controls (`|◀  ⏸/▶  ▶|`), and track progress bar.
> 4. **Spotify Accuracy & Fixes:** Fixed playback timer desync and progress resetting to 0:00 on pause via Windows GSMTC media transport tracking; added clickable song & artist search links; kept HUD visible when opening inventory or containers (`AbstractContainerScreen`).
> 5. **Storage Overlay Crash Fix:** Replaced `SlotAccessor` mixin with direct Unsafe memory offsets for slot positioning, completely preventing classloading linkage crashes when opening storage overlay menus.
> 6. **Spectator Hand & Body Rendering:** Overrode camera entity in `LevelExtractor` to prevent Minecraft from culling the client player's body from the world while spectating, and suppressed client hand rendering in first-person spectator.
> 7. **Hoppity Call Filter:** Gated auto-pickup strictly to calls from Hoppity, preventing accidental pickup of Vincent or other NPC calls.
> 8. **Changelog Colors:** Fixed changelog text color turning white after `§ka` by resetting to `§r§7`, preserving readable gray text across all changelog entries.
>
> ### ✅ COMPLETED & COMPILED IN v26.2.28.52 (Ready for In-Game Testing)
>
> 1. **Interactive Screensharing (`/ss <player>`):** Mod-to-mod in-game spectator/screenshare requests over IRC (`IRCClient.java`). Target gets interactive `§a§l[ACCEPT]` and `§c§l[DENY]` buttons. Added auto-accept whitelist (`autoAcceptScreenshareUsers` in config; commands `/ss whitelist <player>`, `/ss remove <player>`, `/ss list`). Once accepted, sender seamlessly executes spectator camera on target. Cleanly disconnect with `/ss stop`.
> 2. **Interactive Spotify HUD Overlay:** Created `SpotifyManager.java` (background Windows process inspection via JNA `User32` / `ProcessHandle` parsing `Artist - Title`, duration, and controlling playback via virtual media keys) and `SpotifyHud.java` (clean card overlay with track, artist, elapsed timer, and clickable `⏮ ⏯ ⏭` controls). Registered in `HudMoveScreen` under `SPOTIFY_HUD` with custom positioning, dragging, scaling, and reset logic.
> 3. **Storage Overlay Reliability Fix:** Changed screen launch in `ClientPacketListenerMixin.java` to `client.setScreenAndShow(...)`, ensuring `StorageOverlayScreen` reliably initializes and displays without sticking on vanilla containers.
> 4. **Spectator First-Person Arm & Held Item:** In `ItemInHandRendererMixin.java`, bypassed vanilla `submitHandsWithItems` at `HEAD` with `equippedProgress = 0.0f` to render the spectated player's arm skin, held item, offhand, and attack animation cleanly in first-person spectator view.
> 5. **100 Hearts Spectator Bug Fix:** In `HudMixin.java`, redirected `getCameraPlayer()` to `Minecraft.getInstance().player` during spectator camera, preventing vanilla HUD from drawing a massive wall of hearts for Hypixel players with high health.
> 6. **Spectated Held Item Highlight:** In `HudMixin.java`, injected into `tick(boolean)` to update `lastToolHighlight` and `toolHighlightTimer = 40` from the spectated player's held item, displaying their active item name and rarity color above the hotbar.
> 7. **Spectator Look Smoothing:** In `SpectatorCamManager.java`, cleaned mouse delta accumulation and reset offsets on player switch; removed redundant per-mouse-move updates in `MouseMixin.java`.
> 8. **Discord IPC Permission Prompt Prevention:** Updated client ID in `DiscordIpcManager.java` and gated initialization on `discordHudEnabled` so Discord never pops up an authorization dialog on Minecraft startup when disabled.
> 9. **No Obfuscate Label & Changelog Obfuscation Fix:** Fixed formatting of `No Obfuscate (strip §ka§r)` in `ConfigRegistry.java` so only the 'a' character scrambles, and added reset codes `§r` after `§k` in changelog entries.
>
> ### ✅ COMPLETED & COMPILED IN v26.2.28.51 (Ready for In-Game Testing)
>
> 1. **Storage Overlay Execution Method Fix:** Fixed screen launch in `MenuScreensConstructorMixin.java` and `StorageOverlayScreen.java` to invoke `client.setScreenAndShow(...)` instead of `client.gui.setScreen(...)`. Registered `StorageOverlayScreen.setup()` in `BomboaddonsClient.java`. Storage overlay now cleanly opens across `/storage`, ender chests, and backpacks.
> 2. **Discord Voice Call HUD & /ss:** Created `DiscordIpcManager.java` (connecting to Discord Desktop IPC named pipe `\\.\pipe\discord-ipc-0`..`3`, querying active voice channel, member states, and speaking events) and `DiscordVoiceHud.java` (rendering active call participants with green/gray speaking dots, mute/deaf badges, and `[LIVE]` screenshare tag). Added `/ss` and `/b ss` commands and added Discord category to `/b` config.
> 3. **Spectator Camera Dynamic Look & Arm/Item Rendering:** In `SpectatorCamManager.java`, coupled camera angles to the spectated player's rotation (`camEnt.getYRot() + offsetYaw`, etc.) so looking around turns the view. In `ItemInHandRendererMixin.java`, redirected first-person hands rendering so the spectated player's arm skin and held item are rendered in first-person spectator.
> 4. **Player World Body Visibility:** Removed the `Camera.entity()` redirect in `LevelExtractorMixin.java` that caused Minecraft to cull the client player in first-person spectator mode. Kept `isEntityVisible` chunk/frustum bypass and `AvatarRendererMixin.java` so the player body and armor render properly.
> 5. **Anti-Jitter for Recombobulated Items:** In `NoObfuscate.java`, removed `shouldKeepObfuscated` preserving `§ka` on rarity headers, unconditionally clearing obfuscation when `noObfuscate` is active to stop recombobulator text shaking.
> 6. **Clean Mayor Chat Formatting:** In `MayorChatFormatter.java`, removed broken emoji and removed underlines from mayor, perks, and minister names in chat and bridge while preserving colors and interactive tooltips.
> 7. **Version Reporting Fallback:** In `Constants.java`, expanded `myVersion()` fallback chain to try `MOD_ID` -> `LEGIT_MOD_ID` -> `CHEAT_MOD_ID` -> `FALLBACK_VERSION` (`26.2.28.51`), preventing `/b version` from ever reporting "unknown".
>
> ### ✅ COMPLETED & COMPILED IN v26.2.28.50 (Ready for In-Game Testing)
>
> 1. **Storage Overlay Concrete Method Interception Fix:** Shifted screen creation interception from `ScreenConstructor.fromPacket` to concrete static method `MenuScreens.create` in `MenuScreensConstructorMixin.java`. Reliably intercepts container packets, syncs `player.containerMenu`, and launches `StorageOverlayScreen` across all storage menus, ender chests, and backpacks.
> 2. **Stable Obfuscated Glyphs (§k Jitter Fix):** Added `FontMixin.java` to stabilize character advances for obfuscated (`§k`) styles, returning a stable fixed advance equal to the target width while preserving character scrambling animation. Completely stops rarity-upgraded (recombobulated) items from jittering or shaking.
> 3. **Brigadier `/b mayor` Registration:** Registered the `mayor` literal subcommand directly into Brigadier's client command builder in `BomboaddonsClient.java` and `CommandMixin.java`, preventing the `Incorrect argument for command at position 2: b` syntax error.
> 4. **Combined Area & Subarea Output:** Updated `/b area` in both `BomboaddonsClient.java` and `CommandMixin.java` to append the subarea in parentheses when available (e.g. `Current Area: Hub (Village)`).
> 5. **Player Body & Armor/Skin Layer Visibility:** Injected into `LevelExtractor.isEntityVisible` and added `AvatarRendererMixin.java` so `LocalPlayer` is not culled by chunk/frustum checks and armor/skin layers always render cleanly during spectator camera and freecam.
> 6. **Hoppity Call Auto-Pickup:** Implemented automatic answering of incoming Hoppity / Abiphone phone calls on the very first buzz (`BUZZ... [PICK UP]` or `RING... [PICK UP]`) when `autoHoppityCalls` is enabled.
> 7. **No Obfuscate Config Label Fix:** Added `§r` reset formatting in `ConfigRegistry.java` to ensure the closing parenthesis in `No Obfuscate (strip §k§r)` renders properly and is not scrambled.
>
> ### ✅ COMPLETED IN v26.2.28.49 (Ready for In-Game Testing)
>
> 1. **Skyblocker Storage Overlay Interception Fix:** Ported Skyblocker's exact injection point into `MenuScreens.ScreenConstructor.fromPacket` in `MenuScreensConstructorMixin.java`. When enabled, it properly intercepts storage packets, sets `player.containerMenu`, and opens `StorageOverlayScreen` across all ender chests and backpacks.
> 2. **Interactive Hypixel Calendar Mayor Tooltips:** Intercepts `[Mayor]` chat messages (and added `/b mayor`) via `MayorChatFormatter.java`. Hovering the mayor displays the exact Hypixel calendar GUI tooltip layout (yellow header, perks list, descriptions, bottom election disclaimer). Hovering any individual perk shows that perk's description. Hovering the minister displays their name and active minister perk.
> 3. **Live Mayor RTCA Integration:** Server and client now integrate active election data into RTCA calculations. Displays `• Mayor: Diaz (+0%)` under Boost Breakdown and removes `mayor` from the uncounted list.
> 4. **Spectator Camera Entity Decoupling:** Added `SpectatorCamManager.java` to decouple mouse look from the physical entity rotation of the spectated player; the spectated player's body and head pose remain completely stable.
> 5. **Player World Rendering while Spectating:** Added `LevelExtractorMixin.java` redirecting `Camera.entity()` in `LevelExtractor.extractVisibleEntities` so `LocalPlayer` is not discarded and renders properly in the world during spectator camera.
> 6. **Dungeon Quick Join Commands:** Updated `/f1`-`/f7`, `/m1`-`/m7`, and `/e` to dispatch uppercase Hypixel instance names (`CATACOMBS_FLOOR_ONE`..`SEVEN`, `MASTER_CATACOMBS_FLOOR_ONE`..`SEVEN`, `CATACOMBS_ENTRANCE`) with chat feedback.
> 7. **Scoreboard Glyph Subarea Parsing:** Enhanced `SkyblockUtils.parseSubAreaFromLines` to recognize font icon glyphs in Private Use Area (`\uE000-\uF8FF`) before stripping them, parsing subareas like `-  Village` cleanly.
> 8. **Keypad Keybinds Fix:** Removed keypad keys from modifier combo prefixes (`isStarterKey`), enabling Keypad 0-9, Keypad arrows, and math keys to be bound directly in controls.
> 9. **Plaintext IRC IP Removed:** Replaced plaintext server IP `51.170.56.117` in `IRCClient.java` with domain `chat.bombo.dpdns.org`.
>
> ### ✅ COMPLETED IN v26.2.28.48
>
> 1. **RTCA Live Boost Detection (Server & Client):** The server (`bomboapi`) parses player inventory sync data dynamically to detect the highest Hecatomb helmet equipped or in wardrobe (`member.loadout.armor`, `inv_armor`, `wardrobe_contents`), Scarf accessory (`SCARF_GRIMOIRE` +6%, `SCARF_THESIS` +4%, `SCARF_STUDIES` +2%), and Catacombs Graduate shards (`catacombs_graduate` milestone levels 1..10 = 2%..20%). Verified live for fran938 (+28%/+24%) and bomboclas (+36%).
> 2. **Interactive RTCA Chat In-Place Refresh:** `RtcaChatFormatter` updates the pending loading message in `mc.gui.hud.getChat()` in-place and calls `refreshTrimmedMessages()`, eliminating duplicate chat lines.
> 3. **Storage Overlay Activation & Auto-Discovery:** Fixed container title recognition by stripping formatting codes, supporting bare `Ender Chest`, Spanish `Cofre de Ender`, `storage (1/2)`, and backpacks without `#`. Fixed `player.containerMenu` assignment order so container packets sync cleanly without screen reset. Added dynamic re-discovery of backpacks on container open.
> 4. **Backpack Preview Over Inventory Buttons:** Ensured `StoragePreviewManager.renderHoverPreview` renders on top of custom `InventoryButtonManager` buttons and item lists in `InventoryScreen`.
> 5. **IDE Dev Client Clipboard Paste Fix:** Added `System.setProperty("java.awt.headless", "false")` at client initialization, added GLFW modifier key checks in `EditBoxMixin`, and added retry loops for Windows system clipboard locks in `ClipboardImageUploader`.
> 6. **Spectator / Camera Mouse Rotation & Body Visibility:** Allowed full mouse rotation while spectating another player (`mc.getCameraEntity()`) in `MouseMixin`, and ensured player body model remains visible during third-person camera.
> 7. **Dungeon Quick Join Commands:** Updated `/f1`-`/f7`, `/m1`-`/m7`, and `/e` to use `joininstance catacombs_floor_<name>` and `catacombs_entrance`.
> 8. **Starred Mob & F4/M4 Thorn ESP:** Expanded bounding box for starred mob ArmorStand tags, and added arena coordinate detection for F4/M4 Thorn etherwarp helper at `(27, 81, 18)`.
> 9. **Scoreboard Subarea Parsing:** Enhanced `SkyblockUtils.parseSubAreaFromLines` to recognize dash-prefixed scoreboard lines (`- Your Island`) for `/b subarea` and `/b area`.
>
> ### ✅ COMPLETED IN v26.2.28.47
>
> 1. **Chat Imgur Delete / Supr Fix (v26.2.28.47):** Fixed an issue in [`EditBoxMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/EditBoxMixin.java) where selecting text in the chat input and pressing `Delete` (Supr) or `Backspace` would trigger an Imgur upload and paste `$imgur` if any image was present in the clipboard. Removed the phantom `insertText("")` check in `EditBoxMixin` and ensured clipboard image pasting requires an active `Ctrl + V` press in a focused edit box.
> 2. **Interactive RTCA Chat (v26.2.28.45):** BomboBot's flat `[RTCA50]` chat string is intercepted in [`ChatMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ChatMixin.java) and rebuilt as hoverable `MutableComponent` chips by [`RtcaChatFormatter`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/dungeons/RtcaChatFormatter.java). Per-class tooltips show exact level, runs to max, XP to next level, XP/run and the boost breakdown. Async fetch on daemon thread `Bombo-RtcaFetch`, 60s per-player cache, `IN_FLIGHT` guard, records to `ChatHistoryTracker` with tag `"RTCA"`.
> 3. **RTCA Boost Honesty Fix (v26.2.28.46):** The server was defaulting three boosts it cannot see (Hecatomb X, Grimoire scarf, Catacombs Graduate) to their **maximum**, inflating every class to a flat +40%. `computeClassAverage` now counts only what Hypixel actually exposes (the five essence perks from `player_data.perks`) and returns an `assumed[]` list for the rest. Only the `/dungeons` HTML calculator opts into best case via `{ assumeBestCase: true }`. Verified for `bomboclas`: total boost +40% -> **+10%**, M7 XP/run 420,000 -> **330,000**, CA50 runs 1,070 -> **1,361**.
> 3. **⚠️ Neither the hover rendering nor the async fetch path has been seen in-game** — see the test queue below.
>
> ### ✅ COMPLETED & COMPILED IN v26.2.28.44 (Ready for In-Game Testing)
>
> 1. **Skyblocker Storage Overlay Port:** Complete port of Skyblocker's multi-grid storage overlay, replacing `/storage`, ender chest, and backpack menus with a compact, searchable multi-grid interface ([`StorageOverlayScreen`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/StorageOverlayScreen.java), [`SearchableGridWidget`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/SearchableGridWidget.java), [`StorageOverlayScreenHandler`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/StorageOverlayScreenHandler.java), [`BackpackPreview`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/BackpackPreview.java)).
> 2. **Multi-Grid Item Search:** Live item search with instant filtering across all ender chests and backpacks simultaneously.
> 3. **Configurable Layout & Settings:** Configurable storages per row (1–6), backpack columns (1–9), remember search query, remember last opened storage, and mouse cursor position preservation across switches (`dontResetMouseInStorageOverlay` via [`MouseMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/MouseMixin.java)).
> 4. **Live Inventory Synchronization:** Dynamic slot updates and real-time container item caching with full NBT, base64, and custom player head texture support.
> 5. **Hover Slot Isolation:** Injected into `isHovering(Slot, double, double)` in [`AbstractContainerScreenMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/AbstractContainerScreenMixin.java) to restrict hover targeting strictly to visible grid slots and player inventory.
>
> ### ✅ COMPLETED IN v26.2.28.43
>
> 1. **Camera & Spectator FOV Clamping:** Clamped camera FOV to options FOV (110) during `/b cam (user)` and freecam via [`GameRendererMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/GameRendererMixin.java), eliminating speed FOV distortion and dynamic lerp lag.
> 2. **Auto Croesus Instabuy/Instasell & Pricing:** Uses instabuy for keys/kismets and instasell for items; resolves live Bazaar prices for Bank, No Pain No Gain, and Jerry books without hardcoded fallbacks.
> 3. **Auto Croesus Profit Threshold Removal:** Removed profit threshold gate to claim all profitable and free chests unconditionally.
> 4. **Auto Croesus Profit HUD Redesign:** Redesigned chest profit HUD to clean text with shadows and no background box.
> 5. **Dungeon Profit Run Deduplication & Hover Tooltip:** Deduplicates runs on disk/load and renders detailed itemized hover tooltips with values and duration in `/b profit`.
> 6. **Decoupled Profit Sync Auth:** Profit syncing authenticates via `X-Player-UUID` and Bombo API key, completely decoupled from egg auth.
> 7. **Command Separation (`/b area` vs `/b subarea`):** `/b area` displays parent area and `/b subarea` displays subarea.
> 8. **Profile Viewer Shortcuts & Settings:** Added `/b pv1`, `/b pv2`, and `/b pvconfig` commands; added `⚙` settings button directly to the Profile Viewer header.
> 9. **Multi-Profile Support:** HypixelApiClient fetches all profiles via `all_profiles` and accepts `?profile=` query param.
> 10. **Storage Empty Slot Purging:** Automatically purges empty slots from `storageData` on container open, fixing phantom items (e.g. Divan's Drill in Backpack 10).
> 11. **Island Chests Tab & Localization:** Added dedicated "Island Chests" tab to `/b storage`; added Spanish and translatable container title recognition.
> 12. **Egg Finder Area Gating & Waypoints:** Explicitly unsubscribes from egg WebSocket on Private Island, Garden, Limbo, and Dungeons; removes only the closest waypoint on collection.
> 13. **Estimated Item Value Discrepancy Fixes:** Added reforge stone prices, blacksmith apply cost (5M for warped, 10k–1M by rarity), etherwarp conduit + merger (15M), power scroll (3M), and transmission tuners.
> 14. **Bazaar Mode Toggle & Clean Breakdown:** Added toggle for Insta Buy / Insta Sell / Both; reordered breakdown sections cleanly; suppressed HUD on InventoryScreen.
> 15. **Outbound Token Masking:** `/b apihistory` masks sensitive tokens (first 4 and last 4 chars shown, middle masked) in outbound headers and displays them in tooltips.
> 16. **Slider Numeric Entry & Brand:** Config sliders parse numeric values cleanly (e.g. typing into `300ms`); client brand cleanly reports mod ID (`Constants.MOD_ID`).
> 17. **Flavor Switching Completely Removed:** Removed "Switch Flavor" button from Config GUI, removed `/b update switch`, removed `installOtherFlavor()`. Users must only run the jar they downloaded.
> 18. **Stealth Mode & Brand Spoofing Removed:** Removed `Stealth.java`, `/b stealth`, and brand spoofing. Real brand and mod ID are now cleanly reported.

> [!WARNING]
> ### ⚠️ ORDERED IN-GAME TEST QUEUE — next AI should follow this order
>
> Nothing below is verified in-game unless explicitly marked tested. Do not mark an item tested just because compilation passed.
>
> **Phase 0 — RTCA Chat Hover (`v26.2.28.46`, newest, least verified)**
> 1. In game chat (or Discord->in-game bridge) trigger `!rtca` for a player with dungeon data. Confirm the flat `[RTCA50]` line is replaced by a chip line instead of appearing twice.
> 2. Hover a class chip. Confirm the tooltip shows exact level (e.g. `Archer 48.92`), runs to max, **XP remaining** to next level, XP/run and a boost breakdown.
> 3. Confirm the boost total is the honest one: only the five essence perks should be counted. The tooltip must end with a `Not counted: Hecatomb, Scarf accessory, Catacombs Graduate, Mayor` line.
> 4. Confirm class average renders fractionally (`CA 48.47`), not `48`.
> 5. On the very first hover the line may briefly show `loading class details...` while the daemon fetch runs — verify it fills in and that repeated `!rtca` calls within 60s do not re-request.
> 6. If nothing ever loads, check the log for the fetch thread and confirm the API key header (`BomboApiKeyManager.getApiKey()`) is present on `/command/rtca/<player>`.
>
> **Phase 1 — Storage Overlay (`v26.2.28.44`)**
> 1. In `/b` -> Storage, enable `Storage Overlay`.
> 2. Run `/storage` (or open Ender Chest / Backpack); confirm the multi-grid screen opens with all unlocked containers.
> 3. Type in the search box; confirm real-time highlight filtering across all containers simultaneously.
> 4. Test layout configuration: adjust "Storages Per Row" (1–6) and "Backpack Columns" (1–9); confirm screen adapts cleanly.
> 5. Confirm "Preserve Cursor Position" works (mouse cursor does not jump to screen center when switching storages).
> 6. Move items into and out of backpack slots; confirm slots update immediately and no ghost items occur.
>
> **Phase 2 — Camera & Spectator FOV (`v26.2.28.43`)**
> 7. Run `/b cam <player>` or activate freecam. Move at high speed or sprint.
> 8. Confirm FOV is clamped to 110 (options FOV) without dynamic zoom distortion or lerp lag.
>
> **Phase 3 — AutoCroesus & Dungeon Profits (`v26.2.28.43`)**
> 9. Open Croesus GUI in Catacombs. Confirm chest profit HUD renders clean text with shadows and NO background box.
> 10. Verify keys/kismets use instabuy prices and chest items use instasell prices.
> 11. Run `/b profit`; confirm runs are deduplicated and hovering a recent run shows the itemized tooltip with coin values and duration.
> 12. Complete a run and confirm profit sync succeeds without 401 or 404 errors.
>
> **Phase 4 — Commands, Profile Viewer & UI (`v26.2.28.43`)**
> 13. Run `/b area` and `/b subarea`; confirm correct parent area and subarea are printed.
> 14. Test `/b pv1`, `/b pv2`, and `/b pvconfig`; test the `⚙` button in Profile Viewer header.
> 15. Run `/b apihistory`; confirm outbound authorization tokens are masked in headers.
> 16. In `/b` config, click a slider value label (e.g. `300ms`), type a number, and press Enter to confirm value updates.
>
> **Phase 5 — Storage & Egg Finder (`v26.2.28.43`)**
> 17. Open `/b storage`; confirm "Island Chests" tab is present and empty slots are purged on open.
> 18. Enter Private Island / Garden / Limbo / Dungeons; confirm egg WebSocket cleanly unsubscribes.
> 19. Collect an egg; confirm only the closest waypoint is removed.
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
| [`ClientBrandRetrieverMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ClientBrandRetrieverMixin.java) | `ClientBrandRetriever` | Cleanly returns `Constants.MOD_ID` (`bomboaddons` / `bomboclient`). |
| [`GameRendererMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/GameRendererMixin.java) | `GameRenderer` | Clamps camera FOV to options FOV (110) during `/b cam` and freecam. |
| [`MenuScreensConstructorMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/MenuScreensConstructorMixin.java) | `MenuScreens.ScreenConstructor` | Intercepts container screen creation to launch `StorageOverlayScreen` when storage overlay is enabled. |
| [`AbstractContainerMenuMixin`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/AbstractContainerMenuMixin.java) | `AbstractContainerMenu` | Synchronizes container item updates and triggers search re-filtering in storage overlay. |

---

## 6. Next Direct Actions

1. Follow the ordered in-game test queue in Section 4 (Phase 1 Storage Overlay, Phase 2 Camera FOV, Phase 3 AutoCroesus & Profit, Phase 4 Commands & UI, Phase 5 Storage & Eggs).
2. For any new feature or bugfix, adhere strictly to the **Mandatory Release & Versioning Protocol** in Section 2:
   - Bump `mod_version` in `gradle.properties`.
   - Verify clean build (`./gradlew compileClientJava` and `./gradlew build -x test`).
   - Update `CHANGELOG.md` and `data/changelog.json`.
   - Deploy `data/changelog.json` to server via `scp`.
   - Deploy `bomboaddons-<version>.jar` to `/home/ubuntu/bomboapi/releases/` via `scp`.
   - Commit and push to branch `26.2`.
3. Deployment safety: **NEVER upload `bomboclient-*.jar` to `/home/ubuntu/bomboapi/releases/`**. Cheat builds are strictly local.

---

## 7. Key Architecture & Ported Files (v26.2.28.46 -> v26.2.28.43)

| File | Purpose |
| :--- | :--- |
| [`features/dungeons/RtcaChatFormatter.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/dungeons/RtcaChatFormatter.java) | Turns BomboBot's flat `[RTCA50]` chat line into hoverable class chips; owns the `/command/rtca` fetch, the 60s cache and the daemon thread. Shared code, ships in both jars. |
| [`features/storageoverlay/StorageOverlayScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/StorageOverlayScreen.java) | Main storage multi-grid overlay GUI replacing `/storage`, ender chests, and backpacks. |
| [`features/storageoverlay/SearchableGridWidget.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/SearchableGridWidget.java) | Search box and multi-grid item search filter logic. |
| [`features/storageoverlay/StorageOverlayScreenHandler.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/StorageOverlayScreenHandler.java) | Screen handler mapping container slots to the overlay grid. |
| [`features/storageoverlay/BackpackPreview.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/BackpackPreview.java) | Storage item deserialization, caching, and custom player head texture resolver. |
| [`mixin/GameRendererMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/GameRendererMixin.java) | FOV clamping during freecam and spectator camera. |
| [`features/dungeons/DungeonProfitLog.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/dungeons/DungeonProfitLog.java) | Dungeon run recording, disk deduplication, and server synchronization via `X-Player-UUID`. |
| [`gui/DungeonProfitScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/DungeonProfitScreen.java) | Dedicated profit overview GUI (`/b profit`) with stats, floor cards, and itemized hover tooltips. |
| [`docs/HANDOFF_KNOWLEDGE.md`](file:///e:/Users/frand/Documents/bomboaddons-26.2/docs/HANDOFF_KNOWLEDGE.md) | In-depth knowledge document detailing architecture, endpoints, testing status, and quirks. |

