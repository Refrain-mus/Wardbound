package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Rootway. A wet stone door with channels cut into it, and something pale
 * growing along them.
 *
 * <p>Sixteen locks in, every one of them was reaction, memory or inference. None
 * of them was <i>planning</i> - looking at a space, deciding where a thing
 * should go, and being wrong about it in a way you can see coming. This one is.
 *
 * <p>The face of the door is a grid of stone blocks, each with a channel carved
 * through it, and each of those channels can be turned. A root enters at the
 * left and grows along whatever channel it finds, at its own pace, whether or
 * not you are ready for it. Turn the blocks ahead of it so the channel carries
 * on. Get it to the far side and the door opens.
 *
 * <p>The root does not wait and it does not stop. You cannot turn a block it has
 * already entered, so a route you laid badly four blocks ago is a route you are
 * now committed to, and the pressure is not the clock: it is that the mistake
 * you are about to make has already happened and is three seconds away from
 * finding you.
 *
 * <p>Quirks a chest may carry:
 * <ul>
 *   <li>0 moss - some blocks are grown over and their channel cannot be read
 *       until you scrape them, which costs a turn of the block you did not
 *       spend elsewhere.</li>
 *   <li>1 dead stone - a few blocks are split through and will not hold a
 *       channel at all. Route around them.</li>
 *   <li>2 quickening - the root speeds up with every block it fills, so the
 *       far side of the door is a different problem from the near side.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class RootwayScreen extends BaseMinigameScreen {

    /** Openings, as a bitmask. */
    private static final int N = 1, E = 2, S = 4, W = 8;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};

    private static final int TILE = 30;

    private final boolean mossy;
    private final boolean deadStone;
    private final boolean quickening;

    private final int cols;
    private final int rows;
    /** Opening mask per tile. Every live tile has exactly two, so the route never forks. */
    private final int[] shape;
    private final boolean[] dead;
    private final boolean[] hidden;
    private final boolean[] filled;
    /** One guaranteed solvable route; dead stone is never placed on it. */
    private final boolean[] solutionRoute;

    private final int entryRow;
    private final float baseSpeed;

    private final Random rng;

    /** Where the root is, how far through that tile, and which way it came in. */
    private int headX;
    private int headY;
    private int headFrom = W;
    private float crawl;
    /**
     * A pause before the root moves at all.
     *
     * <p>Without it the lock opened with the root already travelling and the
     * first two blocks were a scramble rather than a decision, which is the
     * opposite of what this lock is for.
     */
    private float grace = 2.0f;
    private boolean finished;

    private int filledCount;
    private int turns;

    private float flashTimer;
    private int flashColor = COL_GOOD;
    private int flashTile = -1;
    private float wet;

    public RootwayScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.rootway"), msg);

        boolean[] v = pickVariants(3, 104);
        mossy = v[0];
        deadStone = v[1];
        quickening = v[2];
        if (mossy) modifierLabels.add("moss");
        if (deadStone) modifierLabels.add("dead stone");
        if (quickening) modifierLabels.add("quickening");

        this.rng = new Random(seed ^ 0x2005_7A00L);
        this.cols = Mth.clamp(4 + Math.round(value / 55f * difficulty), 4, 6);
        this.rows = 4;

        int n = cols * rows;
        shape = new int[n];
        dead = new boolean[n];
        hidden = new boolean[n];
        filled = new boolean[n];
        solutionRoute = new boolean[n];

        this.entryRow = rng.nextInt(rows);

        // Every tile is an elbow or a straight, and both have exactly two
        // openings. Three-way junctions would mean the root has a choice, and a
        // root that chooses is a root you are no longer routing.
        int[] pieces = {N | S, E | W, N | E, E | S, S | W, W | N};
        for (int i = 0; i < n; i++) {
            shape[i] = pieces[rng.nextInt(pieces.length)];
        }

        // Carve one real route before placing hazards. Earlier builds only
        // randomised piece types, so some boards had no possible path at all
        // regardless of how well the player rotated them. The route remains
        // hidden from the player: its pieces are scrambled just like everything
        // else, but their straight/elbow type is guaranteed to be sufficient.
        List<Integer> route = buildGuaranteedRoute();
        for (int i = 0; i < route.size(); i++) {
            int cell = route.get(i);
            solutionRoute[cell] = true;
            int cx = cell % cols;
            int cy = cell / cols;
            int in = i == 0 ? W : directionTo(cx, cy, route.get(i - 1) % cols, route.get(i - 1) / cols);
            int out = i == route.size() - 1 ? E
                    : directionTo(cx, cy, route.get(i + 1) % cols, route.get(i + 1) / cols);
            int solvedMask = in | out;
            int scrambled = solvedMask;
            int turnsToScramble = i == 0 ? 0 : rng.nextInt(4);
            for (int r = 0; r < turnsToScramble; r++) scrambled = rotate(scrambled, 1);
            shape[cell] = scrambled;
        }

        if (deadStone) {
            int howMany = 1 + rng.nextInt(cols >= 6 ? 3 : 2);
            for (int k = 0; k < howMany; k++) {
                for (int guard = 0; guard < 40; guard++) {
                    int x = 1 + rng.nextInt(cols - 1);
                    int y = rng.nextInt(rows);
                    int cell = idx(x, y);
                    // A hazard may make the obvious route ugly, never impossible.
                    if (solutionRoute[cell]) continue;
                    if (columnDeadCount(x) >= rows - 1) continue;
                    dead[cell] = true;
                    break;
                }
            }
        }

        if (mossy) {
            for (int i = 0; i < n; i++) {
                hidden[i] = !dead[i] && rng.nextFloat() < 0.30f;
            }
            hidden[idx(0, entryRow)] = false;
        }

        this.baseSpeed = (0.62f + value * 0.0050f) * difficulty * (0.85f + seedVariance(105) * 0.4f);

        headX = 0;
        headY = entryRow;
    }

    @Override
    protected Theme createTheme() {
        return Theme.rootway();
    }

    @Override
    protected int panelWidth() {
        return 320;
    }

    @Override
    protected int panelHeight() {
        return 250;
    }

    /** Planning needs a little room, but not enough to plan the whole door before starting. */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 1.05f;
    }

    /** The root is the pressure; taking the picture away just makes it unplayable. */
    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING;
    }

    private int idx(int x, int y) {
        return y * cols + x;
    }

    private int columnDeadCount(int x) {
        int c = 0;
        for (int y = 0; y < rows; y++) if (dead[idx(x, y)]) c++;
        return c;
    }

    /** Builds a deterministic, self-avoiding route from the left entry to the far edge. */
    private List<Integer> buildGuaranteedRoute() {
        List<Integer> best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int attempt = 0; attempt < 10; attempt++) {
            boolean[] seen = new boolean[cols * rows];
            List<Integer> path = new ArrayList<>();
            if (!carveRoute(0, entryRow, seen, path)) continue;
            int score = routeInterest(path);
            if (best == null || score > bestScore) {
                best = new ArrayList<>(path);
                bestScore = score;
            }
            if (score >= cols + 3) return path;
        }

        if (best != null) return best;
        // The DFS should always succeed on a rectangular grid, but keep a
        // straight fallback so a future rule change cannot reintroduce an
        // unwinnable board.
        List<Integer> path = new ArrayList<>();
        for (int x = 0; x < cols; x++) path.add(idx(x, entryRow));
        return path;
    }

    private int routeInterest(List<Integer> path) {
        if (path == null || path.size() < 2) return 0;
        int bends = 0;
        int vertical = 0;
        int longestStraight = 1;
        int currentStraight = 1;
        int prevDir = 0;
        for (int i = 1; i < path.size(); i++) {
            int ax = path.get(i - 1) % cols, ay = path.get(i - 1) / cols;
            int bx = path.get(i) % cols, by = path.get(i) / cols;
            int dir = directionTo(ax, ay, bx, by);
            if (dir == N || dir == S) vertical++;
            if (i > 1 && dir != prevDir) bends++;
            if (i > 1 && dir == prevDir) currentStraight++; else currentStraight = 1;
            longestStraight = Math.max(longestStraight, currentStraight);
            prevDir = dir;
        }
        return bends * 3 + vertical * 2 - longestStraight;
    }

    private boolean carveRoute(int x, int y, boolean[] seen, List<Integer> path) {
        int cell = idx(x, y);
        seen[cell] = true;
        path.add(cell);
        if (x == cols - 1) return true;

        List<Integer> dirs = new ArrayList<>(List.of(N, S, W));
        Collections.shuffle(dirs, rng);
        // Bias east so most boards read as a route rather than a maze, while
        // still allowing vertical detours and occasional backtracking.
        if (rng.nextFloat() < 0.74f) dirs.add(0, E);
        else {
            dirs.add(E);
            Collections.shuffle(dirs, rng);
        }
        for (int dir : dirs) {
            int di = dirIndex(dir);
            int nx = x + DX[di], ny = y + DY[di];
            if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) continue;
            int next = idx(nx, ny);
            if (seen[next]) continue;
            if (carveRoute(nx, ny, seen, path)) return true;
        }

        path.remove(path.size() - 1);
        return false;
    }

    private static int directionTo(int x, int y, int tx, int ty) {
        if (tx > x) return E;
        if (tx < x) return W;
        if (ty > y) return S;
        return N;
    }

    // ------------------------------------------------------------------ simulation

    private float speedNow() {
        return baseSpeed * (quickening ? 1f + filledCount * 0.14f : 1f);
    }

    private static int opposite(int dir) {
        return switch (dir) {
            case N -> S;
            case S -> N;
            case E -> W;
            default -> E;
        };
    }

    private static int dirIndex(int dir) {
        return switch (dir) {
            case N -> 0;
            case E -> 1;
            case S -> 2;
            default -> 3;
        };
    }

    @Override
    protected void step(float dt) {
        if (flashTimer > 0f) flashTimer -= dt;
        wet += dt;
        if (finished || resolved) return;

        if (grace > 0f) {
            grace -= dt;
            return;
        }
        crawl += speedNow() * dt;
        if (crawl < 1f) return;
        crawl = 0f;
        advance();
    }

    /** The root finishes the tile it is in and tries to enter the next one. */
    private void advance() {
        int here = idx(headX, headY);

        // The entry block is the only one that can still be wrong on arrival,
        // because it is the only one nothing checked before the root reached it.
        // Every other block is vetted at the end of the previous step, below.
        if (dead[here] || (shape[here] & headFrom) == 0) {
            wither("The root found no channel");
            return;
        }

        if (!filled[here]) {
            filled[here] = true;
            filledCount++;
            progress = filledCount;
            Sfx.play(WardSounds.ROOT_GROW, 0.50f, 0.88f + Math.min(0.45f, filledCount * 0.035f));
        }

        int out = shape[here] & ~headFrom;
        int di = dirIndex(out);
        int nx = headX + DX[di], ny = headY + DY[di];

        // Out of the far side is the way through; out of any other edge is not.
        if (nx >= cols) {
            finished = true;
            Sfx.plugTurn(0.8f);
            succeed(Mth.clamp(1f - turns / (float) Math.max(8, cols * rows), 0.25f, 1f));
            return;
        }
        if (nx < 0 || ny < 0 || ny >= rows) {
            wither("The root ran out of the stone");
            return;
        }

        // Look before moving.
        //
        // The check used to happen at the top of the next advance, which meant
        // the head visibly crossed into a block with no matching channel, sat
        // there for a beat, and only then withered. It read as the root passing
        // through a wall and dying afterwards for no reason.
        int nextIdx = idx(nx, ny);
        int nextFrom = opposite(out);
        if (dead[nextIdx] || (shape[nextIdx] & nextFrom) == 0) {
            flashTile = nextIdx;
            wither(dead[nextIdx] ? "It met split stone" : "The channel did not line up");
            return;
        }

        // And the block after that has to accept it too, or the head would sit
        // in a dead end for a whole step looking like it walked into stone. This
        // is the check that was missing: one block of lookahead is not enough
        // when the head is drawn part way into the block it is entering.
        headX = nx;
        headY = ny;
        headFrom = nextFrom;
    }

    private void wither(String why) {
        flashTile = idx(headX, headY);
        flashTimer = 0.6f;
        flashColor = COL_BAD;
        Sfx.play(WardSounds.ROOT_WITHER, 0.75f, 1.0f);

        // Back to the entry, and the door keeps whatever you got right. Sending
        // it all the way back would make a late mistake cost the whole lock,
        // which on a lock about planning is a punishment for having tried.
        headX = 0;
        headY = entryRow;
        headFrom = W;
        crawl = 0f;
        grace = 1.2f;              // a shorter breath on a restart
        for (int i = 0; i < filled.length; i++) filled[i] = false;
        filledCount = 0;
        progress = 0;
        loseLife(why);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int gx = gridX(), gy = gridY();
        int cx = (int) ((mx - gx) / TILE);
        int cy = (int) ((my - gy) / TILE);
        if (cx < 0 || cy < 0 || cx >= cols || cy >= rows) return false;

        int i = idx(cx, cy);
        if (dead[i]) {
            Sfx.markBad();
            showBanner("That stone is split through", theme().dim, 600);
            return true;
        }
        if (filled[i] || (cx == headX && cy == headY)) {
            // A block the root is in or has been through is committed. This is
            // the rule the whole lock rests on.
            Sfx.markBad();
            showBanner("The root is already in that one", theme().dim, 600);
            return true;
        }

        if (hidden[i]) {
            hidden[i] = false;
            turns++;
            Sfx.play(WardSounds.STONE_TURN, 0.40f, 1.25f);
            showBanner("Scraped clear", theme().dim, 450);
            return true;
        }

        shape[i] = rotate(shape[i], button == 1 ? -1 : 1);
        turns++;
        Sfx.play(WardSounds.STONE_TURN, 0.55f, 0.92f + (i % 5) * 0.045f);
        return true;
    }

    private static int rotate(int mask, int dir) {
        int out = 0;
        for (int d = 0; d < 4; d++) {
            int bit = 1 << d;
            if ((mask & bit) == 0) continue;
            out |= 1 << Math.floorMod(d + dir, 4);
        }
        return out;
    }

    @Override
    protected boolean onKey(int key) {
        return false;
    }

    // ------------------------------------------------------------------ layout

    private int gridX() {
        return centerX() - (cols * TILE) / 2;
    }

    private int gridY() {
        return (contentTop() + contentBottom()) / 2 - (rows * TILE) / 2;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int gx = gridX(), gy = gridY();
        int w = cols * TILE, h = rows * TILE;

        // ---- the door itself: wet stone, sunk into a frame, weeping down the face
        p.roundRect(gx - 12, gy - 12, w + 24, h + 24, t.frameDark);
        p.roundRect(gx - 10, gy - 10, w + 20, h + 20, Painter.darken(t.panel, 0.10f) | 0xFF000000);
        p.dither(gx - 10, gy - 10, w + 20, h + 20, Painter.withAlpha(t.frame, 0x14), 3, 0);
        for (int i = 0; i < 6; i++) {
            int sx = gx - 6 + (i * 53) % (w + 12);
            int len = 14 + (i * 29) % 40;
            p.rect(sx, gy - 10, sx + 2, gy - 10 + len, Painter.withAlpha(0x0A1410, 0x66));
        }

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                drawTile(x, y, mouseX, mouseY);
            }
        }

        drawSource(gx, gy);
        drawMouth(gx + w, gy);
        drawHead(gx, gy);

        smallCentered(g, filledCount + " of " + (cols * rows) + " stones running",
                centerX(), gy + h + 16, t.dim, 0.8f);
    }

    private void drawTile(int x, int y, int mouseX, int mouseY) {
        Theme t = theme();
        int i = idx(x, y);
        int px = gridX() + x * TILE, py = gridY() + y * TILE;
        boolean hover = mouseX >= px && mouseX < px + TILE && mouseY >= py && mouseY < py + TILE;
        boolean head = x == headX && y == headY;

        // the block
        int stone = dead[i] ? Painter.darken(t.frame, 0.55f) : t.frame;
        p.rect(px, py, px + TILE, py + TILE, Painter.darken(stone, 0.65f));
        p.rect(px + 1, py + 1, px + TILE - 1, py + TILE - 1, Painter.darken(stone, 0.35f));
        p.rect(px + 1, py + 1, px + TILE - 1, py + 3, Painter.darken(stone, 0.15f));
        p.dither(px + 2, py + 2, TILE - 4, TILE - 4, Painter.withAlpha(0x000000, 0x18), 2, i);

        if (dead[i]) {
            // split through, twice, so it reads as broken rather than as dark
            p.line(px + 4, py + 5, px + TILE - 6, py + TILE - 4, 2, 0xFF0A0B09);
            p.line(px + TILE - 7, py + 4, px + 6, py + TILE - 7, 1, 0xFF0A0B09);
            return;
        }

        if (hidden[i]) {
            drawMoss(px, py, i, true);
            if (hover) p.outline(px + 1, py + 1, TILE - 2, TILE - 2, 1,
                    Painter.withAlpha(t.accentLite, 0x88));
            return;
        }

        // the channel cut through it
        int mid = TILE / 2;
        int chan = Painter.darken(stone, 0.75f);
        drawChannel(px, py, shape[i], 7, chan);
        drawChannel(px, py, shape[i], 5, Painter.darken(stone, 0.55f));

        // and whatever is growing in it. Completed stones may show the full
        // channel, but the live head only paints the path it has actually
        // traversed. This stops an elbow from looking as if the root has already
        // grown through its unopened/walled arm.
        if (filled[i] && !head) {
            int rootCol = 0xFFBFC6A8;
            drawChannel(px, py, shape[i], 3, 0xFF2E3A24);
            drawChannel(px, py, shape[i], 2, rootCol);
            for (int k = 0; k < 3; k++) {
                int hx = px + 6 + ((i * 7 + k * 11) % (TILE - 12));
                int hy = py + mid + (k % 2 == 0 ? -4 : 4);
                p.rect(hx, hy, hx + 1, hy + 2, Painter.withAlpha(rootCol, 0x66));
            }
        } else if (head) {
            drawLiveRoot(px, py, shape[i]);
        }

        drawMoss(px, py, i, false);

        if (i == flashTile && flashTimer > 0f) {
            p.outline(px, py, TILE, TILE, 2,
                    Painter.withAlpha(flashColor, Math.round(flashTimer * 0xD0)));
        }
        if (hover && !filled[i] && !head) {
            p.outline(px + 1, py + 1, TILE - 2, TILE - 2, 1,
                    Painter.withAlpha(t.accentLite, 0x99));
        }
    }

    /** Paint only the portion of the current channel already traversed by the live root. */
    private void drawLiveRoot(int px, int py, int mask) {
        int mid = TILE / 2;
        int out = mask & ~headFrom;
        int rootDark = 0xFF2E3A24;
        int rootLite = 0xFFBFC6A8;
        int in = dirIndex(headFrom);
        int edgeX = px + mid + DX[in] * mid;
        int edgeY = py + mid + DY[in] * mid;

        if (crawl <= 0.5f) {
            float q = Mth.clamp(crawl / 0.5f, 0f, 1f);
            int hx = Math.round(Mth.lerp(q, edgeX, px + mid));
            int hy = Math.round(Mth.lerp(q, edgeY, py + mid));
            p.line(edgeX, edgeY, hx, hy, 5, rootDark);
            p.line(edgeX, edgeY, hx, hy, 2, rootLite);
        } else {
            p.line(edgeX, edgeY, px + mid, py + mid, 5, rootDark);
            p.line(edgeX, edgeY, px + mid, py + mid, 2, rootLite);
            if (Integer.bitCount(out) == 1) {
                int oi = dirIndex(out);
                float q = Mth.clamp((crawl - 0.5f) / 0.5f, 0f, 1f);
                int outX = px + mid + DX[oi] * mid;
                int outY = py + mid + DY[oi] * mid;
                int hx = Math.round(Mth.lerp(q, px + mid, outX));
                int hy = Math.round(Mth.lerp(q, py + mid, outY));
                p.line(px + mid, py + mid, hx, hy, 5, rootDark);
                p.line(px + mid, py + mid, hx, hy, 2, rootLite);
            }
        }
    }

    /** A channel arm from the centre out to each opening. */
    private void drawChannel(int px, int py, int mask, int half, int col) {
        int mid = TILE / 2;
        p.rect(px + mid - half, py + mid - half, px + mid + half, py + mid + half, col);
        if ((mask & N) != 0) p.rect(px + mid - half, py, px + mid + half, py + mid, col);
        if ((mask & S) != 0) p.rect(px + mid - half, py + mid, px + mid + half, py + TILE, col);
        if ((mask & W) != 0) p.rect(px, py + mid - half, px + mid, py + mid + half, col);
        if ((mask & E) != 0) p.rect(px + mid, py + mid - half, px + TILE, py + mid + half, col);
    }

    /**
     * Moss. Heavy where a tile is grown over, a few clumps in the corners
     * everywhere else, seeded off the tile index so it does not crawl about
     * between frames.
     */
    private void drawMoss(int px, int py, int i, boolean heavy) {
        int base = 0xFF3D5232;
        int lit = 0xFF57703F;
        int count = heavy ? 26 : 5;
        for (int k = 0; k < count; k++) {
            int h = (i * 2654435761L != 0 ? (int) ((i * 31L + k * 2654435761L) >>> 13) : k);
            int mx = px + 2 + Math.floorMod(h, TILE - 4);
            int my = py + 2 + Math.floorMod(h >> 5, TILE - 4);
            int size = heavy ? 2 + Math.floorMod(h >> 11, 3) : 2;
            p.rect(mx, my, mx + size, my + size,
                    Painter.withAlpha(Math.floorMod(h, 3) == 0 ? lit : base, heavy ? 0xE8 : 0x77));
        }
        if (heavy) {
            // a couple of pale fronds so it is clearly growth and not a stain
            for (int k = 0; k < 3; k++) {
                int mx = px + 6 + k * 8;
                p.rect(mx, py + 6, mx + 1, py + 12, Painter.withAlpha(0xFF7E9A5C, 0xAA));
            }
        }
    }

    /** Where the root comes from: a crack at the left edge with something in it. */
    private void drawSource(int gx, int gy) {
        int y = gy + entryRow * TILE + TILE / 2;
        p.rect(gx - 12, y - 5, gx, y + 5, 0xFF14180F);
        p.rect(gx - 11, y - 3, gx, y + 3, 0xFF2E3A24);
        p.rect(gx - 10, y - 2, gx, y + 2, 0xFFBFC6A8);
        for (int k = 0; k < 4; k++) {
            p.rect(gx - 12 - k * 2, y - 3 + (k % 2) * 6, gx - 11 - k * 2, y - 1 + (k % 2) * 6,
                    Painter.withAlpha(0xFFBFC6A8, 0x88 - k * 0x18));
        }
    }

    /** And where it has to get to. */
    private void drawMouth(int rightEdge, int gy) {
        Theme t = theme();
        int glow = Painter.withAlpha(t.accentLite, 0x60 + Math.round(breath(0.6f) * 0x60));
        for (int y = 0; y < rows; y++) {
            int cy = gy + y * TILE + TILE / 2;
            p.rect(rightEdge, cy - 4, rightEdge + 9, cy + 4, Painter.withAlpha(0x0A1410, 0xCC));
            p.rect(rightEdge + 1, cy - 2, rightEdge + 9, cy + 2, glow);
        }
    }

    /** The growing tip follows the channel geometry inside the current tile. */
    private void drawHead(int gx, int gy) {
        if (finished) return;
        int px = gx + headX * TILE, py = gy + headY * TILE;
        int mid = TILE / 2;
        int here = idx(headX, headY);
        int out = shape[here] & ~headFrom;
        int hx = px + mid, hy = py + mid;

        // The old renderer always drew the tip straight toward the side opposite
        // the entry. On an elbow tile the simulation turned correctly but the
        // sprite visibly travelled through the stone wall. First half: incoming
        // edge -> centre. Second half: centre -> the tile's actual outgoing arm.
        if (crawl < 0.5f) {
            int inDir = dirIndex(headFrom);
            float d = (0.5f - crawl) * TILE;
            hx += Math.round(DX[inDir] * d);
            hy += Math.round(DY[inDir] * d);
        } else if (Integer.bitCount(out) == 1) {
            int outDir = dirIndex(out);
            float d = (crawl - 0.5f) * TILE;
            hx += Math.round(DX[outDir] * d);
            hy += Math.round(DY[outDir] * d);
        }

        p.disc(hx, hy, 4, 0xFF2E3A24);
        p.disc(hx, hy, 3, 0xFFD3D9BC);
        p.disc(hx - 1, hy - 1, 1, 0xFFF0F3E4);
        // a wet bead that slides off it
        int drip = Math.round(Mth.sin(wet * 2.1f) * 3f);
        p.rect(hx - 1, hy + 4 + drip, hx + 1, hy + 6 + drip, Painter.withAlpha(0xFFA8C0A0, 0x77));
    }

    @Override
    protected String hintText() {
        if (finished) return "It is through";
        if (grace > 0f) return String.format("It starts moving in %.1fs \u2014 lay the way now", grace);
        if (mossy) return "Turn the stones ahead of it \u00b7 click moss to scrape it clear";
        return "Turn the stones ahead of it \u2014 it will not wait for you";
    }
}
