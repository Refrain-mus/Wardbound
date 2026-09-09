package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.CuratorInvocationActionPacket;
import dev.marrowseal.wardbound.net.OpenCuratorInvocationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The Archive Codex is a decision surface, not a generic confirmation dialog. */
public final class CuratorInvocationScreen extends Screen {
    private final OpenCuratorInvocationPacket msg;
    private CuratorInvocationScreen(OpenCuratorInvocationPacket msg){super(Component.literal("Ashen Archive Codex"));this.msg=msg;}
    public static void open(OpenCuratorInvocationPacket msg){Minecraft.getInstance().setScreen(new CuratorInvocationScreen(msg));}

    @Override protected void init(){
        int cx=width/2, y=height/2+52;
        if(!msg.evidenceReady()){
            addRenderableWidget(Button.builder(Component.literal("CLOSE THE CODEX"),b->onClose()).bounds(cx-75,y,150,20).build());
        }else if(msg.recordChoice()==0){
            addRenderableWidget(Button.builder(Component.literal("PRESERVE THE TESTIMONY"),b->act(0)).bounds(cx-156,y,150,20).build());
            addRenderableWidget(Button.builder(Component.literal("BURN THE TESTIMONY"),b->act(1)).bounds(cx+6,y,150,20).build());
        }else{
            addRenderableWidget(Button.builder(Component.literal("ENTER THE PREPARED ARCHIVE"),b->act(2)).bounds(cx-90,y,180,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("NOT YET"),b->onClose()).bounds(cx-50,y+28,100,20).build());
    }

    private void act(int action){Wardbound.CHANNEL.sendToServer(new CuratorInvocationActionPacket(action));onClose();}
    @Override public boolean isPauseScreen(){return false;}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        renderBackground(g);
        int cx=width/2, top=height/2-98;
        int left=cx-190,right=cx+190,bottom=height/2+92;
        g.fill(left-14,top-14,right+14,bottom+14,0x9C090706);
        g.fill(left-6,top-6,right+6,bottom+6,0xB31A120D);
        g.fill(left,top,right,bottom,0xF1080706);
        g.fill(left+14,top+8,right-14,top+10,0xFFD2B98B);
        g.fill(left+14,bottom-10,right-14,bottom-8,0xFFAB6B4A);
        g.fill(left+10,top+22,left+13,bottom-22,0xFF8C6A47);
        g.fill(right-13,top+22,right-10,bottom-22,0xFFB08C57);
        drawFiligree(g,left,top,right,bottom);
        drawGlyph(g,cx,top+20);
        g.drawCenteredString(font,"ASHEN ARCHIVE CODEX",cx,top+34,0xFFF2E3C7);
        g.drawCenteredString(font,"A returned testimony cannot be opened without becoming evidence.",cx,top+48,0xFFCDBA9A);
        String choice=msg.recordChoice()==0?"UNDECIDED":msg.recordChoice()==1?"PRESERVED":"BURNED";
        g.drawCenteredString(font,"TESTIMONY // "+choice,cx,top+66,msg.recordChoice()==1?0xFFA8D7B0:msg.recordChoice()==2?0xFFD98A5A:0xFFD6C09A);
        List<String> lines=wrap(msg.status(),324);
        int y=top+86;for(String s:lines){g.drawCenteredString(font,s,cx,y,0xFFE7DBC7);y+=11;}
        g.drawCenteredString(font,"Every page that survives becomes part of the archive's architecture.",cx,bottom-26,0xFF998571);
        super.render(g,mouseX,mouseY,partial);
    }

    private void drawFiligree(GuiGraphics g,int left,int top,int right,int bottom){
        int ember=0xFFBC734E, ash=0xFF86756A, pale=0xFFE9D8BA;
        for(int i=0;i<4;i++){
            int ox=i*3;
            g.fill(left+18+ox,top+18,left+42+ox,top+19,ash);
            g.fill(right-42-ox,top+18,right-18-ox,top+19,ash);
            g.fill(left+18+ox,bottom-19,left+42+ox,bottom-18,ash);
            g.fill(right-42-ox,bottom-19,right-18-ox,bottom-18,ash);
        }
        g.fill(left+24,top+18,left+26,top+42,pale);g.fill(right-26,top+18,right-24,top+42,pale);
        g.fill(left+24,bottom-42,left+26,bottom-18,pale);g.fill(right-26,bottom-42,right-24,bottom-18,pale);
        g.fill(left+54,top+58,right-54,top+59,ember);
    }

    private void drawGlyph(GuiGraphics g,int cx,int cy){
        int ivory=0xFFE8D6B8,gold=0xFFB99A64,ember=0xFFC56D45;
        g.fill(cx-26,cy,cx+26,cy+1,gold);g.fill(cx-18,cy-5,cx+18,cy-4,ivory);
        g.fill(cx-8,cy-9,cx-6,cy+9,ivory);g.fill(cx+6,cy-9,cx+8,cy+9,ivory);
        g.fill(cx-2,cy-11,cx+2,cy-9,ember);g.fill(cx-3,cy+9,cx+3,cy+11,ember);
    }
    private List<String> wrap(String text,int max){
        List<String> out=new ArrayList<>();StringBuilder cur=new StringBuilder();
        for(String w:text.split("\\s+")){String c=cur.length()==0?w:cur+" "+w;if(font.width(c)<=max||cur.length()==0){cur.setLength(0);cur.append(c);}else{out.add(cur.toString());cur.setLength(0);cur.append(w);}}
        if(cur.length()>0)out.add(cur.toString());return out;
    }
}
