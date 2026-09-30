# BomboAddons Changelog
 
## [26.2.28.59] - 2026-10-01 (Beta)

### Hypixel Alpha Open & Capacity Alert System (Client & Server)
- **Server Alert Logic Fix (`bombot`):** Fixed server watcher logic where Alpha opening alerts were dropped if admins/testers were online before public cap was raised from 0 to 50. Replaced restrictive conditions with strict public capacity detection (`prevMax === 0` to `maxPlayers > 0`).
- **Reduced Alert Cooldown:** Decreased repeat open alert cooldown from 60 minutes to 10 minutes so quick server restarts during testing cycles are properly announced.
- **In-Game Client Alerts (`AlphaTrackerHud`):** Added in-game chat alert with clickable `§8[§a§lJOIN§8]` (`/server alpha`) button, audible level-up chime, and on-screen title banner whenever Hypixel Alpha opens or expands public capacity.
- **Background Client Polling:** Polling now runs every 30 seconds in the background regardless of whether the HUD element is drawn or hidden.
- **Configurable Alert Options:** Added `Alpha Open Alert`, `Alpha Open Sound`, and `Alpha Open Title` under `/b` configuration in HUD settings.
- **Status Commands:** Added `/b alpha` and standalone `/alpha` commands to inspect Alpha player count, cap, and open status directly from chat.

## [26.2.28.58] - 2026-09-30 (Beta)

### Watchdog Shutdown Crash Elimination (`NTSTATUS 0xFFFFFFF8`)
- **Clean Background Thread Shutdown:** Added `DiscordIpcManager.stop()` to close named pipes and unblock `readFully` calls, updated `SpotifyManager.stop()` to forcibly terminate any running PowerShell GSMTC process, and updated `ScreenshareManager.stopStreaming()` with daemon cleanup threads.
- **Client Stopping & JVM Shutdown Hook:** Registered background manager shutdowns on both Fabric's `ClientLifecycleEvents.CLIENT_STOPPING` and `Runtime.getRuntime().addShutdownHook(...)`. Prevents lingering background threads from tripping Mojang's `ClientShutdownWatchdog` on game exit (`Client shutdown from post-main`).

### Discord Image Link & Query Parameter Preservation
- **Preserved `&` in URLs:** Fixed Discord bridge chat payload formatting (`IRCClient`) to format color codes and chroma strictly outside URLs (`formatColorsOutsideUrls`). URLs containing query parameters (e.g. `&ex=`, `&is=`, `&hm=`) are no longer corrupted into Minecraft color codes (`§e`, etc.).
- **Clickable Discord CDN Links:** Fixed `IRCClient.formatWithLinks` to extract URLs and `[SHOW:...]` tokens before running chroma processors, and restored ampersands so `URI.create(...)` cleanly produces clickable `ClickEvent.OpenUrl` links in chat.
- **Image Hover Preview Restoration:** Fixed `ChatImagePreview` (`cleanUrl` and `extractImageUrl`) to restore `§` to `&` before parsing, eliminating 403 Forbidden / 404 Not Found errors caused by stripped `§e` in signed Discord CDN links (`&ex=...`).

## [26.2.28.57] - 2026-09-30 (Beta)

### High-Speed Screenshare Streaming (25-30 FPS MJPEG) & Diagnostics
- **High-Speed MJPEG Stream:** Upgraded live capture loop to 25-30 FPS MJPEG streaming with ultra-low latency (<50ms) to `/api/screenshare/stream/:user` and updated web dashboard viewer.
- **Multi-Monitor Display Clamping:** Added virtual screen bounds clamping in `ScreenshareManager` to prevent `IllegalArgumentException` and black screen captures on multi-monitor setups.
- **Telemetry & `/ss debug` Command:** Added comprehensive `/ss debug` and `/b ss debug` diagnostic dumper reporting active streaming status, target, quality, rolling FPS, bitrate, latency, and frame counters.
- **Bridge Chat Spam Eliminated:** Silenced bridge mapping log spam (`[Bridge Mapping] Registered: bomboclas -> IGN fran938`) on the IRC server bot.

### Discord Desktop IPC Concurrency & Authorization Fixes
- **Eliminated Repeated OAuth Authorization Prompt:** Added `hasAuthorizedThisSession` guard preventing repeated "Visual Studio Code wants to access your Discord account" permission popups.
- **Named Pipe Concurrency (`PIPE_LOCK`):** Synchronized pipe read and write operations with `PIPE_LOCK`, eliminating pipe stalls and packet lockups.
- **Brigadier Command Registration:** Registered `/b discord` along with `debug`, `sync`, and `auth` subcommands, resolving `Incorrect argument for command at position 10` errors.
- **Diagnostic Dump:** `/b discord debug` now outputs full pipe state, handshake history, and the last 15 raw RPC packets.

### Interactive Version Catalog GUI (`/b update versions`)
- **Version Manager Screen (`UpdateVersionsScreen`):** Built interactive GUI accessible via `/b update versions`, `/b versions`, or `/b version versions`, listing all MC 26.2 releases in descending order with `[CURRENT]`, `[FULL]`, and `[BETA]` badges.
- **1-Click Version Switcher:** Switch or downgrade to any released build with 1-click installation and automatic cleanup of previous jars on restart.
- **Channel Filters & Strict MC 26.2 Scope:** Filter catalog by All, Full Only, or Betas Only. Strictly prevents downloading releases for different Minecraft versions (e.g. 26.1).
- **API Content-Type Fix:** Added `Accept: application/json` headers to updater requests so the server returns the structured catalog rather than the HTML webpage.

### Lyrics Engine Polish & Custom Editor Hotkeys (`/b lyrics`)
- **Keyboard-Driven Custom Lyrics Editor:** Implemented full custom lyrics editor with cursor positioning, click-to-edit, text selection, and keyboard hotkeys (Ctrl+A, Ctrl+C, Ctrl+V, Ctrl+X, Enter, Backspace, Delete).
- **GUI Scissor Clipping Fix:** Corrected `GuiGraphicsExtractor.enableScissor` coordinate calculation `(x1, y1, x1+w, y1+h)`, resolving cut-off provider badges in candidate modal and half-hidden text in raw modal.
- **Monotonic Millisecond Clock & Line Oscillation Fix:** Enforced monotonic millisecond progress tracking (`monotonicProgressMs`) and 250ms poller queries, ensuring smooth 1-second ticks (`0:35, 0:36, 0:37...`) and completely stopping active line oscillation jitter.
- **Accurate Sync Badges:** Fixed provider parsing so line-synced Paxsenix tracks are correctly badged as `[Line-Synced]` rather than `[Word-Synced]`.

### Restored Config Options & Error Tooltips
- **Restored Old Config Suite:** Restored full Diana Lootshare alert suite (alerts, user chat, command, sound, title), Item Tooltip Prices (Lowest BIN, Craft Cost, NPC Price), Dojo utilities, Carnival, Sphinx macro, Hollow Wand, Lasso, Trevor, Daily Reward, Camera settings, and Frozen Blaze AFK warning to `/b` config.
- **Playtime HTTP Status Hover Tooltips:** Added interactive hover tooltips explaining HTTP 429 rate limits, 401/403 auth errors, 500 server errors, and network connection diagnostics.

## [26.2.28.56] - 2026-09-29 (Beta)

### Live Screenshare Streaming & Web Dashboard
- **Web Screenshare Dashboard (`https://bombo.dpdns.org/screenshare`):** Built a high-performance web dashboard displaying all active in-game streamers with their Minecraft heads/skins, live FPS/latency statistics, stream resolution, and an HD stream viewer with fullscreen mode. Supports auto-selecting streamers via URL parameter (e.g. `?user=<ign>`).
- **Live Frame Streaming (`ScreenshareManager`):** In-game clients now stream compressed JPEG video frames (~8-10 FPS, 640x360) directly to the server API (`/api/screenshare/frame`) whenever screensharing is active or accepted, bypassing render-distance limitations.
- **Clickable Chat Stream Links:** When accepting or starting a screenshare (`/ss <player>`), chat outputs an interactive, clickable link to watch live on the web (`https://bombo.dpdns.org/screenshare?user=<target>`). If the spectated player is outside local chunk render distance, spectator camera cleanly redirects you to the web live stream.
- **Manual Streaming Commands:** Added `/ss stream` / `/ss share` to start sharing screen anytime, and `/ss stop` to stop streaming.

