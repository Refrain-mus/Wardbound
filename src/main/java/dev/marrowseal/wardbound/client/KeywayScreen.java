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
 * The keyway. A cutaway of a real pin tumbler cylinder, picked one pin at a
 * time in order, the way it is actually done.
 *
 * <p>Everything on screen is the mechanism rather than a dial standing in for
 * it. The horizontal line across the middle is the <b>shear line</b>: the seam
 * between the plug that turns and the housing that does not. Each chamber holds
 * a brass key pin sitting on the plug floor and a steel driver pin above it,
 * held down by a spring. The plug cannot turn while any driver pin bridges the
 * seam.
 *
 * <p>So the whole game is one sentence: <b>push each key pin up until its top
 * sits exactly on the shear line, and let go there.</b> Too early and the pin
 * falls back. Too far and the key pin itself crosses the seam, which binds the
 * plug just as badly as leaving it low, and costs you.
 *
 * <p>The pins are all different lengths, which is the entire difficulty. The
 * target never moves; the distance to it does. A long key pin is nearly there
 * already and needs a tap. A short one needs most of the chamber, and the
 * temptation is to keep pushing after it arrives.
 *
 * <p>Quirks a chest may carry:
 * <ul>
 *   <li>0 binding order - the pins do not bind left to right. The order comes
 *       from the chest's seed and only the pin currently under tension will
 *       move, so you are told which one it is and have to keep track.</li>
 *   <li>1 spool pins - one or two chambers hold a spool driver: a pin with a
 *       waist turned into it. When the waist reaches the seam the plug gives a
 *       little and everything looks and sounds like a set pin. It is not one.
 *       The honest signal is where the brass key pin's top is, and that is
 *       still short of the line. Keep pushing.</li>
 *   <li>2 stiff springs - the spring fights harder the further the pin goes, so
 *       the pin slows as it climbs. You cannot count the time; you have to
 *       watch the pin.</li>
 * </ul>
 *
 * <p>Tension is the clock. The wrench is only under load while you are actually
 * pushing a pin, so thinking is free and dithering is not. Run it out and the
 * wrench skates out of the keyway.
 */
@OnlyIn(Dist.CLIENT)
public class KeywayScreen extends BaseMinigameScreen {

    /** Height in pixels of the plug's interior, i.e. one full push of a pin. */
    private static final int TRAVEL = 46;
    private static final int CHAMBER_W = 20;
    private static final int CHAMBER_GAP = 6;

    private final boolean bindingOrder;
    private final boolean spools;
    private final boolean stiffSprings;
    /**
     * The plug is not seated properly, so it wanders under tension.
     *
     * <p>Every gate in the lock slides slowly up and down together while you
     * work. It does not change what the lock is - you are still feeling for a
     * chamber that trembles - but it stops a gate you found once from staying
     * found, which is the thing that made the lock feel like one motion
     * repeated.
     */
    private final boolean looseCore;
    private float coreDrift;
    private float corePhase;
    /**
     * The seam is not drawn.
     *
     * <p>Every pin in this lock is judged against one line across the middle,
     * and once you have seen that line a few times the whole thing is aiming at
     * a static target. With shivering wards the line is gone: the chamber shakes
     * instead, hardest exactly where the pin wants to sit, and the pick's scrape
     * climbs in pitch and quickens as you close on it. You find the gate by
     * watching a thing tremble and listening to it, which is both a different
     * skill and, for a lock, a considerably more plausible one.
     */
    private final boolean shivering;

    private final int pins;
    /** Push at which this pin's top reaches the seam. Also, one minus its length. */
    private final float[] travel;
    /** Push at which a spool's waist reaches the seam and lies to you. -1 for honest pins. */
    private final float[] falseTravel;
    /** Where the waist is turned into the driver, measured up from its bottom. */
    private final int[] waistPx;
    private final int[] driverLen;
    /** The sequence the pins bind in. Index into the arrays above. */
    private final int[] order;
    private final boolean[] set;
    /** Chambers that return no tension when they seat. See {@link #seat}. */
    private final boolean[] greedy;
    /** How far from the seam this chamber's gate actually sits, in push units. */
    private final float[] gateOffset;

    private final float tolerance;
    private final float riseRate;
    private final float tensionDrain;

    private int seated;
    private int attempts;

    private float push;
    private boolean pushing;
    private float tension = 1f;

    /** Plug rotation in degrees, plus the little lie a spool tells. */
    private float plugAngle;
    private float plugNudge;

    private float flashTimer;
    private int flashColor = COL_GOOD;
    private int flashChamber = -1;
    private float riseSoundTimer;
    private boolean falseSetArmed;

    public KeywayScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.keyway"), msg);

        boolean[] v = pickVariants(4, 12);
        bindingOrder = v[0];
        spools = v[1];
        stiffSprings = v[2];
        looseCore = v[3];

