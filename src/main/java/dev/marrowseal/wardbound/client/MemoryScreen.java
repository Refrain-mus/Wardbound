package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * An old warding seal. The sigils light in order and you trace them back.
 *
 * <p>The marks used to be leftover Latin-1 characters from the font; they are hand-drawn pixel
 * sigils now, which is what lets the "shuffling tiles" quirk work at all - you have to remember a
 * shape, and a shape has to be worth remembering.
 *
 * <p>Quirks: reversed order, shuffling tiles, rapid flicker.
 */
@OnlyIn(Dist.CLIENT)
public class MemoryScreen extends BaseMinigameScreen {

    private static final int COLS = 3;
    private static final int ROWS = 3;
    private static final int CELLS = COLS * ROWS;
    private static final int SIZE = 38;
    private static final int PAD = 8;

    private final boolean reversed;
    private final boolean shuffled;
    private final boolean rapid;

    private final int rounds;
    private final float showTime;
    private final float gapTime;

    /** cellMarks[cell] = which sigil sits on that tile right now. */
    private int[] cellMarks;
    /** the sequence, stored as sigils rather than tiles. */
    private int[] sequence;

    private int showIndex;
    private float showTimer;
    private boolean showing;
    private boolean lit;
    private int inputIndex;
    private int pressedCell = -1;
    private int pressColor = COL_GOOD;
    private float pressTimer;

    private int correct;
    private int wrong;

    public MemoryScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.memory"), msg);

        boolean[] v = pickVariants(3, 3);
        reversed = v[0];
        shuffled = v[1];
        rapid = v[2];
        if (reversed) modifierLabels.add("reversed order");
        if (shuffled) modifierLabels.add("shuffling tiles");
        if (rapid) modifierLabels.add("rapid flicker");

