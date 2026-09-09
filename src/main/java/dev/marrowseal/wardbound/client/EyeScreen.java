package dev.marrowseal.wardbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.EyeTravelPacket;

/**
 * What The Savant opens.
 *
 * <p>Not a minigame and deliberately not built on {@link BaseMinigameScreen}:
 * every lock in this mod is framed as a machine you are working on, with a
 * riveted panel and a header telling you what you are up against. This is not a
 * machine and it is not on your side. It has no frame, no header, no hint line
 * and nothing to beat - a black field, a question, and three answers.
 *
 * <p>Everything on it moves slightly and nothing moves in time with anything
 * else. Each letter of the question drifts on its own period and its own phase,
 * so the word never quite settles; the answers breathe; the ring around the
 * whole thing turns at a rate that does not divide evenly into any of it. The
 * effect being aimed at is not motion, it is the absence of stillness.
 */
@OnlyIn(Dist.CLIENT)
public class EyeScreen extends Screen {

    private static final String QUESTION = "WHERE?";
    private static final String[] PLACES = {"THE OVERWORLD", "THE NETHER", "THE END"};
    /** Kept in step with the item's own cycle: overworld, nether, end. */
    private static final int[] TINT = {0xFF6E8F5E, 0xFF8E4A32, 0xFF6A5E86};

    private float time;
    private int hovered = -1;
    private final float[] swell = new float[PLACES.length];
    private boolean sent;
    /** So the hover sound speaks once per arrival, not once per frame. */
    private int lastSpoken = -1;
    private final Painter p = new Painter();

    public EyeScreen() {
        super(Component.literal("The Savant"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        time = 0f;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(WardSounds.EYE_OPEN.get(), 0.84f, 0.86f));
    }

    private int optionY(int i) {
        return height / 2 + 18 + i * 30;
    }

    private int optionW() {
        return Math.min(260, width - 60);
    }

    private int optionX() {
        return (width - optionW()) / 2;
    }

    private int optionAt(double mx, double my) {
        for (int i = 0; i < PLACES.length; i++) {
            int y = optionY(i);
            if (mx >= optionX() && mx <= optionX() + optionW() && my >= y - 12 && my <= y + 12) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = optionAt(mx, my);
        if (i >= 0 && !sent) {
            sent = true;
            Wardbound.CHANNEL.sendToServer(new EyeTravelPacket(i));
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(WardSounds.EYE_CHOOSE.get(), 1.0f, 0.85f));
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!WardConfig.accessibilityReduceMotion) {
            time += partialTick / 20f * WardConfig.accessibilityGuiAnimationIntensity;
        }
        hovered = optionAt(mouseX, mouseY);
        if (hovered != lastSpoken) {
            lastSpoken = hovered;
            if (hovered >= 0) {
                // A slightly different pitch per destination, so the three
                // answers are three voices rather than one sound played thrice.
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                        WardSounds.EYE_HOVER.get(), 0.82f + hovered * 0.13f, 0.7f));
            }
        }

        for (int i = 0; i < PLACES.length; i++) {
            float want = i == hovered ? 1f : 0f;
            if (WardConfig.accessibilityReduceMotion) swell[i] = want;
            else swell[i] += (want - swell[i]) * Math.min(1f,
                    partialTick * 0.35f * WardConfig.accessibilityGuiAnimationIntensity);
        }

        // Painter batches its own quads and is bound to the graphics for the
        // frame, exactly as the lock screens do it. Anything drawn straight
        // through GuiGraphics has to come after a flush or it lands underneath.
        p.bind(g);

        // ---- the dark. Not a panel: the whole screen goes, because the point is
        // that you are not standing anywhere while this is open.
        p.rect(0, 0, width, height, 0xF2000000);
        for (int i = 0; i < 5; i++) {
            float ph = time * (0.09f + i * 0.031f) + i * 1.7f;
            int cx = Math.round(width * (0.5f + 0.42f * Mth.sin(ph)));
            int cy = Math.round(height * (0.5f + 0.38f * Mth.cos(ph * 0.77f + i)));
            for (int r = 7; r > 0; r--) {
                p.disc(cx, cy, r * 26, Painter.withAlpha(0x101822, 0x0A));
            }
        }

