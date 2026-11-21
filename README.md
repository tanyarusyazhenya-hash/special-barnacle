# Aurora (Fabric 1.21.11)

Client-side mod with a ClickGUI and two ESP modules.

## Controls
- **Right Shift**: open/close the ClickGUI (rebindable in Controls)
- Left click a module: toggle. Right click: expand its settings.
- Drag a panel header to move it, right click a header to collapse it, scroll to scroll.
- Settings: click bools/modes (right click on a mode goes backwards), drag sliders, click a color row to open the picker.
- Config is saved to `config/aurora.json`.

## Modules
**StorageFinder**: chests, trapped chests, shulker boxes, droppers (plus ender chests, barrels, dispensers, off by default). Per-type colors, double chests merged.
**SpawnerFinder**: spawners (and trial spawners, off by default), Y range filter, chat notification.
Both share: Mode (Outline / Filled / Both / Corners), Outline Opacity, Fill Opacity, Thickness, Color Mode (Static / Rainbow / Pulse / Distance), Through Walls, Range.

## Building
Easiest: generate a 1.21.11 template at https://fabricmc.net/develop (Mojang mappings), then copy `src/` over it
(merge `fabric.mod.json`). Or use the included Gradle files after updating the versions in `gradle.properties`
and adding a Gradle wrapper (`gradle wrapper`). Requires Java 21.
`./gradlew build` produces the jar in `build/libs/`.