        this.rounds = wobble(2 + Math.round(value / 30f * difficulty), 1, 19, 2, 5);
        float variance = 0.85f + seedVariance(21) * 0.35f;
        float base = 0.52f / difficulty * variance * (rapid ? 0.66f : 1f);
        // Floor raised from 0.18s. Under about a third of a second a symbol is
        // gone before the eye has finished landing on it, so the opening of the
        // sequence was not a memory test, it was a test of whether you happened
        // to already be looking at the right tile.
        this.showTime = Mth.clamp(base * 1.25f, 0.34f, 0.85f);
        this.gapTime = Math.max(0.14f, showTime * 0.34f);
        buildRound();
    }

    /**
     * The sequence has to be shown before you can start.
     */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 1.15f;
    }

    /**
     * The sequence has to be seen to be remembered. A shroud or a guttering lamp
     * during the reveal does not make the lock harder, it makes it random.
     */
    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING;
    }

    @Override
    protected Theme createTheme() {
        return Theme.basalt();
    }

    /** The 3x3 board needs more vertical room than the dial-based locks. */
    @Override
    protected int panelHeight() {
        return 240;
    }

    private void buildRound() {
        Random rng = new Random(seed + progress * 7919L);

        cellMarks = new int[CELLS];
        for (int i = 0; i < CELLS; i++) cellMarks[i] = i;

        int len = Math.min(7, 3 + progress);
        sequence = new int[len];
        int last = -1;
        for (int i = 0; i < len; i++) {
            int v = rng.nextInt(CELLS);
            if (v == last) v = (v + 1 + rng.nextInt(CELLS - 1)) % CELLS;
            sequence[i] = v;
            last = v;
        }
        showIndex = 0;
        // A beat of darkness before the first tile lights.
        //
        // Without it the sequence restarted with the first tile already on, so
        // after a mistake you could not tell whether you were seeing the new
        // first tile or the tail of the old sequence. Worst on rapid flicker,
        // where everything is short anyway.
        lit = false;
        showTimer = Math.max(gapTime, 0.45f);
        // A beat before the first symbol, so the sequence never starts while the
        // screen is still appearing.
        showTimer = 0.85f;
        showing = true;
        lit = true;
        inputIndex = 0;
    }

    private void shuffleMarks() {
        List<Integer> marks = new ArrayList<>();
        for (int m : cellMarks) marks.add(m);
        Collections.shuffle(marks, new Random(seed + progress * 104729L));
        for (int i = 0; i < CELLS; i++) cellMarks[i] = marks.get(i);
    }

    private int cellOfMark(int mark) {
        for (int i = 0; i < CELLS; i++) if (cellMarks[i] == mark) return i;
        return 0;
    }

    private int expectedMark() {
        return reversed ? sequence[sequence.length - 1 - inputIndex] : sequence[inputIndex];
    }

    private int gridX() {
        return left() + (panelWidth() - (COLS * SIZE + (COLS - 1) * PAD)) / 2;
    }

    private int gridY() {
        int totalH = ROWS * SIZE + (ROWS - 1) * PAD;
        return contentTop() + (contentBottom() - contentTop() - totalH) / 2;
    }

    private int cellAt(double mx, double my) {
        int gx = gridX(), gy = gridY();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = gx + c * (SIZE + PAD);
                int y = gy + r * (SIZE + PAD);
                if (mx >= x && mx < x + SIZE && my >= y && my < y + SIZE) return r * COLS + c;
            }
        }
        return -1;
    }

    @Override
    protected void step(float dt) {
        if (showing) {
            showTimer -= dt;
            if (showTimer <= 0f) {
                if (lit) {
                    lit = false;
                    showTimer = gapTime;
                    showIndex++;
                    if (showIndex >= sequence.length) {
                        showing = false;
                        if (shuffled) shuffleMarks();
                    }
                } else {
                    lit = true;
                    // The last tile of the sequence is held. It is the one you have had
            // the least time with and the one you are most likely to lose.
            showTimer = showIndex >= sequence.length - 1 ? showTime + 1.0f : showTime;
                }
            }
        }
        if (pressTimer > 0f) pressTimer -= dt;
        else pressedCell = -1;
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (showing || button != 0) return false;
        int cell = cellAt(mx, my);
        if (cell < 0) return false;

        pressedCell = cell;
        pressTimer = 0.22f;

        if (cellMarks[cell] == expectedMark()) {
            pressColor = COL_GOOD;
            correct++;
            inputIndex++;
            Sfx.mark(inputIndex);
            burst(gridX() + (cell % COLS) * (SIZE + PAD) + SIZE / 2,
                    gridY() + (cell / COLS) * (SIZE + PAD) + SIZE / 2, COL_GOOD);
            if (inputIndex >= sequence.length) {
                progress++;
                if (progress >= rounds) {
                    int total = correct + wrong;
                    succeed(total == 0 ? 1f : (float) correct / total);
                } else {
                    buildRound();
                }
            }
        } else {
            pressColor = COL_BAD;
            wrong++;
            if (loseLife("Seal broken")) buildRound();
        }
        return true;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int gx = gridX(), gy = gridY();
        int litCell = (showing && lit && showIndex < sequence.length)
                ? cellOfMark(sequence[showIndex]) : -1;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = r * COLS + c;
                int x = gx + c * (SIZE + PAD);
                int y = gy + r * (SIZE + PAD);
                boolean hover = !showing && mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
                boolean active = idx == litCell;
                boolean pressed = idx == pressedCell && pressTimer > 0f;

                int border = active ? t.accentLite : (pressed ? pressColor : (hover ? t.frame : t.wellEdge));
                int body = active ? Painter.withAlpha(t.accent, 0x5C) : (hover ? t.panelLite : t.well);

                if (active) {
                    p.roundRect(x - 5, y - 5, SIZE + 10, SIZE + 10, Painter.withAlpha(t.accent, 0x1C));
                }
                p.roundRect(x - 2, y - 2, SIZE + 4, SIZE + 4, t.edge);
                p.roundRect(x - 1, y - 1, SIZE + 2, SIZE + 2, border);
                p.roundRect(x, y, SIZE, SIZE, body);
                p.rect(x + 1, y + 1, x + SIZE - 1, y + 2, Painter.withAlpha(0xFFFFFF, active ? 0x24 : 0x0C));
                rivet(x + 5, y + 5);
                rivet(x + SIZE - 5, y + SIZE - 5);

                int sigilCol = active ? 0xFFFFFFFF : (hover ? t.text : t.dim);
                int sigilHalf = active ? Painter.withAlpha(t.accentLite, 0xCC) : Painter.withAlpha(t.faint, 0x99);
                Sigils.drawCentered(p, Sigils.TILE[cellMarks[idx]], x + SIZE / 2, y + SIZE / 2, 3,
                        sigilCol, sigilHalf, Painter.withAlpha(0x000000, 0x60));
            }
        }

        int totalH = ROWS * SIZE + (ROWS - 1) * PAD;
        progressDots(centerX(), gy + totalH + 6, rounds, progress);
    }

    @Override
    protected String hintText() {
        if (showing) return reversed ? "Watch, then trace it backwards" : "Watch the sequence light up";
        return "Trace the sequence   " + inputIndex + " / " + sequence.length;
    }
}
