# BomboAddons Changelog

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
