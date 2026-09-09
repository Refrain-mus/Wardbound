package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * A singing lock. Something inside the chest is humming; move the mouse until your trace lies on
 * top of its trace, then click to hold the note.
 *
 * <p>Left and right shift the phase, up and down change the amplitude. It is the only lock driven
 * by mouse position rather than timing, which makes it a change of pace in a run of reflex tests.
 *
 * <p>Quirks:
 * <ul>
 *   <li>0 drifting note - the hum slides its phase while you chase it</li>
 *   <li>1 second harmonic - the hum carries an overtone, so the shape is not a clean sine</li>
 *   <li>2 dead air - the hum cuts out every couple of seconds and you match from memory</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class ResonanceScreen extends BaseMinigameScreen {

    private final boolean drifting;
    private final boolean harmonic;
    private final boolean deadAir;

    private final int rounds;
    private final Random rng;
    private final float[] thresholds;

    private float targetPhase;
    private float targetAmp;
    private float phaseDrift;
    private float cycles;

    private float playerPhase;
    private float playerAmp = 0.5f;

    private float scrollPhase;
    private float airPhase;
    private float flashTimer;
    private int flashColor = COL_GOOD;

    private float matchSum;
    private int matchCount;

    public ResonanceScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.resonance"), msg);

        boolean[] v = pickVariants(3, 7);
        drifting = v[0];
        harmonic = v[1];
        deadAir = v[2];
        if (drifting) modifierLabels.add("drifting note");
        if (harmonic) modifierLabels.add("second harmonic");
        if (deadAir) modifierLabels.add("dead air");

        this.rounds = wobble(3 + Math.round(value / 34f * difficulty), 1, 22, 3, 6);
        this.rng = new Random(seed ^ 0x5A17E5L);
        // One bar per note rather than one for the lock. Six identical tests in a
        // row is one test you pass six times; a bar that moves makes you read the
        // number every time instead of learning a single mouse position.
        float base = Mth.clamp(0.80f + 0.10f * (difficulty - 1f), 0.72f, 0.94f);
        this.thresholds = new float[rounds];
        for (int i = 0; i < rounds; i++) {
            // Widest swing in the middle of the lock: opening on a brutal note is
            // discouraging and finishing on a trivial one is anticlimactic.
            float arcOfLock = 1f - Math.abs((i / Math.max(1f, rounds - 1f)) - 0.5f) * 2f;
            float swing = (rng.nextFloat() - 0.42f) * (0.10f + 0.08f * arcOfLock);
            thresholds[i] = Mth.clamp(base + swing, 0.68f, 0.96f);
        }
        for (int i = 0; i < progress; i++) rng.nextFloat();
        setupRound();
    }

    /**
     * Was the worst offender: no clock at all meant one note could be
     * nursed for a minute and the whole lock was patience.
     */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.30f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.abyss();
    }

    @Override
    protected int panelHeight() {
        return 236;
    }

    private void setupRound() {
        targetPhase = rng.nextFloat() * 360f;
        targetAmp = 0.30f + rng.nextFloat() * 0.65f;
        cycles = 1.6f + rng.nextFloat() * 1.4f;
        phaseDrift = drifting ? (rng.nextBoolean() ? 1f : -1f) * (14f + rng.nextFloat() * 16f) : 0f;
    }

    @Override
    protected void step(float dt) {
        scrollPhase += dt;
        airPhase += dt;
        if (drifting) {
            targetPhase += phaseDrift * dt;
            if (targetPhase > 360f) targetPhase -= 360f;
            if (targetPhase < 0f) targetPhase += 360f;
        }
        if (flashTimer > 0f) flashTimer -= dt;
    }

    /** 0..1 how close the player's trace is to the hum. */
    private float match() {
        float phaseErr = Math.abs(Mth.degreesDifference(playerPhase, targetPhase)) / 180f;
        float ampErr = Math.abs(playerAmp - targetAmp);
        return Mth.clamp(1f - (phaseErr * 1.35f + ampErr * 1.6f), 0f, 1f);
    }

    private boolean humAudible() {
        return !deadAir || (airPhase % 3.0f) < 1.9f;
    }

    private void lockIn() {
        float m = match();
        matchCount++;
        if (m >= threshold()) {
            matchSum += m;
            progress++;
            flashTimer = 0.4f;
            flashColor = COL_GOOD;
            Sfx.mark(progress);
            burst(centerX(), scopeY() + scopeH() / 2, COL_GOOD);
            if (progress >= rounds) {
                succeed(matchCount == 0 ? 0.5f : matchSum / matchCount);
                return;
            }
            setupRound();
        } else {
            flashTimer = 0.4f;
            flashColor = COL_BAD;
            // Locking in a bad reading used to cost only the life, and the round
            // came back identical, so a miss was a free retry of the same note.
            // Now the note that beat you is re-cast harder: the bar it wants
            // creeps up and it will not be the same shape twice.
            for (int i = progress; i < thresholds.length; i++) {
                thresholds[i] = Mth.clamp(thresholds[i] + 0.035f, 0.68f, 0.97f);
            }
            if (loseLife("The note broke apart \u2014 the lock tightened")) setupRound();
        }
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button == 0) {
            lockIn();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKey(int key) {
        if (isActionKey(key)) {
            lockIn();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ layout

    private int scopeX() {
        return left() + 26;
    }

    private int scopeW() {
        return panelWidth() - 52;
    }

    private int scopeY() {
        return contentTop() + 4;
    }

    private int scopeH() {
        return contentBottom() - contentTop() - 34;
    }

    private float waveY(float t, float phaseDeg, float amp, int cy, int halfH, boolean overtone) {
        double a = Math.toRadians(phaseDeg) + t * cycles * Math.PI * 2;
        double s = Math.sin(a);
        if (overtone) s = s * 0.78 + Math.sin(a * 2) * 0.28;
        return cy - (float) (s * amp * halfH);
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int x = scopeX(), w = scopeW(), y = scopeY(), h = scopeH();
        int cy = y + h / 2;
        int halfH = h / 2 - 6;

        // the mouse is the instrument
        playerPhase = Mth.clamp((mouseX - x) / (float) w, 0f, 1f) * 360f;
        playerAmp = Mth.clamp(1f - (mouseY - y) / (float) h, 0.05f, 1f);

        well(x, y, w, h);
        p.scanlines(x + 1, y + 1, w - 2, h - 2, Painter.withAlpha(0x000000, 0x24));

        // graticule
        for (int i = 1; i < 8; i++) {
            int gx = x + w * i / 8;
            p.rect(gx, y + 1, gx + 1, y + h - 1, Painter.withAlpha(t.frame, 0x33));
        }
        for (int i = 1; i < 4; i++) {
            int gy = y + h * i / 4;
            p.rect(x + 1, gy, x + w - 1, gy + 1, Painter.withAlpha(t.frame, 0x33));
        }
        p.rect(x + 1, cy, x + w - 1, cy + 1, Painter.withAlpha(t.frame, 0x66));

        // the hum
        if (humAudible()) {
            for (int px = 1; px < w - 1; px += 2) {
                float tt = px / (float) w;
                float wy = waveY(tt, targetPhase, targetAmp, cy, halfH, harmonic);
                // deliberately a different hue to your own trace, so the two never blur together
                int hum = Painter.lighten(t.frame, 0.45f);
                p.rect(x + px, wy - 2, x + px + 2, wy + 2, Painter.withAlpha(hum, 0x3A));
                p.rect(x + px, wy - 1, x + px + 2, wy + 1, Painter.withAlpha(hum, 0xC8));
            }
        } else {
            for (int px = 1; px < w - 1; px += 8) {
                p.rect(x + px, cy - 1, x + px + 4, cy + 1, Painter.withAlpha(t.frame, 0x55));
            }
        }

        // your trace, drawn brighter and with a soft under-glow so the two are easy to tell apart
        int trace = flashTimer > 0f ? flashColor : t.accentLite;
        for (int px = 1; px < w - 1; px += 2) {
            float tt = px / (float) w;
            float wy = waveY(tt, playerPhase, playerAmp, cy, halfH, false);
            p.rect(x + px, wy - 3, x + px + 2, wy + 3, Painter.withAlpha(trace, 0x1E));
            p.rect(x + px, wy - 1, x + px + 2, wy + 1, trace);
        }

        // running spot, purely so the scope looks alive without flashing anything
        int spot = (int) ((scrollPhase * 60f) % (w - 4)) + 2;
        float sy = waveY(spot / (float) w, playerPhase, playerAmp, cy, halfH, false);
        p.rect(x + spot - 2, sy - 2, x + spot + 2, sy + 2, 0xFFFFFFFF);

        // match meter
        float m = match();
        int bx = x, by = y + h + 8, bw = w, bh = 8;
        p.roundRect(bx - 1, by - 1, bw + 2, bh + 2, t.wellEdge);
        p.roundRect(bx, by, bw, bh, t.well);
        int filled = Math.round(bw * m);
        int barCol = m >= threshold() ? COL_GOOD : Painter.mix(COL_BAD, t.accent, m);
        for (int i = 0; i < filled; i += 4) {
            p.rect(bx + i, by + 1, bx + Math.min(filled, i + 3), by + bh - 1, barCol);
        }
        int tx = bx + Math.round(bw * threshold());
        p.rect(tx - 1, by - 4, tx + 1, by + bh + 4, t.text);

        smallCentered(g, Math.round(m * 100) + "%   \u00b7   lock at " + Math.round(threshold() * 100)
                        + "%   \u00b7   note " + (progress + 1) + " / " + rounds,
                centerX(), by + bh + 6, m >= threshold() ? COL_GOOD : t.faint, 0.9f);
    }

    /** What this note demands. Clamped so a restored progress index cannot walk off the end. */
    private float threshold() {
        return thresholds[Math.min(progress, thresholds.length - 1)];
    }

    @Override
    protected String hintText() {
        return "Move the mouse to match the hum, then click to hold it";
    }
}
