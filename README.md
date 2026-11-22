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

## Added in v0.3 (Utility tab)
- **Freecam**: a client-side camera flies around while your character stays still (no rotation is sent to the server). WASD, Space/Shift for up/down, Sprint for x2. Turns off on damage, death or leaving the world. Clicks still aim from the camera.
- **AutoSprint**: holds sprint while walking forward (stops when hungry or using an item).
- **AutoReconnect**: after a disconnect, counts down and reconnects to the last server, retrying up to the attempt limit. Esc cancels.
- **Friends**: `/friend add|remove|list|clear <name>` or the "Add Nearest Player" button. Friends are recolored (with a [F] tag) or hidden in PlayerESP, Nametags and Tracers.
- **Zoom** (moved here): Hold or Toggle bind mode, mouse wheel changes the zoom while active, smooth easing. Vanilla limits FOV to 30 at minimum.
- Modules with a "Hold" bind mode are active only while the key is down.

## Added in v0.4
- **New ClickGUI** (matches the reference): one window with an orange glow, title bar (minimize and close), category sidebar, a card with the modules of the category and a card with the settings of the selected module. Pill toggles, dropdowns, sliders, color picker, key binds. Left click a module to toggle it, right click to only open its settings. Drag the title bar to move the window.
- **Orange default theme** (Interface > Theme). The HUD uses the same accent and has an "A" logo badge in the watermark.
- **AutoTotem** (Combat): keeps a totem in the offhand, with a tick delay, a "swap at health" limit and a hotbar-only option. Off by default; many servers block automation like this.

## Added in v0.5 (Combat tab)
All three start disabled on every launch, never target friends, and do nothing while Freecam is on. Most servers ban modules like these.
- **KillAura**: attacks the best target in reach when the attack cooldown is ready. Targets: players, hostile and passive mobs. Priority (distance or health), range, cooldown, rotation (None / Snap / Smooth), weapon-only, line of sight check.
- **AutoCrystal**: places end crystals on obsidian or bedrock next to the nearest enemy and breaks them. Damage is estimated with the game's formula (cover, armor, Blast Protection, difficulty; totems not counted). The module list in the HUD shows its status or why it is idle. Option to target hostile mobs. Min target damage, max self damage, anti-suicide, delays, ranges, auto switch, rotate.
- **AutoAnchor**: places a respawn anchor next to the nearest enemy, charges it with glowstone and detonates it. Does nothing in the Nether (anchors only set spawn there). Same safety limits as AutoCrystal.

## Welcome text
Opening the ClickGUI shows "Здравствуй" at the top with "Создатель: winidy" under it. Turn it off with Interface > Welcome Text.

## Building
Easiest: generate a 1.21.11 template at https://fabricmc.net/develop (Mojang mappings), then copy `src/` over it
(merge `fabric.mod.json`). Or use the included Gradle files after updating the versions in `gradle.properties`
and adding a Gradle wrapper (`gradle wrapper`). Requires Java 21.
`./gradlew build` produces the jar in `build/libs/`.

## Added in v0.6
- **TargetHUD** (HUD tab): card with head, name, smooth health bar and armor/hand items of the entity under your crosshair (or the nearest player), lingers a few seconds.
- **ClickPearl** (Combat): bind it to a key; throws a pearl instantly and switches back.
- **AutoTotem** moved to Combat. The Render tab is now **Visuals** and also holds Fullbright and Zoom.
