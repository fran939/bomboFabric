# BomboAddons (Minecraft 26.2 Fabric)

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg)
![Fabric Loader](https://img.shields.io/badge/Fabric-0.19.3-blue.svg)
![Java](https://img.shields.io/badge/Java-25-orange.svg)
![Latest Release](https://img.shields.io/badge/Release-v26.2.28.58-blue.svg)

**BomboAddons** is an advanced, high-performance client mod built on the **Fabric Loader** for **Minecraft 26.2**. Designed with Hypixel SkyBlock quality-of-life enhancements, seamless media integrations, interactive screensharing, and zero-compromise performance profiling.

---

## ✨ Key Features

### 🎵 Spotify Desktop Integration & Synchronized Lyrics
- **Interactive Spotify HUD (`/b hud`):**
  - Displays currently playing track, artist, album art/icon, and progress bar with smooth second-by-second countdown.
  - Interactive on-screen playback controls (`|◀`, `⏸ / ▶`, `▶|`).
  - Direct Spotify Desktop launch: click track or artist to immediately open the album or artist search within the **Spotify Desktop App**.
  - Customizable theme colors (background, border, text, accent, progress bar) via `/b` config.
- **Word-for-Word Synchronized Lyrics (`/b lyrics` or `/lyrics`):**
  - Real-time karaoke lyrics display tracking song progress word-by-word with smooth cyan glowing highlights.
  - Smooth auto-scrolling with manual wheel navigation and a "Jump to Playing" snap-back.
  - Multi-provider support: switch dynamically between **LRCLIB**, **PAXSENIX**, **UNISON**, and **YOULYPLUS**.
  - Built-in **Raw / Edit Lyrics Modal** to inspect, troubleshoot, or copy raw lyric timestamps directly to the clipboard.

### 👥 Interactive Screensharing & Spectator (`/ss <player>`)
- Peer-to-peer spectator invites powered by encrypted IRC channel messaging.
- Targets receive interactive `[ACCEPT]` and `[DENY]` buttons in chat.
- Configurable **Auto-Accept Whitelist** (`/ss whitelist <player>`, `/ss remove <player>`, `/ss list`).
- Decoupled camera controls with smooth mouse sensitivity and full third-person player body rendering.
- Quick disconnect with `/ss stop`.

### 📦 Searchable Multi-Grid Storage Overlay
- Replaces vanilla `/storage`, ender chests, and backpacks with a clean, high-density multi-grid view.
- Live item search with instant filtering across all ender chest pages and backpacks simultaneously.
- Configurable layout: storages per row, backpack columns, persistent search history, and cursor position preservation.

### ⚔️ Hypixel SkyBlock Utilities
- **RTCA Live Boost Detection:** Calculates Catacombs runs to Level 50 with live boost inspection (Hecatomb helmet, Scarf accessories, Catacombs Graduate shards, essence perks, active Mayor boost).
- **Interactive Mayor Calendar Tooltips (`/b mayor`):** Displays active election perks, descriptions, and minister bonuses matching the official calendar GUI layout.
- **Dungeon Quick Join:** One-click instant matchmaking commands (`/f1`–`/f7`, `/m1`–`/m7`, `/e`).
- **Anti-Jitter:** Stabilized font advances preventing recombobulated (`§k`) items from shaking.
- **Hoppity Auto-Pickup:** Automatically answers incoming phone calls from Hoppity on the first ring.

### ⚡ Performance Visualizer & Profiler (`/b perf`)
- In-game real-time profiler measuring CPU time per subsystem down to microseconds.
- JVM Heap and thread activity monitor (`Used MB / Allocated MB / Max MB | Threads`).
- Highly optimized entity scanner with negative cache sentinels, maintaining 0.01 ms scan overhead even in crowded lobbies.

---

## 🎮 Useful Commands

| Command | Description |
| :--- | :--- |
| `/b` | Open the main BomboAddons configuration GUI |
| `/b hud` | Open the interactive HUD repositioning and scale editor |
| `/b lyrics` or `/lyrics` | Open the synchronized karaoke lyrics screen |
| `/b perf` | Open the real-time Performance Profiler & JVM Heap visualizer |
| `/b changelog` | View the interactive in-game changelog fetched from the server |
| `/b mayor` | Inspect current mayor election data, perks, and minister |
| `/ss <player>` | Send an interactive screenshare / spectator invitation |
| `/ss stop` | End current spectator session |
| `/ss whitelist <player>` | Add player to screenshare auto-accept whitelist |
| `/f1`–`/f7`, `/m1`–`/m7`, `/e` | Quick join Catacombs / Master floors / Entrance |

---

## 📥 Downloads

### 🌐 Official Server
- **Latest Full Release:** [`bombo.dpdns.org/mod/latest`](https://bombo.dpdns.org/mod/latest)
- **Version Catalog & Betas:** [`bombo.dpdns.org/mod/version`](https://bombo.dpdns.org/mod/version)
- **Changelog API:** [`api.bombo.dpdns.org/mod/changelog`](https://api.bombo.dpdns.org/mod/changelog)

### 🚀 GitHub Actions
Every commit pushed to the repository triggers an automated GitHub Actions build. You can download the latest compiled `.jar` files directly from the **Actions** tab on GitHub:
1. Navigate to the **Actions** tab on GitHub.
2. Click the latest workflow run.
3. Scroll to the **Artifacts** section at the bottom to download `bomboaddons-jars`.

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 25** (recommended: Eclipse Temurin or Oracle JDK 25)
- Git

### Build Instructions
Clone the repository and compile with Gradle:

```bash
git clone -b 26.2 https://github.com/fran939/bomboFabric.git
cd bomboFabric
./gradlew build -x test
```

Compiled jars will be generated in `build/libs/`:
- `build/libs/bomboaddons-<version>.jar` (Legit flavor)
- `build/libs/bomboclient-<version>.jar` (Cheat flavor)

---

## 📜 License
Licensed under the Apache License 2.0.
