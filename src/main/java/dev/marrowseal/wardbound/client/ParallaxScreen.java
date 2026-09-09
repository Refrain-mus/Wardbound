package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;
import dev.marrowseal.wardbound.WardConfig;

import java.util.Random;

/**
 * The Parallax Seal.
 *
 * <p>Four pieces of one impossible mark sit at different apparent depths. Mouse
 * movement shifts each depth by a different amount. There is exactly one focus
 * at which all four pieces occupy the same place. The player finds that focus
 * visually and commits with a click.
 *
 * <p>No fragment disappears and no target is randomised while the player is
 * solving it. Difficulty only tightens how closely the pieces must coincide and
 * adds one round; it never makes the underlying information unavailable.
 */
@OnlyIn(Dist.CLIENT)
public final class ParallaxScreen extends BaseMinigameScreen {

    private final boolean inverseDepth;
    private final boolean twistedPlate;
    private final boolean ghostFocus;
    private final boolean layeredField;
    private final boolean breathingDepth;
    private final int rounds;

    private float targetX;
    private float targetY;
    private float pulse;
    private float qualitySum;
    private int qualityHits;
    private int lastMouseX;
    private int lastMouseY;

    private static final float[] DEPTH = {0.72f, 1.05f, 1.42f, 1.84f};

    public ParallaxScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.parallax"), msg);
        boolean[] q = pickVariants(3, 883);
        inverseDepth = q[0];
        twistedPlate = q[1];
        ghostFocus = q[2];
        layeredField = expertVariant(897, 2, 72, 0.58f);
        breathingDepth = expertVariant(898, 4, 96, 0.26f);
        if (inverseDepth) modifierLabels.add("inverted depth");
        if (twistedPlate) modifierLabels.add("twisted plate");
        if (ghostFocus) modifierLabels.add("false focus");
        if (layeredField) modifierLabels.add("deep parallax");
        if (breathingDepth) modifierLabels.add("breathing depth");