        // Shivering is no longer a variant; it is how the lock works.
        //
        // With a drawn shear line every pin in every keyway sets at the same
        // height on the screen, which means there is one aim point for the whole
        // lock and no way to be wrong about where it is. The seam is now only a
        // seam: each chamber's gate sits at its own place, nothing marks it, and
        // you find it because the chamber trembles hardest there and the pick's
        // scrape climbs to meet it.
        shivering = true;
        if (bindingOrder) modifierLabels.add("binding order");
        if (spools) modifierLabels.add("spool pins");
        if (stiffSprings) modifierLabels.add("stiff springs");
        if (looseCore) modifierLabels.add("loose core \u00b7 the plug drifts");


        int minPins = Mth.clamp(WardConfig.minPins, 2, 6);
        int maxPins = Mth.clamp(Math.max(minPins, WardConfig.maxPins), minPins, 6);
        int basePins = minPins + Math.round((value / 100f) * (maxPins - minPins) * difficulty);
        this.pins = wobble(basePins, 1, 41, minPins, maxPins);

        // Tolerance shrinks with difficulty, but on the square root of it, not
        // linearly. Linear scaling looked reasonable on paper and was not: the
        // rise rate ALSO scales with difficulty, so the release window was being
        // squeezed from both ends at once and the End's locks landed at about
        // 50ms. That is below what a human can act on through a render loop, so
        // those chests were not hard, they were a dice roll. The floor is set
        // where the window stays near 90ms at the worst difficulty the mod ships.
        this.tolerance = Mth.clamp(
                0.062f / (float) Math.sqrt(Math.max(0.4f, difficulty)), 0.048f, 0.090f);

        float variance = 0.85f + seedVariance(42) * 0.35f;
        this.riseRate = (0.50f + value * 0.0026f) * difficulty * variance;

        Random rng = new Random(seed ^ 0x6B3EA71FL);

        this.travel = new float[pins];
        this.falseTravel = new float[pins];
        this.waistPx = new int[pins];
        this.driverLen = new int[pins];
        this.set = new boolean[pins];
        this.greedy = new boolean[pins];
        this.gateOffset = new float[pins];
        this.order = new int[pins];

        // Pin lengths. Kept away from both ends of the chamber: a pin that sets
        // at almost zero push is a free one, and a pin that needs the entire
        // chamber leaves no room to overset into, which removes the risk.
        for (int i = 0; i < pins; i++) {
            travel[i] = 0.22f + rng.nextFloat() * 0.68f;
            falseTravel[i] = -1f;
            waistPx[i] = -1;
            order[i] = i;
        }

        // No two adjacent pins should want the same push, or half the lock is
        // one motion learned once.
        for (int i = 1; i < pins; i++) {
            if (Math.abs(travel[i] - travel[i - 1]) < tolerance * 3f) {
                travel[i] = Mth.clamp(travel[i] + (rng.nextBoolean() ? 1 : -1) * tolerance * 4f,
                        0.20f, 0.92f);
            }
        }

        if (spools) {
            int howMany = pins >= 5 ? 2 : 1;
            for (int k = 0; k < howMany; k++) {
                int idx = rng.nextInt(pins);
                if (waistPx[idx] >= 0) continue;
                // The waist has to sit far enough below the real gap that the
                // brass pin is visibly short when the plug gives, and the pin
                // needs room underneath it to be pushed that far in the first
                // place.
                int offsetPx = 9 + rng.nextInt(5);
                if (travel[idx] * TRAVEL < offsetPx + 4) {
                    travel[idx] = Mth.clamp(travel[idx] + (offsetPx + 6) / (float) TRAVEL,
                            0.22f, 0.92f);
                }
                waistPx[idx] = offsetPx;
                falseTravel[idx] = travel[idx] - offsetPx / (float) TRAVEL;
            }
        }

        // Driver pins are sized last, because a driver has to be long enough to
        // bridge the seam while its pin is still down - that bridging is the
        // whole reason the plug will not turn. Anything shorter than the key
        // pin's travel would leave the lock hanging open at rest.
        float sumTravel = 0f;
        for (int i = 0; i < pins; i++) {
            sumTravel += travel[i];
            int minimum = Math.round(travel[i] * TRAVEL) + 10;
            driverLen[i] = Math.min(54, minimum + rng.nextInt(7));
            if (waistPx[i] >= 0) driverLen[i] = Math.max(driverLen[i], waistPx[i] + 12);
        }

        if (bindingOrder) {
            for (int i = pins - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = order[i];
                order[i] = order[j];
                order[j] = t;
            }
        }

        // Tension budget, expressed as how many times over you could push every
        // pin exactly once before the wrench skates out.
        //
        // BUG that was here: this divided the total travel by the rise rate,
        // which is only correct while the rate is constant. With stiff springs
        // the pin decelerates as it climbs, so the real hold time is up to half
        // again as long and the wrench ran out on locks that were being played
        // perfectly well. Integrate the actual curve instead.
        float requiredHold = holdTimeFor(sumTravel);
        // Was 2.6x, which is why the lock felt like it could not be failed on
        // purpose: you could tap every pin half a dozen times learning it and
        // still finish with tension to spare. 1.7x leaves room for a handful of
        // honest mistakes and no room to brute force a pin.
        float slack = 1.7f / Mth.clamp(difficulty, 0.7f, 2.0f);
        this.tensionDrain = 1f / Math.max(1f, requiredHold * slack);

