package dev.aurora;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.gui.ClickGuiScreen;
import dev.aurora.hud.HudRenderer;
import dev.aurora.hud.NametagRenderer;
import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.AutoReconnect;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspLine;
import dev.aurora.render.EspRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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
import net.minecraft.world.entity.Entity;
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
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> { dev.aurora.account.AccountManager.init(); dev.aurora.hud.Notifications.ready = true; });

		openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.aurora.open_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.screen != null && mc.screen.getClass() == net.minecraft.client.gui.screens.TitleScreen.class
				&& ModuleManager.ui.customMenu.value) {
				mc.setScreen(new dev.aurora.gui.AuroraMenuScreen());
			}
			while (openGui.consumeClick()) {
				if (mc.screen == null) {
					mc.setScreen(new ClickGuiScreen());
				}
			}
			ModuleManager.tick(mc);
		});

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(AuroraClient::onWorldRender);

		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess) -> ModuleManager.friends.registerCommands(dispatcher));
		dev.aurora.gui.ScreenOverlay.register();
		AutoReconnect.register();

		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, tickCounter) -> {
			if (ModuleManager.crosshair != null && ModuleManager.crosshair.isEnabled()) {
				dev.aurora.hud.CrosshairRenderer.render(graphics);
			} else {
				original.render(graphics, tickCounter);
			}
		});

		HudElementRegistry.attachElementAfter(
			VanillaHudElements.MISC_OVERLAYS,
			Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
			(graphics, tickCounter) -> {
				HudRenderer.render(graphics);
				NametagRenderer.render(graphics);
				dev.aurora.hud.TargetHudRenderer.render(graphics);
				dev.aurora.hud.Notifications.render(graphics);
			});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ModuleManager.shutdown();
			EspRenderer.close();
		});
	}

	private static void onWorldRender(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ModuleManager.frame(mc);
		if (mc.level == null || mc.player == null) {
			return;
		}
		List<EspBox> boxes = new ArrayList<>();
		List<EspLine> lines = new ArrayList<>();
		Vec3 camera = context.worldState().cameraRenderState.pos;
		{
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			Entity viewer = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
			Vec3 start = camera.add(viewer.getViewVector(partial).scale(0.6));
			ModuleManager.collectLines(lines, start);
		}
		ModuleManager.collectEsp(boxes);
		if (boxes.isEmpty() && lines.isEmpty()) {
			return;
		}
		EspRenderer.render(context.matrices(), camera, boxes, lines);
	}
}
