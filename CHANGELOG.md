# Changelog

## v1.0.34
- **Features**: Added support for mining areas in the playtime tracker.
    - Explicit detection for "Dwarven Mines" and "Crystal Hollows".
    - Improved subarea tracking for specific zones within these areas.
    - Switched to more robust color code stripping for better location accuracy.

## v1.0.33
- **Features**: Added comprehensive support for Dungeon playtime tracking. 
    - The mod now detects the "Dungeons" area from the scoreboard.
    - Floors (Entrance, F1-F7, M1-M7) are tracked as subareas.
    - Improved subarea detection logic to handle custom color codes.

## v1.0.32
- **Stability**: Fixed `java.lang.IllegalArgumentException: Illegal base64 char` in `LF.java` that occurred during configuration saving and container data decoding.
- **Rendering**: Resolved the issue where Pest Tracers were not rendering in 2D space. Tracers now correctly point from the bottom-center of the HUD to the pests.
- **Logic**: Refined the `HotbarSwapper` virtual inventory logic to handle complex multi-slot movements without losing track of items. Added detailed debug logging (enable via API Debug).
- **Maintenance**: Incremented version for the auto-update system.
