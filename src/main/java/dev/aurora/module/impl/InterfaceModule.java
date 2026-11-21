package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;

/** Always-on module that holds ClickGUI appearance settings. */
public class InterfaceModule extends Module {
	public final ColorSetting accentColor = add(new ColorSetting("Accent", 0x8B5CF6));
	public final BoolSetting rainbow = add(new BoolSetting("Rainbow Accent", false));
	public final BoolSetting dim = add(new BoolSetting("Dim Background", true));
	public final NumberSetting dimOpacity = add(new NumberSetting("Dim Opacity", 0.45, 0.0, 0.9, 0.05));

	public InterfaceModule() {
		super("Interface", "ClickGUI appearance", Category.CLIENT);
		accentColor.visibleWhen(() -> !rainbow.value);
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
		return accentColor.rgb();
	}
}
