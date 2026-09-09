package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.net.WardLodestoneFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;

import java.awt.Color;
import java.util.Random;

public final class WardLodestoneFx {
    private static final Color GOLD = new Color(244, 220, 160);
    private static final Color IVORY = new Color(255, 242, 220);
    private static final Color TEAL = new Color(104, 224, 215);
    private static final Color VIOLET = new Color(144, 108, 236);
    private static final Color CRIMSON = new Color(214, 74, 110);
    private static final Color ASH = new Color(129, 120, 108);
    private static final Color EMBER = new Color(255, 112, 48);
    private static final Color SOUL = new Color(88, 170, 255);
    private static final Color ELDRITCH_GREEN = new Color(116, 232, 151);
    private static final Color DEEP_VIOLET = new Color(79, 43, 122);
    private static final Color ABYSS = new Color(23, 29, 44);

    private WardLodestoneFx() {}

    public static void spawn(WardLodestoneFxPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        spawnLocal(new Vec3(msg.x(), msg.y(), msg.z()), msg.kind(), msg.scale());
    }

    public static void spawnLocal(Vec3 pos, int kind, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        emitMotif(pos, kind, scale);
        switch (kind) {
            case WardLodestoneFxPacket.WARD_OPEN -> wardOpen(mc.level, pos, scale);
            case WardLodestoneFxPacket.WARD_SUCCESS -> wardSuccess(mc.level, pos, scale);
            case WardLodestoneFxPacket.WARD_FAIL -> wardFailure(mc.level, pos, scale);
            case WardLodestoneFxPacket.WARD_PERFECT -> wardPerfect(mc.level, pos, scale);
            case WardLodestoneFxPacket.BARGAIN_MASTER -> glyph(mc.level, pos, scale, GOLD, VIOLET, 26, 1.0);
            case WardLodestoneFxPacket.BARGAIN_CURSE -> curse(mc.level, pos, scale, CRIMSON, VIOLET, 24, 1.0);
            case WardLodestoneFxPacket.BARGAIN_DEATH -> curse(mc.level, pos, scale, CRIMSON, ASH, 30, 1.15);
            case WardLodestoneFxPacket.BARGAIN_RITUAL -> glyph(mc.level, pos, scale, TEAL, GOLD, 18, 0.9);
            case WardLodestoneFxPacket.BARGAIN_COVENANT -> glyph(mc.level, pos, scale, CRIMSON, SOUL, 20, 0.95);
            case WardLodestoneFxPacket.REMEDY -> blessing(mc.level, pos, scale, GOLD, IVORY, 18, 0.85);
            case WardLodestoneFxPacket.CONTRACT -> pageSwirl(mc.level, pos, scale, IVORY, GOLD, 14, 0.75);
            case WardLodestoneFxPacket.RELIC_SPLINTER -> blessing(mc.level, pos, scale, TEAL, GOLD, 20, 0.95);
            case WardLodestoneFxPacket.RELIC_ELDRITCH -> glyph(mc.level, pos, scale, VIOLET, TEAL, 24, 1.05);
            case WardLodestoneFxPacket.RELIC_ECHO -> blessing(mc.level, pos, scale, SOUL, IVORY, 22, 1.0);
            case WardLodestoneFxPacket.RELIC_BLOODGLASS -> curse(mc.level, pos, scale, CRIMSON, EMBER, 20, 0.85);
            case WardLodestoneFxPacket.LEDGER_WRITE -> pageSwirl(mc.level, pos, scale, IVORY, VIOLET, 16, 0.8);
            case WardLodestoneFxPacket.CTHULHU_FIELD -> glyph(mc.level, pos, scale, TEAL, VIOLET, 26, 1.2);
            case WardLodestoneFxPacket.CTHULHU_TARGET -> blessing(mc.level, pos, scale, TEAL, SOUL, 12, 0.6);
            case WardLodestoneFxPacket.CURSE_SIGN -> curse(mc.level, pos, scale, CRIMSON, ASH, 16, 0.75);
            case WardLodestoneFxPacket.DEATH_AURA -> blessing(mc.level, pos, scale, SOUL, ASH, 10, 0.45);
            case WardLodestoneFxPacket.ASHEN_BRAND -> blessing(mc.level, pos, scale, EMBER, CRIMSON, 14, 0.55);
            case WardLodestoneFxPacket.BARGAIN_DEBT -> curse(mc.level,pos,scale,CRIMSON,GOLD,16,.7);
            case WardLodestoneFxPacket.BARGAIN_WAGER -> glyph(mc.level,pos,scale,GOLD,TEAL,18,.8);
            case WardLodestoneFxPacket.BARGAIN_SCAR -> curse(mc.level,pos,scale,CRIMSON,ASH,18,.75);
            case WardLodestoneFxPacket.BARGAIN_EPIC -> glyph(mc.level,pos,scale,GOLD,VIOLET,22,.95);
            case WardLodestoneFxPacket.BARGAIN_UNIQUE -> glyph(mc.level,pos,scale,IVORY,TEAL,24,1.0);
            case WardLodestoneFxPacket.BARGAIN_REFRESH -> blessing(mc.level,pos,scale,SOUL,IVORY,16,.72);
            case WardLodestoneFxPacket.MASTER_CROOKED -> glyph(mc.level,pos,scale,GOLD,VIOLET,24,1.0);
            case WardLodestoneFxPacket.MASTER_VEILED -> glyph(mc.level,pos,scale,VIOLET,TEAL,24,1.0);
            case WardLodestoneFxPacket.MASTER_EXACTING -> glyph(mc.level,pos,scale,IVORY,CRIMSON,24,1.0);
            case WardLodestoneFxPacket.CARD_HAND_OPEN -> cardOpen(mc.level,pos,scale);
            case WardLodestoneFxPacket.CARD_HOVER -> cardHover(mc.level,pos,scale);
            case WardLodestoneFxPacket.CARD_SIGN -> cardSign(mc.level,pos,scale);
            case WardLodestoneFxPacket.DEALER_TABLE_SWEEP -> dealerSweep(mc.level,pos,scale);
            case WardLodestoneFxPacket.DEALER_CUT_WAVE -> dealerWave(mc.level,pos,scale);
            case WardLodestoneFxPacket.DEALER_DEAD_DRAW -> dealerMarked(mc.level,pos,scale);
            case WardLodestoneFxPacket.DEALER_DOUBLE_DOWN -> dealerDouble(mc.level,pos,scale);
            case WardLodestoneFxPacket.DEALER_ROYAL_FLUSH -> dealerFlush(mc.level,pos,scale);
            case WardLodestoneFxPacket.DEALER_HOUSE_COLLAPSE -> dealerCollapse(mc.level,pos,scale);
            case WardLodestoneFxPacket.REWARD_LAST_MARGIN -> lastMargin(mc.level,pos,scale);
            case WardLodestoneFxPacket.REWARD_UNSIGNED_VERDICT -> unsignedVerdict(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_MANIFEST -> cthulhuManifest(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_EYE_CHARGE -> cthulhuEyeCharge(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_EYE_BEAM -> cthulhuEyeBeam(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_TENTACLE_TELEGRAPH -> cthulhuTentacleTelegraph(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_TENTACLE_IMPACT -> cthulhuTentacleImpact(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_GRASP -> cthulhuGrasp(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_ROAR -> cthulhuRoar(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_VOID_RIFT -> cthulhuVoidRift(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_SLAM_TELEGRAPH -> cthulhuSlamTelegraph(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_SLAM_IMPACT -> cthulhuSlamImpact(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_PULSE -> cthulhuPulse(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_PHASE -> cthulhuPhase(mc.level,pos,scale);
            case WardLodestoneFxPacket.CTHULHU_DEATH -> cthulhuDeath(mc.level,pos,scale);
            default -> wardOpen(mc.level, pos, scale);
        }
    }

    private static void emitMotif(Vec3 pos,int kind,float scale) {
        String name = switch(kind) {
            case WardLodestoneFxPacket.BARGAIN_DEBT -> "debt";
            case WardLodestoneFxPacket.BARGAIN_WAGER -> "wager";
            case WardLodestoneFxPacket.BARGAIN_SCAR -> "scar";
            case WardLodestoneFxPacket.REMEDY -> "remedy";
            case WardLodestoneFxPacket.CONTRACT -> "contract";
            case WardLodestoneFxPacket.BARGAIN_RITUAL -> "ritual";
            case WardLodestoneFxPacket.BARGAIN_COVENANT -> "covenant";
            case WardLodestoneFxPacket.BARGAIN_EPIC -> "epic";
            case WardLodestoneFxPacket.BARGAIN_UNIQUE -> "unique";
            case WardLodestoneFxPacket.BARGAIN_CURSE, WardLodestoneFxPacket.CURSE_SIGN -> "curse";
            case WardLodestoneFxPacket.BARGAIN_DEATH, WardLodestoneFxPacket.DEATH_AURA -> "death";
            case WardLodestoneFxPacket.BARGAIN_REFRESH -> "refresh";
            case WardLodestoneFxPacket.MASTER_CROOKED -> "master_crooked";
            case WardLodestoneFxPacket.MASTER_VEILED -> "master_veiled";
            case WardLodestoneFxPacket.MASTER_EXACTING -> "master_exacting";
            case WardLodestoneFxPacket.BARGAIN_MASTER -> "master_generic";
            default -> null;
        };
        if(name!=null) WardMotifTextureFx.ground(WardMotifTextureFx.motif(name),pos.add(0,.025,0),Math.max(2.8f,3.8f*scale),760,kind*17f,52f,.82f);
    }


    private static void dealerSweep(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level,pos,.85*scale,GOLD,VIOLET,20,24); ring(level,pos.add(0,.18,0),1.55*scale,VIOLET,GOLD,28,30); burst(level,pos.add(0,.3,0),GOLD,IVORY,20,1.25*scale,28,.035);
    }
    private static void dealerWave(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level,pos,.45*scale,GOLD,TEAL,12,18); pillar(level,pos,1.2*scale,VIOLET,GOLD,8); burst(level,pos,GOLD,VIOLET,8,.35*scale,18,.018);
    }
    private static void dealerMarked(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level,pos,.72*scale,CRIMSON,GOLD,18,26); doubleHelix(level,pos,.34*scale,1.5*scale,CRIMSON,GOLD,IVORY,18,28);
    }
    private static void dealerDouble(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level,pos,.62*scale,CRIMSON,GOLD,20,28); spiral(level,pos,.08*scale,.95*scale,GOLD,CRIMSON,22,30); burst(level,pos.add(0,.5,0),CRIMSON,IVORY,16,.75*scale,28,.03);
    }
    private static void dealerFlush(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level,pos,.34*scale,GOLD,IVORY,10,16); burst(level,pos,GOLD,TEAL,10,.30*scale,18,.012);
    }
    private static void dealerCollapse(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level,pos,.65*scale,CRIMSON,VIOLET,18,24); ring(level,pos.add(0,.12,0),1.45*scale,GOLD,CRIMSON,28,32); inwardSpiral(level,pos.add(0,1.4,0),1.4*scale,.12*scale,VIOLET,GOLD,28,34); fallingBurst(level,pos.add(0,2.0,0),GOLD,CRIMSON,24,1.1*scale,36);
    }
    private static void lastMargin(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level,pos,.82*scale,IVORY,EMBER,20,28); pageSwirl(level,pos,.95f*scale,IVORY,ASH,18,.7); burst(level,pos.add(0,.65,0),EMBER,IVORY,12,.55*scale,28,.018);
    }
    private static void unsignedVerdict(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level,pos,.9*scale,SILVER(),CRIMSON,22,30); pillar(level,pos,2.2*scale,IVORY,CRIMSON,14); spiral(level,pos.add(0,.2,0),.12*scale,.72*scale,CRIMSON,IVORY,18,30);
    }
    private static Color SILVER(){ return new Color(190,198,211); }

    private static void cthulhuManifest(net.minecraft.world.level.Level level,Vec3 pos,float scale){
        ring(level,pos,.9*scale,ELDRITCH_GREEN,DEEP_VIOLET,24,38);ring(level,pos.add(0,.45*scale,0),1.55*scale,VIOLET,ABYSS,30,46);doubleHelix(level,pos.add(0,.1,0),.75*scale,3.3*scale,ELDRITCH_GREEN,DEEP_VIOLET,IVORY,34,48);pillar(level,pos,3.8*scale,DEEP_VIOLET,ELDRITCH_GREEN,18);burst(level,pos.add(0,1.0,0),ABYSS,ELDRITCH_GREEN,24,1.4*scale,42,.03);
    }
    private static void cthulhuEyeCharge(net.minecraft.world.level.Level level,Vec3 pos,float scale){
        ring(level,pos,.28*scale,ELDRITCH_GREEN,IVORY,5,18);spiral(level,pos,.03*scale,.34*scale,IVORY,DEEP_VIOLET,5,20);burst(level,pos,ELDRITCH_GREEN,SOUL,2,.16*scale,18,.006);
    }
    private static void cthulhuEyeBeam(net.minecraft.world.level.Level level,Vec3 pos,float scale){
        ring(level,pos,.18*scale,IVORY,ELDRITCH_GREEN,4,14);burst(level,pos,ELDRITCH_GREEN,DEEP_VIOLET,4,.24*scale,16,.008);
    }
    private static void cthulhuTentacleTelegraph(net.minecraft.world.level.Level level,Vec3 pos,float scale){
        ring(level,pos,.34*scale,DEEP_VIOLET,ELDRITCH_GREEN,4,22);burst(level,pos,ABYSS,VIOLET,2,.18*scale,20,.004);
    }
    private static void cthulhuTentacleImpact(net.minecraft.world.level.Level level,Vec3 pos,float scale){
        burst(level,pos.add(0,.15,0),ELDRITCH_GREEN,ABYSS,6,.55*scale,26,.035);ring(level,pos,.52*scale,VIOLET,ELDRITCH_GREEN,6,24);
    }
    private static void cthulhuGrasp(net.minecraft.world.level.Level level,Vec3 pos,float scale){
        ring(level,pos,.24*scale,ELDRITCH_GREEN,DEEP_VIOLET,4,22);burst(level,pos.add(0,.1,0),ABYSS,ELDRITCH_GREEN,2,.16*scale,22,.006);
    }
    private static void cthulhuRoar(net.minecraft.world.level.Level level,Vec3 pos,float scale){
        ring(level,pos,.22*scale,IVORY,DEEP_VIOLET,4,20);burst(level,pos,ABYSS,ELDRITCH_GREEN,2,.18*scale,20,.012);
    }
    private static void cthulhuVoidRift(net.minecraft.world.level.Level level,Vec3 pos,float scale){
        ring(level,pos,.28*scale,DEEP_VIOLET,ELDRITCH_GREEN,4,24);burst(level,pos.add(0,.1,0),ABYSS,ELDRITCH_GREEN,3,.22*scale,24,.018);
    }
    private static void cthulhuSlamTelegraph(net.minecraft.world.level.Level level,Vec3 pos,float scale){ring(level,pos,.4*scale,CRIMSON,DEEP_VIOLET,4,20);burst(level,pos,DEEP_VIOLET,ASH,2,.25*scale,18,.008);}
    private static void cthulhuSlamImpact(net.minecraft.world.level.Level level,Vec3 pos,float scale){burst(level,pos.add(0,.1,0),ABYSS,ELDRITCH_GREEN,16,.82*scale,30,.055);ring(level,pos,.72*scale,ELDRITCH_GREEN,CRIMSON,16,28);}
    private static void cthulhuPulse(net.minecraft.world.level.Level level,Vec3 pos,float scale){ring(level,pos,.24*scale,ELDRITCH_GREEN,DEEP_VIOLET,4,22);burst(level,pos,ABYSS,IVORY,2,.15*scale,20,.008);}
    private static void cthulhuPhase(net.minecraft.world.level.Level level,Vec3 pos,float scale){ring(level,pos,.9*scale,ELDRITCH_GREEN,VIOLET,18,34);doubleHelix(level,pos,.62*scale,2.3*scale,DEEP_VIOLET,ELDRITCH_GREEN,IVORY,20,36);burst(level,pos.add(0,.8,0),ELDRITCH_GREEN,ABYSS,14,.9*scale,32,.028);}
    private static void cthulhuDeath(net.minecraft.world.level.Level level,Vec3 pos,float scale){ring(level,pos,1.3*scale,DEEP_VIOLET,ASH,28,46);fallingBurst(level,pos.add(0,2.4*scale,0),ELDRITCH_GREEN,ABYSS,30,1.3*scale,52);inwardSpiral(level,pos.add(0,1,0),1.4*scale,.04*scale,IVORY,DEEP_VIOLET,28,48);}

    private static void wardSuccess(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        // A clean ward releases outward: two resolving rings, then a compact rising crown.
        ring(level, pos.add(0, .05, 0), .62 * scale, TEAL, GOLD, 16, 24);
        ring(level, pos.add(0, .24 * scale, 0), 1.08 * scale, GOLD, IVORY, 22, 30);
        burst(level, pos.add(0, .35 * scale, 0), TEAL, IVORY, 18, .72 * scale, 30, .025);
        pillar(level, pos.add(0, .08, 0), 1.65 * scale, IVORY, TEAL, 12);
    }

    private static void wardFailure(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        // Failure collapses inward/down instead of using the same outward celebratory language.
        ring(level, pos.add(0, .05, 0), 1.10 * scale, CRIMSON, ASH, 20, 28);
        inwardSpiral(level, pos.add(0, .72 * scale, 0), 1.05 * scale, .10 * scale, CRIMSON, VIOLET, 24, 32);
        fallingBurst(level, pos.add(0, 1.20 * scale, 0), CRIMSON, ASH, 18, .78 * scale, 34);
    }

    private static void wardPerfect(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        // Perfect is not simply "more success": it resolves symmetrically and climbs into a double helix.
        ring(level, pos.add(0, .04, 0), .55 * scale, IVORY, GOLD, 18, 26);
        ring(level, pos.add(0, .18 * scale, 0), 1.05 * scale, TEAL, IVORY, 26, 34);
        ring(level, pos.add(0, .38 * scale, 0), 1.48 * scale, GOLD, SOUL, 30, 38);
        doubleHelix(level, pos.add(0, .18, 0), .62 * scale, 2.35 * scale, GOLD, TEAL, IVORY, 28, 40);
        burst(level, pos.add(0, 1.15 * scale, 0), IVORY, GOLD, 24, .95 * scale, 34, .018);
    }

    private static void inwardSpiral(net.minecraft.world.level.Level level, Vec3 pos, double startRadius, double endRadius, Color a, Color b, int count, int life) {
        for (int i = 0; i < count; i++) {
            double t = i / (double)Math.max(1, count - 1), ang = t * Math.PI * 4.5;
            double radius = startRadius + (endRadius - startRadius) * t;
            wisp(level, pos.x + Math.cos(ang) * radius, pos.y - t * .62, pos.z + Math.sin(ang) * radius,
                    a, b, .20f + (float)(1.0 - t) * .09f, life, -Math.cos(ang) * .008, -.024, -Math.sin(ang) * .008, .026);
        }
    }

    private static void fallingBurst(net.minecraft.world.level.Level level, Vec3 pos, Color a, Color b, int count, double spread, int life) {
        Random r = new Random(Double.doubleToLongBits(pos.x * 41 + pos.y * 17 + pos.z * 73));
        for (int i = 0; i < count; i++) {
            double ang=r.nextDouble()*Math.PI*2, rr=Math.sqrt(r.nextDouble())*spread;
            double ox=Math.cos(ang)*rr, oz=Math.sin(ang)*rr, oy=r.nextDouble()*.55*spread;
            wisp(level,pos.x+ox,pos.y+oy,pos.z+oz,a,b,.16f+r.nextFloat()*.16f,life,
                    -ox*.004,-.035-r.nextDouble()*.025,-oz*.004,.025);
        }
    }

    private static void doubleHelix(net.minecraft.world.level.Level level, Vec3 pos, double radius, double height, Color a, Color b, Color c, int count, int life) {
        for(int i=0;i<count;i++){
            double t=i/(double)Math.max(1,count-1), ang=t*Math.PI*4.0, y=pos.y+t*height;
            for(int arm=0;arm<2;arm++){
                double aa=ang+arm*Math.PI;
                Color start=arm==0?a:b, end=arm==0?c:a;
                wisp(level,pos.x+Math.cos(aa)*radius,y,pos.z+Math.sin(aa)*radius,start,end,.16f+(float)t*.10f,life,
                        -Math.sin(aa)*.008,.018,Math.cos(aa)*.008,.012);
            }
        }
    }

    private static void cardOpen(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level, pos, 0.34 * scale, GOLD, VIOLET, 10, 18);
        burst(level, pos, GOLD, TEAL, 8, 0.22 * scale, 18, 0.014);
    }

    private static void cardHover(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level, pos, 0.22 * scale, IVORY, TEAL, 6, 12);
        burst(level, pos, IVORY, VIOLET, 4, 0.12 * scale, 12, 0.008);
    }

    private static void cardSign(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level, pos, 0.46 * scale, GOLD, IVORY, 14, 24);
        spiral(level, pos, 0.08 * scale, 0.42 * scale, VIOLET, TEAL, 12, 22);
        burst(level, pos, GOLD, VIOLET, 12, 0.32 * scale, 24, 0.02);
    }

    private static void wardOpen(net.minecraft.world.level.Level level, Vec3 pos, float scale) {
        ring(level, pos, 0.95 * scale, GOLD, TEAL, 14, 26);
        burst(level, pos.add(0, 0.15, 0), GOLD, IVORY, 12, 0.45 * scale, 26, 0.04);
        pillar(level, pos.add(0, 0.05, 0), 1.5 * scale, TEAL, GOLD, 12);
    }

    private static void blessing(net.minecraft.world.level.Level level, Vec3 pos, float scale, Color a, Color b, int count, double spread) {
        ring(level, pos, 0.7 * scale, a, b, count, 22);
        burst(level, pos.add(0, 0.1, 0), a, b, count, spread * scale, 24, 0.03);
    }

    private static void curse(net.minecraft.world.level.Level level, Vec3 pos, float scale, Color a, Color b, int count, double spread) {
        spiral(level, pos.add(0, 0.1, 0), 0.18 * scale, 0.8 * scale, a, b, count, 28);
        burst(level, pos.add(0, 0.1, 0), a, b, count, spread * scale, 30, 0.055);
    }

    private static void glyph(net.minecraft.world.level.Level level, Vec3 pos, float scale, Color a, Color b, int count, double spread) {
        ring(level, pos, 0.95 * scale, a, b, count, 28);
        ring(level, pos.add(0, 0.28 * scale, 0), 0.55 * scale, b, a, Math.max(8, count / 2), 24);
        pillar(level, pos.add(0, 0.05, 0), 1.8 * scale, a, b, 14);
        burst(level, pos.add(0, 0.2, 0), a, b, Math.max(8, count - 6), spread * scale, 26, 0.03);
    }

    private static void pageSwirl(net.minecraft.world.level.Level level, Vec3 pos, float scale, Color a, Color b, int count, double spread) {
        Random r = new Random(Double.doubleToLongBits(pos.x + pos.y * 31 + pos.z * 13) ^ (long)(scale * 1000));
        for (int i = 0; i < count; i++) {
            double t = i / (double) Math.max(1, count - 1);
            double ang = t * Math.PI * 2.4 + r.nextDouble() * 0.25;
            double radius = (0.35 + t * spread) * scale;
            wisp(level, pos.x + Math.cos(ang) * radius, pos.y + 0.1 + t * 0.8 * scale, pos.z + Math.sin(ang) * radius,
                    a, b, 0.22f + (float) t * 0.09f, 26 + i % 6, -Math.sin(ang) * 0.01, 0.018, Math.cos(ang) * 0.01, 0.03);
        }
    }

    private static void ring(net.minecraft.world.level.Level level, Vec3 pos, double radius, Color a, Color b, int count, int life) {
        for (int i = 0; i < count; i++) {
            double ang = i * (Math.PI * 2.0 / count);
            wisp(level, pos.x + Math.cos(ang) * radius, pos.y, pos.z + Math.sin(ang) * radius,
                    a, b, 0.24f, life, 0, 0.008, 0, 0.015);
        }
    }

    private static void pillar(net.minecraft.world.level.Level level, Vec3 pos, double height, Color a, Color b, int count) {
        for (int i = 0; i < count; i++) {
            double t = i / (double) Math.max(1, count - 1);
            wisp(level, pos.x, pos.y + t * height, pos.z, a, b, 0.18f + (float)t * 0.08f, 22 + i, 0, 0.018, 0, 0.018);
        }
    }

    private static void spiral(net.minecraft.world.level.Level level, Vec3 pos, double startRadius, double endRadius, Color a, Color b, int count, int life) {
        for (int i = 0; i < count; i++) {
            double t = i / (double) Math.max(1, count - 1);
            double ang = t * Math.PI * 4.0;
            double radius = startRadius + (endRadius - startRadius) * t;
            wisp(level, pos.x + Math.cos(ang) * radius, pos.y + 0.1 + t * 0.8, pos.z + Math.sin(ang) * radius,
                    a, b, 0.18f + (float)t * 0.08f, life, 0, 0.01, 0, 0.03);
        }
    }

    private static void burst(net.minecraft.world.level.Level level, Vec3 pos, Color a, Color b, int count, double spread, int lifetime, double randomMotion) {
        Random random = new Random(Double.doubleToLongBits(pos.x * 17 + pos.y * 37 + pos.z * 67));
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - .5) * spread * 2.0;
            double oy = (random.nextDouble() - .35) * spread * 1.25;
            double oz = (random.nextDouble() - .5) * spread * 2.0;
            wisp(level, pos.x + ox, pos.y + oy, pos.z + oz, a, b, 0.20f + random.nextFloat() * .18f, lifetime,
                    ox * .008, .014 + Math.abs(oy) * .01, oz * .008, randomMotion);
        }
    }

    private static void wisp(net.minecraft.world.level.Level level, double x, double y, double z, Color start, Color end,
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
