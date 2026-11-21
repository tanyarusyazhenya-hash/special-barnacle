package dev.starfall;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class StarfallClient implements ClientModInitializer {
 public static KeyBinding menuKey, containersKey, spawnersKey;
 public static boolean containers=true, spawners=true;
 public static Finder finder;
 private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("starfall", "general"));
 @Override public void onInitializeClient() {
  menuKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.starfall.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,CATEGORY));
  containersKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.starfall.containers",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_K,"category.starfall"));
  spawnersKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.starfall.spawners",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_J,"category.starfall"));
  finder=new Finder();
  ClientTickEvents.END_CLIENT_TICK.register(client->{
   while(menuKey.wasPressed()) client.setScreen(new StarfallScreen());
   while(containersKey.wasPressed()) containers=!containers;
   while(spawnersKey.wasPressed()) spawners=!spawners;
   finder.tick(client);
  });
  HudRenderCallback.EVENT.register((draw, tick)->finder.render(draw));
 }
}