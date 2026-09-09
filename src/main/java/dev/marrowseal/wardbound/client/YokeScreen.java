package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * The Yoke. Two mechanisms bolted to one another, and you have both of them.
 *
 * <p>Every other lock in this mod asks for one kind of attention. Some want a
 * press at the right instant, some want a hold, some want you to think. This
 * one wants a hold <i>and</i> a press, at the same time, from the same person,
 * and the two halves are coupled so that doing either one well makes the other
 * one harder.
 *
 * <p><b>Left hand.</b> A counterweight falls under its own weight. The mouse
 * sets where you want it; it does not go there, it leans that way, so you are
 * steering a heavy thing rather than placing a light one. Keep it inside the
 * collar. Out of the collar the yoke starts to slip, and a full slip costs a
 * life.
 *
 * <p><b>Right hand.</b> A ratchet turns past an index mark. Strike each tooth as
 * it passes. Miss and you have simply missed; there are more teeth coming.
 *
 * <p><b>The coupling.</b> Every tooth you strike kicks the counterweight and
 * speeds the ratchet up. So a good run is one that gets progressively harder in
 * the exact hand you are not currently thinking about, and the failure mode is
 * not being bad at either half - it is forgetting one of them for two seconds.
 *
 * <p>Quirks a chest may carry:
 * <ul>
 *   <li>0 crossed linkage - the counterweight leans away from the mouse rather
 *       than toward it. Trivial to describe, genuinely difficult to hold while
 *       also watching the ratchet.</li>
 *   <li>1 heavy weight - falls faster and answers the mouse more slowly.</li>
 *   <li>2 quickening - the ratchet gains far more speed per tooth, so the last
 *       two are a different lock from the first two.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class YokeScreen extends BaseMinigameScreen {

    private final boolean crossed;
    private final boolean heavy;
    private final boolean quickening;

    private final int teeth;
    private final float fall;
    private final float lag;
    private final float collarHalf;
    private final float sweepBase;
    private final float sweepPerTooth;
    private final float markHalf;

    private final Random rng;

    /** Counterweight position, 0 at the top of its run and 1 at the bottom. */
    private float weight = 0.5f;
    private float weightVel;
    private float want = 0.5f;

    /** Where the collar sits, and where it is drifting to. */
    private float collar = 0.5f;
    private float collarDrift;
    /** Countdown to the collar jumping somewhere it was not. */
    private float feintTimer = 1.8f;
    /** Where the collar is pretending to be, while it moves. */
    private float ghostCollar = -1f;
    private float ghostFade;
    /** Wobble applied to the ratchet's speed, so it never holds one rate. */
    private float sweepWobble = 1f;
    private float wobbleTimer;

    /** Fills while the weight is outside the collar. Full costs a life. */
    private float slip;

    /** Ratchet angle, and where the tooth to strike currently is. */
    private float wheel;
    private int struck;
    private int swings;

    private float jolt;
    private float flashTimer;
    private int flashColor = COL_GOOD;

    public YokeScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.yoke"), msg);

        boolean[] v = pickVariants(3, 96);
        crossed = v[0];
        heavy = v[1];
        quickening = v[2];
        if (crossed) modifierLabels.add("crossed linkage");
        if (heavy) modifierLabels.add("heavy weight");
        if (quickening) modifierLabels.add("quickening");

        this.rng = new Random(seed ^ 0x101E5EEDL);
        this.teeth = wobble(3 + Math.round(value / 42f * difficulty), 1, 97, 3, 6);

        this.fall = (0.16f + value * 0.0011f) * difficulty * (heavy ? 1.6f : 1f);
        // Lag is what makes it a steer rather than a drag. Too little and the
        // weight simply sits where the cursor is and the left hand is free.
        this.lag = (heavy ? 1.5f : 2.6f) / Mth.clamp(difficulty, 0.7f, 1.8f);
        this.collarHalf = Mth.clamp(0.115f / difficulty, 0.055f, 0.15f);
        this.collarDrift = (rng.nextBoolean() ? 1f : -1f) * (0.035f + rng.nextFloat() * 0.05f);

        this.sweepBase = (95f + value * 0.5f) * difficulty * (0.9f + seedVariance(98) * 0.3f);
        this.sweepPerTooth = sweepBase * (quickening ? 0.30f : 0.13f);
        this.markHalf = Mth.clamp(15f / (float) Math.sqrt(Math.max(0.5f, difficulty)), 8f, 22f);

        this.struck = Mth.clamp(progress, 0, teeth - 1);
        this.wheel = rng.nextInt(360);
    }

    @Override
    protected Theme createTheme() {
        return Theme.forge();
    }

    /**
     * Two hands means two things to lose track of, so the clock is generous.
     * The pressure here is the coupling, not the time.
     */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.42f;
    }

    /**
     * Anything that takes the picture away breaks this lock rather than
     * hardening it: you cannot steer a weight you cannot see while also reading
     * a wheel you cannot see.
     */
    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING;
    }

    // ------------------------------------------------------------------ simulation

    private float sweepNow() {
        return (sweepBase + sweepPerTooth * struck) * sweepWobble;
    }

    @Override
    protected void step(float dt) {
        if (flashTimer > 0f) flashTimer -= dt;
        if (jolt > 0f) jolt = Math.max(0f, jolt - dt * 3f);

        // ---- left hand
        float target = crossed ? 1f - want : want;
        weightVel += (target - weight) * lag * dt * 6f;
        weightVel += fall * dt;                       // it is always coming down
        weightVel *= Math.max(0f, 1f - dt * 3.2f);    // and the linkage is stiff
        weight = Mth.clamp(weight + weightVel * dt, 0f, 1f);
        if (weight <= 0f || weight >= 1f) weightVel = 0f;

        // The collar feints.
        //
        // A band sliding steadily along a beam is a band you lead, and once you
        // are leading it the left hand costs nothing. Every couple of seconds it
        // reverses or jumps outright, and the place it was is left drawn for a
        // moment so the lie is legible rather than merely unfair.
        feintTimer -= dt;
        if (feintTimer <= 0f) {
            feintTimer = 1.3f + rng.nextFloat() * 1.8f;
            ghostCollar = collar;
            ghostFade = 0.55f;
            if (rng.nextFloat() < 0.45f) {
                collar = Mth.clamp(collar + (rng.nextBoolean() ? 1f : -1f)
                        * (0.12f + rng.nextFloat() * 0.16f), 0.18f, 0.82f);
                Sfx.play(WardSounds.PLUG_TURN, 0.4f, 1.4f);
            } else {
                collarDrift = -collarDrift * (0.7f + rng.nextFloat() * 0.9f);
            }
        }
        if (ghostFade > 0f) ghostFade -= dt;

        // And the ratchet will not hold a rate.
        wobbleTimer -= dt;
        if (wobbleTimer <= 0f) {
            wobbleTimer = 0.7f + rng.nextFloat() * 1.3f;
            sweepWobble = 0.62f + rng.nextFloat() * 0.85f;
        }

        collar += collarDrift * dt;
        if (collar < 0.16f + collarHalf) {
            collar = 0.16f + collarHalf;
            collarDrift = -collarDrift;
        } else if (collar > 0.84f - collarHalf) {
            collar = 0.84f - collarHalf;
            collarDrift = -collarDrift;
        }

        boolean held = Math.abs(weight - collar) <= collarHalf;
        if (held) {
            slip = Math.max(0f, slip - dt * 0.55f);
        } else {
            slip += dt * 0.75f;
            if (slip >= 1f) {
                slip = 0f;
                weight = collar;
                weightVel = 0f;
                Sfx.wrenchSlip();
                loseLife("The yoke slipped");
                return;
            }
        }

        // ---- right hand
        wheel = (wheel + sweepNow() * dt) % 360f;
    }

    /** Shortest signed distance from the index mark to the tooth, in degrees. */
    private float offMark() {
        float d = ((wheel % 360f) + 360f) % 360f;
        return d > 180f ? d - 360f : d;
    }

    private void strike() {
        if (resolved) return;
        swings++;

        float swept = sweepNow() * grace();
        float now = offMark();
        boolean hit = Math.abs(now) <= markHalf || (now > 0 && now - swept <= markHalf);

        if (!hit) {
            flashColor = COL_BAD;
            flashTimer = 0.35f;
            Sfx.markBad();
            // A missed tooth is not a life. There are more coming, and the real
            // cost is that you looked away from the weight to take the swing.
            slip = Math.min(0.95f, slip + 0.18f);
            showBanner("Struck air \u2014 mind the weight", theme().dim, 700);
            return;
        }

        struck++;
        progress = struck;
        flashColor = COL_GOOD;
        flashTimer = 0.3f;
        Sfx.pinSet(struck);
        // The yoke complains when the coupling throws the weight.
        Sfx.play(WardSounds.YOKE_STRAIN, 0.50f, 0.92f + struck * 0.05f);

        // The coupling: landing a tooth throws the weight and winds the wheel on.
        jolt = 1f;
        weightVel += (rng.nextBoolean() ? 1f : -1f) * (0.55f + rng.nextFloat() * 0.5f);
        wheel = (wheel + 140f + rng.nextInt(80)) % 360f;

        if (struck >= teeth) {
            succeed(teeth / (float) Math.max(teeth, swings));
        }
    }

    // ------------------------------------------------------------------ input

    /**
     * The left hand's only input. Called from render rather than from a mouse
     * event because the weight has to keep answering the cursor even when the
     * cursor is not moving.
     */
    private void readMouse(double my) {
        int top = contentTop() + 12, bottom = contentBottom() - 20;
        want = Mth.clamp((float) (my - top) / Math.max(1, bottom - top), 0f, 1f);
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button == 0) {
            strike();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKey(int key) {
        if (isActionKey(key)) {
            strike();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ render

    private int columnX() {
        return left() + 62;
    }

    private int trackTop() {
        return contentTop() + 12;
    }

    private int trackBottom() {
        return contentBottom() - 20;
    }

    private int wheelCX() {
        return left() + panelWidth() - 84;
    }

    private int wheelCY() {
        return (contentTop() + contentBottom()) / 2 - 4;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The mouse position is the left hand's only input, and render is the one
        // place it is reliably available on every platform.
        readMouse(mouseY);

        Theme t = theme();
        drawColumn(g);
        drawWheel(g);

        progressDots(wheelCX(), wheelCY() + 74, teeth, struck);
    }

    private void drawColumn(GuiGraphics g) {
        Theme t = theme();
        int x = columnX(), y0 = trackTop(), y1 = trackBottom(), h = y1 - y0;

        p.roundRect(x - 20, y0 - 8, 40, h + 16, t.frameDark);
        well(x - 14, y0 - 4, 28, h + 8);
        p.scanlines(x - 13, y0 - 3, 26, h + 6, Painter.withAlpha(0x000000, 0x22));

        // the collar it has to stay inside
        int cTop = y0 + Math.round((collar - collarHalf) * h);
        int cBot = y0 + Math.round((collar + collarHalf) * h);
        boolean held = Math.abs(weight - collar) <= collarHalf;
        int cCol = held ? COL_GOOD : COL_BAD;
        p.rect(x - 18, cTop, x + 18, cBot, Painter.withAlpha(cCol, 0x22));
        p.rect(x - 20, cTop - 2, x + 20, cTop, Painter.withAlpha(cCol, 0xCC));
        p.rect(x - 20, cBot, x + 20, cBot + 2, Painter.withAlpha(cCol, 0xCC));

        // where the collar was a moment ago, so a feint can be read
        if (ghostFade > 0f && ghostCollar >= 0f) {
            int gTop = y0 + Math.round((ghostCollar - collarHalf) * h);
            int gBot = y0 + Math.round((ghostCollar + collarHalf) * h);
            int a = Math.round(Mth.clamp(ghostFade / 0.55f, 0f, 1f) * 0x66);
            p.rect(x - 18, gTop, x + 18, gBot, Painter.withAlpha(COL_BAD, a / 3));
            p.rect(x - 20, gTop - 1, x + 20, gTop, Painter.withAlpha(COL_BAD, a));
            p.rect(x - 20, gBot, x + 20, gBot + 1, Painter.withAlpha(COL_BAD, a));
        }

        // the counterweight
        int wy = y0 + Math.round(weight * h);
        int shake = (WardConfig.accessibilityDisableShake || WardConfig.accessibilityReduceMotion)
                ? 0 : (jolt > 0f ? Math.round((rng.nextFloat() - 0.5f) * jolt * 5f) : 0);
        p.rect(x - 13 + shake, wy - 7, x + 13 + shake, wy + 7, 0xFF15120E);
        p.rect(x - 12 + shake, wy - 6, x + 12 + shake, wy + 6,
                Painter.mix(t.frame, t.accent, 0.35f) | 0xFF000000);
        p.rect(x - 12 + shake, wy - 6, x + 12 + shake, wy - 4, t.frameLite);
        p.rect(x - 4 + shake, wy - 2, x + 4 + shake, wy + 2, Painter.darken(t.frame, 0.5f));
        // the chain it hangs from
        p.line(x, y0 - 4, x + shake, wy - 7, 2, Painter.withAlpha(t.frame, 0x99));

        // the slip gauge, alongside
        p.rect(x + 24, y0, x + 30, y1, 0xFF0B0A09);
        int fill = Math.round(Mth.clamp(slip, 0f, 1f) * h);
        p.rect(x + 25, y1 - fill, x + 29, y1, slip > 0.6f ? COL_BAD : t.accent);
        smallCentered(g, "slip", x + 27, y1 + 4, t.faint, 0.65f);
        smallCentered(g, crossed ? "collar \u00b7 crossed" : "collar", x, y0 - 12,
                held ? COL_GOOD : COL_BAD, 0.7f);
    }

    private void drawWheel(GuiGraphics g) {
        Theme t = theme();
        int cx = wheelCX(), cy = wheelCY(), r = 46;

        p.disc(cx, cy, r + 8, t.frameDark);
        p.ring(cx, cy, r + 7, r + 3, t.frame);
        p.disc(cx, cy, r, Painter.darken(t.panel, 0.2f) | 0xFF000000);

        // ratchet teeth around the rim
        for (int i = 0; i < 18; i++) {
            double a = Math.toRadians(wheel + i * 20 - 90);
            float ux = (float) Math.cos(a), uy = (float) Math.sin(a);
            p.line(cx + ux * (r - 9), cy + uy * (r - 9), cx + ux * r, cy + uy * r, 2,
                    Painter.withAlpha(t.frame, 0x88));
        }

        // the tooth that counts
        double a = Math.toRadians(wheel - 90);
        float ux = (float) Math.cos(a), uy = (float) Math.sin(a);
        int toothCol = Math.abs(offMark()) <= markHalf ? COL_GOOD : t.accentLite;
        p.line(cx + ux * (r - 16), cy + uy * (r - 16), cx + ux * (r + 2), cy + uy * (r + 2), 4,
                0xFF14120E);
        p.line(cx + ux * (r - 16), cy + uy * (r - 16), cx + ux * (r + 1), cy + uy * (r + 1), 2,
                toothCol);

        // the index the tooth has to meet, with its window drawn to scale
        int half = Math.round(markHalf);
        p.arc(cx, cy, r + 5, -90 - half, -90 + half, 4, Painter.withAlpha(COL_GOOD, 0x55));
        p.rect(cx - 2, cy - r - 12, cx + 2, cy - r + 2, t.accentLite);

        p.disc(cx, cy, 7, t.frameDark);
        p.disc(cx, cy, 5, Painter.mix(t.frame, t.accent, 0.3f) | 0xFF000000);

        if (flashTimer > 0f) {
            p.ring(cx, cy, r + 11, r + 9, Painter.withAlpha(flashColor, Math.round(flashTimer * 0xC0)));
        }
        smallCentered(g, Math.round(sweepNow()) + "\u00b0/s", cx, cy + r + 14, t.faint, 0.7f);
    }

    @Override
    protected String hintText() {
        if (slip > 0.5f) return "The weight \u2014 the weight first";
        if (struck == 0) return "Steer the weight with the mouse \u00b7 click to strike the tooth";
        return "Both hands \u00b7 every tooth you land throws the weight";
    }
}