        // ---- a ring around all of it, turning at a rate that fits nothing else
        int rx = width / 2, ry = height / 2 - 6, rr = Math.min(width, height) / 2 - 14;
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(time * 7.3f + i * 6);
            float ux = (float) Math.cos(a), uy = (float) Math.sin(a);
            int len = 5 + (i % 5);
            p.line(rx + ux * rr, ry + uy * rr, rx + ux * (rr - len), ry + uy * (rr - len), 1,
                    Painter.withAlpha(0x2E4A3E, 0x40 + (i % 3) * 0x18));
        }
        p.ring(rx, ry, rr + 2, rr, Painter.withAlpha(0x1B2A24, 0x88));
        p.flush();

        // ---- the question, letter by letter, none of them agreeing
        int qw = font.width(QUESTION) * 3;
        int qx = (width - qw) / 2;
        for (int i = 0; i < QUESTION.length(); i++) {
            String ch = String.valueOf(QUESTION.charAt(i));
            float ph = time * (1.9f + i * 0.43f) + i * 2.1f;
            int dx = WardConfig.accessibilityReduceMotion ? 0 : Math.round(Mth.sin(ph) * 2.2f);
            int dy = WardConfig.accessibilityReduceMotion ? 0 : Math.round(Mth.cos(ph * 1.31f + i) * 2.6f);
            int alpha = WardConfig.accessibilityReduceFlashing ? 0xE8
                    : 0xB0 + Math.round((Mth.sin(ph * 0.7f) + 1f) * 0x27);

            g.pose().pushPose();
            g.pose().translate(qx + i * font.width(QUESTION) * 3f / QUESTION.length() + dx,
                    height / 2f - 62 + dy, 0);
            g.pose().scale(3f, 3f, 1f);
            // a smeared copy behind it, offset the other way, so the letter never
            // reads as being in one place
            g.drawString(font, ch, 1, 1, (0x22 << 24) | 0x7FD4C0, false);
            g.drawString(font, ch, 0, 0, (alpha << 24) | 0xC9E3D6, false);
            g.pose().popPose();
        }

        // ---- the answers
        for (int i = 0; i < PLACES.length; i++) {
            drawOption(g, p, i);
        }

        p.flush();
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawOption(GuiGraphics g, Painter p, int i) {
        int x = optionX(), y = optionY(i), w = optionW();
        float s = swell[i];
        // grows toward the cursor, and the growth is asymmetric so it reads as
        // leaning rather than scaling
        int grow = Math.round(s * 7f);
        int lean = Math.round(s * 4f);

        int tint = TINT[i];
        p.rect(x - grow + lean, y - 11 - grow / 2, x + w + grow + lean, y + 11 + grow / 2,
                Painter.withAlpha(0x000000, 0x99));
        p.rect(x - grow + lean, y - 11 - grow / 2, x + w + grow + lean, y - 10 - grow / 2,
                Painter.withAlpha(tint, 0x66 + Math.round(s * 0x99)));
        p.rect(x - grow + lean, y + 10 + grow / 2, x + w + grow + lean, y + 11 + grow / 2,
                Painter.withAlpha(tint, 0x66 + Math.round(s * 0x99)));

        if (s > 0.01f) {
            // a wash of the destination's colour, and hairs reaching in from the
            // edges of it
            p.rect(x - grow + lean, y - 10 - grow / 2, x + w + grow + lean, y + 10 + grow / 2,
                    Painter.withAlpha(tint, Math.round(s * 0x22)));
            for (int k = 0; k < 9; k++) {
                float ph = time * 3.1f + k * 1.3f + i;
                int hx = x + 12 + k * (w - 24) / 8;
                int len = WardConfig.accessibilityReduceMotion ? Math.round(s * 6f)
                        : Math.round((1.2f + Mth.sin(ph)) * s * 5f);
                p.rect(hx, y - 11 - grow / 2 - len, hx + 1, y - 11 - grow / 2,
                        Painter.withAlpha(tint, 0x88));
                p.rect(hx, y + 11 + grow / 2, hx + 1, y + 11 + grow / 2 + len,
                        Painter.withAlpha(tint, 0x88));
            }
        }
        p.flush();

        int label = WardConfig.accessibilityHighContrast
                ? (i == hovered ? 0xFFFFFFFF : 0xFFD8D8D8)
                : (i == hovered ? 0xFFE8F2E6 : 0xFF7F8F84);
        int shiver = i == hovered && !WardConfig.accessibilityReduceMotion
                ? Math.round(Mth.sin(time * 17f)) : 0;
        g.drawCenteredString(font, PLACES[i], x + w / 2 + lean + shiver, y - 4, label);
    }

    @Override
    public void onClose() {
        // Only when you backed out. Choosing already has its own sound and the
        // two would land on top of each other.
        if (!sent) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(WardSounds.EYE_CLOSE.get(), 1.0f, 0.8f));
        }
        Minecraft.getInstance().setScreen(null);
    }
}
