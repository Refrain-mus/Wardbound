package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * Cipher rings. Four or five lettered rings turn inside one another at different speeds; each ring
 * has its own index. Work from the outside in and lock each ring the moment its marked sigil
 * passes under the index.
 *
 * <p>Quirks:
 * <ul>
 *   <li>0 winding - rings that are still turning speed up the longer you leave them,
 *       on top of the kick every ring gets when one of its neighbours bites down</li>
 *   <li>1 veiled - a ring's index hides its wanted sigil every couple of seconds, so you have to
 *       hold it in your head</li>
 *   <li>2 counter-turn - each ring turns against the one outside it</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class CipherScreen extends BaseMinigameScreen {

    private static final int MAX_RINGS = 5;
    private static final int[] RADIUS = {66, 52, 39, 27, 16};
    private static final int[] BAND = {13, 12, 11, 10, 9};

    /** Where ring zero's index sits; the others are offset from it. */
    private static final float INDEX_ANGLE = 270f;

    private final boolean winding;
    private final boolean veiled;
    private final boolean counter;
    private final int ringCount;

    private final int[] slots = new int[MAX_RINGS];
    private final int[][] ringSigils = new int[MAX_RINGS][];
    private final int[] target = new int[MAX_RINGS];
    private final float[] angle = new float[MAX_RINGS];
    private final float[] speed = new float[MAX_RINGS];
    private final float[] dir = new float[MAX_RINGS];
    private final boolean[] locked = new boolean[MAX_RINGS];
    /** Field rather than a constructor local: the kick on lock-in needs it too. */
    private final Random rng;
    /**
     * Where each ring's index mark sits.
     *
     * <p>All three used to be at twelve o'clock, so the lock was one reading
     * taken three times: the same glance at the same place, waiting for three
     * different rings. Giving each ring its own mark means three separate places
     * to watch, which is what turns it from a queue into a lock.
     */
    private final float[] indexAt = new float[MAX_RINGS];
    private final float[] lockPop = new float[MAX_RINGS];

    private final float tolerance;
    private float elapsed;
    private float veilPhase;
    private int attempts;

    public CipherScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.cipher"), msg);

        boolean[] v = pickVariants(3, 5);
        winding = v[0];
        veiled = v[1];
        counter = v[2];
        if (winding) modifierLabels.add("winding rings");
        if (veiled) modifierLabels.add("veiled mark");
        if (counter) modifierLabels.add("counter-turn");

        // Teach the plate in layers. A first-time Cipher is three rings with no quirk stack;
        // after a few successful solves it becomes the familiar four-ring lock, and only a
        // practiced player can roll the fivefold form from an ordinary chest.
        boolean richPlate = familiarity >= 4 || value >= 68 || difficulty > 1.18f;
        boolean fivefold = advancedNarrativeWard() || (masteryTier >= 2 && richPlate
                && (masteryTier >= 3 || seedVariance(73) < 0.58f));
        ringCount = masteryTier <= 0 && !advancedNarrativeWard() ? 3 : (fivefold ? 5 : 4);
        modifierLabels.add(ringCount == 5 ? "fivefold cipher" : ringCount == 4 ? "fourfold cipher" : "threefold cipher");

        rng = new Random(seed ^ 0x5EED);
        for (int i = 0; i < ringCount; i++) {
            // No ring keeps twelve o'clock. The first one used to, which meant
            // the plate always had one familiar mark on it and the eye went
            // there first every time.
            indexAt[i] = rng.nextInt(360);
        }
        int extra = wobble(Math.round(Mth.clamp(value / 45f, 0f, 2f)), 1, 24, 0, 3);
        for (int i = 0; i < ringCount; i++) {
            // One ring in three carries an extra slot, so the rings are not
            // three sizes of the same thing.
            slots[i] = Math.max(5, 7 + extra - i / 2 + (rng.nextFloat() < 0.34f ? 1 : 0));
            ringSigils[i] = new int[slots[i]];
            // every ring gets a distinct set of sigils, so the wanted one is always findable
            boolean[] used = new boolean[Sigils.TILE.length];
            for (int s = 0; s < slots[i]; s++) {
                int pick;
                int guard = 0;
                do {
                    pick = rng.nextInt(Sigils.TILE.length);
                } while (used[pick] && ++guard < 40);
                used[pick] = true;
                ringSigils[i][s] = pick;
            }
            target[i] = rng.nextInt(slots[i]);
            // Was a free roll, which lands on or near the answer often enough
            // that a ring could be locked in the first half second without
            // reading anything. Every ring now starts at least a third of a
            // turn away from where it needs to be.
            float away = wrap(indexAt[i] - slotAngle(i, target[i]));
            angle[i] = wrap(away + 120f + rng.nextFloat() * 120f);
            float base = (62f + value * 0.42f) * difficulty * (0.85f + seedVariance(40 + i) * 0.45f);
            speed[i] = base * (1f + i * 0.28f);
            dir[i] = counter ? (i % 2 == 0 ? 1f : -1f) : (rng.nextBoolean() ? 1f : -1f);
        }

        this.tolerance = Mth.clamp(7.5f / difficulty, 4.5f, 10f);

        // rings already locked in a previous attempt stay locked
        for (int i = 0; i < ringCount && i < progress; i++) {
            locked[i] = true;
            lockPop[i] = 1f;
            angle[i] = wrap(indexAt[i] - slotAngle(i, target[i]));
        }
    }

    /**
     * Still the shortest lock in the mod, and now it has teeth of its own rather
     * than borrowing all of them from the clock.
     */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * (ringCount >= 5 ? 0.62f : ringCount == 4 ? 0.56f : 0.64f);
    }

    @Override
    protected Theme createTheme() {
        return Theme.bone();
    }

    @Override
    protected int panelHeight() {
        return 276;
    }

    private static float wrap(float a) {
        a %= 360f;
        return a < 0 ? a + 360f : a;
    }

    /** Angle of a slot relative to the ring's own rotation. */
    private float slotAngle(int ring, int slot) {
        return slot * (360f / slots[ring]);
    }

    /** Where a slot currently sits on screen. */
    private float slotWorldAngle(int ring, int slot) {
        return wrap(angle[ring] + slotAngle(ring, slot));
    }

    private int activeRing() {
        for (int i = 0; i < ringCount; i++) if (!locked[i]) return i;
        return -1;
    }

    @Override
    protected void step(float dt) {
        elapsed += dt;
        veilPhase += dt;
        for (int i = 0; i < ringCount; i++) {
            if (locked[i]) {
                if (lockPop[i] < 1f) lockPop[i] = Math.min(1f, lockPop[i] + dt * 6f);
                continue;
            }
            float s = speed[i];
            if (winding) s *= 1f + Math.min(0.9f, elapsed * 0.05f);
            angle[i] = wrap(angle[i] + dir[i] * s * dt);
        }
    }

    private static float distanceToSweep(float target, float from, float to) {
        float sweep = Mth.degreesDifference(from, to);
        float dFrom = Mth.degreesDifference(from, target);
        if (Math.abs(sweep) > 0.0001f) {
            float t = dFrom / sweep;
            if (t >= 0f && t <= 1f) return 0f;
        }
        return Math.min(Math.abs(dFrom), Math.abs(Mth.degreesDifference(to, target)));
    }

    private void tryLock() {
        int r = activeRing();
        if (r < 0) return;
        attempts++;

        float s = speed[r] * (winding ? 1f + Math.min(0.9f, elapsed * 0.05f) : 1f);
        float travelled = dir[r] * s * grace();
        float now = slotWorldAngle(r, target[r]);
        float from = now - travelled;

        float d = distanceToSweep(indexAt[r], from, now);
        if (d > tolerance) {
            loseLife("The ring bit down out of line");
            return;
        }

        locked[r] = true;
        lockPop[r] = 0f;
        angle[r] = wrap(indexAt[r] - slotAngle(r, target[r]));
        elapsed = 0f;
        Sfx.plugTurn(0.92f + r * 0.14f);
        progress = Math.max(progress, r + 1);

        // Biting one ring down shoves the rest.
        //
        // This was the easiest lock in the mod because the rings were entirely
        // independent: three separate, unhurried timing presses with nothing
        // connecting them. Now every ring you lock kicks the remaining ones - a
        // shove of speed, a shift in phase, and sometimes a reversal - so the
        // read you were building on ring three is worthless the moment ring two
        // goes in, and the lock has to be solved from the outside inwards
        // rather than three times in parallel.
        for (int i = 0; i < ringCount; i++) {
            if (locked[i]) continue;
            speed[i] *= 1.16f + rng.nextFloat() * 0.20f;
            angle[i] = wrap(angle[i] + (rng.nextFloat() - 0.5f) * 70f);
            if (rng.nextFloat() < 0.45f) dir[i] = -dir[i];
        }

        int cx = centerX();
        int cy = contentCenterY();
        burst(cx, cy - RADIUS[r], COL_GOOD);

        if (activeRing() < 0) {
            succeed(attempts <= ringCount ? 1f : (float) ringCount / attempts);
        }
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button == 0) {
            tryLock();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKey(int key) {
        if (isActionKey(key)) {
            tryLock();
            return true;
        }
        return false;
    }

    private boolean markVisible() {
        return !veiled || (veilPhase % 2.4f) < 1.5f;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int cx = centerX();
        int cy = contentCenterY();
        int act = activeRing();

        // backing plate
        p.disc(cx, cy, RADIUS[0] + 14, t.frameDark);
        p.ring(cx, cy, RADIUS[0] + 13, RADIUS[0] + 10, t.frame);
        p.ring(cx, cy, RADIUS[0] + 12, RADIUS[0] + 11, t.frameLite);
        p.disc(cx, cy, RADIUS[0] + 9, t.well);

        for (int i = 0; i < ringCount; i++) {
            int r = RADIUS[i];
            int half = BAND[i] / 2;
            boolean isActive = i == act;

            // the ring band
            int bandCol = locked[i] ? Painter.withAlpha(COL_GOOD, 0x2E)
                    : (isActive ? Painter.withAlpha(t.accent, 0x26) : Painter.withAlpha(t.frame, 0x18));
            p.ring(cx, cy, r + half, r - half, bandCol);
            p.ring(cx, cy, r + half, r + half - 1, Painter.withAlpha(t.frame, locked[i] ? 0xCC : 0x77));
            p.ring(cx, cy, r - half + 1, r - half, Painter.withAlpha(t.frame, locked[i] ? 0xCC : 0x55));

            for (int s = 0; s < slots[i]; s++) {
                float a = slotWorldAngle(i, s);
                double rad = Math.toRadians(a);
                int px = cx + (int) Math.round(Math.cos(rad) * r);
                int py = cy + (int) Math.round(Math.sin(rad) * r);
                boolean isTarget = s == target[i];

                int col;
                int halfCol;
                if (locked[i]) {
                    col = isTarget ? COL_GOOD : Painter.withAlpha(t.faint, 0x88);
                    halfCol = isTarget ? Painter.withAlpha(COL_GOOD, 0x77) : 0;
                } else if (isTarget && isActive && markVisible()) {
                    col = t.accentLite;
                    halfCol = Painter.withAlpha(t.accent, 0xCC);
                    p.disc(px, py, 11, Painter.withAlpha(t.accent, 0x22));
                } else {
                    col = isActive ? t.dim : Painter.withAlpha(t.faint, 0x99);
                    halfCol = 0;
                }
                Sigils.drawCentered(p, Sigils.TILE[ringSigils[i][s]], px, py, 1, col, halfCol,
                        Painter.withAlpha(0x000000, 0x66));
            }

            if (locked[i]) {
                // a locked ring gets a set of teeth so it reads as bitten down
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    int px = cx + (int) Math.round(Math.cos(rad) * (r + half));
                    int py = cy + (int) Math.round(Math.sin(rad) * (r + half));
                    p.rect(px - 1, py - 1, px + 1, py + 1, Painter.withAlpha(COL_GOOD, 0x99));
                }
                int pop = Math.round((1f - lockPop[i]) * 10f);
                if (pop > 0) p.ringThick(cx, cy, r + half + pop, 2,
                        Painter.withAlpha(COL_GOOD, Math.round(lockPop[i] * 0x80)));
            }
        }

        // hub
        p.disc(cx, cy, 14, t.frameDark);
        p.disc(cx, cy, 12, t.frame);
        Sigils.drawCentered(p, Sigils.TILE[4], cx, cy, 2, t.panelDark,
                Painter.withAlpha(t.frameLite, 0x66), 0);

        // Every ring's index, drawn on its own ring rather than one shared wedge
        // at the top. The rings still to be set are marked faintly, so the whole
        // plate reads as an instrument with three separate readings on it.
        for (int i = 0; i < ringCount; i++) {
            if (locked[i] && i != act) continue;
            boolean live = i == act;
            int markR = RADIUS[i] + BAND[i] / 2 + 4;
            double rad = Math.toRadians(indexAt[i]);
            float ux = (float) Math.cos(rad), uy = (float) Math.sin(rad);
            int mCol = live ? t.accentLite : Painter.withAlpha(t.frameLite, 0x66);
            for (int k = 0; k < (live ? 8 : 5); k++) {
                int w = (live ? 5 : 3) - k / 2;
                float px = cx + ux * (markR + 10 - k);
                float py = cy + uy * (markR + 10 - k);
                p.rect(px - w, py - w, px + w, py + w, mCol);
            }
            p.line(cx + ux * (markR - 2), cy + uy * (markR - 2),
                    cx + ux * (markR + 3), cy + uy * (markR + 3), 2, mCol);
        }

        // tolerance whiskers, so the window is something you can see rather than guess
        if (act >= 0) {
            int ar = RADIUS[act];
            for (int side = -1; side <= 1; side += 2) {
                double rad = Math.toRadians(indexAt[act] + side * tolerance);
                int px = cx + (int) Math.round(Math.cos(rad) * (ar + BAND[act] / 2 + 3));
                int py = cy + (int) Math.round(Math.sin(rad) * (ar + BAND[act] / 2 + 3));
                p.rect(px - 1, py - 1, px + 1, py + 1, Painter.withAlpha(t.accent, 0xAA));
            }
        }

        // Keep the physical ring feedback, but also leave an unambiguous
        // persistent ledger of what has already been solved. This matches the
        // readable "set stays set" feedback of the drum/keyway rather than
        // asking the player to infer progress from ring colour alone.
        int set = act < 0 ? ringCount : act;
        progressDots(cx, cy + RADIUS[0] + 25, ringCount, set);
    }

    @Override
    protected String hintText() {
        int act = activeRing();
        if (act < 0) return "The cipher reads true";
        String which = switch (act) {
            case 0 -> "outer";
            case 1 -> ringCount == 5 ? "outer-middle" : "middle-outer";
            case 2 -> "middle";
            case 3 -> ringCount == 5 ? "inner-middle" : "inner";
            default -> "inner";
        };
        return "Lock the " + which + " ring when its lit sigil reaches the mark";
    }
}
