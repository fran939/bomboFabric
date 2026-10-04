# BomboAddons Agent Continuation & Knowledge Handoff

> **Current Version:** `26.2.28.77` (Beta)
> **Branch:** `26.2` (`origin/26.2`)
> **Minecraft:** `26.2` | **Fabric Loader:** `0.19.3` | **Loom:** `1.17.11` | **Java:** `25` (compatibility 21/25)
> **Flavors:** `bomboaddons` (legit) & `bomboclient` (cheat)
> **Remote Server:** `ubuntu@ssh.bombo.dpdns.org` (port 22, SSH key `C:\Users\frand\.ssh\id_ed25519`)
> **Remote API Path:** `/home/ubuntu/bomboapi/` (PM2 service `bomboapi`)

---

## 1. Mandatory Release & Versioning Protocol (CRITICAL)

On **EVERY PROMPT FINISH**, every agent **MUST** complete all of the following:

1. **Version Format:** `26.2.<mod_version>.<subversion>`
   - Subversion (Beta): 4 parts (e.g. `26.2.28.76` -> `26.2.28.77`).
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

## 3. Features Implemented in v26.2.28.77 (Awaiting In-Game Testing)

### A. Storage Overlay Open by Name (`/bp <name>`, `/backpack <name>`, `/ec <name>`, `/enderchest <name>`, `/echest <name>`)
- **Location:** [`BackpackPreview.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/storageoverlay/BackpackPreview.java), [`CommandMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/CommandMixin.java)
- **Behavior:**
  - Automatically resolves custom storage card names stored in `BomboConfig.get().storageCustomNames`.
  - For example, if backpack 18 is renamed to `"kudar"`, typing `/bp kudar` or `/backpack kudar` automatically translates to `/backpack 18` and sends it to the server.
  - Same for Ender Chests (`/ec <name>`, `/enderchest <name>`, `/echest <name>`).
  - If no custom name matches and the argument is non-numeric, displays a helpful chat message listing known custom storage names instead of failing silently.

### B. Discord Voice Call "You" Duplication, Commands & Low-Level Hotkeys
- **Location:** [`DiscordIpcManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/discord/DiscordIpcManager.java), [`CommandMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/CommandMixin.java)
- **Behavior:**
  - **Single-User Deduplication:** Purged the synthetic duplicate `"You"` card when alone in a voice call (`webrtcUserIds.isEmpty()`), cleanly binding the local Discord user ID to prevent duplicate user listings.
  - **Persistent Background IPC:** Separated `/b discord` status from HUD toggle so status checks never invoke `stop()` or destroy the background Named Pipe thread (`\\.\pipe\discord-ipc-0`).
  - **OS-Level Windows Shortcuts:** Replaced high-level robot key dispatch with low-level Windows `keybd_event` via JNA `WinUser32` for `Ctrl+Shift+M` (mute) and `Ctrl+Shift+D` (deafen). Hotkeys trigger reliably even when Minecraft has exclusive OS window focus.
  - **Complete Command Routing:** Registered and fully routed `/b discord mute [user]`, `/b discord unmute [user]`, `/b discord deafen [user]`, `/b discord undeafen [user]`, `/b discord hud`, `/b discord sync`, and `/b discord auth`.

### C. Scratch Visual Studio Drag-and-Drop & Visuals
- **Location:** [`AutoSequenceVisualScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/auto/AutoSequenceVisualScreen.java), [`AutoSequenceExecutor.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/cheat/java/me/bombo/bomboaddons/cheat/sequences/AutoSequenceExecutor.java)
- **Behavior:**
  - **Draggable Block Reordering:** Implemented mouse drag-and-drop reordering with a live floating ghost block preview and horizontal blue insertion indicator line.
  - **Kid-Friendly Visuals:** Replaced broken unicode font emojis with crisp UI symbols (`▶`, `■`, `>`, `*`, `~`, `[!]`, `[+]`) and added `formatFriendlyCondition()` translating condition codes into human-readable phrases ("Container is Full", "Top N Rows Full", "Slot [X] has [Item]", "Contains [Item]", "NO [Item]", "Repeat until count is [N]").
  - **Comparison Engine Bugfix:** In `AutoSequenceExecutor`, fixed a bug where `<` comparison checked `indexOf("<=")`. Added `no_item:`, `!has_item:`, and `slot_empty:` condition matchers.

### D. Screenshare Live Telemetry & VulkanMod Direct GPU
- **Location:** [`PerformanceScreen.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/gui/PerformanceScreen.java), [`ScreenshareManager.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/features/screenshare/ScreenshareManager.java), [`data/screenshare.html`](file:///e:/Users/frand/Documents/bomboaddons-26.2/data/screenshare.html)
- **Behavior:**
  - **Live Profiler Telemetry Card:** In `/b perf`, renders a dedicated pulsing live streaming telemetry card displaying active FPS, bitrate in kbps, capture pipeline mode, HTTP POST latency, frame payload size in KB, and total bandwidth sent in MB.
  - **VulkanMod Auto-Detection:** Automatically detects VulkanMod via `FabricLoader.getInstance().isModLoaded("vulkanmod")` and sets capture pipeline reporting to `Direct GPU (Vulkan - <3ms)`.
  - **Web Viewer Synchronized:** Deployed `screenshare.html` to the remote server with instant canvas unhiding on player selection.

### E. Chat Image Hover Preview Bounds Guard
- **Location:** [`ChatMixin.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/mixin/ChatMixin.java), [`ChatImagePreview.java`](file:///e:/Users/frand/Documents/bomboaddons-26.2/src/client/java/me/bombo/bomboaddons/util/ChatImagePreview.java)
- **Behavior:**
  - Bounded line inspection in `bombo$getLineAt()` to `lineIndex >= 0 && lineIndex < this.getLinesPerPage()`.
  - In `ChatImagePreview`, strictly requires `mc.gui.screen() instanceof ChatScreen`.
  - Hovering in the upper game world above the chat box now never triggers unintended image preview popups.

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
