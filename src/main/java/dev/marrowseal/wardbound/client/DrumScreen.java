package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * Tumbler lock. The pick sweeps the drum, you set each pin as it passes.
 *
 * <p>Quirks a chest may carry:
 * <ul>
 *   <li>0 reversing - the drum turns the other way after <b>every</b> click, and it does it with
 *       weight: the dial decelerates, hangs at a standstill for a moment, then winds back up the
 *       other way. Timing the pause is most of the game when this quirk is on.</li>
 *   <li>1 accelerating - the drum winds up the longer you take</li>
 *   <li>2 decoy - one of the notches is a false pin and will jam the lock</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class DrumScreen extends BaseMinigameScreen {

    /** How long a full direction change takes, in seconds. */
    private static final float TURN_TIME = 0.34f;

    private final boolean reversing;
    private final boolean accelerating;
    private final boolean decoy;

    private final float[] pinAngles;
    private final boolean[] pinHit;
    private final boolean[] pinFake;
    private final boolean[] pinRevealed;
    private final float[] pinPop;
    private final int slotCount;
    private final int realPins;
    private final int radius;

    private final float tolerance;
    private final float baseSpeed;

    private float angle;

    /** Signed speed factor. Eased through zero on a flip, which is where the momentum comes from. */
    private float dirCurrent = 1f;
    private float dirTarget = 1f;
    private float dirFrom = 1f;
    private float turnTimer;

    private float elapsed;
    private float flashTimer;
    private int flashSlot = -1;
    private float kickTimer;

    private int hits;
    private int attempts;

    public DrumScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.drum"), msg);

        boolean[] v = pickVariants(3, 1);
        reversing = v[0];
        accelerating = v[1];
        decoy = v[2];
        if (reversing) modifierLabels.add("reversing drum");
        if (accelerating) modifierLabels.add("wind-up spring");
        if (decoy) modifierLabels.add("false pin");

        int minPins = Math.max(2, WardConfig.minPins);
        int maxPins = Math.max(minPins, WardConfig.maxPins);
        // Worth sets the baseline, the chest's own seed moves it by up to two either way, so two
        // equally rich chests in the same dimension do not hand you the same drum twice.
        int basePins = minPins + Math.round((value / 100f) * (maxPins - minPins) * difficulty);
        this.realPins = wobble(basePins, 2, 17, minPins, maxPins);
        this.slotCount = realPins + (decoy ? 1 : 0);
        this.radius = Math.min(58, 50 + Math.max(0, slotCount - 6) * 2);

        // A touch more generous than before; combined with the swept hit test below this is what
        // makes a pin land when you press on the beat instead of a frame early.
        this.tolerance = Mth.clamp(8.5f / difficulty, 4.5f, 11f);

        float variance = 0.78f + seedVariance(1) * 0.5f;
        this.baseSpeed = (78f + value * 0.85f) * difficulty * variance;
        this.dirCurrent = this.dirTarget = this.dirFrom = seedVariance(2) < 0.5f ? 1f : -1f;
        this.angle = seedVariance(3) * 360f;

        this.pinAngles = new float[slotCount];
        this.pinHit = new boolean[slotCount];
        this.pinFake = new boolean[slotCount];
        this.pinRevealed = new boolean[slotCount];
        this.pinPop = new float[slotCount];

        // Pins are seated by walking the rim and spending a random share of the slack at each
        // step, then the whole ring is rotated. Even spacing was the old giveaway: you learned
        // where the next pin would be after two chests.
        Random rng = new Random(seed);
        float minGap = tolerance * 2f + 12f;
        float slack = Math.max(0f, 360f - minGap * slotCount);
        float[] share = new float[slotCount];
        float total = 0f;
        for (int i = 0; i < slotCount; i++) {
            share[i] = 0.35f + rng.nextFloat();
            total += share[i];
        }
        float offset = rng.nextFloat() * 360f;
        float walk = 0f;
        for (int i = 0; i < slotCount; i++) {
            pinAngles[i] = (offset + walk) % 360f;
            walk += minGap + slack * (share[i] / total);
        }
        if (decoy) pinFake[rng.nextInt(slotCount)] = true;

        for (int i = 0; i < slotCount; i++) {
            if ((progress & (1 << i)) != 0) {
                pinHit[i] = true;
                pinPop[i] = 1f;
                if (!pinFake[i]) hits++;
                else pinRevealed[i] = true;
            }
        }
        this.attempts = hits;
    }

    /** Short. A ward you can outwait is not a ward. */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.26f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.oak();
    }

    /** The drum plus its direction gauge needs a taller panel than the bar-based locks. */
    @Override
    protected int panelHeight() {
        return 240;
    }

    // ------------------------------------------------------------------ simulation

    private float currentSpeed() {
        float s = baseSpeed * (1f + hits * 0.10f);
        if (accelerating) s *= 1f + Math.min(1.1f, elapsed * 0.055f);
        return s;
    }

    /** Starts a weighted direction change from wherever the dial currently is. */
    private void flipDirection() {
        dirFrom = dirCurrent != 0f ? Math.signum(dirCurrent) : dirTarget;
        dirTarget = -Math.signum(dirTarget == 0f ? 1f : dirTarget);
        turnTimer = TURN_TIME;
        Sfx.plugTurn(0.78f);
    }

    @Override
    protected void step(float dt) {
        elapsed += dt;

        if (turnTimer > 0f) {
            turnTimer -= dt;
            float u = Mth.clamp(turnTimer / TURN_TIME, 0f, 1f); // 1 at the start, 0 at the end
            float s = 2f * u - 1f;                              // +1 -> 0 -> -1
            dirCurrent = dirFrom * (s * Math.abs(s));           // signed square: slow, hang, wind up
            if (turnTimer <= 0f) {
                turnTimer = 0f;
                dirCurrent = dirTarget;
            }
        }

        angle = (angle + dirCurrent * currentSpeed() * dt) % 360f;
        if (angle < 0) angle += 360f;

        if (flashTimer > 0f) flashTimer -= dt;
        if (kickTimer > 0f) kickTimer -= dt;
        for (int i = 0; i < slotCount; i++) {
            if (pinHit[i] && pinPop[i] < 1f) pinPop[i] = Math.min(1f, pinPop[i] + dt * 8f);
        }
    }

    /**
     * Distance in degrees from {@code target} to the arc the pick swept between {@code from} and
     * {@code to}. Zero if the pick passed straight over it. This is what makes a press land when
     * you hit the beat instead of the frame.
     */
    private static float distanceToSweep(float target, float from, float to) {
        float sweep = Mth.degreesDifference(from, to);
        float dFrom = Mth.degreesDifference(from, target);
        if (Math.abs(sweep) > 0.0001f) {
            float t = dFrom / sweep;
            if (t >= 0f && t <= 1f) return 0f;
        }
        return Math.min(Math.abs(dFrom), Math.abs(Mth.degreesDifference(to, target)));
    }

    private void strike() {
        if (resolved) return;
        attempts++;

        // Where the pick was one grace window ago; anything it crossed since then counts.
        float travelled = dirCurrent * currentSpeed() * grace();
        float from = angle - travelled;

        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < slotCount; i++) {
            if (pinHit[i]) continue;
            float d = distanceToSweep(pinAngles[i], from, angle);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }

        // Every click turns the drum around when this lock reverses, hit or miss.
        if (reversing) flipDirection();

        if (best < 0 || bestDist > tolerance) {
            loseLife("Pin missed its seat");
            return;
        }

        if (pinFake[best]) {
            pinHit[best] = true;
            pinRevealed[best] = true;
            progress |= (1 << best);
            flashSlot = best;
            flashTimer = 0.4f;
            loseLife("False pin \u2014 mechanism jams");
            return;
        }

        pinHit[best] = true;
        progress |= (1 << best);
        hits++;
        flashSlot = best;
        flashTimer = 0.35f;
        kickTimer = 0.16f;
        elapsed = 0f;
        Sfx.pinSet(hits);

        int cx = centerX();
        int cy = contentCenterY() - 4;
        double r = Math.toRadians(pinAngles[best]);
        burst(cx + (int) Math.round(Math.cos(r) * radius), cy + (int) Math.round(Math.sin(r) * radius), COL_GOOD);

        if (hits >= realPins) {
            succeed(attempts == 0 ? 1f : (float) realPins / attempts);
        }
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

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int cx = centerX();
        int cy = contentCenterY() - 4;
        int kick = Math.round(kickTimer * 16f);

        // drum plate: hard pixel rings rather than a soft gradient
        p.disc(cx, cy, radius + 11 + kick, t.frameDark);
        p.ring(cx, cy, radius + 10 + kick, radius + 7, t.frame);
        p.ring(cx, cy, radius + 9 + kick, radius + 8, t.frameLite);
        p.disc(cx, cy, radius + 6, t.well);
        p.ring(cx, cy, radius + 1, radius - 1, Painter.withAlpha(t.frame, 0x66));

        // engraved notches around the rim
        for (int a = 0; a < 360; a += 15) {
            double r = Math.toRadians(a);
            int nx = cx + (int) Math.round(Math.cos(r) * (radius + 4));
            int ny = cy + (int) Math.round(Math.sin(r) * (radius + 4));
            boolean major = a % 45 == 0;
            p.rect(nx - 1, ny - 1, nx + 1, ny + 1, major ? t.frameLite : Painter.withAlpha(t.frame, 0x99));
        }

        // Pin windows. Drawn as a seat on the inner face as well as under the pin itself: the
        // pin used to sit on top of its own window and hide it, which is half of why the timing
        // felt arbitrary.
        int windowCol = Painter.withAlpha(t.accent, 0x38 + Math.round(breath(0.5f) * 0x10));
        int seatCol = Painter.withAlpha(t.accentLite, 0x48);
        for (int i = 0; i < slotCount; i++) {
            if (pinHit[i]) continue;
            float a1 = pinAngles[i] - tolerance, a2 = pinAngles[i] + tolerance;
            p.arc(cx, cy, radius + 6, a1, a2, 4, windowCol);
            p.arc(cx, cy, radius - 11, a1, a2, 3, seatCol);
        }

        // pins as square studs
        for (int i = 0; i < slotCount; i++) {
            double r = Math.toRadians(pinAngles[i]);
            int px = cx + (int) Math.round(Math.cos(r) * radius);
            int py = cy + (int) Math.round(Math.sin(r) * radius);
            boolean flashing = i == flashSlot && flashTimer > 0f;
            float pop = pinHit[i] ? Mth.clamp(pinPop[i], 0f, 1f) : 1f;
            int size = pinHit[i] ? Math.round(5 * (0.5f + 0.5f * pop)) : 5;
            if (flashing) size += 1;

            int col = pinRevealed[i] ? COL_BAD : (pinHit[i] ? COL_GOOD : t.accent);
            if (flashing) p.rect(px - size - 3, py - size - 3, px + size + 3, py + size + 3,
                    Painter.withAlpha(col, 0x40));
            p.rect(px - size - 1, py - size - 1, px + size + 1, py + size + 1, 0xFF0B0806);
            p.rect(px - size, py - size, px + size, py + size, Painter.darken(col, 0.35f));
            p.rect(px - size + 1, py - size + 1, px + size - 1, py + size - 1, col);
            p.rect(px - size + 1, py - size + 1, px + 1, py + 1, Painter.lighten(col, 0.35f));
        }

        // the pick
        double ar = Math.toRadians(angle);
        float tipX = cx + (float) Math.cos(ar) * (radius + 2);
        float tipY = cy + (float) Math.sin(ar) * (radius + 2);
        float tailX = cx - (float) Math.cos(ar) * 15f;
        float tailY = cy - (float) Math.sin(ar) * 15f;
        p.line(tailX, tailY, tipX, tipY, 4, 0x44000000);
        p.line(cx, cy, tipX, tipY, 2, 0xFFF3E6CB);
        p.rect(tipX - 3, tipY - 3, tipX + 3, tipY + 3, 0xFF1A120B);
        p.rect(tipX - 2, tipY - 2, tipX + 2, tipY + 2, 0xFFFFF3D2);

        // hub: a small idol eye instead of a plain boss
        p.disc(cx, cy, 12, t.frameDark);
        p.disc(cx, cy, 10, t.frame);
        Sigils.drawCentered(p, Sigils.TILE[0], cx, cy, 2, t.panelDark,
                Painter.withAlpha(t.frameLite, 0x70), 0);

        // direction gauge: the bar shrinks to nothing as the drum turns around, so the
        // stand-still in the middle of a reversal is something you can actually read
        if (reversing) {
            int gy = cy + 34;
            int gw = 44;
            p.rect(cx - gw / 2 - 2, gy - 5, cx + gw / 2 + 2, gy + 5, t.frameDark);
            p.rect(cx - gw / 2 - 1, gy - 4, cx + gw / 2 + 1, gy + 4, t.wellEdge);
            p.rect(cx - gw / 2, gy - 3, cx + gw / 2, gy + 3, 0xFF0C0906);
            int fill = Math.round(dirCurrent * (gw / 2f - 1));
            int col = turnTimer > 0f ? t.accentLite : t.accent;
            if (fill >= 0) p.rect(cx, gy - 2, cx + Math.max(1, fill), gy + 2, col);
            else p.rect(cx + fill, gy - 2, cx, gy + 2, col);
            p.rect(cx - 1, gy - 4, cx + 1, gy + 4, t.frameLite);
        }

        progressDots(cx, cy + radius + 16, realPins, hits);
    }

    @Override
    protected String hintText() {
        if (reversing) return "SPACE in the notch \u00b7 every press turns the drum around";
        return "Press SPACE when the pick sits in the marked notch";
    }
}
