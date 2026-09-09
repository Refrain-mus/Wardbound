package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * The lock is heat welded shut. Work the bellows and stop the flame at the right temperature to
 * soften the bolt.
 *
 * <p>Quirks: drifting band, blinking band, ember hazard.
 */
@OnlyIn(Dist.CLIENT)
public class TimingScreen extends BaseMinigameScreen {

    private final boolean drifting;
    private final boolean blinking;
    private final boolean hazard;

    private final int rounds;
    private final Random rng;
    private final float baseSpeed;

    private float marker;
    private float dir = 1f;
    private float zoneCenter;
    private float zoneHalf;
    private float zoneDrift;
    private float hazardCenter = -1f;
    /** Countdown to the zone changing direction on its own. */
    private float zoneJinkTimer;
    /** Countdown to the ember jumping somewhere else entirely. */
    private float hazardJumpTimer;
    private float hazardHalf;
    private float blinkPhase;
    private float flashTimer;

    private float accuracySum;
    private int accuracyCount;

    public TimingScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.timing"), msg);

        boolean[] v = pickVariants(3, 2);
        drifting = v[0];
        blinking = v[1];
        hazard = v[2];
        if (drifting) modifierLabels.add("drifting zone");
        if (blinking) modifierLabels.add("fading flame \u00b7 the zone blinks out");
        if (hazard) modifierLabels.add("ember hazard");

