package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.MourningNotaryEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Short legal/funeral callouts; never steals player input. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class NotaryScreenFx {
    private static final Map<UUID,Integer> LAST=new HashMap<>();private static String title="",sub="";private static long start,duration;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();if(mc.level==null){LAST.clear();return;}Set<UUID> live=new HashSet<>();for(var ent:mc.level.entitiesForRendering())if(ent instanceof MourningNotaryEntity b&&!b.isRemoved()){live.add(b.getUUID());int st=b.view().getInt("state");Integer old=LAST.put(b.getUUID(),st);if(old==null){if(st==MourningNotaryEntity.MANIFEST)trigger("NOTICE OF APPEARANCE","THE COURT ACCEPTS A WITNESS",1200);continue;}if(old==st)continue;switch(st){case MourningNotaryEntity.DECLARE->trigger("DECLARATION","THE RULED LINE IS NOW EVIDENCE",760);case MourningNotaryEntity.STAMP->trigger("SEALED FINDINGS","VACATE THE MARKED SEALS",760);case MourningNotaryEntity.TOLL->trigger("TEMPORARY LAW",b.view().getInt("rule")==1?"MOVEMENT IS REQUIRED":"STILLNESS IS REQUIRED",880);case MourningNotaryEntity.JUDGMENT->trigger("JOINT JUDGMENT","THE RECORD FILES THREE LAWS",1000);case MourningNotaryEntity.UNBOUND->trigger("SIGNATURE BROKEN","AMEND THE AUTHOR",800);case MourningNotaryEntity.COLLAPSE->trigger("FINAL ENTRY","THE LAST SIGNATURE IS WITHHELD",1300);}}LAST.keySet().retainAll(live);}
    private static void trigger(String a,String b,long d){title=a;sub=b;start=System.currentTimeMillis();duration=d;}
    @SubscribeEvent public static void render(RenderGuiEvent.Post e){if(title.isEmpty())return;long age=System.currentTimeMillis()-start;if(age<0||age>=duration){title="";sub="";return;}Minecraft mc=Minecraft.getInstance();float f=Math.min(1,age/120f)*Math.min(1,(duration-age)/190f);GuiGraphics g=e.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),top=13;g.fill(w/2-126,top,w/2+126,top+30,((int)(165*f)<<24)|0x09070B);g.fill(w/2-108,top,w/2+108,top+1,((int)(230*f)<<24)|0xD8CDBA);g.fill(w/2-76,top+29,w/2+76,top+30,((int)(220*f)<<24)|0x7D3444);if(f>.15){g.drawCenteredString(mc.font,title,w/2,top+6,0xffe7dcc8);g.drawCenteredString(mc.font,sub,w/2,top+18,0xffb5a896);}}
}
