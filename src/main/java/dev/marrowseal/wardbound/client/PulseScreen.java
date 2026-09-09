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
 * Something behind the lid is beating and the lock opens on the beat. Marks slide toward the strike
 * line; hit each one as it crosses.
 *
 * <p>The whole lock hangs on input accuracy, which is only fair now that a press is timed against
 * the moment it happened rather than the last frame drawn. Judging is in milliseconds, not pixels:
 * a perfect is a 55 ms window, a good is 130 ms.
 *
 * <p>Quirks:
 * <ul>
 *   <li>0 double time - the tempo climbs through the second half</li>
 *   <li>1 ghost marks - some marks fade out before they reach the line</li>
 *   <li>2 off beat - syncopated marks land between the beats</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class PulseScreen extends BaseMinigameScreen {

    private static final float PERFECT = 0.055f;
    private static final float GOOD = 0.130f;
    private static final float LEAD_IN = 1.15f;

    /**
     * The lock plays no sound on a hit, only on a miss.
     *
     * <p>Replaces "double time", which multiplied a tempo the phrasing already
     * varies and so read as nothing. Taking the confirmation away matters far
     * more than any speed change: you stop playing to the click you hear back
     * and start playing to the lane you can see.
     */
    private final boolean muffled;
    private final boolean ghosts;
    /**
     * Some strikes are doubles: two marks arriving together that must be taken
     * with two presses inside the same window.
     *
     * <p>Replaces "off beat", which inserted extra notes at half-steps and was
     * indistinguishable from the ordinary phrasing the lane already has. A
     * double is the one thing a rhythm lane can ask that a single stream of
     * marks cannot: not when to press, but how many times.
     */
    private final boolean chords;

    private final float[] noteTime;
    private final boolean[] noteGhost;
    private final byte[] noteState; // 0 waiting, 1 perfect, 2 good, 3 missed
    private final int noteCount;
    private final int visibleCount;

    private final float pixelsPerSecond;

    private float songTime = -LEAD_IN;
    private int nextNote;
    private int combo;
    private int bestCombo;
    private int perfects;
    private int goods;

    private float flashTimer;
    private int flashColor = COL_GOOD;
    private String judgement = "";

    public PulseScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.pulse"), msg);

        boolean[] v = pickVariants(3, 8);
        muffled = v[0];
        ghosts = v[1];
        chords = v[2];
        if (muffled) modifierLabels.add("muffled");
        if (ghosts) modifierLabels.add("ghost marks");
        if (chords) modifierLabels.add("chords");

        Random rng = new Random(seed ^ 0xBEA7L);

        int count = wobble(8 + Math.round(value / 11f * difficulty), 3, 23, 7, 20);
        float beat = Mth.clamp(0.78f / difficulty * (0.9f + seedVariance(61) * 0.3f), 0.34f, 0.95f);

        java.util.ArrayList<Float> times = new java.util.ArrayList<>();
        float tCursor = 0f;
        // The lock used to run at one tempo with the odd doubled gap, which made
        // it a metronome you sync to once and then stop reading. It now moves in
        // phrases: a stretch of steady beat, then a change of pace that holds
        // long enough to learn before it changes again. You are following a
        // thing that is breathing rather than counting a clock.
        float pace = 1f;
        int phraseLeft = 0;
        for (int i = 0; i < count; i++) {
            if (phraseLeft <= 0) {
                phraseLeft = 2 + rng.nextInt(3);
                // never two identical phrases running, and never so slow that it
                // stops feeling like a pulse
                float next;
                do {
                    next = new float[]{0.62f, 0.78f, 1f, 1f, 1.35f, 1.7f}[rng.nextInt(6)];
                } while (Math.abs(next - pace) < 0.05f);
                pace = next;
            }
            phraseLeft--;

            float step = beat * pace;

            // a held gap on the turn of a phrase, where it is felt most
            if (i > 0 && rng.nextFloat() < 0.13f) step *= 2f;
            tCursor += step;
            times.add(tCursor);
            if (chords && i > 1 && rng.nextFloat() < 0.26f) {
                // a double: the same instant, twice
                times.add(tCursor);
            }
            // a stutter: two strikes almost on top of each other
            if (i > 2 && rng.nextFloat() < 0.10f) {
                times.add(tCursor + step * 0.28f);
            }
        }

        this.noteCount = times.size();
        this.noteTime = new float[noteCount];
        this.noteGhost = new boolean[noteCount];
        this.noteState = new byte[noteCount];
        boolean lastGhost = false;
        int visible = 0;
        for (int i = 0; i < noteCount; i++) {
            noteTime[i] = times.get(i);
            boolean makeGhost = ghosts && i > 4 && !lastGhost && rng.nextFloat() < 0.22f;
            noteGhost[i] = makeGhost;
            if (!makeGhost) visible++;
            lastGhost = makeGhost;
        }
        this.visibleCount = Math.max(1, visible);

        this.pixelsPerSecond = Mth.clamp(96f * (0.9f + difficulty * 0.25f), 90f, 190f);
    }

    /**
     * The lane already runs to a fixed length, so a clock only cuts the song
     * off mid-phrase, and a shroud over a scrolling lane is unreadable rather
     * than hard.
     */
    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.HURRIED && a != Anomaly.SHROUDED;
    }

    /** Short. Every one of these was outlastable. */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.75f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.flesh();
    }

    @Override
    protected void step(float dt) {
        songTime += dt;
        if (flashTimer > 0f) flashTimer -= dt;

        while (nextNote < noteCount && songTime > noteTime[nextNote] + GOOD) {
            if (noteState[nextNote] == 0) {
                noteState[nextNote] = 3;
                combo = 0;
                if (noteGhost[nextNote]) {
                    // A fully invisible memory note may lower score / break combo,
                    // but it can never take a life. This removes "MISS" events
                    // with literally no visible target on screen.
                    judgement = "GHOST PASSED";
                    flashTimer = 0.18f;
                    flashColor = theme().dim;
                } else {
                    judgement = "MISS";
                    flashTimer = 0.3f;
                    flashColor = COL_BAD;
                    if (!loseLife("The beat slipped past")) return;
                }
            }
            nextNote++;
        }

        if (nextNote >= noteCount && !resolved) {
            int landed = perfects + goods;
            if (landed == 0) {
                failOut();
            } else {
                float perf = (perfects + goods * 0.55f) / visibleCount;
                succeed(Mth.clamp(perf, 0.1f, 1f));
            }
        }
    }

    private void strike() {
        int best = -1;
        float bestErr = Float.MAX_VALUE;
        boolean bestIsGhost = true;
        for (int i = nextNote; i < noteCount; i++) {
            if (noteState[i] != 0) continue;
            float err = Math.abs(noteTime[i] - songTime);
            if (err <= GOOD) {
                boolean ghost = noteGhost[i];
                // An invisible ghost must never steal the press intended for a
                // visible mark arriving in the same timing window.
                if (best < 0 || (bestIsGhost && !ghost) || (bestIsGhost == ghost && err < bestErr)) {
                    bestErr = err;
                    best = i;
                    bestIsGhost = ghost;
                }
            }
            if (noteTime[i] - songTime > GOOD) break;
        }

        if (best < 0 || bestErr > GOOD) {
            combo = 0;
            judgement = "EARLY";
            flashTimer = 0.3f;
            flashColor = COL_BAD;
            loseLife("Struck off the beat");
            return;
        }

        if (bestErr <= PERFECT) {
            noteState[best] = 1;
            perfects++;
            judgement = "PERFECT";
            flashColor = COL_GOOD;
            if (!muffled) Sfx.play(WardSounds.PIN_SET, 0.62f, 1.32f);
        } else {
            noteState[best] = 2;
            goods++;
            judgement = "GOOD";
            flashColor = theme().accentLite;
            if (!muffled) Sfx.mark(0);
        }
        combo++;
        bestCombo = Math.max(bestCombo, combo);
        progress = perfects + goods;
        flashTimer = 0.3f;
        burst(strikeX(), laneY() + laneH() / 2, flashColor);
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

    // ------------------------------------------------------------------ layout

    /** Draws a rect trimmed to the lane, so nothing is ever painted beside it. */
    private void clipRect(int x1, int y1, int x2, int y2, int lo, int hi, int argb) {
        int a = Math.max(x1, lo), b = Math.min(x2, hi);
        if (b > a) p.rect(a, y1, b, y2, argb);
    }

    private int laneX() {
        return left() + 22;
    }

    private int laneW() {
        return panelWidth() - 44;
    }

    private int laneY() {
        return contentCenterY() - 26;
    }

    private int laneH() {
        return 52;
    }

    private int strikeX() {
        return laneX() + 52;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int x = laneX(), w = laneW(), y = laneY(), h = laneH();
        int sx = strikeX();

        well(x, y, w, h);
        p.dither(x + 1, y + 1, w - 2, h - 2, Painter.withAlpha(t.accent, 0x0C), 2, 0);

        // Beat grid, drawn from the note times so it lines up with what you are
        // hearing - but never under a ghost. The grid line was the bug: the note
        // faded out exactly as intended and left a tick sitting on the lane
        // saying where it had gone, so the variant cost nothing.
        for (int i = 0; i < noteCount; i++) {
            if (noteGhost[i]) continue;
            int nx = sx + Math.round((noteTime[i] - songTime) * pixelsPerSecond);
            if (nx < x || nx > x + w) continue;
            p.rect(nx, y + 2, nx + 1, y + h - 2, Painter.withAlpha(t.frame, 0x28));
        }

        // the strike line
        int lineCol = flashTimer > 0f ? flashColor : t.accentLite;
        p.rect(sx - 9, y - 5, sx + 9, y + h + 5, Painter.withAlpha(lineCol, 0x14));
        p.rect(sx - 3, y - 5, sx + 3, y + h + 5, Painter.darken(lineCol, 0.55f));
        p.rect(sx - 2, y - 5, sx + 2, y + h + 5, lineCol);
        // the good window, shown as a pair of guides so the timing is legible
        int gw = Math.round(GOOD * pixelsPerSecond);
        p.rect(sx - gw, y + 1, sx - gw + 1, y + h - 1, Painter.withAlpha(lineCol, 0x55));
        p.rect(sx + gw - 1, y + 1, sx + gw, y + h - 1, Painter.withAlpha(lineCol, 0x55));
        p.rect(sx - 6, y - 9, sx + 6, y - 5, lineCol);
        p.rect(sx - 6, y + h + 5, sx + 6, y + h + 9, lineCol);

        // the marks
        for (int i = 0; i < noteCount; i++) {
            float dt = noteTime[i] - songTime;
            int nx = sx + Math.round(dt * pixelsPerSecond);
            // Clipped to the lane. Was x-20..x+w+20, so a mark's first and last
            // frames were painted outside the well it is supposed to be
            // travelling down, which looked like the sprite escaping the box.
            if (nx + 9 < x || nx - 9 > x + w) continue;

            byte st = noteState[i];
            if (st == 1 || st == 2) continue;

            int alpha = 0xFF;
            if (noteGhost[i]) {
                // Gone well before the line, and gone completely.
                //
                // It used to bottom out at 0x30 a quarter of a second out, which
                // is both late and still perfectly visible: you could read the
                // ghost right up to the strike and the variant cost nothing. It
                // now reaches zero roughly half a second early, so the last
                // stretch really is played from memory.
                float fade = Mth.clamp((dt - 0.55f) / 0.85f, 0f, 1f);
                alpha = Math.round(0xFF * fade * fade);
            }
            if (st == 3) alpha = Math.min(alpha, 0x50);

            int col = st == 3 ? COL_BAD : t.accent;
            int cy = y + h / 2;
            int lo = x + 1, hi = x + w - 1;
            clipRect(nx - 8, cy - 13, nx + 8, cy + 13, lo, hi, Painter.withAlpha(0x000000, alpha / 3));
            clipRect(nx - 7, cy - 12, nx + 7, cy + 12, lo, hi,
                    Painter.withAlpha(Painter.darken(col, 0.45f), alpha));
            clipRect(nx - 5, cy - 10, nx + 5, cy + 10, lo, hi, Painter.withAlpha(col, alpha));
            clipRect(nx - 5, cy - 10, nx + 1, cy - 4, lo, hi,
                    Painter.withAlpha(Painter.lighten(col, 0.4f), alpha));
            if (nx - 4 >= lo && nx + 4 <= hi) {
                Sigils.drawCentered(p, Sigils.TILE[0], nx, cy, 1,
                        Painter.withAlpha(0x120A0C, alpha), 0, 0);
            }
        }

        // combo and judgement
        if (combo > 1) {
            centered(g, combo + "x", sx, y - 24, t.accentLite);
        }
        if (flashTimer > 0f && !judgement.isEmpty()) {
            smallCentered(g, judgement, centerX(), y + h + 14, flashColor, 1.1f);
        }

        int visibleHit = 0;
        int ghostHit = 0;
        for (int i = 0; i < noteCount; i++) {
            if (noteState[i] != 1 && noteState[i] != 2) continue;
            if (noteGhost[i]) ghostHit++; else visibleHit++;
        }
        String resultLine = visibleHit + " / " + visibleCount + " visible   ·   " + perfects + " perfect   ·   best " + bestCombo + "x";
        if (ghostHit > 0) resultLine += "   ·   +" + ghostHit + " ghost";
        smallCentered(g, resultLine, centerX(), y + h + 28, t.faint, 0.9f);

    }

    @Override
    protected String hintText() {
        if (songTime < 0f) return "Listen \u2014 the first mark is on its way";
        return "SPACE or click when each visible mark crosses the strike line";
    }
}