### Synchronized Lyrics Overhaul (`/b lyrics` & `/lyrics`)
- **Interactive Delay Slider & Custom Number Typing:** Replaced the step buttons with an interactive draggable delay slider (`-5000ms` to `+5000ms`) and a click-to-type number box where users can type any exact millisecond offset (e.g. `-250`, `1200`) and hit Enter to set. Includes a quick `[↺]` reset button.
- **Unformatted & Concatenated LRC Splitting:** Enhanced LRC parser with regex lookahead `(?=\\[\\d{1,2}:\\d{2})|\\r?\\n` to separate lines even when returned as a single unformatted line without newlines.
- **Word-for-Word Syllable Sync:** Supports both `<mm:ss.xxx>word` format and `<word:start:end>` (BetterLyrics / Musixmatch) formats with exact word boundaries.
- **Spotify-Style Full Line Highlights:** For songs without word-by-word timestamps, the active line is highlighted in bold bright white with a background pill (exactly like Spotify), eliminating fake word-by-word jumping.
- **Custom Lyrics Editor:** In the `[Raw / Edit]` modal, users can switch to Edit Custom mode to type or paste custom lyrics from clipboard, and click `[Apply Lyrics]` to immediately activate them.
- **Vivi Music Candidate Selector Modal:** Added a multi-candidate selection modal displaying all lyric versions collected across **PAXSENIX**, **LRCLIB**, **BETTERLYRICS**, **KUGOU**, **UNISON**, and **YOULYPLUS**, showing preview snippets, provider badges, and sync tags (`[Word-Synced]`, `[Line-Synced]`, `[Plain]`).

### Discord Desktop IPC Voice HUD
- **Authorization Flow & Voice Polling:** Added automatic `AUTHORIZE` approval handling for Discord Desktop IPC voice scopes (`rpc.voice.read`), and added a 4-second background poll so joining Discord voice channels after launching Minecraft automatically syncs the HUD without reconnecting.
- **Force Sync Command:** Added `/b discord sync` / `/ss discord sync` to trigger an instant re-query of the active voice channel.

## [26.2.28.55] - 2026-09-28 (Beta)

### Screensharing & Social
- **Screenshare Leak Elimination & Delivery Confirmation (`/ss <player>`):** Patched remote IRC bot (`bombot`) to prevent `[SS_` protocol messages from leaking into chat or Discord. Added live IRC online status checks and instant receipt acknowledgment (`[SS_RCVD]`) so senders receive immediate feedback when targets receive requests.
- **Discord Desktop IPC Diagnostics & Handshake:** Updated Discord Client ID to `383226320970055681` (standard desktop IPC), expanded named pipes probe across `\\.\pipe\discord-ipc-0`..`9`, and built `/b discord debug` / `/ss discord debug` diagnostic dumper.

### Synchronized Lyrics (`/b lyrics`)
- **Word-for-Word Paxsenix Integration:** Integrated iTunes Search metadata API with Paxsenix Apple Music syllable sync API, parsing exact word-by-word timestamps and background vocals.
- **Line Wobble & Monotonic Highlights Fix:** Stabilized karaoke word slot widths to eliminate line shifting as words become active, and enforced monotonic forward progress so word highlights never jump backwards.
- **Custom Delay Offset Adder:** Added `[-100ms]` and `[+100ms]` offset buttons on the lyrics GUI with config persistence (`lyricsOffsetMs`) and quick reset.
- **Secondary Vocals Rendering:** Secondary / dual singer background vocals now render underneath the primary line in a subtle dimmer font.
- **Modal ESC Key Handling:** Pressing ESC inside the raw lyrics viewer now closes only the modal without closing the lyrics screen.

### Spotify HUD Overlay & Config
- **Album Cover Artwork Display:** Asynchronously downloads and registers 256x256 album covers via iTunes API, rendering album art directly on `SpotifyHud` (toggleable via `spotifyHudShowAlbumArt`).
- **Interactive Color Swatches in Config:** Converted Spotify hex color options to `ConfigItem.color` with visible color swatch preview boxes next to buttons in `/b` config.

### Storage & Containers
- **Storage Overlay Slot Indexing Fix:** Preserved slot indices in `StorageOverlayScreenHandler.InactiveSlot` (`this.index = index`), preventing slot index corruption and enabling reliable overlay display across `/storage`, ender chests, and backpacks.

### Spectator Camera
- **FOV Clamping:** Clamped camera FOV to base FOV (capped at 110.0) whenever `SpectatorCamManager.isActive()` or `mc.getCameraEntity() != mc.player` in `GameRendererMixin`, stopping disorienting fish-eye distortions.

### Performance Profiler (`/b perf`)
- **CPU % & Estimated Memory Metrics:** Added CPU usage percentage and estimated memory footprint per feature to `PerformanceProfiler.Snapshot` and `PerformanceScreen`.

## [26.2.28.54] - 2026-09-28 (Beta)

### Screensharing & Social
- **Screensharing Protocol Delimiter Fix (`/ss <player>`):** Enhanced IRC command parser to split protocol messages on both delimiter (`\u0002`) and whitespace (`\\s+`), preventing request payloads from leaking into IRC chat and ensuring the recipient reliably receives `[ACCEPT]` and `[DENY]` buttons.

### Synchronized Lyrics (`/b lyrics` & `/lyrics`)
- **Word-for-Word Spacing & Font Metrics:** Fixed word merging (`Soloquierebailar`) and active highlight overlap by measuring exact bold font width and adding explicit inter-word spacing.
- **Smooth Millisecond Timer Interpolation:** Replaced jumping seconds with smooth continuous millisecond interpolation based on playback state timestamp.
- **Raw / Edit Lyrics Inspection Modal:** Added `[Raw / Edit Lyrics]` button displaying raw API/LRC responses with scrollable view and one-click clipboard copy.
- **Multi-Provider Lyric Selector:** Added cycling between **LRCLIB**, **PAXSENIX**, **UNISON**, and **YOULYPLUS** with persistent configuration.
- **Direct Spotify Desktop App Search:** Clicking song title or artist launches directly in the **Spotify Desktop App** (`spotify:search:...`) via Windows URI protocol.
- **Official Spotify Branding:** Replaced custom icon with official, crisp 64x64 Spotify logo.

### Spotify HUD Overlay
- **Configurable Theme Colors:** Added customizable hex colors (Background, Border, Title, Artist, Accent/Controls, Progress Bar Background) to `/b` config under Spotify category.

### Storage & Containers
- **Storage Overlay Main Thread Launch:** Switched container interception to `setScreenAndShow` on the main client thread in `MenuScreensConstructorMixin`, resolving issues where the multi-grid overlay failed to initialize.

### Spectator Camera
- **FOV & Mouse Look Smoothing:** Excluded spectator camera from aggressive FOV clamping so FOV remains natural, and applied a 0.5x smooth sensitivity factor to mouse turning.

### Performance Profiler & Engine (`/b perf`)
- **Entity Scanner Cache Sentinels:** Optimized `HighlightESP` entity scan using an `EMPTY_INFO` cache sentinel, reducing scan overhead to <0.01 ms even with hundreds of entities in the area.
- **JVM Heap & Thread Metrics:** Added real-time JVM Heap memory usage (`Used MB / Allocated MB / Max MB`) and active thread counts directly to the profiler header.
- **Subsystem Profiling Scopes:** Added profiling scopes for Spotify HUD rendering, Lyrics screen rendering, and Storage Overlay rendering.

### CI/CD & Documentation
- **GitHub Actions Automated Builds:** Added `.github/workflows/build.yml` to automatically compile both mod flavors on push and publish downloadable `.jar` artifacts to Actions runs.
- **Documentation:** Added comprehensive `README.md` for the `26.2` branch.

## [26.2.28.53] - 2026-09-28 (Beta)

### Screensharing & Social
- **Screensharing Protocol Delivery (`/ss <player>`):** Switched in-game spectator request transport to `PRIVMSG` over IRC, ensuring spectator invites reliably deliver to the target player with interactive `[ACCEPT]` and `[DENY]` buttons.
- **Discord HUD Command Separation:** Un-hijacked `/ss` and `/b ss` from Discord IPC to screensharing; added `/b discord` for Discord Voice HUD.

### Synchronized Lyrics (`/b lyrics` & `/lyrics`)
- **Word-for-Word & Line Synced Lyrics:** Built a rich lyrics GUI powered by LRCLIB API (inspired by `vivi-music`). Features real-time karaoke tracking with glowing cyan active words, smooth auto-scrolling, manual scrolling with a "Jump to Playing" button, and web search links for song and artist.

### Spotify HUD Overlay
- **Visual Redesign:** Updated HUD card to a modern dark plum background (`#1E1324`), official Spotify icon, neon cyan `#00A4DC` controls (`|◀  ⏸/▶  ▶|`), and styled progress bar.
- **Accurate Playback & Pause Persistence:** Resolved timer desync and progress resetting to 0:00 on pause using Windows GSMTC media transport tracking.
- **Clickable Links & Inventory Visibility:** Click song title to open Spotify track search and artist to open artist search; HUD stays visible when opening inventory or containers (`AbstractContainerScreen`).

### Bug Fixes & Improvements
- **Storage Overlay Crash Fix:** Replaced `SlotAccessor` mixin with direct Unsafe memory offsets for slot positioning, completely preventing classloading crashes when opening storage menus.
- **Spectator Camera & Hands:** Injected into `LevelExtractor` to prevent Minecraft from culling `LocalPlayer` from the world while spectating, and suppressed client hand rendering in first-person spectator.
- **Hoppity Phone Auto-Pickup:** Gated auto-pickup strictly to callers matching "Hoppity", preventing accidental pickup of Vincent or other NPC calls.
- **Changelog Colors:** Fixed changelog text color turning white after obfuscation reset by resetting to `§r§7`.

