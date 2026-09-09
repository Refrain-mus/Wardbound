package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Black Lattice: a pure logic ward.
 *
 * <p>Sixteen seals are cut into one slab of cyclopean stone. Pressing a seal
 * flips it and a visible neighbourhood. The ward is open while any eye remains
 * lit; close every eye to seat the bolt. The initial board is never rolled
 * blindly: it is made by applying a seeded list of legal presses to an empty
 * board, so replaying those presses is always a valid solution.
 *
 * <p>This is deliberately unlike the other locks. Nothing has to be caught at a
 * timing window, remembered after it disappears, traced, balanced or heard.
 * You can stop and think. The pressure is the ordinary ward clock and a generous
 * ink/move budget, not twitch execution.
 *
 * <p>Quirks:
 * <ul>
 *   <li>diagonal veins — a press reaches diagonals instead of orthogonal neighbours;</li>
 *   <li>mirror echo — the horizontally opposite seal answers the same press;</li>
 *   <li>scarred stone — two clearly cracked seals cannot themselves be pressed.
 *       The generated solution never requires either one.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class LatticeScreen extends BaseMinigameScreen {

    private static final int N = 4;
    private static final int CELLS = N * N;
    private static final int SIZE = 33;
    private static final int GAP = 8;
    private static final int TILE_NORMAL = 0;
    private static final int TILE_FROZEN = 1;
    private static final int TILE_MIRRORED = 2;
    private static final int TILE_NEIGHBOUR_ONLY = 3;
    private static final int TILE_ONE_USE = 4;

    private final boolean diagonal;
    private final boolean mirrorEcho;
    private final boolean scarred;
    private final boolean advancedTiles;
    private final int rounds;

    private final boolean[] lit = new boolean[CELLS];
    private final boolean[] scars = new boolean[CELLS];
    private final int[] tileType = new int[CELLS];
    private final boolean[] oneUseSpent = new boolean[CELLS];

    private int movesLeft;
    private int idealMoves;
    private int usedMoves;
    private int totalIdeal;
    private int totalUsed;
    private int lastPress = -1;
    private float pressFlash;
    private float veinPulse;

    public LatticeScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.lattice"), msg);

        boolean[] v = pickVariants(3, 127);
        diagonal = v[0];
        mirrorEcho = v[1];
        scarred = v[2];
        advancedTiles = expertVariant(139, 2, 74, 0.52f);
        if (diagonal) modifierLabels.add("diagonal veins");
        if (mirrorEcho) modifierLabels.add("mirror echo");
        if (scarred) modifierLabels.add("scarred stone");
        if (cardBindsOne()) modifierLabels.add("card · frozen tile");
        if (makerBindsLogic()) modifierLabels.add("maker · dead tile");
        if (advancedTiles) modifierLabels.add("mixed tile law");

        // Two boards normally, three only on richer/harder wards. Keeping this
        // short is important because the challenge is thought, not endurance.
        rounds = wobble(2 + (value >= 58 && difficulty > 1.05f ? 1 : 0), 0,
                129, 2, 3);
        buildRound();
    }

    @Override
    protected Theme createTheme() {
        return Theme.blackLattice();
    }

    @Override
    protected int panelHeight() {
        return 272;
    }

    @Override
    protected float timeBudget() {
        // Logic needs room to inspect consequences. Still a finite ward, and a
        // hurried anomaly can still be felt, but it should never become a reflex game.
        return super.timeBudget() * 1.12f;
    }

    @Override
    protected boolean allows(Anomaly a) {
        // Hiding arbitrary cells in a deterministic logic puzzle destroys
        // information rather than adding difficulty.
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING;
    }

    private void buildRound() {
        for (int i = 0; i < CELLS; i++) {
            lit[i] = false;
            scars[i] = false;
            tileType[i] = TILE_NORMAL;
            oneUseSpent[i] = false;
        }

        Random rng = new Random(seed ^ (0x4C415454494345L + progress * 104729L));

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < CELLS; i++) candidates.add(i);
        Collections.shuffle(candidates, rng);

        int scarCount = scarred ? 2 : 0;
        if (cardBindsOne()) scarCount++;
        if (makerBindsLogic()) scarCount++;
        scarCount = Math.min(4, scarCount);
        for (int i = 0; i < scarCount; i++) scars[candidates.get(i)] = true;

        if (advancedTiles) {
            List<Integer> typed = new ArrayList<>();
            for (int i = 0; i < CELLS; i++) if (!scars[i]) typed.add(i);
            Collections.shuffle(typed, new Random(seed ^ (progress * 65537L) ^ 0xA11CE55L));
            int count = Math.min(4, 2 + Math.max(0, masteryTier - 2) / 2);
            int placed = 0;
            for (int cell : typed) {
                if (placed >= count) break;
                int mode = switch (placed % 4) {
                    case 0 -> TILE_FROZEN;
                    case 1 -> mirrorEcho ? TILE_NEIGHBOUR_ONLY : TILE_MIRRORED;
                    case 2 -> TILE_NEIGHBOUR_ONLY;
                    default -> TILE_ONE_USE;
                };
                tileType[cell] = mode;
                placed++;
            }
        }

        List<Integer> legal = new ArrayList<>();
        for (int i = 0; i < CELLS; i++) {
            if (isFrozen(i)) continue;
            legal.add(i);
        }

        int pressCount = Mth.clamp(3 + progress + Math.round((difficulty - 0.8f) * 1.3f), 3, 6);
        pressCount = Math.min(pressCount, legal.size());
        idealMoves = pressCount;

        // A few operator sets have linear dependencies, especially when the
        // mirror quirk is active. Reroll only the solution order if a generated
        // combination happens to cancel all the way back to darkness. The same
        // seed still gets the same board, and every accepted board is non-empty.
        for (int attempt = 0; attempt < 8; attempt++) {
            for (int i = 0; i < CELLS; i++) lit[i] = false;
            Collections.shuffle(legal, new Random(seed + progress * 7919L
                    + 0xB1A2L + attempt * 31337L));
            for (int i = 0; i < pressCount; i++) applyPress(legal.get(i), false);
            if (!solved()) break;
        }
        if (solved() && !legal.isEmpty()) {
            // Defensive fallback; one legal involution can never leave the
            // all-dark board unchanged.
            for (int i = 0; i < CELLS; i++) lit[i] = false;
            applyPress(legal.get(0), false);
        }

        // The scramble length is only a proof of solvability, not necessarily the
        // shortest solution: overlapping kernels can cancel. Compute the actual
        // minimum over the 16-bit state space so move budget and performance are
        // scored against the puzzle the player can see, not the hidden scramble.
        int exact = minimalMovesToDark(legal);
        if (cardRevealsOne() && progress == 0 && exact > 1) {
            int start = stateMask();
            for (int cell : legal) {
                int next = start ^ operatorMask(cell);
                if (minimalMovesToDark(legal, next) == exact - 1) {
                    applyPress(cell, false);
                    exact--;
                    modifierLabels.remove("card · revealed tell");
                    if (!modifierLabels.contains("card · one proof-step written"))
                        modifierLabels.add("card · one proof-step written");
                    break;
                }
            }
        }
        idealMoves = exact > 0 ? exact : Math.max(1, pressCount);

        // Four spare presses is enough to recover from a wrong hypothesis while
        // still making the ink counter meaningful. Familiarity adds forgiveness,
        // never complexity.
        int familiarSpare = Math.min(2, familiarity / 8);
        movesLeft = idealMoves + 4 + familiarSpare;
        usedMoves = 0;
        lastPress = -1;
        pressFlash = 0f;
        veinPulse = 0f;
    }


    private int stateMask() {
        int mask = 0;
        for (int i = 0; i < CELLS; i++) if (lit[i]) mask |= 1 << i;
        return mask;
    }

    private boolean isFrozen(int cell) {
        return scars[cell] || tileType[cell] == TILE_FROZEN;
    }

    private int kernelMaskAt(int cell, boolean includeSelf) {
        int x = cell % N, y = cell / N;
        int mask = includeSelf ? (1 << cell) : 0;
        int[][] delta = diagonal
                ? new int[][]{{-1,-1},{1,-1},{-1,1},{1,1}}
                : new int[][]{{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : delta) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && nx < N && ny >= 0 && ny < N) mask ^= 1 << (ny * N + nx);
        }
        return mask;
    }

    private int localKernelMask(int cell) {
        return kernelMaskAt(cell, tileType[cell] != TILE_NEIGHBOUR_ONLY);
    }

    private int operatorMask(int cell) {
        int mask = localKernelMask(cell);
        boolean echo = mirrorEcho ^ (tileType[cell] == TILE_MIRRORED);
        if (echo) {
            int x = cell % N, y = cell / N;
            int mirror = y * N + (N - 1 - x);
            mask ^= kernelMaskAt(mirror, tileType[cell] != TILE_NEIGHBOUR_ONLY);
        }
        return mask;
    }

    private int minimalMovesToDark(List<Integer> legal) {
        return minimalMovesToDark(legal, stateMask());
    }

    private int minimalMovesToDark(List<Integer> legal, int start) {
        if (start == 0) return 0;
        int[] dist = new int[1 << CELLS];
        Arrays.fill(dist, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        dist[start] = 0;
        q.add(start);
        int[] operators = new int[legal.size()];
        for (int i = 0; i < legal.size(); i++) operators[i] = operatorMask(legal.get(i));
        while (!q.isEmpty()) {
            int state = q.removeFirst();
            int nd = dist[state] + 1;
            for (int op : operators) {
                int next = state ^ op;
                if (dist[next] >= 0) continue;
                if (next == 0) return nd;
                dist[next] = nd;
                q.addLast(next);
            }
        }
        return -1;
    }

    private void toggle(int i) {
        if (i >= 0 && i < CELLS) lit[i] = !lit[i];
    }

    /** Apply the exact operator shown by the hover preview. */
    private void applyPress(int cell, boolean feedback) {
        int mask = operatorMask(cell);
        for (int i = 0; i < CELLS; i++) if ((mask & (1 << i)) != 0) toggle(i);
        if (feedback) {
            if (tileType[cell] == TILE_ONE_USE) oneUseSpent[cell] = true;
            lastPress = cell;
            pressFlash = 0.30f;
            veinPulse = 0.42f;
        }
    }

    private void toggleAt(int x, int y) {
        if (x >= 0 && x < N && y >= 0 && y < N) toggle(y * N + x);
    }

    private boolean solved() {
        for (boolean b : lit) if (b) return false;
        return true;
    }

    private int boardX() {
        int w = N * SIZE + (N - 1) * GAP;
        return centerX() - w / 2;
    }

    private int boardY() {
        int h = N * SIZE + (N - 1) * GAP;
        return contentCenterY() - h / 2 + 4;
    }

    private int cellAt(double mx, double my) {
        int bx = boardX(), by = boardY();
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int px = bx + x * (SIZE + GAP);
                int py = by + y * (SIZE + GAP);
                if (mx >= px && mx < px + SIZE && my >= py && my < py + SIZE)
                    return y * N + x;
            }
        }
        return -1;
    }

    @Override
    protected void step(float dt) {
        if (pressFlash > 0f) pressFlash = Math.max(0f, pressFlash - dt);
        if (veinPulse > 0f) veinPulse = Math.max(0f, veinPulse - dt);
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        int cell = cellAt(mx, my);
        if (cell < 0) return false;

        if (isFrozen(cell)) {
            Sfx.markBad();
            showBanner(tileType[cell] == TILE_FROZEN ? "The frozen seal can change, but cannot be pressed" : "The crack will not take pressure", theme().dim, 700);
            return true;
        }
        if (tileType[cell] == TILE_ONE_USE && oneUseSpent[cell]) {
            Sfx.markBad();
            showBanner("That one-use seal has gone inert", theme().dim, 650);
            return true;
        }

        applyPress(cell, true);
        usedMoves++;
        movesLeft--;
        Sfx.play(WardSounds.STONE_TURN, 0.48f, 0.84f + (cell % 4) * 0.055f);

        if (solved()) {
            totalIdeal += idealMoves;
            totalUsed += usedMoves;
            progress++;
            Sfx.mark(progress + 1);
            burst(centerX(), contentCenterY(), COL_GOOD);
            if (progress >= rounds) {
                float efficiency = totalUsed <= 0 ? 1f
                        : Mth.clamp((float) totalIdeal / totalUsed, 0.45f, 1f);
                succeed(efficiency);
            } else {
                showBanner("The stone forgets one layer", COL_GOOD, 700);
                buildRound();
            }
            return true;
        }

        if (movesLeft <= 0) {
            totalUsed += usedMoves;
            if (loseLife("The lattice drank the last of the ink")) buildRound();
        }
        return true;
    }

    @Override
    protected boolean onKey(int key) {
        return false;
    }

    private boolean affectedByHover(int hover, int cell) {
        if (hover < 0 || isFrozen(hover) || (tileType[hover] == TILE_ONE_USE && oneUseSpent[hover])) return false;
        return (operatorMask(hover) & (1 << cell)) != 0;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int bx = boardX(), by = boardY();
        int bw = N * SIZE + (N - 1) * GAP;
        int bh = N * SIZE + (N - 1) * GAP;
        int hover = cellAt(mouseX, mouseY);

        // A crooked stack of stone plates: the offsets are deliberate and make
        // the frame read like architecture that was never square to begin with.
        p.roundRect(bx - 19, by - 17, bw + 38, bh + 34, 0xFF050706);
        p.roundOutline(bx - 18, by - 16, bw + 36, bh + 32, t.frameDark);
        p.roundRect(bx - 14, by - 12, bw + 28, bh + 24, Painter.darken(t.panel, 0.12f));
        p.roundOutline(bx - 11, by - 15, bw + 25, bh + 29, Painter.withAlpha(t.accentDark, 0x88));
        p.dither(bx - 12, by - 10, bw + 24, bh + 20,
                Painter.withAlpha(t.frame, 0x14), 3, 1);

        // Veins between cells. Hover shows exactly what a click will touch, so
        // the quirk changes the rule without hiding it.
        for (int a = 0; a < CELLS; a++) {
            for (int b = a + 1; b < CELLS; b++) {
                int ax = a % N, ay = a / N, bx2 = b % N, by2 = b / N;
                int dx = Math.abs(ax - bx2), dy = Math.abs(ay - by2);
                boolean linked = diagonal ? (dx == 1 && dy == 1) : (dx + dy == 1);
                if (!linked) continue;
                int x1 = this.bxCell(ax), y1 = this.byCell(ay);
                int x2 = this.bxCell(bx2), y2 = this.byCell(by2);
                boolean hot = affectedByHover(hover, a) && affectedByHover(hover, b);
                int col = hot ? Painter.withAlpha(t.accent, 0x90)
                        : Painter.withAlpha(t.frame, 0x38);
                p.line(x1, y1, x2, y2, hot ? 2 : 1, col);
            }
        }

        if (mirrorEcho && hover >= 0) {
            int hx = hover % N, hy = hover / N;
            int mirror = hy * N + (N - 1 - hx);
            p.line(bxCell(hx), byCell(hy), bxCell(mirror % N), byCell(mirror / N), 1,
                    Painter.withAlpha(0xFF8B6EB1, 0xA0));
        }

        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) drawCell(x, y, hover);
        }

        // Four watchers in the corners. They are decoration, but unmistakably
        // make this panel belong to the same world as the wards rather than a
        // generic lights-out board.
        drawWatcher(bx - 10, by - 7, 0);
        drawWatcher(bx + bw + 10, by - 7, 1);
        drawWatcher(bx - 10, by + bh + 7, 2);
        drawWatcher(bx + bw + 10, by + bh + 7, 3);

        progressDots(centerX(), by + bh + 18, rounds, progress);
        smallCentered(g, "ink " + movesLeft,
                centerX(), by + bh + 33, movesLeft <= 2 ? COL_BAD : t.dim, 0.82f);
        smallCentered(g, "close every eye",
                centerX(), by + bh + 46, t.text, 0.80f);
    }

    private int bxCell(int x) {
        return boardX() + x * (SIZE + GAP) + SIZE / 2;
    }

    private int byCell(int y) {
        return boardY() + y * (SIZE + GAP) + SIZE / 2;
    }

    private void drawCell(int x, int y, int hover) {
        Theme t = theme();
        int i = y * N + x;
        int px = boardX() + x * (SIZE + GAP);
        int py = boardY() + y * (SIZE + GAP);
        boolean h = i == hover;
        boolean affected = affectedByHover(hover, i);
        boolean flash = i == lastPress && pressFlash > 0f;

        boolean frozen = isFrozen(i);
        boolean inertOneUse = tileType[i] == TILE_ONE_USE && oneUseSpent[i];
        int edge = frozen ? Painter.darken(t.frame, 0.34f)
                : (inertOneUse ? Painter.darken(t.frame, 0.28f) : (affected ? t.accentDark : t.wellEdge));
        int body = frozen ? Painter.darken(t.panel, 0.30f)
                : (inertOneUse ? Painter.darken(t.well, 0.24f) : (lit[i] ? Painter.mix(t.well, t.accentDark, 0.36f) : t.well));

        if (affected && !frozen)
            p.roundRect(px - 3, py - 3, SIZE + 6, SIZE + 6, Painter.withAlpha(t.accent, 0x18));
        p.roundRect(px - 1, py - 1, SIZE + 2, SIZE + 2, edge);
        p.roundRect(px, py, SIZE, SIZE, body);
        p.dither(px + 2, py + 2, SIZE - 4, SIZE - 4,
                Painter.withAlpha(0xFFFFFF, lit[i] ? 0x08 : 0x04), 3, i);

        if (scars[i]) {
            p.line(px + 5, py + 5, px + SIZE - 6, py + SIZE - 7, 2, t.frameDark);
            p.line(px + SIZE - 9, py + 4, px + 13, py + SIZE - 5, 1, t.frameDark);
            p.line(px + 11, py + 13, px + 5, py + SIZE - 4, 1, t.frameDark);
        } else if (tileType[i] == TILE_FROZEN) {
            p.line(px + 5, py + SIZE / 2, px + SIZE - 5, py + SIZE / 2, 1, Painter.withAlpha(0xFF9AC7D2, 0x90));
            p.line(px + SIZE / 2, py + 5, px + SIZE / 2, py + SIZE - 5, 1, Painter.withAlpha(0xFF9AC7D2, 0x70));
        }

        int cx = px + SIZE / 2, cy = py + SIZE / 2;
        if (lit[i]) {
            float pulse = 0.70f + breath(0.38f) * 0.30f;
            p.disc(cx, cy, 10, Painter.scaleAlpha(t.accentDark, pulse));
            p.disc(cx, cy, 7, Painter.withAlpha(t.accent, 0xE8));
            p.disc(cx, cy, 3, 0xFF10160E);
            p.rect(cx - 1, cy - 6, cx + 1, cy + 6, Painter.withAlpha(t.accentLite, 0xB0));
        } else {
            p.ringThick(cx, cy, 8, 2, h ? t.frameLite : t.frame);
            p.disc(cx, cy, 2, t.frameDark);
        }

        if (tileType[i] == TILE_MIRRORED) {
            p.line(px + 5, py + SIZE - 6, px + SIZE - 5, py + SIZE - 6, 1, Painter.withAlpha(0xFFA995D7, 0xB0));
            p.disc(px + 7, py + SIZE - 6, 1, 0xFFA995D7);
            p.disc(px + SIZE - 7, py + SIZE - 6, 1, 0xFFA995D7);
        } else if (tileType[i] == TILE_NEIGHBOUR_ONLY) {
            p.ringThick(cx, cy, 13, 1, Painter.withAlpha(0xFFD8B778, 0xA0));
        } else if (tileType[i] == TILE_ONE_USE) {
            int c = inertOneUse ? t.faint : 0xFFB8D88A;
            p.rect(px + SIZE - 8, py + 4, px + SIZE - 5, py + 9, c);
        }
        if (flash) p.ringThick(cx, cy, 13, 2, Painter.withAlpha(t.accentLite, 0xC0));
        if (h) p.roundOutline(px - 2, py - 2, SIZE + 4, SIZE + 4, t.frameLite);
    }

    private void drawWatcher(int cx, int cy, int salt) {
        Theme t = theme();
        int w = 9 + salt % 2;
        p.line(cx - w, cy, cx, cy - 4, 1, Painter.withAlpha(t.frame, 0x90));
        p.line(cx, cy - 4, cx + w, cy, 1, Painter.withAlpha(t.frame, 0x90));
        p.line(cx + w, cy, cx, cy + 4, 1, Painter.withAlpha(t.frame, 0x90));
        p.line(cx, cy + 4, cx - w, cy, 1, Painter.withAlpha(t.frame, 0x90));
        p.disc(cx, cy, 2, Painter.withAlpha(t.accent, 0x90));
    }

    @Override
    protected String hintText() {
        if (advancedTiles) return "Read each tile's mark: ice cannot be pressed, a ring spares itself, twin dots echo, and the small bar is one-use";
        if (diagonal && mirrorEcho)
            return "A touch crosses the corners, and its reflection answers";
        if (diagonal) return "A touch crosses the corners around it";
        if (mirrorEcho) return "Every touch is answered by its reflection";
        return "Touch a seal to turn it and its four neighbours";
    }
}
