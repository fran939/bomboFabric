# Changelog

## v1.0.32
- **Stability**: Fixed `java.lang.IllegalArgumentException: Illegal base64 char` in `LF.java` that occurred during configuration saving and container data decoding.
- **Rendering**: Resolved the issue where Pest Tracers were not rendering in 2D space. Tracers now correctly point from the bottom-center of the HUD to the pests.
- **Logic**: Refined the `HotbarSwapper` virtual inventory logic to handle complex multi-slot movements without losing track of items. Added detailed debug logging (enable via API Debug).
- **Maintenance**: Incremented version for the auto-update system.
