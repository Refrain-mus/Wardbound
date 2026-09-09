package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * The Vessel. A bowl of something dark set into the lid, with shapes moving
 * under the surface.
 *
 * <p>Every other lock in this mod gives you its information for free and asks
 * what you do with it. This one charges for the information. The surface clouds
 * over on its own and only stills while you hold it still, and holding it costs
 * the one resource in the lock. So the loop is: look, and pay for looking; let
 * go, and track the thing you want in the dark from memory; look again when you
 * are no longer sure. Almost all of the play happens while you cannot see.
 *
 * <p>That is the reason this lock exists. "Knowing costs something" is a
 * sentence the mod has been decorating with for a long time without ever making
 * it true of anything, and a mechanic states it far better than a tooltip does.
 *
 * <p>Quirks a chest may carry:
 * <ul>
 *   <li>0 boiling - the shapes move faster and change direction on their own,
 *       so dead reckoning between looks stops working.</li>
 *   <li>1 many hands - more decoys under the surface.</li>
 *   <li>2 thin veil - the surface clouds faster and stilling it costs more, so
 *       you get fewer and shorter looks.</li>
 * </ul>
 *
 * <p>An ichorheart riding the lock leaves a wake behind the shape you want,
 * visible for a moment after each look. It does not tell you the answer; it
 * tells you which way the answer was going, which is what you actually lose in
 * the dark.
 */
@OnlyIn(Dist.CLIENT)
public class VesselScreen extends BaseMinigameScreen {

    private final boolean boiling;
    private final boolean manyHands;
    private final boolean thinVeil;

    private final int rounds;
    private final int shapes;
    private final float[] px;
    private final float[] py;
    private final float[] vx;
    private final float[] vy;
    private final int[] glyph;

    /** Trail of the marked shape, for the heart's tell. */
    private final float[] wakeX = new float[10];
    private final float[] wakeY = new float[10];
    private int wakeHead;
    private float wakeTimer;

    private final float drift;
    private final float clarityDrain;
    private final float clarityRegen;

    private int target;
    private float clarity = 1f;
    private boolean looking;
    /**
     * Seconds after a look during which you cannot commit.
     *
     * <p>Without it the honest way to play was still to look, release, and click
     * in the same motion before anything had moved: the surface was closed for a
     * tenth of a second and the shape was effectively still under your cursor.
     * The delay is what forces you to actually track the thing.
     */
    private float settle;
    private boolean scattered;

    /** Seconds left of the free look at the start of a round. */
    private float reveal;
    private float surface;

    private float flashTimer;
    private int flashColor = COL_GOOD;
    private int flashShape = -1;

    private int correct;
    private int wrong;

    private final Random rng;

    public VesselScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.vessel"), msg);

        boolean[] v = pickVariants(3, 88);
        boiling = v[0];
        manyHands = v[1];
        thinVeil = v[2];
        if (boiling) modifierLabels.add("boiling");
        if (manyHands) modifierLabels.add("many hands");
        if (thinVeil) modifierLabels.add("thin veil");

        this.rounds = wobble(2 + Math.round(value / 45f * difficulty), 1, 89, 2, 4);
        int base = 4 + Math.round((value / 100f) * 2f * difficulty) + (manyHands ? 2 : 0);
        this.shapes = Mth.clamp(base, 4, 8);

        this.drift = (0.155f + value * 0.00070f) * difficulty * (boiling ? 2.1f : 1f);
        // A full look lasts about five seconds on an ordinary vessel, and you
        // need three or four of them, so the budget is the lock.
        this.clarityDrain = (1f / 5.0f) * (thinVeil ? 1.7f : 1f) * Mth.clamp(difficulty, 0.7f, 1.8f);
        this.clarityRegen = clarityDrain * 0.42f;

        this.rng = new Random(seed ^ 0x0CEA112BL);
        px = new float[shapes];
        py = new float[shapes];
        vx = new float[shapes];
        vy = new float[shapes];
        glyph = new int[shapes];
        for (int i = 0; i < shapes; i++) glyph[i] = rng.nextInt(Sigils.TILE.length);

