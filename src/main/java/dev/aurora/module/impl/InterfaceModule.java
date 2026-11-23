package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.setting.ActionSetting;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;

/** Always-on module: ClickGUI look (themes) and config profiles. */
public class InterfaceModule extends Module {
	private static final int[] THEME_COLORS = {
		0xFF7A1A, // Orange
		0x8B5CF6, // Purple
		0x38BDF8, // Ocean
		0x34D399, // Emerald
		0xEF4444, // Ruby
		0xF5B83D, // Gold
		0xF472B6, // Rose
		0xFF5F6D, // Sunset
		0x2DD4BF, // Mint
		0xF9A8D4, // Sakura
		0x00E5FF, // Cyber
		0xFF3D00, // Lava
		0x7DD3FC, // Arctic
		0xA3E635, // Lime
		0x6366F1, // Midnight
		0xFB7185, // Coral
		0xF472B6, // Candy
		0xE5E7EB  // Mono
	};

	/** Second color of each theme, used by gradients. Same order as THEME_COLORS. */
	private static final int[] THEME_COLORS_2 = {
		0xFFC24A, 0xC084FC, 0x818CF8, 0x86EFAC, 0xFB923C, 0xFDE68A, 0xFB7185,
		0xFFC371, 0xA7F3D0, 0xC084FC, 0xFF2BD6, 0xFFB300, 0xE0F2FE, 0x22C55E,
		0x0EA5E9, 0xFDBA74, 0x60A5FA, 0x6B7280
	};

	public final ModeSetting theme = add(new ModeSetting("Theme", "Orange",
		"Custom", "Orange", "Purple", "Ocean", "Emerald", "Ruby", "Gold", "Rose", "Sunset", "Mint", "Sakura", "Cyber",
		"Lava", "Arctic", "Lime", "Midnight", "Coral", "Candy", "Mono"));
	public final ColorSetting accentColor = add(new ColorSetting("Custom Accent", 0xFF7A1A));
	public final BoolSetting gradient = add(new BoolSetting("Gradient Accents", true));
	public final BoolSetting gradientAnim = add(new BoolSetting("Animate Gradient", true));
	public final ColorSetting accentColor2 = add(new ColorSetting("Custom Gradient Color", 0xFFC24A));
	public final BoolSetting rainbow = add(new BoolSetting("Rainbow Accent", false));
	public final BoolSetting dim = add(new BoolSetting("Dim Background", true));
	public final NumberSetting dimOpacity = add(new NumberSetting("Dim Opacity", 0.45, 0.0, 0.9, 0.05));
	public final BoolSetting greeting = add(new BoolSetting("Welcome Text", true));
	public final BoolSetting notifications = add(new BoolSetting("Notifications", true));
	public final BoolSetting notifyToggles = add(new BoolSetting("Notify Module Toggles", true));
	public final ModeSetting notifySide = add(new ModeSetting("Notification Side", "Right", "Right", "Left"));
	public final ModeSetting notifyPos = add(new ModeSetting("Notification Position", "Bottom", "Bottom", "Top"));
	public final NumberSetting notifyTime = add(new NumberSetting("Notification Time (s)", 2.5, 1.0, 8.0, 0.5));
	public final BoolSetting customMenu = add(new BoolSetting("Custom Main Menu", true));

	public final ModeSetting profile = add(new ModeSetting("Profile", "1", "1", "2", "3", "4"));
	public final ActionSetting saveProfile = add(new ActionSetting("Save Profile", () -> ModuleManager.saveProfile(profile.get())));
	public final ActionSetting loadProfile = add(new ActionSetting("Load Profile", () -> ModuleManager.loadProfile(profile.get())));

	public InterfaceModule() {
		super("Interface", "ClickGUI theme and config profiles", Category.CLIENT);
		theme.visibleWhen(() -> !rainbow.value);
		accentColor.visibleWhen(() -> !rainbow.value && theme.is("Custom"));
		dimOpacity.visibleWhen(() -> dim.value);
		gradientAnim.visibleWhen(() -> gradient.value);
		accentColor2.visibleWhen(() -> gradient.value && theme.is("Custom") && !rainbow.value);
		notifyToggles.visibleWhen(() -> notifications.value);
		notifySide.visibleWhen(() -> notifications.value);
		notifyPos.visibleWhen(() -> notifications.value);
		notifyTime.visibleWhen(() -> notifications.value);
		setEnabled(true);
	}

	@Override
	public boolean hasToggle() {
		return false;
	}

	public int accent() {
		if (rainbow.value) {
			return ColorUtil.hsb((System.currentTimeMillis() % 5000L) / 5000f, 0.65f, 1f);
		}
		int i = theme.index();
		if (i == 0) {
			return accentColor.rgb();
		}
		return THEME_COLORS[i - 1];
	}

	public int accent2() {
		if (rainbow.value) {
			return ColorUtil.hsb(((System.currentTimeMillis() + 1500L) % 5000L) / 5000f, 0.65f, 1f);
		}
		int i = theme.index();
		return i == 0 ? accentColor2.rgb() : THEME_COLORS_2[i - 1];
	}

	/** Color at position t (0..1) along the accent gradient; animated if enabled. Plain accent if gradients are off. */
	public int gradientAt(float t) {
		if (!gradient.value) {
			return accent();
		}
		float k = t;
		if (gradientAnim.value) {
			k = (t + (System.currentTimeMillis() % 4000L) / 4000f) % 1f;
		}
		k = k < 0.5f ? k * 2f : (1f - k) * 2f; // ping-pong so it loops without a seam
		int a = accent();
		int b = accent2();
		int r = (int) (((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
		int g = (int) (((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
		int bl = (int) ((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
		return (r << 16) | (g << 8) | bl;
	}
}