        rounds = Mth.clamp(3 + (value > 62 || difficulty > 1.32f ? 1 : 0), 3, 4);
        makeRound();
    }

    @Override
    protected Theme createTheme() {
        return Theme.parallax();
    }

    @Override
    protected int panelHeight() {
        return 238;
    }

    @Override
    protected float timeBudget() {
        return super.timeBudget() * 1.04f;
    }

    @Override
    protected boolean allows(Anomaly a) {
        // This game is entirely about reading four fragments at once. Occluding
        // them converts a spatial puzzle into guessing, so the two visual-loss
        // anomalies are excluded here.
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING;
    }

    private void makeRound() {
        Random r = new Random(seed ^ 0x504152414C4C4158L ^ (progress * 104729L));
        targetX = -0.58f + r.nextFloat() * 1.16f;
        targetY = -0.50f + r.nextFloat() * 1.00f;
        pulse = 0f;
    }

    private float normX(double mx) {
        float half = Math.max(70f, panelWidth() * 0.38f);
        return Mth.clamp((float) ((mx - centerX()) / half), -1f, 1f);
    }

    private float normY(double my) {
        float half = Math.max(54f, (contentBottom() - contentTop()) * 0.38f);
        return Mth.clamp((float) ((my - contentCenterY()) / half), -1f, 1f);
    }

    private float dx(double mx) { return normX(mx) - targetX; }
    private float dy(double my) { return normY(my) - targetY; }

    private float depth(int i) {
        float d = DEPTH[i];
        if (breathingDepth && !WardConfig.accessibilityReduceMotion) {
            d *= 0.91f + 0.09f * Mth.sin(pulse * 0.72f + i * 1.37f);
        }
        if (inverseDepth && i == 2) d = -d * 0.74f;
        return d;
    }

    private float quality(double mx, double my) {
        float ex = Math.abs(dx(mx));
        float ey = Math.abs(dy(my));
        float err = (float) Math.sqrt(ex * ex + ey * ey);
        return Mth.clamp(1f - err / 0.43f, 0f, 1f);
    }

    private float requiredQuality() {
        float base = 0.835f + Mth.clamp((difficulty - 1f) * 0.05f, -0.03f, 0.08f);
        // Larger input window means more forgiveness, as it does everywhere else.
        return Mth.clamp(base / (float) Math.sqrt(gameInputWindowScale), 0.76f, 0.93f);
    }

    @Override
    protected void step(float dt) {
        pulse += dt;
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        int cx = centerX(), cy = contentCenterY() - 2;
        if (mx < cx - 100 || mx > cx + 100 || my < cy - 82 || my > cy + 88) return false;
        float q = quality(mx, my);
        if (q >= requiredQuality()) {
            Sfx.mark(progress + 1);
            qualitySum += q;
            qualityHits++;
            progress++;
            if (progress >= rounds) {
                float avg = qualityHits <= 0 ? q : qualitySum / qualityHits;
                float perf = Mth.clamp((0.72f + avg * 0.28f) * (1f - Math.min(0.18f, mistakes * 0.035f)), 0.45f, 1f);
                succeed(perf);
            } else {
                showBanner("The angles agree", COL_GOOD, 650);
                makeRound();
            }
        } else {
            Sfx.markBad();
            loseLife(q > requiredQuality() - 0.12f
                    ? "Almost. The last edge still doubles"
                    : "The angles do not meet");
        }
        return true;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        int cx = centerX();
        int cy = contentCenterY() - 2;

        float fieldDx = normX(mouseX) - targetX;
        float fieldDy = normY(mouseY) - targetY;
        if (layeredField) {
            // Actual depth planes: distant scratches barely move, the mid plate
            // moves more, and foreground bars travel fastest. They all collapse
            // to the same origin at the real focus, so this is readable depth,
            // not decorative camera shake.
            int farX = Math.round(fieldDx * 10f), farY = Math.round(fieldDy * 8f);
            int midX = Math.round(fieldDx * 21f), midY = Math.round(fieldDy * 17f);
            p.roundOutline(cx - 106 + farX, cy - 80 + farY, 212, 160, Painter.withAlpha(theme().frame, 0x38));
            p.line(cx - 98 + farX, cy - 62 + farY, cx + 92 + farX, cy + 55 + farY, 1, Painter.withAlpha(theme().accentDark, 0x28));
            p.line(cx + 96 + midX, cy - 58 + midY, cx - 86 + midX, cy + 61 + midY, 1, Painter.withAlpha(theme().frameLite, 0x24));
        }

        // A thin cyclopean plate behind the fragments.
        p.roundRect(cx - 92, cy - 72, 184, 144, 0xFF11171A);
        p.outline(cx - 92, cy - 72, 184, 144, 2, 0xFF435158);
        p.outline(cx - 82, cy - 62, 164, 124, 1, 0xFF263238);

        // Faint impossible perspective lines. They are decoration, not a clue.
        int faint = Painter.withAlpha(theme().accentDark, 0x55);
        p.line(cx - 80, cy - 54, cx + 72, cy + 46, 1, faint);
        p.line(cx + 77, cy - 51, cx - 68, cy + 54, 1, faint);
        p.line(cx, cy - 60, cx - 16, cy + 58, 1, faint);

        float ndx = dx(mouseX);
        float ndy = dy(mouseY);
        float q = quality(mouseX, mouseY);
        int col = q >= requiredQuality() ? theme().good : theme().accent;

        // Optional decoy: a faint second focus that never receives all four
        // fragments. It is visibly translucent and wavers, so it can mislead at
        // a glance without becoming an invisible rule.
        if (ghostFocus) {
            float motion = WardConfig.accessibilityReduceMotion ? 0f : WardConfig.accessibilityGuiAnimationIntensity;
            int gx = cx + 44 + Math.round(Mth.sin(pulse * 1.3f) * 3f * motion);
            int gy = cy - 31 + Math.round(Mth.cos(pulse * 1.1f) * 2f * motion);
            p.disc(gx, gy, 9, Painter.withAlpha(theme().accentDark, 0x42));
            p.outline(gx - 10, gy - 10, 20, 20, 1,
                    Painter.withAlpha(theme().accent, 0x55));
        }

        for (int i = 0; i < 4; i++) {
            float dep = depth(i);
            int ox = Math.round(Mth.clamp(ndx * dep * 42f, -72f, 72f));
            int oy = Math.round(Mth.clamp(ndy * dep * 38f, -52f, 52f));
            int x = cx + ox;
            int y = cy + oy;
            int c = Painter.withAlpha(col, 0xD8 - i * 0x12);
            drawFragment(i, x, y, c, mouseX, mouseY);
        }
        if (layeredField) {
            int nearX = Math.round(Mth.clamp(fieldDx * 31f, -34f, 34f));
            int nearY = Math.round(Mth.clamp(fieldDy * 25f, -28f, 28f));
            int fc = Painter.withAlpha(theme().frameLite, 0x35);
            p.line(cx - 108 + nearX, cy - 74 + nearY, cx - 88 + nearX, cy - 74 + nearY, 2, fc);
            p.line(cx + 88 + nearX, cy + 74 + nearY, cx + 108 + nearX, cy + 74 + nearY, 2, fc);
        }

        // Central focus ring becomes solid only when the fragments really agree.
        int ring = q >= requiredQuality() ? theme().good
                : Painter.withAlpha(theme().accent, 0x70);
        p.outline(cx - 12, cy - 12, 24, 24, 1, ring);
        p.rect(cx - 2, cy - 2, cx + 2, cy + 2, ring);

        // Do not turn the puzzle into "sweep the mouse until a bar fills". The
        // numeric meter only appears once the player has already found the right
        // neighbourhood by reading the fragments. Before that, the geometry is
        // the clue.
        if (q >= WardConfig.parallaxMeterRevealThreshold) {
            int meterX = cx - 70;
            int meterY = cy + 82;
            int meterW = 140;
            p.rect(meterX, meterY, meterX + meterW, meterY + 4, 0xFF080B0D);
            float local = Mth.clamp((q - WardConfig.parallaxMeterRevealThreshold)
                    / Math.max(0.001f, 1f - WardConfig.parallaxMeterRevealThreshold), 0f, 1f);
            p.rect(meterX, meterY, meterX + Math.round(meterW * local), meterY + 4,
                    q >= requiredQuality() ? theme().good : theme().accentDark);
            int req = meterX + Math.round(meterW * Mth.clamp(
                    (requiredQuality() - WardConfig.parallaxMeterRevealThreshold)
                            / Math.max(0.001f, 1f - WardConfig.parallaxMeterRevealThreshold), 0f, 1f));
            p.rect(req, meterY - 2, req + 1, meterY + 6, theme().text);
        }

        progressDots(cx, cy + 96, rounds, progress);
    }

    private void drawFragment(int i, int cx, int cy, int color, int mouseX, int mouseY) {
        float turn = twistedPlate && i == 1
                ? (normX(mouseX) - targetX) * 12f + (normY(mouseY) - targetY) * 8f
                : 0f;
        int skew = Math.round(turn);
        switch (i) {
            case 0 -> { // upper fork
                p.line(cx - 14, cy - 14, cx - 3 + skew, cy - 3, 2, color);
                p.line(cx + 14, cy - 14, cx + 3 + skew, cy - 3, 2, color);
            }
            case 1 -> { // left hook
                p.line(cx - 15, cy - 2, cx - 5, cy - 2 + skew / 3f, 2, color);
                p.line(cx - 15, cy - 2, cx - 10, cy + 11, 2, color);
            }
            case 2 -> { // right broken bar
                p.line(cx + 5, cy - 2, cx + 15, cy - 2, 2, color);
                p.line(cx + 10, cy - 10, cx + 10, cy + 7, 2, color);
            }
            case 3 -> { // lower eye / root
                p.line(cx - 8, cy + 8, cx, cy + 14, 2, color);
                p.line(cx, cy + 14, cx + 8, cy + 8, 2, color);
                p.rect(cx - 2, cy + 5, cx + 2, cy + 9, color);
            }
        }
    }

    @Override
    protected String hintText() {
        if (breathingDepth) return "Depth itself breathes, but the true focus does not move: all layers still collapse at one fixed point";
        if (layeredField) return "Use the distant, middle and foreground planes as depth references; all of them settle at the true focus";
        return "Move the mouse until all four fragments collapse into one sigil. Click only when every layer agrees.";
    }
}
