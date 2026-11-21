package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Holds every HUD option; drawing lives in HudRenderer. */
public class HudModule extends Module {
	// Elements
	public final BoolSetting watermark = add(new BoolSetting("Watermark", true));
	public final BoolSetting moduleList = add(new BoolSetting("Module List", true));
	public final BoolSetting listInfo = add(new BoolSetting("List Mode Info", true));
	public final ModeSetting listSide = add(new ModeSetting("List Side", "Right", "Right", "Left"));
	public final BoolSetting fps = add(new BoolSetting("FPS", true));
	public final BoolSetting ping = add(new BoolSetting("Ping", true));
	public final BoolSetting coords = add(new BoolSetting("Coordinates", true));
	public final BoolSetting speed = add(new BoolSetting("Speed", true));
	public final BoolSetting direction = add(new BoolSetting("Direction", true));
	public final ModeSetting infoSide = add(new ModeSetting("Info Side", "Right", "Right", "Left"));

	// Look
	public final ModeSetting colorMode = add(new ModeSetting("Color Mode", "Accent", "Accent", "Rainbow", "White"));
	public final BoolSetting background = add(new BoolSetting("Background", true));
	public final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", 0.45, 0.0, 1.0, 0.05));
	public final BoolSetting accentBar = add(new BoolSetting("Accent Bar", true));
	public final BoolSetting shadow = add(new BoolSetting("Text Shadow", true));

	private double lastX, lastZ;
	private boolean hasLast;
	/** Horizontal speed in blocks per second, smoothed. */
	public double speedBps;

	public HudModule() {
		super("HUD", "On-screen overlay: watermark, module list, FPS, coordinates...", Category.HUD);
		setEnabled(true);
		listInfo.visibleWhen(() -> moduleList.value);
		listSide.visibleWhen(() -> moduleList.value);
		bgOpacity.visibleWhen(() -> background.value);
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void onDisable() {
		hasLast = false;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) {
			hasLast = false;
			speedBps = 0;
			return;
		}
		double x = mc.player.getX();
		double z = mc.player.getZ();
		if (hasLast) {
			double d = Math.hypot(x - lastX, z - lastZ) * 20.0;
			speedBps = speedBps * 0.7 + d * 0.3;
		}
		lastX = x;
		lastZ = z;
		hasLast = true;
	}
}
