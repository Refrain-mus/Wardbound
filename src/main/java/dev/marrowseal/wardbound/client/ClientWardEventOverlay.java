package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.WardConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Atmospheric, non-chat feedback for transient ward events. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID, value = Dist.CLIENT)
public final class ClientWardEventOverlay {
    private static final ResourceLocation UNIFORM_FONT = new ResourceLocation("minecraft", "uniform");
    private static final long DEFAULT_DISPLAY_MS = 4300L;
    private static long displayMs = DEFAULT_DISPLAY_MS;
    private static final long MERGE_WINDOW_MS = 900L;
    private static final int MAX_ACTIVE = 4;
    private static final int MAX_QUEUE = 4;

    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final Deque<List<Entry>> QUEUE = new ArrayDeque<>();
    private static long shownAtMs;
    private static long lastIncomingAtMs;

    private ClientWardEventOverlay() {}

    public static void show(String text, int moodId) { show(text, moodId, (int) DEFAULT_DISPLAY_MS); }

    public static void show(String text, int moodId, int durationMs) {
        String incoming = text == null ? "" : text.trim();
        if (incoming.isEmpty()) return;
        int mood = Math.max(0, Math.min(4, moodId));
        int requestedDuration = Math.max(1200, Math.min(6500, durationMs));
        // Long HUD prose used to fade before the typewriter had even reached its tail.
        // Extend locally for readability without changing the packet contract.
        int duration = Math.min(9000, Math.max(requestedDuration, 1700 + incoming.length() * 14));
        long now = System.currentTimeMillis();
        Entry entry = splitEntry(incoming, mood, now, duration);

        if (ACTIVE.isEmpty() || now - shownAtMs >= displayMs) {
            ACTIVE.clear();
            ACTIVE.add(entry);
            displayMs = duration;
            shownAtMs = now;
            lastIncomingAtMs = now;
            return;
        }

        if (canMerge(mood, now) && ACTIVE.size() < MAX_ACTIVE) {
            ACTIVE.add(entry);
            displayMs = Math.max(displayMs, duration);
            lastIncomingAtMs = now;
            // Give the player enough time to read the newly appended line.
            shownAtMs = Math.max(shownAtMs, now - Math.min(900L, displayMs / 4));
            return;
        }

        if (QUEUE.size() >= MAX_QUEUE) QUEUE.removeFirst();
        QUEUE.addLast(new ArrayList<>(List.of(entry)));
        lastIncomingAtMs = now;
    }

