package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Augury. The one lock in the mod that cannot be beaten by reflexes.
 *
 * <p>Every other ward here asks you to press at the right instant, hold for the
 * right length, or remember a sequence. This one has no clock worth the name and
 * no motion to read. It is a combination of sigil wheels, and all you get for a
 * guess is a count.
 *
 * <p>Set the wheels, submit, and the lock answers with two numbers:
 * <ul>
 *   <li><b>filled marks</b> - sigils that are right and on the right wheel.</li>
 *   <li><b>hollow marks</b> - sigils that belong somewhere in the combination
 *       but not on the wheel you put them on.</li>
 * </ul>
 * The marks are never attached to particular wheels, which is the whole game:
 * "two of these five are right" is a fact about the guess, and turning it into a
 * fact about a wheel is your problem.
 *
 * <p><b>Guesses and lives are separate.</b> The lock gives you a budget of
 * attempts, drawn as tally marks. Spending them all costs one life and buys a
 * fresh budget against <i>the same combination</i>, so a life is time rather
 * than progress: everything you worked out still holds. This is what makes the
 * lock survivable without making it free, and it is why the reward weight sits
 * where it does.
 *
 * <p>Quirks a chest may carry:
 * <ul>
 *   <li>0 wider set - two more sigils in the pool. Costs you a great deal more
 *       than it sounds like.</li>
 *   <li>1 silent tell - only the filled marks are reported. Without the hollow
 *       count you lose all information about sigils that are present but
 *       misplaced, which is most of what makes early guesses worth making.</li>
 *   <li>2 impatient lock - the mechanism will not wait. A bar drains while you
 *       deliberate and eats a guess when it empties.</li>
 * </ul>
 *
 * <p>An ichorheart riding the lock names one wheel outright and holds it there.
 * That is worth roughly a guess and a half, and it is the first thing the heart
 * has ever done that the key could not do more slowly.
 */
@OnlyIn(Dist.CLIENT)
public class AuguryScreen extends BaseMinigameScreen {

    private static final int WHEEL_W = 34;
    private static final int WHEEL_GAP = 8;
    private static final int SUBMIT_W = 44;

    private final boolean widerSet;
    private final boolean silentTell;
    private final boolean impatient;

    private final int wheels;
    private final int palette;
    private final int[] answer;
    /** Wheel the heart gave away, or -1. Locked: there is no reason to change it. */
    private final int gift;

    private final int budget;
    private int spent;

    private final int[] guess;
    private int cursor;

    private final List<Attempt> history = new ArrayList<>();

    private float patience = 1f;
    private final float patienceDrain;

    private float flashTimer;
    private int flashColor = COL_GOOD;

    /**
     * The lock's answer, played rather than only drawn.
     *
     * <p>One rising note per sigil that is exactly right, struck a tenth of a
     * second apart. You hear "three" before you have finished counting the marks,
     * and after a few guesses you stop reading the row at all and just listen for
     * whether this one went up further than the last. Costs nothing: it is the
     * pin-set click at different pitches.
     */
    private int chime;
    private float chimeTimer;

    /** One submitted guess and what the lock said about it. */
    private record Attempt(int[] sigils, int exact, int misplaced) {
    }

    public AuguryScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.augury"), msg);

        boolean[] v = pickVariants(3, 61);
        widerSet = v[0];
        silentTell = v[1];
        impatient = v[2];
        if (widerSet) modifierLabels.add("wider set");
        if (silentTell) modifierLabels.add("silent tell");
        if (impatient) modifierLabels.add("impatient lock");

        int baseWheels = 3 + Math.round((value / 100f) * 2f * difficulty);
        // and a wheel with it, because one more sigil across three wheels is a
        // smaller change than it sounds and the player should feel the lock get
        // bigger rather than merely denser.
        this.wheels = wobble(baseWheels + (widerSet ? 1 : 0), 1, 62, 3, 6);

