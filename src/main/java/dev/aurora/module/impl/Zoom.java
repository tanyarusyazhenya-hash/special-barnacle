package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;

/**
 * Zoom by lowering the FOV option. Scroll wheel changes the zoom while active, and the FOV is eased
 * toward its target every frame. Vanilla does not allow an FOV below 30, so that is the limit.
 */
public class Zoom extends Module {
	private static final int MIN_FOV = 30;

	public final ModeSetting bindMode = add(new ModeSetting("Bind Mode", "Hold", "Hold", "Toggle"));
	public final NumberSetting fov = add(new NumberSetting("Zoom FOV", 30, MIN_FOV, 60, 1));
	public final BoolSetting scroll = add(new BoolSetting("Mouse Wheel", true));
	public final NumberSetting scrollStep = add(new NumberSetting("Wheel Step", 4, 1, 10, 1));
	public final BoolSetting smooth = add(new BoolSetting("Smooth", true));
	public final NumberSetting smoothSpeed = add(new NumberSetting("Smooth Speed", 0.25, 0.05, 0.8, 0.05));

	/** FOV before zooming, or -1 when nothing needs restoring. */
	private int original = -1;
	private int target;
	private boolean animating;
	private boolean hookInstalled;

	public Zoom() {
		super("Zoom", "Zoom in. Hold the bind, scroll to adjust", Category.UTILITY);
		scrollStep.visibleWhen(() -> scroll.value);
		smoothSpeed.visibleWhen(() -> smooth.value);
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	public boolean holdToActivate() {
		return bindMode.is("Hold");
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) {
			return;
		}
		if (original < 0) {
			original = mc.options.fov().get();
		}
		target = Mth.clamp(fov.i(), MIN_FOV, Math.max(MIN_FOV, original));
		animating = true;
	}

	@Override
	protected void onDisable() {
		if (original < 0) {
			return;
		}
		if (!smooth.value) {
			restoreNow(Minecraft.getInstance());
		} else {
			animating = true;
		}
	}

	/** Called every rendered frame (even while disabled, so the restore animation can finish). */
	public void frame(Minecraft mc) {
		if (!animating || mc.options == null) {
			return;
		}
		int current = mc.options.fov().get();
		int goal = isEnabled() ? target : original;
		if (goal < 0 || current == goal) {
			if (!isEnabled()) {
				original = -1;
			}
			animating = isEnabled() && current != goal;
			return;
		}
		int diff = Math.abs(goal - current);
		int step = smooth.value ? Math.min(diff, Math.max(1, (int) Math.ceil(diff * smoothSpeed.value))) : diff;
		mc.options.fov().set(current < goal ? current + step : current - step);
	}

	/** Mouse wheel while zoomed: smaller FOV = more zoom. */
	public void adjust(double wheel) {
		int max = Math.max(MIN_FOV, original);
		target = Mth.clamp(target - (int) Math.signum(wheel) * scrollStep.i(), MIN_FOV, max);
		animating = true;
	}

	/** Put the FOV back immediately (shutdown, leaving the world). */
	public void restoreNow(Minecraft mc) {
		if (original > 0 && mc.options != null) {
			mc.options.fov().set(original);
		}
		original = -1;
		animating = false;
	}

	/** Leaving the world while zoomed would otherwise leave the FOV option changed. */
	public void safetyCheck(Minecraft mc) {
		if (mc.level == null && (isEnabled() || original > 0)) {
			boolean wasEnabled = isEnabled();
			if (wasEnabled) {
				setEnabled(false);
			}
			restoreNow(mc);
		}
	}

	/**
	 * Wraps the window's scroll callback so the wheel zooms instead of changing the hotbar while the
	 * module is active. Installed lazily from the first client tick, when the window already exists.
	 */
	public void installScrollHook() {
		if (hookInstalled) {
			return;
		}
		long window = GLFW.glfwGetCurrentContext();
		if (window == 0L) {
			return;
		}
		hookInstalled = true;
		GLFWScrollCallback previous = GLFW.glfwSetScrollCallback(window, null);
		GLFW.glfwSetScrollCallback(window, (w, x, y) -> {
			Minecraft mc = Minecraft.getInstance();
			if (isEnabled() && scroll.value && mc.screen == null && y != 0) {
				adjust(y);
				return;
			}
			if (previous != null) {
				previous.invoke(w, x, y);
			}
		});
	}
}
