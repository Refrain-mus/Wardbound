package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;

import java.awt.Color;

/** Client-only ambient language for the hidden Master relics and the major progression items that follow them. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class MasterSummonRelicAmbientFx {
    private static final Color CURATOR_IVORY=new Color(236,221,185);
    private static final Color CURATOR_ASH=new Color(105,94,81);
    private static final Color CURATOR_EMBER=new Color(201,116,60);
    private static final Color NOTARY_GOLD=new Color(229,190,104);
    private static final Color NOTARY_RED=new Color(176,47,53);
    private static final Color NOTARY_INK=new Color(63,37,42);
    private static final Color MAESTRO_TEAL=new Color(78,214,205);
    private static final Color MAESTRO_VIOLET=new Color(126,88,201);
    private static final Color GAMBLER_GOLD=new Color(217,183,83);
    private static final Color GAMBLER_VIOLET=new Color(113,55,155);
    private static final Color ABYSS_CYAN=new Color(86,229,245);
    private static final Color ABYSS_DEEP=new Color(22,96,112);
    private static int ticks;
    private MasterSummonRelicAmbientFx(){}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        ticks++;
        int cadence=WardConfig.accessibilityReduceMotion?4:1;
        if(ticks%cadence!=0)return;
        for(ItemEntity item:mc.level.getEntitiesOfClass(ItemEntity.class,mc.player.getBoundingBox().inflate(28),e->
                e.getItem().is(WardItems.GAMBLER_INVITATION.get())||e.getItem().is(WardItems.ARCHIVE_WRIT.get())||e.getItem().is(WardItems.FINAL_WITNESS_BELL.get())||
                e.getItem().is(WardItems.SCORE_BEYOND_THE_MARGIN.get())||e.getItem().is(WardItems.CANTICLE_FOR_THE_SLEEPER.get())||
                e.getItem().is(WardItems.DEALERS_HAND.get())||e.getItem().is(WardItems.UNSIGNED_VERDICT.get())||e.getItem().is(WardItems.ABYSSAL_VESTIGE.get()))){
            if(item.getItem().is(WardItems.GAMBLER_INVITATION.get())) GamblerCinematicFx.ambientInvitation(item,ticks);
            else if(item.getItem().is(WardItems.ARCHIVE_WRIT.get())) archiveWrit(item,ticks);
            else if(item.getItem().is(WardItems.FINAL_WITNESS_BELL.get())) witnessBell(item,ticks);
            else if(item.getItem().is(WardItems.SCORE_BEYOND_THE_MARGIN.get())) scoreBeyondMargin(item,ticks);
            else if(item.getItem().is(WardItems.CANTICLE_FOR_THE_SLEEPER.get())) canticle(item,ticks);
            else if(item.getItem().is(WardItems.DEALERS_HAND.get())) dealersHand(item,ticks);
            else if(item.getItem().is(WardItems.UNSIGNED_VERDICT.get())) unsignedVerdict(item,ticks);
            else abyssalVestige(item,ticks);
        }
    }

    private static void archiveWrit(ItemEntity item,int tick){
        Vec3 p=item.position().add(0,.18,0);boolean reduced=WardConfig.accessibilityReduceMotion;
        double phase=tick*.095+item.getId()*.41;
        int orbit=reduced?2:6;
        for(int i=0;i<orbit;i++){
            double a=phase+i*Math.PI*2.0/orbit;
            double r=.44+.08*Math.sin(phase*.7+i);
            double y=.18+.16*Math.sin(a*2.0);
            wisp(p.add(Math.cos(a)*r,y,Math.sin(a)*r),CURATOR_IVORY,CURATOR_ASH,.16f,24,0,.010,0,.018f);
        }
        if(tick%(reduced?16:8)==0){
            ring(p.add(0,-.03,0),.58,CURATOR_ASH,CURATOR_IVORY,reduced?8:18,26);
            int n=reduced?3:8;
            for(int i=0;i<n;i++){
                double a=phase+i*Math.PI*2.0/n;
                Vec3 q=p.add(Math.cos(a)*.16,.04+i*.035,Math.sin(a)*.16);
                wisp(q,CURATOR_EMBER,CURATOR_IVORY,.12f,20,0,.016,0,.014f);
            }
        }
    }

    private static void witnessBell(ItemEntity item,int tick){
        Vec3 p=item.position().add(0,.16,0);boolean reduced=WardConfig.accessibilityReduceMotion;
        double phase=tick*.075+item.getId()*.29;
        int orbit=reduced?2:5;
        for(int i=0;i<orbit;i++){
            double a=phase+i*Math.PI*2.0/orbit;
            double r=.34+.05*Math.sin(phase*1.4+i);
            wisp(p.add(Math.cos(a)*r,.18+.10*Math.sin(a*3),Math.sin(a)*r),NOTARY_GOLD,NOTARY_INK,.14f,24,0,.008,0,.016f);
        }
        if(tick%(reduced?18:9)==0){
            ring(p.add(0,-.02,0),.42,NOTARY_RED,NOTARY_GOLD,reduced?8:18,24);
            ring(p.add(0,.02,0),.72,NOTARY_GOLD,NOTARY_INK,reduced?8:22,30);
            int rays=reduced?4:8;
            for(int i=0;i<rays;i++){
                double a=i*Math.PI*2.0/rays;
                Vec3 q=p.add(Math.cos(a)*.22,.12,Math.sin(a)*.22);
                wisp(q,NOTARY_RED,NOTARY_GOLD,.12f,18,Math.cos(a)*.014,.018,Math.sin(a)*.014,.018f);
            }
        }
    }

    private static void scoreBeyondMargin(ItemEntity item,int tick){
        Vec3 p=item.position().add(0,.2,0); boolean reduced=WardConfig.accessibilityReduceMotion;
        double phase=tick*.09+item.getId()*.33;
        int orbit=reduced?3:6;
        for(int i=0;i<orbit;i++){
            double a=phase+i*Math.PI*2.0/orbit;
            double r=.42+.06*Math.sin(phase+i*.7);
            double y=.16+.12*Math.sin(a*2.0+i*.4);
            wisp(p.add(Math.cos(a)*r,y,Math.sin(a)*r),MAESTRO_TEAL,MAESTRO_VIOLET,.15f,24,0,.010,0,.016f);
        }
        if(tick%(reduced?14:7)==0){
            ring(p.add(0,-.03,0),.60,MAESTRO_TEAL,MAESTRO_VIOLET,reduced?8:18,28);
            musicalTrace(p, MAESTRO_TEAL, MAESTRO_VIOLET, reduced?3:6, .34, .06);
        }
    }

    private static void canticle(ItemEntity item,int tick){
        Vec3 p=item.position().add(0,.2,0); boolean reduced=WardConfig.accessibilityReduceMotion;
        double phase=tick*.07+item.getId()*.51;
        int orbit=reduced?2:5;
        for(int i=0;i<orbit;i++){
            double a=phase+i*Math.PI*2.0/orbit;
            double r=.36+.07*Math.sin(phase*1.3+i);
            double y=.18+.10*Math.sin(a*1.7);
            wisp(p.add(Math.cos(a)*r,y,Math.sin(a)*r),ABYSS_CYAN,MAESTRO_TEAL,.14f,24,0,.010,0,.016f);
        }
        if(tick%(reduced?16:8)==0){
            ring(p.add(0,-.02,0),.56,ABYSS_DEEP,ABYSS_CYAN,reduced?8:18,26);
            ring(p.add(0,.08,0),.28,MAESTRO_TEAL,ABYSS_CYAN,reduced?6:14,20);
        }
    }

    private static void dealersHand(ItemEntity item,int tick){
        Vec3 p=item.position().add(0,.16,0); boolean reduced=WardConfig.accessibilityReduceMotion;
        double phase=tick*.11+item.getId()*.44;
        int orbit=reduced?2:4;
        for(int i=0;i<orbit;i++){
            double a=phase+i*Math.PI*2.0/orbit;
            wisp(p.add(Math.cos(a)*.30,.16+.10*Math.sin(a*2.0),Math.sin(a)*.30),GAMBLER_GOLD,GAMBLER_VIOLET,.13f,22,0,.012,0,.016f);
        }
        if(tick%(reduced?16:8)==0){
            cardFan(p.add(0,.12,0), GAMBLER_GOLD, GAMBLER_VIOLET, reduced?3:5, .32);
        }
    }

    private static void unsignedVerdict(ItemEntity item,int tick){
        Vec3 p=item.position().add(0,.18,0); boolean reduced=WardConfig.accessibilityReduceMotion;
        double phase=tick*.08+item.getId()*.39;
        int orbit=reduced?2:4;
        for(int i=0;i<orbit;i++){
            double a=phase+i*Math.PI*2.0/orbit;
            wisp(p.add(Math.cos(a)*.38,.18+.06*Math.sin(a*3.0),Math.sin(a)*.38),NOTARY_RED,NOTARY_GOLD,.13f,22,0,.008,0,.016f);
        }
        if(tick%(reduced?18:9)==0){
            ring(p.add(0,-.02,0),.46,NOTARY_GOLD,NOTARY_RED,reduced?8:18,24);
            for(int i=0;i<(reduced?3:6);i++){
                double a=i*Math.PI*2.0/(reduced?3:6);
                wisp(p.add(Math.cos(a)*.18,.08,Math.sin(a)*.18),NOTARY_INK,NOTARY_GOLD,.12f,20,Math.cos(a)*.01,.015,Math.sin(a)*.01,.014f);
            }
        }
    }

    private static void abyssalVestige(ItemEntity item,int tick){
        Vec3 p=item.position().add(0,.17,0); boolean reduced=WardConfig.accessibilityReduceMotion;
        double phase=tick*.065+item.getId()*.57;
        int orbit=reduced?2:5;
        for(int i=0;i<orbit;i++){
            double a=phase+i*Math.PI*2.0/orbit;
            double r=.28+.08*Math.sin(phase+i);
            double y=.12+.16*Math.sin(a*2.0);
            wisp(p.add(Math.cos(a)*r,y,Math.sin(a)*r),ABYSS_CYAN,ABYSS_DEEP,.14f,24,0,.012,0,.016f);
        }
        if(tick%(reduced?18:9)==0){
            ring(p.add(0,-.03,0),.52,ABYSS_CYAN,ABYSS_DEEP,reduced?8:18,28);
            pillar(p.add(0,.02,0),ABYSS_CYAN,MAESTRO_TEAL,reduced?4:8,.54);
        }
    }

    private static void cardFan(Vec3 center, Color a, Color b, int cards, double radius){
        for(int i=0;i<cards;i++){
            double f=cards<=1?.5:i/(double)(cards-1);
            double ang=-0.8+f*1.6;
            Vec3 q=center.add(Math.cos(ang)*radius,.02+f*.12,Math.sin(ang)*radius);
            wisp(q,a,b,.14f,20,0,.012,0,.014f);
        }
    }

    private static void musicalTrace(Vec3 center, Color a, Color b, int notes, double radius, double rise){
        for(int i=0;i<notes;i++){
            double f=i/(double)Math.max(1,notes-1);
            double ang=-1.2+f*2.4;
            Vec3 q=center.add(Math.cos(ang)*radius,.06+f*rise,Math.sin(ang)*radius*.7);
            wisp(q,a,b,.13f,22,0,.014,0,.012f);
            wisp(q.add(0,.06,0),b,a,.09f,16,0,.008,0,.010f);
        }
    }

    private static void pillar(Vec3 c, Color a, Color b, int count, double height){
        for(int i=0;i<count;i++){
            double y=height*i/Math.max(1,count-1);
            wisp(c.add(0,y,0),a,b,.12f,20,0,.006,0,.010f);
        }
    }

    private static void ring(Vec3 c,double radius,Color a,Color b,int points,int life){
        for(int i=0;i<points;i++){
            double ang=i*Math.PI*2.0/Math.max(1,points);
            Vec3 p=c.add(Math.cos(ang)*radius,0,Math.sin(ang)*radius);
            wisp(p,a,b,.12f,life,0,.005,0,.012f);
        }
    }

    private static void wisp(Vec3 p,Color a,Color b,float scale,int life,double vx,double vy,double vz,float gravity){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                .setColorData(ColorParticleData.create(a,b).build())
                .setTransparencyData(GenericParticleData.create(.62f,0f).build())
                .setScaleData(GenericParticleData.create(scale,0f).build())
                .setMotion(new Vec3(vx,vy,vz)).setGravityStrength(gravity).setLifetime(life)
                .spawn(mc.level,p.x,p.y,p.z);
    }
}
