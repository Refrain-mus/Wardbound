package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The Shardsong: six rune tablets have been broken out of a larger inscription.
 * Rotate each shard until neighbouring edge-runes agree and the blank border is
 * turned outward again.
 *
 * <p>The solved edge map is authored first. The starting state is produced only
 * by applying legal shard rotations, which makes every board provably solvable.
 * Left click turns clockwise, right click turns counter-clockwise; the puzzle is
 * spatial edge matching rather than another timing, memory or sequence game.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ShardsongScreen extends BaseMinigameScreen {

    private static final int ROWS = 2;
    private static final int COLS = 3;
    private static final int TILES = ROWS * COLS;
    private static final int TILE = 54;
    private static final int GAP = 12;

    private final boolean linkedShards;
    private final boolean ashenShard;
    private final boolean falseEtchings;
    private final boolean resonantEdges;
    private final boolean turntableShard;
    private final int rounds;

    /** Base solved edge labels: north, east, south, west. Zero is the dead border. */
    private final int[][] edge = new int[TILES][4];
    private final int[] glyph = new int[TILES];
    private final int[] rot = new int[TILES];
    private final int[] bgGlyph = new int[18];
    private final int[] bgX = new int[18];
    private final int[] bgY = new int[18];

    private int fixedTile = -1;
    private int turntableTile = -1;
    private float autoTurnIn;
    private int movesLeft;
    private int idealMoves;
    private int usedMoves;
    private int totalIdeal;
    private int totalUsed;
    private int lastTile = -1;
    private float flash;
    private float pulse;

    public ShardsongScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.shardsong"), msg);
        boolean[] q = pickVariants(3, 2039);
        linkedShards = q[0];
        ashenShard = q[1];
        falseEtchings = q[2];
        resonantEdges = expertVariant(2051, 2, 72, 0.48f);
        turntableShard = expertVariant(2052, 4, 96, 0.22f);
        if (linkedShards) modifierLabels.add("linked shards");
        if (ashenShard) modifierLabels.add("ashen shard");
        if (falseEtchings) modifierLabels.add("false etchings");
        if (resonantEdges) modifierLabels.add("resonant edges");
        if (turntableShard) modifierLabels.add("turntable shard");

        rounds = Mth.clamp(2 + (value >= 62 || difficulty > 1.28f ? 1 : 0), 2, 3);
        buildRound();
    }

    @Override
    protected Theme createTheme() {
        return Theme.abyss();
    }

    @Override
    protected int panelHeight() {
        return 278;
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
        Random r = new Random(seed ^ 0x5348415244534F4EL);
        int lx = left() + 18;
        int rx = left() + panelWidth() - 18;
        int ty = contentTop() + 8;
        int by = contentBottom() - 8;
        for (int i = 0; i < bgGlyph.length; i++) {
            bgGlyph[i] = r.nextInt(Sigils.TILE.length);
            bgX[i] = lx + r.nextInt(Math.max(1, rx - lx));
            bgY[i] = ty + r.nextInt(Math.max(1, by - ty));
        }
    }

    private void buildRound() {
        Random r = new Random(seed ^ (progress * 0x9E3779B97F4A7C15L) ^ 0x53484EL);
        for (int t = 0; t < TILES; t++) {
            for (int s = 0; s < 4; s++) edge[t][s] = 0;
            rot[t] = 0;
            glyph[t] = r.nextInt(Sigils.TILE.length);
        }

        // Horizontal joins.
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS - 1; col++) {
                int a = row * COLS + col;
                int b = a + 1;
                int mark = 1 + r.nextInt(4);
                edge[a][1] = mark;
                edge[b][3] = mark;
            }
        }
        // Vertical joins.
        for (int col = 0; col < COLS; col++) {
            int a = col;
            int b = COLS + col;
            int mark = 1 + r.nextInt(4);
            edge[a][2] = mark;
            edge[b][0] = mark;
        }

        fixedTile = ashenShard ? 1 + r.nextInt(TILES - 2) : -1;
        turntableTile = -1;
        if (turntableShard) {
            List<Integer> moving = new ArrayList<>();
            for (int i = 0; i < TILES; i++) {
                if (i == fixedTile) continue;
                if (linkedShards && linkedMate(i) == fixedTile) continue;
                moving.add(i);
            }
            if (!moving.isEmpty()) turntableTile = moving.get(r.nextInt(moving.size()));
        }
        autoTurnIn = 4.4f + r.nextFloat() * 1.4f;
        List<Integer> legal = legalOperators();
        if (legal.isEmpty()) {
            fixedTile = -1;
            legal = legalOperators();
        }

        int clicks = Mth.clamp(4 + progress + Math.round((difficulty - 0.9f) * 1.7f), 4, 7);
        int prev = -1;
        idealMoves = 0;
        for (int i = 0; i < clicks; i++) {
            int op;
            int guard = 0;
            do {
                op = legal.get(r.nextInt(legal.size()));
            } while (op == prev && legal.size() > 1 && ++guard < 12);
            rotateOperator(op, 1, false);
            prev = op;
            idealMoves++;
        }
        if (solved()) {
            rotateOperator(legal.get(0), 1, false);
        }
        int exact = minimalMovesToSolved(legal);
        if (exact > 0) idealMoves = exact;
        movesLeft = idealMoves + 4 + Math.min(2, familiarity / 8);
        usedMoves = 0;
        lastTile = -1;
        flash = 0f;
    }


    private int rotationCode() {
        int code = 0;
        for (int i = 0; i < TILES; i++) code |= (rot[i] & 3) << (i * 2);
        return code;
    }

    private int rotateCodeOne(int code, int tile, int dir) {
        int shiftBits = tile * 2;
        int v = (code >> shiftBits) & 3;
        v = Math.floorMod(v + dir, 4);
        code &= ~(3 << shiftBits);
        code |= v << shiftBits;
        return code;
    }

    private int rotateCodeOperator(int code, int tile, int dir) {
        code = rotateCodeOne(code, tile, dir);
        if (linkedShards) {
            int mate = linkedMate(tile);
            if (mate != tile) code = rotateCodeOne(code, mate, dir);
        }
        return code;
    }

    private int edgeAtCode(int code, int tile, int worldSide) {
        int rotation = (code >> (tile * 2)) & 3;
        int source = Math.floorMod(worldSide - rotation, 4);
        return edge[tile][source];
    }

    private boolean solvedCode(int code) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int t = row * COLS + col;
                if (row == 0 && edgeAtCode(code, t, 0) != 0) return false;
                if (row == ROWS - 1 && edgeAtCode(code, t, 2) != 0) return false;
                if (col == 0 && edgeAtCode(code, t, 3) != 0) return false;
                if (col == COLS - 1 && edgeAtCode(code, t, 1) != 0) return false;
                if (col < COLS - 1 && edgeAtCode(code, t, 1) != edgeAtCode(code, t + 1, 3)) return false;
                if (row < ROWS - 1 && edgeAtCode(code, t, 2) != edgeAtCode(code, t + COLS, 0)) return false;
            }
        }
        return true;
    }

    private int minimalMovesToSolved(List<Integer> legal) {
        int start = rotationCode();
        if (solvedCode(start)) return 0;
        int[] dist = new int[1 << (TILES * 2)];
        Arrays.fill(dist, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        dist[start] = 0;
        q.add(start);
        while (!q.isEmpty()) {
            int state = q.removeFirst();
            int nd = dist[state] + 1;
            for (int tile : legal) {
                int nextLeft = rotateCodeOperator(state, tile, -1);
                if (dist[nextLeft] < 0) {
                    if (solvedCode(nextLeft)) return nd;
                    dist[nextLeft] = nd;
                    q.addLast(nextLeft);
                }
                int nextRight = rotateCodeOperator(state, tile, 1);
                if (dist[nextRight] < 0) {
                    if (solvedCode(nextRight)) return nd;
                    dist[nextRight] = nd;
                    q.addLast(nextRight);
                }
            }
        }
        return -1;
    }

    private List<Integer> legalOperators() {
        List<Integer> out = new ArrayList<>();
        for (int t = 0; t < TILES; t++) {
            if (t == fixedTile) continue;
            int mate = linkedMate(t);
            if (linkedShards && mate == fixedTile) continue;
            if (linkedShards && t > mate) continue;
            out.add(t);
        }
        return out;
    }

    private int linkedMate(int tile) {
        return TILES - 1 - tile;
    }

    private void rotateOne(int tile, int dir) {
        rot[tile] = Math.floorMod(rot[tile] + dir, 4);
    }

    private void rotateOperator(int tile, int dir, boolean feedback) {
        rotateOne(tile, dir);
        if (linkedShards) {
            int mate = linkedMate(tile);
            if (mate != tile) rotateOne(mate, dir);
        }
        if (feedback) {
            lastTile = tile;
            flash = 0.30f;
        }
    }

    private int edgeAt(int tile, int worldSide) {
        int source = Math.floorMod(worldSide - rot[tile], 4);
        return edge[tile][source];
    }

    private boolean solved() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int t = row * COLS + col;
                if (row == 0 && edgeAt(t, 0) != 0) return false;
                if (row == ROWS - 1 && edgeAt(t, 2) != 0) return false;
                if (col == 0 && edgeAt(t, 3) != 0) return false;
                if (col == COLS - 1 && edgeAt(t, 1) != 0) return false;
                if (col < COLS - 1 && edgeAt(t, 1) != edgeAt(t + 1, 3)) return false;
                if (row < ROWS - 1 && edgeAt(t, 2) != edgeAt(t + COLS, 0)) return false;
            }
        }
        return true;
    }

    private int resonanceScore() {
        int score = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int t = row * COLS + col;
                if (col < COLS - 1 && edgeAt(t, 1) == edgeAt(t + 1, 3)) score++;
                if (row < ROWS - 1 && edgeAt(t, 2) == edgeAt(t + COLS, 0)) score++;
            }
        }
        return score;
    }

    private void playResonance(int tile) {
        if (!resonantEdges) {
            Sfx.play(WardSounds.STONE_TURN, 0.40f, 0.86f + tile * 0.035f);
            return;
        }
        int score = resonanceScore();
        float pitch = 0.70f + score * 0.075f;
        Sfx.play(WardSounds.STONE_TURN, 0.38f, pitch);
        if (score >= 5) Sfx.play(WardSounds.VEILSTEP_CHIME, 0.18f, 0.88f + score * 0.045f);
    }

    private void completeSolvedRound() {
        totalIdeal += idealMoves;
        totalUsed += usedMoves;
        progress++;
        Sfx.mark(progress + 1);
        burst(centerX(), contentCenterY(), COL_GOOD);
        if (progress >= rounds) {
            float efficiency = totalUsed <= 0 ? 1f : Mth.clamp((float) totalIdeal / totalUsed, 0.42f, 1f);
            succeed(efficiency);
        } else {
            showBanner(resonantEdges ? "The chord resolves into another broken stanza" : "The broken cant remembers another stanza", COL_GOOD, 760);
            buildRound();
        }
    }

    private int boardW() { return COLS * TILE + (COLS - 1) * GAP; }
    private int boardH() { return ROWS * TILE + (ROWS - 1) * GAP; }
    private int boardX() { return centerX() - boardW() / 2; }
    private int boardY() { return contentCenterY() - boardH() / 2 - 4; }

    private int tileAt(double mx, double my) {
        int bx = boardX(), by = boardY();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = bx + c * (TILE + GAP);
                int y = by + r * (TILE + GAP);
                if (mx >= x && mx < x + TILE && my >= y && my < y + TILE) return r * COLS + c;
            }
        }
        return -1;
    }

    @Override
    protected void step(float dt) {
        pulse += dt;
        if (flash > 0f) flash = Math.max(0f, flash - dt);
        if (turntableTile >= 0 && !resolved) {
            autoTurnIn -= dt;
            if (autoTurnIn <= 0f) {
                rotateOperator(turntableTile, 1, true);
                Sfx.play(WardSounds.STONE_TURN, 0.30f, 0.66f);
                autoTurnIn = 4.4f + seedVariance(2060 + progress) * 1.4f;
                if (solved()) completeSolvedRound();
            }
        }
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int tile = tileAt(mx, my);
        if (tile < 0 || (button != 0 && button != 1)) return false;
        if (tile == fixedTile || (linkedShards && linkedMate(tile) == fixedTile)) {
            Sfx.markBad();
            Sfx.play(WardSounds.BRAID_REBUKE, 0.34f, 0.80f);
            showBanner("The ashen shard will not turn", COL_BAD, 700);
            return true;
        }

        int dir = button == 1 ? -1 : 1;
        rotateOperator(tile, dir, true);
        movesLeft--;
        usedMoves++;
        playResonance(tile);

        if (solved()) {
            completeSolvedRound();
            return true;
        }

        if (movesLeft <= 0) {
            totalUsed += usedMoves;
            if (loseLife("The shards sing against one another")) buildRound();
        }
        return true;
    }

    @Override
    protected boolean onKey(int key) { return false; }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int bx = boardX(), by = boardY();
        int hover = tileAt(mouseX, mouseY);

        // Runic wallpaper beneath the broken tablet.
        for (int i = 0; i < bgGlyph.length; i++) {
            int a = 0x12 + (i % 4) * 4;
            if (!WardConfig.accessibilityReduceMotion) {
                a += Math.round((0.5f + 0.5f * Mth.sin(pulse * 0.55f + i * 0.7f)) * 5f);
            }
            Sigils.drawCentered(p, Sigils.TILE[bgGlyph[i]], bgX[i], bgY[i], 1,
                    Painter.withAlpha(t.frameLite, a), Painter.withAlpha(t.accentDark, a / 2), 0);
        }

        p.roundRect(bx - 18, by - 16, boardW() + 36, boardH() + 32, 0xFF06070B);
        p.roundOutline(bx - 17, by - 15, boardW() + 34, boardH() + 30, t.frameDark);
        p.dither(bx - 14, by - 12, boardW() + 28, boardH() + 24,
                Painter.withAlpha(t.accentDark, 0x12), 4, progress * 3);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) drawTile(r * COLS + c, c, r, hover);
        }

        progressDots(centerX(), by + boardH() + 22, rounds, progress);
        smallCentered(g, "turn shards until every touching rune agrees", centerX(), by + boardH() + 35,
                t.dim, 0.78f);
        smallCentered(g, "moves " + movesLeft + "   ·   left/right click = turn each way", centerX(),
                by + boardH() + 47, movesLeft <= 2 ? COL_BAD : t.faint, 0.72f);
        if (resonantEdges) smallCentered(g, "listen: consonance rises as true edges agree", centerX(),
                by + boardH() + 58, t.faint, 0.66f);
    }

    private void drawTile(int tile, int col, int row, int hover) {
        Theme t = theme();
        int x = boardX() + col * (TILE + GAP);
        int y = boardY() + row * (TILE + GAP);
        boolean hot = tile == hover;
        boolean fixed = tile == fixedTile;
        boolean flashed = flash > 0f && (tile == lastTile || (linkedShards && tile == linkedMate(lastTile)));

        int body = fixed ? 0xFF171718 : (hot ? Painter.mix(t.well, t.accentDark, 0.18f) : t.well);
        p.roundRect(x - 2, y - 2, TILE + 4, TILE + 4, fixed ? Painter.darken(t.frame, 0.45f) : t.frameDark);
        p.roundRect(x, y, TILE, TILE, body);
        p.dither(x + 2, y + 2, TILE - 4, TILE - 4,
                Painter.withAlpha(t.frameLite, fixed ? 0x07 : 0x0E), 3, tile + progress * 7);

        // Central sigil rotates with the shard, so orientation is readable even
        // before the player starts comparing edge marks.
        boolean fx = (rot[tile] & 1) != 0;
        boolean fy = (rot[tile] & 2) != 0;
        int rune = fixed ? Painter.withAlpha(t.faint, 0x88) : t.accentLite;
        Sigils.drawCenteredFlipped(p, Sigils.TILE[glyph[tile]], x + TILE / 2, y + TILE / 2, 2,
                rune, Painter.withAlpha(t.accent, fixed ? 0x22 : 0x66), 0x55000000, fx, fy);

        for (int side = 0; side < 4; side++) drawEdgeMark(x, y, side, edgeAt(tile, side), fixed);

        if (falseEtchings && tile % 2 == 0) {
            int fake = 1 + Math.floorMod(tile + progress, 4);
            int px = x + TILE / 2 + (tile % 3 - 1) * 7;
            int py = y + TILE / 2 + (tile / 3 == 0 ? 12 : -12);
            drawMarkShape(px, py, fake, Painter.withAlpha(t.faint, 0x38));
        }

        if (fixed) {
            p.line(x + 7, y + 7, x + TILE - 7, y + TILE - 7, 1, Painter.withAlpha(COL_BAD, 0x88));
            p.line(x + TILE - 7, y + 7, x + 7, y + TILE - 7, 1, Painter.withAlpha(COL_BAD, 0x88));
        }
        if (tile == turntableTile) {
            int warn = autoTurnIn < 1.0f ? 0xFFD8B778 : Painter.withAlpha(t.accent, 0x70);
            p.ringThick(x + TILE / 2, y + TILE / 2, 22, autoTurnIn < 1.0f ? 2 : 1, warn);
        }
        if (hot && !fixed) p.roundOutline(x - 3, y - 3, TILE + 6, TILE + 6, t.accentLite);
        if (flashed) p.roundOutline(x - 5, y - 5, TILE + 10, TILE + 10,
                Painter.scaleAlpha(t.accentLite, flash));
    }

    private void drawEdgeMark(int x, int y, int side, int mark, boolean fixed) {
        Theme t = theme();
        int cx = x + TILE / 2;
        int cy = y + TILE / 2;
        int px = cx, py = cy;
        if (side == 0) py = y + 5;
        else if (side == 1) px = x + TILE - 5;
        else if (side == 2) py = y + TILE - 5;
        else px = x + 5;
        int col = mark == 0 ? Painter.withAlpha(t.frame, 0x55)
                : Painter.withAlpha(fixed ? t.faint : edgeColor(mark), fixed ? 0x88 : 0xE5);
        drawMarkShape(px, py, mark, col);
    }

    private int edgeColor(int mark) {
        return switch (mark) {
            case 1 -> 0xFFB8D88A;
            case 2 -> 0xFFD8B778;
            case 3 -> 0xFFA995D7;
            default -> 0xFF78B8C6;
        };
    }

    private void drawMarkShape(int x, int y, int mark, int col) {
        switch (mark) {
            case 0 -> p.rect(x - 2, y - 1, x + 2, y + 1, col);
            case 1 -> {
                p.rect(x - 3, y - 1, x + 3, y + 1, col);
                p.rect(x - 1, y - 3, x + 1, y + 3, col);
            }
            case 2 -> {
                p.line(x - 3, y + 3, x, y - 3, 1, col);
                p.line(x, y - 3, x + 3, y + 3, 1, col);
            }
            case 3 -> p.ringThick(x, y, 4, 2, col);
            default -> {
                p.rect(x - 3, y - 3, x + 3, y - 1, col);
                p.rect(x - 3, y + 1, x + 3, y + 3, col);
            }
        }
    }

    @Override
    protected String hintText() {
        if (turntableShard) return "The ringed shard turns on its own; its warning brightens before each turn, and waiting is always legal";
        if (resonantEdges) return "The edge shapes stay truthful while the pitch rises with consonant joins; hearing is a second solution channel";
        if (linkedShards && ashenShard) return "Opposite shards answer together; one has turned to ash";
        if (linkedShards) return "Opposite shards answer the same turn";
        if (ashenShard) return "The crossed shard is fixed; fit the inscription around it";
        if (falseEtchings) return "Faint scratches are old lies; only edge-runes bind the tablet";
        return "Match every touching edge-rune and turn the blank edges outward";
    }
}
