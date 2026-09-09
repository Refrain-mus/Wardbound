package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;

/** Savant dialogue drawn above the hotbar as an eldritch subtitle, never chat. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class ClientSavantOverlay {
    private static final ResourceLocation UNIFORM_FONT = new ResourceLocation("minecraft", "uniform");
    private static final long DEFAULT_DISPLAY_MS = 5600L;
    private static long displayMs = DEFAULT_DISPLAY_MS;
    private static String line = "";
    private static String speaker = "THE SAVANT // OBSERVING";
    private static long masterUntil;
    private static long shownAtMs;
    private static final Deque<QueuedMasterLine> MASTER_QUEUE = new ArrayDeque<>();
    private record QueuedMasterLine(String speaker, String line, int durationMs) {}

    private ClientSavantOverlay() {}

    public static void show(String text) {
        if (System.currentTimeMillis() < masterUntil) return;
        speaker = "THE SAVANT // OBSERVING";
        line = text == null ? "" : text.trim();
        displayMs = DEFAULT_DISPLAY_MS;
        shownAtMs = System.currentTimeMillis();
    }

    public static void showMaster(String name, String text) {
        showMaster(name, text, (int) DEFAULT_DISPLAY_MS);
    }

    public static void showMaster(String name, String text, int durationMs) {
        String nextSpeaker = name == null ? "THE SAVANT // OBSERVING" : name;
        String nextLine = text == null ? "" : text.trim();
        int nextDuration = Math.max(1800, Math.min(9000, durationMs));
        long now = System.currentTimeMillis();
        if (now < masterUntil && !line.isEmpty()) {
            if (MASTER_QUEUE.size() < 6) MASTER_QUEUE.addLast(new QueuedMasterLine(nextSpeaker, nextLine, nextDuration));
            return;
        }
        activateMaster(nextSpeaker, nextLine, nextDuration, now);
    }

    private static void activateMaster(String nextSpeaker, String nextLine, int durationMs, long now) {
        speaker = nextSpeaker;
        line = nextLine;
        displayMs = Math.max(1800L, Math.min(9000L, durationMs));
        shownAtMs = now;
        masterUntil = now + displayMs;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || line.isEmpty()) return;
        long now = System.currentTimeMillis();
        long age = now - shownAtMs;
        if (age < 0) return;
        if (age >= displayMs) {
            QueuedMasterLine queued = MASTER_QUEUE.pollFirst();
            if (queued == null) { line = ""; return; }
            activateMaster(queued.speaker(), queued.line(), queued.durationMs(), now);
            age = 0;
        }

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        boolean gambler = speaker != null && speaker.toLowerCase().contains("pale gambler");
        boolean curator = speaker != null && speaker.toLowerCase().contains("ashen curator");
        boolean notary = speaker != null && speaker.toLowerCase().contains("mourning notary");
        boolean silas = speaker != null && speaker.toLowerCase().contains("silas veyr");
        boolean savantVisible = gambler || curator || notary || silas || hasSavant(mc);
        if (!savantVisible) return;

        float fade = age < 220L ? age / 220f : age > displayMs - 300L ? Math.max(0f, (displayMs - age) / 300f) : 1f;
        List<String> lines = wrap(font, line, gambler ? Math.min(360, screenW - 88) : curator ? Math.min(340, screenW - 84) : notary ? Math.min(350, screenW - 84) : Math.min(300, screenW - 76));
        if (gambler) renderGambler(g, font, screenW, screenH, lines, age, fade);
        else if (curator) renderCurator(g, font, screenW, screenH, lines, age, fade);
        else if (notary) renderNotary(g, font, screenW, screenH, lines, age, fade);
        else renderSavant(g, font, screenW, screenH, lines, age, fade);
    }

    private static void renderGambler(GuiGraphics g, Font font, int screenW, int screenH, List<String> lines, long age, float fade) {
        int lineH = 12;
        int textWidth = 0;
        for (String s : lines) textWidth = Math.max(textWidth, font.width(s));
        int panelW = Math.min(screenW - 44, Math.max(260, textWidth + 90));
        int contentH = 30 + lineH * lines.size();
        int left = screenW / 2 - panelW / 2;
        int top = screenH - 110 - Math.max(0, lines.size() - 1) * 6;
        int right = left + panelW;
        int bottom = top + contentH;

        int bg = ((int) (210 * fade) << 24) | 0x06060B;
        int haze = ((int) (130 * fade) << 24) | 0x1A0C1E;
        int gold = ((int) (255 * fade) << 24) | 0xD1B16D;
        int bone = ((int) (255 * fade) << 24) | 0xF4DFC0;
        int violet = ((int) (215 * fade) << 24) | 0x8D62C4;
        int crimson = ((int) (200 * fade) << 24) | 0xB74B63;
        int text = 0xFFF0E6D8;
        int text2 = 0xFFD8CAE8;
        int blink = eyeFrame(System.currentTimeMillis());

        g.fill(left - 8, top - 12, right + 8, bottom + 10, haze);
        g.fill(left, top, right, bottom, bg);
        g.fill(left + 10, top + 1, right - 10, top + 2, gold);
        g.fill(left + 10, bottom - 2, right - 10, bottom - 1, violet);
        g.fill(left + 1, top + 8, left + 2, bottom - 8, gold);
        g.fill(right - 2, top + 8, right - 1, bottom - 8, crimson);
        drawHook(g, left + 10, top + 6, gold);
        drawHook(g, right - 10, top + 6, gold);
        drawHook(g, left + 10, bottom - 6, violet);
        drawHook(g, right - 10, bottom - 6, crimson);
        // upper card rail
        g.fill(screenW / 2 - 44, top - 7, screenW / 2 + 44, top - 6, gold);
        g.fill(screenW / 2 - 28, top - 11, screenW / 2 + 28, top - 10, bone);
        drawEyeGlyph(g, screenW / 2, top - 12, blink, bone, crimson, violet);

        PoseStack pose = g.pose();
        pose.pushPose();
        float titleScale = 0.86f;
        pose.scale(titleScale, titleScale, 1f);
        String title = "THE PALE GAMBLER // THE LAST TABLE SPEAKS";
        g.drawString(font, title, Math.round(screenW / 2f / titleScale - font.width(title) / 2f), Math.round((top + 5) / titleScale), 0xFFF6E7C4, false);
        pose.popPose();

        pose.pushPose();
        float dividerScale = 0.72f;
        pose.scale(dividerScale, dividerScale, 1f);
        String subtitle = "the house does not converse. it writes outcomes aloud.";
        g.drawString(font, subtitle, Math.round(screenW / 2f / dividerScale - font.width(subtitle) / 2f), Math.round((top + 16) / dividerScale), 0xFFD8B989, false);
        pose.popPose();

        int drawY = top + 30;
        for (int i = 0; i < lines.size(); i++) {
            String draw = lines.get(i);
            g.drawCenteredString(font, Component.literal(draw).withStyle(s -> s.withFont(UNIFORM_FONT).withItalic(true)), screenW / 2, drawY, i == lines.size() - 1 ? text : text2);
            drawY += lineH;
        }
        int scan = (int) ((age / 85L) % Math.max(1, panelW - 52));
        g.fill(left + 24 + scan, bottom - 5, Math.min(right - 20, left + 30 + scan), bottom - 4, bone);
    }

    private static void renderCurator(GuiGraphics g, Font font, int screenW, int screenH, List<String> lines, long age, float fade) {
        int lineH = 12;
        int textWidth = 0;
        for (String s : lines) textWidth = Math.max(textWidth, font.width(s));
        int panelW = Math.min(screenW - 48, Math.max(270, textWidth + 92));
        int contentH = 30 + lineH * lines.size();
        int left = screenW / 2 - panelW / 2;
        int top = screenH - 112 - Math.max(0, lines.size() - 1) * 6;
        int right = left + panelW;
        int bottom = top + contentH;

        int bg = ((int) (214 * fade) << 24) | 0x0B0907;
        int haze = ((int) (118 * fade) << 24) | 0x23160F;
        int ivory = ((int) (255 * fade) << 24) | 0xE8D6B8;
        int sage = ((int) (220 * fade) << 24) | 0x9FBEA2;
        int ember = ((int) (225 * fade) << 24) | 0xC56D45;
        int gold = ((int) (235 * fade) << 24) | 0xB99A64;
        int text = 0xFFF1E5CF;
        int text2 = 0xFFD9CCB7;

        g.fill(left - 8, top - 12, right + 8, bottom + 10, haze);
        g.fill(left, top, right, bottom, bg);
        g.fill(left + 10, top + 1, right - 10, top + 2, ivory);
        g.fill(left + 10, bottom - 2, right - 10, bottom - 1, ember);
        g.fill(left + 1, top + 7, left + 2, bottom - 7, gold);
        g.fill(right - 2, top + 7, right - 1, bottom - 7, sage);
        drawHook(g, left + 10, top + 6, ivory);
        drawHook(g, right - 10, top + 6, sage);
        drawHook(g, left + 10, bottom - 6, ember);
        drawHook(g, right - 10, bottom - 6, gold);
        g.fill(screenW / 2 - 40, top - 7, screenW / 2 + 40, top - 6, gold);
        g.fill(screenW / 2 - 26, top - 11, screenW / 2 + 26, top - 10, ivory);
        drawArchiveGlyph(g, screenW / 2, top - 12, ivory, ember, sage);

        PoseStack pose = g.pose();
        pose.pushPose();
        float titleScale = 0.84f;
        pose.scale(titleScale, titleScale, 1f);
        String title = "THE ASHEN CURATOR // THE ARCHIVE SPEAKS";
        g.drawString(font, title, Math.round(screenW / 2f / titleScale - font.width(title) / 2f), Math.round((top + 5) / titleScale), 0xFFF2E3C9, false);
        pose.popPose();

        pose.pushPose();
        float subtitleScale = 0.72f;
        pose.scale(subtitleScale, subtitleScale, 1f);
        String subtitle = "nothing is forgotten. everything is indexed.";
        g.drawString(font, subtitle, Math.round(screenW / 2f / subtitleScale - font.width(subtitle) / 2f), Math.round((top + 16) / subtitleScale), 0xFFD0BA98, false);
        pose.popPose();

        int drawY = top + 30;
        for (int i = 0; i < lines.size(); i++) {
            String draw = lines.get(i);
            g.drawCenteredString(font, Component.literal(draw).withStyle(s -> s.withFont(UNIFORM_FONT).withItalic(true)), screenW / 2, drawY, i == lines.size() - 1 ? text : text2);
            drawY += lineH;
        }
        int scan = (int) ((age / 92L) % Math.max(1, panelW - 52));
        g.fill(left + 24 + scan, bottom - 5, Math.min(right - 20, left + 30 + scan), bottom - 4, ivory);
    }

    private static void renderNotary(GuiGraphics g, Font font, int screenW, int screenH, List<String> lines, long age, float fade) {
        int lineH=12,textWidth=0;for(String s:lines)textWidth=Math.max(textWidth,font.width(s));int panelW=Math.min(screenW-48,Math.max(280,textWidth+94));int contentH=30+lineH*lines.size();int left=screenW/2-panelW/2,top=screenH-112-Math.max(0,lines.size()-1)*6,right=left+panelW,bottom=top+contentH;
        int bg=((int)(218*fade)<<24)|0x08070B,haze=((int)(118*fade)<<24)|0x170D14,ivory=((int)(255*fade)<<24)|0xE7DCC8,red=((int)(225*fade)<<24)|0x87384B,gold=((int)(225*fade)<<24)|0xAC8E59,violet=((int)(205*fade)<<24)|0x7A668A;
        g.fill(left-8,top-12,right+8,bottom+10,haze);g.fill(left,top,right,bottom,bg);g.fill(left+10,top+1,right-10,top+2,ivory);g.fill(left+10,bottom-2,right-10,bottom-1,red);g.fill(left+1,top+8,left+2,bottom-8,gold);g.fill(right-2,top+8,right-1,bottom-8,violet);drawHook(g,left+10,top+6,ivory);drawHook(g,right-10,top+6,gold);drawHook(g,left+10,bottom-6,red);drawHook(g,right-10,bottom-6,violet);drawNotaryGlyph(g,screenW/2,top-12,ivory,red,gold);
        PoseStack pose=g.pose();pose.pushPose();float ts=.83f;pose.scale(ts,ts,1);String title="THE MOURNING NOTARY // THE RECORD SPEAKS";g.drawString(font,title,Math.round(screenW/2f/ts-font.width(title)/2f),Math.round((top+5)/ts),0xFFF0E6D8,false);pose.popPose();
        pose.pushPose();float ss=.72f;pose.scale(ss,ss,1);String subtitle="testimony becomes law when the witness survives it.";g.drawString(font,subtitle,Math.round(screenW/2f/ss-font.width(subtitle)/2f),Math.round((top+16)/ss),0xFFC6B7A2,false);pose.popPose();
        int y=top+30;for(int i=0;i<lines.size();i++){g.drawCenteredString(font,Component.literal(lines.get(i)).withStyle(s->s.withFont(UNIFORM_FONT).withItalic(true)),screenW/2,y,i==lines.size()-1?0xFFE8DED0:0xFFD3C6B8);y+=lineH;}int scan=(int)((age/88L)%Math.max(1,panelW-52));g.fill(left+24+scan,bottom-5,Math.min(right-20,left+30+scan),bottom-4,ivory);
    }

    private static void renderSavant(GuiGraphics g, Font font, int screenW, int screenH, List<String> lines, long age, float fade) {
        int lineH = 12;
        int textWidth = 0;
        for (String s : lines) textWidth = Math.max(textWidth, font.width(s));
        int panelW = Math.min(screenW - 56, Math.max(190, textWidth + 72));
        int contentH = 24 + lineH * lines.size();
        int y = screenH - 96 - Math.max(0, lines.size() - 1) * 6;
        int left = screenW / 2 - panelW / 2;
        int right = left + panelW;
        int panelAlpha = Math.max(0, Math.min(210, Math.round(210 * fade)));
        int cyanAlpha = Math.max(0, Math.min(210, Math.round(210 * fade)));
        int violetAlpha = Math.max(0, Math.min(165, Math.round(165 * fade)));
        boolean impact = isImpactLine(line);
        int jitter = impact && age < 650L && ((age / 80L) % 4L == 1L) ? (((age / 80L) & 1L) == 0L ? 1 : -1) : 0;
        left += jitter;
        right += jitter;

        g.fill(left + 8, y - 3, right - 8, y + contentH + 4, panelAlpha << 24 | 0x040A0E);
        g.fill(left + 1, y, left + 18, y + 1, cyanAlpha << 24 | 0x43D8D0);
        g.fill(right - 18, y, right - 1, y + 1, cyanAlpha << 24 | 0x43D8D0);
        g.fill(left, y, left + 1, y + 9, cyanAlpha << 24 | 0x43D8D0);
        g.fill(right - 1, y, right, y + 9, cyanAlpha << 24 | 0x43D8D0);
        g.fill(left + 4, y + contentH + 2, left + 28, y + contentH + 3, violetAlpha << 24 | 0x7651A8);
        g.fill(right - 28, y + contentH + 2, right - 4, y + contentH + 3, violetAlpha << 24 | 0x7651A8);
        g.fill(screenW / 2 - 5, y - 2, screenW / 2 + 5, y - 1, violetAlpha << 24 | 0x7651A8);
        int scan = (int) ((age / 90L) % Math.max(1, panelW - 36));
        g.fill(left + 18 + scan, y + contentH + 1, Math.min(right - 12, left + 22 + scan), y + contentH + 2, Math.max(0, cyanAlpha / 2) << 24 | 0x43D8D0);

        PoseStack pose = g.pose();
        pose.pushPose();
        float titleScale = 0.72f;
        pose.scale(titleScale, titleScale, 1f);
        Component title = Component.literal(speaker).withStyle(style -> style.withFont(UNIFORM_FONT).withColor(ChatFormatting.AQUA).withBold(true));
        g.drawCenteredString(font, title, Math.round((screenW / 2f + jitter) / titleScale), Math.round((y + 1) / titleScale), 0xE0FFF9);
        pose.popPose();

        float textScale = 1.06f;
        pose.pushPose();
        pose.scale(textScale, textScale, 1f);
        int drawY = y + 14;
        for (String s : lines) {
            String draw = impact && age < 720L && ((age / 95L) % 5L == 2L) ? microStutter(s) : s;
            Component text = Component.literal(draw).withStyle(style -> style.withFont(UNIFORM_FONT).withColor(ChatFormatting.LIGHT_PURPLE).withItalic(true));
            g.drawCenteredString(font, text, Math.round((screenW / 2f + jitter) / textScale), Math.round(drawY / textScale), 0xE3D3FF);
            drawY += lineH;
        }
        pose.popPose();
    }

    private static int eyeFrame(long now) {
        int cycle = Math.floorMod((int) (now / 120L), 20);
        if (cycle == 0) return 0;
        if (cycle == 1) return 1;
        if (cycle <= 13) return 2;
        if (cycle == 14) return 1;
        if (cycle == 15) return 0;
        return 2;
    }

    private static void drawHook(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 1, cy - 2, cx + 1, cy + 2, color);
        g.fill(cx - 2, cy - 1, cx + 2, cy + 1, color);
    }

    /** Curator header sigil: an opened folio with a burning index spine. */
    private static void drawArchiveGlyph(GuiGraphics g, int cx, int cy, int ivory, int ember, int sage) {
        // Two page leaves, deliberately asymmetric so this never reads like the Gambler eye glyph.
        g.fill(cx - 12, cy - 2, cx - 2, cy - 1, ivory);
        g.fill(cx + 2, cy - 2, cx + 12, cy - 1, ivory);
        g.fill(cx - 11, cy - 1, cx - 2, cy + 3, sage);
        g.fill(cx + 2, cy - 1, cx + 11, cy + 3, sage);
        g.fill(cx - 1, cy - 4, cx + 1, cy + 5, ember);
        g.fill(cx - 8, cy + 3, cx - 2, cy + 4, ivory);
        g.fill(cx + 2, cy + 3, cx + 8, cy + 4, ivory);
        g.fill(cx - 4, cy - 5, cx + 4, cy - 4, ember);
        g.fill(cx - 2, cy - 6, cx + 2, cy - 5, ember);
    }

    /** Notary header sigil: a suspended funeral seal and signature bar. */
    private static void drawNotaryGlyph(GuiGraphics g, int cx, int cy, int ivory, int red, int gold) {
        // Hanging chain / bell-stroke.
        g.fill(cx, cy - 7, cx + 1, cy - 2, ivory);
        g.fill(cx - 5, cy - 3, cx + 6, cy - 2, gold);
        g.fill(cx - 7, cy - 2, cx + 8, cy + 2, red);
        g.fill(cx - 5, cy + 2, cx + 6, cy + 4, red);
        g.fill(cx - 2, cy + 4, cx + 3, cy + 5, gold);
        // Distinct notarial signature rail under the seal.
        g.fill(cx - 13, cy + 7, cx + 14, cy + 8, ivory);
        g.fill(cx - 9, cy + 9, cx - 2, cy + 10, gold);
        g.fill(cx + 2, cy + 9, cx + 9, cy + 10, gold);
    }

    private static void drawEyeGlyph(GuiGraphics g, int cx, int cy, int frame, int outline, int iris, int side) {
        if (frame == 0) {
            g.fill(cx - 8, cy, cx + 8, cy + 1, outline);
            return;
        }
        g.fill(cx - 10, cy, cx + 10, cy + 1, outline);
        g.fill(cx - 8, cy - 1, cx + 8, cy, outline);
        g.fill(cx - 8, cy + 1, cx + 8, cy + 2, outline);
        if (frame >= 2) {
            g.fill(cx - 3, cy - 1, cx + 3, cy + 2, iris);
            g.fill(cx - 1, cy, cx + 1, cy + 1, 0xFF10070C);
            g.fill(cx - 13, cy, cx - 11, cy + 1, side);
            g.fill(cx + 11, cy, cx + 13, cy + 1, side);
        }
    }

    private static boolean isImpactLine(String s) {
        String x = s.toLowerCase();
        return x.contains("retina") || x.contains("club") || x.contains("mace") || x.contains("my face")
                || x.contains("what are you doing") || x.contains("concussion") || x.contains("strike it")
                || x.contains("you asked for that") || x.contains("overcorrected") || x.contains("ceased")
                || x.contains("administrative error") || x.contains("formal objection")
                || x.contains("proportional correction") || x.contains("pain improves")
                || x.contains("warning version") || x.contains("redirect force")
                || x.contains("inconvenienced") || x.contains("pedagogical")
                || x.contains("hand slips");
    }

    private static String microStutter(String s) {
        if (s.length() < 8) return s;
        int cut = Math.min(s.length(), Math.max(4, s.length() / 3));
        return s.substring(0, cut) + "- " + s.substring(Math.max(0, cut - 1));
    }

    private static boolean hasSavant(Minecraft mc) {
        if (mc.player.getMainHandItem().is(WardItems.THE_SAVANT.get()) || mc.player.getOffhandItem().is(WardItems.THE_SAVANT.get())) return true;
        for (var stack : mc.player.getInventory().items) if (stack.is(WardItems.THE_SAVANT.get())) return true;
        return false;
    }

    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (font.width(candidate) <= maxWidth || current.length() == 0) {
                current.setLength(0);
                current.append(candidate);
            } else {
                out.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) out.add(current.toString());
        if (out.isEmpty()) out.add(text);
        return out;
    }
}