## [26.2.28.52] - 2026-09-28 (Beta)

### Screensharing & Social
- **In-Game Screensharing (`/ss <player>`):** Added mod-to-mod in-game spectator requests over IRC. The recipient receives clickable `[ACCEPT]` and `[DENY]` chat buttons. Includes an auto-accept whitelist (`/ss whitelist <player>`, `/ss remove <player>`, `/ss list`) to instantly accept requests from trusted players.
- **Clean Disconnects (`/ss stop`):** Cleanly exits screenshare/spectator mode and informs both players.
- **Discord IPC Permission Prevention:** Gated Discord IPC on `discordHudEnabled` and updated client ID so Discord never prompts for permissions on Minecraft startup when disabled.

### Spotify HUD Overlay
- **Interactive Music HUD:** Displays track title, artist, progress timer (`min:sec`), and clickable playback controls (`⏮`, `⏯`, `⏭`) driven by Windows media key emulation and background process monitoring.
- **Config & HUD Movement:** Full toggles, scaling, and dragging support under `/b` Spotify category and `/b huds`.

### Storage & Overlay
- **Storage Overlay Launch Reliability:** Switched screen opening to `setScreenAndShow` in `ClientPacketListenerMixin.java`, ensuring the multi-grid storage overlay reliably opens on `/storage`, backpacks, and ender chests.

### Spectator Camera & HUD
- **Spectator First-Person Arm & Item:** First-person spectator cleanly renders the spectated player's arm skin and held item with full bobbing/attack animations instead of the client player's hand.
- **100 Hearts Fix:** Eliminated the multi-row 100+ hearts health bar bug when spectating players with high max HP on Hypixel.
- **Spectated Item Highlight:** Displays the spectated player's held item name and rarity color above the hotbar.
- **Look Smoothing:** Eliminated camera jitter and stutter by cleaning mouse delta handling in `SpectatorCamManager.java`.

### Polish
- **No Obfuscate Label:** Formatted `No Obfuscate (strip §ka§r)` so the 'a' character scrambles and the closing parenthesis remains clean.

## [26.2.28.51] - 2026-09-28 (Beta)

### Storage & Overlay
- **Storage Overlay Opening Fix:** Fixed an issue where the custom storage overlay screen would not open upon accessing storage, ender chests, or backpacks.
- **Anti-Jitter for Recombobulated Items:** Completely eliminated horizontal text shaking on recombobulated items when No Obfuscate is enabled.

### Discord & Voice HUD
- **Discord Voice Call HUD (/ss or /b ss):** Added a Discord Voice Call HUD showing current call members, speaking indicators, and live screenshares, with quick status via `/ss` and `/b ss`.
- **Config & HUD Customization:** Added a dedicated Discord category in `/b` config and added HUD positioning/scaling support in `/b huds`.

### Spectator Camera & Player Rendering
- **Dynamic Camera Look:** The spectator camera now smoothly tracks the spectated player as they look around.
- **First-Person Hands & Items:** Spectating in first-person now correctly displays the spectated player's arm skin and currently held item.
- **World Body Visibility:** Your own player model and armor layers remain visible in the world during spectator camera and freecam.

### Mayor & Polish
- **Clean Mayor Formatting:** Cleaned up mayor chat messages by removing the broken temple icon and underlines from names and perks while keeping full hover tooltips.
- **Version Reporting:** Fixed `/b version` sometimes reporting "unknown" in development or custom configurations.

## [26.2.28.50] - 2026-09-28 (Beta)

### Storage & GUI
- **Storage Overlay Interception Concrete Fix:** Shifted injection from `ScreenConstructor.fromPacket` interface to concrete static method `MenuScreens.create` in `MenuScreensConstructorMixin.java`, cleanly intercepting container opening packets and opening `StorageOverlayScreen` across all ender chests and backpacks.
- **Stable Obfuscated Glyph Advance (§k Jitter Fix):** Added `FontMixin.java` to stabilize obfuscated character advances to a fixed width matching the base codepoint, completely eliminating text shaking/jittering on rarity-upgraded (recombobulated) items.
- **No Obfuscate Config Label Fix:** Added `§r` reset code in `ConfigRegistry.java` to prevent the closing parenthesis in `No Obfuscate (strip §k§r)` from being scrambled.

### Commands & Subareas
- **Brigadier `/b mayor` Registration:** Registered `mayor` in Brigadier command tree in `BomboaddonsClient.java` and `CommandMixin.java`, resolving `Incorrect argument for command at position 2: b`.
- **Combined Area & Subarea Display:** Updated `/b area` to include subarea alongside parent area (e.g. `Current Area: Hub (Village)`).

### Spectator & Player Body Rendering
- **Player Body & Layer Visibility:** Added `isEntityVisible` injection in `LevelExtractorMixin.java` and added `AvatarRendererMixin.java` to guarantee the player's body and all armor/skin layers render cleanly in the world while spectating.

### Hoppity & Phone Calls
- **Hoppity Call Auto-Pickup:** Implemented automatic answering of incoming Hoppity phone calls on the first buzz (`BUZZ... [PICK UP]`) when `autoHoppityCalls` is enabled.

## [26.2.28.49] - 2026-09-28 (Beta)

### Storage & GUI
- **Storage Overlay Interception Port:** Updated `MenuScreensConstructorMixin` to target `MenuScreens.ScreenConstructor.fromPacket` matching Skyblocker's port architecture to reliably build and display the searchable multi-grid `StorageOverlayScreen`.
- **Keypad Keybind Fix:** Removed keypad keys from modifier combo prefixes in `ConfigCustomWidgets.isStarterKey`, enabling direct binding of Keypad 0-9, Keypad arrows, and keypad operators in controls.

### Mayor & RTCA Live Integration
- **Interactive Mayor & Minister Tooltips:** Intercepts `[Mayor]` chat lines (and added `/b mayor`) with interactive chips. Hovering the Mayor displays the exact Hypixel calendar GUI tooltip layout with perks list and election notes; hovering individual perks shows their description; hovering the minister displays their name and active minister perk.
- **Live Mayor RTCA Integration:** Server and client now integrate live election mayor data into RTCA calculations; displays `• Mayor: Diaz (+0%)` under Boost Breakdown and removes `mayor` from the uncounted list.

### Spectator & Camera Fixes
- **Spectator Camera Entity Decoupling:** Added `SpectatorCamManager` to decouple mouse rotation during `/b cam` spectator mode from the entity's physical body/head rotation; the spectated player's pose remains completely stable.
- **Player World Rendering while Spectating:** Added `LevelExtractorMixin` redirecting `Camera.entity()` in `extractVisibleEntities` to ensure `LocalPlayer` is not discarded and renders properly in the world during spectator camera.

### Commands, Scoreboard & Network
- **Dungeon Quick Join Commands:** Updated `/f1`-`/f7`, `/m1`-`/m7`, and `/e` to dispatch uppercase Hypixel instance names (`CATACOMBS_FLOOR_ONE`..`SEVEN`, `MASTER_CATACOMBS_FLOOR_ONE`..`SEVEN`, `CATACOMBS_ENTRANCE`) with chat feedback.
- **Scoreboard Font Icon Subarea Parsing:** Enhanced `SkyblockUtils.parseSubAreaFromLines` to detect Private Use Area glyphs (`\uE000-\uF8FF`) before stripping formatting, correctly parsing subareas like `-  Village`.
- **IRC Plaintext IP Replaced:** Replaced plaintext server IP `chat.bombo.dpdns.org` in `IRCClient.java` with domain `chat.bombo.dpdns.org`.

## [26.2.28.48] - 2026-09-28 (Beta)

### RTCA & Backend
- **Live Boost Detection:** The server (`bomboapi`) now dynamically parses player inventory data to detect the highest Hecatomb helmet equipped or in wardrobe (`member.loadout.armor`, `inv_armor`, `wardrobe_contents`), Scarf accessory (`SCARF_GRIMOIRE` +6%, `SCARF_THESIS` +4%, `SCARF_STUDIES` +2%), and Catacombs Graduate shards (`catacombs_graduate` milestone levels 1..10 = 2%..20%).
- **Interactive Chat In-Place Refresh:** `RtcaChatFormatter` updates the pending loading message in `mc.gui.hud.getChat()` in-place and calls `refreshTrimmedMessages()`, eliminating duplicate chat lines.
- **Discord Bot RTCA Target:** Updated `bombot` to display the actual target level (e.g. `CA 50`) in status chips.

