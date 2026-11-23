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

## Added in v0.7
- **Predictions** (Visuals): trajectory and landing marker for the item in your hand (pearl, snowball, egg, potions, XP bottle, trident, bow/crossbow) and for projectiles already flying.
- **Groups window**: open it with the "Groups" button in the ClickGUI title bar (or Friends > Open Groups Window): create groups, click online players to invite them, type a name, remove members. Commands still work: `/group invite <player>` (group "team"), `/group add|remove <group> <player>`, `/group delete <group>`, `/group list`, plus "Add Nearest To Team" in Friends. Group members count as friends: KillAura/AutoCrystal/AutoAnchor never target them and ESP recolors them. Groups are local to your client (`config/aurora_groups.json`); other players are not notified.
- **KillAura presets** (Legit / Strict / Grim / Custom): limited FOV, aim at a drifting point in the hitbox with noise and mouse-sensitivity steps, attacks only when the crosshair is on the hitbox, random timing and rare missed swings. Not tested against any anti-cheat; it is not undetectable.

## Added in v0.8
- **TargetAura** (Combat): runs after the nearest enemy player and jumps (Follow or Orbit). Presses your movement keys and turns you toward the target; releases them when off. Friends and group members are ignored. It only moves you, KillAura does the hitting.
- **Hitboxes** (Visuals): colored, see-through hitboxes for players, hostile/passive mobs, items and projectiles. Display only, it does not enlarge real hitboxes.
- **Custom main menu**: replaces the title screen (Interface > Custom Main Menu). Singleplayer, Multiplayer, Options, Quit and **Accounts**.
- **Account switcher**: saved offline names (`config/aurora_accounts.json`), click to use. Works on cracked servers and singleplayer only; premium servers need a real login and will refuse these names.
- **TargetHUD styles**: Classic, Compact, Minimal, Modern, Neon, Ring, Bubble; bar styles Smooth, Segmented, Gradient; health or accent coloring; distance.

## Added in v0.9
- **Notifications**: slide-in toasts for module on/off, totem pops and deaths (Interface: side, position, time).
- **Module search**: field in the ClickGUI title bar; click it and type, right click clears.
- **TriggerBot** (Combat): hits what the crosshair is on with a human-like reaction delay, random charge threshold and rare skipped hits (Legit / Strict / Custom), optional crits only. Not tested against any anti-cheat.
- **PopCounter** (Combat): counts other players' totem pops (guessed from the totem's Regeneration II + Absorption effects), shows them in chat, toasts and nametags.
- **TimeChanger** (Visuals): fixed or cycling time of day, client-side only.
- **CustomCrosshair** (Visuals): Cross, Plus, Dot, Circle, Square, Cross + Dot; size, gap, thickness, outline, colors, enemy color, dynamic gap and an attack cooldown ring.

## Added in v1.0
- **Aurora loading and disconnect screens**: connecting, joining a world and the disconnect screen are painted in the Aurora style (logo, animated bar, reason text); the vanilla buttons still work. Turn off with Interface > Custom Main Menu.
- **Macros** (Utility): a key sends a command or chat message. Edit them in the Macros window (Macros > Open Macros Window), saved to `config/aurora_macros.json`.
- **More themes** (Sunset, Mint, Sakura, Cyber, Lava, Arctic, Lime, Midnight, Coral, Candy, Mono) and **gradient accents** (title bar line, notifications, TargetHUD gradient bar, loading bar). Interface > Gradient Accents / Animate Gradient / Custom Gradient Color.
- **Particles** (Visuals): Halo, Spiral, Orbit, Wings, Trail and Cloud effects with several particle types, optionally on other players. Only you see them. A custom cape is not possible without changing the game's player renderer.
- **FriendAlert** (Utility): toast, chat message and sound when a friend or group member joins or leaves the server, read from the tab list. Players already online when you join are not announced.
