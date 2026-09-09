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
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Runebraid: a stone tablet wrapped in six runes and five knot-points.
 *
 * <p>Clicking a knot swaps the two adjacent runes. The board is generated from
 * a known answer by replaying a legal sequence of swaps, so it is always
 * solvable by undoing that sequence. It is a planning puzzle: no timing
 * windows, no hidden information, no disappearing target.</p>
 *
 * <p>The visual language is intentionally more fantastical than mechanical: a
 * field of dim sigils under the slab, glowing braided seams between plaques and
 * pixel-art rune stones that belong beside the rest of Wardbound's sealwork.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RunebraidScreen extends BaseMinigameScreen {

    private static final int SLOTS = 6;
    private static final int SEAMS = SLOTS - 1;
    private static final int TILE = 34;
    private static final int GAP = 10;

    private final boolean crossedThreads;
    private final boolean boundKnot;
    private final boolean falseChorus;
    private final boolean deadRune;
    private final boolean mirroredGoal;
    private final boolean coupledRunes;
    private final int rounds;

    private final int[] target = new int[SLOTS];
    private final int[] current = new int[SLOTS];
    private final int[] bgGlyph = new int[14];
    private final int[] bgX = new int[14];
    private final int[] bgY = new int[14];
    private final int[] bgScale = new int[14];

    private int blockedSeam = -1;
    private int deadIndex = -1;
    private int linkedRuneA = -1;
    private int linkedRuneB = -1;
    private int movesLeft;
    private int idealMoves;
    private int usedMoves;
    private int totalIdeal;
    private int totalUsed;
    private int lastSeam = -1;
    private float flash;
    private float pulse;

    public RunebraidScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.runebraid"), msg);

        boolean[] q = pickVariants(5, 1471);
        crossedThreads = q[0];
        boundKnot = q[1];
        falseChorus = q[2];
        deadRune = q[3];
        mirroredGoal = q[4];
        coupledRunes = expertVariant(1489, 3, 82, 0.38f)
                && !crossedThreads && !deadRune && !boundKnot && !makerBindsLogic() && !cardBindsOne();
        if (crossedThreads) modifierLabels.add("crossed threads");
        if (boundKnot) modifierLabels.add("bound knot");
        if (falseChorus) modifierLabels.add("false chorus");
        if (deadRune) modifierLabels.add("dead rune");
        if (mirroredGoal) modifierLabels.add("mirrored goal");
        if (coupledRunes) modifierLabels.add("coupled runes");
        if (makerBindsLogic()) modifierLabels.add("maker · bound knot");
        if (cardBindsOne()) modifierLabels.add("card · bound knot");

        rounds = Mth.clamp(2 + (value >= 56 || difficulty > 1.18f ? 1 : 0), 2, 3);
        buildRound();
    }

    @Override
    protected Theme createTheme() {
        return Theme.parallax();
    }

    @Override
    protected int panelHeight() {
        return 254;
    }

    @Override
    protected void init() {
        super.init();
        buildBackground();
    }

    @Override
    protected float timeBudget() {
        return super.timeBudget() * 1.08f;
    }

    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING;
    }

    private void buildBackground() {
        Random r = new Random(seed ^ 0x52554E4542524149L);
        int left = left() + 22;
        int top = contentTop() + 18;
        int w = panelWidth() - 44;
        int h = contentBottom() - contentTop() - 32;
        for (int i = 0; i < bgGlyph.length; i++) {
            bgGlyph[i] = r.nextInt(Sigils.TILE.length);
            bgX[i] = left + r.nextInt(Math.max(1, w));
            bgY[i] = top + r.nextInt(Math.max(1, h));
            bgScale[i] = 1 + r.nextInt(2);
        }
    }

    private void buildRound() {
        Random r = new Random(seed ^ (progress * 0x9E3779B97F4A7C15L) ^ 0x52424C);

        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < Sigils.TILE.length; i++) pool.add(i);
        Collections.shuffle(pool, r);
        for (int i = 0; i < SLOTS; i++) {
            target[i] = pool.get(i);
            current[i] = target[i];
        }
        if (mirroredGoal) {
            for (int i = 0; i < SLOTS / 2; i++) {
                int tmp = target[i];
                target[i] = target[SLOTS - 1 - i];
                target[SLOTS - 1 - i] = tmp;
            }
            for (int i = 0; i < SLOTS; i++) current[i] = target[i];
        }
        linkedRuneA = coupledRunes ? target[2] : -1;
        linkedRuneB = coupledRunes ? target[3] : -1;

        blockedSeam = -1;
        deadIndex = -1;
        if (boundKnot || makerBindsLogic() || cardBindsOne()) blockedSeam = r.nextInt(SEAMS);
        if (deadRune) {
            deadIndex = 1 + r.nextInt(SLOTS - 2);
        }

        List<Integer> legal = legalSeams(blockedSeam);
        if (legal.isEmpty()) {
            blockedSeam = -1;
            for (int s = 0; s < SEAMS; s++) legal.add(s);
        }

        int pressCount = Mth.clamp(3 + progress + Math.round((difficulty - 0.95f) * 1.6f), 3, 6);
        int prev = -1;
        idealMoves = 0;
        for (int i = 0; i < pressCount; i++) {
            int seam;
            int guard = 0;
            do {
                seam = legal.get(r.nextInt(legal.size()));
            } while ((seam == prev || !canApplySeam(current, seam)) && legal.size() > 1 && ++guard < 24);
            if (!canApplySeam(current, seam)) continue;
            applySeam(seam, false);
            prev = seam;
            idealMoves++;
        }
        if (matchesTarget()) {
            for (int seam : legal) {
                if (canApplySeam(current, seam)) { applySeam(seam, false); break; }
            }
        }

        int exact = minimalMovesToTarget(legal);
        if (exact > 0) idealMoves = exact;

        int spare = 2 + Math.min(2, familiarity / 8);
        movesLeft = idealMoves + spare;
        usedMoves = 0;
        flash = 0f;
        lastSeam = -1;
    }


    private List<Integer> legalSeams(int blocked) {
        List<Integer> legal = new ArrayList<>();
        for (int s = 0; s < SEAMS; s++) {
            if (s == blocked) continue;
            if (deadIndex >= 0 && (s == deadIndex - 1 || s == deadIndex)) continue;
            if (crossedThreads && s > mirrorSeam(s)) continue; // same operator twice
            legal.add(s);
        }
        return legal;
    }

    /**
     * The natural Bound Knot is now an active obstacle instead of a decorative
     * missing button. After every legal pull it crawls to another seam, but only
     * to a position from which the current braid still has a solution.
     */
    private void relocateBoundKnot() {
        if (!(boundKnot || makerBindsLogic() || cardBindsOne()) || matchesTarget()) return;
        List<Integer> candidates = new ArrayList<>();
        for (int s = 0; s < SEAMS; s++) {
            if (s == blockedSeam) continue;
            if (deadIndex >= 0 && (s == deadIndex - 1 || s == deadIndex)) continue;
            candidates.add(s);
        }
        Collections.shuffle(candidates, new Random(seed ^ 0xB0A7D0L ^ (long) usedMoves * 8191L));
        for (int candidate : candidates) {
            List<Integer> legal = legalSeams(candidate);
            if (legal.isEmpty()) continue;
            int exact = minimalMovesToTarget(legal);
            if (exact >= 0) {
                blockedSeam = candidate;
                // Moving the knot must not invalidate the old move allowance.
                movesLeft = Math.max(movesLeft, exact + 1);
                return;
            }
        }
    }


    private int targetIndexOf(int rune) {
        for (int i = 0; i < SLOTS; i++) if (target[i] == rune) return i;
        return 0;
    }

    private int normalizedCode() {
        int code = 0;
        for (int i = 0; i < SLOTS; i++) code |= (targetIndexOf(current[i]) & 7) << (i * 3);
        return code;
    }

    private int targetCode() {
        int code = 0;
        for (int i = 0; i < SLOTS; i++) code |= (i & 7) << (i * 3);
        return code;
    }

    private int swapCode(int code, int seam) {
        int sa = seam * 3, sb = (seam + 1) * 3;
        int a = (code >> sa) & 7, b = (code >> sb) & 7;
        code &= ~(7 << sa);
        code &= ~(7 << sb);
        code |= b << sa;
        code |= a << sb;
        return code;
    }

    private int[] codeToArray(int code) {
        int[] a = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) a[i] = (code >> (i * 3)) & 7;
        return a;
    }

    private int arrayToCode(int[] a) {
        int code = 0;
        for (int i = 0; i < SLOTS; i++) code |= (a[i] & 7) << (i * 3);
        return code;
    }

    private int posOf(int[] a, int rune) {
        for (int i = 0; i < a.length; i++) if (a[i] == rune) return i;
        return -1;
    }

    private boolean canApplyCoupled(int[] a, int seam, int runeA, int runeB) {
        if (!coupledRunes) return true;
        int pa = posOf(a, runeA), pb = posOf(a, runeB);
        if (pa < 0 || pb < 0 || Math.abs(pa - pb) != 1) return true;
        int lo = Math.min(pa, pb), hi = Math.max(pa, pb);
        return seam != lo; // the seam inside the bound pair cannot split it
    }

    private void applyCoupled(int[] a, int seam, int runeA, int runeB) {
        if (!coupledRunes) {
            int t = a[seam]; a[seam] = a[seam + 1]; a[seam + 1] = t;
            return;
        }
        int pa = posOf(a, runeA), pb = posOf(a, runeB);
        int lo = Math.min(pa, pb), hi = Math.max(pa, pb);
        if (seam == lo) return;
        if (seam == lo - 1) {
            int outsider = a[lo - 1];
            a[lo - 1] = a[lo];
            a[lo] = a[hi];
            a[hi] = outsider;
            return;
        }
        if (seam == hi && hi + 1 < SLOTS) {
            int outsider = a[hi + 1];
            a[hi + 1] = a[hi];
            a[hi] = a[lo];
            a[lo] = outsider;
            return;
        }
        int t = a[seam]; a[seam] = a[seam + 1]; a[seam + 1] = t;
    }

    private int applySeamCode(int code, int seam) {
        if (coupledRunes) {
            int[] a = codeToArray(code);
            // normalized identities 2 and 3 are the linked pair in the target.
            if (!canApplyCoupled(a, seam, 2, 3)) return code;
            applyCoupled(a, seam, 2, 3);
            return arrayToCode(a);
        }
        code = swapCode(code, seam);
        if (crossedThreads) {
            int echo = mirrorSeam(seam);
            if (echo != seam) code = swapCode(code, echo);
        }
        return code;
    }

    private int minimalMovesToTarget(List<Integer> legal) {
        int start = normalizedCode();
        int goal = targetCode();
        if (start == goal) return 0;
        Map<Integer,Integer> dist = new HashMap<>();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        dist.put(start, 0);
        q.add(start);
        while (!q.isEmpty()) {
            int state = q.removeFirst();
            int nd = dist.get(state) + 1;
            for (int seam : legal) {
                int next = applySeamCode(state, seam);
                if (next == state || dist.containsKey(next)) continue;
                if (next == goal) return nd;
                dist.put(next, nd);
                q.addLast(next);
            }
        }
        return -1;
    }

    private int mirrorSeam(int seam) {
        return SEAMS - 1 - seam;
    }

    private boolean canApplySeam(int[] row, int seam) {
        if (!coupledRunes) return true;
        return canApplyCoupled(row, seam, linkedRuneA, linkedRuneB);
    }

    private void doSwap(int seam) {
        int a = current[seam];
        current[seam] = current[seam + 1];
        current[seam + 1] = a;
    }

    private void applySeam(int seam, boolean feedback) {
        if (coupledRunes) {
            if (!canApplySeam(current, seam)) return;
            applyCoupled(current, seam, linkedRuneA, linkedRuneB);
        } else {
            doSwap(seam);
            if (crossedThreads) {
                int echo = mirrorSeam(seam);
                if (echo != seam) doSwap(echo);
            }
        }
        if (feedback) {
            lastSeam = seam;
            flash = 0.32f;
        }
    }

    private boolean matchesTarget() {
        for (int i = 0; i < SLOTS; i++) if (current[i] != target[i]) return false;
        return true;
    }

    private int rowWidth() {
        return SLOTS * TILE + (SLOTS - 1) * GAP;
    }

    private int rowX() {
        return centerX() - rowWidth() / 2;
    }

    private int currentRowY() {
        return contentCenterY() + 14;
    }

    private int targetRowY() {
        return contentTop() + 28;
    }

    private int seamX(int seam) {
        return rowX() + seam * (TILE + GAP) + TILE + GAP / 2;
    }

    private int seamAt(double mx, double my) {
        int y = currentRowY();
        if (my < y + 4 || my > y + TILE - 4) return -1;
        for (int s = 0; s < SEAMS; s++) {
            int x = seamX(s);
            if (mx >= x - 8 && mx <= x + 8) return s;
        }
        return -1;
    }

    @Override
    protected void step(float dt) {
        pulse += dt;
        if (flash > 0f) flash = Math.max(0f, flash - dt);
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        int seam = seamAt(mx, my);
        if (seam < 0) return false;
        if (seam == blockedSeam) {
            Sfx.play(WardSounds.BRAID_REBUKE, 0.42f, 0.95f);
            Sfx.markBad();
            showBanner("That knot was sealed shut", COL_BAD, 700);
            return true;
        }
        if (deadIndex >= 0 && (seam == deadIndex - 1 || seam == deadIndex)) {
            Sfx.play(WardSounds.BRAID_REBUKE, 0.36f, 0.86f);
            Sfx.markBad();
            showBanner("The ashen rune will not be moved", COL_BAD, 700);
            return true;
        }
        if (!canApplySeam(current, seam)) {
            Sfx.play(WardSounds.BRAID_REBUKE, 0.38f, 0.92f);
            showBanner("Those two runes are braided into one body", theme().dim, 720);
            return true;
        }

        applySeam(seam, true);
        usedMoves++;
        movesLeft--;
        if (!matchesTarget()) relocateBoundKnot();
        float pitch = 0.88f + seam * 0.05f;
        Sfx.play(WardSounds.BRAID_PULL, 0.44f, pitch);

        if (matchesTarget()) {
            totalIdeal += idealMoves;
            totalUsed += usedMoves;
            progress++;
            Sfx.mark(progress + 1);
            burst(centerX(), currentRowY() + TILE / 2, theme().good);
            if (progress >= rounds) {
                float efficiency = totalUsed <= 0 ? 1f : Mth.clamp((float) totalIdeal / totalUsed, 0.45f, 1f);
                Sfx.play(WardSounds.BRAID_SETTLE, 0.62f, 1.0f);
                succeed(efficiency);
            } else {
                Sfx.play(WardSounds.BRAID_SETTLE, 0.46f, 1.07f);
                showBanner("The braid settles into the next verse", COL_GOOD, 750);
                buildRound();
            }
            return true;
        }

        if (movesLeft <= 0) {
            totalUsed += usedMoves;
            if (loseLife("The thread knotted into itself")) buildRound();
            return true;
        }
        return true;
    }

    @Override
    protected boolean onKey(int key) {
        return false;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int bx = left() + 14;
        int by = contentTop() + 8;
        int bw = panelWidth() - 28;
        int bh = contentBottom() - contentTop() - 6;
        int hover = seamAt(mouseX, mouseY);

        p.roundRect(bx, by, bw, bh, Painter.darken(t.panel, 0.18f));
        p.roundOutline(bx, by, bw, bh, Painter.lighten(t.frameDark, 0.10f));
        p.roundRect(bx + 10, by + 12, bw - 20, bh - 24, 0xFF090B12);
        p.outline(bx + 10, by + 12, bw - 20, bh - 24, 1, Painter.withAlpha(t.frame, 0x88));
        p.dither(bx + 12, by + 14, bw - 24, bh - 28, Painter.withAlpha(t.accentDark, 0x12), 5, progress * 5);

        // Faint runic wallpaper.
        for (int i = 0; i < bgGlyph.length; i++) {
            int alpha = 0x13 + (i % 3) * 0x08;
            if (!WardConfig.accessibilityReduceMotion) alpha += Math.round((0.5f + 0.5f * Mth.sin(pulse * 0.7f + i)) * 6f);
            Sigils.drawCentered(p, Sigils.TILE[bgGlyph[i]], bgX[i], bgY[i], bgScale[i],
                    Painter.withAlpha(t.frameLite, alpha), Painter.withAlpha(t.accentDark, alpha / 2), 0);
        }

        smallCentered(g, mirroredGoal ? "desired braid · mirrored" : "desired braid", centerX(), targetRowY() - 17, t.dim, 0.78f);
        drawRuneRow(target, rowX(), targetRowY(), 2, false, false, -1, mirroredGoal);
        if (coupledRunes) drawCoupling(target, targetRowY(), Painter.withAlpha(t.accentLite, 0x88));

        if (falseChorus) {
            int[] decoy = target.clone();
            int a = 1, b = 4;
            int tmp = decoy[a]; decoy[a] = decoy[b]; decoy[b] = tmp;
            drawRuneRow(decoy, rowX() + 6, targetRowY() + 18, 1,
                    true, false, -1, false);
        }

        p.line(rowX(), currentRowY() - 10, rowX() + rowWidth(), currentRowY() - 10, 1,
                Painter.withAlpha(t.accentDark, 0x70));
        drawRuneRow(current, rowX(), currentRowY(), 2, false, true, hover, false);
        if (coupledRunes) drawCoupling(current, currentRowY(), 0xFFD8B778);

        progressDots(centerX(), currentRowY() + TILE + 24, rounds, progress);
        smallCentered(g, "moves " + movesLeft + "   ·   reorder the runes to match the braid",
                centerX(), currentRowY() + TILE + 37, movesLeft <= 2 ? COL_BAD : t.dim, 0.79f);
        if (coupledRunes) smallCentered(g, "the gold-linked pair travels as one body", centerX(), currentRowY() + TILE + 50, t.faint, 0.73f);
        else if (deadRune) smallCentered(g, "the ashen rune cannot be moved", centerX(), currentRowY() + TILE + 50, t.faint, 0.73f);
        else if (crossedThreads) smallCentered(g, "some pulls answer across the braid", centerX(), currentRowY() + TILE + 50, t.faint, 0.73f);
    }

    private void drawCoupling(int[] row, int y, int color) {
        int a = -1, b = -1;
        for (int i = 0; i < row.length; i++) {
            if (row[i] == linkedRuneA) a = i;
            if (row[i] == linkedRuneB) b = i;
        }
        if (a < 0 || b < 0) return;
        int lo = Math.min(a, b), hi = Math.max(a, b);
        int x1 = rowX() + lo * (TILE + GAP) + TILE / 2;
        int x2 = rowX() + hi * (TILE + GAP) + TILE / 2;
        int yy = y + TILE + 4;
        p.line(x1, yy, x2, yy, 2, color);
        p.disc(x1, yy, 2, color);
        p.disc(x2, yy, 2, color);
    }

    private void drawRuneRow(int[] row, int x, int y, int glyphScale,
                             boolean faint, boolean drawSeams, int hoverSeam, boolean mirrorDisplay) {
        Theme t = theme();
        int plaque = faint ? Painter.withAlpha(Painter.darken(t.well, 0.10f), 0x90) : t.well;
        int edge = faint ? Painter.withAlpha(t.frame, 0x90) : t.wellEdge;
        int body = faint ? Painter.withAlpha(t.accentLite, 0x74) : t.accentLite;
        int half = faint ? Painter.withAlpha(t.accent, 0x36) : Painter.withAlpha(t.accent, 0x84);
        int shadow = faint ? 0 : Painter.withAlpha(0xFF000000, 0x50);

        for (int i = 0; i < row.length; i++) {
            int px = x + i * (TILE + GAP);
            int py = y;
            p.roundRect(px, py, TILE, TILE, edge);
            p.roundRect(px + 1, py + 1, TILE - 2, TILE - 2, plaque);
            p.dither(px + 2, py + 2, TILE - 4, TILE - 4,
                    Painter.withAlpha(0xFFFFFFFF, faint ? 0x04 : 0x08), 3, i + progress * 7);
            String[] glyph = Sigils.TILE[row[i]];
            boolean flip = mirrorDisplay && (i % 2 == 0);
            Sigils.drawCenteredFlipped(p, glyph, px + TILE / 2, py + TILE / 2, glyphScale,
                    body, half, shadow, flip, false);
            if (deadIndex == i && !faint) {
                p.line(px + 6, py + 6, px + TILE - 6, py + TILE - 6, 1, Painter.withAlpha(COL_BAD, 0xD8));
                p.line(px + TILE - 6, py + 6, px + 6, py + TILE - 6, 1, Painter.withAlpha(COL_BAD, 0xD8));
            }
        }

        if (!drawSeams) return;
        for (int s = 0; s < SEAMS; s++) {
            int cx = seamX(s);
            int cy = y + TILE / 2;
            boolean blocked = s == blockedSeam;
            boolean hot = s == hoverSeam;
            int col = blocked ? Painter.darken(COL_BAD, 0.35f)
                    : (hot ? t.accent : t.frameLite);
            p.ringThick(cx, cy, hot ? 8 : 7, 2, col);
            p.disc(cx, cy, 2, blocked ? COL_BAD : Painter.withAlpha(t.accentDark, 0xC0));
            p.line(cx - 6, cy, cx + 6, cy, 1, col);
            if (blocked) {
                p.line(cx - 5, cy - 5, cx + 5, cy + 5, 1, COL_BAD);
                p.line(cx - 5, cy + 5, cx + 5, cy - 5, 1, COL_BAD);
            } else if (crossedThreads && s != mirrorSeam(s) && hot) {
                // No screen-wide translucent wire. Hovering a crossed knot now
                // simply marks the seam that will answer with it.
                int mx = seamX(mirrorSeam(s));
                p.ringThick(mx, cy, 9, 1, Painter.withAlpha(t.accent, 0x78));
                p.disc(mx, cy, 1, Painter.withAlpha(t.accentLite, 0xB0));
            }
            if (flash > 0f && (s == lastSeam || (crossedThreads && mirrorSeam(lastSeam) == s && s != lastSeam))) {
                p.ringThick(cx, cy, 11, 2, Painter.scaleAlpha(t.accentLite, flash));
            }
        }
    }

    @Override
    protected String hintText() {
        if (coupledRunes) return "The gold-linked runes cannot be separated; pull at either outer seam to move the pair together";
        if (crossedThreads && (boundKnot || makerBindsLogic() || cardBindsOne()))
            return "A pull echoes across the braid; the sealed knot crawls after every move";
        if (deadRune) return "The ashen rune is fixed in place; work around it";
        if (mirroredGoal) return "The upper braid is mirrored; match what it shows, not what you expect";
        if (crossedThreads) return "Some pulls echo across the braid";
        if (boundKnot || makerBindsLogic() || cardBindsOne()) return "The sealed knot crawls to a new seam after every pull";
        return "Click a knot to swap the two runes beside it";
    }
}