        // Roughly a third of the chambers, never the first one: opening on a
        // chamber that gives nothing back reads as the lock being broken.
        for (int i = 1; i < pins; i++) {
            greedy[i] = rng.nextFloat() < 0.34f;
        }

        // Every chamber's gate sits at a different height.
        //
        // Shifting the required push would not do this: the pin is drawn
        // relative to its own gate, so changing how far it travels still leaves
        // every pin setting on the same line. The offset has to move where the
        // pin is DRAWN, so that a seated pin in chamber two sits visibly higher
        // than a seated pin in chamber three and there is no single height to
        // aim at.
        for (int i = 0; i < pins; i++) {
            // Spread wide and pushed apart: one chamber sets high in the plug,
            // the next low, the one after somewhere between. Clustering them
            // near the seam left a single band to aim at, which is the thing
            // that made the lock trivial in the first place.
            gateOffset[i] = -0.62f + rng.nextFloat() * 1.24f;
            if (i > 0 && Math.abs(gateOffset[i] - gateOffset[i - 1]) < 0.30f) {
                gateOffset[i] = -gateOffset[i];
            }
        }


        this.seated = Mth.clamp(progress, 0, pins);
        for (int i = 0; i < seated; i++) set[order[i]] = true;
        this.attempts = seated;
        this.plugAngle = turnPerPin() * seated;
    }

    /** Short. Every one of these was outlastable. */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.45f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.keyway();
    }

    /**
     * Wider than the other locks. The cutaway needs room for six chambers, the
     * seam markers hang off both sides of it, and the plug face has to sit
     * clear of all of that rather than on top of it.
     */
    @Override
    protected int panelWidth() {
        return 332;
    }

    @Override
    protected int panelHeight() {
        return 246;
    }

    /**
     * Seconds of held tension needed to push a pin through {@code distance} of
     * travel.
     *
     * <p>Linear while the springs are ordinary. With stiff springs the rate is
     * {@code r(1 - k p)}, whose solution for the time to reach p is
     * {@code -ln(1 - k p) / (k r)}. Worth doing properly: the difference decides
     * whether the tension bar is a pressure or a wall.
     */
    private float holdTimeFor(float distance) {
        float r = Math.max(0.05f, riseRate);
        if (!stiffSprings) return distance / r;

        // sumTravel arrives as a total, but the curve is per pin, so treat it as
        // the average pin repeated: close enough, and never optimistic.
        float perPin = distance / Math.max(1, pins);
        float k = 0.62f;
        float one = (float) -Math.log(Math.max(0.05, 1.0 - k * Math.min(perPin, 0.95f))) / (k * r);
        return one * pins;
    }

    /**
     * How far past the gate the pin may go before the stack binds.
     *
     * <p>It used to be the tolerance itself, so the instant you were late you
     * were also overset and the lock ended. With no line drawn that is a
     * guillotine rather than a difficulty: you have to be allowed to be a little
     * late and merely fail the pin. Three times the window, so overshooting is
     * a real mistake and being fractionally slow is not.
     */
    private float oversetMargin() {
        return tolerance * 3.2f;
    }

    private float turnPerPin() {
        return 62f / Math.max(1, pins);
    }

    /** Where chamber {@code i} sits in the binding order. */
    private int orderOf(int i) {
        for (int k = 0; k < pins; k++) {
            if (order[k] == i) return k;
        }
        return i;
    }

    /** Which chamber is currently under tension. -1 once they are all seated. */
    private int active() {
        return seated < pins ? order[seated] : -1;
    }

    // ------------------------------------------------------------------ simulation

    private float rateNow() {
        // A stiff spring pushes back harder the further it is compressed, so the
        // pin creeps at the top. Bottoms out at a third of the starting rate
        // rather than at zero, or the pin would never arrive.
        return stiffSprings ? riseRate * (1f - 0.62f * Mth.clamp(push, 0f, 1f)) : riseRate;
    }

    @Override
    protected void step(float dt) {
        if (flashTimer > 0f) flashTimer -= dt;
        if (looseCore) {
            corePhase += dt;
            // two periods that never line up, so the wander has no beat to learn
            coreDrift = (Mth.sin(corePhase * 0.55f) * 0.55f + Mth.sin(corePhase * 0.23f) * 0.45f)
                    * 0.085f;
        }
        if (plugNudge > 0f) plugNudge = Math.max(0f, plugNudge - dt * 5f);

        int a = active();
        if (a < 0) return;

        if (pushing) {
            push += rateNow() * dt;
            tension -= tensionDrain * dt;

            // The scrape tells you where the pin is, not just that it is moving.
            // Pitch climbs with the pin and the ticks crowd together as the seam
            // gets close, so the release point can be heard as well as seen.
            // A timing game whose only cue is a two-pixel line is a game about
            // eyesight; this gives the same information through a second sense.
            riseSoundTimer -= dt;
            if (riseSoundTimer <= 0f) {
                // Pitch tracks CLOSENESS to the gate, not how far the pin has
                // come. It used to be mostly the latter, so on a chamber whose
                // gate is low the note peaked long before the gate and then
                // kept climbing past it, which is the opposite of a cue.
                float toGate = Math.abs(gateFor(a) - push);
                float near = Mth.clamp(1f - toGate / (tolerance * 9f), 0f, 1f);
                Sfx.play(WardSounds.PIN_RISE,
                        0.28f + near * 0.34f,
                        0.72f + near * near * 0.85f);
                // 200ms while the pin is far, down to 35ms at the gate. With no
                // line drawn, the ticks crowding together is one of only two
                // signals there are.
                riseSoundTimer = 0.20f - near * 0.165f;
            }

            // The spool's waist reaching the seam. The plug gives, it clicks,
            // and none of it means anything.
            if (falseTravel[a] > 0f && !falseSetArmed && push >= falseTravel[a]) {
                falseSetArmed = true;
                plugNudge = 1f;
                Sfx.plugTurn(1.35f);
            }

            // Overset. Held open by the grace window so a release that was
            // already on its way is still judged as a release, not a jam.
            //
            if (push > gateFor(a) + oversetMargin() + rateNow() * grace()) {
                overset(a);
                return;
            }
            if (push > 1.05f) {
                overset(a);
                return;
            }

            if (tension <= 0f) {
                tension = 1f;
                push = 0f;
                pushing = false;
                falseSetArmed = false;
                Sfx.wrenchSlip();
                loseLife("The wrench skated out");
                return;
            }
        } else if (push > 0f) {
            // Nothing holds the pin up, so it drops back at spring speed.
            push = Math.max(0f, push - dt * 2.6f);
        }
    }

    private void startPush() {
        if (pushing || active() < 0) return;
        pushing = true;
        push = 0f;
        falseSetArmed = false;
        riseSoundTimer = 0f;
    }

    private void release() {
        if (!pushing) return;
        pushing = false;

        int a = active();
        if (a < 0) return;
        attempts++;

        // Judge the release against everything the pin swept during the grace
        // window rather than against one instant, the same way every other lock
        // in the mod treats a press.
        float from = Math.max(0f, push - rateNow() * grace());
        boolean crossed = bandCrossed(gateFor(a), from, push);

        if (crossed) {
            seat(a);
            return;
        }

        if (falseTravel[a] > 0f && bandCrossed(falseTravel[a] + coreDrift, from, push)) {
            flashChamber = a;
            flashTimer = 0.5f;
            flashColor = COL_BAD;
            plugNudge = 0f;
            push = 0f;
            falseSetArmed = false;
            Sfx.overset();
            loseLife("False set \u2014 the spool caught");
            return;
        }

        // Released short. Costs nothing but the tension already spent, which is
        // the right price: guessing low is how you learn a pin.
        // Releasing short is still the cheap mistake, but it is no longer free:
        // the wrench gives up a little every time a pin slams back down.
        push = 0f;
        falseSetArmed = false;
        tension = Math.max(0.02f, tension - 0.07f);
        Sfx.pinDrop();
        showBanner("The pin dropped back", theme().dim, 600);
    }

    private boolean bandCrossed(float target, float from, float to) {
        return to >= target - tolerance && from <= target + tolerance;
    }

    private void seat(int chamber) {
        set[chamber] = true;
        seated++;
        progress = seated;
        push = 0f;
        falseSetArmed = false;
        plugNudge = 0f;
        plugAngle = turnPerPin() * seated;
        flashChamber = chamber;
        flashTimer = 0.4f;
        flashColor = COL_GOOD;
        // A set pin used to always hand tension back, which meant a clean run
        // topped itself up and the wrench could not run out on anyone playing
        // well. Now some chambers give nothing: the pin seats, the plug turns,
        // and the wrench is exactly as tired as it was. Which chambers those
        // are comes from the seed, so it is a property of the lock rather than
        // a dice roll, and a lock can be learned.
        if (!greedy[chamber]) {
            tension = Math.min(1f, tension + 0.10f);
        } else {
            showBanner("No give in that one", theme().dim, 700);
        }

        Sfx.pinSet(seated);
        Sfx.plugTurn(0.88f + seated * 0.06f);
        burst(chamberX(chamber) + CHAMBER_W / 2, shearY(), COL_GOOD);

        if (seated >= pins) {
            succeed(pins / (float) Math.max(pins, attempts));
        }
    }



    private void overset(int chamber) {
        pushing = false;
        push = 0f;
        falseSetArmed = false;
        plugNudge = 0f;

        // An overset binds the plug hard, and a bound plug does not hold the
        // pins that were already set. Losing the last one is what a real overset
        // costs, and without it the lock had no move that could actually take
        // progress away from you.
        if (seated > 0) {
            seated--;
            set[order[seated]] = false;
            progress = seated;
            plugAngle = turnPerPin() * seated;
            showBanner("The plug dropped a pin", COL_BAD, 900);
        }
        flashChamber = chamber;
        flashTimer = 0.5f;
        flashColor = COL_BAD;
        Sfx.overset();
        loseLife("Overset \u2014 the pin bound above the line");
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button == 0) {
            startPush();
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
            startPush();
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

    private int cutawayW() {
        return pins * CHAMBER_W + (pins - 1) * CHAMBER_GAP;
    }

    /**
     * Left edge of the chamber block. Offset far enough from the panel edge that
     * the pin-height marker and the seam arrow, both of which sit outside the
     * cutaway, still land inside the frame.
     */
    private int cutawayX() {
        return left() + 46 + (150 - cutawayW()) / 2;
    }

    private int chamberX(int i) {
        return cutawayX() + i * (CHAMBER_W + CHAMBER_GAP);
    }

    private int housingTop() {
        return contentTop() + 8;
    }

    private int shearY() {
        return housingTop() + 76;
    }

    private int plugBottom() {
        return shearY() + TRAVEL + 6;
    }

    /** Screen y of a pin top at the given push. */
    private int pinTopY(float travelValue, float pushValue) {
        return shearY() + Math.round((travelValue - pushValue) * TRAVEL);
    }

    /** Where chamber {@code i}'s pin top is on screen, gate offset included. */
    private int pinTopY(int i, float pushValue) {
        return shearY() + Math.round((travel[i] - pushValue + gateOffset[i] + coreDrift) * TRAVEL);
    }

    private int faceCX() {
        return left() + panelWidth() - 58;
    }

    private int faceCY() {
        return contentTop() + 66;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int x0 = cutawayX(), w = cutawayW();
        int hTop = housingTop(), shear = shearY(), pBottom = plugBottom();
        int a = active();

        // ---- the block the cylinder is sunk into
        p.roundRect(x0 - 12, hTop - 6, w + 24, (pBottom - hTop) + 20, t.frameDark);
        p.roundRect(x0 - 11, hTop - 5, w + 22, (pBottom - hTop) + 18, Painter.darken(t.panel, 0.18f) | 0xFF000000);
        p.dither(x0 - 11, hTop - 5, w + 22, (pBottom - hTop) + 18, Painter.withAlpha(t.frame, 0x12), 2, 0);

        // ---- housing above the seam, plug below it
        well(x0 - 6, hTop, w + 12, shear - hTop);
        int plugTint = Painter.mix(t.frame, t.accent, 0.35f) | 0xFF000000;
        p.rect(x0 - 6, shear, x0 + w + 6, pBottom, Painter.darken(plugTint, 0.62f));
        p.rect(x0 - 5, shear + 1, x0 + w + 5, pBottom - 1, Painter.darken(plugTint, 0.45f));
        p.scanlines(x0 - 5, shear + 1, w + 10, pBottom - shear - 2, Painter.withAlpha(0x000000, 0x1A));

        int half = Math.max(1, Math.round(tolerance * TRAVEL));

        // ---- chambers
        for (int i = 0; i < pins; i++) {
            drawChamber(i, i == a, hTop, shear, pBottom, half);
        }

        // ---- the shear line, drawn last so it lies on top of the chambers.
        // Drawing it before them was the original mistake: every chamber fills
        // its own bore, so the line survived only in the gaps between chambers
        // and the one thing the whole game aims at was the one thing you could
        // not see.
        drawShearLine(g, x0, w, shear, half, a);

        // ---- the pick, sitting under whichever pin is being worked
        if (a >= 0) {
            int px = chamberX(a) + CHAMBER_W / 2;
            int tipY = pinTopY(a, push) + pinLength(a) + 2;
            p.line(px - 1, tipY + 3, x0 - 26, pBottom + 26, 3, 0xFF11100E);
            p.line(px - 1, tipY + 2, x0 - 26, pBottom + 25, 2, 0xFFD8CDB8);
            p.rect(px - 4, tipY, px + 4, tipY + 3, 0xFF14120F);
            p.rect(px - 3, tipY, px + 3, tipY + 2, 0xFFEDE2C8);
        }

        // ---- chamber labels, in one pass after every chamber is painted.
        // Normally just the chamber number. With the heart riding the lock, the
        // position each chamber holds in the binding order instead: on a lock
        // that does not bind left to right that is the one thing you otherwise
        // have to discover a mistake at a time.
        for (int i = 0; i < pins; i++) {
            String label = String.valueOf(i + 1);
            int col = i == a ? t.accent : t.faint;
            if (heartHint()) {
                label = String.valueOf(orderOf(i) + 1);
                col = set[i] ? Painter.withAlpha(COL_GOOD, 0x99) : (i == a ? COL_GOOD : t.dim);
            }
            smallCentered(g, label, chamberX(i) + CHAMBER_W / 2, pBottom + 12, col, 0.7f);
        }

        // ---- the tension wrench, jammed into the bottom of the keyway
        drawTension(g, x0 - 6, pBottom + 22, w + 12);

        // ---- the plug seen end on, which is the only place the turn is visible
        drawPlugFace(g);

        progressDots(faceCX(), faceCY() + 58, pins, seated);
    }

    /**
     * The seam, its tolerance band, and how close the working pin is to it.
     *
     * <p>Painted over the chambers rather than under them, and painted bright.
     * This is the target; everything else on the panel is context for it.
     */
    private void drawShearLine(GuiGraphics g, int x0, int w, int shear, int half, int active) {
        Theme t = theme();

        if (shivering) {
            // Nothing but the housing seam, drawn as a hairline the chambers
            // cover, so there is a joint there but no target on it.
            p.rect(x0 - 8, shear, x0 + w + 8, shear + 1, Painter.withAlpha(t.frame, 0x33));
            return;
        }

        // How near the working pin is to the seam: 0 far, 1 sitting on it. The
        // line answers back as you approach, so the last few pixels stay
        // readable even when the pin is moving quickly.
        float near = 0f;
        boolean inside = false;
        if (active >= 0 && !set[active]) {
            float gap = Math.abs(travel[active] - push);
            near = Mth.clamp(1f - gap / (tolerance * 5f), 0f, 1f);
            inside = gap <= tolerance;
        }

        int bandCol = inside ? COL_GOOD : Painter.mix(t.accent, COL_GOOD, near);
        int bandAlpha = 0x30 + Math.round(near * 0x60);

        // the tolerance band, full width and wide enough to actually see
        p.rect(x0 - 14, shear - half, x0 + w + 14, shear + half,
                Painter.withAlpha(bandCol, bandAlpha));
        p.rect(x0 - 14, shear - half, x0 + w + 14, shear - half + 1,
                Painter.withAlpha(bandCol, 0xAA));
        p.rect(x0 - 14, shear + half - 1, x0 + w + 14, shear + half,
                Painter.withAlpha(bandCol, 0xAA));

        // the seam itself: a dark core with a lit edge above it, the way a
        // machined joint catches light
        int lineCol = inside ? COL_GOOD : t.accentLite;
        p.rect(x0 - 18, shear - 2, x0 + w + 18, shear + 2, 0xE0100E0C);
        p.rect(x0 - 18, shear - 2, x0 + w + 18, shear - 1, Painter.withAlpha(lineCol, 0xFF));
        p.rect(x0 - 18, shear + 1, x0 + w + 18, shear + 2,
                Painter.withAlpha(Painter.darken(lineCol, 0.35f), 0xFF));

        // arrow heads either side, so the eye finds the line before it finds
        // anything else on the panel
        int glow = Painter.withAlpha(lineCol, 0x90 + Math.round(breath(0.9f) * 0x40));
        for (int k = 0; k < 5; k++) {
            p.rect(x0 - 20 - k, shear - k, x0 - 19 - k, shear + k + 1, glow);
            p.rect(x0 + w + 19 + k, shear - k, x0 + w + 20 + k, shear + k + 1, glow);
        }

        // where the working pin's top is right now, repeated outside the
        // cutaway where no pin can ever cover it
        if (active >= 0 && !set[active]) {
            int py = pinTopY(active, push);
            int marker = inside ? COL_GOOD : (py < shear - half ? COL_BAD : t.accent);
            p.rect(x0 - 31, py - 1, x0 - 22, py + 1, marker);
            p.rect(x0 - 34, py - 2, x0 - 31, py + 2, marker);
        }

        smallCentered(g, "shear line", x0 + w / 2, shear - half - 11,
                inside ? COL_GOOD : t.dim, 0.7f);
    }

    /** Length in pixels of chamber i's brass key pin. Long pin, short push. */
    private int pinLength(int i) {
        return Math.max(5, Math.round((1f - travel[i]) * TRAVEL) + 4);
    }

    /**
     * How hard chamber {@code i} is trembling, 0 to 1.
     *
     * <p>Only the chamber under the pick trembles, and only while it is being
     * pushed: a lock that shakes all over tells you nothing about any one pin.
     */
    /** Where chamber {@code i} actually sets right now, drift included. */
    private float gateFor(int i) {
        return travel[i] + coreDrift;
    }

    private float shiver(int i) {
        if (!shivering || !pushing || i != active() || set[i]) return 0f;
        float gap = Math.abs(gateFor(i) - push);
        return Mth.clamp(1f - gap / (tolerance * 7f), 0f, 1f);
    }

    private void drawChamber(int i, boolean isActive,
                             int hTop, int shear, int pBottom, int bandHalf) {
        Theme t = theme();
        float sh = shiver(i);
        // Ramped hard so the last fraction before the gate is unmistakable while
        // everything before it is only a hint.
        // Calmer, and it only really moves at the very end. The cube ramp with a
        // seven pixel throw read as the whole lock rattling apart; two pixels on
        // a fifth power is a chamber that is almost still until it is not.
        float amp = sh <= 0f ? 0f : (float) Math.pow(sh, 5) * 2.4f;
        int shake = (WardConfig.accessibilityDisableShake || WardConfig.accessibilityReduceMotion || amp <= 0.05f)
                ? 0 : Math.round((float) (Math.random() - 0.5) * 2f * amp);
        int cx = chamberX(i) + shake;
        int cw = CHAMBER_W;
        boolean seatedHere = set[i];
        boolean flashing = i == flashChamber && flashTimer > 0f;

        // the bore
        p.rect(cx, hTop + 2, cx + cw, pBottom - 2, Painter.darken(t.well, 0.30f));
        p.rect(cx + 1, hTop + 2, cx + cw - 1, pBottom - 2, t.well);
        if (isActive) {
            p.rect(cx + 1, hTop + 2, cx + cw - 1, pBottom - 2, Painter.withAlpha(t.accent, 0x14));
        }

        float shown = seatedHere ? travel[i] : (isActive ? push : 0f);
        int keyTop = pinTopY(i, shown);
        int keyBottom = keyTop + pinLength(i);
        int driverBottom = keyTop;
        int driverTop = driverBottom - driverLen[i];

        // ---- driver pin, steel. A spool has a waist turned into it and that
        // waist is the whole lie, so it is drawn plainly rather than hinted at.
        boolean spool = falseTravel[i] > 0f;
        int steel = seatedHere ? Painter.darken(COL_GOOD, 0.35f) : 0xFF6E6A63;
        int steelLite = Painter.lighten(steel, 0.30f);
        int steelDark = Painter.darken(steel, 0.40f);
        drawPin(cx + 3, driverTop, cw - 6, driverBottom - driverTop, steel, steelLite, steelDark);
        if (spool) {
            int waistY = driverBottom - waistPx[i];
            p.rect(cx + 3, waistY - 3, cx + cw - 3, waistY + 3, t.well);
            p.rect(cx + 6, waistY - 3, cx + cw - 6, waistY + 3, steelDark);
            p.rect(cx + 7, waistY - 2, cx + cw - 7, waistY + 2, steel);
        }

        // ---- spring, compressed by however far the pin has come up
        int springTop = hTop + 3;
        if (driverTop > springTop + 3) {
            int coils = 5;
            int span = driverTop - springTop;
            for (int c = 0; c < coils; c++) {
                int sy = springTop + span * c / coils;
                p.rect(cx + 4, sy, cx + cw - 4, sy + 1, Painter.withAlpha(t.frameLite, 0x88));
            }
            p.rect(cx + cw / 2 - 1, springTop, cx + cw / 2 + 1, driverTop, Painter.withAlpha(t.frame, 0x55));
        } else {
            p.rect(cx + 3, springTop, cx + cw - 3, driverTop, Painter.withAlpha(t.frameLite, 0x66));
        }

        // ---- key pin, brass. The one thing worth watching.
        int brass = seatedHere ? COL_GOOD : (flashing ? flashColor : 0xFFC49A4E);
        // Red means the pin has gone past its own gate, which is the only thing
        // red should mean here. It used to mean "above the seam", so on a
        // chamber whose gate sits low the pin turned red while it was still
        // short of where it needed to be.
        boolean over = !seatedHere && (isActive
                ? push > gateFor(i) + tolerance
                : false);
        if (over && !seatedHere) brass = COL_BAD;
        drawPin(cx + 3, keyTop, cw - 6, keyBottom - keyTop,
                brass, Painter.lighten(brass, 0.35f), Painter.darken(brass, 0.42f));

        // The pin's own top edge, called out hard. This edge meeting the seam is
        // the entire game, so it gets two pixels and the brightest tone in the
        // chamber rather than a polite highlight.
        p.rect(cx + 1, keyTop - 2, cx + cw - 1, keyTop, Painter.lighten(brass, 0.65f));
        p.rect(cx + 2, keyTop - 2, cx + cw - 2, keyTop - 1, Painter.lighten(brass, 0.85f));

        // ---- the part of the key pin that has crossed into the housing
        if (over && !seatedHere) {
            p.rect(cx + 3, keyTop, cx + cw - 3, shear - bandHalf, Painter.withAlpha(COL_BAD, 0x55));
        }


        if (flashing) {
            p.outline(cx - 1, hTop + 1, cw + 2, pBottom - hTop - 2, 1,
                    Painter.withAlpha(flashColor, 0x88));
        }

        // ---- which pin is binding, marked under the plug
        if (isActive) {
            int ax = cx + cw / 2;
            int ay = plugBottom() + 5;
            int glow = Painter.withAlpha(theme().accentLite, 0x80 + Math.round(breath(0.8f) * 0x50));
            p.rect(ax - 3, ay + 2, ax + 3, ay + 4, glow);
            p.rect(ax - 2, ay, ax + 2, ay + 2, glow);
            p.rect(ax - 1, ay - 2, ax + 1, ay, glow);
        }
    }

    /** A pin: flat body, lit left edge, dark right edge. No gradient. */
    private void drawPin(int x, int y, int w, int h, int body, int lite, int dark) {
        if (h <= 0) return;
        p.rect(x - 1, y, x + w + 1, y + h, 0xFF0C0B09);
        p.rect(x, y, x + w, y + h, body);
        p.rect(x, y, x + 2, y + h, lite);
        p.rect(x + w - 2, y, x + w, y + h, dark);
        p.rect(x, y, x + w, y + 1, lite);
    }

    private void drawTension(GuiGraphics g, int x, int y, int w) {
        Theme t = theme();
        p.rect(x - 1, y - 1, x + w + 1, y + 9, t.frameDark);
        p.rect(x, y, x + w, y + 8, 0xFF0B0A09);

        int fill = Math.round(Mth.clamp(tension, 0f, 1f) * (w - 2));
        int col = tension < 0.25f ? COL_BAD : (tension < 0.5f ? 0xFFD9A441 : t.accent);
        for (int i = 0; i < fill; i += 3) {
            p.rect(x + 1 + i, y + 1, x + 1 + Math.min(fill, i + 2), y + 7,
                    Painter.mix(Painter.darken(col, 0.30f), col, i / (float) Math.max(1, w)));
        }
        // wear marks, so the bar reads as a wrench flexing rather than a health bar
        for (int i = x + 8; i < x + w; i += 16) {
            p.rect(i, y + 1, i + 1, y + 7, Painter.withAlpha(0x000000, 0x50));
        }
        smallCentered(g, "tension", x + w / 2, y + 11, t.faint, 0.7f);
    }

    /**
     * The plug end on. The cutaway shows you what to do; this shows you whether
     * it worked, because rotation is the one thing a side view cannot express.
     */
    private void drawPlugFace(GuiGraphics g) {
        Theme t = theme();
        int cx = faceCX(), cy = faceCY(), r = 38;

        p.disc(cx, cy, r + 7, t.frameDark);
        p.ring(cx, cy, r + 6, r + 2, t.frame);
        p.ring(cx, cy, r + 5, r + 3, t.frameLite);
        p.disc(cx, cy, r, Painter.darken(t.panel, 0.20f) | 0xFF000000);

        // the plug face, turned by however many pins are seated
        float angle = plugAngle + plugNudge * 5f;
        int plugR = r - 6;
        p.disc(cx, cy, plugR, Painter.mix(t.frame, t.accent, 0.30f) | 0xFF000000);
        p.ring(cx, cy, plugR, plugR - 2, Painter.darken(t.frame, 0.45f));

        // keyway slot cut into the face, rotating with the plug
        double rad = Math.toRadians(angle - 90);
        float ux = (float) Math.cos(rad), uy = (float) Math.sin(rad);
        float vx = -uy, vy = ux;
        for (int s = -plugR + 8; s <= plugR - 8; s++) {
            // a keyway is wide at the top and narrow at the bottom
            float halfW = s < 0 ? 4.2f : 2.0f;
            float px = cx + ux * s;
            float py = cy + uy * s;
            p.rect(px - vx * halfW - 1, py - vy * halfW - 1,
                    px + vx * halfW + 1, py + vy * halfW + 1, 0xFF0A0908);
        }
        // the wards inside the keyway, so it is not just a slot
        for (int k = -1; k <= 1; k += 2) {
            float px = cx + ux * (k * 6);
            float py = cy + uy * (k * 6);
            p.rect(px - vx * 5 - 1, py - vy * 5 - 1, px - vx * 2, py - vy * 2,
                    Painter.withAlpha(t.frame, 0x99));
        }

        // index mark on the housing, so the turn is measured against something
        p.rect(cx - 2, cy - r - 2, cx + 2, cy - r + 4, t.accentLite);

        // how far round the plug has come
        p.arc(cx, cy, r + 4, -90, -90 + Math.max(1f, plugAngle), 3,
                Painter.withAlpha(COL_GOOD, 0xAA));

        Sigils.drawCentered(p, Sigils.TILE[0], cx, cy - r - 16, 1,
                Painter.withAlpha(t.faint, 0x77), 0, 0);
    }

    @Override
    protected String hintText() {
        if (seated >= pins) return "The plug is free";
        int a = active();
        if (pushing) {
            if (falseTravel[a] > 0f && falseSetArmed) {
                return "The plug gave \u2014 but look where the brass pin is";
            }
            return "Let go the instant the brass pin's top meets the line";
        }

        if (shivering) return "Push pin " + (a + 1);
        if (heartHint() && bindingOrder) {
            return "The heart numbered the chambers \u00b7 hold to push the one marked 1";
        }
        if (spools) return "Hold to push pin " + (a + 1) + " \u00b7 a spool will lie to you once";
        return "Hold SPACE or the mouse to push pin " + (a + 1) + " up to the line";
    }
}
