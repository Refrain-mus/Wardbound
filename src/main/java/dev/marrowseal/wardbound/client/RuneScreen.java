package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * A warded coffer. Sigils kindle and burn out fast, snuff each one in time.
 *
 * <p>Quirks: twin sigils, cursed tiles, wandering light.
 */
@OnlyIn(Dist.CLIENT)
public class RuneScreen extends BaseMinigameScreen {

    private static final int COLS = 4;
    private static final int ROWS = 3;
    private static final int CELLS = COLS * ROWS;
    private static final int SIZE = 36;
    private static final int PAD = 10;

    private final boolean twin;
    private final boolean cursed;
    private final boolean wander;

    private final int targetHits;
    private final float lifetime;
    private final Random rng;
    /** How many tiles are forbidden at once. Fixed for the lock, but not which ones. */
    private int curseCount;
    private final boolean[] curse = new boolean[CELLS];
    private final int[] tileSigil = new int[CELLS];

    private final int[] active = {-1, -1};
    private float cellTimer;
    private float spawnDelay;
    private boolean wandered;
    /** Where the light was before it wandered, and for how much longer that still counts. */
    private final int[] ghostCell = {-1, -1};
    private float ghostTimer;
    private int fadeCell = -1;
    private float fadeTimer;
    private int fadeColor = COL_GOOD;

    private int hits;
    private int misses;

    public RuneScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.runes"), msg);

        boolean[] v = pickVariants(3, 4);
        twin = v[0];
        cursed = v[1];
        wander = v[2];
        if (twin) modifierLabels.add("twin sigil");
        if (cursed) modifierLabels.add("cursed tile");
        if (wander) modifierLabels.add("wandering light");

        this.targetHits = wobble(4 + Math.round(value / 16f * difficulty), 2, 20, 4, 11);
        float variance = 0.85f + seedVariance(31) * 0.4f;
        this.lifetime = Mth.clamp(1.35f / difficulty * variance * (twin ? 1.35f : 1f), 0.50f, 2.0f);
        this.rng = new Random(seed);

        for (int i = 0; i < CELLS; i++) tileSigil[i] = rng.nextInt(Sigils.TILE.length);

        if (cursed) {
            int count = 2 + rng.nextInt(2);
            recurse(count);
        }

