package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.NotaryInvocationActionPacket;
import dev.marrowseal.wardbound.net.OpenNotaryInvocationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Consent is required before the Bell of Final Witness will toll. */
public final class NotaryInvocationScreen extends Screen {
    private final OpenNotaryInvocationPacket msg;
    private NotaryInvocationScreen(OpenNotaryInvocationPacket msg){super(Component.literal("Service of Final Witness"));this.msg=msg;}
    public static void open(OpenNotaryInvocationPacket msg){Minecraft.getInstance().setScreen(new NotaryInvocationScreen(msg));}

    @Override protected void init(){int cx=width/2,y=height/2+44;
        Button accept=Button.builder(Component.literal("I ACCEPT SERVICE"),b->act(0)).bounds(cx-156,y,150,20).build();accept.active=msg.eligible();addRenderableWidget(accept);
        addRenderableWidget(Button.builder(Component.literal("I WITHDRAW"),b->act(1)).bounds(cx+6,y,150,20).build());
        addRenderableWidget(Button.builder(Component.literal("NOT YET"),b->onClose()).bounds(cx-50,y+28,100,20).build());
    }
    private void act(int a){Wardbound.CHANNEL.sendToServer(new NotaryInvocationActionPacket(a));onClose();}
    @Override public boolean isPauseScreen(){return false;}

    @Override public void render(GuiGraphics g,int mx,int my,float p){
        renderBackground(g);int cx=width/2,top=height/2-88,left=cx-178,right=cx+178,bottom=height/2+80;
        g.fill(left-9,top-9,right+9,bottom+9,0xB00B090E);g.fill(left,top,right,bottom,0xED08070B);
        g.fill(left+12,top+1,right-12,top+3,0xFFD8CDBA);g.fill(left+12,bottom-3,right-12,bottom-1,0xFF7D3444);
        g.fill(left+2,top+12,left+4,bottom-12,0xFF8C7857);g.fill(right-4,top+12,right-2,bottom-12,0xFF7B6A8B);
        drawSeal(g,cx,top+16);
        g.drawCenteredString(font,"SERVICE OF FINAL WITNESS",cx,top+31,0xFFE8DECD);
        g.drawCenteredString(font,"NOTICE OF APPEARANCE // THREE TOLLS REQUIRED",cx,top+45,0xFFAD9F89);
        g.drawCenteredString(font,"The bell has no tongue. It requires a witness willing to lend it one.",cx,top+61,0xFFCFC3AF);
        int y=top+82;for(String s:wrap(msg.status(),306)){g.drawCenteredString(font,s,cx,y,0xFFD8CCBC);y+=11;}
        g.drawCenteredString(font,msg.eligible()?"THE NOTICE MAY BE ACCEPTED":"THE RECORD DOES NOT YET ADMIT YOU",cx,bottom-34,msg.eligible()?0xFFC5B27B:0xFFB65D6F);
        super.render(g,mx,my,p);
    }
    private void drawSeal(GuiGraphics g,int cx,int cy){int ivory=0xFFE4D9C7,red=0xFF83394B,gold=0xFFAC8E59;g.fill(cx-26,cy,cx+26,cy+1,gold);g.fill(cx-16,cy-7,cx+16,cy+8,0xFF2A1720);g.fill(cx-12,cy-5,cx+12,cy+6,red);g.fill(cx-1,cy-8,cx+1,cy+9,ivory);g.fill(cx-8,cy,cx+8,cy+1,ivory);}
    private List<String> wrap(String text,int max){List<String> out=new ArrayList<>();StringBuilder cur=new StringBuilder();for(String w:text.split("\\s+")){String c=cur.length()==0?w:cur+" "+w;if(font.width(c)<=max||cur.length()==0){cur.setLength(0);cur.append(c);}else{out.add(cur.toString());cur.setLength(0);cur.append(w);}}if(cur.length()>0)out.add(cur.toString());return out;}
}
