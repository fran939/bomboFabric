# Discord Local RPC / IPC Architecture & Capabilities Research

## 1. Executive Summary & Overview

Discord Desktop exposes an internal IPC endpoint allowing local applications to control voice, audio, video, screenshare, channels, soundboard, and notifications.

Hardware manufacturers like **Elgato (Stream Deck)**, game overlays like **Overwolf**, and widgets like **Discord StreamKit Overlay** communicate directly over this local interface.

This document details the complete wire protocol, commands, subscriptions, payload schemas, and practical applications for BomboAddons in Minecraft.

---

## 2. IPC Wire Protocol & Connection

Discord communicates locally via two transports:
1. **Named Pipes (Windows):** `\\.\pipe\discord-ipc-0` through `\\.\pipe\discord-ipc-9`
2. **Unix Domain Sockets (macOS / Linux):** `$XDG_RUNTIME_DIR/discord-ipc-0` or `/tmp/discord-ipc-0`
3. **Local WebSockets (Web / Browser fallback):** `ws://127.0.0.1:6463/?v=1&client_id=...` (ports 6463 through 6472)

### Frame Layout (Little-Endian Binary Header)
Every packet has an 8-byte binary header followed by a UTF-8 JSON payload:
```
+--------------------+--------------------+----------------------------------------+
|  Opcode (uint32)   |   Length (uint32)  |         JSON Payload (UTF-8)           |
|      4 bytes       |       4 bytes      |             Length bytes               |
+--------------------+--------------------+----------------------------------------+
```

### Opcodes
| Opcode | Name | Description |
| :--- | :--- | :--- |
| `0` | `HANDSHAKE` | Sent by client to initialize session (`{"v": 1, "client_id": "<id>"}`) |
| `1` | `FRAME` | Normal JSON RPC command or event dispatch |
| `2` | `CLOSE` | Clean shutdown of connection |
| `3` | `PING` | Heartbeat ping |
| `4` | `PONG` | Heartbeat pong response |

---

## 3. Authentication & Scopes

### Discord Client IDs & Privileges
Discord restricts sensitive RPC scopes to whitelisted Client IDs:
- **StreamKit Overlay:** `207646673902501888` (Whitelisted for `rpc`, `rpc.voice.read`, `rpc.voice.write`, `identify`)
- **Elgato Stream Deck:** `1051567156172607548` (Whitelisted for `rpc`, `rpc.voice.read`, `rpc.voice.write`, `rpc.video.read`, `rpc.video.write`, `rpc.screenshare.read`, `rpc.screenshare.write`, `messages.read`, `rpc.notifications.read`)
- **Custom / Unapproved Applications:** Standard user applications attempting `rpc.voice.write` receive `Error 5000: Not Authorized`.

### Available Scopes & Capabilities
| Scope | Description | StreamKit | Elgato |
| :--- | :--- | :---: | :---: |
| `identify` | Read username, discriminator, avatar, banner | ✅ | ✅ |
| `rpc` | Base RPC command execution | ✅ | ✅ |
| `rpc.voice.read` | Read voice channel state, participant speaking, mute/deaf status | ✅ | ✅ |
| `rpc.voice.write` | Toggle mute/deafen, adjust member volumes, move voice channels | ✅ | ✅ |
| `rpc.video.read` | Inspect user camera and video streaming state | ❌ | ✅ |
| `rpc.video.write` | Toggle user video camera on/off | ❌ | ✅ |
| `rpc.screenshare.read` | Inspect user screenshare broadcast state | ❌ | ✅ |
| `rpc.screenshare.write` | Start/stop screensharing and change capture source | ❌ | ✅ |
| `messages.read` | Read text channel messages in real-time | ❌ | ✅ |
| `rpc.notifications.read` | Intercept incoming Discord desktop notifications / mentions | ❌ | ✅ |

---

## 4. Complete RPC Commands Catalog

### A. Voice & Audio Settings (`SET_VOICE_SETTINGS`)
Sets user microphone mute, audio deafen, input mode, or device:
```json
{
  "cmd": "SET_VOICE_SETTINGS",
  "args": {
    "mute": true,
    "deaf": false,
    "input": {
      "volume": 100,
      "mode": {
        "type": "VOICE_ACTIVITY", // or "PUSH_TO_TALK"
        "threshold": -45
      }
    },
    "output": {
      "volume": 85
    },
    "echo_cancellation": true,
    "noise_suppression": true,
    "automatic_gain_control": true
  },
  "nonce": "uuid-here"
}
```

