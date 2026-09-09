package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * A star chart cut into the lid. A line is drawn between certain stars, held for a moment and then
 * taken away; you draw it back by clicking the stars in the same order.
 *
 * <p>Unlike the seal, the stars are scattered rather than gridded and the line you are copying is
 * a shape in space, not a list of tiles, which is a different kind of remembering.
 *
 * <p>Quirks:
 * <ul>
 *   <li>0 drifting sky - the sky always moves; this makes it move fast</li>
 *   <li>1 false lightning - a star outside the true path is struck by a jagged
 *       branch from one real path star. The branch flickers while the true path
 *       remains steady, so it is a meaningful decoy with a readable tell rather
 *       than a decorative extra light.</li>
 *   <li>2 read backwards - retrace the line from its end</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class ConstellationScreen extends BaseMinigameScreen {

    private final boolean drifting;
    private final boolean falseLightning;
    private final boolean backwards;

    private final int rounds;
    private final int starCount;
    private final float showTime;

    private final float[] sx;
    private final float[] sy;
    private final float[] vx;
    private final float[] vy;
    private final int[] glyph;

    private int[] path;
    private float showTimer;
    private boolean showing;
    private int inputIndex;

    /**
     * Meteors. Purely decorative in the sense that they are never a target, and
     * anything but decorative in the sense that one crossing the star you are
     * about to click at the moment you click it is exactly the kind of thing a
     * sky does to you. Different colour, far faster than anything else on
     * screen, and they will happily pass straight through the path.
     */
    private final float[] metX = new float[7];
    private final float[] metY = new float[7];
    private final float[] metVX = new float[7];
    private final float[] metVY = new float[7];
    private final float[] metLife = new float[7];
    private float metTimer = 1.5f;
    /** Meteors only fall on a sky that is already restless. */
    private boolean meteors;
    /** Meteors are not part of the puzzle, so they get their own unseeded source. */
    private final Random meteorRng = new Random();
    private int wrongStar = -1;
    private float wrongTimer;
    private float twinkle;

    private int correct;
    private int wrong;

    public ConstellationScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.constellation"), msg);

        boolean[] v = pickVariants(3, 10);
        drifting = v[0];
        falseLightning = v[1];
        backwards = v[2];
        if (drifting) modifierLabels.add("drifting sky \u00b7 meteors");
        meteors = drifting;
        if (falseLightning) modifierLabels.add("false lightning");
        if (backwards) modifierLabels.add("read backwards");

        this.rounds = wobble(2 + Math.round(value / 34f * difficulty), 1, 25, 2, 4);
        this.starCount = wobble(9 + Math.round(value / 20f), 2, 26, 7, 14);
        this.showTime = Mth.clamp(2.4f / difficulty, 1.1f, 3.2f);

        Random rng = new Random(seed ^ 0x57A25L);
        sx = new float[starCount];
        sy = new float[starCount];
        vx = new float[starCount];
        vy = new float[starCount];
        glyph = new int[starCount];

        // Poisson-ish scatter in normalised space, so stars never sit on top of one another
        for (int i = 0; i < starCount; i++) {
            float bx = 0, by = 0;
            for (int attempt = 0; attempt < 40; attempt++) {
                bx = 0.07f + rng.nextFloat() * 0.86f;
                by = 0.09f + rng.nextFloat() * 0.82f;
                boolean ok = true;
                for (int j = 0; j < i; j++) {
                    float dx = (bx - sx[j]) * 1.7f, dy = by - sy[j];
                    if (dx * dx + dy * dy < 0.035f) {
                        ok = false;
                        break;
                    }
                }
                if (ok) break;
            }
            sx[i] = bx;
            sy[i] = by;
            float a = rng.nextFloat() * (float) Math.PI * 2f;
            float pace = (0.45f + rng.nextFloat() * 1.30f) * (drifting ? 3.4f : 0.62f);
            vx[i] = (float) Math.cos(a) * 0.026f * pace;
            vy[i] = (float) Math.sin(a) * 0.021f * pace;
            glyph[i] = rng.nextInt(Sigils.TILE.length);
        }

        buildRound();
    }

    /** Same reason as the seal: the path is shown once and has to be visible. */
    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING;
    }

    /** Short. A ward you can outwait is not a ward. */
    @Override
    protected float timeBudget() {
        // The old 0.52 multiplier made later multi-round charts feel rushed rather
        // than difficult. Keep a real clock, but give the spatial-memory task room
        // to be read. This is ~38% more time than before.
        return super.timeBudget() * 0.72f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.nightSky();
    }

    @Override
    protected int panelHeight() {
        return 244;
    }

    private void buildRound() {
        Random rng = new Random(seed + progress * 6151L);
        // Three-star constellations should be the common case. Extra stars are
        // reserved for later rounds and genuinely harder/richer wards instead of
        // arriving almost immediately on a normal chart.
        int bonus = (difficulty > 1.22f || value >= 64) ? 1 : 0;
        int len = Math.min(starCount, 3 + Math.max(0, progress - 1) + bonus);
        path = new int[len];
        boolean[] used = new boolean[starCount];
        for (int i = 0; i < len; i++) {
            int pick;
            int guard = 0;
            do {
                pick = rng.nextInt(starCount);
            } while (used[pick] && ++guard < 60);
            used[pick] = true;
            path[i] = pick;
        }

        showTimer = showTime;
        showing = true;
        inputIndex = 0;
    }

    /**
     * Star struck by the false-lightning branch. It is never a real answer.
     * The choice is deterministic for a round, so saving/reopening the screen
     * cannot move the decoy.
     */
    private int falseStar() {
        if (!falseLightning || path.length == 0 || starCount <= path.length) return -1;
        long roundSalt = seed ^ (progress * 0x6A09E667F3BCC909L);
        for (int guard = 0; guard < 64; guard++) {
            int s = (int) Math.floorMod((roundSalt >> 7) + guard * 2654435761L, starCount);
            if (!contains(path, s)) return s;
        }
        return -1;
    }

    /** Which real path star the false bolt pretends to branch from. */
    private int falseAnchorStep() {
        if (path.length == 0) return 0;
        return (int) Math.floorMod((seed >> 15) + progress * 17L, path.length);
    }

    private int falseAnchorStar() {
        return path[falseAnchorStep()];
    }

    /**
     * The path in the order it must be clicked. Reading backwards changes the
     * answer order only. The printed numbers remain the original 1..N path
     * labels: a chart marked 1-2-3 is therefore answered 3-2-1.
     */
    private int clickStep(int i) {
        return backwards ? path[path.length - 1 - i] : path[i];
    }

    private int expected() {
        return clickStep(inputIndex);
    }

    /**
     * Printed label on a true path star. Labels describe the line's natural
     * order, never the current click order. This is the crucial distinction for
     * the read-backwards quirk.
     */
    private int pathOrderOf(int star) {
        for (int i = 0; i < path.length; i++) {
            if (path[i] == star) return i;
        }
        return -1;
    }

    /**
     * Draw the fake branch as unstable lightning. The true constellation line
     * is straight and steady; this one kinks, changes intensity and occasionally
     * drops a segment. That is the fair tell. The decoy also echoes the number
     * of its anchor, so watching only flashes/numbers is not enough.
     */
    private void drawFalseLightning(GuiGraphics g, Theme t) {
        if (!showing || !falseLightning) return;
        int fake = falseStar();
        if (fake < 0) return;
        int anchor = falseAnchorStar();
        int ax = starX(anchor), ay = starY(anchor);
        int bx = starX(fake), by = starY(fake);

        int segments = 6;
        int px = ax, py = ay;
        long salt = seed ^ (progress * 0x9E3779B97F4A7C15L) ^ ((long)(twinkle * 12f) * 131L);
        for (int i = 1; i <= segments; i++) {
            float q = i / (float) segments;
            int nx = Math.round(Mth.lerp(q, ax, bx));
            int ny = Math.round(Mth.lerp(q, ay, by));
            if (i < segments) {
                long h = salt + i * 0x632BE59BD9B4E019L;
                int jitterX = (int) Math.floorMod(h >> 11, 9) - 4;
                int jitterY = (int) Math.floorMod(h >> 23, 9) - 4;
                nx += jitterX;
                ny += jitterY;
            }
            // One segment intermittently vanishes; a real path never does.
            boolean dropout = !WardConfig.accessibilityReduceFlashing
                    && ((int)(twinkle * 9f) + i + progress) % 7 == 0;
            if (!dropout) {
                int glow = Painter.withAlpha(0xB79AE2, 0x52);
                int core = Painter.withAlpha(0xE7D8FF, 0xD8);
                p.line(px, py, nx, ny, 3, glow);
                p.line(px, py, nx, ny, 1, core);
            }
            px = nx; py = ny;
        }

        int fx = starX(fake), fy = starY(fake);
        int pulse = WardConfig.accessibilityReduceFlashing ? 0xFFD7C8F2
                : ((((int)(twinkle * 10f)) & 1) == 0 ? 0xFFE9DEFF : 0xFFBFA5E8);
        p.disc(fx, fy, 8, Painter.withAlpha(pulse, 0x35));
        p.rect(fx - 6, fy - 1, fx + 6, fy + 1, pulse);
        p.rect(fx - 1, fy - 6, fx + 1, fy + 6, pulse);
        Sigils.drawCentered(p, Sigils.TILE[glyph[fake]], fx, fy - 13, 1,
                Painter.withAlpha(pulse, 0xCC), 0, Painter.withAlpha(0x000000, 0x66));
        // Duplicate the anchor's natural path number: it looks plausible, but
        // the duplicate itself is another clue that lightning is lying.
        int echo = pathOrderOf(anchor) + 1;
        smallCentered(g, String.valueOf(echo), fx, fy + 7, pulse, 0.8f);
    }

    // ------------------------------------------------------------------ layout

    private int fieldX() {
        return left() + 20;
    }

    private int fieldY() {
        return contentTop() + 6;
    }

    private int fieldW() {
        return panelWidth() - 40;
    }

    private int fieldH() {
        return contentBottom() - contentTop() - 24;
    }

    private int starX(int i) {
        return fieldX() + Math.round(sx[i] * fieldW());
    }

    private int starY(int i) {
        return fieldY() + Math.round(sy[i] * fieldH());
    }

    @Override
    protected void step(float dt) {
        twinkle += dt;
        stepMeteors(dt);
        if (showing) {
            showTimer -= dt;
            if (showTimer <= 0f) showing = false;
        }
        if (wrongTimer > 0f) wrongTimer -= dt;
        else wrongStar = -1;

        // Always moving. A chart that holds perfectly still reads as a frozen
        // screen, and the whole appeal of this lock is that it is a sky.
        {
            for (int i = 0; i < starCount; i++) {
                sx[i] += vx[i] * dt;
                sy[i] += vy[i] * dt;
                if (sx[i] < 0.05f || sx[i] > 0.95f) {
                    vx[i] = -vx[i];
                    sx[i] = Mth.clamp(sx[i], 0.05f, 0.95f);
                }
                if (sy[i] < 0.06f || sy[i] > 0.94f) {
                    vy[i] = -vy[i];
                    sy[i] = Mth.clamp(sy[i], 0.06f, 0.94f);
                }
            }
        }
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (showing || button != 0) return false;

        int hit = -1;
        double best = 13 * 13;
        for (int i = 0; i < starCount; i++) {
            double dx = mx - starX(i), dy = my - starY(i);
            double d = dx * dx + dy * dy;
            if (d < best) {
                best = d;
                hit = i;
            }
        }
        if (hit < 0) return false;

        if (hit == expected()) {
            correct++;
            inputIndex++;
            Sfx.mark(inputIndex);
            burst(starX(hit), starY(hit), COL_GOOD);
            if (inputIndex >= path.length) {
                progress++;
                if (progress >= rounds) {
                    int total = correct + wrong;
                    succeed(total == 0 ? 1f : (float) correct / total);
                } else {
                    buildRound();
                }
            }
        } else {
            wrong++;
            wrongStar = hit;
            wrongTimer = 0.35f;
            if (loseLife("The line broke")) buildRound();
        }
        return true;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int fx = fieldX(), fy = fieldY(), fw = fieldW(), fh = fieldH();

        well(fx, fy, fw, fh);
        p.scanlines(fx + 1, fy + 1, fw - 2, fh - 2, Painter.withAlpha(0x000000, 0x1E));

        // dust
        Random dust = new Random(seed ^ 0xD0571L);
        for (int i = 0; i < 60; i++) {
            int dx = fx + 2 + dust.nextInt(fw - 4);
            int dy = fy + 2 + dust.nextInt(fh - 4);
            p.rect(dx, dy, dx + 1, dy + 1, Painter.withAlpha(t.frameLite, 0x22 + dust.nextInt(0x22)));
        }

        // the line being shown, or the part of it you have retraced
        int drawn = showing ? path.length : inputIndex;
        for (int i = 1; i < drawn; i++) {
            int a = showing ? path[i - 1] : order(i - 1);
            int b = showing ? path[i] : order(i);
            int col = showing
                    // The true path remains steady while it is shown.
                    ? Painter.withAlpha(t.accent, 0xBB)
                    : Painter.withAlpha(COL_GOOD, 0xBB);
            p.line(starX(a), starY(a), starX(b), starY(b), 3, Painter.withAlpha(col, 0x33));
            p.line(starX(a), starY(a), starX(b), starY(b), 1, col);
        }

        for (int i = 0; i < starCount; i++) {
            int x = starX(i), y = starY(i);
            boolean onPath = showing && contains(path, i);
            boolean done = !showing && retraced(i);
            boolean hover = !showing && Math.abs(mouseX - x) < 12 && Math.abs(mouseY - y) < 12;
            boolean bad = i == wrongStar;

            int col = bad ? COL_BAD
                    : done ? COL_GOOD
                    : onPath ? t.accentLite
                    : hover ? t.text
                    : Painter.withAlpha(t.frameLite, 0x99);

            int r = onPath || done || bad ? 4 : 3;
            if (onPath || done || bad) p.disc(x, y, r + 4, Painter.withAlpha(col, 0x26));
            // a four-point star rather than a dot, so the field reads as a sky
            p.rect(x - r - 2, y - 1, x + r + 2, y + 1, col);
            p.rect(x - 1, y - r - 2, x + 1, y + r + 2, col);
            p.rect(x - 1, y - 1, x + 1, y + 1, Painter.lighten(col, 0.6f));

            if (onPath) {
                int idx = pathOrderOf(i) + 1;
                Sigils.drawCentered(p, Sigils.TILE[glyph[i]], x, y - 13, 1,
                        Painter.withAlpha(t.accentLite, 0xAA), 0, Painter.withAlpha(0x000000, 0x66));
                smallCentered(g, String.valueOf(idx), x, y + 7, t.accentLite, 0.8f);
            }
        }

        drawFalseLightning(g, t);
        drawMeteors();

        progressDots(centerX(), contentBottom() - 4, rounds, progress);
    }

    /**
     * Meteors cross the field right to left, fast, occasionally in twos and
     * threes. They are never clickable and never part of the answer; they exist
     * to move through the thing you are aiming at.
     */
    private void stepMeteors(float dt) {
        if (!meteors) return;
        metTimer -= dt;
        if (metTimer <= 0f) {
            metTimer = 0.6f + meteorRng.nextFloat() * 1.5f;
            int burst = meteorRng.nextFloat() < 0.45f ? 2 + meteorRng.nextInt(3) : 1;
            for (int k = 0; k < burst; k++) {
                for (int i = 0; i < metLife.length; i++) {
                    if (metLife[i] > 0f) continue;
                    metX[i] = 1.15f + k * 0.14f;
                    metY[i] = 0.08f + meteorRng.nextFloat() * 0.84f;
                    metVX[i] = -(2.1f + meteorRng.nextFloat() * 1.5f);
                    metVY[i] = (meteorRng.nextFloat() - 0.5f) * 0.45f;
                    metLife[i] = 1f;
                    break;
                }
            }
        }
        for (int i = 0; i < metLife.length; i++) {
            if (metLife[i] <= 0f) continue;
            metX[i] += metVX[i] * dt;
            metY[i] += metVY[i] * dt;
            if (metX[i] < -0.25f) metLife[i] = 0f;
        }
    }

    private void drawMeteors() {
        if (!meteors) return;
        for (int i = 0; i < metLife.length; i++) {
            if (metLife[i] <= 0f) continue;
            int x = fieldX() + Math.round(metX[i] * fieldW());
            int y = fieldY() + Math.round(metY[i] * fieldH());
            // Clipped to the field. They enter from off the right edge, so
            // without this the head and its tail are painted beside the panel.
            int lo = fieldX(), hi = fieldX() + fieldW();
            if (x < lo - 12 || x > hi + 12) continue;
            // a short tail behind it, in a colour nothing else on the field uses
            for (int k = 0; k < 7; k++) {
                int tx = fieldX() + Math.round((metX[i] - metVX[i] * 0.012f * k) * fieldW());
                int ty = fieldY() + Math.round((metY[i] - metVY[i] * 0.012f * k) * fieldH());
                if (tx < lo || tx > hi) continue;
                p.disc(tx, ty, k < 3 ? 4 : 3, Painter.withAlpha(0xC8A8E8, 0xCC - k * 0x12));
            }
            if (x >= lo && x <= hi) {
                // Bigger than a star, so it reads as something passing rather
                // than as one of them twitching.
                p.disc(x, y, 7, Painter.withAlpha(0xC8A8E8, 0x44));
                p.disc(x, y, 5, Painter.withAlpha(0xE0CCF4, 0xCC));
                p.disc(x, y, 3, 0xFFF4ECFF);
            }
        }
    }

    private int order(int i) {
        return clickStep(i);
    }

    private boolean retraced(int star) {
        for (int i = 0; i < inputIndex; i++) if (order(i) == star) return true;
        return false;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    private static int indexIn(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return 0;
    }

    @Override
    protected String hintText() {
        if (showing) {
            if (backwards) {
                return "Watch 1 to " + path.length + " — then answer " + path.length + " to 1";
            }
            return falseLightning ? "Watch the steady line — lightning lies" : "Watch the line";
        }
        if (backwards) {
            int nextLabel = path.length - inputIndex;
            return "Read backwards: " + nextLabel + " next   " + inputIndex + " / " + path.length;
        }
        return "Click the stars in order   " + inputIndex + " / " + path.length;
    }
}