    private static boolean canMerge(int incomingMood, long now) {
        if (now - lastIncomingAtMs > MERGE_WINDOW_MS) return false;
        if (incomingMood == 2) return false; // danger gets its own interruption panel
        for (Entry entry : ACTIVE) if (entry.mood == 2) return false;
        return true;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        long age = now - shownAtMs;

        if (!ACTIVE.isEmpty() && age >= displayMs) {
            if (QUEUE.isEmpty()) {
                ACTIVE.clear();
                displayMs = DEFAULT_DISPLAY_MS;
                return;
            }
            ACTIVE.clear();
            ACTIVE.addAll(QUEUE.removeFirst());
            displayMs = ACTIVE.stream().mapToInt(Entry::durationMs).max().orElse((int) DEFAULT_DISPLAY_MS);
            shownAtMs = now;
            age = 0L;
        }
        if (ACTIVE.isEmpty() || age < 0) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int maxWidth = Math.min(470, Math.max(200, sw - 56));

        List<DrawLine> drawLines = new ArrayList<>();
        int widest = 0;
        boolean anyGlitch = false;
        boolean anyDanger = false;
        boolean anyReward = false;

        for (int entryIndex = 0; entryIndex < ACTIVE.size(); entryIndex++) {
            Entry entry = ACTIVE.get(entryIndex);
            long localAge = Math.max(0L, now - entry.addedAt);
            float revealRate = entry.mood == 1 ? 0.080f : entry.mood == 2 ? 0.110f : 0.15f;
            int reveal = WardConfig.accessibilityReduceMotion
                    ? entry.text.length()
                    : Math.min(entry.text.length(), Math.max(1, (int) (localAge * revealRate)));
            String visible = entry.text.substring(0, reveal);
            boolean glitchFrame = !WardConfig.accessibilityReduceFlashing
                    && (entry.mood == 1 || entry.mood == 4) && localAge < 1250L && ((localAge / 85L) % 4L == 1L);
            boolean stutterFrame = !WardConfig.accessibilityReduceFlashing
                    && entry.mood == 2 && localAge < 850L && ((localAge / 70L) % 5L == 2L);
            if (glitchFrame) visible = corrupt(visible, localAge + entryIndex * 17L);
            if (stutterFrame && visible.length() > 4) visible = visible.substring(0, Math.max(1, visible.length() - 2));

            if (ACTIVE.size() > 1 && !entry.speaker.isBlank()) visible = entry.speaker + " // " + visible;
            List<String> wrapped = wrap(font, visible, maxWidth - 24);
            // Never throw the unread tail away. Once a long notice grows beyond three
            // lines the window follows the newest lines, so the entire sentence can pass.
            if (wrapped.size() > 3) wrapped = wrapped.subList(wrapped.size() - 3, wrapped.size());
            for (String line : wrapped) {
                drawLines.add(new DrawLine(line, entry.mood));
                widest = Math.max(widest, font.width(line));
            }
            anyGlitch |= entry.mood == 1 || entry.mood == 4;
            anyDanger |= entry.mood == 2;
            anyReward |= entry.mood == 3;
        }

        if (drawLines.isEmpty()) return;
        if (drawLines.size() > 8) drawLines = drawLines.subList(drawLines.size() - 8, drawLines.size());

        String panelTitle = null;
        if (ACTIVE.size() == 1 && !ACTIVE.get(0).speaker.isBlank()) panelTitle = ACTIVE.get(0).speaker;
        else if (ACTIVE.size() > 1) panelTitle = anyReward ? "WARD // RESOLUTION" : anyGlitch ? "WARD // SIGNAL" : "WARD // EVENTS";
        if (panelTitle != null) widest = Math.max(widest, font.width(panelTitle));

        float fadeWindow = Math.min(700f, Math.max(260f, displayMs * .22f));
        float fade = age > displayMs - fadeWindow ? (displayMs - age) / fadeWindow : 1f;
        int alpha = Math.max(0, Math.min(215, Math.round(215f * fade)));
        int lineH = 11;
        int titleH = panelTitle != null ? 12 : 0;
        int panelW = Math.min(maxWidth + 20, widest + 34);
        int panelH = 9 + titleH + drawLines.size() * lineH;
        int x = sw / 2 - panelW / 2;

        // Dynamic vertical placement: short one-line notices sit low near the hotbar,
        // while tall/stacked panels climb only as much as needed to stay fully on-screen.
        int bottomSafe = Math.max(46, Math.min(74, sh / 8));
        int preferredBottomGap = drawLines.size() <= 2 ? bottomSafe + 10
                : drawLines.size() <= 4 ? bottomSafe + 24 : bottomSafe + 42;
        int y = sh - preferredBottomGap - panelH;
        int minY = 24;
        int maxY = Math.max(minY, sh - panelH - bottomSafe);
        y = Math.max(minY, Math.min(maxY, y));

        boolean jitter = !WardConfig.accessibilityReduceMotion && anyDanger && age < 700L && ((age / 80L) % 4L == 1L);
        if (jitter) x += ((age / 80L) % 2L == 0L) ? 1 : -1;

        int accent = anyDanger ? 0x7B252E : anyGlitch ? 0x633B8E : anyReward ? 0x786125 : 0x255D61;
        g.fill(x + 5, y, x + panelW - 5, y + panelH, (Math.min(120, alpha) << 24) | 0x05090D);
        g.fill(x, y + 3, x + 13, y + 4, (alpha << 24) | accent);
        g.fill(x + panelW - 13, y + 3, x + panelW, y + 4, (alpha << 24) | accent);
        g.fill(x + 3, y, x + 4, y + 8, (alpha << 24) | accent);
        g.fill(x + panelW - 4, y, x + panelW - 3, y + 8, (alpha << 24) | accent);

        int drawY = y + 5;
        if (panelTitle != null) {
            Component c = Component.literal(panelTitle).withStyle(style -> style.withFont(UNIFORM_FONT).withBold(true));
            g.drawCenteredString(font, c, sw / 2, drawY, (alpha << 24) | 0xA7A0AF);
            drawY += titleH;
        }

        PoseStack pose = g.pose();
        pose.pushPose();
        float scale = 1.01f;
        pose.scale(scale, scale, 1f);
        for (DrawLine dl : drawLines) {
            int color = moodColor(dl.mood);
            Component c = Component.literal(dl.text).withStyle(style -> style.withFont(UNIFORM_FONT));
            g.drawCenteredString(font, c,
                    Math.round((sw / 2f) / scale), Math.round(drawY / scale), (alpha << 24) | color);
            drawY += lineH;
        }
        pose.popPose();
    }

    private static int moodColor(int mood) {
        return switch (mood) {
            case 1 -> 0xC17CFF;
            case 2 -> 0xFF7777;
            case 3 -> 0xE7C96C;
            case 4 -> 0xB47CFF;
            default -> 0x8FE6DD;
        };
    }

    private static String corrupt(String input, long age) {
        if (input.length() < 5) return input;
        char[] chars = input.toCharArray();
        int seed = (int) (age / 85L);
        for (int i = 2; i < chars.length; i += 7 + Math.abs(seed % 3)) {
            if (chars[i] != ' ') chars[i] = (seed + i) % 2 == 0 ? '/' : '_';
        }
        return new String(chars);
    }

    private static Entry splitEntry(String incoming, int mood, long now, int duration) {
        int split = incoming.indexOf("//");
        if (split > 0 && split <= 32 && split + 2 < incoming.length()) {
            String speaker = incoming.substring(0, split).trim();
            String body = incoming.substring(split + 2).trim();
            if (!speaker.isBlank() && !body.isBlank()) return new Entry(speaker, body, mood, now, duration);
        }
        return new Entry("", incoming, mood, now, duration);
    }

    private record Entry(String speaker, String text, int mood, long addedAt, int durationMs) {}
    private record DrawLine(String text, int mood) {}

    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
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