### Storage & GUI
- **Storage Overlay Activation & Discovery:** Fixed container title recognition by stripping formatting codes, supporting bare `Ender Chest`, Spanish `Cofre de Ender`, `storage (1/2)`, and backpacks without `#`. Fixed `player.containerMenu` assignment order so container packets sync cleanly without screen reset. Added dynamic re-discovery of backpacks on container open.
- **Backpack Preview Over Inventory Buttons:** Ensured `StoragePreviewManager.renderHoverPreview` renders on top of custom `InventoryButtonManager` buttons and item lists in `InventoryScreen`.
- **IDE Dev Client Clipboard Fix:** Added headless AWT prevention at mod initialization, GLFW modifier key checks in `EditBoxMixin`, and retry loops for Windows system clipboard locks in `ClipboardImageUploader`.

### Dungeons, Camera & Commands
- **Spectator / Camera Mouse Rotation & Visibility:** Enabled mouse rotation when camera entity is set to another player (`/b cam`) and ensured player body remains rendered.
- **Dungeon Quick Join Commands:** Updated `/f1`-`/f7`, `/m1`-`/m7`, and `/e` to use `joininstance catacombs_floor_<name>` and `catacombs_entrance`.
- **Starred Mob & F4/M4 Thorn ESP:** Expanded bounding box for starred mob ArmorStand tags, and added arena coordinate detection for F4/M4 Thorn etherwarp helper at `(27, 81, 18)`.
- **Scoreboard Subarea Parsing:** Enhanced `SkyblockUtils.parseSubAreaFromLines` to recognize dash-prefixed scoreboard lines (`- Your Island`) for `/b subarea` and `/b area`.

## [26.2.28.47] - 2026-09-28 (Beta)

### Chat & Input
- **Chat Imgur Delete / Supr Fix:** Fixed an issue where selecting text in the chat input and pressing `Delete` (Supr) or `Backspace` would trigger an Imgur upload and paste `$imgur` if any image was present in the clipboard. Removed the phantom `insertText("")` check in `EditBoxMixin` and ensured clipboard image pasting requires an active `Ctrl + V` press in a focused edit box.

### Documentation
- **Handoff Docs Updated:** `AGENTS.md` and `docs/HANDOFF_KNOWLEDGE.md` now cover v26.2.28.45/46 and carry an explicit Tested vs. Untested split.
- **New Priority 0 Test Queue:** in-game verification of the RTCA hover chips (replacement not duplication, exact levels, honest +10% boost with the "Not counted" line, fractional CA, the `loading class details...` first-hover placeholder and the 60s cache).
- **Server Knowledge Captured:** the `exactLevel` = `level + into/need` fix (because `classProgress().into` is *remaining* XP), the self-referential `c.boost.label` crash that 502'd `/api/v1/dungeons`, the `xpForNextLevel`-is-a-total trap, the two false alarms not to "fix" (stale `cold_efficiency: 1` snapshot; the `assumeBestCase` code-path difference that looked like caching), and the MC 26.2 API notes (`Style.EMPTY`, no `ChatFormatting.getChar()`).
- **Quirks Recorded:** the `bombofabric` -> `bomboFabric` remote rename, the fact that `docs/CODE_STYLE.md` does not exist, and the write-locally-then-`scp` rule for server patches.
- **Known Outstanding:** top-level `classAverage` in `/api/v1/dungeons/class-average` still returns the integer `48` instead of the exact `48.47`.

## [26.2.28.46] - 2026-09-27 (Beta)

### RTCA Boost Honesty Fix
- **No More Invented Boosts:** RTCA previously defaulted three boosts it cannot actually see to their *maximum* — Hecatomb X (+4%), Grimoire scarf (+6%) and Catacombs Graduate (+20%) — inflating every class to a flat +40%. Real player lookups now count those as **0** and list them as "not counted".
- **Verified perks only:** The only boost reported for a player is what Hypixel actually exposes (the five class essence perks, read from `player_data.perks`). For bomboclas that is +10% per class, not +40%.
- **Impact:** M7 XP/run dropped 420,000 -> 330,000 and the CA50 estimate rose 1,070 -> 1,361 runs, which is the honest figure without unverifiable gear.
- **Tooltip Honesty:** Hover tooltips now end with a "Not counted: Hecatomb, Scarf accessory, Catacombs Graduate, Mayor" line explaining those are unknown rather than assumed.
- **Calculator Unchanged:** `/dungeons` is an explicit "what if" tool and still opens with best-case defaults (Hecatomb X, Grimoire, Graduate 20%); it now opts in via `assumeBestCase` rather than being the silent default everywhere.

## [26.2.28.45] - 2026-09-27 (Beta)

### RTCA Interactive Chat
- **Hoverable Class Chips:** BomboBot's flat `[RTCA50]` chat line is intercepted in `ChatMixin` and rebuilt from `/command/rtca/<player>` as `MutableComponent` text. Each class becomes a coloured, hoverable chip (gold when maxed).
- **Per-Class Tooltips:** Hovering a class shows its exact level, runs to max that class, XP remaining to the next level, XP per run, and the full boost breakdown (essence perk tier, Hecatomb, scarf accessory, Graduate, Global, Mayor multiplier).
- **Exact Class Average:** Class levels and the class average now render as exact fractional values (`Archer 48.92`, `CA 48.47`) instead of truncating to integers.
- **Clean Chat Line:** Dropped the trailing `|  | Boost: Archer: +40% | ...` block that made the line unreadable; all boost detail moved into the tooltips.
- **Caching:** API responses are cached per player for 60s, so repeated `!rtca` prints cost one request.

### Server (`bomboapi`)
- `/command/rtca` and `/command/dungeons` now expose `exactLevel`, `xpToNextLevel` (remaining, not total), `xpPerRun`, `runsToMax` and a per-class `boost` object with a human-readable label.
- Added `classAverageExact` so consumers can show the true fractional class average.

## [26.2.28.44] - 2026-09-25 (Beta)

### Storage Overlay
- **Skyblocker Storage Overlay Port:** Complete port of Skyblocker's multi-grid storage overlay, replacing `/storage`, ender chest, and backpack menus with a searchable, compact multi-grid interface.
- **Multi-Grid Item Search:** Live item search with instant filtering across all ender chests and backpacks simultaneously.
- **Configurable Layout & Settings:** Configurable storages per row, backpack columns, remember search query, remember last opened storage, and mouse cursor position preservation.
- **Live Inventory Synchronization:** Dynamic slot updates and real-time container item caching with full NBT, base64, and custom player head texture support.

## [26.2.28.43] - 2026-09-24 (Beta)

### Camera & Spectator
- **Camera & Spectator FOV Clamping:** Clamped camera FOV to options FOV (110) during `/b cam` and freecam via `GameRendererMixin`, eliminating speed FOV distortion and dynamic lerp lag.

### Auto Croesus & Profit Tracking
- **Insta Buy & Insta Sell Pricing:** Uses instabuy for keys/kismets and instasell for items; resolves live Bazaar prices for Bank, No Pain No Gain, and Jerry books without hardcoded fallbacks.
- **Removed Profit Threshold Gate:** Claims all profitable and free chests unconditionally.
- **Clean Profit HUD:** Redesigned chest profit HUD to clean text with shadows and no background box.
- **Run Deduplication & Itemized Tooltip:** Deduplicates runs on disk/load and renders detailed itemized hover tooltips with values and duration in `/b profit`.
- **Decoupled Profit Sync Auth:** Profit syncing authenticates via `X-Player-UUID` and Bombo API key, completely decoupled from egg auth.

### Commands & Profile Viewer
- **Command Separation:** `/b area` displays area and `/b subarea` displays subarea.
- **Profile Viewer Shortcuts & Settings:** Added `/b pv1`, `/b pv2`, and `/b pvconfig` commands; added `⚙` settings button directly to the Profile Viewer header.
- **Multi-Profile Support:** HypixelApiClient fetches all profiles via `all_profiles` and accepts `?profile=` query param.

