package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.net.GamblerCinematicPacket;
import dev.marrowseal.wardbound.boss.GamblerPatterns;
import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import dev.marrowseal.wardbound.net.MasterCuesPacket;
import dev.marrowseal.wardbound.net.MasterAnimationBeatPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;

import java.awt.Color;
import java.util.Random;

/** Client-only Lodestone presentation for The Pale Gambler. */
@OnlyIn(Dist.CLIENT)
public final class GamblerCinematicFx {
    private static final Color GOLD = new Color(238, 193, 103);
    private static final Color PALE_GOLD = new Color(255, 235, 176);
    private static final Color VIOLET = new Color(157, 77, 218);
    private static final Color DEEP_VIOLET = new Color(57, 14, 84);
    private static final Color CRIMSON = new Color(224, 55, 91);
    private static final Color BLOOD = new Color(91, 7, 29);
    private static final Color TEAL = new Color(74, 230, 205);
    private static final Color VOID = new Color(20, 7, 27);

    private GamblerCinematicFx() {}

    public static void handle(GamblerCinematicPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MasterSpectacleFx.gamblerCue(msg);
        EncounterAftermathFx.recordGambler(msg);
        Level level = mc.level;
        Vec3 pos = new Vec3(msg.x(), msg.y(), msg.z());
        boolean reduced = WardConfig.accessibilityReduceMotion;

        switch (msg.effect()) {
            case GamblerCinematicPacket.INVITATION_REVEAL -> {
                sigil(level, pos, Math.max(2.3f, msg.size()), GOLD, VIOLET, reduced ? 22 : 54, msg.seed());
                pillar(level, pos.add(0, .15, 0), 3.4, PALE_GOLD, VIOLET, reduced ? 8 : 24);
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_GOLD,pos.add(0,.035,0),5.4f,5.4f,1650,msg.seed()%360,9f,.90f);
                GamblerTextureFx.billboard(GamblerTextureFx.HALO,pos.add(0,1.15,0),2.4f,2.4f,1200,0,30f,true,.78f);
            }
            case GamblerCinematicPacket.TABLE_OPEN -> {
                GamblerScreenFx.trigger("THE TABLE ACCEPTS THE INVITATION", reduced ? 420 : 760, 1);
                sigil(level, pos, Math.max(6f, msg.size()), GOLD, CRIMSON, reduced ? 34 : 100, msg.seed());
                for (int i = 0; i < (reduced ? 2 : 4); i++)
                    ring(level, pos.add(0, .1 + i * .42, 0), 3.0 + i * 2.2, GOLD, VIOLET, reduced ? 16 : 36, 34 + i * 5);
                burst(level, pos.add(0, 1.0, 0), VIOLET, PALE_GOLD, reduced ? 10 : 34, 1.35, 48, msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_GOLD,pos.add(0,.025,0),15f,15f,1900,msg.seed()%360,5f,.92f);
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_BLOOD,pos.add(0,.05,0),9f,9f,1600,-msg.seed()%360,-10f,.72f);
            }
            case GamblerCinematicPacket.PLAYER_STAGGER -> {
                burst(level, pos.add(0, 1.0, 0), CRIMSON, VIOLET, reduced ? 5 : 13, .55, 18, msg.seed());
            }
            case GamblerCinematicPacket.CHOICE_BIND -> {
                ring(level, pos.add(0, .08, 0), 1.25, GOLD, VIOLET, reduced ? 12 : 28, 36);
                pillar(level, pos.add(0, .2, 0), 2.6, VIOLET, GOLD, reduced ? 6 : 16);
                cardOutline(level,pos.add(-.72,1.45,0),.72,1.08,-14,GOLD,VIOLET,reduced?12:26,38);
                cardOutline(level,pos.add(.72,1.45,0),.72,1.08,14,VIOLET,PALE_GOLD,reduced?12:26,38);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.025,0),3.8f,3.8f,820,msg.seed()%360,34f,.88f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,1.6,0),1.45f,1.45f,620,0,72f,true,.84f);
            }
            case GamblerCinematicPacket.PHASE_BREAK -> {
                MasterCameraShake.pulse(msg.size()>=8f?.58f:.44f,msg.size()>=8f?28:22,2.7f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                GamblerTextureFx.clearTablePreviews();
                if(msg.entityId()>=0) GamblerTextureFx.triggerBoundaryImpulse(msg.entityId(), .55f, (msg.seed()%360), false, 900);
                GamblerScreenFx.trigger(msg.size()>=8f?"THE LAST HAND":"THE TABLE TURNS", reduced ? 360 : 680, msg.size()>=8f?3:2);
                sigil(level, pos, Math.max(4f, msg.size()), CRIMSON, VIOLET, reduced ? 30 : 90, msg.seed());
                burst(level, pos.add(0, 2.7, 0), CRIMSON, PALE_GOLD, reduced ? 14 : 46, 2.1, 52, msg.seed());
                pillar(level, pos, 5.8, CRIMSON, VIOLET, reduced ? 8 : 28);
                helix(level,pos.add(0,.2,0),Math.max(4.0,msg.size()*.72),4.8,CRIMSON,GOLD,reduced?14:44,msg.seed());
                radialSpokes(level,pos.add(0,.14,0),Math.max(4.5,msg.size()),CRIMSON,PALE_GOLD,reduced?4:10,34,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_BLOOD,pos.add(0,.04,0),Math.max(8f,msg.size()*1.6f),Math.max(8f,msg.size()*1.6f),1200,msg.seed()%360,24f,.96f);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.06,0),Math.max(10f,msg.size()*1.85f),Math.max(10f,msg.size()*1.85f),1050,-msg.seed()%360,-32f,.78f);
                GamblerTextureFx.billboard(GamblerTextureFx.RIFT,pos.add(0,3.0,0),4.0f,4.0f,850,0,90f,true,.9f);
            }
            case GamblerCinematicPacket.FAN_IMPACT -> {
                MasterCameraShake.pulse(.16f,8,3.8f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                burst(level, pos.add(0, .6, 0), VIOLET, CRIMSON, reduced ? 8 : 22, 1.1, 26, msg.seed());
                coneBurst(level,pos.add(0,1.15,0),(msg.seed()%6283)/1000.0,Math.max(4.2,msg.size()*.8),VIOLET,CRIMSON,reduced?12:34,28,msg.seed());
                tiltedTorus(level,pos.add(0,1.1,0),Math.max(2.4,msg.size()*.48),.26,70,18,VIOLET,GOLD,reduced?12:34,28,msg.seed()+5);
                sigil(level, pos, Math.max(2.8f, msg.size()), VIOLET, GOLD, reduced ? 18 : 44, msg.seed());
                cardCrown(level,pos.add(0,1.35,0),Math.max(2.8,msg.size()*.52),VIOLET,GOLD,reduced?4:9,msg.seed());
                radialSpokes(level,pos.add(0,.18,0),Math.max(3.2,msg.size()*.72),VIOLET,CRIMSON,reduced?4:8,24,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.025,0),Math.max(5.5f,msg.size()*1.15f),Math.max(5.5f,msg.size()*1.15f),560,msg.seed()%360,58f,.82f);
                GamblerTextureFx.billboard(GamblerTextureFx.IMPACT,pos.add(0,1.25,0),3.2f,3.2f,520,0,160f,true,.95f);
            }
            case GamblerCinematicPacket.RING_IMPACT -> {
                MasterCameraShake.pulse(.19f,9,3.4f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                ring(level, pos.add(0, .18, 0), Math.max(2f, msg.size()), CRIMSON, GOLD, reduced ? 22 : 58, 30);
                ring(level, pos.add(0, .42, 0), Math.max(1.5f, msg.size()*.78), GOLD, VIOLET, reduced ? 12 : 34, 26);
                tiltedTorus(level,pos.add(0,1.4,0),Math.max(2.0,msg.size()),.28,84,10,CRIMSON,GOLD,reduced?16:48,30,msg.seed());
                sphereShell(level,pos.add(0,1.4,0),Math.max(1.6,msg.size()*.72),GOLD,VIOLET,reduced?12:36,28,msg.seed()+31);
                radialSpokes(level,pos.add(0,.16,0),Math.max(2.2,msg.size()),CRIMSON,GOLD,reduced?4:10,24,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.HALO,pos.add(0,.03,0),Math.max(4f,msg.size()*2f),Math.max(4f,msg.size()*2f),620,msg.seed()%360,45f,.86f);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.055,0),Math.max(3.8f,msg.size()*1.65f),Math.max(3.8f,msg.size()*1.65f),570,-msg.seed()%360,-62f,.72f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,1.0,0),1.35f,1.35f,420,0,110f,true,.82f);
            }
            case GamblerCinematicPacket.FLOOR_CARD_IMPACT -> {
                sigil(level, pos, Math.max(5f, msg.size()), TEAL, GOLD, reduced ? 28 : 84, msg.seed());
                burst(level, pos.add(0, .35, 0), TEAL, CRIMSON, reduced ? 8 : 28, 1.8, 36, msg.seed());
                radialSpokes(level,pos.add(0,.14,0),Math.max(5.0,msg.size()),TEAL,CRIMSON,reduced?5:12,30,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_TEAL,pos.add(0,.03,0),Math.max(9f,msg.size()*1.2f),Math.max(9f,msg.size()*1.2f),900,msg.seed()%360,18f,.92f);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.055,0),Math.max(10f,msg.size()*1.45f),Math.max(10f,msg.size()*1.45f),780,-msg.seed()%360,-38f,.82f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,1.4,0),2.3f,2.3f,610,0,95f,true,.9f);
            }
            case GamblerCinematicPacket.HOUSE_TEETH -> {
                MasterCameraShake.pulse(.24f,11,3.0f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                ring(level, pos.add(0, .12, 0), Math.max(3f, msg.size()), BLOOD, CRIMSON, reduced ? 18 : 50, 26);
                sphereShell(level,pos.add(0,1.8,0),Math.max(2.8,msg.size()*.75),BLOOD,CRIMSON,reduced?14:42,30,msg.seed());
                tiltedTorus(level,pos.add(0,1.6,0),Math.max(2.4,msg.size()*.64),.32,68,12,CRIMSON,PALE_GOLD,reduced?14:40,28,msg.seed()+13);
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_BLOOD,pos.add(0,.03,0),Math.max(6f,msg.size()*1.4f),Math.max(6f,msg.size()*1.4f),720,msg.seed()%360,28f,.78f);
                for (int i = 0; i < (reduced ? 4 : 10); i++) {
                    double a = i * Math.PI * 2.0 / (reduced ? 4 : 10);
                    pillar(level, pos.add(Math.cos(a) * msg.size(), 0, Math.sin(a) * msg.size()), 2.7, BLOOD, PALE_GOLD, reduced ? 3 : 7);
                }
            }
            case GamblerCinematicPacket.STARFALL -> {
                pillar(level, pos, 6.5, PALE_GOLD, VIOLET, reduced ? 10 : 30);
                helix(level,pos.add(0,.1,0),1.15,6.2,PALE_GOLD,VIOLET,reduced?10:30,msg.seed());
                sphereShell(level,pos.add(0,4.5,0),2.6,PALE_GOLD,VIOLET,reduced?16:48,36,msg.seed()+21);
                coneBurst(level,pos.add(0,5.8,0),-Math.PI/2,5.8,PALE_GOLD,CRIMSON,reduced?14:40,34,msg.seed()+27);
                burst(level, pos.add(0, .5, 0), PALE_GOLD, CRIMSON, reduced ? 10 : 34, 1.0, 40, msg.seed());
                GamblerTextureFx.billboard(GamblerTextureFx.RUNE_PILLAR,pos.add(0,3.0,0),1.8f,6.2f,920,0,0f,false,.80f);
                GamblerTextureFx.billboard(GamblerTextureFx.IMPACT,pos.add(0,2.0,0),3.8f,3.8f,760,msg.seed()%360,115f,true,.96f);
                GamblerTextureFx.ground(GamblerTextureFx.RIFT,pos.add(0,.03,0),5.0f,5.0f,820,0,55f,.82f);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.055,0),6.2f,6.2f,660,msg.seed()%360,70f,.76f);
            }
            case GamblerCinematicPacket.CARD_WALL -> {
                wall(level, pos, Math.max(4f, msg.size()), (msg.seed() & 1) == 0, CRIMSON, VIOLET, reduced);
                tiltedTorus(level,pos.add(0,2.1,0),Math.max(3.2,msg.size()*.55),.38,82,(msg.seed()&1)==0?0:90,CRIMSON,VIOLET,reduced?16:48,34,msg.seed());
                casinoVortex(level,pos.add(0,.35,0),Math.max(3.6,msg.size()*.62),3.6,CRIMSON,GOLD,reduced?14:42,34,msg.seed()+11);
                for(int i=-2;i<=2;i++) GamblerTextureFx.billboard((i&1)==0?GamblerTextureFx.CARD_BLOOD:GamblerTextureFx.CARD_GOLD,
                        pos.add(((msg.seed()&1)==0?0:i*2.1),1.8,((msg.seed()&1)==0?i*2.1:0)),2.2f,3.1f,880,i*13,0,false,.84f);
            }
            case GamblerCinematicPacket.SPIRAL -> {
                spiral(level, pos, Math.max(3f, msg.size()), CRIMSON, GOLD, reduced ? 18 : 60, msg.seed());
                casinoVortex(level,pos.add(0,.2,0),Math.max(3.6,msg.size()*.72),4.8,CRIMSON,GOLD,reduced?18:58,40,msg.seed()+17);
                tiltedTorus(level,pos.add(0,2.7,0),Math.max(2.8,msg.size()*.5),.35,34,66,VIOLET,GOLD,reduced?16:48,38,msg.seed()+23);
                helix(level,pos.add(0,.1,0),Math.max(2.4,msg.size()*.52),2.8,CRIMSON,VIOLET,reduced?10:28,msg.seed());
                cardCrown(level,pos.add(0,1.1,0),Math.max(2.0,msg.size()*.42),CRIMSON,GOLD,reduced?3:7,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.HALO,pos.add(0,.03,0),Math.max(8f,msg.size()*1.8f),Math.max(8f,msg.size()*1.8f),900,msg.seed()%360,80f,.9f);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.055,0),Math.max(6f,msg.size()*1.32f),Math.max(6f,msg.size()*1.32f),760,-msg.seed()%360,-92f,.74f);
                GamblerTextureFx.billboard(GamblerTextureFx.VOID_EYE,pos.add(0,2.1,0),1.5f,1.5f,610,0,-72f,true,.68f);
            }
            case GamblerCinematicPacket.ROYAL -> {
                MasterCameraShake.pulse(.32f,14,2.8f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                GamblerScreenFx.trigger("ROYAL HOUSE // NO MERCY", reduced ? 280 : 520, 3);
                sigil(level, pos, Math.max(7f, msg.size()), CRIMSON, GOLD, reduced ? 38 : 120, msg.seed());
                sphereShell(level,pos.add(0,3.0,0),Math.max(4.0,msg.size()*.6),CRIMSON,GOLD,reduced?22:76,48,msg.seed()+33);
                tiltedTorus(level,pos.add(0,3.2,0),Math.max(4.8,msg.size()*.72),.48,44,28,GOLD,VIOLET,reduced?24:82,48,msg.seed()+39);
                ring(level, pos.add(0, .35, 0), Math.max(8f, msg.size()), CRIMSON, VIOLET, reduced ? 30 : 90, 44);
                burst(level, pos.add(0, 2.4, 0), GOLD, CRIMSON, reduced ? 15 : 54, 2.3, 56, msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_BLOOD,pos.add(0,.03,0),Math.max(16f,msg.size()*1.5f),Math.max(16f,msg.size()*1.5f),1400,msg.seed()%360,36f,.96f);
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_GOLD,pos.add(0,.055,0),Math.max(10f,msg.size()),Math.max(10f,msg.size()),1300,-msg.seed()%360,-54f,.88f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,3.0,0),2.0f,2.0f,780,0,84f,true,.90f);
                GamblerTextureFx.billboard(GamblerTextureFx.RUNE_PILLAR,pos.add(0,1.7,0),1.6f,4.8f,1100,0,0f,false,.82f);
            }
            case GamblerCinematicPacket.ACE_OATH -> {
                sigil(level, pos, Math.max(2.4f, msg.size()), GOLD, TEAL, reduced ? 24 : 64, msg.seed());
                pillar(level, pos, 3.0, GOLD, PALE_GOLD, reduced ? 7 : 18);
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_TEAL,pos.add(0,.025,0),5.0f,5.0f,1300,msg.seed()%360,12f,.9f);
            }
            case GamblerCinematicPacket.BOSS_INTRO -> {
                MasterCameraShake.pulse(.34f,20,2.2f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                sigil(level, pos, Math.max(8f, msg.size()), GOLD, VIOLET, reduced ? 42 : 138, msg.seed());
                ring(level, pos.add(0,.32,0), Math.max(6f,msg.size()*.72), VIOLET, PALE_GOLD, reduced ? 26 : 84, 54);
                helix(level,pos.add(0,.2,0),6.8,2.1,GOLD,VIOLET,reduced?18:64,msg.seed());
                tiltedTorus(level,pos.add(0,3.0,0),4.8,.55,28,18,GOLD,VIOLET,reduced?28:92,52,msg.seed());
                sphereShell(level,pos.add(0,3.0,0),4.1,GOLD,DEEP_VIOLET,reduced?24:86,58,msg.seed()+91);
                casinoVortex(level,pos.add(0,.4,0),6.4,5.8,GOLD,VIOLET,reduced?22:70,58,msg.seed());
                worldCardOrbit(pos.add(0,3.0,0),4.6,reduced?5:12,msg.seed(),false,1100);
                burst(level,pos.add(0,3.1,0),VOID,PALE_GOLD,reduced?16:58,2.6,64,msg.seed());
                GamblerScreenFx.trigger("THE LAST TABLE OPENS", reduced ? 450 : 900, 1);
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_GOLD,pos.add(0,.02,0),Math.max(17f,msg.size()*1.5f),Math.max(17f,msg.size()*1.5f),2200,msg.seed()%360,7f,.95f);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.03,0),Math.max(13f,msg.size()*1.2f),Math.max(13f,msg.size()*1.2f),1700,-msg.seed()%360,-18f,.82f);
                GamblerTextureFx.billboard(GamblerTextureFx.HALO,pos.add(0,4.8,0),5.2f,5.2f,1650,0,42f,true,.8f);
                GamblerTextureFx.billboard(GamblerTextureFx.VOID_EYE,pos.add(0,3.4,0),2.4f,2.4f,1550,0,36f,true,.78f);
                GamblerTextureFx.billboard(GamblerTextureFx.RUNE_PILLAR,pos.add(0,2.4,0),2.0f,5.8f,1450,0,0f,false,.76f);
            }
            case GamblerCinematicPacket.HAND_CHARGE -> {
                // Let the previous hand's cards remain on the felt through recovery; clear them only
                // when a new hand is actually dealt so the arena does not look like it erases itself.
                GamblerTextureFx.clearTablePreviews();
                int hand=msg.seed() & 0xFF;
                int phase=(msg.seed()>>>8)&0xF;
                if(msg.entityId()>=0) GamblerTextureFx.triggerBoundaryImpulse(msg.entityId(), phase>=3?.52f:.34f, ((msg.seed()>>>12)%360), false, phase>=3?760:520);
                Color a=handPrimary(hand,phase), b=handSecondary(hand,phase);
                cardFan(level,pos.add(0,2.95,0),Math.max(2.8,msg.size()*.68),a,b,reduced?12:34,msg.seed());
                halo(level,pos.add(0,3.35,0),Math.max(2.2,msg.size()*.5),a,b,reduced?14:36,34,msg.seed());
                tiltedTorus(level,pos.add(0,3.1,0),Math.max(2.5,msg.size()*.58),.35,22,-18,a,b,reduced?18:54,34,msg.seed());
                tiltedTorus(level,pos.add(0,3.1,0),Math.max(2.0,msg.size()*.46),.28,-24,32,b,a,reduced?14:42,30,msg.seed()+17);
                casinoVortex(level,pos.add(0,.45,0),Math.max(3.6,msg.size()*.72),4.6,a,b,reduced?18:48,38,msg.seed()+41);
                if(phase>=3)sphereShell(level,pos.add(0,3.05,0),Math.max(2.7,msg.size()*.52),a,VOID,reduced?18:52,34,msg.seed()+101);
                worldCardOrbit(pos.add(0,3.0,0),Math.max(2.8,msg.size()*.58),reduced?4:9,msg.seed()+121,phase>=3||hand==GamblerPatterns.DEAD_MANS_DRAW,780);
                sigil(level,pos,Math.max(3.6,msg.size()*.88),a,b,reduced?22:62,msg.seed());
                GamblerTextureFx.ground(hand==GamblerPatterns.ROYAL?GamblerTextureFx.SIGIL_BLOOD:hand==GamblerPatterns.FLOOR_CARDS?GamblerTextureFx.SIGIL_TEAL:GamblerTextureFx.SIGIL_GOLD,
                        pos.add(0,.03,0),Math.max(5.6f,msg.size()*1.2f),Math.max(5.6f,msg.size()*1.2f),950,msg.seed()%360,18f,.90f);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.04,0),Math.max(4.8f,msg.size()),Math.max(4.8f,msg.size()),880,-msg.seed()%360,-26f,.78f);
                GamblerTextureFx.billboard(hand==GamblerPatterns.ROYAL?GamblerTextureFx.CARD_BLOOD:hand==GamblerPatterns.FLOOR_CARDS?GamblerTextureFx.CARD_TEAL:GamblerTextureFx.CARD_GOLD,
                        pos.add(0,3.2,0),2.4f,3.45f,980,0,26f,true,.86f);
                // Forward-pointing cast crest: intentionally asymmetric in height/rise so the
                // attack reads as a thrust from the dealer rather than an aura behind him.
                GamblerTextureFx.billboard(GamblerTextureFx.FORWARD_CAST,pos.add(0,3.05,0),1.05f,3.0f,720,0,0f,true,.76f);
                if(hand==GamblerPatterns.STACKED_DECK){
                    GamblerTextureFx.ground(GamblerTextureFx.STACKED_SEAL,pos.add(0,.052,0),7.4f,7.4f,980,msg.seed()%360,22f,.82f);
                    GamblerScreenFx.trigger("STACKED DECK // RED CARDS ARE LIVE",reduced?240:420,2);
                }else if(hand==GamblerPatterns.MIRROR_DEBT){
                    GamblerTextureFx.ground(GamblerTextureFx.MIRROR_SPLINTER,pos.add(0,.052,0),7.2f,7.2f,980,msg.seed()%360,-18f,.78f);
                    GamblerScreenFx.trigger("MIRROR DEBT // THEN COMES YOUR REFLECTION",reduced?260:450,2);
                }else if(hand==GamblerPatterns.BURIED_ACE)GamblerScreenFx.trigger("BURIED ACE // ONE SEAL IS TRUE",reduced?260:450,1);
                else if(hand==GamblerPatterns.HOUSE_EDGE){
                    GamblerTextureFx.ground(GamblerTextureFx.HOUSE_EDGE,pos.add(0,.052,0),8.4f,8.4f,1050,msg.seed()%360,48f,.88f);
                    GamblerScreenFx.trigger("HOUSE EDGE // RING AND BLADES",reduced?260:460,2);
                }else if(hand==GamblerPatterns.DOUBLE_DOWN){
                    GamblerTextureFx.ground(GamblerTextureFx.DOUBLE_DOWN,pos.add(0,.052,0),8.0f,8.0f,1050,msg.seed()%360,-34f,.88f);
                    GamblerScreenFx.trigger("DOUBLE DOWN // TWO SAFE BETS",reduced?260:460,1);
                }else if(hand==GamblerPatterns.DEAD_MANS_DRAW){
                    GamblerTextureFx.ground(GamblerTextureFx.DEAD_MANS_DRAW,pos.add(0,.052,0),8.4f,8.4f,1100,msg.seed()%360,28f,.90f);
                    GamblerScreenFx.trigger("DEAD MAN'S DRAW // DO NOT STAND ON THE CALLED CARD",reduced?270:480,2);
                }else if(hand==GamblerPatterns.FALSE_TELL){
                    GamblerTextureFx.ground(GamblerTextureFx.FALSE_TELL,pos.add(0,.052,0),8.6f,8.6f,1150,msg.seed()%360,-42f,.90f);
                    GamblerScreenFx.trigger("FALSE TELL // THE WARNING WILL LIE",reduced?280:500,2);
                }else if(hand==GamblerPatterns.SUIT_CALL){
                    GamblerScreenFx.trigger("SUIT CALL // FIND YOUR OWN SEAL",reduced?280:500,1);
                }else if(hand==GamblerPatterns.ROYAL_FLUSH){
                    GamblerTextureFx.ground(GamblerTextureFx.ROYAL_FLUSH,pos.add(0,.052,0),9.0f,9.0f,1180,msg.seed()%360,34f,.94f);
                    GamblerScreenFx.trigger("ROYAL FLUSH // TWO CARDS WILL CLOSE",reduced?280:500,3);
                }else if(hand==GamblerPatterns.LAST_DEAL){
                    GamblerTextureFx.ground(GamblerTextureFx.LAST_DEAL,pos.add(0,.052,0),11.0f,11.0f,1450,msg.seed()%360,-18f,.96f);
                    GamblerScreenFx.trigger("THE LAST DEAL // NO PAUSE BETWEEN LESSONS",reduced?420:760,3);
                }
            }
            case GamblerCinematicPacket.PLAYER_MARKED -> {
                ring(level,pos.add(0,.08,0),1.45,CRIMSON,VIOLET,reduced?10:26,30);
                pillar(level,pos.add(0,.15,0),2.2,CRIMSON,VIOLET,reduced?5:13);
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_BLOOD,pos.add(0,.025,0),3.1f,3.1f,720,msg.seed()%360,48f,.76f);
                GamblerTextureFx.billboard(GamblerTextureFx.VOID_EYE,pos.add(0,1.55,0),1.1f,1.1f,620,0,-55f,true,.70f);
            }
            case GamblerCinematicPacket.PLAYER_BRACE -> {
                ring(level,pos.add(0,.08,0),1.25,GOLD,CRIMSON,reduced?10:22,28);
                burst(level,pos.add(0,1.1,0),GOLD,CRIMSON,reduced?5:12,.5,22,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.025,0),2.8f,2.8f,520,msg.seed()%360,52f,.72f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,1.15,0),1.0f,1.0f,420,0,86f,true,.76f);
            }
            case GamblerCinematicPacket.DEATH_FINAL_CARD -> {
                // The table's last physical statement. It is intentionally quieter than a danger tell.
                MasterCameraShake.pulse(.18f,12,2.7f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                cardOutline(level,pos.add(0,1.15,0),.72,1.05,-7,GOLD,CRIMSON,reduced?18:46,70);
                GamblerTextureFx.card3D(GamblerTextureFx.CARD_BLOOD,pos.add(0,1.05,0),1.25f,1.85f,1500,0,-12,4,0,0,34,.98f);
                GamblerTextureFx.ground(GamblerTextureFx.LAST_DEAL,pos.add(0,.018,0),4.4f,4.4f,2300,msg.seed()%360,0f,.54f);
                burst(level,pos.add(0,1.0,0),PALE_GOLD,CRIMSON,reduced?8:24,.75,32,msg.seed());
            }
            case GamblerCinematicPacket.VICTORY_SHATTER -> {
                GamblerTextureFx.clearTablePreviews();
                if(msg.entityId()>=0) GamblerTextureFx.triggerBoundaryImpulse(msg.entityId(), .90f, (msg.seed()%360), true, 1600);
                collapse(level,pos.add(0,2.7,0),Math.max(7f,msg.size()),CRIMSON,GOLD,reduced?34:120,msg.seed());
                ring(level,pos.add(0,.15,0),Math.max(8f,msg.size()),TEAL,PALE_GOLD,reduced?30:96,62);
                burst(level,pos.add(0,3.0,0),PALE_GOLD,VIOLET,reduced?18:68,3.0,72,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_TEAL,pos.add(0,.03,0),Math.max(18f,msg.size()*1.5f),Math.max(18f,msg.size()*1.5f),1800,msg.seed()%360,-38f,.92f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,3.3,0),2.8f,2.8f,980,0,64f,true,.90f);
                GamblerTextureFx.billboard(GamblerTextureFx.VOID_EYE,pos.add(0,4.15,0),2.2f,2.2f,920,0,-54f,true,.72f);
                for(int i=0;i<(reduced?4:12);i++){double a=i*Math.PI*2.0/(reduced?4:12);GamblerTextureFx.billboard(GamblerTextureFx.SHARD,pos.add(Math.cos(a)*2.3,2.0+(i%3)*.45,Math.sin(a)*2.3),1.0f,1.7f,1200,i*29,70f,true,.9f);}
                boundaryCardCollapse(pos.add(0,3.1,0),Math.max(8.0,msg.size()),reduced?18:64,msg.seed());
                GamblerScreenFx.trigger("THE HOUSE HAS LOST", reduced ? 550 : 1200, 3);
            }
            case GamblerCinematicPacket.PLAYER_AWE -> {
            }
            case GamblerCinematicPacket.PLAYER_RELEASE -> {
                ring(level,pos.add(0,.06,0),1.15,TEAL,PALE_GOLD,reduced?8:18,24);
            }
            case GamblerCinematicPacket.FLOOR_CARD_PREVIEW -> {
                cardSeal(level,pos,msg.size(),msg.seed(),false,reduced);
                GamblerTextureFx.card(pos,msg.size(),(msg.seed()>>4)/1000.0,msg.seed()&15,false);
            }
            case GamblerCinematicPacket.FLOOR_CARD_STRIKE -> {
                cardSeal(level,pos,msg.size(),msg.seed(),true,reduced);
                GamblerTextureFx.card(pos,msg.size(),(msg.seed()>>4)/1000.0,msg.seed()&15,true);
            }
            case GamblerCinematicPacket.SWEEP_STRIKE -> {
                double angle=Float.intBitsToFloat(msg.seed());
                slashLine(level,pos,angle,Math.max(8.0,msg.size()),CRIMSON,GOLD,reduced?18:42);
                slashLine(level,pos,angle+Math.PI,Math.max(8.0,msg.size()),CRIMSON,VIOLET,reduced?18:42);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.035,0),8.5f,8.5f,420,(float)Math.toDegrees(angle),105f,.58f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,1.15,0),1.7f,1.7f,360,0,150f,true,.88f);
            }
            case GamblerCinematicPacket.CAGE_STRIKE -> {
                gridBurst(level,pos,Math.max(8.0,msg.size()),CRIMSON,VIOLET,reduced);
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_BLOOD,pos.add(0,.035,0),11f,11f,520,msg.seed()%360,42f,.66f);
                GamblerTextureFx.billboard(GamblerTextureFx.RUNE_PILLAR,pos.add(0,2.2,0),1.5f,4.8f,520,0,0,false,.72f);
            }
            case GamblerCinematicPacket.MARK_RIFT -> {
                pillar(level,pos.add(0,.05,0),4.7,CRIMSON,VIOLET,reduced?7:20);
                ring(level,pos.add(0,.08,0),Math.max(1.8,msg.size()),CRIMSON,GOLD,reduced?12:30,30);
                burst(level,pos.add(0,.65,0),CRIMSON,VIOLET,reduced?7:20,.75,28,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.SIGIL_BLOOD,pos.add(0,.025,0),5.2f,5.2f,650,msg.seed()%360,55f,.80f);
                GamblerTextureFx.billboard(GamblerTextureFx.VOID_EYE,pos.add(0,1.7,0),1.4f,1.4f,560,0,-80f,true,.78f);
            }
            case GamblerCinematicPacket.DEALER_CROSS -> {
                radialSpokes(level,pos.add(0,.12,0),Math.max(8.0,msg.size()),CRIMSON,GOLD,reduced?4:8,30,msg.seed());
                burst(level,pos.add(0,.8,0),GOLD,CRIMSON,reduced?8:24,1.2,28,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.DEALER_CROSS,pos.add(0,.035,0),12.5f,12.5f,760,msg.seed()%360,78f,.92f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,1.45,0),2.4f,2.4f,520,0,130f,true,.92f);
            }
            case GamblerCinematicPacket.CHIP_STORM -> {
                ring(level,pos.add(0,.09,0),Math.max(1.8,msg.size()*.55),GOLD,TEAL,reduced?14:34,32);
                burst(level,pos.add(0,.35,0),GOLD,CRIMSON,reduced?8:24,.9,30,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.CHIP_RUNE,pos.add(0,.025,0),Math.max(4.2f,msg.size()),Math.max(4.2f,msg.size()),720,msg.seed()%360,95f,.96f);
                GamblerTextureFx.billboard(GamblerTextureFx.IMPACT,pos.add(0,1.0,0),1.6f,1.6f,430,0,160f,true,.88f);
            }
            case GamblerCinematicPacket.SPLIT_POT -> {
                ring(level,pos.add(0,.08,0),5.6,CRIMSON,GOLD,reduced?14:34,30);
                ring(level,pos.add(0,.11,0),10.2,GOLD,VIOLET,reduced?18:48,34);
                ring(level,pos.add(0,.14,0),14.5,TEAL,CRIMSON,reduced?20:56,38);
                GamblerTextureFx.ground(GamblerTextureFx.SPLIT_POT,pos.add(0,.025,0),15.5f,15.5f,820,msg.seed()%360,-60f,.90f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,1.25,0),1.8f,1.8f,450,0,90f,true,.82f);
            }
            case GamblerCinematicPacket.CHOICE_REVEAL -> {
                int left=msg.seed()&0xFF,right=(msg.seed()>>>8)&0xFF;
                ResourceLocation leftTex=left==0?GamblerTextureFx.CARD_BLOOD:left==1?GamblerTextureFx.CARD_GOLD:GamblerTextureFx.CARD_TEAL;
                ResourceLocation rightTex=right==0?GamblerTextureFx.CARD_BLOOD:right==1?GamblerTextureFx.CARD_GOLD:GamblerTextureFx.CARD_TEAL;
                cardOutline(level,pos.add(-.85,1.55,0),.78,1.18,-12,GOLD,VIOLET,reduced?10:24,42);
                cardOutline(level,pos.add(.85,1.55,0),.78,1.18,12,VIOLET,GOLD,reduced?10:24,42);
                GamblerTextureFx.billboard(leftTex,pos.add(-1.05,1.72,0),1.45f,2.18f,1450,-9,10f,false,.92f);
                GamblerTextureFx.billboard(rightTex,pos.add(1.05,1.72,0),1.45f,2.18f,1450,9,-10f,false,.92f);
                // The third card remains face-down: the pool is three cards, but the House only exposes two.
                GamblerTextureFx.billboard(GamblerTextureFx.CARD_BACK,pos.add(0,2.42,.08),1.05f,1.58f,1450,0,18f,true,.72f);
                GamblerTextureFx.billboard(GamblerTextureFx.CHOICE_CHAIN,pos.add(0,1.62,.03),4.35f,3.65f,1500,0,20f,false,.80f);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.025,0),5.2f,5.2f,1380,msg.seed()%360,34f,.90f);
                ring(level,pos.add(-1.05,.08,0),1.15,GOLD,VIOLET,reduced?8:18,30);
                ring(level,pos.add(1.05,.08,0),1.15,VIOLET,GOLD,reduced?8:18,30);
                burst(level,pos.add(0,1.4,0),GOLD,VIOLET,reduced?10:28,.9,42,msg.seed());
                GamblerScreenFx.trigger("TWO SHOWN // ONE KEPT IN THE DECK", reduced ? 260 : 430, 2);
            }
            case GamblerCinematicPacket.SUMMON_PULSE -> {
                ring(level,pos.add(0,.1,0),Math.max(2.2,msg.size()),VIOLET,GOLD,reduced?16:42,44);
                pillar(level,pos.add(0,.05,0),3.6+msg.size()*.18,GOLD,VIOLET,reduced?6:18);
                GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,pos.add(0,.025,0),Math.max(5f,msg.size()*1.65f),Math.max(5f,msg.size()*1.65f),700,msg.seed()%360,28f,.68f);
                GamblerTextureFx.billboard(GamblerTextureFx.VOID_EYE,pos.add(0,2.6,0),1.4f,1.4f,620,0,-36f,true,.56f);
            }
            case GamblerCinematicPacket.AMBIENT_DETONATION -> {
                burst(level,pos.add(0,.35,0),VIOLET,CRIMSON,reduced?7:19,Math.max(.7,msg.size()*.35),28,msg.seed());
                ring(level,pos.add(0,.05,0),Math.max(1.2,msg.size()*.5),CRIMSON,GOLD,reduced?8:20,24);
                GamblerTextureFx.ground(GamblerTextureFx.RIFT,pos.add(0,.025,0),Math.max(2.8f,msg.size()),Math.max(2.8f,msg.size()),520,msg.seed()%360,80f,.64f);
            }
            case GamblerCinematicPacket.FLOOR_FIELD_PREVIEW -> {
                // The whole felt is hostile; authored card decals laid on top are the safe islands.
                GamblerTextureFx.ground(GamblerTextureFx.FLOOR_DANGER,pos.add(0,.012,0),31.2f,31.2f,6000,msg.seed()%360,4f,.78f);
                ring(level,pos.add(0,.06,0),15.5,CRIMSON,VIOLET,reduced?24:64,42);
                ring(level,pos.add(0,.09,0),12.0,BLOOD,CRIMSON,reduced?16:42,34);
                radialSpokes(level,pos.add(0,.08,0),14.8,CRIMSON,VIOLET,reduced?4:10,30,msg.seed());
            }
            case GamblerCinematicPacket.HOUSE_EXPOSED -> {
                GamblerScreenFx.trigger("THE TELL IS OPEN // STRIKE NOW", reduced ? 320 : 620, 2);
                sigil(level,pos,5.4,TEAL,PALE_GOLD,reduced?26:78,msg.seed());
                ring(level,pos.add(0,.12,0),3.3,PALE_GOLD,TEAL,reduced?16:48,42);
                burst(level,pos.add(0,2.5,0),TEAL,GOLD,reduced?12:38,1.5,42,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.HOUSE_TELL,pos.add(0,.025,0),8.8f,8.8f,1250,msg.seed()%360,52f,.96f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,3.2,0),2.4f,2.4f,850,0,120f,true,.92f);
                GamblerTextureFx.billboard(GamblerTextureFx.CARD_GOLD,pos.add(0,4.0,0),1.35f,2.0f,980,0,-18f,true,.84f);
            }
            case GamblerCinematicPacket.DANGER_CARD_PREVIEW -> {
                double angle=(msg.seed()>>4)/1000.0;
                GamblerTextureFx.dangerCard(pos,msg.size(),angle,false);
                cardOutline(level,pos.add(0,.09,0),Math.max(1.25,msg.size()*.62),Math.max(2.0,msg.size()),Math.toDegrees(angle),CRIMSON,VIOLET,reduced?10:26,34);
            }
            case GamblerCinematicPacket.DANGER_CARD_STRIKE -> {
                double angle=(msg.seed()>>4)/1000.0;
                GamblerTextureFx.dangerCard(pos,msg.size(),angle,true);
                burst(level,pos.add(0,.35,0),CRIMSON,BLOOD,reduced?10:32,1.0,34,msg.seed());
                pillar(level,pos.add(0,.05,0),2.8,CRIMSON,VIOLET,reduced?5:15);
            }
            case GamblerCinematicPacket.BURIED_ACE -> {
                boolean safe=(msg.seed()&1)!=0;
                Color a=safe?TEAL:CRIMSON,b=safe?PALE_GOLD:BLOOD;
                ring(level,pos.add(0,.08,0),Math.max(1.3,msg.size()*.5),a,b,reduced?10:30,34);
                burst(level,pos.add(0,.25,0),a,b,reduced?7:20,.7,28,msg.seed());
                GamblerTextureFx.ground(safe?GamblerTextureFx.HOUSE_TELL:GamblerTextureFx.CHIP_RUNE,pos.add(0,.025,0),Math.max(3.6f,msg.size()),Math.max(3.6f,msg.size()),safe?1200:700,msg.seed()%360,safe?18f:72f,safe?.94f:.82f);
                if(safe){
                    GamblerTextureFx.billboard(GamblerTextureFx.CARD_GOLD,pos.add(0,1.35,0),1.1f,1.65f,900,0,14f,true,.82f);
                    GamblerScreenFx.trigger("BURIED ACE // REACH THE TRUE SEAL", reduced ? 260 : 480, 1);
                }
            }
            case GamblerCinematicPacket.MIRROR_DEBT -> {
                boolean mirror=(msg.seed()&1)!=0;
                Color a=mirror?VIOLET:CRIMSON,b=mirror?PALE_GOLD:BLOOD;
                ring(level,pos.add(0,.06,0),Math.max(1.2,msg.size()*.48),a,b,reduced?10:28,30);
                burst(level,pos.add(0,.3,0),a,b,reduced?6:18,.65,28,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.MIRROR_SPLINTER,pos.add(0,.025,0),Math.max(3.4f,msg.size()),Math.max(3.4f,msg.size()),720,msg.seed()%360,mirror?-74f:74f,.82f);
                GamblerTextureFx.billboard(GamblerTextureFx.VOID_EYE,pos.add(0,1.1,0),.9f,.9f,520,0,mirror?-80f:80f,true,.62f);
            }
            case GamblerCinematicPacket.HOUSE_EDGE -> {
                int phase=(msg.seed()>>>24)&0xF;
                double radius=Math.max(3.0,msg.size());
                ring(level,pos.add(0,.08,0),radius,CRIMSON,GOLD,reduced?18:52,36);
                tiltedTorus(level,pos.add(0,1.6,0),radius*.72,.28,78,24,CRIMSON,GOLD,reduced?14:44,34,msg.seed()+45);
                sphereShell(level,pos.add(0,1.8,0),Math.max(2.8,radius*.55),CRIMSON,VIOLET,reduced?14:42,32,msg.seed()+51);
                radialSpokes(level,pos.add(0,.09,0),15.2,CRIMSON,VIOLET,reduced?4:Math.max(8,phase*4),32,msg.seed());
                burst(level,pos.add(0,.35,0),CRIMSON,PALE_GOLD,reduced?10:34,1.3,32,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.HOUSE_EDGE,pos.add(0,.028,0),(float)(radius*2.1),(float)(radius*2.1),720,msg.seed()%360,120f,.92f);
                GamblerTextureFx.billboard(GamblerTextureFx.FORWARD_CAST,pos.add(0,1.8,0),1.0f,2.6f,560,0,0f,true,.72f);
            }
            case GamblerCinematicPacket.DOUBLE_DOWN -> {
                int phase=(msg.seed()>>>24)&0xF;
                ring(level,pos.add(0,.08,0),14.8,CRIMSON,VIOLET,reduced?20:58,38);
                tiltedTorus(level,pos.add(0,2.1,0),5.6,.34,28,-38,GOLD,TEAL,reduced?18:52,36,msg.seed()+57);
                sphereShell(level,pos.add(0,2.2,0),4.2,GOLD,TEAL,reduced?16:46,34,msg.seed()+63);
                burst(level,pos.add(0,.45,0),GOLD,TEAL,reduced?10:30,1.2,34,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.DOUBLE_DOWN,pos.add(0,.028,0),10.8f,10.8f,850,msg.seed()%360,-72f,.94f);
                for(int i=0;i<(reduced?2:4);i++){double a=i*Math.PI*2/(reduced?2:4);GamblerTextureFx.billboard(GamblerTextureFx.CARD_GOLD,pos.add(Math.cos(a)*2.2,1.4+i*.18,Math.sin(a)*2.2),.8f,1.25f,620,i*35,18f,true,.66f);}
                if(phase>=3)GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,2.0,0),1.8f,1.8f,520,0,130f,true,.84f);
            }
            case GamblerCinematicPacket.HAND_RELEASE -> {
                MasterCameraShake.pulse(.12f,7,4.2f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                if(msg.entityId()>=0) GamblerTextureFx.triggerBoundaryImpulse(msg.entityId(), .42f, ((msg.seed()>>>5)%360), false, 420);
                ring(level,pos.add(0,.08,0),2.6,PALE_GOLD,VIOLET,reduced?8:22,24);
                burst(level,pos.add(0,1.6,0),PALE_GOLD,VIOLET,reduced?6:18,.7,24,msg.seed());
                GamblerTextureFx.billboard(GamblerTextureFx.FORWARD_CAST,pos.add(0,2.6,0),.72f,2.1f,380,180,0f,true,.52f);
            }
            case GamblerCinematicPacket.DEAD_MANS_DRAW -> {
                int wave=msg.seed()&0xF;
                ring(level,pos.add(0,.08,0),8.8,CRIMSON,BLOOD,reduced?18:52,34);
                casinoVortex(level,pos.add(0,.25,0),5.0,4.2,BLOOD,CRIMSON,reduced?16:48,36,msg.seed()+71);
                tiltedTorus(level,pos.add(0,2.4,0),4.4,.30,38,52,CRIMSON,PALE_GOLD,reduced?14:42,34,msg.seed()+77);
                burst(level,pos.add(0,.45,0),CRIMSON,PALE_GOLD,reduced?8:26,1.2,32,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.DEAD_MANS_DRAW,pos.add(0,.028,0),10.2f,10.2f,760,msg.seed()%360,54f,.90f);
                if(wave>0)GamblerTextureFx.billboard(GamblerTextureFx.CARD_BLOOD,pos.add(0,2.2,0),1.25f,1.9f,560,0,18f,true,.80f);
            }
            case GamblerCinematicPacket.FALSE_TELL -> {
                boolean strike=(msg.seed()&1)!=0;
                radialSpokes(level,pos.add(0,.08,0),14.8,strike?CRIMSON:VIOLET,strike?BLOOD:GOLD,reduced?6:18,30,msg.seed());
                sphereShell(level,pos.add(0,2.5,0),4.8,strike?CRIMSON:VIOLET,strike?BLOOD:TEAL,reduced?18:54,34,msg.seed()+83);
                tiltedTorus(level,pos.add(0,2.4,0),5.2,.32,strike?70:22,strike?24:-48,strike?CRIMSON:VIOLET,strike?PALE_GOLD:GOLD,reduced?16:48,34,msg.seed()+89);
                GamblerTextureFx.ground(GamblerTextureFx.FALSE_TELL,pos.add(0,.026,0),10.8f,10.8f,strike?720:1250,msg.seed()%360,strike?82f:-24f,strike?.94f:.82f);
                if(strike)burst(level,pos.add(0,.5,0),CRIMSON,BLOOD,reduced?10:34,1.5,30,msg.seed());
            }
            case GamblerCinematicPacket.FALSE_TELL_REVEAL -> {
                GamblerScreenFx.trigger("FALSE TELL BREAKS // RED IS REAL",reduced?300:520,3);
                ring(level,pos.add(0,.1,0),14.9,TEAL,CRIMSON,reduced?24:72,38);
                burst(level,pos.add(0,2.0,0),TEAL,CRIMSON,reduced?12:36,1.25,36,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.FALSE_TELL,pos.add(0,.03,0),12.0f,12.0f,780,msg.seed()%360,120f,.98f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,2.8,0),2.0f,2.0f,520,0,140f,true,.90f);
            }
            case GamblerCinematicPacket.SUIT_CALL -> {
                int suit=msg.seed()&0xF;
                int turn=msg.seed()>>>4;
                String[] names={"SPADE","HEART","CLUB","DIAMOND"};
                ResourceLocation[] tex={GamblerTextureFx.SUIT_SPADE,GamblerTextureFx.SUIT_HEART,GamblerTextureFx.SUIT_CLUB,GamblerTextureFx.SUIT_DIAMOND};
                var marks=GamblerPatterns.suitMarks(turn);
                for(int i=0;i<4;i++){
                    double[] m=marks.get(i);Vec3 q=pos.add(m[0],.02,m[1]);
                    GamblerTextureFx.ground(tex[i],q,4.3f,4.3f,1750,(float)(i*90+turn*11),i==suit?18f:5f,i==suit?.98f:.70f);
                    GamblerTextureFx.ground(i==suit?GamblerTextureFx.TELEGRAPH_RING_SAFE:GamblerTextureFx.TELEGRAPH_RING_GOLD,q.add(0,.01,0),6.0f,6.0f,1750,0,0f,i==suit?.95f:.52f);
                    ring(level,q.add(0,.06,0),3.0,i==suit?TEAL:GOLD,i==suit?PALE_GOLD:VIOLET,reduced?8:22,34);
                    sphereShell(level,q.add(0,1.2,0),1.65,i==suit?TEAL:GOLD,i==suit?PALE_GOLD:VIOLET,reduced?6:18,28,msg.seed()+i*19);
                }
                GamblerScreenFx.trigger("SUIT CALL // "+names[Math.floorMod(suit,4)]+" // YOUR SEAL ONLY",reduced?340:620,2);
            }
            case GamblerCinematicPacket.SUIT_CALL_STRIKE -> {
                int turn=msg.seed()>>>4;
                var marks=GamblerPatterns.suitMarks(turn);
                for(int i=0;i<4;i++){double[] m=marks.get(i);Vec3 q=pos.add(m[0],.05,m[1]);burst(level,q.add(0,.3,0),CRIMSON,GOLD,reduced?6:18,.55,28,msg.seed()+i*17);GamblerTextureFx.billboard(GamblerTextureFx.IMPACT,q.add(0,1.2,0),1.45f,1.45f,420,i*30,120f,true,.82f);}
                ring(level,pos.add(0,.08,0),14.8,CRIMSON,BLOOD,reduced?20:58,34);
            }
            case GamblerCinematicPacket.ROYAL_FLUSH -> {
                MasterCameraShake.pulse(.22f,11,3.2f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                int wave=msg.seed()&0xF;
                cardCrown(level,pos.add(0,1.5,0),5.2,GOLD,CRIMSON,reduced?5:11,msg.seed());
                casinoVortex(level,pos.add(0,.25,0),6.4,5.0,GOLD,CRIMSON,reduced?18:58,42,msg.seed()+97);
                tiltedTorus(level,pos.add(0,3.0,0),5.6,.38,32,54,GOLD,CRIMSON,reduced?18:56,40,msg.seed()+103);
                worldCardOrbit(pos.add(0,2.8,0),5.2,reduced?5:11,msg.seed()+113,true,850);
                sigil(level,pos,7.5,GOLD,CRIMSON,reduced?26:76,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.ROYAL_FLUSH,pos.add(0,.03,0),11.5f,11.5f,800,msg.seed()%360,52f,.96f);
                if(wave>0)burst(level,pos.add(0,1.0,0),CRIMSON,PALE_GOLD,reduced?10:30,1.4,34,msg.seed());
            }
            case GamblerCinematicPacket.LAST_DEAL -> {
                MasterCameraShake.pulse(.28f,14,2.9f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                int stage=msg.seed()&0xFF;
                String[] stages={"CUT","POT","DOUBLE DOWN","MIRROR","ROYAL","FALSE TELL"};
                int idx=Math.floorMod(stage,stages.length);
                GamblerScreenFx.trigger("LAST DEAL // "+(idx+1)+"/6 // "+stages[idx],reduced?300:520,3);
                ring(level,pos.add(0,.08,0),14.9,CRIMSON,GOLD,reduced?24:70,38);
                helix(level,pos.add(0,.2,0),5.8,3.8,CRIMSON,VIOLET,reduced?14:42,msg.seed());
                tiltedTorus(level,pos.add(0,3.0,0),6.1,.48,36,22,CRIMSON,GOLD,reduced?24:78,46,msg.seed());
                casinoVortex(level,pos.add(0,.3,0),7.2,5.2,CRIMSON,VIOLET,reduced?22:72,46,msg.seed()+29);
                worldCardOrbit(pos.add(0,3.0,0),5.8,reduced?5:13,msg.seed()+47,true,900);
                coneBurst(level,pos.add(0,2.8,0),(msg.seed()%6283)/1000.0,7.0,CRIMSON,PALE_GOLD,reduced?16:46,40,msg.seed()+61);
                GamblerTextureFx.ground(GamblerTextureFx.LAST_DEAL,pos.add(0,.025,0),14.0f,14.0f,900,msg.seed()%360,70f,.96f);
                GamblerTextureFx.billboard(GamblerTextureFx.FORWARD_CAST,pos.add(0,2.8,0),1.4f,3.6f,640,0,0f,true,.84f);
            }
            case GamblerCinematicPacket.ALL_IN -> {
                MasterCameraShake.pulse(.70f,34,2.35f,msg.seed(),MasterAnimationBeatPacket.GAMBLER);
                if(msg.entityId()>=0) GamblerTextureFx.triggerBoundaryImpulse(msg.entityId(), .82f, (msg.seed()%360), true, 1800);
                GamblerScreenFx.trigger("ALL IN // THE HOUSE HAS NO RESERVE",reduced?520:980,3);
                sigil(level,pos,15.0,CRIMSON,BLOOD,reduced?42:140,msg.seed());
                ring(level,pos.add(0,.12,0),15.3,CRIMSON,GOLD,reduced?30:92,52);
                helix(level,pos.add(0,.2,0),7.5,5.5,CRIMSON,PALE_GOLD,reduced?20:70,msg.seed());
                tiltedTorus(level,pos.add(0,3.2,0),7.3,.65,52,28,CRIMSON,GOLD,reduced?30:110,58,msg.seed());
                tiltedTorus(level,pos.add(0,3.2,0),5.4,.52,-42,55,PALE_GOLD,VIOLET,reduced?24:88,52,msg.seed()+73);
                sphereShell(level,pos.add(0,3.1,0),6.5,CRIMSON,VOID,reduced?30:112,62,msg.seed()+137);
                casinoVortex(level,pos.add(0,.25,0),8.0,6.6,CRIMSON,PALE_GOLD,reduced?28:96,60,msg.seed()+181);
                worldCardOrbit(pos.add(0,3.2,0),6.9,reduced?6:16,msg.seed()+197,true,1400);
                boundaryCardFreezeBurst(pos.add(0,3.2,0),15.2,reduced?24:88,msg.seed());
                burst(level,pos.add(0,3.1,0),CRIMSON,GOLD,reduced?20:70,2.8,58,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.ALL_IN,pos.add(0,.03,0),18f,18f,1800,msg.seed()%360,34f,.98f);
                GamblerTextureFx.billboard(GamblerTextureFx.VOID_EYE,pos.add(0,4.3,0),3.2f,3.2f,1400,0,70f,true,.86f);
            }
            case GamblerCinematicPacket.HOUSE_READ -> {
                int read=(msg.seed()>>>24)&0xF;
                GamblerScreenFx.trigger("HOUSE READ "+Math.max(1,read)+"/3 // PUNISH WINDOW",reduced?280:500,2);
                sigil(level,pos,4.8+read*.8,TEAL,PALE_GOLD,reduced?22:66,msg.seed());
                ring(level,pos.add(0,.1,0),3.0+read*.45,TEAL,GOLD,reduced?14:42,38);
                GamblerTextureFx.ground(GamblerTextureFx.HOUSE_READ,pos.add(0,.025,0),7.5f+read,7.5f+read,1100,msg.seed()%360,38f,.95f);
                for(int i=0;i<Math.max(1,read);i++)GamblerTextureFx.billboard(GamblerTextureFx.CARD_TEAL,pos.add((i-(read-1)/2.0)*1.05,2.8+i*.12,0),.72f,1.08f,780,i*17,12f,true,.76f);
            }
            case GamblerCinematicPacket.FINAL_TELL -> {
                GamblerScreenFx.trigger("FINAL TELL // THE HOUSE IS OPEN",reduced?520:1000,3);
                sigil(level,pos,8.5,TEAL,PALE_GOLD,reduced?38:120,msg.seed());
                ring(level,pos.add(0,.1,0),5.5,PALE_GOLD,TEAL,reduced?28:82,52);
                tiltedTorus(level,pos.add(0,3.0,0),5.8,.5,28,48,TEAL,PALE_GOLD,reduced?24:84,52,msg.seed());
                sphereShell(level,pos.add(0,3.0,0),4.6,TEAL,PALE_GOLD,reduced?22:78,50,msg.seed()+211);
                casinoVortex(level,pos.add(0,.4,0),6.2,4.8,TEAL,GOLD,reduced?20:68,48,msg.seed()+263);
                burst(level,pos.add(0,3.0,0),TEAL,PALE_GOLD,reduced?18:58,2.0,52,msg.seed());
                GamblerTextureFx.ground(GamblerTextureFx.FINAL_TELL,pos.add(0,.025,0),12.5f,12.5f,1800,msg.seed()%360,54f,.99f);
                GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,pos.add(0,3.4,0),3.0f,3.0f,1100,0,140f,true,.96f);
            }

            default -> {}
        }
    }

    /** Persistent fight aura. Kept intentionally sparse; it makes the boss feel alive between attacks. */
    public static void ambientBoss(PaleGamblerEntity boss, int tick) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || boss==null || !boss.isAlive())return;
        Level level=mc.level;
        boolean reduced=WardConfig.accessibilityReduceMotion;
        int phase=Math.max(1,Math.min(3,boss.currentPhase()));
        int hand=boss.currentHand();
        Color a=handPrimary(hand,phase),b=handSecondary(hand,phase);
        if(boss.openTell()){a=TEAL;b=PALE_GOLD;}
        double t=tick*.11 + boss.getId()*.37;
        int motes=reduced?2:(phase==3?6:4);
        for(int i=0;i<motes;i++){
            double ang=t+i*Math.PI*2.0/motes;
            double r=1.55+.22*Math.sin(t*1.7+i);
            double y=boss.getY()+2.55+i*.34+.20*Math.sin(t*2+i);
            wisp(level,boss.getX()+Math.cos(ang)*r,y,boss.getZ()+Math.sin(ang)*r,a,b,.23f+(i%2)*.06f,24,0,.010,0,.03);
        }
        if(tick%(reduced?6:3)==0) halo(level,new Vec3(boss.getX(),boss.getY()+5.1,boss.getZ()),1.2+phase*.15,a,b,reduced?7:15,26,boss.getId()+tick);
        if(tick%(reduced?8:4)==0) cardFan(level,new Vec3(boss.getX(),boss.getY()+3.72,boss.getZ()),1.95+phase*.16,a,b,reduced?4:7,boss.getId()*17+tick);
        if(tick%(reduced?12:6)==0){
            Vec3 core=new Vec3(boss.getX(),boss.getY()+3.05,boss.getZ());
            tiltedTorus(level,core,2.1+phase*.25,.22,28+phase*9,-22+phase*7,a,b,reduced?10:28,24,boss.getId()*31+tick);
            casinoVortex(level,new Vec3(boss.getX(),boss.getY()+.35,boss.getZ()),2.8+phase*.55,3.2+phase*.35,a,b,reduced?8:22,24,boss.getId()*47+tick);
        }
        if(tick%(reduced?20:10)==0){
            double ca=t*.58;
            cardOutline(level,new Vec3(boss.getX()+Math.cos(ca)*1.72,boss.getY()+2.55,boss.getZ()+Math.sin(ca)*1.72),.52,.78,(float)Math.toDegrees(-ca),a,b,reduced?8:18,28);
        }
        if(tick%(reduced?18:10)==0){
            GamblerTextureFx.ground(phase==3?GamblerTextureFx.SIGIL_BLOOD:phase==2?GamblerTextureFx.SIGIL_GOLD:GamblerTextureFx.SIGIL_TEAL,
                    new Vec3(boss.getX(),boss.getY()+.03,boss.getZ()),5.8f+phase*1.15f,5.8f+phase*1.15f,720,tick*3f,14f,.62f);
        }
        if(!reduced && tick%20==0){
            worldCardOrbit(new Vec3(boss.getX(),boss.getY()+3.0,boss.getZ()),2.5+phase*.32,4+phase,boss.getId()*59+tick,phase==3,720);
        }
        if(tick%(reduced?16:8)==0){
            double ang=t*.72;
            GamblerTextureFx.billboard(GamblerTextureFx.WISP,new Vec3(boss.getX()+Math.cos(ang)*1.8,boss.getY()+3.1+Math.sin(t)*.45,boss.getZ()+Math.sin(ang)*1.8),1.6f,.7f,700,(float)(tick*7),22f,true,.66f);
            GamblerTextureFx.billboard((phase&1)==0?GamblerTextureFx.CARD_GOLD:GamblerTextureFx.CARD_BLOOD,new Vec3(boss.getX()-Math.cos(ang)*1.45,boss.getY()+3.45,boss.getZ()-Math.sin(ang)*1.45),1.1f,1.65f,660,(float)(tick*11),0f,false,.72f);
        }
        if(!reduced && mc.player!=null && (hand==GamblerPatterns.MARKS || hand==GamblerPatterns.FORCED_CHOICE || hand==GamblerPatterns.ROYAL) && tick%4==0){
            Vec3 from=new Vec3(boss.getX(),boss.getY()+5.35,boss.getZ());
            Vec3 to=mc.player.getEyePosition();
            if(from.distanceToSqr(to)<1600) gaze(level,from,to,phase==3?CRIMSON:VIOLET,PALE_GOLD,10);
        }
        if(boss.openTell() && tick%(reduced?8:4)==0){
            double oa=t*1.15;
            wisp(level,boss.getX()+Math.cos(oa)*1.2,boss.getY()+3.0,boss.getZ()+Math.sin(oa)*1.2,TEAL,PALE_GOLD,.30f,24,0,.02,0,.035);
            if(!reduced)GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,new Vec3(boss.getX(),boss.getY()+4.5,boss.getZ()),1.6f,1.6f,360,tick*9f,120f,true,.58f);
        }
        if(phase==3 && tick%(reduced?6:3)==0){
            if(tick%(reduced?18:9)==0)sphereShell(level,new Vec3(boss.getX(),boss.getY()+3.0,boss.getZ()),2.8,CRIMSON,VOID,reduced?12:34,24,boss.getId()+tick*13);
            double ang=t*1.6;
            wisp(level,boss.getX()+Math.cos(ang)*.85,boss.getY()+1.3+Math.sin(t)*.4,boss.getZ()+Math.sin(ang)*.85,BLOOD,CRIMSON,.28f,22,0,.02,0,.04);
            if(!reduced)GamblerTextureFx.billboard(GamblerTextureFx.RIFT,new Vec3(boss.getX(),boss.getY()+4.35,boss.getZ()),1.8f,1.8f,380,(float)(tick*12),95f,true,.55f);
        }
        if(boss.houseReadLevel()>0 && !boss.openTell() && tick%(reduced?22:12)==0){
            int read=boss.houseReadLevel();
            GamblerTextureFx.ground(GamblerTextureFx.HOUSE_READ,new Vec3(boss.getX(),boss.getY()+.04,boss.getZ()),4.6f+read*.6f,4.6f+read*.6f,520,tick*4f,18f,.48f+.08f*read);
        }
        if(boss.allIn() && tick%(reduced?10:5)==0){
            double aa=t*2.05;
            wisp(level,boss.getX()+Math.cos(aa)*1.15,boss.getY()+1.2+Math.sin(aa*.7)*.5,boss.getZ()+Math.sin(aa)*1.15,CRIMSON,PALE_GOLD,.34f,24,0,.025,0,.055);
            if(!reduced)GamblerTextureFx.ground(GamblerTextureFx.ALL_IN,new Vec3(boss.getX(),boss.getY()+.035,boss.getZ()),7.8f,7.8f,520,tick*6f,44f,.62f);
        }
        if(boss.finalTell() && tick%(reduced?8:4)==0){
            GamblerTextureFx.ground(GamblerTextureFx.FINAL_TELL,new Vec3(boss.getX(),boss.getY()+.04,boss.getZ()),8.6f,8.6f,580,tick*5f,30f,.74f);
        }
    }

    private static Color handPrimary(int hand,int phase){
        if(phase==3)return hand==GamblerPatterns.FLOOR_CARDS?TEAL:CRIMSON;
        return switch(hand){
            case GamblerPatterns.STARFALL -> PALE_GOLD;
            case GamblerPatterns.FLOOR_CARDS -> TEAL;
            case GamblerPatterns.HOUSE_TEETH -> BLOOD;
            case GamblerPatterns.ROYAL -> GOLD;
            case GamblerPatterns.CARD_WALL, GamblerPatterns.SPIRAL, GamblerPatterns.MIRROR_DEBT -> VIOLET;
            case GamblerPatterns.BURIED_ACE -> TEAL;
            case GamblerPatterns.HOUSE_EDGE -> CRIMSON;
            case GamblerPatterns.DOUBLE_DOWN -> GOLD;
            case GamblerPatterns.STACKED_DECK -> CRIMSON;
            case GamblerPatterns.DEAD_MANS_DRAW -> BLOOD;
            case GamblerPatterns.FALSE_TELL -> VIOLET;
            case GamblerPatterns.SUIT_CALL -> TEAL;
            case GamblerPatterns.ROYAL_FLUSH -> GOLD;
            case GamblerPatterns.LAST_DEAL -> CRIMSON;
            default -> phase==2?VIOLET:GOLD;
        };
    }

    private static Color handSecondary(int hand,int phase){
        return switch(hand){
            case GamblerPatterns.FLOOR_CARDS -> GOLD;
            case GamblerPatterns.STARFALL -> VIOLET;
            case GamblerPatterns.HOUSE_TEETH -> CRIMSON;
            case GamblerPatterns.ROYAL -> phase==3?CRIMSON:VIOLET;
            case GamblerPatterns.BURIED_ACE -> PALE_GOLD;
            case GamblerPatterns.HOUSE_EDGE -> GOLD;
            case GamblerPatterns.DOUBLE_DOWN -> TEAL;
            case GamblerPatterns.STACKED_DECK -> BLOOD;
            case GamblerPatterns.MIRROR_DEBT -> CRIMSON;
            case GamblerPatterns.DEAD_MANS_DRAW -> CRIMSON;
            case GamblerPatterns.FALSE_TELL -> TEAL;
            case GamblerPatterns.SUIT_CALL -> GOLD;
            case GamblerPatterns.ROYAL_FLUSH -> CRIMSON;
            case GamblerPatterns.LAST_DEAL -> PALE_GOLD;
            default -> phase==3?BLOOD:DEEP_VIOLET;
        };
    }

    private static void halo(Level level,Vec3 pos,double radius,Color a,Color b,int count,int lifetime,int seed){
        for(int i=0;i<count;i++){
            double ang=seed*.017+i*Math.PI*2.0/count;
            double y=pos.y+Math.sin(ang*2.0)*.14;
            wisp(level,pos.x+Math.cos(ang)*radius,y,pos.z+Math.sin(ang)*radius,a,b,.22f,lifetime,0,.006,0,.025);
        }
    }

    private static void cardFan(Level level,Vec3 pos,double radius,Color a,Color b,int count,int seed){
        for(int i=0;i<count;i++){
            double f=count<=1?0:i/(double)(count-1);
            double ang=-1.1+f*2.2+seed*.003;
            double x=pos.x+Math.cos(ang)*radius,z=pos.z+Math.sin(ang)*radius;
            double y=pos.y+(f-.5)*.85;
            wisp(level,x,y,z,a,b,.30f,30,Math.cos(ang)*.006,.012,Math.sin(ang)*.006,.028);
            if(i%2==0)wisp(level,x,y+.16,z,b,a,.17f,24,0,.018,0,.02);
        }
    }

    private static void helix(Level level,Vec3 pos,double height,double radius,Color a,Color b,int count,int seed){
        for(int i=0;i<count;i++){
            double f=i/(double)Math.max(1,count-1),ang=seed*.013+f*Math.PI*8.0;
            double r=radius*(.75+.25*Math.sin(f*Math.PI));
            wisp(level,pos.x+Math.cos(ang)*r,pos.y+height*f,pos.z+Math.sin(ang)*r,a,b,.27f,42,0,.015,0,.035);
        }
    }

    private static void cardOutline(Level level,Vec3 pos,double halfW,double halfH,double angleDeg,Color a,Color b,int count,int lifetime){
        double ang=Math.toRadians(angleDeg),cos=Math.cos(ang),sin=Math.sin(ang);
        int per=Math.max(2,count/4);
        for(int side=0;side<4;side++){
            double x1,z1,x2,z2;
            if(side==0){x1=-halfW;z1=-halfH;x2=halfW;z2=-halfH;}
            else if(side==1){x1=halfW;z1=-halfH;x2=halfW;z2=halfH;}
            else if(side==2){x1=halfW;z1=halfH;x2=-halfW;z2=halfH;}
            else{x1=-halfW;z1=halfH;x2=-halfW;z2=-halfH;}
            for(int i=0;i<per;i++){
                double f=(i+.5)/per,lx=x1+(x2-x1)*f,lz=z1+(z2-z1)*f;
                double wx=pos.x+lx*cos-lz*sin,wz=pos.z+lx*sin+lz*cos;
                wisp(level,wx,pos.y,wz,a,b,.19f,lifetime,0,.006,0,.018);
            }
        }
    }

    private static void cardCrown(Level level,Vec3 pos,double radius,Color a,Color b,int cards,int seed){
        for(int i=0;i<cards;i++){
            double f=cards<=1?.5:i/(double)(cards-1);
            double ang=-1.22+f*2.44+seed*.009;
            Vec3 p=pos.add(Math.cos(ang)*radius,(f-.5)*.75,Math.sin(ang)*radius);
            cardOutline(level,p,.38,.58,Math.toDegrees(-ang)+90,a,b,12,30);
        }
    }

    private static void radialSpokes(Level level,Vec3 pos,double radius,Color a,Color b,int spokes,int lifetime,int seed){
        for(int i=0;i<spokes;i++){
            double ang=seed*.017+i*Math.PI*2.0/spokes;
            for(int q=2;q<=7;q++){
                double r=radius*q/7.0;
                wisp(level,pos.x+Math.cos(ang)*r,pos.y+.035*q,pos.z+Math.sin(ang)*r,a,b,.18f+(q%2)*.03f,lifetime,0,.004,0,.018);
            }
        }
    }

    private static void gaze(Level level,Vec3 from,Vec3 to,Color a,Color b,int count){
        for(int i=1;i<=count;i++){
            double f=i/(double)(count+1);
            Vec3 p=from.lerp(to,f);
            wisp(level,p.x,p.y,p.z,a,b,.12f+(float)(f*.06),14,0,0,0,.012);
        }
    }

    private static void collapse(Level level,Vec3 pos,double radius,Color a,Color b,int count,int seed){
        Random r=new Random(seed*0x632BE59BD9B4E019L);
        for(int i=0;i<count;i++){
            double ang=r.nextDouble()*Math.PI*2.0;
            double rr=radius*(.35+r.nextDouble()*.65);
            double y=(r.nextDouble()-.35)*radius*.45;
            double x=pos.x+Math.cos(ang)*rr,z=pos.z+Math.sin(ang)*rr;
            double dx=(pos.x-x)*.035,dy=(pos.y-y-pos.y)*.01,dz=(pos.z-z)*.035;
            wisp(level,x,pos.y+y,z,a,b,.22f+r.nextFloat()*.25f,34,dx,.01,dz,.02);
        }
    }

    public static void ambientInvitation(net.minecraft.world.entity.item.ItemEntity item,int tick){
        if(item==null || item.level()==null)return;
        Level level=item.level();
        boolean reduced=WardConfig.accessibilityReduceMotion;
        Vec3 pos=item.position().add(0,.2,0);
        double t=tick*.12+item.getId();
        int count=reduced?2:5;
        for(int i=0;i<count;i++){
            double a=t+i*Math.PI*2.0/count;
            wisp(level,pos.x+Math.cos(a)*.58,pos.y+.25+Math.sin(a*2)*.18,pos.z+Math.sin(a)*.58,GOLD,VIOLET,.18f,24,0,.012,0,.022);
        }
        if(tick%(reduced?12:6)==0)ring(level,pos.add(0,-.05,0),.72,GOLD,TEAL,reduced?8:16,28);
    }

    public static void ambientAce(net.minecraft.client.player.AbstractClientPlayer player,int tick){
        if(player==null || player.level()==null)return;
        Vec3 look=player.getLookAngle();
        Vec3 right=look.cross(new Vec3(0,1,0));
        if(right.lengthSqr()<1.0E-4)right=new Vec3(1,0,0); else right=right.normalize();
        Vec3 pos=player.getEyePosition().add(0,-.48,0).add(right.scale(.42)).add(look.scale(.28));
        Color a=(tick/8)%2==0?GOLD:TEAL;
        wisp(player.level(),pos.x,pos.y,pos.z,a,PALE_GOLD,.13f,16,0,.006,0,.012);
    }

    private static void cardSeal(Level level,Vec3 pos,float halfHeight,int encoded,boolean strike,boolean reduced){
        int suit=encoded&15;
        int angleBits=encoded>>4;
        double angle=angleBits/1000.0;
        double hh=Math.max(1.4,halfHeight), hw=hh*.62;
        Color a=strike?TEAL:GOLD,b=strike?CRIMSON:VIOLET;
        int life=strike?34:96;
        int edge=reduced?5:10;
        for(int side=0;side<4;side++){
            double x1,z1,x2,z2;
            if(side==0){x1=-hw;z1=-hh;x2=hw;z2=-hh;}
            else if(side==1){x1=hw;z1=-hh;x2=hw;z2=hh;}
            else if(side==2){x1=hw;z1=hh;x2=-hw;z2=hh;}
            else{x1=-hw;z1=hh;x2=-hw;z2=-hh;}
            for(int i=0;i<=edge;i++){
                double f=i/(double)edge,lx=x1+(x2-x1)*f,lz=z1+(z2-z1)*f;
                double wx=pos.x+lx*Math.cos(angle)-lz*Math.sin(angle),wz=pos.z+lx*Math.sin(angle)+lz*Math.cos(angle);
                wisp(level,wx,pos.y,wz,a,b,strike?.28f:.19f,life,0,strike?.025:.004,0,strike?.035:.014);
            }
        }
        // Inner rail + occult central suit.
        for(int i=0;i<(reduced?8:18);i++){
            double f=i/(double)Math.max(1,(reduced?7:17)),lx=-hw*.68+hw*1.36*f;
            for(double lz:new double[]{-hh*.68,hh*.68}){
                double wx=pos.x+lx*Math.cos(angle)-lz*Math.sin(angle),wz=pos.z+lx*Math.sin(angle)+lz*Math.cos(angle);
                wisp(level,wx,pos.y+.035,wz,b,a,.14f,life,0,.003,0,.01);
            }
        }
        int rays=(suit&1)==0?4:8;
        for(int i=0;i<rays;i++){
            double a0=i*Math.PI*2.0/rays+angle;
            double r=(suit&1)==0?.95:1.15;
            for(int q=1;q<=3;q++){
                double rr=r*q/3.0;
                wisp(level,pos.x+Math.cos(a0)*rr,pos.y+.06,pos.z+Math.sin(a0)*rr,a,b,strike?.25f:.16f,life,0,.006,0,.012);
            }
        }
        if(strike){
            pillar(level,pos.add(0,.05,0),2.6,TEAL,CRIMSON,reduced?6:16);
            burst(level,pos.add(0,.35,0),TEAL,PALE_GOLD,reduced?7:20,.8,30,encoded);
        }
    }

    /**
     * Lodestone is the authoritative visual layer for server-owned danger geometry.
     * Cues can be very dense (floor-card faces in particular), so every pulse samples
     * across the entire list instead of spending the whole budget on the first cues.
     */
    public static void telegraphPulse(MasterCuesPacket packet) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || packet.cues().isEmpty())return;
        Level level=mc.level;
        boolean reduced=WardConfig.accessibilityReduceMotion;
        int budget=reduced?40:156;
        int targetCues=reduced?14:52;
        int size=packet.cues().size();
        int stride=Math.max(1,(int)Math.ceil(size/(double)targetCues));
        int phase=Math.floorMod((int)(level.getGameTime()+packet.entityId()*7L),stride);
        int emitted=0, textured=0;
        double spin=(level.getGameTime()*.16+packet.entityId()*.37);
        for(int index=phase;index<size && emitted<budget;index+=stride){
            MasterCuesPacket.Cue c=packet.cues().get(index);
            Color start=color(c.color());
            Color end=start.getRed()>220 && start.getGreen()<110?BLOOD:DEEP_VIOLET;
            if(c.circle()){
                int count=reduced?5:10;
                for(int i=0;i<count && emitted<budget;i++){
                    double a=spin+i*Math.PI*2.0/count;
                    double r=c.a()+Math.sin(spin+i)*Math.min(.16,c.width()*.18);
                    wisp(level,c.x()+Math.cos(a)*r,packet.y()+.12+((i&1)*.10),c.z()+Math.sin(a)*r,start,end,.24f+(float)Math.min(.16,c.width()*.035),24,0,.007,0,.022);
                    emitted++;
                }
                if(!reduced && textured<2 && c.a()>2.4){
                    GamblerTextureFx.ground(GamblerTextureFx.RING_ARC,new Vec3(c.x(),packet.y()+.012,c.z()),(float)(c.a()*2.05),(float)(c.a()*2.05),420,(float)Math.toDegrees(spin),52f,.42f);
                    textured++;
                }
            }else{
                int count=reduced?3:5;
                double dx=c.a()-c.x(),dz=c.b()-c.z();
                double n=Math.max(.001,Math.hypot(dx,dz));
                double ox=-dz/n*Math.min(.55,c.width()*.46),oz=dx/n*Math.min(.55,c.width()*.46);
                for(int i=0;i<count && emitted<budget;i++){
                    double t=(i+.5)/count;
                    double side=(i&1)==0?-1:1;
                    double x=c.x()+dx*t+ox*side,z=c.z()+dz*t+oz*side;
                    wisp(level,x,packet.y()+.12+(i%3)*.07,z,start,end,.22f,23,0,.009,0,.020);
                    emitted++;
                }
                if(!reduced && textured<3 && n>5.0){
                    double mx=(c.x()+c.a())*.5,mz=(c.z()+c.b())*.5;
                    GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,new Vec3(mx,packet.y()+.7,mz),.72f,.72f,320,0,90f,true,.34f);
                    textured++;
                }
            }
        }
    }

    private static Color color(int rgb) {
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
    }

    private static void worldCardOrbit(Vec3 pos,double radius,int cards,int seed,boolean blood,int duration){
        Random r=new Random(seed*109L+17);
        for(int i=0;i<cards;i++){
            double ang=seed*.019+i*Math.PI*2.0/Math.max(1,cards),y=Math.sin(ang*1.7)*.8+(i%3)*.18;
            Vec3 q=pos.add(Math.cos(ang)*radius,y,Math.sin(ang)*radius);
            ResourceLocation tex=blood?(i%3==0?GamblerTextureFx.CARD_GOLD:GamblerTextureFx.CARD_BLOOD):(i%3==0?GamblerTextureFx.CARD_TEAL:GamblerTextureFx.CARD_GOLD);
            float yaw=(float)Math.toDegrees(-ang)+90f,pitch=-28f+r.nextFloat()*56f,roll=-35f+r.nextFloat()*70f;
            float yawSpeed=(i%2==0?70f:-70f)+(r.nextFloat()-.5f)*35f,rollSpeed=(i%2==0?95f:-95f)+(r.nextFloat()-.5f)*45f;
            GamblerTextureFx.card3D(tex,q,.82f,1.24f,duration,yaw,pitch,roll,yawSpeed,18f*(i%2==0?1:-1),rollSpeed,.82f);
        }
    }

    private static void tiltedTorus(Level level,Vec3 pos,double radius,double tube,double tiltXDeg,double tiltZDeg,Color a,Color b,int count,int life,int seed){
        double tx=Math.toRadians(tiltXDeg),tz=Math.toRadians(tiltZDeg),cx=Math.cos(tx),sx=Math.sin(tx),cz=Math.cos(tz),sz=Math.sin(tz);
        Random r=new Random(seed*0x9E3779B9L+17);
        for(int i=0;i<count;i++){
            double u=i*Math.PI*2.0/Math.max(1,count)+seed*.007;
            double v=(i*2.399963229728653+seed*.013)%(Math.PI*2.0);
            double rr=radius+tube*Math.cos(v);
            double x=rr*Math.cos(u),y=tube*Math.sin(v),z=rr*Math.sin(u);
            double y1=y*cx-z*sx,z1=y*sx+z*cx,x2=x*cz-y1*sz,y2=x*sz+y1*cz;
            Color start=(i&1)==0?a:b,end=(i&1)==0?b:a;
            wisp(level,pos.x+x2,pos.y+y2,pos.z+z1,start,end,.16f+r.nextFloat()*.10f,life,0,.006,0,.018);
        }
    }

    private static void sphereShell(Level level,Vec3 pos,double radius,Color a,Color b,int count,int life,int seed){
        Random r=new Random(seed*31L+7);
        double golden=Math.PI*(3-Math.sqrt(5));
        for(int i=0;i<count;i++){
            double y=1.0-2.0*(i+.5)/Math.max(1,count),rr=Math.sqrt(Math.max(0,1-y*y)),ang=i*golden+seed*.01;
            double wobble=radius*(.90+r.nextDouble()*.18);
            double x=Math.cos(ang)*rr*wobble,z=Math.sin(ang)*rr*wobble,yy=y*wobble;
            wisp(level,pos.x+x,pos.y+yy,pos.z+z,(i%3==0)?b:a,(i%3==0)?a:b,.13f+r.nextFloat()*.11f,life,-x*.0018,-yy*.0014,-z*.0018,.016);
        }
    }

    private static void coneBurst(Level level,Vec3 origin,double angle,double length,Color a,Color b,int count,int life,int seed){
        Random r=new Random(seed*67L+3);double fx=Math.cos(angle),fz=Math.sin(angle),rx=-fz,rz=fx;
        for(int i=0;i<count;i++){
            double t=.12+r.nextDouble()*.88,spread=(.12+t*.58)*length*(r.nextDouble()*2-1),up=(r.nextDouble()-.32)*(.25+t*.95)*length;
            double forward=t*length;
            double x=origin.x+fx*forward+rx*spread*.38,z=origin.z+fz*forward+rz*spread*.38,y=origin.y+up*.34;
            wisp(level,x,y,z,a,b,.15f+r.nextFloat()*.13f,life,fx*(.018+.018*t),.004+up*.0015,fz*(.018+.018*t),.022);
        }
    }

    private static void casinoVortex(Level level,Vec3 base,double radius,double height,Color a,Color b,int count,int life,int seed){
        Random r=new Random(seed*101L+11);
        for(int i=0;i<count;i++){
            double f=i/(double)Math.max(1,count-1),ang=seed*.011+f*Math.PI*7.0+(i%3)*.55;
            double r0=radius*(.35+.65*(1-Math.abs(f-.5)*1.25));
            double x=Math.cos(ang)*r0,z=Math.sin(ang)*r0,y=f*height+.18*Math.sin(ang*2.0);
            double tang=.018+.015*f;
            wisp(level,base.x+x,base.y+y,base.z+z,(i&1)==0?a:b,(i&1)==0?b:a,.15f+r.nextFloat()*.12f,life,-Math.sin(ang)*tang,.012+f*.008,Math.cos(ang)*tang,.024);
        }
    }

    private static void sigil(Level level, Vec3 pos, double radius, Color a, Color b, int count, int seed) {
        int outer = Math.max(12, count / 2);
        ring(level, pos.add(0, .08, 0), radius, a, b, outer, 42);
        ring(level, pos.add(0, .13, 0), radius * .62, b, a, Math.max(10, outer / 2), 38);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4.0 + seed * .031;
            for (double r = radius * .28; r <= radius * .88; r += Math.max(.45, radius / 8.0)) {
                wisp(level, pos.x + Math.cos(ang) * r, pos.y + .14, pos.z + Math.sin(ang) * r, a, b, .26f, 28, 0, .006, 0, .03);
            }
        }
    }

    private static void ring(Level level, Vec3 pos, double radius, Color a, Color b, int count, int lifetime) {
        for (int i = 0; i < count; i++) {
            double ang = i * Math.PI * 2.0 / count;
            wisp(level, pos.x + Math.cos(ang) * radius, pos.y, pos.z + Math.sin(ang) * radius, a, b, .28f, lifetime, 0, .008, 0, .035);
        }
    }

    private static void pillar(Level level, Vec3 pos, double height, Color a, Color b, int count) {
        for (int i = 0; i < count; i++) {
            double y = pos.y + height * (i / (double) Math.max(1, count - 1));
            double ang = i * 2.399963229728653;
            double radius = .12 + .26 * Math.sin(i * .7) * Math.sin(i * .7);
            wisp(level, pos.x + Math.cos(ang) * radius, y, pos.z + Math.sin(ang) * radius, a, b, .33f, 34, 0, .018, 0, .06);
        }
    }

    private static void boundaryCardFreezeBurst(Vec3 center,double radius,int count,int seed){
        for(int i=0;i<count;i++){
            double ang=i*Math.PI*2.0/Math.max(1,count)+(seed%360)*0.013;
            double x=center.x+Math.cos(ang)*radius,z=center.z+Math.sin(ang)*radius;
            float yaw=(float)(-Math.toDegrees(ang)+90.0);
            GamblerTextureFx.card3D(GamblerTextureFx.CARD_BACK,new Vec3(x,center.y+1.0+(i%5)*.28,z),.86f,1.28f,1100,yaw,0f,(i%2==0?-8f:8f),0f,0f,(i%2==0?18f:-18f),.82f);
        }
    }

    private static void boundaryCardCollapse(Vec3 center,double radius,int count,int seed){
        Random random=new Random(seed*211L+29L);
        for(int i=0;i<count;i++){
            double ang=i*Math.PI*2.0/Math.max(1,count)+random.nextDouble()*.12;
            double x=center.x+Math.cos(ang)*radius,z=center.z+Math.sin(ang)*radius;
            double targetAng=ang+Math.PI;
            float yaw=(float)(-Math.toDegrees(targetAng)+90.0);
            GamblerTextureFx.card3D(GamblerTextureFx.CARD_BACK,new Vec3(x,center.y+0.6+(i%6)*.22,z),.92f,1.38f,1650,yaw,(i%3-1)*5f,(i%2==0?-14f:14f),(float)(-Math.cos(ang)*22f),0f,(i%2==0?24f:-24f),.88f);
            GamblerTextureFx.billboard(GamblerTextureFx.CROSS_FLARE,new Vec3((center.x+x)*.5,center.y+1.2+(i%4)*.22,(center.z+z)*.5),.82f,.82f,520,i*11,120f,true,.42f);
        }
    }

    private static void burst(Level level, Vec3 pos, Color a, Color b, int count, double spread, int lifetime, int seed) {
        Random random = new Random(seed * 0x9E3779B9L + 0x51D7348DL);
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - .5) * spread * 2.0;
            double oy = (random.nextDouble() - .35) * spread * 1.35;
            double oz = (random.nextDouble() - .5) * spread * 2.0;
            wisp(level, pos.x + ox, pos.y + oy, pos.z + oz, a, b, .30f + random.nextFloat() * .25f, lifetime, ox * .006, .018 + Math.abs(oy) * .009, oz * .006, .08);
        }
    }

    private static void spiral(Level level, Vec3 pos, double radius, Color a, Color b, int count, int seed) {
        for (int i = 0; i < count; i++) {
            double t = i / (double) Math.max(1, count - 1);
            double ang = seed * .03 + t * Math.PI * 5.0;
            double r = .6 + radius * t;
            wisp(level, pos.x + Math.cos(ang) * r, pos.y + .1 + t * .7, pos.z + Math.sin(ang) * r, a, b, .28f + (float) t * .18f, 30 + (int) (t * 18), 0, .014, 0, .045);
        }
    }

    private static void wall(Level level, Vec3 pos, double half, boolean vertical, Color a, Color b, boolean reduced) {
        int count = reduced ? 12 : 34;
        for (int i = 0; i < count; i++) {
            double t = count <= 1 ? 0 : i / (double) (count - 1);
            double along = -half + half * 2.0 * t;
            double x = vertical ? pos.x : pos.x + along;
            double z = vertical ? pos.z + along : pos.z;
            double y = pos.y + .12 + (i % 5) * .32;
            wisp(level, x, y, z, a, b, .34f, 28, 0, .01, 0, .045);
        }
    }

    private static void slashLine(Level level,Vec3 pos,double angle,double radius,Color a,Color b,int count){
        for(int i=0;i<count;i++){
            double t=(i+.5)/count;
            double r=2.0+(radius-2.0)*t;
            double side=(i&1)==0?-.14:.14;
            double x=pos.x+Math.cos(angle)*r-Math.sin(angle)*side;
            double z=pos.z+Math.sin(angle)*r+Math.cos(angle)*side;
            wisp(level,x,pos.y+.14+(i%3)*.05,z,a,b,.22f,22,Math.cos(angle)*.015,.006,Math.sin(angle)*.015,.018);
        }
    }

    private static void gridBurst(Level level,Vec3 pos,double half,Color a,Color b,boolean reduced){
        int lines=reduced?3:5,steps=reduced?7:13;
        for(int i=0;i<lines;i++){
            double k=-half*.65+i*(half*1.3/Math.max(1,lines-1));
            for(int j=0;j<steps;j++){
                double t=-half+2*half*j/Math.max(1,steps-1);
                wisp(level,pos.x+k,pos.y+.12,pos.z+t,a,b,.20f,22,0,.012,0,.018);
                wisp(level,pos.x+t,pos.y+.15,pos.z+k,b,a,.20f,22,0,.012,0,.018);
            }
        }
    }

    private static void wisp(Level level, double x, double y, double z, Color start, Color end,
                             float scale, int lifetime, double mx, double my, double mz, double randomMotion) {
        WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                .setColorData(ColorParticleData.create(start, end).build())
                .setTransparencyData(GenericParticleData.create(.92f, 0f).build())
                .setScaleData(GenericParticleData.create(scale, 0f).build())
                .setLifetime(lifetime)
                .addMotion(mx, my, mz)
                .setRandomMotion(randomMotion, randomMotion * .7, randomMotion)
                .enableNoClip()
                .spawn(level, x, y, z);
    }
}