        this.rounds = wobble(3 + Math.round(value / 26f * difficulty), 1, 18, 3, 7);
        this.rng = new Random(seed);
        float variance = 0.8f + seedVariance(11) * 0.45f;
        this.baseSpeed = (0.72f + value * 0.012f) * difficulty * variance;
        for (int i = 0; i < progress; i++) rng.nextFloat();
        setupRound();
    }

    /** Short. Every one of these was outlastable. */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.40f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.forge();
    }

    private void setupRound() {
        zoneHalf = Math.max(0.036f, (0.115f - progress * 0.010f) / Math.max(0.6f, difficulty));
        zoneCenter = 0.20f + rng.nextFloat() * 0.60f;
        // Was a slow, constant slide that you could simply lead. It now moves
        // faster and changes its mind, so the zone has to be re-read rather than
        // predicted from where it was a second ago.
        zoneDrift = drifting ? (rng.nextBoolean() ? 1f : -1f) * (0.13f + rng.nextFloat() * 0.14f) : 0f;
        zoneJinkTimer = 0.5f + rng.nextFloat() * 0.9f;
        if (hazard) {
            hazardHalf = zoneHalf * 0.85f;
            do {
                hazardCenter = 0.12f + rng.nextFloat() * 0.76f;
            } while (Math.abs(hazardCenter - zoneCenter) < zoneHalf + hazardHalf + 0.10f);
        }
        marker = rng.nextBoolean() ? 0f : 1f;
        dir = marker < 0.5f ? 1f : -1f;
        blinkPhase = 0f;
    }

    private float speedNow() {
        return baseSpeed * (1f + progress * 0.13f);
    }

    /** The band is hidden for a third of its cycle rather than a full third of a second. */
    private boolean zoneVisible() {
        // Out for longer than it is in, and on a cycle that is not a round
        // number of anything. It used to be visible 70% of the time on a tidy
        // 1.6s loop, which you could simply wait out.
        return !blinking || (blinkPhase % 1.35f) < 0.72f;
    }

    /**
     * The ember does not stay where you left it.
     *
     * <p>A hazard fixed in one place for a whole round is a spot you avoid once
     * and then forget, which made the variant a mild inconvenience. Jumping it
     * makes the bar something you keep watching instead of something you set up
     * one swing against.
     */
    /**
     * Sometimes the ember and the zone trade places outright.
     *
     * <p>Moving the hazard around was still a hazard you tracked separately from
     * the target. Swapping them means the place you were about to strike is now
     * the place that costs two lives, which is the only version of this that
     * makes you re-read the bar rather than re-aim at it.
     */
    private void swapHazard() {
        if (!hazard || hazardCenter < 0f) return;
        float t = zoneCenter;
        zoneCenter = hazardCenter;
        hazardCenter = t;
        Sfx.play(WardSounds.OVERSET, 0.45f, 1.5f);
        showBanner("They changed places", COL_BAD, 700);
    }

    private void jumpHazard() {
        if (!hazard) return;
        for (int guard = 0; guard < 40; guard++) {
            float c = 0.10f + rng.nextFloat() * 0.80f;
            if (Math.abs(c - zoneCenter) >= zoneHalf + hazardHalf + 0.08f) {
                hazardCenter = c;
                return;
            }
        }
    }

    @Override
    protected void step(float dt) {
        marker += dir * speedNow() * dt;
        if (marker > 1f) {
            marker = 1f;
            dir = -1f;
        } else if (marker < 0f) {
            marker = 0f;
            dir = 1f;
        }
        // The ember moves whether or not the zone does.
        if (hazard) {
            hazardJumpTimer -= dt;
            if (hazardJumpTimer <= 0f) {
                hazardJumpTimer = 0.55f + rng.nextFloat() * 0.75f;
                if (rng.nextFloat() < 0.45f) swapHazard();
                else jumpHazard();
            }
        }

        if (drifting) {
            zoneJinkTimer -= dt;
            if (zoneJinkTimer <= 0f) {
                zoneJinkTimer = 0.45f + rng.nextFloat() * 0.85f;
                if (rng.nextFloat() < 0.55f) zoneDrift = -zoneDrift;
                // and occasionally a lurch rather than a turn
                if (rng.nextFloat() < 0.30f) zoneCenter += zoneDrift * 0.35f;
            }
            zoneCenter += zoneDrift * dt;
            float lo = zoneHalf + 0.04f, hi = 1f - zoneHalf - 0.04f;
            if (zoneCenter < lo) {
                zoneCenter = lo;
                zoneDrift = -zoneDrift;
            } else if (zoneCenter > hi) {
                zoneCenter = hi;
                zoneDrift = -zoneDrift;
            }
        }
        boolean litBefore = zoneVisible();
        blinkPhase += dt;
        // Every time the flame comes back it comes back somewhere else. A zone
        // that blinks in place is a zone you memorise once and then ignore the
        // blinking of entirely.
        if (blinking && !litBefore && zoneVisible()) {
            zoneCenter = Mth.clamp(zoneCenter + (rng.nextFloat() - 0.5f) * 0.55f,
                    zoneHalf + 0.06f, 1f - zoneHalf - 0.06f);
        }
        if (flashTimer > 0f) flashTimer -= dt;
    }

    private void stopMarker() {
        if (resolved) return;

        // Same forgiveness the dial gets: judge against the stretch the flame covered during the
        // input grace window, not a single instant.
        float back = dir * speedNow() * grace();
        float from = Mth.clamp(marker - back, 0f, 1f);
        float lo = Math.min(from, marker), hi = Math.max(from, marker);

        if (hazard && hazardCenter >= 0f
                && hi >= hazardCenter - hazardHalf && lo <= hazardCenter + hazardHalf) {
            // Two lives, not one. An ember is drawn in red, sits away from the
            // zone, and moves: hitting it is never a near miss, it is a swing
            // taken at the one place on the bar you were told not to. A cost
            // equal to a mistimed press made it not worth avoiding.
            accuracyCount++;
            jumpHazard();
            Sfx.overset();
            // loseLife returns TRUE when you survived it, so the second life is
            // only taken if the first one did not already end the lock, and the
            // round is only rebuilt if you are still standing after both.
            if (loseLife("Struck the hot ember \u2014 twice over") && loseLife("That one costs two")) {
                setupRound();
            }
            return;
        }

        float dist;
        if (hi >= zoneCenter && lo <= zoneCenter) dist = 0f;
        else dist = Math.min(Math.abs(lo - zoneCenter), Math.abs(hi - zoneCenter));

        if (dist <= zoneHalf) {
            accuracySum += 1f - (dist / zoneHalf);
            accuracyCount++;
            progress++;
            flashTimer = 0.35f;
            Sfx.mark(progress);
            burst(barX() + Math.round(marker * barW()), barY() + 13, COL_GOOD);
            if (progress >= rounds) {
                succeed(accuracyCount == 0 ? 0.5f : accuracySum / accuracyCount);
            } else {
                setupRound();
            }
        } else {
            accuracyCount++;
            if (loseLife("Missed the heat")) setupRound();
        }
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button == 0) {
            stopMarker();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKey(int key) {
        if (isActionKey(key)) {
            stopMarker();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ layout

    private int barX() {
        return left() + 32;
    }

    private int barW() {
        return panelWidth() - 64;
    }

    private int barY() {
        return contentCenterY() - 26;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int x = barX(), w = barW(), y = barY(), h = 28;
        int cx = centerX();

        // the forge channel
        well(x, y, w, h);
        p.dither(x + 1, y + 1, w - 2, h - 2, Painter.withAlpha(t.accent, 0x10), 2, 0);
        rivet(x - 8, y + h / 2);
        rivet(x + w + 7, y + h / 2);

        // heat gradient, drawn as chunky bands rather than a per-pixel ramp
        int bands = 12;
        for (int i = 0; i < bands; i++) {
            float f = i / (float) bands;
            int bx1 = x + Math.round(w * f);
            int bx2 = x + Math.round(w * (i + 1) / (float) bands);
            p.rect(bx1, y + 1, bx2, y + h - 1, Painter.withAlpha(t.accent, (int) (0x0C + 0x1C * f)));
        }

        if (hazard && hazardCenter >= 0f) {
            int hx1 = x + Math.round((hazardCenter - hazardHalf) * w);
            int hx2 = x + Math.round((hazardCenter + hazardHalf) * w);
            p.rect(hx1, y + 1, hx2, y + h - 1, Painter.withAlpha(COL_BAD, 0x44));
            for (int i = hx1; i < hx2; i += 6) {
                p.rect(i, y + 1, Math.min(hx2, i + 3), y + h - 1, Painter.withAlpha(COL_BAD, 0x30));
            }
            p.rect(hx1, y + 1, hx1 + 2, y + h - 1, COL_BAD);
            p.rect(hx2 - 2, y + 1, hx2, y + h - 1, COL_BAD);
            Sigils.drawCentered(p, Sigils.TILE[6], (hx1 + hx2) / 2, y + h / 2, 1,
                    Painter.withAlpha(COL_BAD, 0xCC), 0, 0);
        }

        if (zoneVisible()) {
            int zx1 = x + Math.round((zoneCenter - zoneHalf) * w);
            int zx2 = x + Math.round((zoneCenter + zoneHalf) * w);
            int zc = x + Math.round(zoneCenter * w);
            int core = Math.max(2, (zx2 - zx1) / 6);
            p.rect(zx1, y + 1, zx2, y + h - 1, Painter.withAlpha(COL_GOOD, 0x30));
            p.rect(zc - core, y + 1, zc + core, y + h - 1, Painter.withAlpha(COL_GOOD, 0x5A));
            p.rect(zx1, y + 1, zx1 + 2, y + h - 1, COL_GOOD);
            p.rect(zx2 - 2, y + 1, zx2, y + h - 1, COL_GOOD);
            // marker chevron above the sweet spot
            p.rect(zc - 3, y - 8, zc + 3, y - 5, COL_GOOD);
            p.rect(zc - 2, y - 5, zc + 2, y - 3, COL_GOOD);
            p.rect(zc - 1, y - 3, zc + 1, y - 1, COL_GOOD);
        }

        // tick marks
        for (int i = 1; i < 10; i++) {
            int tx = x + w * i / 10;
            p.rect(tx, y + h - 6, tx + 1, y + h - 2, Painter.withAlpha(t.frame, 0xAA));
        }

        // the flame
        int m = x + Math.round(marker * w);
        int glowCol = flashTimer > 0f ? COL_GOOD : t.accentLite;
        p.rect(m - 4, y - 7, m + 4, y + h + 7, Painter.withAlpha(glowCol, 0x1E));
        p.rect(m - 2, y - 7, m + 2, y + h + 7, Painter.darken(glowCol, 0.5f));
        p.rect(m - 1, y - 7, m + 1, y + h + 7, glowCol);
        p.rect(m - 3, y + h + 6, m + 3, y + h + 10, glowCol);
        p.rect(m - 2, y - 10, m + 2, y - 6, glowCol);

        progressDots(cx, y + h + 26, rounds, progress);
    }

    @Override
    protected String hintText() {
        return "Press SPACE at the centre of the green band";
    }
}