        this.hits = Math.min(progress, targetHits);
        spawn();
    }

    /**
     * Re-deals which tiles are forbidden.
     *
     * <p>They used to be chosen once and then sat there for the whole lock, so
     * after two hits you had simply memorised four dead squares and the variant
     * stopped existing. Re-dealing on every score means the board you learned a
     * second ago is not the board in front of you, which is the thing the
     * variant was always supposed to be doing.
     */
    private void recurse(int count) {
        java.util.Arrays.fill(curse, false);
        curseCount = count;
        for (int i = 0; i < count; i++) {
            for (int guard = 0; guard < 30; guard++) {
                int c = rng.nextInt(CELLS);
                // never on a tile that is live right now: that is a trap you
                // cannot see coming rather than one you have to read
                if (!curse[c] && !isActive(c)) {
                    curse[c] = true;
                    break;
                }
            }
        }
    }

    /** Short. A ward you can outwait is not a ward. */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.34f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.verdigris();
    }

    @Override
    protected int panelHeight() {
        return 240;
    }

    private boolean isActive(int cell) {
        return cell >= 0 && (active[0] == cell || active[1] == cell);
    }

    private int activeCount() {
        return (active[0] >= 0 ? 1 : 0) + (active[1] >= 0 ? 1 : 0);
    }

    private int freeCell() {
        int c;
        int guard = 0;
        do {
            c = rng.nextInt(CELLS);
        } while ((isActive(c) || (cursed && curse[c])) && ++guard < 40);
        return c;
    }

    private void spawn() {
        active[0] = freeCell();
        active[1] = twin ? freeCell() : -1;
        cellTimer = lifetime * (1f - hits * 0.035f);
        spawnDelay = 0f;
        wandered = false;
        ghostCell[0] = -1;
        ghostCell[1] = -1;
        ghostTimer = 0f;
    }

    private void registerHit(int cell) {
        // A twin pair is one sigil shown twice, so taking both of them is one
        // hit. Counting them separately made the twin variant a discount rather
        // than a complication: half the required hits arrived in pairs.
        boolean twinPending = twin && active[0] >= 0 && active[1] >= 0;
        if (!twinPending) {
            hits++;
            if (cursed) recurse(curseCount);
        }
        progress = hits;
        fadeCell = cell;
        fadeColor = COL_GOOD;
        fadeTimer = 0.25f;
        Sfx.mark(hits);
        burst(cellCenterX(cell), cellCenterY(cell), COL_GOOD);

        if (active[0] == cell) active[0] = -1;
        if (active[1] == cell) active[1] = -1;

        if (hits >= targetHits) {
            int total = hits + misses;
            succeed(total == 0 ? 1f : (float) hits / total);
        } else if (activeCount() == 0) {
            spawnDelay = 0.16f;
        }
    }

    @Override
    protected void step(float dt) {
        if (activeCount() == 0) {
            spawnDelay -= dt;
            if (spawnDelay <= 0f) spawn();
        } else {
            cellTimer -= dt;

            if (wander && !wandered && cellTimer < lifetime * 0.5f) {
                wandered = true;
                // Remember where it was.
                //
                // A click aimed at the old tile a fraction after the light moved
                // was being scored as a hit on empty stone, which is a miss for
                // a shot that was on target when it was taken. The old position
                // stays live for the same grace window everything else uses.
                ghostCell[0] = active[0];
                ghostCell[1] = active[1];
                ghostTimer = grace() + 0.10f;
                for (int i = 0; i < 2; i++) {
                    if (active[i] < 0) continue;
                    int row = active[i] / COLS, col = active[i] % COLS;
                    int nc = Mth.clamp(col + (rng.nextBoolean() ? 1 : -1), 0, COLS - 1);
                    int nr = Mth.clamp(row + (rng.nextBoolean() ? 1 : -1), 0, ROWS - 1);
                    int next = nr * COLS + nc;
                    if (!isActive(next) && !(cursed && curse[next])) active[i] = next;
                }
            }

            // The grace window also applies here: a sigil is still clickable for a heartbeat
            // after its ring closes, which is exactly the amount of lag a low frame rate adds.
            if (cellTimer <= -grace()) {
                misses++;
                if (loseLife("Sigil burned out")) {
                    active[0] = -1;
                    active[1] = -1;
                    spawnDelay = 0.22f;
                }
            }
        }
        if (ghostTimer > 0f) ghostTimer -= dt;
        else {
            ghostCell[0] = -1;
            ghostCell[1] = -1;
        }
        if (fadeTimer > 0f) fadeTimer -= dt;
        else fadeCell = -1;
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;

        int gx = gridX(), gy = gridY();
        int cell = -1;
        for (int r = 0; r < ROWS && cell < 0; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = gx + c * (SIZE + PAD);
                int y = gy + r * (SIZE + PAD);
                if (mx >= x && mx < x + SIZE && my >= y && my < y + SIZE) {
                    cell = r * COLS + c;
                    break;
                }
            }
        }
        if (cell < 0) return false;

        if (cursed && curse[cell]) {
            misses++;
            fadeCell = cell;
            fadeColor = COL_BAD;
            fadeTimer = 0.3f;
            if (loseLife("You touched a cursed tile")) {
                active[0] = -1;
                active[1] = -1;
                spawnDelay = 0.3f;
            }
            return true;
        }

        // A tile the light has only just left still counts. See ghostCell.
        // Consume the corresponding moved slot, not the stale coordinate itself;
        // otherwise a grace-click was accepted visually but the live sigil stayed
        // on the board and had to be hit a second time.
        if (isActive(cell)) {
            registerHit(cell);
        } else if (ghostTimer > 0f && (cell == ghostCell[0] || cell == ghostCell[1])) {
            int slot = cell == ghostCell[0] ? 0 : 1;
            int liveCell = active[slot];
            if (liveCell >= 0) registerHit(liveCell);
            ghostCell[slot] = -1;
        } else {
            misses++;
            if (loseLife("Wrong sigil")) {
                active[0] = -1;
                active[1] = -1;
                spawnDelay = 0.28f;
            }
        }
        return true;
    }

    private int gridX() {
        return left() + (panelWidth() - (COLS * SIZE + (COLS - 1) * PAD)) / 2;
    }

    private int gridY() {
        int totalH = ROWS * SIZE + (ROWS - 1) * PAD;
        return contentTop() + (contentBottom() - contentTop() - totalH) / 2;
    }

    private int cellCenterX(int cell) {
        return gridX() + (cell % COLS) * (SIZE + PAD) + SIZE / 2;
    }

    private int cellCenterY(int cell) {
        return gridY() + (cell / COLS) * (SIZE + PAD) + SIZE / 2;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int gx = gridX(), gy = gridY();
        float life = Mth.clamp(cellTimer / Math.max(0.01f, lifetime), 0f, 1f);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = r * COLS + c;
                int x = gx + c * (SIZE + PAD);
                int y = gy + r * (SIZE + PAD);
                int ccx = x + SIZE / 2;
                int ccy = y + SIZE / 2;
                boolean isCursed = cursed && curse[idx];
                boolean live = isActive(idx);

                p.roundRect(x - 2, y - 2, SIZE + 4, SIZE + 4, t.edge);
                p.roundRect(x - 1, y - 1, SIZE + 2, SIZE + 2,
                        isCursed ? Painter.darken(COL_BAD, 0.55f) : t.wellEdge);
                p.roundRect(x, y, SIZE, SIZE, t.well);
                p.scanlines(x + 1, y + 1, SIZE - 2, SIZE - 2, Painter.withAlpha(0x000000, 0x1A));
                rivet(x + 5, y + 5);
                rivet(x + SIZE - 5, y + SIZE - 5);

                if (live) {
                    // the closing ring is the timer; the sigil under it is the target
                    int ringR = Math.round(5 + (SIZE / 2f - 5) * life);
                    p.ringThick(ccx, ccy, ringR, 2, Painter.withAlpha(t.accent, 0xAA));
                    p.roundRect(x, y, SIZE, SIZE, Painter.withAlpha(t.accent, 0x22));
                    Sigils.drawCentered(p, Sigils.TILE[tileSigil[idx]], ccx, ccy, 2,
                            t.accentLite, Painter.withAlpha(t.accent, 0xCC),
                            Painter.withAlpha(0x000000, 0x70));
                } else if (idx == fadeCell) {
                    Sigils.drawCentered(p, Sigils.TILE[tileSigil[idx]], ccx, ccy, 2,
                            fadeColor, Painter.withAlpha(fadeColor, 0x88), 0);
                } else if (isCursed) {
                    Sigils.drawCentered(p, Sigils.TILE[3], ccx, ccy, 2,
                            Painter.withAlpha(COL_BAD, 0xBB), Painter.withAlpha(COL_BAD, 0x55), 0);
                } else {
                    Sigils.drawCentered(p, Sigils.TILE[tileSigil[idx]], ccx, ccy, 2,
                            Painter.withAlpha(t.faint, 0x88), 0, 0);
                }
            }
        }

        int totalH = ROWS * SIZE + (ROWS - 1) * PAD;
        progressDots(centerX(), gy + totalH + 8, targetHits, hits);
    }

    @Override
    protected String hintText() {
        if (cursed) return "Click the kindled sigil, never the red ones";
        return "Click the sigil before it burns out";
    }
}
