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
**PlayerESP**: other players, smoothly interpolated. Static color or health color (red to green), optional invisible/spectator players.
All three ESP modules share: Mode (Outline / Filled / Both / Corners), Outline Opacity, Fill Opacity, Thickness, Color Mode (Static / Rainbow / Pulse / Distance), Through Walls, Range.

**HUD**: watermark, module list (with each module's current mode), FPS, ping, coordinates, speed, facing. Choose the side of the module list and the info stack, color mode (Accent / Rainbow / White), background, opacity, accent bar and text shadow.

## Added in v0.2
- **Nametags** (Render): name, health, distance and held item above players, with background, color mode and height. Works in first person.
- **Tracers** (Render): lines from the crosshair to players, storage, spawners, entities and waypoints.
- **EntityESP** (Render): hostile and passive mobs, dropped items, item frames.
- **BlockFinder** (Render): selected ores (diamond, ancient debris, emerald, gold, iron, lapis, redstone, coal, copper, quartz), scanned a few chunks per tick.
- **Waypoints** (World): remembers spawners and stashes per server (`config/aurora_waypoints.json`), shown in the HUD and as tracers.
- **Fullbright**, **Zoom** (World): Zoom always starts disabled.
- **Keybinds**: every module has a Bind row (click, press a key; right click clears).
- **Profiles**: Interface has Save/Load for 4 profiles. **Themes**: Purple, Ocean, Emerald, Ruby, Gold, Rose, Custom, Rainbow.
- **HUD** extras: armor and durability, potion effects, nearby players, waypoint list, session time, totem count.

Note: ore finders, Fullbright and similar tools are banned on many servers. Use them only where allowed.

## Building
Easiest: generate a 1.21.11 template at https://fabricmc.net/develop (Mojang mappings), then copy `src/` over it
(merge `fabric.mod.json`). Or use the included Gradle files after updating the versions in `gradle.properties`
and adding a Gradle wrapper (`gradle wrapper`). Requires Java 21.
`./gradlew build` produces the jar in `build/libs/`.
