# Feature Audit — reachability and dead code

Generated for v26.2.28.33. Method and findings below are reproducible from the repository
with plain text searches; re-run them after large refactors rather than trusting this file
forever.

## 1. Method

Five surfaces can expose a feature. A feature counts as **reachable** if it appears on at
least one of them:

| Surface | Ground truth |
| :--- | :--- |
| Config schema | `BomboConfig.Settings` public fields |
| Config GUI | `ConfigRegistry.getRegisteredItemsForCategory` (the `s.<field>` getters/setters) |
| HUD editor (`/b gui`) | `HudMoveScreen.HudTarget` (28 elements) + `hudToggle` items |
| Commands | `ClientCommands.literal(...)` in `BomboaddonsClient.setupCommands`, `CommandMixin` intercepts, alias tables |
| Feature gating | `ConfigRegistry.CHEAT_CATEGORIES` + `Flavor.get().isCheat()` |

Commands used:

```bash
# settings fields
awk '/public static class Settings/,0' src/client/java/me/bombo/bomboaddons/BomboConfig.java \
  | awk '/public static class [A-Z]/ && !/class Settings/{exit} {print}' \
  | grep -oE 'public (boolean|int|long|float|double|String|List|Map|Set)[^=;]* [a-zA-Z0-9_]+' \
  | grep -oE '[a-zA-Z0-9_]+$' | sort -u

# fields wired into the config GUI
grep -oE '\bs\.[a-zA-Z0-9_]+' src/client/java/me/bombo/bomboaddons/gui/config/ConfigRegistry.java \
  | sed 's/^s\.//' | sort -u

# identifiers referenced anywhere outside BomboConfig
find src/client/java -name '*.java' ! -name 'BomboConfig.java' -print0 \
  | xargs -0 grep -rhoE '[a-zA-Z_][a-zA-Z0-9_]*' | sort -u
```

## 2. Measured results

| Metric | Value |
| :--- | :--- |
| Public `Settings` fields | **745** |
| Fields referenced by the config GUI | **353** |
| Fields used in code but not listed in the config GUI | **379** |
| Fields declared and never referenced outside `BomboConfig` | **14** |
| Config categories in `BASE_CATEGORIES` | 39 |
| `switch` cases in `ConfigRegistry` | 42 |
| Categories with no implementation | **0** |
| Categories implemented but unreachable | **0** |
| `HudTarget` (HUD editor) entries | 28 |

`Debug`, `Dev` and `GUI Settings` are implemented as cases and appended explicitly by
`getAvailableCategories()`, so all 42 categories are reachable — no orphaned UI.

### Caveat on the 379

This bucket is **not** automatically "missing a GUI entry". The largest group is HUD
geometry (`*HudX`, `*HudY`, `*HudScale`, `*HudEnabled`) which is edited interactively in the
HUD editor (`/b gui`) rather than through a list row, and therefore never appears in
`ConfigRegistry`. Before treating any member of this list as unreachable, check:

1. Is it a `HudTarget`-backed position/scale field? → reachable via `/b gui`.
2. Is it read by a command handler in `BomboaddonsClient`/`CommandMixin`? → reachable.
3. Is it only ever written by a migration or a feature's own runtime? → intentional state.
4. Otherwise it is a genuine audit finding and belongs in the table in section 4.

## 3. Dead config fields (do not delete)

These 14 fields are declared in `BomboConfig.Settings` and referenced nowhere else in the
source tree. They are kept deliberately:

- They are **serialized**. Removing one silently drops the value from every user's JSON on
  the next save, and a downgrade re-reads the file with the field defaulted.
- Several look like intentionally reserved knobs (`hudStyles`, `previousThemeMode`,
  `loreOrder`, `commandDebug`, `ircCustomFormat`, `tooltipAlpha`).
- `lastServerIp` / `lastServerName` are state, not settings.

`autoCroesusKismetDelay`, `clearInfoHudX`, `clearInfoHudY`, `etherwarpMaxDistance`,
`signCalcX`, `signCalcY` are the only candidates that plausibly describe superseded
features. Removing any of them requires a config schema bump plus a migration entry in
`FlavorMigration`, which is more risk than the cleanup is worth. **Verdict: keep.**

Deletion policy for this repository:

- Never delete or rename a `BomboConfig.Settings` field without a schema bump + migration.
- Never reorder enum constants that are serialized by name.
- A class may only be deleted when it has zero inbound references **and** no reflective or
  string-based reference (mixin targets, `Class.forName`, JSON keys).
- `BomboConfigGUI` (legacy `/b old`) is frozen, not pruned — it is the fallback if the new
  GUI breaks.

## 4. Findings that were acted on

| Finding | Resolution |
| :--- | :--- |
| `ChatHistoryScreen` was fully implemented but **never instantiated** — zero references, and `/b chathistory` did not exist as a command. | Wired: `/b chathistory`, `/b chathistory auto`, `/b chathistory all`, a `chatHistoryKey` keybind, and a config GUI button. |
| `ChatHistoryTracker` had no way to record anything raised by a feature: `recordIncoming` early-returns on any text containing `[BomboAddons]`. | Added `recordEvent(...)` plus an `EVENT` status that bypasses the filter, and an `Auto Sequences` filter tab. |
| Auto sequence starts/stops were invisible in history. | Emitted with real provenance (`Keybind TAB`, `Mouse Button 5`, `Config GUI RUN button`, `Command /b auto run <name>`). |
| `ChatHistoryScreen` render and click geometry disagreed by 2px (`rowsTop` 16 vs 18), making the bottom row unclickable. | Single `listRowsTop()`/`listVisibleRows()` source of truth. |
| Auto sequences had no safety guards: a closed container made `CLICK_SLOT` a silent no-op forever. | 3x retry then halt, with the reason recorded (`container closed`, `slot not found`, `player left the world`, `sequence no longer exists`). |
| `.gitignore` contained a bare `config/`, which also matched `src/client/java/me/bombo/bomboaddons/gui/config/` and kept 5 build-critical sources out of version control. | Anchored to `/config/`; the 5 files are now tracked. |

## 5. Deferred

- The ~379 "no GUI list row" fields need a per-item pass to separate HUD-editor-backed
  geometry from genuine gaps. The four-question checklist in section 2 is the procedure.
- `FeatureOrganizerManager` classifies a feature as cheat when its display name contains
  `auto`, `macro` or `fast`. That heuristic misfires on legitimate names and should be
  replaced with explicit metadata from `ConfigRegistry.CHEAT_CATEGORIES` plus a per-item
  flag.