        // The cap used to be 8 and the base already reached it on a good chest,
        // so "two more sigils" frequently meant no more sigils at all. The base
        // is lower now and the variant raises the ceiling as well as the count.
        int basePalette = 4 + Math.round((value / 100f) * 1.5f * difficulty);
        // Four more sigils, not three, and the cap moves with it. The search
        // space is what this variant is, so a change that the cap eats is not a
        // change at all.
        this.palette = Mth.clamp(basePalette + (widerSet ? 4 : 0), 4, widerSet ? 11 : 6);

        Random rng = new Random(seed ^ 0x5A19C7E3L);
        this.answer = new int[wheels];
        for (int i = 0; i < wheels; i++) answer[i] = rng.nextInt(palette);

        this.gift = heartHint() ? rng.nextInt(wheels) : -1;

        this.guess = new int[wheels];
        for (int i = 0; i < wheels; i++) guess[i] = 0;
        if (gift >= 0) guess[gift] = answer[gift];

        // Enough attempts to actually deduce with, scaled down as the lock gets
        // harder. Three wheels of four sigils is 64 combinations and falls to
        // simple elimination; five of eight is 32768 and needs every guess it
        // can get, so the budget grows with the search space rather than with a
        // flat number.
        // Was wheels + palette/2 + 2, which the solver simulation showed needing
        // only about half of. A budget you never spend is not a budget, and a
        // life that buys a fresh one made the surplus doubly generous.
        int room = wheels + palette / 3;
        this.budget = Math.max(4, Math.round(room / Mth.clamp(difficulty, 0.7f, 1.8f)));

        this.spent = Mth.clamp(progress, 0, budget - 1);
        this.cursor = gift == 0 && wheels > 1 ? 1 : 0;

