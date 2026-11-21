package dev.starfall;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

public class Finder {
 private final List<String> found=new ArrayList<>();
 private int cooldown=0;
 public void tick(MinecraftClient mc){
  if(mc.player==null||mc.world==null||cooldown-->0)return;
  cooldown=40; found.clear();
  BlockPos p=mc.player.getBlockPos(); int radius=12;
  for(int x=-radius;x<=radius;x++) for(int y=-radius;y<=radius;y++) for(int z=-radius;z<=radius;z++){
   BlockPos pos=p.add(x,y,z); Block b=mc.world.getBlockState(pos).getBlock();
   if(StarfallClient.containers&&isContainer(b)) found.add("Container "+pos.getX()+" "+pos.getY()+" "+pos.getZ());
   if(StarfallClient.spawners&&b==Blocks.SPAWNER) found.add("Spawner "+pos.getX()+" "+pos.getY()+" "+pos.getZ());
  }
 }
 private boolean isContainer(Block b){return b==Blocks.CHEST||b==Blocks.TRAPPED_CHEST||b==Blocks.BARREL||b==Blocks.DISPENSER||b==Blocks.DROPPER||b==Blocks.HOPPER||b==Blocks.SHULKER_BOX||b==Blocks.WHITE_SHULKER_BOX||b==Blocks.ORANGE_SHULKER_BOX||b==Blocks.MAGENTA_SHULKER_BOX||b==Blocks.LIGHT_BLUE_SHULKER_BOX||b==Blocks.YELLOW_SHULKER_BOX||b==Blocks.LIME_SHULKER_BOX||b==Blocks.PINK_SHULKER_BOX||b==Blocks.GRAY_SHULKER_BOX||b==Blocks.LIGHT_GRAY_SHULKER_BOX||b==Blocks.CYAN_SHULKER_BOX||b==Blocks.PURPLE_SHULKER_BOX||b==Blocks.BLUE_SHULKER_BOX||b==Blocks.BROWN_SHULKER_BOX||b==Blocks.GREEN_SHULKER_BOX||b==Blocks.RED_SHULKER_BOX||b==Blocks.BLACK_SHULKER_BOX;}
 public void render(DrawContext d){
  MinecraftClient mc=MinecraftClient.getInstance(); if(mc.player==null)return;
  int x=8,y=8; d.fill(x-4,y-4,x+165,y+22+(Math.min(found.size(),6)*12),0x990B1020);
  d.drawTextWithShadow(mc.textRenderer,"STARFALL",x,y,0xFF80D8FF);
  d.drawTextWithShadow(mc.textRenderer,"Containers: "+(StarfallClient.containers?"ON":"OFF")+"  [K]",x,y+12,0xFFE4EAF5);
  d.drawTextWithShadow(mc.textRenderer,"Spawners: "+(StarfallClient.spawners?"ON":"OFF")+"  [J]",x,y+24,0xFFE4EAF5);
  int yy=y+38; for(String s:found){if(yy>y+110)break;d.drawTextWithShadow(mc.textRenderer,s,x,yy,0xFF9AE6B4);yy+=12;}
 }
}