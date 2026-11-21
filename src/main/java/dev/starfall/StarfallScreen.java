package dev.starfall;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.Click;

public class StarfallScreen extends Screen {
 public StarfallScreen(){super(Text.literal("Starfall Client"));}
 @Override public void render(DrawContext d,int mouseX,int mouseY,float delta){
  renderBackground(d,mouseX,mouseY,delta);
  int cx=width/2,cy=height/2,w=230,h=170;
  d.fill(cx-w/2,cy-h/2,cx+w/2,cy+h/2,0xE9111725);
  d.fill(cx-w/2,cy-h/2,cx+w/2,cy-h/2+34,0xFF263A59);
  d.drawCenteredTextWithShadow(textRenderer,"STARFALL CLIENT",cx,cy-h/2+10,0xFF8BD9FF);
  d.drawCenteredTextWithShadow(textRenderer,"Utility modules",cx,cy-h/2+25,0xFFAABBD0);
  button(d,cx,cy-35,"Containers Finder",StarfallClient.containers);
  button(d,cx,cy-5,"Spawner Finder",StarfallClient.spawners);
  d.drawCenteredTextWithShadow(textRenderer,"K / J toggle modules • Right Shift close",cx,cy+55,0xFFB7C5D8);
  d.drawCenteredTextWithShadow(textRenderer,"Scan radius: 12 blocks",cx,cy+70,0xFF7C91AA);
 }
 private void button(DrawContext d,int cx,int cy,String label,boolean enabled){
  int c=enabled?0xFF244D66:0xFF303746; d.fill(cx-90,cy-10,cx+90,cy+12,c);
  d.drawCenteredTextWithShadow(textRenderer,label+"  "+(enabled?"ON":"OFF"),cx,cy-4,enabled?0xFF9AE6B4:0xFFD2D8E2);
 }
 @Override public boolean mouseClicked(Click click, boolean doubled){
  double x=click.x(), y=click.y();
  int cx=width/2,cy=height/2;
  if(click.button()==0&&x>=cx-90&&x<=cx+90){
   if(y>=cy-45&&y<=cy-23)StarfallClient.containers=!StarfallClient.containers;
   if(y>=cy-15&&y<=cy+7)StarfallClient.spawners=!StarfallClient.spawners;
  }
  return super.mouseClicked(click,doubled);
 }
 @Override public boolean shouldPause(){return false;}
}