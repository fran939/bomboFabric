# BomboAddons Agent Continuation & Knowledge Handoff

> **Current Version:** `26.2.28.69` (Beta)
> **Branch:** `26.2` (`origin/26.2`)
> **Minecraft:** `26.2` | **Fabric Loader:** `0.19.3` | **Loom:** `1.17.11` | **Java:** `25` (compatibility 21/25)
> **Flavors:** `bomboaddons` (legit) & `bomboclient` (cheat)
> **Remote Server:** `ubuntu@ssh.bombo.dpdns.org` (port 22, SSH key `C:\Users\frand\.ssh\id_ed25519`)
> **Remote API Path:** `/home/ubuntu/bomboapi/` (PM2 service `bomboapi`)

---

## 1. Mandatory Release & Versioning Protocol (CRITICAL)

On **EVERY PROMPT FINISH**, every agent **MUST** complete all of the following:

1. **Version Format:** `26.2.<mod_version>.<subversion>`
   - Subversion (Beta): 4 parts (e.g. `26.2.28.68`, next is `26.2.28.69`).
   - Milestone (Full): 3 parts (e.g. `26.2.29`).
   - Bump `mod_version` in [`gradle.properties`](file:///e:/Users/frand/Documents/bomboaddons-26.2/gradle.properties).
2. **Build Verification (Both Flavors):**
   ```powershell
   ./gradlew compileClientJava
   ./gradlew build -x test
   ```
   - Guards run automatically: `assertFlavorIntegrity` and `assertNoCheatReferences`.
   - Stale outputs quirk: if `Failed to clean up stale outputs` on resources, re-run command.
   - Directory lock quirk: if `Unable to delete directory build/classes/java/client`, delete that directory via PowerShell (`Remove-Item -Recurse -Force build\classes\java\client`) and re-run.
3. **Update Changelogs:**
   - Update [`CHANGELOG.md`](file:///e:/Users/frand/Documents/bomboaddons-26.2/CHANGELOG.md) and [`data/changelog.json`](file:///e:/Users/frand/Documents/bomboaddons-26.2/data/changelog.json).
   - SCP changelog to server:
     ```powershell
     scp -i "C:\Users\frand\.ssh\id_ed25519" data\changelog.json ubuntu@ssh.bombo.dpdns.org:/home/ubuntu/bomboapi/data/changelog.json
     ```
4. **Deploy Jars to Remote Server:**
   ```powershell
   scp -i "C:\Users\frand\.ssh\id_ed25519" build\libs\bomboaddons-<version>.jar build\libs\bomboclient-<version>.jar ubuntu@ssh.bombo.dpdns.org:/home/ubuntu/bomboapi/releases/
   ```
5. **Git Commit & Push:**
   ```powershell
   git add .
   git commit -m "Release v<version>: <summary>"
   git push origin 26.2
   ```

---

## 2. Features Implemented & Awaiting In-Game Testing (v26.2.28.68)

### A. Storage Overlay (`/st`, Ender Chests, Backpacks)
1. **Dynamic Sizing & Void Row Elimination:**
   - [`BackpackPreview.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/BackpackPreview.java) parses item count and lore capacity lines.
   - Ender Chest 4 (and 1-row ender chests 9x1) now renders strictly its 1 real row. Empty black void rows are eliminated.
2. **Account UUID & Profile ID Segregation:**
   - When switching SkyBlock lobbies, chat message `Profile ID: <uuid>` is intercepted by [`SkyblockUtils.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/SkyblockUtils.java) and [`ChatMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ChatMixin.java).
   - Storages are persisted to `.minecraft/config/bomboaddons/storage/<account_uuid>/<profile_id>/<slot>.json`.
   - Accounts on the same coop share the profile ID; different coop profiles or separate accounts have isolated caches. Changing profile immediately clears memory cache and reloads from disk.
3. **Persistent Search Highlighting Across Tabs:**
   - Query in search box (e.g. `aurora`) remains highlighted when clicking on an item, switching between Ender Chests, or navigating backpacks. Fixed in [`StorageOverlayScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/StorageOverlayScreen.java) and [`SearchableGridWidget.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/SearchableGridWidget.java).
4. **Visual Header Renaming (`RenameStorageScreen.java`):**
   - Clicking any category header (e.g. "Ender Chest 1") opens [`RenameStorageScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/RenameStorageScreen.java) modal allowing the user to rename it (e.g. "Mining Gear"). Names are stored in `BomboConfig.get().storageCustomNames`.
5. **Storage Overlay Themes:**
   - Themes added in [`BomboConfig.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/BomboConfig.java): Default (vanilla), Dark (`0xEE1A1A1A`), Light (`0xEEF0F0F0`), Transparent (`storageOverlayTransparent = true` with custom ARGB hex color `storageOverlayCustomColor`).

### B. Discord Voice HUD & IPC
1. **Ghost Caller Pruning (WebRTC 25s Cutoff):**
   - Scans `%APPDATA%\discord\logs\discord-webrtc_0` and `discord-webrtc_1` in [`DiscordIpcManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/discord/DiscordIpcManager.java).
   - Inbound audio stats timestamps `[YYYY-MM-DD HH:mm:ss.SSS]` are parsed. Users not heard within 25 seconds of the log's newest entry are pruned from `voiceUsers`. Resolves the bug where 7 people were shown when only 5 remained.
2. **In-HUD User Muting:**
   - Clicking a user on [`DiscordVoiceHud.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/discord/DiscordVoiceHud.java) (or running `/b discord mute <id>`) sends `SET_USER_VOICE_SETTINGS` via Discord IPC RPC and tags them `§c[MUTED]`.
3. **Debug Diagnostics & Bot Resolution:**
   - Fixed JSON parsing for `https://api.bombo.dpdns.org/api/bot/users?ids=...` (`root.getAsJsonObject("users")`), resolving display names and global names.
   - `/b discord debug` prints channel snowflake ID and full user IDs.

### C. Live Synced Lyrics Web Player & Spotify Sync
1. **Live Web Player (`https://bombo.dpdns.org/lyrics`):**
   - Deployed at `https://bombo.dpdns.org/lyrics` (served from `/home/ubuntu/bomboapi/public/lyrics.html`).
   - Glassmorphic Spotify dark aesthetic, real-time animated background glow, scrubbing progress bar, and play/pause controls.
   - Synchronized word-by-word active glow animation with automatic center scroll.
   - Interpolates audio progress at 60 FPS via `requestAnimationFrame`.
2. **Client Telemetry Dispatch:**
   - [`SpotifyManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/spotify/SpotifyManager.java) periodically POSTs track name, artist, duration, and millisecond progress to `https://api.bombo.dpdns.org/api/spotify/now-playing`.
3. **Direct Album URI Resolution:**
   - Server endpoint `/api/spotify/resolve` queries MusicBrainz release relations and returns `albumUri` (`spotify:album:<id>`) and `albumUrl`.
   - Clicking tracks/albums in `/b lyrics` or `SpotifyHud` opens Spotify Desktop directly without falling back to Google web searches.

### D. Screenshare 1440p Frame Pipeline
1. **Direct INT_RGB GPU Capture:**
   - Switched [`ScreenshareManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/screenshare/ScreenshareManager.java) framebuffer capture from `TYPE_INT_ARGB` to native `TYPE_INT_RGB`, eliminating expensive per-frame color space conversions for 1440p displays.
2. **Native MJPEG Stream in Web Viewer:**
   - Updated `/home/ubuntu/bomboapi/public/screenshare.html` to connect directly to `/api/screenshare/stream/:user` (`multipart/x-mixed-replace`), removing continuous `Image` garbage collection and achieving smooth 60 FPS playback.

### E. Config Backups, Fresh Defaults & Brigadier Registration
1. **Automatic Backup on Upgrade:**
   - [`BomboConfig.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/BomboConfig.java) automatically backs up `.minecraft/config/bomboaddons/bomboaddons.json` to `.minecraft/config/bomboaddons/backups/config_backup_v<version>.json` whenever `lastModVersion` increases.
2. **Backup Commands:**
   - `/b backup create [name]`
   - `/b backup list` / `/b backup check`
   - `/b backup restore <name>` (supports tab-completion; restoring never deletes the backup).
3. **Safety Defaults:**
   - All macros, auto sequences, and inventory buttons are initialized to disabled (`false`) on fresh configs.
4. **Brigadier Registration:**
   - `/buttons`, `/buttons move`, `/b buttons`, and `/b backup` subcommands registered in Brigadier client dispatcher in [`BomboaddonsClient.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/BomboaddonsClient.java) and [`CommandMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/CommandMixin.java).
5. **No Obfuscate Single-Character Exception:**
   - [`NoObfuscate.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/util/NoObfuscate.java) retains `§k` obfuscation if the string length is 1 (e.g. recombobulator tag `&ka>>`), while stripping obfuscation from multi-character chat spam.

---

## 3. Server Architecture & Ports Reference

| Service / Port | Location | Description |
| :--- | :--- | :--- |
| **`bomboapi` (Port 3000)** | `/home/ubuntu/bomboapi/server.js` | PM2 backend serving mod updates, releases, changelog, Discord bot proxy, Spotify resolver, now-playing sync, and lyrics player. |
| **`bombot` (Port 6668)** | `/home/ubuntu/bombot/` | Discord bot process proxied by `bomboapi` via `/api/bot/*`. |
| **Releases Directory** | `/home/ubuntu/bomboapi/releases/` | Houses compiled `.jar` files served by `/mod/version/:version` and `/mod/latest`. |
| **Live Lyrics** | `https://bombo.dpdns.org/lyrics` | HTML5 synced lyrics web player. |
| **Screenshare Web** | `https://bombo.dpdns.org/screenshare` | Low-latency MJPEG player. |

---

## 4. Key Code Locations

- **Storage Overlay:**
  - Dynamic sizing & profiles: [`src/client/java/me/bombo/bomboaddons/features/storageoverlay/BackpackPreview.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/BackpackPreview.java)
  - Overlay screen & themes: [`src/client/java/me/bombo/bomboaddons/features/storageoverlay/StorageOverlayScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/StorageOverlayScreen.java)
  - Renaming modal: [`src/client/java/me/bombo/bomboaddons/features/storageoverlay/RenameStorageScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/RenameStorageScreen.java)
  - Search grid widget: [`src/client/java/me/bombo/bomboaddons/features/storageoverlay/SearchableGridWidget.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/SearchableGridWidget.java)
  - Screen packet interception: [`src/client/java/me/bombo/bomboaddons/mixin/ClientPacketListenerMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ClientPacketListenerMixin.java)
- **Discord Voice:**
  - Local log scanner & muting: [`src/client/java/me/bombo/bomboaddons/features/discord/DiscordIpcManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/discord/DiscordIpcManager.java)
  - Voice HUD overlay: [`src/client/java/me/bombo/bomboaddons/features/discord/DiscordVoiceHud.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/discord/DiscordVoiceHud.java)
- **Spotify & Lyrics:**
  - Desktop integration & now-playing: [`src/client/java/me/bombo/bomboaddons/features/spotify/SpotifyManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/spotify/SpotifyManager.java)
  - Lyrics screen: [`src/client/java/me/bombo/bomboaddons/features/spotify/LyricsScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/spotify/LyricsScreen.java)
  - Lyrics engine & multi-provider: [`src/client/java/me/bombo/bomboaddons/features/spotify/LyricsManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/spotify/LyricsManager.java)
- **Screenshare:**
  - Engine: [`src/client/java/me/bombo/bomboaddons/features/screenshare/ScreenshareManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/screenshare/ScreenshareManager.java)
- **Config & Backups:**
  - Backups & properties: [`src/client/java/me/bombo/bomboaddons/BomboConfig.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/BomboConfig.java)
  - Client initialization & Brigadier: [`src/client/java/me/bombo/bomboaddons/BomboaddonsClient.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/BomboaddonsClient.java)
  - Chat commands: [`src/client/java/me/bombo/bomboaddons/mixin/CommandMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/CommandMixin.java)
  - Chat interceptor: [`src/client/java/me/bombo/bomboaddons/mixin/ChatMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ChatMixin.java)