### B. Per-User Voice Control (`SET_USER_VOICE_SETTINGS`)
Locally mutes/unmutes or adjusts the volume of any specific voice participant:
```json
{
  "cmd": "SET_USER_VOICE_SETTINGS",
  "args": {
    "user_id": "123456789012345678",
    "mute": true,
    "volume": 0,
    "pan": {
      "left": 0.5,
      "right": 0.5
    }
  },
  "nonce": "uuid-here"
}
```

### C. Voice Channel Navigation (`SELECT_VOICE_CHANNEL`)
Joins or leaves voice channels directly:
```json
{
  "cmd": "SELECT_VOICE_CHANNEL",
  "args": {
    "channel_id": "123456789012345678", // or null to disconnect
    "force": true,
    "navigate": false
  },
  "nonce": "uuid-here"
}
```

### D. Selected Voice Channel Query (`GET_SELECTED_VOICE_CHANNEL`)
Retrieves the voice channel currently joined, its guild, and all active members with voice states:
```json
{
  "cmd": "GET_SELECTED_VOICE_CHANNEL",
  "nonce": "uuid-here"
}
```

### E. Text Channel Navigation (`SELECT_TEXT_CHANNEL`)
Switches the Discord desktop client to view a specific text channel:
```json
{
  "cmd": "SELECT_TEXT_CHANNEL",
  "args": {
    "channel_id": "123456789012345678"
  },
  "nonce": "uuid-here"
}
```

### F. Soundboard Audio Playback (`PLAY_SOUND`)
Triggers soundboard sound effects in the active voice channel:
```json
{
  "cmd": "PLAY_SOUND",
  "args": {
    "sound_id": "112233445566778899"
  },
  "nonce": "uuid-here"
}
```

### G. Video & Camera Controls (`SET_VIDEO`)
Enables or disables webcam video broadcast:
```json
{
  "cmd": "SET_VIDEO",
  "args": {
    "active": true
  },
  "nonce": "uuid-here"
}
```

### H. Screenshare Controls (`SET_SCREENSHARE`)
Configures or starts screensharing of a specific application window:
```json
{
  "cmd": "SET_SCREENSHARE",
  "args": {
    "pid": 12345,
    "audio": true,
    "resolution": 1080,
    "fps": 60
  },
  "nonce": "uuid-here"
}
```

---

## 5. Event Subscriptions (`SUBSCRIBE`)

Local IPC supports pub-sub subscriptions using the `SUBSCRIBE` command:
```json
{
  "cmd": "SUBSCRIBE",
  "evt": "VOICE_STATE_UPDATE",
  "args": {
    "channel_id": "123456789012345678"
  },
  "nonce": "uuid-here"
}
```

### Supported Events
1. **`VOICE_STATE_CREATE` / `VOICE_STATE_UPDATE` / `VOICE_STATE_DELETE`:** Real-time updates when users join, leave, mute, deafen, or screenshare in the channel.
2. **`SPEAKING_START` / `SPEAKING_STOP`:** Sub-millisecond speaking indicator events per user.
3. **`VOICE_CHANNEL_SELECT`:** Dispatched when the local user connects to or disconnects from any voice channel.
4. **`VOICE_SETTINGS_UPDATE`:** Dispatched when input/output volume, device, or mute state changes in Discord.
5. **`VOICE_CONNECTION_STATUS`:** WebRTC ICE state (`DISCONNECTED`, `CONNECTING`, `AUTHENTICATING`, `CONNECTED`).
6. **`MESSAGE_CREATE` / `MESSAGE_UPDATE` / `MESSAGE_DELETE`:** Real-time text channel messages (with `messages.read` scope).
7. **`NOTIFICATION_CREATE`:** Desktop toasts and unread mention notifications (with `rpc.notifications.read`).
8. **`CAPTURE_SHORTCUT_CHANGE`:** Triggered when Discord push-to-talk or shortcut keys are pressed.

---

## 6. Implementation Opportunities for BomboAddons

1. **Direct In-Game Discord Chat Relay (`messages.read`):**
   - Stream clan/guild Discord channel messages directly into Minecraft chat without external Discord bots.
2. **In-Game Soundboard Keybinds (`PLAY_SOUND`):**
   - Bind keys or chat commands (`/b discord sound <name>`) to play favorite Discord sounds in voice calls.
3. **Voice Channel Quick Switcher (`SELECT_VOICE_CHANNEL`):**
   - Join guild party voice channels automatically when entering Dungeons or Kuudra parties.
4. **Discord Mention Toast (`NOTIFICATION_CREATE`):**
   - Display a floating in-game HUD banner when mentioned on Discord while playing fullscreen.
5. **One-Click Minecraft Screenshare to Discord (`SET_SCREENSHARE`):**
   - Automatically share the Minecraft PID directly into the Discord voice channel with a single keybind.