        beginRound();
    }

    /**
     * Looking is already metered, so the clock only stops you stalling.
     */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.48f;
    }

    /**
     * The whole lock is already about not being able to see. Layering a shroud
     * or a failing lamp on top of a surface that clouds by itself is noise, not
     * difficulty.
     */
    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING;
    }

    @Override
    protected Theme createTheme() {
        return Theme.vessel();
    }

    @Override
    protected int panelHeight() {
        return 252;
    }

    // ------------------------------------------------------------------ round setup

    private void beginRound() {
        // Scatter inside the unit circle, spaced so two shapes never start
        // overlapping: telling them apart is the game and a pile is not a puzzle.
        for (int i = 0; i < shapes; i++) {
            for (int attempt = 0; attempt < 60; attempt++) {
                float a = rng.nextFloat() * (float) Math.PI * 2f;
                float r = (float) Math.sqrt(rng.nextFloat()) * 0.78f;
                px[i] = Mth.cos(a) * r;
                py[i] = Mth.sin(a) * r;
                boolean ok = true;
                for (int j = 0; j < i; j++) {
                    float dx = px[i] - px[j], dy = py[i] - py[j];
                    if (dx * dx + dy * dy < 0.10f) {
                        ok = false;
                        break;
                    }
                }
                if (ok) break;
            }
            float a = rng.nextFloat() * (float) Math.PI * 2f;
            float pace = 0.55f + rng.nextFloat() * 0.95f;
            vx[i] = Mth.cos(a) * drift * pace;
            vy[i] = Mth.sin(a) * drift * pace;
        }
        target = rng.nextInt(shapes);
        reveal = 1.35f;
        wakeHead = 0;
        for (int i = 0; i < wakeX.length; i++) {
            wakeX[i] = px[target];
            wakeY[i] = py[target];
        }
    }

    // ------------------------------------------------------------------ simulation

    /** True while the shapes can actually be made out. */
    private boolean visible() {
        return reveal > 0f || looking;
    }

    @Override
    protected void step(float dt) {
        if (flashTimer > 0f) flashTimer -= dt;
        surface += dt;
        if (reveal > 0f) {
            reveal -= dt;
            // The opening look has to close the same way a paid one does.
            // Without this the round began with the shapes visible, the reveal
            // simply expired, and you could commit on the instant it did.
            if (reveal <= 0f) {
                settle = WardConfig.vesselSettle;
                scattered = false;
            }
        }

        // Only the free look at the top of the round is free.
        if (settle > 0f) {
            settle -= dt;
            // The shapes are kicked the moment the surface closes, so the
            // position you memorised is already wrong by the time you may act
            // on it. The settle delay alone only made you wait; this makes the
            // waiting cost you something.
            if (settle > 0f && !scattered) {
                scattered = true;
                for (int i = 0; i < shapes; i++) {
                    float a = rng.nextFloat() * (float) Math.PI * 2f;
                    float kick = drift * (1.6f + rng.nextFloat() * 1.4f);
                    vx[i] = Mth.cos(a) * kick;
                    vy[i] = Mth.sin(a) * kick;
                }
            }
        } else {
            scattered = false;
        }

        if (looking && reveal <= 0f) {
            clarity -= clarityDrain * dt;
            if (clarity <= 0f) {
                clarity = 1f;
                looking = false;
                Sfx.play(WardSounds.OVERSET, 0.55f, 0.72f);
                loseLife("The surface boiled over");
                beginRound();
                return;
            }
        } else if (!looking) {
            clarity = Math.min(1f, clarity + clarityRegen * dt);
        }

        for (int i = 0; i < shapes; i++) {
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;

            // Reflect off the wall of the bowl.
            float d2 = px[i] * px[i] + py[i] * py[i];
            if (d2 > 0.86f * 0.86f) {
                float d = (float) Math.sqrt(Math.max(1e-5f, d2));
                float nx = px[i] / d, ny = py[i] / d;
                float dot = vx[i] * nx + vy[i] * ny;
                vx[i] -= 2f * dot * nx;
                vy[i] -= 2f * dot * ny;
                px[i] = nx * 0.855f;
                py[i] = ny * 0.855f;
            }
            if (boiling && rng.nextFloat() < dt * 0.9f) {
                // A shove from underneath. This is what breaks dead reckoning:
                // a shape you were tracking is not where you extrapolated it.
                float a = rng.nextFloat() * (float) Math.PI * 2f;
                vx[i] = Mth.cos(a) * drift * (0.6f + rng.nextFloat() * 0.9f);
                vy[i] = Mth.sin(a) * drift * (0.6f + rng.nextFloat() * 0.9f);
            }
        }

        if (heartHint()) {
            wakeTimer -= dt;
            if (wakeTimer <= 0f) {
                wakeTimer = 0.10f;
                wakeHead = (wakeHead + 1) % wakeX.length;
                wakeX[wakeHead] = px[target];
                wakeY[wakeHead] = py[target];
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int cx0 = bowlCX(), cy0 = bowlCY(), r0 = bowlR();
        if (button == 1) {
            // Right button stills the surface. Kept separate from the left so
            // that looking and committing can never be the same gesture.
            double ddx = mx - cx0, ddy = my - cy0;
            if (ddx * ddx + ddy * ddy <= (double) r0 * r0) {
                if (!looking) Sfx.play(WardSounds.SURFACE_STILL, 0.55f, 1.0f);
                looking = true;
                return true;
            }
            return false;
        }
        if (button != 0) return false;

        int cx = bowlCX(), cy = bowlCY(), r = bowlR();
        double dx = mx - cx, dy = my - cy;
        boolean inBowl = dx * dx + dy * dy <= (double) r * r;

        // You cannot reach through a still surface.
        //
        // This was the hole in the lock: hold to look, see the shape you want,
        // click it. Two seconds and almost no clarity spent, and every word
        // about tracking things in the dark was decoration. Committing now has
        // to happen blind - you point at where you believe the shape has got to,
        // and it has been moving the whole time you were deciding.
        if (looking || reveal > 0f) {
            if (inBowl) {
                showBanner("Not while you are holding it still", theme().dim, 700);
                return true;
            }
            return false;
        }
        if (settle > 0f) {
            showBanner(String.format("The water is still moving \u2014 %.1fs", settle),
                    theme().dim, 500);
            return true;
        }

        if (inBowl) {
            int hit = shapeAt(mx, my);
            if (hit >= 0) {
                guess(hit);
            } else {
                // A guess at empty water still costs: pointing at nothing is a
                // wrong answer, not a free probe of where the shapes are not.
                wrong++;
                flashTimer = 0.4f;
                flashColor = COL_BAD;
                Sfx.markBad();
                loseLife("Nothing was there");
                beginRound();
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRelease(double mx, double my, int button) {
        if (button == 1) {
            if (looking) {
                settle = WardConfig.vesselSettle;
                Sfx.play(WardSounds.SURFACE_CLOSE, 0.60f, 1.0f);
            }
            looking = false;
            return true;
        }
        return false;
    }

    /** Right-drag, or the action key, is the only way to still the surface. */
    @Override
    protected boolean onKey(int key) {
        if (isActionKey(key)) {
            if (!looking) Sfx.play(WardSounds.SURFACE_STILL, 0.55f, 1.0f);
            looking = true;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyRelease(int key) {
        if (isActionKey(key)) {
            if (looking) {
                settle = WardConfig.vesselSettle;
                Sfx.play(WardSounds.SURFACE_CLOSE, 0.60f, 1.0f);
            }
            looking = false;
            return true;
        }
        return false;
    }

    private int shapeAt(double mx, double my) {
        int best = -1;
        double bestD = 13 * 13;
        for (int i = 0; i < shapes; i++) {
            double dx = mx - shapeX(i), dy = my - shapeY(i);
            double d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private void guess(int i) {
        if (i == target) {
            correct++;
            progress++;
            flashShape = i;
            flashColor = COL_GOOD;
            flashTimer = 0.45f;
            Sfx.mark(progress);
            burst(shapeX(i), shapeY(i), COL_GOOD);
            if (progress >= rounds) {
                int total = correct + wrong;
                succeed(total == 0 ? 1f : (float) correct / total);
            } else {
                clarity = Math.min(1f, clarity + 0.25f);
                beginRound();
            }
        } else {
            wrong++;
            flashShape = i;
            flashColor = COL_BAD;
            flashTimer = 0.5f;
            Sfx.markBad();
            loseLife("That was not the one");
            beginRound();
        }
    }

    // ------------------------------------------------------------------ layout

    private int bowlCX() {
        return centerX();
    }

    private int bowlCY() {
        return (contentTop() + contentBottom()) / 2 - 8;
    }

    private int bowlR() {
        return Math.min(84, (contentBottom() - contentTop()) / 2 - 16);
    }

    private int shapeX(int i) {
        return bowlCX() + Math.round(px[i] * bowlR());
    }

    private int shapeY(int i) {
        return bowlCY() + Math.round(py[i] * bowlR());
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int cx = bowlCX(), cy = bowlCY(), r = bowlR();

        // ---- the vessel
        p.disc(cx, cy + 3, r + 10, Painter.withAlpha(0x000000, 0x66));
        p.ring(cx, cy, r + 9, r + 3, t.frameDark);
        p.ring(cx, cy, r + 8, r + 5, t.frame);
        p.disc(cx, cy, r + 2, Painter.darken(t.panel, 0.35f) | 0xFF000000);
        p.disc(cx, cy, r, 0xFF0A1412);

        // ---- what is in it
        boolean clear = visible();
        drawLiquid(cx, cy, r, clear);

        if (heartHint() && clear) drawWake(cx, cy, r);

        for (int i = 0; i < shapes; i++) {
            drawShape(i, clear);
        }

        // The meniscus, over everything, so the shapes read as being under it.
        p.ring(cx, cy, r, r - 2, Painter.withAlpha(t.accent, clear ? 0x44 : 0x22));

        drawClarity(g, cx - r, cy + r + 14, r * 2);
        progressDots(cx, cy + r + 34, rounds, progress);
    }

    /** The liquid itself: slow bands that thicken into an opaque murk when unheld. */
    private void drawLiquid(int cx, int cy, int r, boolean clear) {
        Theme t = theme();
        for (int i = 0; i < 5; i++) {
            float ph = surface * (0.30f + i * 0.11f) + i * 1.7f;
            int oy = cy + Math.round(Mth.sin(ph) * r * 0.42f);
            int band = Painter.withAlpha(t.accent, clear ? 0x10 : 0x1A);
            p.rect(cx - r + 3, oy, cx + r - 3, oy + 2, band);
        }
        if (!clear) {
            // The murk. Heavy enough that the shapes really are gone, because a
            // veil you can squint through makes the whole resource pointless.
            p.disc(cx, cy, r - 1, Painter.withAlpha(0x060E0D, 0xE8));
            for (int i = 0; i < 3; i++) {
                float ph = surface * (0.5f + i * 0.2f) + i * 2.3f;
                int bx = cx + Math.round(Mth.sin(ph) * r * 0.4f);
                int by = cy + Math.round(Mth.cos(ph * 0.8f) * r * 0.4f);
                p.disc(bx, by, 14 - i * 3, Painter.withAlpha(t.accent, 0x0C));
            }
        }
    }

    private void drawWake(int cx, int cy, int r) {
        for (int k = 1; k < wakeX.length; k++) {
            int idx = (wakeHead + k) % wakeX.length;
            int alpha = 0x10 + k * 0x0C;
            p.disc(cx + Math.round(wakeX[idx] * r), cy + Math.round(wakeY[idx] * r),
                    2, Painter.withAlpha(0xB165C4, alpha));
        }
    }

    private void drawShape(int i, boolean clear) {
        Theme t = theme();
        int x = shapeX(i), y = shapeY(i);
        boolean marked = i == target && reveal > 0f;
        boolean flashing = i == flashShape && flashTimer > 0f;

        if (!clear && !flashing) return;

        int col = marked ? COL_GOOD : (flashing ? flashColor : t.accentLite);
        int alpha = clear ? 0xFF : Math.round(flashTimer * 0x99);

        p.disc(x, y, 10, Painter.withAlpha(0x000000, clear ? 0x55 : 0x33));
        Sigils.drawCentered(p, Sigils.TILE[glyph[i]], x, y, 1,
                Painter.withAlpha(col, alpha),
                Painter.withAlpha(Painter.darken(col, 0.4f), alpha), 0);

        if (marked) {
            int glow = Painter.withAlpha(COL_GOOD, 0x70 + Math.round(breath(1.3f) * 0x60));
            p.ring(x, y, 12, 11, glow);
        }
    }

    private void drawClarity(GuiGraphics g, int x, int y, int w) {
        Theme t = theme();
        p.rect(x, y, x + w, y + 6, 0xFF07100F);
        int fill = Math.round(Mth.clamp(clarity, 0f, 1f) * (w - 2));
        int col = clarity < 0.25f ? COL_BAD : t.accent;
        p.rect(x + 1, y + 1, x + 1 + fill, y + 5, col);
        if (looking) {
            p.rect(x + 1 + fill - 2, y, x + 1 + fill + 1, y + 6,
                    Painter.withAlpha(t.accentLite, 0xCC));
        }
        smallCentered(g, looking ? "the surface is still"
                        : (settle > 0f ? String.format("settling \u2014 %.1fs", settle)
                        : "hold to still the surface"),
                x + w / 2, y + 8,
                looking ? t.accent : (settle > 0f ? COL_BAD : t.faint), 0.75f);
    }

    @Override
    protected String hintText() {
        if (reveal > 0f) return "Mark the shape \u2014 it is about to go under";
        if (looking) return "Let go before you commit \u2014 it will keep moving";
        return "Hold right button or SPACE to look \u00b7 left click commits, blind";
    }
}
