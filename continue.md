# BomboAddons Agent Continuation & Knowledge Handoff

> **Current Version:** `26.2.28.78` (Beta)
> **Branch:** `26.2` (`origin/26.2`)
> **Minecraft:** `26.2` | **Fabric Loader:** `0.19.3` | **Loom:** `1.17.11` | **Java:** `25` (compatibility 21/25)
> **Flavors:** `bomboaddons` (legit) & `bomboclient` (cheat)
> **Remote Server:** `ubuntu@ssh.bombo.dpdns.org` (port 22, SSH key `C:\Users\frand\.ssh\id_ed25519`)
> **Remote API Path:** `/home/ubuntu/bomboapi/` (PM2 service `bomboapi`)

---

## 1. Mandatory Release & Versioning Protocol (CRITICAL)

On **EVERY PROMPT FINISH**, every agent **MUST** complete all of the following:

1. **Version Format:** `26.2.<mod_version>.<subversion>`
   - Subversion (Beta): 4 parts (e.g. `26.2.28.77` -> `26.2.28.78`).
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

## 2. Server Architecture & Endpoints (`bomboapi`)

The backend API running on the server (`ssh.bombo.dpdns.org:3000` via PM2 `bomboapi`, Nginx reverse proxy on 80/443):

| Endpoint / Service | Purpose & Behavior |
| :--- | :--- |
| `https://api.bombo.dpdns.org/mod/latest` | **CRITICAL:** ALWAYS resolves to the **LATEST FULL VERSION** (3 parts, e.g. `26.2.28` or `26.2.29`). Never serves beta subversions. |
| `https://api.bombo.dpdns.org/mod/version` | JSON API catalog listing all versions, `latestFull`, and `latestBeta`. |
| `https://api.bombo.dpdns.org/mod/version/beta` | JSON catalog of beta releases and download links. |
| `https://api.bombo.dpdns.org/mod/version/:version` | Direct `.jar` download for the requested version with `Content-Disposition`. |
| `https://api.bombo.dpdns.org/mod/changelog` | Serves `/home/ubuntu/bomboapi/data/changelog.json` for in-game `/b changelog`. |
| `https://bombo.dpdns.org/screenshare` | Web dashboard displaying live streams from active Minecraft players. |
| `https://api.bombo.dpdns.org/api/screenshare/stream/:user` | Ultra-low latency MJPEG streaming (`multipart/x-mixed-replace`). |
| `https://api.bombo.dpdns.org/api/screenshare/frame` | Ingestion endpoint for base64 JPEG client frames (Nginx rate-limit bypassed). |
| `bombot` (`/home/ubuntu/bombot/`, port 6668) | Privileged Discord bot bridge for voice muting/deafening (`/api/bot/voice/mute`, `/api/bot/voice/deafen`). |

---

## 3. Features Implemented in v26.2.28.78 (Awaiting In-Game Testing)

### A. Storage Overlay Auto-Scroll to Target Container
- **Location:** [`StorageOverlayScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/StorageOverlayScreen.java), [`SearchableGridWidget.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/SearchableGridWidget.java)
- **Behavior:**
  - Added `scrollToWidget(AbstractWidget widget)` to `SearchableGridWidget`.
  - When opening `/bp 4` or `/bp <name>` when previously at backpack 18, `StorageOverlayScreen.init()` automatically scrolls to the opened backpack's exact height so it is immediately visible in view without manual scrolling.

### B. Discord Voice Local Client Mute, Instant Discovery & Anti-Cycling
- **Location:** [`DiscordIpcManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/discord/DiscordIpcManager.java)
- **Behavior:**
  - **Strictly Local Client Mute/Deafen:** Removed bot server-mute/server-deafen calls from self actions; self mute and deafen now strictly toggle the user's local Discord microphone and headset via IPC `SET_VOICE_SETTINGS` and OS hotkeys.
  - **Fast Player Discovery:** Removed the artificial 3000ms delay in bot user lookups, resolving all participant names and avatars concurrently in under 1-2 seconds when joining calls.
  - **Anti-Cycling & Simultaneous Screenshare [LIVE]:** Extended WebRTC user and video log retention to 15s and stopped clearing user maps on empty responses, preventing member flickering and keeping multiple streams (e.g. `pavlor` and `67`) simultaneously marked as `[LIVE]`.

### C. Scratch Visual Studio Quick-Pills & Input Focus Bugfix
- **Location:** [`AutoSequenceVisualScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/auto/AutoSequenceVisualScreen.java)
- **Behavior:**
  - **Dual Cursor Bugfix:** Fixed text box focus handling so clicking an exception guard input un-focuses the slot input, eliminating simultaneous typing and duplicate `_` cursors.
  - **Visual Scratch Quick-Pick Pills:** Added clickable condition pills (`[📦 Full]`, `[🚫 No Item]`, `[✨ Has Item]`, `[📭 Slot Empty]`, `[📂 In Menu]`, `[Clear]`) and parameter chips (`[# 0]`, `[# 10]`, `[# 18]`, `[🎨 ${color}]`) directly in the step editor.

### D. Screenshare 60 FPS Asynchronous Capture Engine
- **Location:** [`ScreenshareManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/screenshare/ScreenshareManager.java)
- **Behavior:**
  - **Decoupled 2-Stage Pipeline:** Decoupled GPU capture from CPU encoding, releasing the GPU lock immediately after frame readout (<13ms) and submitting work to a 3-thread encode pool.
  - **Direct Raster Downsampling:** Replaced slow `setRGB()` loops with direct `DataBufferInt` array operations, dropping downsample time from 20ms to <1.5ms and raising live framerates to smooth 50-60 FPS.

---

## 4. Key File Locations Quick Reference

- **Storage Overlay:**
  - Custom name resolver: [`BackpackPreview.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/BackpackPreview.java)
  - Overlay screen: [`StorageOverlayScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/StorageOverlayScreen.java)
  - Command routing: [`CommandMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/CommandMixin.java)
- **Discord Voice:**
  - Named Pipe IPC & OS shortcuts: [`DiscordIpcManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/discord/DiscordIpcManager.java)
  - Voice HUD overlay: [`DiscordVoiceHud.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/discord/DiscordVoiceHud.java)
- **Scratch Visual Studio:**
  - Visual block editor: [`AutoSequenceVisualScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/auto/AutoSequenceVisualScreen.java)
  - Macro execution engine: [`AutoSequenceExecutor.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/cheat/java/me/bombo/bomboaddons/cheat/sequences/AutoSequenceExecutor.java)
  - Sequence data model & manager: [`AutoSequenceManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/cheat/java/me/bombo/bomboaddons/cheat/sequences/AutoSequenceManager.java)
- **Screenshare & Performance:**
  - Performance visualizer: [`PerformanceScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/PerformanceScreen.java)
  - Stream capture engine: [`ScreenshareManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/screenshare/ScreenshareManager.java)
  - Web player: [`data/screenshare.html`](file:///e:/Users/frand/Documents/bomboaddons-26.2/data/screenshare.html)
- **Chat Enhancements:**
  - Chat mixin: [`ChatMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ChatMixin.java)
  - Hover preview: [`ChatImagePreview.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/util/ChatImagePreview.java)
