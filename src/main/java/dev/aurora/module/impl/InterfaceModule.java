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
		0xF472B6  // Rose
	};

	public final ModeSetting theme = add(new ModeSetting("Theme", "Orange",
		"Custom", "Orange", "Purple", "Ocean", "Emerald", "Ruby", "Gold", "Rose"));
	public final ColorSetting accentColor = add(new ColorSetting("Custom Accent", 0xFF7A1A));
	public final BoolSetting rainbow = add(new BoolSetting("Rainbow Accent", false));
	public final BoolSetting dim = add(new BoolSetting("Dim Background", true));
	public final NumberSetting dimOpacity = add(new NumberSetting("Dim Opacity", 0.45, 0.0, 0.9, 0.05));
	public final BoolSetting greeting = add(new BoolSetting("Welcome Text", true));
	public final BoolSetting customMenu = add(new BoolSetting("Custom Main Menu", true));

	public final ModeSetting profile = add(new ModeSetting("Profile", "1", "1", "2", "3", "4"));
	public final ActionSetting saveProfile = add(new ActionSetting("Save Profile", () -> ModuleManager.saveProfile(profile.get())));
	public final ActionSetting loadProfile = add(new ActionSetting("Load Profile", () -> ModuleManager.loadProfile(profile.get())));

	public InterfaceModule() {
		super("Interface", "ClickGUI theme and config profiles", Category.CLIENT);
		theme.visibleWhen(() -> !rainbow.value);
		accentColor.visibleWhen(() -> !rainbow.value && theme.is("Custom"));
		dimOpacity.visibleWhen(() -> dim.value);
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
}