        // Generous: the impatient variant is meant to stop you stalling forever,
        // not to turn a thinking lock into a timing one.
        this.patienceDrain = impatient ? 1f / (8.5f / Mth.clamp(difficulty, 0.7f, 1.8f)) : 0f;
    }

    /**
     * Deduction needs room to think. Still finite: the impatient variant
     * exists precisely because unlimited thinking was the problem.
     */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.80f;
    }

    /**
     * Nothing on this lock moves, so a shroud drifting over it just hides the
     * readings you have already taken, and there is no skill in remembering a
     * table. The impatient variant is this lock's version of a clock.
     */
    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.HURRIED && a != Anomaly.UNLIT;
    }

    @Override
    protected Theme createTheme() {
        return Theme.augury();
    }

    @Override
    protected int panelWidth() {
        return 320;
    }

    @Override
    protected int panelHeight() {
        return 252;
    }

    // ------------------------------------------------------------------ simulation

    @Override
    protected void step(float dt) {
        if (flashTimer > 0f) flashTimer -= dt;

        if (chime > 0) {
            chimeTimer -= dt;
            if (chimeTimer <= 0f) {
                int note = wheels - chime;                  // count up, not down
                Sfx.play(WardSounds.PIN_SET, 0.55f,
                        0.90f + note * 0.13f);
                chime--;
                chimeTimer = 0.11f;
            }
        }

        if (!impatient || resolved) return;

        patience -= patienceDrain * dt;
        if (patience <= 0f) {
            patience = 1f;
            Sfx.markBad();
            spendGuess("The mechanism would not wait");
        }
    }

    /**
     * Counts a guess against the combination.
     *
     * <p>Two passes, because a sigil already claimed as an exact match must not
     * be counted again as a misplaced one. Doing it in a single pass is the
     * classic way to get this wrong and it produces counts that are quietly
     * impossible to reason from.
     */
    private Attempt judge(int[] g) {
        boolean[] answerUsed = new boolean[wheels];
        boolean[] guessUsed = new boolean[wheels];
        int exact = 0;
        for (int i = 0; i < wheels; i++) {
            if (g[i] == answer[i]) {
                exact++;
                answerUsed[i] = true;
                guessUsed[i] = true;
            }
        }
        int misplaced = 0;
        for (int i = 0; i < wheels; i++) {
            if (guessUsed[i]) continue;
            for (int j = 0; j < wheels; j++) {
                if (answerUsed[j] || g[i] != answer[j]) continue;
                misplaced++;
                answerUsed[j] = true;
                break;
            }
        }
        return new Attempt(g.clone(), exact, misplaced);
    }

    private void submit() {
        if (resolved) return;

        Attempt a = judge(guess);
        patience = 1f;

        // On a silent lock every other reading is withheld. The guess is still
        // spent, which is the whole point: you choose which one to spend the
        // answer on.
        boolean answered = !silentTell || (spent % 2 == 0) || a.exact() == wheels;

        // The chime speaks the count, so it has to be silent too or the variant
        // leaks through the audio it was supposed to withhold.
        chime = answered ? a.exact() : 0;
        chimeTimer = 0f;

        history.add(0, answered ? a : new Attempt(guess.clone(), -1, -1));
        if (history.size() > 6) history.remove(history.size() - 1);

        if (a.exact() == wheels) {
            flashColor = COL_GOOD;
            flashTimer = 0.6f;
            // Performance is how much of the budget you did not need.
            succeed(Mth.clamp(1f - spent / (float) Math.max(1, budget - 1), 0f, 1f));
            return;
        }

        flashColor = COL_BAD;
        flashTimer = 0.4f;
        if (a.exact() == 0) Sfx.markBad();
        spendGuess(null);
    }

    private void spendGuess(String why) {
        spent++;
        progress = spent;
        if (spent < budget) {
            if (why != null) showBanner(why, COL_BAD, 900);
            return;
        }
        // Out of attempts. Costs a life, but the combination stays put, so
        // everything already deduced is still worth what it was.
        spent = 0;
        progress = 0;
        Sfx.play(WardSounds.WRENCH_SLIP, 0.6f, 1.15f);
        loseLife("The wards reset \u2014 but not the combination");
    }

    private void cycle(int wheel, int delta) {
        if (wheel == gift || resolved) return;
        guess[wheel] = Math.floorMod(guess[wheel] + delta, palette);
        Sfx.play(WardSounds.PLUG_TURN, 0.35f, 1.25f + delta * 0.08f);
    }

    // ------------------------------------------------------------------ input

    private int wheelAt(double mx, double my) {
        int y = wheelY();
        if (my < y || my > y + WHEEL_W) return -1;
        for (int i = 0; i < wheels; i++) {
            int x = wheelX(i);
            if (mx >= x && mx <= x + WHEEL_W) return i;
        }
        return -1;
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int w = wheelAt(mx, my);
        if (w >= 0) {
            cursor = w;
            cycle(w, button == 1 ? -1 : 1);
            return true;
        }
        // Only the plate submits. Letting the rest of the panel do it meant a
        // stray click spent an attempt, on the one lock in the mod where nothing
        // else punishes the mouse.
        if (mx >= submitX() && mx <= submitX() + SUBMIT_W
                && my >= wheelY() && my <= wheelY() + WHEEL_W) {
            submit();
        }
        return true;
    }

    @Override
    protected boolean onKey(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                cursor = Math.floorMod(cursor - 1, wheels);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                cursor = Math.floorMod(cursor + 1, wheels);
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                cycle(cursor, 1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                cycle(cursor, -1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                submit();
                return true;
            }
            default -> {
                if (isActionKey(key)) {
                    submit();
                    return true;
                }
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ layout

    private int rowW() {
        return wheels * WHEEL_W + (wheels - 1) * WHEEL_GAP;
    }

    /**
     * Left edge of the wheel row. The read plate sits to the right of the
     * wheels, so the row is offset left by half of it: centring the wheels alone
     * would leave the whole assembly visibly hanging off one side.
     */
    private int rowLeft() {
        return centerX() - (rowW() + 8 + SUBMIT_W) / 2;
    }

    private int wheelX(int i) {
        return rowLeft() + i * (WHEEL_W + WHEEL_GAP);
    }

    private int wheelY() {
        return contentTop() + 27;
    }

    private int submitX() {
        return rowLeft() + rowW() + 8;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int wy = wheelY();

        smallCentered(g, silentTell ? "it answers every other reading \u2014 choose which"
                        : "how many are right, how many belong",
                centerX(), contentTop() + 4, t.faint, 0.75f);

        // ---- the wheels
        for (int i = 0; i < wheels; i++) {
            drawWheel(i, wheelX(i), wy);
        }

        drawSubmit(g, mouseX, mouseY, submitX(), wy);

        // The impatient clock now sits above the moving parts. It used to be
        // wedged between the guess tallies and the history table, where the
        // three readouts became one line of pixels on smaller GUI scales.
        if (impatient) {
            drawPatience(rowLeft(), contentTop() + 15, rowW() + 8 + SUBMIT_W);
        }

        // ---- attempts remaining, as tally marks scratched into the plate
        drawTallies(g, rowLeft() + rowW() / 2, wy + WHEEL_W + 12);

        // ---- what the lock has already told you
        int hy = wy + WHEEL_W + 28;
        drawHistory(g, hy);
    }

    private void drawWheel(int i, int x, int y) {
        Theme t = theme();
        boolean here = i == cursor;
        boolean given = i == gift;

        int face = given
                ? Painter.mix(t.well, COL_GOOD, 0.16f) | 0xFF000000
                : t.well;

        p.roundRect(x - 2, y - 2, WHEEL_W + 4, WHEEL_W + 4, t.frameDark);
        p.roundRect(x, y, WHEEL_W, WHEEL_W, face);
        p.scanlines(x + 1, y + 1, WHEEL_W - 2, WHEEL_W - 2, Painter.withAlpha(0x000000, 0x22));

        int glyph = given ? COL_GOOD : (here ? t.accentLite : t.accent);
        Sigils.drawCentered(p, Sigils.TILE[guess[i] % Sigils.TILE.length],
                x + WHEEL_W / 2, y + WHEEL_W / 2, 2,
                glyph, Painter.darken(glyph, 0.35f), 0x77000000);

        if (given) {
            // the heart's tell: named and pinned, with the seam of the wheel
            // welded shut so it cannot be turned away by accident
            p.outline(x, y, WHEEL_W, WHEEL_W, 1, Painter.withAlpha(COL_GOOD, 0xAA));
            p.rect(x + WHEEL_W / 2 - 4, y + WHEEL_W + 2, x + WHEEL_W / 2 + 4, y + WHEEL_W + 4,
                    Painter.withAlpha(COL_GOOD, 0xCC));
        } else if (here) {
            int glow = Painter.withAlpha(t.accentLite, 0x88 + Math.round(breath(0.9f) * 0x55));
            p.outline(x - 2, y - 2, WHEEL_W + 4, WHEEL_W + 4, 1, glow);
            // little arrows saying this one turns
            for (int k = 0; k < 3; k++) {
                p.rect(x + WHEEL_W / 2 - k, y - 6 + k, x + WHEEL_W / 2 + k + 1, y - 5 + k, glow);
                p.rect(x + WHEEL_W / 2 - k, y + WHEEL_W + 5 - k,
                        x + WHEEL_W / 2 + k + 1, y + WHEEL_W + 6 - k, glow);
            }
        }

        if (flashTimer > 0f) {
            p.outline(x - 2, y - 2, WHEEL_W + 4, WHEEL_W + 4, 1,
                    Painter.withAlpha(flashColor, Math.round(flashTimer * 0x90)));
        }
    }

    /** The plate that reads the lock. The only thing on the panel that spends an attempt. */
    private void drawSubmit(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        Theme t = theme();
        boolean hot = mouseX >= x && mouseX <= x + SUBMIT_W && mouseY >= y && mouseY <= y + WHEEL_W;

        p.roundRect(x - 2, y - 2, SUBMIT_W + 4, WHEEL_W + 4, t.frameDark);
        p.roundRect(x, y, SUBMIT_W, WHEEL_W,
                hot ? Painter.mix(t.well, t.accent, 0.22f) | 0xFF000000 : t.well);
        p.rect(x + 1, y + 1, x + SUBMIT_W - 1, y + 2, Painter.withAlpha(t.accent, 0x44));
        if (hot) {
            p.outline(x - 2, y - 2, SUBMIT_W + 4, WHEEL_W + 4, 1,
                    Painter.withAlpha(t.accentLite, 0xAA));
        }

        smallCentered(g, "read", x + SUBMIT_W / 2, y + WHEEL_W / 2 - 8,
                hot ? t.accentLite : t.accent, 0.85f);
        smallCentered(g, "enter", x + SUBMIT_W / 2, y + WHEEL_W / 2 + 2, t.faint, 0.7f);
    }

    private void drawTallies(GuiGraphics g, int cx, int y) {
        Theme t = theme();
        int left = spent >= budget ? 0 : budget - spent;
        int span = budget * 7;
        int x0 = cx - span / 2;

        for (int i = 0; i < budget; i++) {
            boolean used = i >= left;
            int col = used ? Painter.withAlpha(t.frame, 0x55) : t.accent;
            p.rect(x0 + i * 7, y, x0 + i * 7 + 2, y + 9, col);
            if (used) {
                // struck through rather than removed, so the shape of how the
                // lock is going stays visible
                p.rect(x0 + i * 7 - 1, y + 4, x0 + i * 7 + 4, y + 5,
                        Painter.withAlpha(COL_BAD, 0x99));
            }
        }
        smallCentered(g, left + " guess" + (left == 1 ? "" : "es") + " before a life",
                cx, y + 11, left <= 2 ? COL_BAD : t.dim, 0.7f);
    }

    private void drawPatience(int x, int y, int w) {
        Theme t = theme();
        p.rect(x, y, x + w, y + 4, 0xFF0B0A09);
        int fill = Math.round(Mth.clamp(patience, 0f, 1f) * (w - 2));
        p.rect(x + 1, y + 1, x + 1 + fill, y + 3,
                patience < 0.3f ? COL_BAD : Painter.darken(t.accent, 0.15f));
    }

    private void drawHistory(GuiGraphics g, int y) {
        Theme t = theme();
        if (history.isEmpty()) {
            smallCentered(g, "no readings yet", centerX(), y + 8, t.faint, 0.8f);
            return;
        }

        int rowH = 15;
        int rows = Math.min(history.size(), Math.max(1, (contentBottom() - y - 4) / rowH));

        for (int r = 0; r < rows; r++) {
            Attempt a = history.get(r);
            int ry = y + r * rowH;
            int alpha = r == 0 ? 0xFF : 0xFF - Math.min(0xA0, r * 0x28);

            int x = rowLeft();
            for (int i = 0; i < wheels; i++) {
                int gx = x + i * 13;
                p.rect(gx, ry, gx + 11, ry + 11, Painter.withAlpha(t.well, alpha));
                Sigils.drawCentered(p, Sigils.TILE[a.sigils()[i] % Sigils.TILE.length],
                        gx + 5, ry + 5, 1,
                        Painter.withAlpha(t.accent, alpha),
                        Painter.withAlpha(Painter.darken(t.accent, 0.4f), alpha), 0);
            }

            int mx = x + wheels * 13 + 8;
            if (a.exact() < 0) {
                // it did not answer this one
                p.rect(mx, ry + 5, mx + 26, ry + 6, Painter.withAlpha(t.faint, alpha));
                continue;
            }

            // the answer: filled marks first, then hollow ones
            for (int k = 0; k < a.exact(); k++) {
                p.disc(mx + k * 8 + 3, ry + 5, 3, Painter.withAlpha(COL_GOOD, alpha));
            }
            int base = mx + a.exact() * 8;
            for (int k = 0; k < a.misplaced(); k++) {
                p.ring(base + k * 8 + 3, ry + 5, 3, 1, Painter.withAlpha(t.accentLite, alpha));
            }
        }
    }

    @Override
    protected String hintText() {
        if (gift >= 0 && history.isEmpty()) {
            return "The heart named wheel " + (gift + 1) + " \u00b7 arrows to turn, Enter to read";
        }
        if (history.isEmpty()) {
            return silentTell
                    ? "Set the wheels and read the lock \u2014 it counts only what is exactly right"
                    : "Set the wheels and read the lock \u00b7 filled = right place, hollow = right sigil";
        }
        return "Arrows or click to turn a wheel \u00b7 listen: one note per sigil in place";
    }
}
