package dev.aurora;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.gui.ClickGuiScreen;
import dev.aurora.hud.HudRenderer;
import dev.aurora.module.ModuleManager;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AuroraClient implements ClientModInitializer {
	public static final String MOD_ID = "aurora";

	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping openGui;

	@Override
	public void onInitializeClient() {
		ModuleManager.init();
		ModuleManager.load();

		openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.aurora.open_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openGui.consumeClick()) {
				if (mc.screen == null) {
					mc.setScreen(new ClickGuiScreen());
				}
			}
			ModuleManager.tick(mc);
		});

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(AuroraClient::onWorldRender);

		HudElementRegistry.attachElementAfter(
			VanillaHudElements.MISC_OVERLAYS,
			Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
			(graphics, tickCounter) -> HudRenderer.render(graphics));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ModuleManager.save();
			EspRenderer.close();
		});
	}

	private static void onWorldRender(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		List<EspBox> boxes = new ArrayList<>();
		ModuleManager.collectEsp(boxes);
		if (boxes.isEmpty()) {
			return;
		}
		Vec3 camera = context.worldState().cameraRenderState.pos;
		EspRenderer.render(context.matrices(), camera, boxes);
	}
}