### Storage & Eggs
- **Storage Empty Slot Purging:** Automatically purges empty slots from `storageData` on container open, fixing phantom items (e.g. Divan's Drill in Backpack 10).
- **Island Chests Tab & Localization:** Added dedicated "Island Chests" tab to `/b storage`; added Spanish and translatable container title recognition.
- **Egg Finder Area Gating & Waypoints:** Explicitly unsubscribes from egg WebSocket on Private Island, Garden, Limbo, and Dungeons; removes only the closest waypoint on collection.

### Estimated Item Value & UI
- **Discrepancy Fixes:** Added reforge stone prices, blacksmith apply cost (5M for warped, 10k–1M by rarity), etherwarp conduit + merger (15M), power scroll (3M), and transmission tuners.
- **Bazaar Mode Toggle & Clean Breakdown:** Added toggle for Insta Buy / Insta Sell / Both; reordered breakdown sections cleanly; suppressed HUD on InventoryScreen.
- **Outbound Token Masking:** `/b apihistory` masks sensitive tokens (first 4 and last 4 chars shown, middle masked) in outbound headers and displays them in tooltips.
- **Slider Numeric Entry & Brand:** Config sliders parse numeric values cleanly (e.g. typing into `300ms`); client brand cleanly reports `bomboaddons`/`bomboclient`.

## [26.2.28.42] - 2026-09-24 (Beta)

### Freecam & Camera
- **Per-frame camera interpolation:** Camera movement is now interpolated using render delta partial ticks, eliminating 20Hz tick jitter.
- **Normal FOV override:** FOV modifier is clamped to 1.0 while in freecam / `/b cam`, preventing dynamic FOV and sprint distortion.

### Flavors & Security
- **Flavor switching completely removed:** Removed "Switch Flavor" button from Config GUI, `/b update switch` command, and internal updater switching routines.
- **Brand spoofing & stealth mode removed:** Removed `ClientBrandRetrieverMixin`, `modIdHider`, `Stealth.java`, and `/b stealth`. Real brand and mod ID are now cleanly reported.

### Automation & Dungeons
- **Auto Croesus dungeon key accounting:** Chest evaluation accounts for dungeon chest keys (live Bazaar pricing or manual threshold, spent state check).
- **Dungeon Chest Profit HUD:** Displays breakdown of coin cost + key cost (`coinCost + Key (keyCost)`).
- **Dedicated Profit GUI (`/b profit`):** Added responsive screen showing overall run statistics, profit per run, kismets used, floor breakdown, and itemized recent runs.
- **Profit sync fixes:** Removed deprecated `/kuudra/profit/sync` call; `DungeonProfitLog` only posts when authenticated with Bombo token.
- **Ultimate enchantment valuation:** `/b ac stats` and AutoCroesus correctly resolve `ENCHANTMENT_ULTIMATE_*` prices.

### Auth & APIs
- **Separated Aaron and Bombo auth:** Skyblocker/Aaron token is strictly used for `hysky.de` WebSocket; Bombo token is used for `api.bombo.dpdns.org`.
- **Profile Viewer Bomboclas API:** HypixelApiClient includes `User-Agent: BomboAddons/<version>` and logs queries to `ApiHistory`.
- **API History initial scroll:** Opens scrolled to the latest entries.

### Quality of Life & Fixes
- **Chat history locraw tracking:** Automated `/locraw` queries are stamped with `"Locraw Tracker"`, preserving player-input attribution.
- **Area & subarea parsing:** Scoreboard lines strip PUA unicode characters (`\uE000`–`\uF8FF`); `/b area` displays clean `Area / Subarea`.
- **Storage chest waypoints:** Waypoints render through walls and refresh on container close.
- **Egg finder island mapping:** Removed invalid `lobby -> Hub` mapping; mapped Moonglade Marsh correctly.
- **Terminal improvements (`/b cmd`):** Forcibly terminates process tree on interrupt; added drag-to-select, right-click copy, and right-click paste.
- **Model & texture fixes:** Aspect of the Void model fallback points to vanilla shovel model; ported `PlayerHeadSpecialRendererMixin` so player heads retain skins when resource packs override base models.
- **Tooltip values decoupled:** Value additions render based on their specific config toggles rather than requiring `lowestBin`.
- **Slider & keybind entry:** Sliders accept manual text entry with coin suffixes; keybind capture listener properly resets on mouse button clicks.
- **Command auto-completion:** `/b item` provides Brigadier tab suggestions from the cached SkyBlock items list.

## [26.2.28.41] - 2026-09-24 (Beta)

### Fixed
- **Tooltip rendering no longer crashes on immutable tooltip lists.** SupercraftHelper now copies tooltip lines into a mutable `ArrayList` before adding configured lore, price, and other tooltip additions. The ItemStack tooltip mixin receives and returns that mutable list safely.

## [26.2.28.40] - 2026-09-24 (Beta)

### 1. Freecam actually usable
- **Your body stays visible.** Freecam detaches the render camera from the player, which silently made the player's own model disappear (`LevelExtractor#isEntityVisible` culls it through the entity render-distance check and the chunk-visibility check). While freecam is active the camera entity is now exempt, so you can see where you left your character.
- **F3 was lying about your position.** The position block (`XYZ`, `Block`, `Chunk`, `Facing`) is built from `Minecraft.getCameraEntity()`, so it kept printing the *player's* coordinates while you looked somewhere else entirely. It now reports the camera you are actually flying - tagged `§7(freecam camera)` so it is obvious which frame of reference you are reading.

### 2. The legit build can no longer install the cheat build
- **"Switch Flavor" button removed from General** in the `bomboaddons` build - the legit artifact must not be able to pull `bomboclient` onto a user's disk. The cheat build keeps the button (that direction is fine).
- **`/b update switch`** on the legit build now answers that this build always stays on the legit artifact instead of starting a download.

### 3. Modularisation groundwork (no user-visible change)
- Every cheat module now lives in its own source tree (`src/cheat/java`) rather than being mixed into the shared `me.bombo.bomboaddons.*` packages, and the per-build packaging guards are being moved on top of that split.
- Freecam is the first subsystem routed through the flavor SPI: the shared camera / mouse / keyboard mixins no longer name a cheat class at all, they ask `FlavorBridge` for the camera state. In the legit build every freecam hook is inert instead of absent.

## [26.2.28.39] - 2026-09-24 (Beta)

### 1. Sequences & config GUI
- Every step row now has an **`[ON]`/`[OFF]`** switch between Edit and Delete. A disabled step stays in the list (greyed, `[OFF]`) but is **skipped during playback**.
- **`TAB`** in a step field moves focus to the GUI/Container name field instead of jumping out of the builder.
- Clicking a slider's **numeric label** turns it into a text field, so exact values can be typed.
- New sliders are coin-aware (`100K`, `1.5M`), used by the Auto Croesus key/kismet thresholds, which now run **100K - 3M** instead of 5M - 500M.

### 2. Chat history survives everything
- New `unlimitedChatHistory` (off by default; when off the existing limit slider applies) and `persistHistoryAcrossServers` (on by default).
- Switching worlds, disconnecting or changing servers **no longer wipes** the buffer: a session divider is recorded instead of a clear, so previous-server chat stays readable.

### 3. Dungeons
- `/b area` now prints the real floor and phase: `F4 (clear)` / `M7 (boss)`, parsed from the `The Catacombs (F4)` scoreboard line and the `[BOSS] <name>:` chat lines. `[BOSS] The Watcher:` is excluded (blood room is not a floor boss).
- New **M4/F4 Etherwarp Helper** toggle (Dungeons category): highlights `(27, 81, 18)` while the F4/M4 boss fight is active.

### 4. Auto Croesus fixes
- **Misclicks fixed.** Chest slots are classified strictly by chest-interaction items, so the bot can no longer click a loot item ("power dragon shard") instead of *Open Reward Chest*, and `boughtCurrentChest` is only set after the click is verified.
- **No more false "no profitable chests"** - a chest is only skipped when its profit is genuinely non-positive (Emerald at +515.6K is claimed).
- **Chest modifiers parsed** from the Redstone Torch tooltip: a greyed/struck-through `Kismet Feather` or `Dungeon Chest Key` marks that modifier as **already spent** for that chest.
- **Auto dungeon key**: reads the live Bazaar `DUNGEON_CHEST_KEY` price and re-enters for a second chest when its profit clears key price + safety margin.
- **Kismet EV** uses the same maths as the Discord bot's `!kismet` (dungeon tier, box level, pity counter, drop rate, reroll cost threshold) via `GET /mod/kismet/<user>`, with a local port as fallback.

### 5. Profit tracking & web sync
- Every chest opened (manual or automatic) is recorded itemised: items + counts, floor/tier (`M7`, `F7`, `T1`...), run duration, net profit, kismet use.
- Runs sync to the new backend: `POST /api/v1/profits`, browsable at **`/profits`**, **`/profit/:user`** and **`/profit/:user/:type`** (`dungeons`, `kuudra`).
- `/b profit` prints your own summary; `/b profit <user>` fetches someone else's.
- The chest panel has a **NET_ONLY / ITEMIZED** display mode ("Chest Panel Mode").

### 6. Storage
- Backpack contents refresh when a container is opened, killing phantom items ("Chimera in backpack 15" on an empty backpack).
- `/b storage <query>` places **in-world chest waypoints** over every island chest holding the item, merging double chests into one box. Waypoints update (partial) or disappear when the items run out, and positions verified as air/not-a-container are purged from the cache.

### 7. Egg Finder & API history
- Island mapping fixed: `/b egg` no longer reports `Active Subscription Area: None` on supported islands - an unmappable reading keeps the working subscription instead of tearing it down.
- Collected eggs are remembered **per spawn position**, so a Brunch Egg picked up before a lobby hop is not highlighted again after it.
- Egg pickup detection no longer depends on an allowlist of egg flavours, so new/renamed Hoppity eggs still register (and an unknown flavour retires the nearest waypoint instead of leaving a ghost highlight).
- New **`/b apihistory`** (`/b apihistory chat`): timestamp, service, method, endpoint, status code and response time for every outbound HTTP/WebSocket call (Hypixel, Athen, EliteSkyblock, Bombo API...), with an on-disk log.

### 8. Textures, obfuscation & values
- Obfuscation stripping is now per line: recombobulated rarity headers keep their `§k` padding (e.g. `MYTHIC DUNGEON SWORD`), while dummy markers such as `§9§kObfuscated-3` are still revealed.
- **Aspect of the Void** can no longer render as the purple/black missing-texture checkerboard - model resolution always falls back to the vanilla base item, and the pack lookup honours the texture toggle.
- The **no-resource-pack** bypass now actually applies: it short-circuits before a pack model is chosen, so vanilla overrides show properly.
- `/b pv`: failures are shown instead of the endless loading egg (no more fake-ban limbo), tab completion uses the lobby/friends/guild/party roster and rejects Hypixel's `!A-a` placeholder entries, the window is bigger with a more transparent backdrop (both configurable in GUI Settings).
- Estimated Item Value: price source is now selectable (**Instant Buy (Lowest BIN)** vs **Instant Sell (Bazaar Sell Offer / Average BIN)**), and the breakdown covers base item, stars, master stars, enchants, recombobulator, potato books, reforge, gemstones and attributes.

## [26.2.28.38] - 2026-09-24 (Beta)

### 1. Sequence editor: steps are editable
- Every step row has a **pencil** button that loads that step back into the action builder (`[✔ Save Step]`), so an existing `Run Chat / Command` step can be changed from `ah` to `/ah` instead of being deleted and recreated. Type pills switch the step's action without losing the edit.

### 2. Croesus profit tracker (like `/gp`)
- New HUD: **total profit, runs, average, kismet count**, then a **per-floor breakdown** (`F7`, `M7`, `M4`, `T1`...), read from `bombo_croesus_profit.json` (cached, not re-read every frame). The floor tag comes from the sidebar sub-area.
- `/b ac stats` prints the same per-floor breakdown in chat.

### 3. Auto Croesus settings in the config GUI
- New **Auto Croesus** category, visible only in the `bomboclient` (cheat) build: master switch, paid-chest buying, action/kismet delays, kismet threshold, reroll value, dungeon chest profit threshold, dungeon key value, the three Croesus HUD toggles and the **Debug Highlight Mode** switch.

### 4. Chest value panel is movable
- The dungeon chest value panel now uses configurable position/scale (`/b gui`), with a sample panel to position against. Until you move it, it stays docked right of the container as before.

### 5. Debug no longer spams chat
- The simulation line (`[SIMULATION] Highlighted slot #N`) is printed **once per slot/action per container**. The highlight still follows the decision every tick; only the chat line is deduplicated.

### 6. `/b cmd`
- Command history is persisted to `config/bomboaddons/cmd_history.txt` (500 entries) and survives restarts.
- Every Bombo keybind is suppressed while the terminal is open - typing `ls` can no longer fire a sequence. `Esc` behaves normally.

### 7. EggFinder
- **`/b egg debug` / `auth` / `reconnect` now exist** (they were missing, hence `Incorrect argument for command`).
- They **bypass the Hoppity-season gate**, so the handshake runs whenever you ask for it.
- The websocket now connects **as soon as the token arrives** after a manual request, instead of reporting `Disconnected` forever.
- **`RuntimeException` fixed**: the profile key pair is read through Minecraft's public `getProfileKeyPairManager()`; the broken accessor mixin was removed. Failures now report the real cause (offline account, expired key pair, HTTP status).

## [26.2.28.37] - 2026-09-24 (Beta)

### 1. Bombo authentication server (the "endpoint like hysky but ours")
- `bomboapi` now has **`POST /mod/auth`** - an aaron-compatible endpoint: the client sends the exact same payload Skyblocker sends to hysky (Mojang profile key pair + `SHA256withRSA`-signed random data), the server verifies the key-pair signature against **Mojang's own public keys** (fetched live, cached 1h) and the signed-data proof against the client's public key, then issues a Bombo token (HMAC-signed, 6h TTL, refresh 5 min before expiry).
- **`GET /mod/auth/status`** shows whether the Mojang keys pipeline is healthy.
- **`/mod/hoppity` writes now require a token** (`Authorization: Bearer`) - reads stay open. Any random client can no longer pollute the egg waypoint database.

### 2. Mod fallback chain
- EggFinder auth now goes: Skyblocker token (if Skyblocker installed) → **hysky aaron** → **Bombo auth** (our server). If hysky refuses or errors, the same payload is retried against `api.bombo.dpdns.org/mod/auth` and its token is used for everything, including the websocket handshake.
- Hoppity publishes to bomboapi now attach `Authorization: Bearer <token>` (they would 401 otherwise) and identify as `BomboAddons/<version>`.

### Verification (server-side, done during this release)
- `/mod/auth/status` → `{status: ok, mojangKeysLoaded: 2}`.
- Forged key pair → `401 publicKeySignature is not a valid Mojang signature` (the crypto check is real).
- Token-less hoppity POST → 401; authenticated flow issues tokens (visible in `auth_tokens.json`).

## [26.2.28.36] - 2026-09-23 (Beta)

### 1. Sequences actually run now
- **The runtime was the missing piece.** The editor, `/b auto` commands and the ON/RUN buttons shipped in both builds, but the code that *executes* steps was still cheat-source-only, so pressing N did nothing. The executor (`AutoSequenceExecutor`) now ships in **both** jars: triggers fire, steps run, the loop repeats with jittered cooldowns, and safety halts (container closed, 3x step failure, leaving the world) work everywhere.
- **Click Slot / Item is one field now**: type an item name (`auction`) or a slot number (`5` or `slot 5`) - whichever is natural. A second, optional **GUI Title** field guards the step, so a "click auction" step only fires inside the auction house GUI.

### 2. Sequence editor polish
- **Ctrl+X cuts** the selection in every text field (copy + delete).
- **Long text no longer overflows the box.** Focused fields now scroll horizontally: the text stays inside its box and the caret follows, instead of spilling over the background.
- The action builder box got the extra row of height it needed; no more cramped layout.

### 3. /b cmd: ssh works
- **Typing while a command runs goes to its stdin.** `ssh host` then `ls` (or any interactive prompt) is delivered to the running process - the old "a command is still running" dead end is gone for input-taking programs.
- **Ctrl+C interrupts** the running process (real terminal semantics).
- **The view auto-follows new output** until you scroll up; scrolling back to the bottom (or pressing End) re-arms following. No more manual scrolling to read a tail.

### 4. AutoCroesus debug
- **Once per GUI visit, not spam.** Opening a dungeon chest GUI in simulation prints exactly one summary line per visit + one hoverable line with every chest's breakdown (item x qty = value).
- **The all-chests value panel actually renders.** `DungeonChestProfitHud` existed but nothing called its render hook - the panel was orphaned. It now shows every chest with contents and profit on the right side of the GUI (in debug mode and whenever the HUD is enabled).
- The debug summary lists **all chests**, not just the best one.

### 5. EggFinder: real aaron authentication
- The status-disconnected dead end is fixed at the root: when Skyblocker is not installed, we now run the **full Skyblocker handshake** - fetch the player's Mojang profile key pair, sign random data with its private key (`SHA256withRSA`), and POST the proof to `hysky.de/api/aaron/authenticate` with Skyblocker's mod envelope. The server validates it against Mojang's key signature and issues a real token; the token auto-refreshes 5 minutes before expiry, exactly like Skyblocker's own flow.
- Added the `MinecraftAccessor` mixin for the profile key pair manager (parity with Skyblocker's accessor).

### 6. Playtime sync hardening
- The sync now identifies as the mod (Java's default `Java/x` User-Agent is intermittently refused by the edge) and sets explicit connect/read timeouts. Verified the `/playtime` endpoint itself answers 200.

### 7. Chat attribution: the deferred-execution race
- Keybind-fired commands (e.g. pressing B for `/bz`) were labelled `Server` because `executeTracked` defers the send through `mc.execute(...)`, which can run *after* the bind handler's `finally` had already cleared the trigger. The trigger is now snapshot before deferral and re-applied inside the lambda: keybind sends keep `Trigger: Keybind B`, typed sends stay `Player Input`.

## [26.2.28.35] - 2026-09-23 (Beta)

### 1. Chat history attribution
- **Outgoing rows no longer say "created by null".** A command fired by a keybind now carries its real provenance: the bind handler stamps a `Keybind <name>` trigger before executing, so the row shows `Source: BomboAddons` with `Trigger: Keybind Tab` (readable name) in the tooltip - and never leaves a null author.
- **Outgoing distinguishes you from your binds.** A message or command you typed in the chat screen shows `Source: Player Input`; the same command fired by a bind shows the bind. Column and tooltip both reflect it.
- **Readable key names everywhere.** Tab, arrows, Page Up/Down, Home/End, Insert/Delete, Caps Lock, Backspace, Escape and Enter resolve to names (`key 258` is now `Tab`), in triggers, capture buttons and tooltips.

### 2. Sequences category
- **The category is back on installs with a custom `/b order`.** The organizer file is now merged, not applied wholesale: your saved order is kept, and categories that did not exist when you last saved are appended instead of vanishing. Features whose saved category no longer exists fall back to `Uncategorized` rather than disappearing.
- **Auto is now displayed as Sequences** (internal id, `/b auto` commands and config schema unchanged), so the tab reads like what it does.

### 3. /b cmd
- **Fixed:** a second `cmd` subcommand registered later on the same tree was winning Brigadier's conflict resolution, so `/b cmd` ran the legacy "run last detected command" path instead of opening the terminal. The duplicate is gone - bare `/b cmd` opens the terminal again.
- **The session survives closing.** Scrollback, history, the typed input line and any running command are held outside the screen: close with Esc, reopen with `/b cmd`, and everything is still there streaming. `Ctrl+L` or `clear` wipes the buffer on demand.

### 4. Mod ID hiding always on
- The toggle is gone; both builds now always answer the server's brand query with the vanilla name (the Odin countermeasure). The config field stays for schema compatibility but is ignored.

### 5. Updater version-line guard
- A `26.1.x` install can no longer "update" to a `26.2.x` jar (different Minecraft line - it would not even load). The updater now compares the first two version segments and refuses cross-line candidates with an explicit message.

### 6. Keybind capture: modifier combos
- Pressing Ctrl/Alt/Shift/Win/F-keys in a `[Press Key...]` capture no longer commits instantly. A modifier opens a combo: the button shows `[CTRL + ...]` while held, pressing a letter commits `Ctrl + A`, and releasing the modifier alone keeps it as a single-key bind. Fixes Trade Max Pet Hotkey and every other config keybind.

### 7. Performance
- **Pest ESP renders only in the Garden** (it can never see pests anywhere else).
- **Critter Capsule trajectory only runs in the Safari**, render and tick both - it previously walked tracked projectiles every tick on every island.
- Expect both rows to disappear from `/b perf` outside those islands.

### 8. AutoCroesus
- **`/b ac debug`** now exists: simulation mode highlights the slot it *would* click (buy, kismet reroll, RNG claim) instead of clicking, and reports each simulated action in chat. ESC stops.
- Ported the classification sets from RandomStuff's AutoCroesus: an **always-buy list** (Necron Handle, Dark Claymore, master stars, Shadow Fury, scrolls, Livid Dye) that claims a chest even at computed loss, and a **worthless list** (junk ultimates, dungeon discs, fish) priced at 0 so manipulated prices cannot inflate chest values or trip the 0-price safety abort.
- **Pet loot parsing** added (`[Lvl 1] <Pet>` with rarity from color code).
- Kismet logic now also refuses to reroll chests containing blacklisted RNG items exactly like the reference (already the case) and records `WORTHLESS` price sources in the item detail for debugging.

### 9. EggFinder handshake aligned with Skyblocker
- Studied Skyblocker's actual websocket client (source in `bombotest/skyblocker`): our payload envelope already matched byte-for-byte, but the **`Authorization` header was missing the `Bearer ` prefix** and the User-Agent was pinned to an old release. Both now match current Skyblocker (`Bearer <aaron token>`, `Skyblocker/6.10.4 (<mc>)`), so the hysky endpoint sees a well-formed Skyblocker handshake.

### 10. Account swapper race fix
- **Selecting an account now switches the session synchronously** from the cached token; the refresh still runs and re-applies when done. Joining a server immediately after swapping can no longer connect as the previous account.
- A failed internal session-services update is no longer silent - the mod tells you the swap was partial and a relog is needed.

## [26.2.28.34] - 2026-09-23 (Beta)

### 1. Chat history fixes
- **Fixed wrong attribution.** Every server message was labelled "created by BomboAddons" because our own mixin and message-routing frames sat in the call path and were picked as the caller. The inspector now skips infrastructure frames, and detects a network delivery (`handleSystemChat` etc.) first: messages that arrived from the server are shown as `Hypixel / Server`, player-sent ones as `Player Input`, and only real mod frames claim authorship.
- **One row per feature event, with its trigger.** The `[BomboAddons]` chat line that reports an event ("Started auto sequence: Plushie Transfer Macro") is now merged into the event row instead of being dropped, so the row carries `Origin:`, `Feature:` and `Trigger:` (e.g. `Keybind TAB`, `Mouse Button 5`, `Command /b auto run X`, `Config GUI RUN button`) and the tooltip shows it on hover.
- **Opens at the bottom** (most recent message) with `End` to jump back to newest, `Home`, PageUp/PageDown, and the scroll position no longer resets when switching tabs.
- **Requested tabs:** `All`, `BomboAddons`, `Mods Only`, `Normal Chat` (includes blocked), `Blocked Only`, `Outgoing`, plus an `Events` chip for the auto-sequence log. New subcommands: `/b chathistory bombo|mods|normal|blocked|outgoing|events|clear`.
- Added a `Source` column so who produced a row is visible without hovering.

### 2. Auto sequences are editable again
- The `Auto` category is no longer hidden behind `hideCheats`, and it is no longer cheat-only: the editor, the data model and `/b auto ...` ship in **both** builds. Only the executor is cheat-only, and running a sequence on the legit build now says so instead of doing nothing.
- New step type **Right/Left Click NPC** with a name matcher, search radius and click button - so "right-click the NPC, then click slot X, then shift-click, then close the GUI" is expressible.
- **Repeat** count per step, **loop** with a randomised cooldown, and a per-sequence **jitter %** (default 30). A `3000ms` delay with 30% jitter fires between ~2100ms and ~3900ms, so runs are never a metronome. The UI shows the computed range, e.g. `[LOOP ~280-520ms (±30%)]`.
- Editing from chat: `/b auto add|remove|run|stop|list|toggle|loop|jitter|key <name> <KEY>`.
- Sequence persistence gained `jitterPercent`, `repeatCount`, `entityMatcher` and `searchRadius`; the JSON remains backward compatible.

### 3. Build: one command, two jars
- `./gradlew build` now produces **both** artifacts: `bomboaddons-<ver>.jar` (legit) and `bomboclient-<ver>.jar` (cheat). Cheat sources are compiled every build and separated at packaging time, so there is no flavor flag to forget.
- Added `assertNoCheatReferences`: fails the build if shared code references a cheat package (comments and string literals are ignored, so the reflective SPI stays legal).
- Added `assertFlavorIntegrity` positive control: the cheat jar must contain the cheat classes and its marker, otherwise the build fails - two identical legit jars can no longer ship silently.
- Removed sources jars entirely (a sources jar would have published cheat sources) and added `sweepStaleArtifacts`, which deletes leftover `*-sources.jar` / `*-dev.jar` from `build/libs`.

### 4. Each build has its own mod id
- Legit ships as `bomboaddons`, cheat as `bomboclient`. A mod-id blacklist on one can no longer take the other down with it, and the two builds are individually identifiable in logs and crash reports. The asset namespace stays `bomboaddons`, so every texture, model and lang entry keeps resolving.
- Added a startup warning (chat + log) when both jars are installed at once, replacing the duplicate-jar protection that a shared mod id used to provide implicitly.

### 5. Hide Mod ID On Join (both builds)
- New `modIdHider` setting (**on by default**), applied in both flavors: the client answers the server's brand query with the vanilla name, so the one mod-related field a vanilla server receives carries no mod marker. This is the countermeasure to what took Odin's users down after a mod-id blacklist.
- Stated honestly: this hides identity, not behaviour - it does not defeat behavioural detection or staff review.

### 6. No Obfuscate (§k)
- New `noObfuscate` setting (and `/b noobfuscate`, or `nb on|off` in the terminal) strips the obfuscated style from chat messages and item tooltips, revealing the text behind it while preserving colours, bold/italic, click and hover events.
- Implemented as a `ModifyVariable` argument rewrite on chat (not a cancel-and-re-add), so enabling it cannot silently disable chat triggers, tab filtering or message history.

### 7. `/b cmd` - in-game terminal
- `/b cmd` opens a real terminal inside Minecraft; `/b cmd ping 1.1.1.1` opens it and runs the command immediately.
- Runs actual shell commands on the machine (streamed output, scrollback, `↑`/`↓` history, Ctrl+V paste, PageUp/PageDown, click a line to copy it). Built-ins: `help`, `clear`, `close`, `ver`, `flavor`, `echo`, `nb on|off`.
- Not sandboxed and it is not a pseudo-terminal: interactive full-screen programs (`ssh` password prompts, `vim`, `top`) will not behave interactively.

### 8. Updater
- Fixed "No releases or update jars found" being reported for a build that was simply up to date: the flavor gate blanked the version before the comparison, so any flavor without a published artifact dead-ended.
- Now: the channel's latest version is resolved and compared first; "up to date" is reported as such. Only a genuine update that has no artifact for **this** build reports the actionable message (which artifact exists, and `/b update switch`).

## [26.2.28.33] - 2026-09-23 (Beta)

### 1. `/b chathistory` fixed (it never existed)
- `ChatHistoryScreen` was fully implemented but **never instantiated** — there was no command, no keybind and no GUI entry. The screen even advertised `/b chathistory` in its own header.
- Added `/b chathistory`, `/b chathistory auto`, `/b chathistory all`, a `chatHistoryKey` keybind, a "Open Chat History" button and a "Chat History Limit" slider.
- Fixed a 2px geometry mismatch between rendering and click handling (`rowsTop` 16 vs 18) that made the bottom row unclickable, and clamped the scroll offset.

### 2. Auto sequence events now appear in chat history
- Added `ChatHistoryTracker.recordEvent(...)` with a new `EVENT` status. The existing `recordIncoming` path silently discarded anything containing `[BomboAddons]`, so feature messages could never be recorded.
- Sequence starts, stops, completions and halts are recorded with their real trigger: `Keybind TAB`, `Mouse Button 5`, `Config GUI ▶ RUN button` or `Command /b auto run <name>`.
- The hover tooltip now shows `Origin:`, `Feature:`, `Trigger:` and `Sequence:` lines alongside the existing mod/caller information, and a new `Auto Sequences` filter tab was added.
- Added `/b auto list`, `/b auto run <name>` and `/b auto stop`.

### 3. Auto sequence safety guards
- A step is now retried up to 3 times before the sequence halts, instead of silently skipping and continuing forever.
- Halts with a recorded reason when the container closes mid-step, the item matcher resolves to nothing, the player leaves the world, the sequence is deleted while running, or the step list is empty.

### 4. Dual flavor build: `bomboaddons` (legit) and `bomboclient` (cheat)
- `./gradlew build` produces the legit `bomboaddons-<version>.jar`; `./gradlew build -Pflavor=cheat` produces `bomboclient-<version>.jar`.
- Cheat-only sources live in `src/cheat/java` and are compiled **only** into the cheat artifact. Shared code reaches them through `FlavorBridge`/`Flavor`, a reflective SPI, so the legit jar never links a cheat type.
- A new `assertFlavorIntegrity` verification task fails the build if a cheat-only class leaks into the legit jar (verified against a deliberately planted class).
- Moved into cheat-only: the sequence executor, `/b hide`, `/b stealth`, the `Auto` category registration, and a cheat-only `ClientBrandRetriever` mixin.
- Both jars keep the same Fabric mod id so textures, lang and the config directory keep working; the flavor is signalled by a bundled marker.

### 5. Flavor-aware updater and legacy migration
- `/b update` now only ever installs the artifact matching the running flavor, and the old-jar cleanup is scoped to that flavor's prefix (previously it could delete the other flavor's jar).
- Added `/b update flavor` and `/b update switch` (downloads the other flavor and removes this one on restart).
- Added `FlavorMigration`: existing users with `hideCheats = false` or automation configured are recognised and moved onto the cheat flavor; the legit jar forces `hideCheats = true` and preserves every value so switching back loses nothing. Config schema version bumped to 2.

### 6. Stealth mode (cheat flavor, identity/presence hygiene)
- `stealthMode` / `/b stealth` stops the client posting presence to the Bombo bridge (no more `/b online` visibility for the player), stops publishing egg finds, and reports the vanilla brand on join instead of a modded one.
- Documented honestly in-game: this is client-side identity hygiene and does not defeat server-side behavioural detection.

### 7. Repository and audit
- **Fixed a `.gitignore` defect:** a bare `config/` pattern also matched `src/client/java/me/bombo/bomboaddons/gui/config/`, keeping 5 build-critical sources (`BomboConfigScreen`, `ConfigCustomWidgets`, `ConfigItem`, `ConfigUITheme`, `DungeonPricesScreen`) out of version control. Anchored to `/config/` and the files are now tracked.
- Added `docs/FEATURE_AUDIT.md`: reachability method, measured results (745 settings fields, 353 wired into the config GUI, 14 unreferenced fields kept for schema safety), command/GUI/HUD coverage, and the no-deletion policy.

## [26.2.28.32] - 2026-09-21 (Beta)

### Mod Update Channels & Automated Updating
- **Update Channel Setting:** Added `updateChannel` setting ("Full Only" vs "Betas & Full") in `BomboConfig`.
- **Channel-Aware Checks:** `/b update` and automated startup checks now filter updates according to the configured channel:
  - *"Full Only"*: Only downloads stable, 3-component full releases (e.g. `26.2.28`, `26.2.29`).
  - *"Betas & Full"*: Automatically detects and installs the newest release across both betas and full builds (e.g. `26.2.28.32`).
- **Command Enhancements:** Expanded `/b update` to support:
  - `/b update`: Checks and installs updates following current channel setting.
  - `/b update full`: Explicitly checks and installs the latest full release.
  - `/b update beta` / `betas`: Explicitly checks and installs the latest beta/full release.
  - `/b update channel [full|beta]`: Shows or changes the active update channel on the fly.
- **Config GUI Integration:** Added "Update Channel" cycle option and "Check For Updates" button to the "General" category in both the new and classic config screens.
- **Download Reliability:** Direct downloads from `api.bombo.dpdns.org/mod/version` using proper headers and jar naming without version prefix duplication.

## [26.2.28.30] - 2026-09-21 (Beta)

### 1. Missing Vanilla Textures (lead etc.)
- In `TotemAnimationManager.java`, guarded custom SkyBlock item creation with `SkyblockItemManager.getInfo(upperId) != null`.
- Vanilla items such as lead now cleanly fall back to `BuiltInRegistries.ITEM` (`Items.LEAD`, `minecraft:lead`) instead of displaying as missing stone textures.

### 2. Search Bar & Text Fields (Ctrl + A, Ctrl + Z, Word Deletions)
- **Select All & Undo/Redo:** Fixed in `InventorySlotColorScreen.java` so `Ctrl + A` selects all text (with a selection highlight) rather than erasing it. Added full `Ctrl + Z` undo and `Ctrl + Y` redo stack support.
- **Search Bar Shortcuts:** In `InventoryButtonsScreen.java`, added cursor position navigation, `Ctrl + A`, `Ctrl + Delete` (delete word forward), `Ctrl + Backspace` (delete word backward), `Delete`, `Ctrl + C`, `Ctrl + V`, `Ctrl + Z`, and `Ctrl + Y`. Text fields inside the button editor also now undo text modifications rather than deleting buttons.

### 3. Aspect of the Void Texture Overrides
- In `ItemModelResolverMixin.java`, whenever the item is an `aspect_of_the_void`, it directly resolves the base `aspect_of_the_void` model, bypassing broken warped variant texture overrides.

### 4. Compact Chat History & Accurate Mod Origin Detection
- **Compact Layout:** Changed row height to 13px in `ChatHistoryScreen.java` for a dense layout matching SkyHanni `/shchathistory`.
- **Mod Caller Detection:** Updated `inspectCaller()` in `ChatHistoryTracker.java` to skip Mixin generated methods (`handler$*`, `wrapOperation$*`, `invoke$*`, etc.) and Minecraft forwarding methods. Correctly attributes messages to Devonian, BomboAddons, SkyHanni, BetterPV, NEU, Odin, and Skytils.
- **Blocked Messages:** Hooked Fabric API's `ClientReceiveMessageEvents.GAME_CANCELED` and `CHAT_CANCELED` in `BomboaddonsClient.java` so chat messages suppressed by SkyHanni or other mods are recorded and tagged as blocked.

### 5. Clicker Trigger Keybind
- In `ConfigCustomWidgets.java`, replaced the text input for "Trigger Keybind" with an interactive button showing `[Press Key...]`. Pressing any key (e.g. Tab, R, F) or mouse button (e.g. Mouse 4, Mouse 5) instantly captures the key without typing.

### 6. New "Auto" Automation Category & Sequencer
- Added the `"Auto"` category to `ConfigRegistry.java`.
- Implemented `AutoSequenceManager.java` and its UI card in `ConfigCustomWidgets.java`:
  - **Sequence Header:** Name, Trigger Keybind (with press-to-bind listener), Loop toggle (`Loop: ON/OFF`), and Loop delay (ms).
  - **Step Builder:**
    - *Click Slot / Item:* Click slot by index or item name matcher, with click type (`LEFT`, `RIGHT`, `SHIFT_LEFT`, `DROP`) and custom delay (ms).
    - *Close GUI:* Automatically closes container screens with delay (ms).
    - *Run Command:* Sends chat or slash command with delay (ms).
    - *Click in World (NPC):* Performs right-click or left-click with delay (ms).
    - *Wait:* Dedicated delay pause (ms).
  - **Controls:** Live `[ON]/[OFF]` toggle, instant test run button `[▶ RUN]/[⏹ STOP]`, reordering buttons `[▲] / [▼]`, edit, and delete.
  - Linked to client key and mouse listeners in `KeyboardMixin.java` and `MouseMixin.java`.

---

## [26.2.28] - 2026-09-15 (Full Release)
- Initial 26.2 port and Fabric loader stabilization.
- Storage preview hover restrictions.
- Unified versioning system across build files.
- Etherwarp lore range check.
- Highlight search bar and category refinements.
- Diana lootshare recursion safety fix.
