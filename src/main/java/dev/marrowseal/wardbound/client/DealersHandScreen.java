package dev.marrowseal.wardbound.client;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.DealerHandSkills;
import dev.marrowseal.wardbound.net.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class DealersHandScreen extends Screen{
 private final OpenDealersHandPacket msg;private boolean answered;
 public DealersHandScreen(OpenDealersHandPacket m){super(Component.literal("Dealer's Hand"));msg=m;}
 private int w(){return Math.min(250,Math.max(170,(width-54)/2));}private int h(){return Math.min(270,Math.max(190,height-105));}private int y(){return Math.max(58,(height-h())/2);}private int lx(){return width/2-w()-11;}private int rx(){return width/2+11;}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.drawCenteredString(font,"DEALER'S HAND",width/2,20,0xFFFFD77A);g.drawCenteredString(font,"The House has offered violence, not law.",width/2,34,0xFFD4C3D7);card(g,lx(),y(),w(),h(),mx,my,msg.left(),0);card(g,rx(),y(),w(),h(),mx,my,msg.right(),1);g.drawCenteredString(font,"ESC closes the hand. The same draw waits until it expires.",width/2,Math.min(height-12,y()+h()+12),0xFF8D838C);super.render(g,mx,my,partial);}
 private void card(GuiGraphics g,int x,int y,int w,int h,int mx,int my,int skill,int choice){boolean hot=inside(mx,my,x,y,w,h);int accent=hot?0xFFFFD36D:0xFF9C6B49;g.fill(x-2,y-2,x+w+2,y+h+2,accent);g.fill(x,y,x+w,y+h,0xFF09090D);g.fill(x+5,y+5,x+w-5,y+h-5,0xFF17101C);g.fill(x+9,y+10,x+w-9,y+12,accent);g.drawCenteredString(font,DealerHandSkills.title(skill),x+w/2,y+28,hot?0xFFFFE6A6:0xFFD8C39D);int yy=y+54;for(var line:font.split(Component.literal(DealerHandSkills.description(skill)),w-28)){g.drawCenteredString(font,line,x+w/2,yy,0xFFC7BDC9);yy+=10;}g.drawCenteredString(font,hot?"PLAY THIS CARD":"",x+w/2,y+h-25,accent);}
 private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
 @Override public boolean mouseClicked(double mx,double my,int b){if(b==0&&!answered){if(inside(mx,my,lx(),y(),w(),h())){choose(0);return true;}if(inside(mx,my,rx(),y(),w(),h())){choose(1);return true;}}return super.mouseClicked(mx,my,b);}
 private void choose(int c){answered=true;Wardbound.CHANNEL.sendToServer(new DealersHandChoicePacket(c,msg.nonce()));if(minecraft!=null)minecraft.setScreen(null);}
 @Override public boolean shouldCloseOnEsc(){return true;}@Override public boolean isPauseScreen(){return false;}
}
