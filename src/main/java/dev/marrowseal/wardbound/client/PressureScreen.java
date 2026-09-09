package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * A pneumatic lock. Hold the bellows to build pressure and let go inside the marked band; hold too
 * long and the vessel splits.
 *
 * <p>This is the only lock played by holding rather than tapping, which is on purpose: it reads
 * completely differently to the timing bar even though both are about a moving value.
 *
 * <p>Quirks:
 * <ul>
 *   <li>0 leaking seal - the feed surges and sags, so the needle never climbs evenly</li>
 *   <li>1 juddering gauge - the safe band creeps up and down the vessel</li>
 *   <li>2 hairline crack - the vessel gives out well below the top of the scale</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class PressureScreen extends BaseMinigameScreen {

    private final boolean leaking;
    private final boolean juddering;
    private final boolean cracked;

    private final int rounds;
    private final Random rng;
    private final float fillRate;
    private final float burstAt;

    private float pressure;
    private boolean holding;
    private float bandCenter;
    private float bandHalf;
    private float bandDrift;
    private float wobblePhase;
    /** Builds while held, bleeds off after release. See the fill in {@link #step}. */
    private float momentum;
    /** Where the hairline crack sits, as a pressure value. -1 if this lock has none. */
    private float crackAt = -1f;
    /**
     * A second flaw, below the target band rather than above it.
     *
     * <p>The crack punished overshooting, which is the mistake you were already
     * being punished for. This one punishes the opposite: creeping up slowly and
     * stopping short. Between them there is a narrow approach that is safe, and
     * that is the lock.
     */
    private float flawAt = -1f;
    private float flawHalf = 0.05f;
    private float flashTimer;
    private int flashColor = COL_GOOD;

    private float accuracySum;
    private int accuracyCount;

    public PressureScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.pressure"), msg);

        boolean[] v = pickVariants(3, 6);
        leaking = v[0];
        juddering = v[1];
        cracked = v[2];
        if (leaking) modifierLabels.add("leaking seal");
        if (juddering) modifierLabels.add("juddering gauge");
        if (cracked) modifierLabels.add("crack");

        this.rounds = wobble(3 + Math.round(value / 30f * difficulty), 1, 21, 3, 6);
        this.rng = new Random(seed ^ 0xB0117L);
        float variance = 0.85f + seedVariance(51) * 0.4f;
        this.fillRate = (0.44f + value * 0.0032f) * difficulty * variance;
        // Was just a lower burst point, which the player could not interact with
        // at all: the vessel simply popped sooner and the variant amounted to
        // "this one is harder". The crack is now a specific place on the gauge
        // that you must stop short of, it sits somewhere different every round,
        // and running through it costs two lives rather than one.
        this.burstAt = 1.0f;

        for (int i = 0; i < progress; i++) rng.nextFloat();
        setupRound();
    }

    /** Short. A ward you can outwait is not a ward. */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.32f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.copper();
    }

    private void setupRound() {
        bandHalf = Math.max(0.024f, (0.072f - progress * 0.008f) / Math.max(0.6f, difficulty));
        float hi = burstAt - bandHalf - 0.05f;
        bandCenter = Mth.clamp(0.28f + rng.nextFloat() * 0.50f, bandHalf + 0.10f, hi);
        if (cracked) {
            // Just above the target band, so overshooting is what finds it.
            crackAt = Math.min(0.97f, bandCenter + bandHalf + 0.025f + rng.nextFloat() * 0.075f);
            // And one below it, somewhere different every round, so the run up
            // to the band is not free either.
            // A band, not a hairline. A one-pixel flaw you had to avoid landing
            // on was a coin toss; a visible stretch of the gauge you must not
            // stop in is a thing you can actually play around.
            float low = bandCenter - bandHalf - 0.06f;
            if (low > 0.20f) {
                flawHalf = 0.045f + rng.nextFloat() * 0.035f;
                flawAt = flawHalf + 0.04f + rng.nextFloat() * (low - flawHalf - 0.08f);
            } else {
                flawAt = -1f;
            }
        }
        bandDrift = juddering ? (rng.nextBoolean() ? 1f : -1f) * (0.075f + rng.nextFloat() * 0.08f) : 0f;
        pressure = 0f;
        holding = false;
    }

    private float rateNow() {
        float r = fillRate * (1f + progress * 0.10f);
        if (leaking) {
            // Was a clean sine you could feel the period of after two rounds.
            // Two beating frequencies plus a slow one never repeat cleanly, so
            // the vessel surges and stalls without a rhythm to learn.
            r *= 0.55f
                    + 0.42f * (float) Math.sin(wobblePhase * 2.6f)
                    + 0.28f * (float) Math.sin(wobblePhase * 4.7f + 1.3f)
                    + 0.15f * (float) Math.sin(wobblePhase * 0.9f);
            r = Math.max(fillRate * 0.15f, r);
        }
        return r;
    }

    @Override
    protected void step(float dt) {
        wobblePhase += dt;

        if (holding) {
            // The needle carries momentum: it takes a moment to get going and
            // it does not stop the instant you let go. Without this the lock was
            // a pure reaction test with no feel to it, and every press was
            // identical to every other press.
            momentum = Math.min(1f, momentum + dt * 3.4f);
            pressure += rateNow() * dt * (0.45f + momentum * 0.75f);
            if (cracked && crackAt > 0f && pressure >= crackAt) {
                pressure = 0f;
                holding = false;
                momentum = 0f;
                Sfx.overset();
            // loseLife returns TRUE when you survived it, so the second life is
                // only taken if the first one did not already end the lock, and the
                // round is only rebuilt if you are still standing after both.
                if (loseLife("You drove it through the crack") && loseLife("That one costs two")) {
                    setupRound();
                }
                return;
            }
            if (pressure >= burstAt) {
                pressure = burstAt;
                holding = false;
                flashTimer = 0.4f;
                flashColor = COL_BAD;
                Sfx.overset();
                if (loseLife("The vessel split")) setupRound();
                return;
            }
        } else {
            momentum = Math.max(0f, momentum - dt * 2.6f);
        }
        if (!holding && pressure > 0f) {
            pressure = Math.max(0f, pressure - dt * 0.75f);
        }

        if (juddering) {
            bandCenter += bandDrift * dt;
            float lo = bandHalf + 0.08f, hi = burstAt - bandHalf - 0.05f;
            if (bandCenter < lo) {
                bandCenter = lo;
                bandDrift = -bandDrift;
            } else if (bandCenter > hi) {
                bandCenter = hi;
                bandDrift = -bandDrift;
            }
        }

        if (flashTimer > 0f) flashTimer -= dt;
    }

    private void startHold() {
        if (holding) return;
        holding = true;
        pressure = 0f;
        Sfx.pinRise();
    }

    private void release() {
        if (!holding) return;
        holding = false;

        float dist = Math.abs(pressure - bandCenter);
        accuracyCount++;
        if (dist <= bandHalf) {
            accuracySum += 1f - (dist / bandHalf);
            progress++;
            flashTimer = 0.35f;
            flashColor = COL_GOOD;
            Sfx.mark(progress);
            burst(tubeX() + tubeW() / 2, valueY(pressure), COL_GOOD);
            if (progress >= rounds) {
                succeed(accuracyCount == 0 ? 0.5f : accuracySum / accuracyCount);
                return;
            }
            setupRound();
        } else {
            flashTimer = 0.35f;
            flashColor = COL_BAD;
            // Releasing inside the lower flaw is the mistake. Passing through it
            // on the way up is not, and cannot be: the flaw sits below the band,
            // so every single attempt crosses it. Judging the crossing meant the
            // variant failed the lock on the first press, every time.
            if (cracked && flawAt > 0f
                    && pressure >= flawAt - flawHalf && pressure <= flawAt + flawHalf) {
                Sfx.overset();
                if (loseLife("You let go on the flaw") && loseLife("That one costs two")) {
                    setupRound();
                }
                return;
            }
            if (loseLife(pressure < bandCenter ? "Not enough pressure" : "Over pressure")) setupRound();
        }
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button == 0) {
            startHold();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRelease(double mx, double my, int button) {
        if (button == 0) {
            release();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKey(int key) {
        if (isActionKey(key)) {
            startHold();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyRelease(int key) {
        if (isActionKey(key)) {
            release();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ layout

    private int tubeX() {
        return left() + 46;
    }

    private int tubeW() {
        return 40;
    }

    private int tubeY() {
        return contentTop() + 6;
    }

    private int tubeH() {
        return contentBottom() - contentTop() - 14;
    }

    /** Screen y of a 0..1 pressure reading. */
    private int valueY(float v) {
        return tubeY() + tubeH() - Math.round(Mth.clamp(v, 0f, 1f) * tubeH());
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int x = tubeX(), w = tubeW(), y = tubeY(), h = tubeH();

        well(x, y, w, h);

        // scale ticks down the left flank
        for (int i = 0; i <= 10; i++) {
            int ty = y + h - h * i / 10;
            boolean major = i % 5 == 0;
            p.rect(x - 8, ty, x - (major ? 3 : 5), ty + 1, major ? t.frameLite : Painter.withAlpha(t.frame, 0xAA));
        }

        // the danger zone above the burst line
        if (cracked && flawAt > 0f) {
            int top = valueY(flawAt + flawHalf), bot = valueY(flawAt - flawHalf);
            p.rect(left() + 26, top, left() + 26 + 62, bot, Painter.withAlpha(COL_BAD, 0x33));
            p.rect(left() + 26, top, left() + 26 + 62, top + 1, Painter.withAlpha(COL_BAD, 0xAA));
            p.rect(left() + 26, bot - 1, left() + 26 + 62, bot, Painter.withAlpha(COL_BAD, 0xAA));
            for (int i = 0; i < 7; i++) {
                int fx = left() + 28 + i * 9;
                p.line(fx, bot - 1, fx + 6, top + 1, 1, Painter.withAlpha(COL_BAD, 0x66));
            }
        }
        if (cracked && crackAt > 0f) {
            int crackY = valueY(crackAt);
            for (int i = 0; i < 5; i++) {
                int cx0 = left() + 30 + i * 9;
                p.rect(cx0, crackY - 1 - (i % 2), cx0 + 7, crackY + (i % 2), COL_BAD);
            }
        }
        int burstY = valueY(burstAt);
        p.rect(x + 1, y + 1, x + w - 1, burstY, Painter.withAlpha(COL_BAD, 0x1E));
        for (int i = y + 1; i < burstY; i += 6) {
            p.rect(x + 1, i, x + w - 1, i + 2, Painter.withAlpha(COL_BAD, 0x22));
        }
        p.rect(x - 3, burstY - 1, x + w + 3, burstY + 1, COL_BAD);

        // the safe band
        int b1 = valueY(bandCenter + bandHalf);
        int b2 = valueY(bandCenter - bandHalf);
        p.rect(x + 1, b1, x + w - 1, b2, Painter.withAlpha(COL_GOOD, 0x38));
        p.rect(x - 4, b1, x + w + 4, b1 + 2, COL_GOOD);
        p.rect(x - 4, b2 - 2, x + w + 4, b2, COL_GOOD);
        int bc = valueY(bandCenter);
        p.rect(x + 1, bc, x + w - 1, bc + 1, Painter.withAlpha(COL_GOOD, 0x99));

        // the column of pressure, drawn in blocks so it climbs like a pixel bar
        int py = valueY(pressure);
        int fillCol = flashTimer > 0f ? flashColor : t.accent;
        for (int i = y + h - 3; i > py; i -= 3) {
            float f = (y + h - i) / (float) Math.max(1, h);
            p.rect(x + 3, i, x + w - 3, i + 2, Painter.mix(Painter.darken(fillCol, 0.35f), fillCol, f));
        }
        p.rect(x + 1, py, x + w - 1, py + 2, Painter.lighten(fillCol, 0.5f));

        // needle
        p.rect(x + w + 2, py - 3, x + w + 10, py + 3, t.frameDark);
        p.rect(x + w + 3, py - 2, x + w + 9, py + 2, t.frameLite);
        p.rect(x - 10, py - 1, x + 1, py + 1, t.frameLite);

        rivet(x - 6, y - 6);
        rivet(x + w + 6, y - 6);
        rivet(x - 6, y + h + 6);
        rivet(x + w + 6, y + h + 6);

        // the manometer: same reading, told as a dial, and it gives the panel its own face
        int dcx = left() + panelWidth() - 84;
        int dcy = contentCenterY();
        int dr = 44;
        p.disc(dcx, dcy, dr + 6, t.frameDark);
        p.ring(dcx, dcy, dr + 5, dr + 2, t.frame);
        p.ring(dcx, dcy, dr + 4, dr + 3, t.frameLite);
        p.disc(dcx, dcy, dr + 1, t.well);
        p.dither(dcx - dr, dcy - dr, dr * 2, dr * 2, Painter.withAlpha(t.accent, 0x08), 2, 0);

        // 240 degree scale, starting bottom-left
        for (int i = 0; i <= 12; i++) {
            float f = i / 12f;
            double a = Math.toRadians(150 + 240 * f);
            boolean major = i % 3 == 0;
            int r1 = dr - (major ? 12 : 7);
            int tx1 = dcx + (int) Math.round(Math.cos(a) * r1);
            int ty1 = dcy + (int) Math.round(Math.sin(a) * r1);
            int tx2 = dcx + (int) Math.round(Math.cos(a) * (dr - 2));
            int ty2 = dcy + (int) Math.round(Math.sin(a) * (dr - 2));
            int col = f > burstAt ? COL_BAD : (major ? t.frameLite : Painter.withAlpha(t.frame, 0xAA));
            p.line(tx1, ty1, tx2, ty2, major ? 3 : 2, col);
        }
        // the band, marked on the dial too
        p.arc(dcx, dcy, dr - 3, 150 + 240 * (bandCenter - bandHalf), 150 + 240 * (bandCenter + bandHalf),
                5, Painter.withAlpha(COL_GOOD, 0x99));
        p.arc(dcx, dcy, dr - 3, 150 + 240 * burstAt, 390, 5, Painter.withAlpha(COL_BAD, 0x88));

        double ha = Math.toRadians(150 + 240 * Mth.clamp(pressure, 0f, 1f));
        float hx = dcx + (float) Math.cos(ha) * (dr - 9);
        float hy = dcy + (float) Math.sin(ha) * (dr - 9);
        p.line(dcx, dcy, hx, hy, 3, 0xFF120C08);
        p.line(dcx, dcy, hx, hy, 2, flashTimer > 0f ? flashColor : t.accentLite);
        p.disc(dcx, dcy, 6, t.frameDark);
        p.disc(dcx, dcy, 4, t.frameLite);

        Sigils.drawCentered(p, Sigils.TILE[7], dcx, dcy - 24, 1,
                Painter.withAlpha(t.faint, 0x99), 0, 0);

        smallCentered(g, Math.round(pressure * 100) + " psi", dcx, dcy + 18, t.dim, 0.9f);
        progressDots(dcx, dcy + dr + 14, rounds, progress);
    }

    @Override
    protected String hintText() {
        if (holding) return "Let go inside the green band";
        return "Hold SPACE or the mouse to pump \u00b7 release in the band";
    }
}
