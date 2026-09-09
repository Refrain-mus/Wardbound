package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Pale Gambler's boss bar is a bespoke eldritch table instrument.
 * This version leans harder into pixel-art ornament, with heavier framing,
 * denser backdrop work, and more theatrical phase presentation.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class MasterBossBar {
    private static final ResourceLocation ATLAS = new ResourceLocation(Wardbound.MODID, "textures/gui/gambler_bossbar_atlas.png");
    private static final int ATLAS_W = 256;
    private static final int ATLAS_H = 128;
    private static final int BAR_W = 182;
    private static final int FRAME_W = 208;
    private static final int FRAME_H = 32;
    private static final int EYE_X = 208;
    private static final int EYE_W = 24;
    private static final int EYE_H = 14;

    private MasterBossBar() {}

    @SubscribeEvent
    public static void draw(CustomizeGuiOverlayEvent.BossEventProgress e) {
        if (!(e.getBossEvent().getName().getContents() instanceof TranslatableContents t)
                || !t.getKey().equals("entity.wardbound.pale_gambler")) return;

        e.setCanceled(true);
        e.setIncrement(56);

        GuiGraphics g = e.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int x = e.getX();
        int y = e.getY() + 13;
        float progress = Math.max(0f, Math.min(1f, e.getBossEvent().getProgress()));
        int phase = progress <= 0.34f ? 3 : progress <= 0.67f ? 2 : 1;
        long now = System.currentTimeMillis();
        boolean reduceMotion = WardConfig.accessibilityReduceMotion;
        PaleGamblerEntity gambler=nearbyGambler();
        boolean openTell = gambler!=null && gambler.openTell();
        boolean finalTell = gambler!=null && gambler.finalTell();
        boolean allIn = gambler!=null && gambler.allIn();
        int readLevel = gambler==null?0:gambler.houseReadLevel();
        int activeHand = gambler==null?0:gambler.currentHand();
        int frameY = (phase - 1) * 32;

        int glow = switch (phase) {
            case 3 -> 0x5A29030F;
            case 2 -> 0x4B28134E;
            default -> 0x3E1A143C;
        };
        int fog = switch (phase) {
            case 3 -> 0xCC0B050F;
            case 2 -> 0xCC090813;
            default -> 0xCC07070E;
        };
        int fill = switch (phase) {
            case 3 -> 0xFF8F1736;
            case 2 -> 0xFF7550B5;
            default -> 0xFF5F54B1;
        };
        int highlight = switch (phase) {
            case 3 -> 0xFFE29AA8;
            case 2 -> 0xFFE5C979;
            default -> 0xFFD1B6F0;
        };
        int shadow = switch (phase) {
            case 3 -> 0xFF300711;
            case 2 -> 0xFF251431;
            default -> 0xFF1A1128;
        };
        int ink = 0xB4060912;
        int bone = phase == 3 ? 0xFFF4D9D3 : 0xFFF0E3BE;
        int small = phase == 3 ? 0xFFE6A4B0 : phase == 2 ? 0xFFE5C478 : 0xFFD1B6F0;
        int whisper = openTell ? 0xFF8EF2D8 : (phase == 3 ? 0xFFD6A2AA : 0xFFB7AA8A);
        if(allIn && !openTell){fill=0xFFB41436;highlight=0xFFFFB4C4;shadow=0xFF3A030E;small=0xFFFFC0A2;}
        if(openTell){fill=finalTell?0xFF36C9B0:0xFF3EA88F;highlight=0xFFF4E4A8;shadow=0xFF123A35;small=0xFFF1D992;}

        int breathe = reduceMotion ? 0 : (phase == 3 ? Math.floorMod((int) (now / 80L), 5) : Math.floorMod((int) (now / 120L), 3));

        // Large stained plate behind the whole instrument so it reads as a ritual overlay.
        g.fill(x - 30 - breathe, y - 28 - breathe, x + BAR_W + 30 + breathe, y + 34 + breathe, glow);
        g.fill(x - 25, y - 24, x + BAR_W + 25, y + 29, fog);
        g.fill(x - 18, y - 21, x + BAR_W + 18, y + 26, ink);
        g.fill(x - 8, y - 20, x + BAR_W + 8, y - 19, phase == 3 ? 0x88D66E80 : 0x88BCA466);
        g.fill(x - 8, y + 20, x + BAR_W + 8, y + 21, phase == 3 ? 0x88D66E80 : 0x887764B4);

        // Explicit empty well first: the missing health must remain visually obvious even when
        // shaders/resource packs brighten the gilded atlas around it.
        int filled = Math.round(BAR_W * progress);
        g.fill(x, y + 2, x + BAR_W, y + 10, phase == 3 ? 0xFF21070F : 0xFF120B1B);
        if (filled < BAR_W) {
            g.fill(x + filled, y + 3, x + BAR_W, y + 9, phase == 3 ? 0xFF3A0A18 : 0xFF251333);
        }
        if (filled > 0) {
            g.fill(x, y + 2, x + filled, y + 9, fill);
            g.fill(x, y + 2, x + filled, y + 4, highlight);
            g.fill(x, y + 8, x + filled, y + 10, shadow);
            if (!reduceMotion) {
                int sweep = x + Math.floorMod((int) (now / (phase == 3 ? 42L : 62L)) * 5, BAR_W + 36) - 18;
                int a = Math.max(x, sweep);
                int b = Math.min(x + filled, sweep + 22);
                if (b > a) g.fill(a, y + 3, b, y + 8, phase == 3 ? 0x66F09AA8 : 0x55F2D89B);
            }
        }
        for (int i = 1; i < 13; i++) {
            int sx = x + i * BAR_W / 13;
            g.fill(sx, y + 4, sx + 1, y + 9, phase == 3 ? 0x88C86E7B : 0x88DCC48C);
        }

        // decorative frame atlas
        g.blit(ATLAS, x - 13, y - 11, 0, frameY, FRAME_W, FRAME_H, ATLAS_W, ATLAS_H);

        // eye and phase sigil
        int eyeFrame = eyeFrame(now, phase, reduceMotion);
        g.blit(ATLAS, x + BAR_W / 2 - EYE_W / 2, y - 10, EYE_X, eyeFrame * 16, EYE_W, EYE_H, ATLAS_W, ATLAS_H);
        g.blit(ATLAS, x + BAR_W - 5, y + 12, 208, 80 + (phase - 1) * 14, 20, 12, ATLAS_W, ATLAS_H);

        // moving health needle
        int needleX = x + Math.max(0, Math.min(BAR_W, filled));
        g.fill(needleX - 1, y - 1, needleX + 1, y + 11, phase == 3 ? 0xFFF0B4BE : 0xFFE8D49A);
        g.fill(needleX - 3, y - 2, needleX + 3, y - 1, phase == 3 ? 0xFF7F1930 : 0xFF806029);

        // Numerical fraction makes health loss readable even during very bright particle phases.
        String hp = Math.max(0, Math.min(100, Math.round(progress * 100f))) + "%";
        drawSmallCentered(g, font, hp, x + BAR_W / 2, y + 3, phase == 3 ? 0xFFFFD6DB : 0xFFF7E8C5, 0.64f);

        // title block
        drawSmallCentered(g, font, "✦ THE PALE GAMBLER ✦", x + BAR_W / 2, y - 24, bone, 1.08f);
        drawSmallCentered(g, font, allIn?"ALL IN // THE HOUSE BLEEDS":phaseTitle(phase), x + BAR_W / 2, y - 14, small, 0.84f);
        String stateLine;
        if(finalTell && openTell) stateLine="FINAL TELL // +150% PUNISH";
        else if(openTell){int bonus=readLevel>=3?90:readLevel==2?65:45;stateLine="HOUSE READ "+Math.max(1,readLevel)+"/3 // +"+bonus+"% PUNISH";}
        else if(activeHand!=0) stateLine="HAND // "+handName(activeHand);
        else if(readLevel>0) stateLine="READ "+readLevel+"/3 // SURVIVE ANOTHER CLEAN HAND";
        else stateLine=whisperLine(phase);
        drawSmallCentered(g, font, stateLine, x + BAR_W / 2, y + 15, whisper, 0.74f);
        drawTinyMarkers(g, x, y, phase);
    }

    private static PaleGamblerEntity nearbyGambler(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null)return null;
        double best=2304.0;
        PaleGamblerEntity found=null;
        for(var e:mc.level.entitiesForRendering())if(e instanceof PaleGamblerEntity g && g.isAlive()){double d=g.distanceToSqr(mc.player);if(d<best){best=d;found=g;}}
        return found;
    }

    private static String handName(int hand){
        return switch(hand){
            case 1->"CARD FAN";case 2->"HOUSE RINGS";case 3->"MARKED DEBT";case 4->"CLOCK SWEEP";case 5->"CARD CAGE";
            case 6->"ROYAL HOUSE";case 7->"FLOOR CARDS";case 8->"FORCED CHOICE";case 9->"HOUSE TEETH";case 10->"STARFALL";
            case 11->"CARD WALL";case 12->"SPIRAL";case 13->"DEALER CROSS";case 14->"CHIP STORM";case 15->"SPLIT POT";
            case 16->"STACKED DECK";case 17->"BURIED ACE";case 18->"MIRROR DEBT";case 19->"HOUSE EDGE";case 20->"DOUBLE DOWN";
            case 21->"DEAD MAN'S DRAW";case 22->"FALSE TELL";case 23->"SUIT CALL";case 24->"ROYAL FLUSH";case 25->"THE LAST DEAL";
            default->"DEALING";
        };
    }

    private static String phaseTitle(int phase) {
        return switch (phase) {
            case 3 -> "THE LAST HAND";
            case 2 -> "THE TABLE TURNS";
            default -> "THE HOUSE REMEMBERS";
        };
    }

    private static String whisperLine(int phase) {
        return switch (phase) {
            case 3 -> "the felt drinks what the cards leave behind";
            case 2 -> "the dealer has begun to use both hands";
            default -> "the opening deal still pretends to be merciful";
        };
    }

    private static void drawTinyMarkers(GuiGraphics g, int x, int y, int phase) {
        int left = phase == 3 ? 0xFFC86B7C : 0xFFD9C37E;
        int right = phase == 3 ? 0xFF8B1733 : 0xFF7A5FAE;
        for (int i = 0; i < 4; i++) {
            int px = x - 24 + i * 6;
            g.fill(px, y - 3 + Math.abs(1 - (i % 3)), px + 1, y - 1 + Math.abs(1 - (i % 3)), left);
            int qx = x + BAR_W + 22 - i * 6;
            g.fill(qx, y + 13 - Math.abs(1 - (i % 3)), qx + 1, y + 15 - Math.abs(1 - (i % 3)), right);
        }
    }

    /** 0 closed, 1 slit, 2 opening, 3 fully open, 4 half-close. */
    private static int eyeFrame(long now, int phase, boolean reduceMotion) {
        if (reduceMotion) return 3;
        long speed = phase == 3 ? 85L : phase == 2 ? 115L : 145L;
        long step = now / speed;
        int cycle = Math.floorMod((int) step, phase == 3 ? 18 : 24);
        if (phase == 3) {
            if (cycle <= 1) return cycle;
            if (cycle == 2) return 2;
            if (cycle <= 13) return 3;
            if (cycle == 14) return 4;
            if (cycle == 15) return 1;
            if (cycle == 16) return 0;
            return 2;
        }
        if (cycle == 0) return 0;
        if (cycle == 1) return 1;
        if (cycle == 2) return 2;
        if (cycle <= (phase == 2 ? 18 : 20)) return 3;
        if (cycle == 21 || (phase == 2 && cycle == 19)) return 4;
        if (cycle == 22 || (phase == 2 && cycle == 20)) return 1;
        return 2;
    }

    private static void drawSmallCentered(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
        var pose = g.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1f);
        g.drawString(font, text, Math.round(x / scale - font.width(text) / 2f), Math.round(y / scale), color, false);
        pose.popPose();
    }
}
