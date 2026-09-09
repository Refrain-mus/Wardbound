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
 * The Gyre. A ring of nodes and a needle sweeping round it: strike each node in
 * turn as the needle crosses it.
 *
 * <p>For the first two nodes that is all it is, and it is easy. Then the ring
 * wakes up. Every node still standing begins to orbit, and it keeps gathering
 * speed with each one you take, so the lock you finish is not the lock you
 * started. Two moving things have to line up instead of one, and the last node
 * on a seven node ring is a genuinely hard shot.
 *
 * <p>The escalation is the point. A lock that is hard from the first input just
 * filters people out; a lock that hands you two free hits and then accelerates
 * makes you feel it getting away from you, which is a different and better kind
 * of difficult.
 *
 * <p>Quirks a chest may carry:
 * <ul>
 *   <li>0 counter-spin - the ring turns against the needle instead of with it.
 *       Closing speed roughly doubles, so the window in real time halves.</li>
 *   <li>1 false nodes - two of the marks on the ring are not part of the lock at
 *       all. They look identical and they orbit identically. Striking one costs
 *       a life. The only tell is the order: real nodes are numbered, and a
 *       false one never carries the number you are looking for.</li>
 *   <li>2 wandering needle - the needle will not hold a speed, drifting between
 *       half and half again of it, so the rhythm of one lap does not predict
 *       the next.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class GyreScreen extends BaseMinigameScreen {

    /** Nodes struck for free before the ring starts moving. */
    private static final int GRACE_HITS = 2;
    /** Chance the ring simply never wakes up, and the lock stays a pure sweep. */
    private static final float STILL_RING = 0.30f;

    private final boolean counterSpin;
    private final boolean falseNodes;
    private final boolean wanderingNeedle;

    private final int nodes;
    private final int decoys;
    /** Resting angle of every mark on the ring, real ones first. */
    private final float[] baseAngle;
    private final float arc;

    private final float needleSpeed;
    private final float spinPerHit;
    /** Seeded separately so gameplay reversals remain reproducible for this ward. */
    private final Random swingRng;

    private float needle;
    private float orbit;
    /**
     * Rendered angles, chased toward the simulated ones.
     *
     * <p>The simulation ticks at whatever rate catchUp feeds it; the render does
     * not. Drawing the last simulated angle directly is what made a fast sweep
     * look like it was skipping rather than turning.
     */
    private float drawNeedle;
    private float drawOrbit;
    private float wander;
    /** Builds while a node is left standing, and resets when one is taken. */
    private float momentum;
    /** Which node wakes the ring. Not always the third. */
    private final int wakeAt;
    /** +1 normally; dips through zero and back when the ring loses its footing. */
    private float swing = 1f;
    private float swingTimer;

    private int hits;
    private int attempts;

    private float flashTimer;
    private int flashColor = COL_GOOD;
    private float ringPulse;

    public GyreScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.gyre"), msg);

        boolean[] v = pickVariants(3, 77);
        counterSpin = v[0];
        falseNodes = v[1];
        wanderingNeedle = v[2];
        if (counterSpin) modifierLabels.add("counter-spin");
        if (falseNodes) modifierLabels.add("false nodes");
        if (wanderingNeedle) modifierLabels.add("wandering needle");

        int baseNodes = 6 + Math.round((value / 100f) * 3f * difficulty);
        this.nodes = wobble(baseNodes, 2, 78, 5, 9);
        this.decoys = falseNodes ? 2 : 0;

        Random rng = new Random(seed ^ 0x37C0FFEEL);
        this.swingRng = new Random(seed ^ 0x6A09E667F3BCC909L);

        // Marks are spread round the ring with jitter rather than evenly: an
        // even ring has one rhythm and you can play it with your eyes shut.
        int marks = nodes + decoys;
        this.baseAngle = new float[marks];
        float step = 360f / marks;
        for (int i = 0; i < marks; i++) {
            baseAngle[i] = i * step + (rng.nextFloat() - 0.5f) * step * 0.45f;
        }
        // Shuffle which slots the real nodes occupy, so decoys are not simply
        // the last two positions on the ring.
        for (int i = marks - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            float t = baseAngle[i];
            baseAngle[i] = baseAngle[j];
            baseAngle[j] = t;
        }

        // On the square root of difficulty, not on difficulty. The needle speed
        // and the ring speed both already scale with it, so a linear window was
        // being squeezed from three directions at once and the last node of a
        // seven node ring in the End came out at 36ms, which is a coin flip
        // rather than a shot.
        this.arc = Mth.clamp(15f / (float) Math.sqrt(Math.max(0.5f, difficulty)), 9f, 22f);
        // The needle used to be fast on every single lock, which made every
        // gyre feel the same before the ring even woke up. The spread is now
        // wide: some of these are a slow, careful sweep and some are a blur, and
        // you have to read which one you have got.
        this.needleSpeed = (95f + value * 0.55f) * difficulty
                * (0.45f + seedVariance(79) * 1.15f)
                * (rng.nextBoolean() ? 1f : -1f);
        // Momentum: the ring is still at first, then gains a fixed step of speed
        // for every node taken past the grace hits.
        // Momentum is deliberately NOT scaled by difficulty: the needle already
        // is, and stacking both turns the back half of a hard ring into noise.
        // Not every ring turns. A lock that always escalates at the same node
        // is a script; one that sometimes just stays still is a lock you have to
        // watch rather than one you can plan against.
        this.spinPerHit = (seedVariance(81) < STILL_RING ? 0f : 1f)
                * (26f + value * 0.16f) * 0.7f * (counterSpin ? -1f : 1f)
                * Math.signum(needleSpeed);

        // Somewhere in the first half, so the free stretch is a different length
        // every time and you cannot count on two easy shots.
        this.wakeAt = 1 + Math.round(seedVariance(83) * Math.max(1, nodes / 2f - 1f));

        this.hits = Mth.clamp(progress, 0, nodes - 1);
        this.attempts = hits;
        this.orbit = 0f;
        this.needle = rng.nextInt(360);
        this.drawNeedle = needle;
        this.drawOrbit = orbit;
    }

    /** Short. A ward you can outwait is not a ward. */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.78f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.gyre();
    }

    @Override
    protected int panelHeight() {
        return 270;
    }

    // ------------------------------------------------------------------ simulation

    /**
     * Degrees per second the ring is turning right now.
     *
     * <p>Was a flat step per hit, which meant the ring reached its speed and then
     * simply held it: the back half of a long ring was one shot repeated. It now
     * accelerates between hits as well, and above a certain speed it starts to
     * lose its footing and swing back the other way. The reversal is the part
     * worth having - a ring that only ever goes one way is a ring you can lead.
     */
    private float spinNow() {
        int past = hits - wakeAt;
        if (past <= 0) return 0f;
        float base = spinPerHit * past * (1f + momentum * 0.55f);
        return base * swing;
    }

    private float needleNow() {
        if (!wanderingNeedle) return needleSpeed;
        return needleSpeed * (1f + 0.5f * Mth.sin(wander));
    }

    @Override
    protected void step(float dt) {
        if (flashTimer > 0f) flashTimer -= dt;
        if (ringPulse > 0f) ringPulse -= dt * 2.2f;
        if (resolved) return;

        wander += dt * 1.7f;

        int past = hits - wakeAt;
        if (past > 0) {
            // Standing still is not free: every second you spend lining up a
            // shot makes the shot harder.
            momentum = Math.min(1.6f, momentum + dt * 0.22f);

            swingTimer -= dt;
            if (swingTimer <= 0f) {
                // Reversals get more likely the faster it is going, so the ring
                // becomes genuinely unreliable exactly when it matters.
                swingTimer = 2.4f - Math.min(1.5f, momentum);
                if (swingRng.nextFloat() < 0.35f + past * 0.06f) swing = -swing;
            }
        }

        needle = wrap(needle + needleNow() * dt);
        orbit = wrap(orbit + spinNow() * dt);

        // Chase the drawn angles toward the real ones. The simulation ticks at
        // whatever rate catchUp feeds it; the render does not, and snapping the
        // needle to the last simulated angle is what made a fast sweep look like
        // it was skipping rather than turning.
        drawNeedle = wrap(drawNeedle + delta(drawNeedle, needle) * Math.min(1f, dt * 22f));
        drawOrbit = wrap(drawOrbit + delta(drawOrbit, orbit) * Math.min(1f, dt * 22f));
    }

    private static float wrap(float deg) {
        float d = deg % 360f;
        return d < 0 ? d + 360f : d;
    }

    /** Shortest signed distance from a to b, in degrees. */
    private static float delta(float a, float b) {
        float d = wrap(b - a);
        return d > 180f ? d - 360f : d;
    }

    private float angleOf(int mark) {
        return wrap(baseAngle[mark] + orbit);
    }

    /** Where a mark is drawn, as opposed to where it is. */
    private float drawAngleOf(int mark) {
        return wrap(baseAngle[mark] + drawOrbit);
    }

    /** Which mark the needle is currently over, or -1. */
    private int markUnderNeedle() {
        int best = -1;
        float bestGap = arc;
        for (int i = 0; i < baseAngle.length; i++) {
            float gap = Math.abs(delta(needle, angleOf(i)));
            if (gap <= bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        if (best >= 0) return best;

        // Nothing under it now, but the needle may have swept across a mark
        // inside the grace window. Judged the same way every other lock here
        // judges a press.
        float swept = Math.abs(needleNow()) * grace();
        for (int i = 0; i < baseAngle.length; i++) {
            float gap = Math.abs(delta(needle, angleOf(i)));
            if (gap <= arc + swept) return i;
        }
        return -1;
    }

    private void strike() {
        if (resolved) return;
        attempts++;

        int mark = markUnderNeedle();

        if (mark < 0) {
            Sfx.markBad();
            flashColor = COL_BAD;
            flashTimer = 0.4f;
            loseLife("Nothing there");
            return;
        }
        if (mark >= nodes) {
            // A decoy. It never carried a number; you struck it anyway.
            Sfx.play(WardSounds.OVERSET, 0.6f, 1.25f);
            flashColor = COL_BAD;
            flashTimer = 0.5f;
            loseLife("A false node \u2014 it was never part of the lock");
            return;
        }
        if (mark != hits) {
            Sfx.markBad();
            flashColor = COL_BAD;
            flashTimer = 0.4f;
            loseLife("Out of order \u2014 " + (hits + 1) + " was next");
            return;
        }

        hits++;
        progress = hits;
        momentum = 0f;        // taking a node steadies the ring, briefly
        swingTimer = 1.2f;
        flashColor = COL_GOOD;
        flashTimer = 0.35f;
        Sfx.mark(hits);

        if (hits == wakeAt && hits < nodes && spinPerHit != 0f) {
            // The moment the lock stops being easy, announced rather than sprung.
            ringPulse = 1f;
            Sfx.plugTurn(0.8f);
            showBanner("The ring is turning", theme().accentLite, 900);
        } else if (hits > wakeAt && hits < nodes && spinPerHit != 0f) {
            ringPulse = 0.6f;
            Sfx.plugTurn(1.0f + (hits - GRACE_HITS) * 0.09f);
        }

        if (hits >= nodes) {
            succeed(nodes / (float) Math.max(nodes, attempts));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onClick(double mx, double my, int button) {
        // Only on the ring. Clicking anywhere on the panel counted as a swing,
        // which is why a press well outside the dial still registered.
        int cx = ringCX(), cy = ringCY(), r = ringR();
        double dx = mx - cx, dy = my - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        // Anywhere on the plate counts. Restricting it to a narrow band around
        // the nodes made the lock feel broken rather than precise: you are
        // timing a needle, not aiming at a target, so where the cursor happens
        // to be is not part of the skill.
        if (d > r + 24) return false;
        strike();
        return true;
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

    private int ringCX() {
        return centerX();
    }

    private int ringCY() {
        return (contentTop() + contentBottom()) / 2 - 14;
    }

    private int ringR() {
        return Math.min(70, (contentBottom() - contentTop()) / 2 - 24);
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int cx = ringCX(), cy = ringCY(), r = ringR();

        // ---- the plate the ring is cut into
        //
        // Was a plain disc with a plain ring on it, which next to the keyway's
        // cutaway or the vessel's bowl looked like a placeholder. This is an
        // astrolabe: a cast plate with four lugs, an engraved bezel, a graduated
        // scale and a worn face. None of it moves, and all of it is the reason
        // the thing reads as an instrument somebody owned.
        p.disc(cx, cy + 3, r + 24, Painter.withAlpha(0x000000, 0x55));
        p.disc(cx, cy, r + 22, Painter.darken(t.panel, 0.10f) | 0xFF000000);
        p.dither(cx - r - 22, cy - r - 22, (r + 22) * 2, (r + 22) * 2,
                Painter.withAlpha(t.frame, 0x12), 3, 0);

        // lugs at the quarters, where a plate like this would be bolted down
        for (int q = 0; q < 4; q++) {
            double a = Math.toRadians(q * 90 + 45);
            float lx = (float) Math.cos(a), ly = (float) Math.sin(a);
            int lugX = cx + Math.round(lx * (r + 18));
            int lugY = cy + Math.round(ly * (r + 18));
            p.disc(lugX, lugY, 6, t.frameDark);
            p.disc(lugX, lugY, 4, t.frame);
            p.disc(lugX, lugY, 2, Painter.darken(t.frame, 0.5f));
        }

        // the bezel: two rings with a graduated scale bitten into the outer one
        p.ring(cx, cy, r + 17, r + 10, t.frameDark);
        p.ring(cx, cy, r + 16, r + 14, t.frameLite);
        p.ring(cx, cy, r + 12, r + 10, Painter.darken(t.frame, 0.45f));
        for (int i = 0; i < 72; i++) {
            double a = Math.toRadians(i * 5 - 90);
            float gx = (float) Math.cos(a), gy = (float) Math.sin(a);
            boolean major = i % 6 == 0;
            float inner = major ? r + 10 : r + 12.5f;
            p.line(cx + gx * inner, cy + gy * inner,
                    cx + gx * (r + 16), cy + gy * (r + 16),
                    major ? 2 : 1,
                    Painter.withAlpha(t.frameLite, major ? 0xAA : 0x55));
        }

        // the face itself, aged unevenly
        p.disc(cx, cy, r + 9, Painter.darken(t.panel, 0.28f) | 0xFF000000);
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(i * 67 + 20);
            p.disc(cx + Math.round((float) Math.cos(a) * r * 0.55f),
                    cy + Math.round((float) Math.sin(a) * r * 0.55f),
                    9 + i * 2, Painter.withAlpha(t.frame, 0x0A));
        }
        // engraved chords across the face, the way an astrolabe is ruled
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 30 + 15);
            float ux = (float) Math.cos(a), uy = (float) Math.sin(a);
            p.line(cx - ux * (r - 2), cy - uy * (r - 2),
                    cx + ux * (r - 2), cy + uy * (r - 2), 1,
                    Painter.withAlpha(t.frame, 0x18));
        }

        // ---- the track
        int trackCol = ringPulse > 0f
                ? Painter.mix(t.frame, t.accentLite, Mth.clamp(ringPulse, 0f, 1f))
                : t.frame;
        p.ring(cx, cy, r + 4, r - 4, Painter.withAlpha(trackCol, 0x55));
        p.ring(cx, cy, r + 1, r - 1, Painter.withAlpha(trackCol, 0xAA));

        // ---- teeth cut into the track, so the ring visibly turns rather than
        // merely having its contents move
        for (int i = 0; i < 48; i++) {
            double a = Math.toRadians(drawOrbit + i * 7.5);
            float tx = (float) Math.cos(a), ty = (float) Math.sin(a);
            p.rect(cx + tx * (r + 5) - 1, cy + ty * (r + 5) - 1,
                    cx + tx * (r + 8) + 1, cy + ty * (r + 8) + 1,
                    Painter.withAlpha(t.frameLite, i % 2 == 0 ? 0x55 : 0x22));
        }

        // ---- the needle, with a wake behind it so speed is legible at a glance
        double rad = Math.toRadians(drawNeedle - 90);
        float nx = (float) Math.cos(rad), ny = (float) Math.sin(rad);
        for (int k = 1; k <= 6; k++) {
            double back = Math.toRadians(drawNeedle - 90 - Math.signum(needleNow()) * k * 3.5);
            float bx = (float) Math.cos(back), by = (float) Math.sin(back);
            p.line(cx + bx * 20, cy + by * 20, cx + bx * (r + 2), cy + by * (r + 2), 1,
                    Painter.withAlpha(t.accent, 0x40 - k * 8));
        }
        p.line(cx + nx * 12, cy + ny * 12, cx + nx * (r + 8), cy + ny * (r + 8), 3, 0xFF15130F);
        p.line(cx + nx * 12, cy + ny * 12, cx + nx * (r + 7), cy + ny * (r + 7), 1, t.accentLite);
        p.disc(cx, cy, 8, t.frameDark);
        p.disc(cx, cy, 6, Painter.mix(t.frame, t.accent, 0.3f) | 0xFF000000);

        // ---- the marks
        for (int i = 0; i < baseAngle.length; i++) {
            drawMark(g, i, cx, cy, r);
        }

        // ---- how fast the ring is going, as a readable number rather than a feel
        float spin = Math.abs(spinNow());
        String state = spinNow() == 0f
                ? "ring still"
                : "ring at " + Math.round(spin) + "\u00b0/s "
                        + (spinNow() * needleSpeed < 0 ? "against you" : "with you");
        smallCentered(g, state, cx, cy + r + 18,
                spin > 0f ? Painter.mix(theme().dim, COL_BAD, Mth.clamp(spin / 160f, 0f, 1f))
                        : theme().dim, 0.8f);

        progressDots(cx, cy + r + 31, nodes, hits);
    }

    private void drawMark(GuiGraphics g, int i, int cx, int cy, int r) {
        Theme t = theme();
        float a = drawAngleOf(i);
        double rad = Math.toRadians(a - 90);
        int x = cx + (int) Math.round(Math.cos(rad) * r);
        int y = cy + (int) Math.round(Math.sin(rad) * r);

        boolean taken = i < hits;
        boolean next = i == hits;
        // A decoy is drawn exactly like an untaken real node. It has to be: the
        // whole point is that the ring does not tell you, the numbering does.
        boolean decoy = i >= nodes;

        int col = taken ? COL_GOOD : (next ? t.accentLite : t.accent);
        if (taken) {
            p.disc(x, y, 7, Painter.withAlpha(COL_GOOD, 0x33));
            p.ring(x, y, 6, 4, COL_GOOD);
            return;
        }

        p.disc(x, y, 7, t.frameDark);
        p.disc(x, y, 5, Painter.darken(col, 0.55f));
        p.ring(x, y, 5, 4, col);

        if (next) {
            int glow = Painter.withAlpha(t.accentLite, 0x70 + Math.round(breath(1.1f) * 0x60));
            p.ring(x, y, 10, 9, glow);
        }
        if (flashTimer > 0f && (next || decoy)) {
            p.ring(x, y, 9, 8, Painter.withAlpha(flashColor, Math.round(flashTimer * 0x80)));
        }

        // Numbers only on nodes you have already taken and on the one you want.
        //
        // Numbering every real node meant the decoys announced themselves by
        // being the blank ones: you never had to judge anything, you just
        // avoided the marks without numbers. Now the ring shows you which node
        // is next and nothing else, so an unnumbered mark might be the decoy or
        // it might simply be node five, and striking the wrong one is possible
        // again.
        if (next) {
            smallCentered(g, String.valueOf(i + 1), x, y - 3, t.text, 0.7f);
        }
    }

    @Override
    protected String hintText() {
        if (spinNow() == 0f) {
            return "Strike node " + (hits + 1) + " as the needle crosses it";
        }
        if (falseNodes) {
            return "Node " + (hits + 1) + " \u00b7 unnumbered marks are not part of the lock";
        }
        return "Node " + (hits + 1) + " \u00b7 the ring is moving now";
    }
}
