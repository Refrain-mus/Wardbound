package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

/**
 * Black Orrery: four coupled occult rings. Turning one ring also turns the ring
 * meshed to it; advanced seals may mesh both neighbours. The state is always
 * produced by legal moves from a solved meridian, and the exact shortest route
 * is calculated over the 8^4 state space. It is therefore a coupled-system
 * deduction puzzle rather than another timing dial.
 */
@OnlyIn(Dist.CLIENT)
public final class OrreryScreen extends BaseMinigameScreen {
    private static final int RINGS = 4;
    private static final int SLOTS = 8;
    private static final int[] BASE_RADII = {34, 54, 74, 94};

    private final int[] pos = new int[RINGS];
    private final int[] rune = new int[RINGS];
    private final int[] coupling = {1, 1, 1, 1};
    private final int rounds;
    private final boolean reversedTooth;
    private final boolean twinMesh;
    private final boolean phantomIndex;

    private int reversedLink = -1;
    private int boundRing = -1;
    private int selectedRing;
    private int moves;
    private int ideal;
    private int moveLimit;
    private int totalMoves;
    private int totalIdeal;
    private float flash;

    public OrreryScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.orrery"), msg);
        boolean[] variants = pickVariants(2, 0x0A771E);
        reversedTooth = variants[0];
        twinMesh = expertVariant(0x0A771F, 3, 76, 0.34f);
        phantomIndex = variants[1] && familiarity >= 2;
        if (reversedTooth) modifierLabels.add("reversed tooth");
        if (twinMesh) modifierLabels.add("twin mesh");
        if (phantomIndex) modifierLabels.add("false meridian");
        if (makerBindsLogic()) modifierLabels.add("maker · sealed axle");
        if (cardBindsOne()) modifierLabels.add("card · sealed axle");
        rounds = Mth.clamp(2 + (value >= 64 || difficulty > 1.22f ? 1 : 0), 2, 3);
        buildRound();
    }

    @Override protected Theme createTheme() {
        return Theme.of(0xFF4E5963, 0xFF0A0D12, 0xFF8DC9C5, 0xFFE6D6A8, 1, Theme.PATTERN_DITHER);
    }
    @Override protected int panelHeight() {
        if (height <= 0) return 266;
        return Math.min(266, Math.max(220, height - 8));
    }
    private boolean compactLayout() { return height > 0 && panelHeight() < 254; }
    private int ringCenterY() { return compactLayout() ? contentCenterY() + 5 : contentCenterY(); }
    private int ringRadius(int index) {
        if (!compactLayout()) return BASE_RADII[index];
        int topBand = contentTop() + 35;
        int bottomBand = contentBottom() - 17;
        int max = Math.max(46, Math.min(76, (bottomBand - topBand) / 2));
        return Math.max(16, Math.round(BASE_RADII[index] * (max / 94f)));
    }
    @Override protected float timeBudget() { return super.timeBudget() * 1.10f; }

    private void buildRound() {
        Random r = new Random(seed ^ (progress * 0x9E3779B97F4A7C15L) ^ 0x4F52524552594CL);
        Arrays.fill(pos, 0);
        for (int i = 0; i < RINGS; i++) rune[i] = r.nextInt(Sigils.TILE.length);
        Arrays.fill(coupling, 1);
        reversedLink = reversedTooth ? r.nextInt(RINGS) : -1;
        if (reversedLink >= 0) coupling[reversedLink] = -1;
        boundRing = (makerBindsLogic() || cardBindsOne()) ? r.nextInt(RINGS) : -1;
        selectedRing = boundRing == 0 ? 1 : 0;

        int scramble = Mth.clamp(4 + progress + Math.round(Math.max(0f, difficulty - 1f) * 2f), 4, 8);
        int lastRing = -1, lastDir = 0;
        for (int attempt = 0; attempt < 24; attempt++) {
            Arrays.fill(pos, 0);
            for (int s = 0; s < scramble; s++) {
                int ring;
                do ring = r.nextInt(RINGS); while (ring == boundRing);
                int dir = r.nextBoolean() ? 1 : -1;
                if (ring == lastRing && dir == -lastDir) dir = lastDir;
                applyMove(pos, ring, dir);
                lastRing = ring; lastDir = dir;
            }
            if (cardRevealsOne()) {
                // Apply one legal move from the shortest route, never teleport a
                // single ring into a state that the coupled mechanism cannot reach.
                int[] hint = firstShortestMove();
                if (hint != null) applyMove(pos, hint[0], hint[1]);
            }
            ideal = shortestDistance(pos);
            if (ideal > 0 && ideal <= 12) break;
        }
        if (ideal <= 0) {
            int ring = boundRing == 0 ? 1 : 0;
            applyMove(pos, ring, 1);
            ideal = Math.max(1, shortestDistance(pos));
        }
        moves = 0;
        moveLimit = ideal + (twinMesh ? 2 : 3) + (familiarity <= 1 ? 1 : 0);
        flash = 0f;
    }

    private int[] firstShortestMove() {
        int before = shortestDistance(pos);
        if (before <= 1) return null;
        for (int ring = 0; ring < RINGS; ring++) {
            if (ring == boundRing) continue;
            for (int dir : new int[]{-1, 1}) {
                int[] copy = pos.clone();
                applyMove(copy, ring, dir);
                if (shortestDistance(copy) == before - 1) return new int[]{ring, dir};
            }
        }
        return null;
    }

    private void applyMove(int[] state, int ring, int dir) {
        state[ring] = Math.floorMod(state[ring] + dir, SLOTS);
        int next = (ring + 1) % RINGS;
        state[next] = Math.floorMod(state[next] + dir * coupling[ring], SLOTS);
        if (twinMesh) {
            int prev = (ring + RINGS - 1) % RINGS;
            state[prev] = Math.floorMod(state[prev] - dir * coupling[prev], SLOTS);
        }
    }

    private int encode(int[] state) {
        int code = 0, mul = 1;
        for (int v : state) { code += v * mul; mul *= SLOTS; }
        return code;
    }

    private void decode(int code, int[] out) {
        for (int i = 0; i < RINGS; i++) { out[i] = code % SLOTS; code /= SLOTS; }
    }

    /** Exact minimum over all 4096 coupled states. */
    private int shortestDistance(int[] startState) {
        int start = encode(startState);
        if (start == 0) return 0;
        int[] dist = new int[4096];
        Arrays.fill(dist, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        dist[start] = 0; q.add(start);
        int[] tmp = new int[RINGS];
        while (!q.isEmpty()) {
            int code = q.removeFirst();
            int d = dist[code];
            decode(code, tmp);
            for (int ring = 0; ring < RINGS; ring++) {
                if (ring == boundRing) continue;
                for (int dir : new int[]{-1, 1}) {
                    int[] next = tmp.clone();
                    applyMove(next, ring, dir);
                    int nc = encode(next);
                    if (dist[nc] >= 0) continue;
                    if (nc == 0) return d + 1;
                    dist[nc] = d + 1;
                    q.addLast(nc);
                }
            }
        }
        return -1;
    }

    private boolean solved() {
        for (int v : pos) if (v != 0) return false;
        return true;
    }

    private void turn(int ring, int dir) {
        if (ring < 0 || ring >= RINGS) return;
        selectedRing = ring;
        if (ring == boundRing) {
            showBanner("That axle is sealed; move it through another gear", theme().dim, 720);
            Sfx.play(WardSounds.STONE_TURN, 0.22f, 0.62f);
            return;
        }
        applyMove(pos, ring, dir);
        moves++;
        Sfx.play(WardSounds.STONE_TURN, 0.34f, 0.76f + ring * 0.07f);
        flash = 0.18f;
        if (solved()) {
            totalMoves += moves;
            totalIdeal += Math.max(1, ideal);
            progress++;
            burst(centerX(), contentCenterY(), COL_GOOD);
            Sfx.mark(progress + 2);
            if (progress >= rounds) {
                float efficiency = Mth.clamp(totalIdeal / (float)Math.max(totalIdeal, totalMoves), 0.48f, 1f);
                succeed(efficiency);
            } else {
                showBanner("The meridian holds. Another sky turns behind it.", COL_GOOD, 760);
                buildRound();
            }
            return;
        }
        if (moves >= moveLimit) {
            if (loseLife("The gears spend the last tooth of your allowance")) {
                showBanner("The orrery rewinds to its witnessed position", COL_BAD, 760);
                buildRound();
            }
        }
    }

    private int ringAt(double mx, double my) {
        double dx = mx - centerX(), dy = my - ringCenterY();
        double dist = Math.sqrt(dx * dx + dy * dy);
        int best = -1; double err = 999;
        for (int i = 0; i < RINGS; i++) {
            double e = Math.abs(dist - ringRadius(i));
            if (e < err && e <= 9) { err = e; best = i; }
        }
        return best;
    }

    @Override protected void step(float dt) { if (flash > 0f) flash = Math.max(0f, flash - dt); }

    @Override protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        int ring = ringAt(mx, my);
        if (ring < 0) return false;
        turn(ring, mx < centerX() ? -1 : 1);
        return true;
    }

    @Override protected boolean onKey(int key) {
        if (key == 265 || key == 87) { selectedRing = Math.floorMod(selectedRing - 1, RINGS); return true; }
        if (key == 264 || key == 83) { selectedRing = Math.floorMod(selectedRing + 1, RINGS); return true; }
        if (key == 263 || key == 65) { turn(selectedRing, -1); return true; }
        if (key == 262 || key == 68) { turn(selectedRing, 1); return true; }
        return false;
    }

    @Override protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = centerX(), cy = ringCenterY();
        boolean compact = compactLayout();
        progressDots(cx, contentTop() + (compact ? 10 : 16), rounds, progress);
        if (!compact) {
            smallCentered(g, "LEFT HALF = COUNTERCLOCKWISE   ·   RIGHT HALF = CLOCKWISE", cx, contentTop() + 29, theme().dim, 0.62f);
            smallCentered(g, "MOVES " + moves + " / " + moveLimit + "   ·   SHORTEST WITNESS " + ideal, cx, contentBottom() - 18, theme().faint, 0.66f);
        } else {
            smallCentered(g, "A/D TURN · W/S RING", cx, contentTop() + 21, theme().dim, 0.56f);
            smallCentered(g, "MOVES " + moves + "/" + moveLimit + " · MIN " + ideal, cx, contentBottom() - 10, theme().faint, 0.55f);
        }

        p.discPixel(cx, cy, 13, Painter.withAlpha(theme().accentDark, 0x80));
        Sigils.headerEye(p, cx, cy, 1, theme().frameLite, theme().accent, 0);
        for (int i = RINGS - 1; i >= 0; i--) {
            int rad = ringRadius(i);
            int col = i == selectedRing ? theme().accentLite : i == boundRing ? 0xFF785D67 : theme().frame;
            p.ringThick(cx, cy, rad, i == selectedRing ? 2 : 1, Painter.withAlpha(col, i == selectedRing ? 0xD0 : 0x78));
            for (int s = 0; s < SLOTS; s++) {
                double a = -Math.PI / 2 + s * Math.PI * 2 / SLOTS;
                int x = cx + (int)Math.round(Math.cos(a) * rad);
                int y = cy + (int)Math.round(Math.sin(a) * rad);
                int mark = s == 0 ? theme().accent : Painter.withAlpha(theme().frameLite, 0x60);
                p.rect(x - 1, y - 1, x + 2, y + 2, mark);
            }
            double a = -Math.PI / 2 + pos[i] * Math.PI * 2 / SLOTS;
            int rx = cx + (int)Math.round(Math.cos(a) * rad);
            int ry = cy + (int)Math.round(Math.sin(a) * rad);
            Sigils.drawCentered(p, Sigils.TILE[rune[i]], rx, ry, 1,
                    flash > 0f ? theme().accentLite : 0xFFE4D6B4, Painter.withAlpha(theme().accent, 0x80), 0x44000000);
            if (phantomIndex && i == 2) {
                int fake = Math.floorMod(pos[i] + 3, SLOTS);
                double fa = -Math.PI / 2 + fake * Math.PI * 2 / SLOTS;
                int fx = cx + (int)Math.round(Math.cos(fa) * rad), fy = cy + (int)Math.round(Math.sin(fa) * rad);
                p.outline(fx - 4, fy - 4, 8, 8, 1, Painter.withAlpha(0xFF985D91, 0x60));
            }
            if (i == boundRing) smallCentered(g, compact ? "LOCK" : "SEALED", cx + rad + (compact ? 12 : 19), cy + i * (compact ? 6 : 8) - 12, 0xFF96707B, compact ? 0.46f : 0.52f);
        }
        // Gear-link arrows make the coupling readable rather than hidden trial-and-error.
        for (int i = 0; i < RINGS; i++) {
            int y = contentTop() + (compact ? 34 : 48) + i * (compact ? 10 : 13);
            String dir = coupling[i] > 0 ? "↻" : "↺";
            smallCentered(g, "R" + (i + 1) + " → R" + ((i + 1) % RINGS + 1) + " " + dir, left() + (compact ? 37 : 48), y,
                    i == reversedLink ? 0xFFC784A8 : theme().faint, compact ? 0.46f : 0.56f);
        }
    }

    @Override protected String hintText() {
        return "Align all four witness runes with the upper meridian; every turn drives its meshed ring too";
    }
}
