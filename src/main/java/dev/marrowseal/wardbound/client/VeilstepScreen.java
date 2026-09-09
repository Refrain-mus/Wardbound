package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The Veilstep: a runic stepping board. The stone under the hand names the
 * exact distance of the next move; reach the true eye before the path spends
 * itself.
 *
 * <p>The canonical path is authored first, then the rest of the board is filled
 * around it, which keeps every board solvable while still leaving room for
 * decoys and wrong turns. It is a route-planning puzzle rather than timing,
 * rhythm or local tile matching.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VeilstepScreen extends BaseMinigameScreen {

    private static final int SIZE = 4;
    private static final int CELLS = SIZE * SIZE;
    private static final int TILE = 42;
    private static final int GAP = 8;

    private final boolean crackedStone;
    private final boolean longMarch;
    private final boolean falseGate;
    private final boolean pilgrimMark;
    private final boolean forkedOmen;
    private final boolean waningLantern;
    private final boolean afterimagePath;
    private final int rounds;

    private final int[] step = new int[CELLS];
    private final boolean[] spent = new boolean[CELLS];
    private final boolean[] route = new boolean[CELLS];
    private final int[] revealAfter = new int[CELLS];
    private final int[] bgGlyph = new int[18];
    private final int[] bgX = new int[18];
    private final int[] bgY = new int[18];

    private int current = 0;
    private int goal = 0;
    private int blocked = -1;
    private int decoyGoal = -1;
    private int secondDecoyGoal = -1;
    private int sanctuary = -1;
    private boolean sanctuaryClaimed;
    private int stepsLeft;
    private int idealMoves;
    private int usedMoves;
    private int totalIdeal;
    private int totalUsed;
    private int roundMissteps;
    private float flash;
    private float pulse;
    private int lastBad = -1;

    public VeilstepScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.veilstep"), msg);
        boolean[] q = pickVariants(6, 2213);
        crackedStone = q[0];
        longMarch = q[1];
        falseGate = q[2];
        pilgrimMark = q[3];
        forkedOmen = q[4];
        waningLantern = q[5];
        afterimagePath = expertVariant(2231, 2, 74, 0.46f);
        if (crackedStone) modifierLabels.add("cracked stone");
        if (longMarch) modifierLabels.add("long march");
        if (falseGate) modifierLabels.add("false gate");
        if (pilgrimMark) modifierLabels.add("pilgrim mark");
        if (forkedOmen) modifierLabels.add("forked omen");
        if (waningLantern) modifierLabels.add("waning lantern");
        if (afterimagePath) modifierLabels.add("afterimage path");
        rounds = Mth.clamp(2 + (value >= 60 || difficulty > 1.24f ? 1 : 0), 2, 3);
        buildRound();
    }

    @Override
    protected Theme createTheme() {
        return Theme.parallax();
    }

    @Override
    protected int panelHeight() {
        return 284;
    }

    @Override
    protected float timeBudget() {
        return super.timeBudget() * 1.12f;
    }

    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING;
    }

    @Override
    protected void init() {
        super.init();
        Random r = new Random(seed ^ 0x5645494C53544550L);
        int left = boardX() - 20;
        int top = boardY() - 22;
        int width = boardW() + 40;
        int height = boardH() + 64;
        for (int i = 0; i < bgGlyph.length; i++) {
            bgGlyph[i] = r.nextInt(Sigils.TILE.length);
            bgX[i] = left + r.nextInt(Math.max(1, width));
            bgY[i] = top + r.nextInt(Math.max(1, height));
        }
    }

    private void buildRound() {
        Random r = new Random(seed ^ (progress * 0x9E3779B97F4A7C15L) ^ 0x5645494CL);
        int baseMoves = Mth.clamp(4 + progress + Math.round((difficulty - 0.95f) * 1.6f) + (longMarch ? 1 : 0), 4, 7);
        List<Integer> path = null;
        for (int attempt = 0; attempt < 250 && path == null; attempt++) path = generateRoute(r, baseMoves);
        if (path == null) {
            path = List.of(0, 2, 10, 11, 15);
            Arrays.fill(step, 1);
            for (int i = 0; i < path.size() - 1; i++) step[path.get(i)] = distance(path.get(i), path.get(i + 1));
        }

        Arrays.fill(spent, false);
        Arrays.fill(route, false);
        Arrays.fill(revealAfter, -1);
        for (int i = 0; i < CELLS; i++) if (step[i] <= 0) step[i] = 1 + r.nextInt(3);
        for (int idx : path) route[idx] = true;
        if (afterimagePath && path.size() >= 4) {
            // Only a few canonical stones phase in late. The predecessor itself
            // is the key, so the route remains fully deterministic and never asks
            // the player to click an invisible destination.
            int hidden = Math.min(2, Math.max(1, masteryTier / 2));
            for (int n = 0; n < hidden; n++) {
                int pi = 2 + Math.floorMod((int)(seed + progress * 17L + n * 3L), path.size() - 2);
                revealAfter[path.get(pi)] = path.get(pi - 1);
            }
        }

        current = path.get(0);
        goal = path.get(path.size() - 1);
        idealMoves = path.size() - 1;
        blocked = -1;
        decoyGoal = -1;
        secondDecoyGoal = -1;
        sanctuary = -1;
        sanctuaryClaimed = false;

        if (crackedStone) {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < CELLS; i++) if (!route[i] && i != current && i != goal) candidates.add(i);
            if (!candidates.isEmpty()) blocked = candidates.get(r.nextInt(candidates.size()));
        }
        if (falseGate || forkedOmen) {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < CELLS; i++) if (i != goal && !route[i]) candidates.add(i);
            if (!candidates.isEmpty()) {
                decoyGoal = candidates.remove(r.nextInt(candidates.size()));
                if (forkedOmen && !candidates.isEmpty()) {
                    secondDecoyGoal = candidates.get(r.nextInt(candidates.size()));
                }
            }
        }
        if (pilgrimMark && idealMoves >= 3) {
            List<Integer> routeCandidates = new ArrayList<>();
            for (int i = 0; i < CELLS; i++) {
                if (route[i] && i != current && i != goal) routeCandidates.add(i);
            }
            if (!routeCandidates.isEmpty()) sanctuary = routeCandidates.get(r.nextInt(routeCandidates.size()));
        }

        stepsLeft = idealMoves + 3 + Math.min(2, familiarity / 6) + (longMarch ? 1 : 0);
        usedMoves = 0;
        roundMissteps = 0;
        flash = 0f;
        lastBad = -1;
    }

    private List<Integer> generateRoute(Random r, int moves) {
        Arrays.fill(step, 0);
        List<Integer> path = new ArrayList<>();
        boolean[] used = new boolean[CELLS];
        int pos = r.nextInt(CELLS);
        path.add(pos);
        used[pos] = true;

        for (int m = 0; m < moves; m++) {
            List<int[]> options = legalJumpOptions(pos, used);
            if (options.isEmpty()) return null;
            int[] pick = options.get(r.nextInt(options.size()));
            int next = pick[0], dist = pick[1];
            step[pos] = dist;
            pos = next;
            path.add(pos);
            used[pos] = true;
        }
        return path;
    }

    private int distance(int a, int b) {
        int ar = a / SIZE, ac = a % SIZE;
        int br = b / SIZE, bc = b % SIZE;
        return Math.abs(ar - br) + Math.abs(ac - bc);
    }

    private List<int[]> legalJumpOptions(int from, boolean[] forbidden) {
        List<int[]> out = new ArrayList<>();
        int row = from / SIZE, col = from % SIZE;
        for (int dist = 1; dist <= 3; dist++) {
            addJump(out, row - dist, col, from, dist, forbidden);
            addJump(out, row + dist, col, from, dist, forbidden);
            addJump(out, row, col - dist, from, dist, forbidden);
            addJump(out, row, col + dist, from, dist, forbidden);
        }
        return out;
    }

    private void addJump(List<int[]> out, int row, int col, int from, int dist, boolean[] forbidden) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return;
        int idx = row * SIZE + col;
        if (idx == from) return;
        if (forbidden != null && forbidden[idx]) return;
        out.add(new int[]{idx, dist});
    }

    private List<Integer> legalMovesNow() {
        boolean[] forbidden = new boolean[CELLS];
        for (int i = 0; i < CELLS; i++) forbidden[i] = spent[i];
        forbidden[current] = false;
        List<Integer> out = new ArrayList<>();
        int dist = Mth.clamp(step[current], 1, 3);
        int row = current / SIZE, col = current % SIZE;
        collectLegal(out, row - dist, col, forbidden);
        collectLegal(out, row + dist, col, forbidden);
        collectLegal(out, row, col - dist, forbidden);
        collectLegal(out, row, col + dist, forbidden);
        return out;
    }

    private void collectLegal(List<Integer> out, int row, int col, boolean[] forbidden) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return;
        int idx = row * SIZE + col;
        if (idx == blocked || forbidden[idx]) return;
        out.add(idx);
    }

    private int boardW() { return SIZE * TILE + (SIZE - 1) * GAP; }
    private int boardH() { return boardW(); }
    private int boardX() { return centerX() - boardW() / 2; }
    private int boardY() { return contentCenterY() - boardH() / 2 - 2; }

    private int cellAt(double mx, double my) {
        int bx = boardX(), by = boardY();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int x = bx + c * (TILE + GAP);
                int y = by + r * (TILE + GAP);
                if (mx >= x && mx < x + TILE && my >= y && my < y + TILE) return r * SIZE + c;
            }
        }
        return -1;
    }

    private int cellCenterX(int idx) { return boardX() + (idx % SIZE) * (TILE + GAP) + TILE / 2; }
    private int cellCenterY(int idx) { return boardY() + (idx / SIZE) * (TILE + GAP) + TILE / 2; }

    @Override
    protected void step(float dt) {
        pulse += dt;
        if (flash > 0f) flash = Math.max(0f, flash - dt);
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        int tile = cellAt(mx, my);
        if (tile < 0) return false;
        if (veiledUntilPredecessor(tile)) {
            Sfx.play(WardSounds.VEILSTEP_REBUKE, 0.28f, 0.76f);
            showBanner("That stone has not entered this path yet", theme().dim, 650);
            return true;
        }
        if (tile == blocked) {
            mistakes++;
            roundMissteps++;
            lastBad = tile;
            flash = 0.30f;
            Sfx.play(WardSounds.VEILSTEP_REBUKE, 0.40f, 0.85f);
            Sfx.markBad();
            showBanner("That stone has fallen into the veil", COL_BAD, 650);
            return true;
        }

        List<Integer> legal = legalMovesNow();
        if (!legal.contains(tile)) {
            mistakes++;
            roundMissteps++;
            stepsLeft--;
            lastBad = tile;
            flash = 0.30f;
            Sfx.play(WardSounds.VEILSTEP_REBUKE, 0.36f, 0.93f);
            Sfx.markBad();
            showBanner("The veil refused that step", COL_BAD, 700);
            if (stepsLeft <= 0) {
                totalUsed += usedMoves + roundMissteps;
                if (loseLife("You spent the path on false steps")) buildRound();
            }
            return true;
        }

        spent[current] = true;
        current = tile;
        usedMoves++;
        stepsLeft--;
        flash = 0.24f;
        lastBad = -1;
        Sfx.play(WardSounds.VEILSTEP_STEP, 0.42f, 0.92f + current * 0.02f);
        if (pilgrimMark && current == sanctuary && !sanctuaryClaimed) {
            sanctuaryClaimed = true;
            stepsLeft++;
            Sfx.play(WardSounds.VEILSTEP_CHIME, 0.36f, 1.12f);
            showBanner("The pilgrim mark grants one more step", theme().accentLite, 650);
        }

        if (current == goal) {
            totalIdeal += idealMoves;
            totalUsed += usedMoves + roundMissteps;
            progress++;
            Sfx.play(WardSounds.VEILSTEP_CHIME, 0.56f, 1.00f);
            Sfx.mark(progress + 1);
            burst(cellCenterX(goal), cellCenterY(goal), COL_GOOD);
            if (progress >= rounds) {
                float efficiency = totalUsed <= 0 ? 1f : Mth.clamp((float) totalIdeal / totalUsed, 0.42f, 1f);
                succeed(efficiency);
            } else {
                showBanner("The veil parts to another crossing", COL_GOOD, 760);
                buildRound();
            }
            return true;
        }

        if (stepsLeft <= 0 || legalMovesNow().isEmpty()) {
            totalUsed += usedMoves + roundMissteps;
            if (loseLife("The path gave you no more answers")) buildRound();
        }
        return true;
    }

    @Override
    protected boolean onKey(int key) { return false; }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int bx = boardX(), by = boardY();
        int hover = cellAt(mouseX, mouseY);
        List<Integer> legal = legalMovesNow();

        for (int i = 0; i < bgGlyph.length; i++) {
            int a = 0x11 + (i % 4) * 4;
            if (!WardConfig.accessibilityReduceMotion) {
                a += Math.round((0.5f + 0.5f * Mth.sin(pulse * 0.42f + i * 0.8f)) * 5f);
            }
            Sigils.drawCentered(p, Sigils.TILE[bgGlyph[i]], bgX[i], bgY[i], 1,
                    Painter.withAlpha(t.frameLite, a), Painter.withAlpha(t.accentDark, a / 2), 0);
        }

        p.roundRect(bx - 18, by - 20, boardW() + 36, boardH() + 42, 0xFF05070E);
        p.roundOutline(bx - 17, by - 19, boardW() + 34, boardH() + 40, t.frameDark);
        p.dither(bx - 14, by - 16, boardW() + 28, boardH() + 32,
                Painter.withAlpha(t.accentDark, 0x12), 4, progress * 11);

        drawReachLines(legal, t);
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) drawCell(g, r * SIZE + c, hover, legal.contains(r * SIZE + c));
        }

        progressDots(centerX(), by + boardH() + 22, rounds, progress);
        smallCentered(g, "read the number under the glowing stone, then move exactly that far in a straight line", centerX(), by + boardH() + 36, t.dim, 0.72f);
        smallCentered(g, "steps " + stepsLeft + "   ·   spent stones cannot be used again", centerX(),
                by + boardH() + 49, stepsLeft <= 2 ? COL_BAD : t.faint, 0.72f);
        if (afterimagePath) smallCentered(g, "some stones exist only after their predecessor is reached", centerX(),
                by + boardH() + 60, t.faint, 0.64f);
    }

    private void drawReachLines(List<Integer> legal, Theme t) {
        int sx = cellCenterX(current), sy = cellCenterY(current);
        for (int idx : legal) {
            if (veiledUntilPredecessor(idx)) continue;
            int ex = cellCenterX(idx), ey = cellCenterY(idx);
            p.line(sx, sy, ex, ey, 1, Painter.withAlpha(t.accent, 0x44));
            p.disc(ex, ey, 1, Painter.withAlpha(t.accentLite, 0x88));
        }
    }

    private boolean veiledUntilPredecessor(int idx) {
        int prev = revealAfter[idx];
        if (prev < 0) return false;
        return current != prev && !spent[prev];
    }

    private void drawCell(GuiGraphics g, int idx, int hover, boolean reachable) {
        Theme t = theme();
        int x = boardX() + (idx % SIZE) * (TILE + GAP);
        int y = boardY() + (idx / SIZE) * (TILE + GAP);
        boolean hot = idx == hover;
        boolean now = idx == current;
        boolean goalCell = idx == goal;
        boolean decoy = idx == decoyGoal;
        boolean dead = idx == blocked;
        boolean burnt = spent[idx];

        boolean sanctuaryCell = idx == sanctuary;
        boolean falseGoalCell = idx == decoyGoal || idx == secondDecoyGoal;
        boolean veiled = veiledUntilPredecessor(idx);
        int dimmer = (waningLantern && !now && !reachable && !goalCell && !falseGoalCell && !sanctuaryCell && !dead) ? 1 : 0;
        int frame = dead ? Painter.darken(COL_BAD, 0.35f) : (veiled ? t.frameDark : (goalCell ? t.accent : t.frameDark));
        int body = dead ? 0xFF151518 : (burnt ? Painter.darken(t.well, 0.28f)
                : (veiled ? 0xFF090A10 : (now ? Painter.mix(t.well, t.accentDark, 0.18f) : (dimmer == 1 ? Painter.darken(t.well, 0.18f) : t.well))));
        p.roundRect(x - 2, y - 2, TILE + 4, TILE + 4, frame);
        p.roundRect(x, y, TILE, TILE, body);
        p.dither(x + 2, y + 2, TILE - 4, TILE - 4,
                Painter.withAlpha(t.frameLite, burnt ? 0x05 : (dimmer == 1 ? 0x06 : 0x0C)), 3, idx * 3 + progress);

        if (reachable && !dead && !goalCell && !veiled) {
            p.roundOutline(x - 3, y - 3, TILE + 6, TILE + 6, Painter.withAlpha(t.accentLite, 0x7A));
        }
        if (hot && !dead) p.roundOutline(x - 4, y - 4, TILE + 8, TILE + 8, t.frameLite);
        if (flash > 0f && idx == lastBad) p.roundOutline(x - 4, y - 4, TILE + 8, TILE + 8, Painter.scaleAlpha(COL_BAD, flash));

        if (dead) {
            p.line(x + 7, y + 7, x + TILE - 7, y + TILE - 7, 1, Painter.withAlpha(COL_BAD, 0xA8));
            p.line(x + TILE - 7, y + 7, x + 7, y + TILE - 7, 1, Painter.withAlpha(COL_BAD, 0xA8));
        } else if (veiled) {
            p.dither(x + 5, y + 5, TILE - 10, TILE - 10, Painter.withAlpha(t.accentDark, 0x18), 3, idx + progress);
            p.ringThick(x + TILE / 2, y + TILE / 2, 8, 1, Painter.withAlpha(t.faint, 0x48));
        } else {
            int numCol = burnt ? t.faint : (dimmer == 1 ? Painter.withAlpha(t.faint, 0xA0) : t.frameLite);
            centered(g, Integer.toString(step[idx]), x + TILE / 2, y + 10, numCol);
            Sigils.drawCentered(p, Sigils.TILE[(idx + progress * 3) % Sigils.TILE.length], x + TILE / 2, y + 29, 1,
                    burnt ? Painter.withAlpha(t.faint, 0x88) : (dimmer == 1 ? Painter.withAlpha(t.accentLite, 0x66) : t.accentLite),
                    burnt ? Painter.withAlpha(t.accentDark, 0x18) : Painter.withAlpha(t.accent, dimmer == 1 ? 0x22 : 0x48), 0);
        }

        if (burnt) {
            p.rect(x + 6, y + TILE - 8, x + TILE - 6, y + TILE - 6, Painter.withAlpha(t.faint, 0x66));
        }
        if (now) {
            p.ringThick(x + TILE / 2, y + TILE / 2, 17, 2, t.accentLite);
            p.disc(x + TILE / 2, y + 7, 2, t.accentLite);
        }
        if (sanctuaryCell && !sanctuaryClaimed && !veiled) {
            p.ringThick(x + TILE / 2, y + TILE / 2, 11, 1, Painter.withAlpha(0xFFE9D98E, 0xB0));
            p.disc(x + TILE / 2, y + TILE / 2 + 11, 2, Painter.withAlpha(0xFFE9D98E, 0xD0));
        }
        if ((goalCell || falseGoalCell) && !veiled) {
            int eye = goalCell ? Painter.withAlpha(0xFFF2E5B8, 0xF0) : Painter.withAlpha(t.faint, falseGate || forkedOmen ? 0x74 : 0x6C);
            p.ringThick(x + TILE / 2, y + TILE / 2, 7, 2, eye);
            p.rect(x + TILE / 2 - 5, y + TILE / 2 - 1, x + TILE / 2 + 5, y + TILE / 2 + 1, eye);
            p.disc(x + TILE / 2, y + TILE / 2, 1, goalCell ? t.accent : t.faint);
            if (idx == secondDecoyGoal) {
                p.line(x + 10, y + TILE - 9, x + TILE - 10, y + TILE - 9, 1, Painter.withAlpha(t.faint, 0x90));
            }
        }
    }

    @Override
    protected String hintText() {
        if (afterimagePath) return "A veiled stone phases in only when you reach its predecessor; blank afterimages are intentional, not missing information";
        if (pilgrimMark) return "A marked stone will grant one extra step the first time you land on it";
        if (forkedOmen && falseGate) return "Two eyes lie and one stone is true; trust the distance, not the omen";
        if (crackedStone && falseGate) return "One stone is gone and one eye is lying; trust the distance, not the omen";
        if (waningLantern) return "Only the present step and its reach stay bright in the dark";
        if (crackedStone) return "One stone has fallen away; jump by the number beneath your feet";
        if (longMarch) return "The crossing is longer than it first appears";
        if (falseGate || forkedOmen) return "Some eyes are false; the numbered path is the only truth";
        return "The bright stone tells the distance. Move exactly that many tiles up, down, left or right.";
    }
}
