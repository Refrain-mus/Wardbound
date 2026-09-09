package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * A scrying plate. One sigil is cut into the lid and four are cut into the plate below it, but
 * three of them are held the wrong way round. Pick the one that faces the same way as the lid,
 * before the light on the plate runs out.
 *
 * <p>Quirks:
 * <ul>
 *   <li>0 clouded plate - the plate fogs over between glances, so a candidate
 *       you have already read stops being readable while you check another</li>
 *   <li>1 short light - much less time per plate</li>
 *   <li>2 restless plate - the candidates swap places twice, later and later</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class MirrorScreen extends BaseMinigameScreen {

    private static final int CARDS = 4;
    private static final int CARD_W = 54;
    private static final int CARD_H = 58;
    private static final int GAP = 8;

    private final boolean clouded;
    private final boolean shortLight;
    private final boolean restless;

    private final int rounds;
    private final float lightTime;
    private final Random rng;

    private int targetGlyph;
    private boolean targetFlipX;
    private boolean targetFlipY;
    private final boolean[] cardFlipX = new boolean[CARDS];
    private final boolean[] cardFlipY = new boolean[CARDS];
    private final int[] cardGlyph = new int[CARDS];
    private int answer;

    private float timer;
    /** How many times the plate has rearranged itself this round. */
    private int swaps;
    private float fogPhase;
    private int pressed = -1;
    private float pressTimer;
    private int pressColor = COL_GOOD;

    private int right;
    private int missed;

    public MirrorScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.mirror"), msg);

        boolean[] v = pickVariants(3, 11);
        clouded = v[0];
        shortLight = v[1];
        restless = v[2];
        if (clouded) modifierLabels.add("clouded plate");
        if (shortLight) modifierLabels.add("short light");
        if (restless) modifierLabels.add("restless plate");

        this.rounds = wobble(3 + Math.round(value / 26f * difficulty), 1, 27, 3, 7);
        this.rng = new Random(seed ^ 0x111202L);
        this.lightTime = Mth.clamp(3.4f / difficulty * (shortLight ? 0.55f : 1f), 1.2f, 4.5f);
        for (int i = 0; i < progress; i++) rng.nextInt(4);
        deal();
    }

    /** See {@link #timeBudget()}: this lock already runs its own clock. */
    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.HURRIED;
    }

    /**
     * No ward clock at all.
     *
     * <p>Every round here is already timed by the light going out, and a second
     * bar counting down the whole lock on top of that is two clocks measuring
     * the same pressure. Returning zero switches the ward's own timer off.
     */
    @Override
    protected float timeBudget() {
        return 0f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.vellum();
    }

    @Override
    protected int panelHeight() {
        return 236;
    }

    private void deal() {
        // Only glyphs whose four mirrorings all look different can be used: with a symmetric sigil
        // two cards would be equally right and the plate would be unsolvable.
        int guard = 0;
        do {
            targetGlyph = rng.nextInt(Sigils.TILE.length);
        } while (!Sigils.fullyAsymmetric(Sigils.TILE[targetGlyph]) && ++guard < 60);

        // the four cards are the four orientations, shuffled, so exactly one can match the lid
        int[] order = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        int targetOrientation = rng.nextInt(4);
        targetFlipX = (targetOrientation & 1) != 0;
        targetFlipY = (targetOrientation & 2) != 0;

        for (int i = 0; i < CARDS; i++) {
            cardGlyph[i] = targetGlyph;
            cardFlipX[i] = (order[i] & 1) != 0;
            cardFlipY[i] = (order[i] & 2) != 0;
            if (order[i] == targetOrientation) answer = i;
        }
        timer = lightTime;
        swaps = 0;
        pressed = -1;
    }

    private int cardX(int i) {
        int total = CARDS * CARD_W + (CARDS - 1) * GAP;
        return centerX() - total / 2 + i * (CARD_W + GAP);
    }

    private int cardY() {
        return contentBottom() - CARD_H - 18;
    }

    @Override
    protected void step(float dt) {
        fogPhase += dt;
        timer -= dt;
        if (pressTimer > 0f) pressTimer -= dt;

        if (restless && swaps < 2 && timer < lightTime * (swaps == 0 ? 0.62f : 0.28f)) {
            swaps++;
            int a = rng.nextInt(CARDS), b = rng.nextInt(CARDS);
            if (a != b) {
                boolean fx = cardFlipX[a], fy = cardFlipY[a];
                int gl = cardGlyph[a];
                cardFlipX[a] = cardFlipX[b];
                cardFlipY[a] = cardFlipY[b];
                cardGlyph[a] = cardGlyph[b];
                cardFlipX[b] = fx;
                cardFlipY[b] = fy;
                cardGlyph[b] = gl;
                if (answer == a) answer = b;
                else if (answer == b) answer = a;
                Sfx.plugTurn(1.22f);
            }
        }

        if (timer <= -grace()) {
            missed++;
            if (loseLife("The light went out")) deal();
        }
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        int y = cardY();
        if (my < y || my > y + CARD_H) return false;
        for (int i = 0; i < CARDS; i++) {
            int x = cardX(i);
            if (mx < x || mx > x + CARD_W) continue;

            pressed = i;
            pressTimer = 0.28f;
            if (i == answer) {
                pressColor = COL_GOOD;
                right++;
                progress++;
                Sfx.mark(progress);
                burst(x + CARD_W / 2, y + CARD_H / 2, COL_GOOD);
                if (progress >= rounds) {
                    int total = right + missed;
                    succeed(total == 0 ? 1f : (float) right / total);
                } else {
                    deal();
                }
            } else {
                pressColor = COL_BAD;
                missed++;
                if (loseLife("Wrong hand of the sigil")) deal();
            }
            return true;
        }
        return false;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int cx = centerX();

        // the lid sigil, in its own recess
        int tw = 66, th = 62;
        int tx = cx - tw / 2, ty = contentTop() + 6;
        well(tx, ty, tw, th);
        p.dither(tx + 1, ty + 1, tw - 2, th - 2, Painter.withAlpha(t.accent, 0x10), 2, 0);
        Sigils.drawCenteredFlipped(p, Sigils.TILE[targetGlyph], tx + tw / 2, ty + th / 2, 5,
                t.accentLite, Painter.withAlpha(t.accent, 0xCC),
                Painter.withAlpha(0x000000, 0x77), targetFlipX, targetFlipY);
        rivet(tx + 5, ty + 5);
        rivet(tx + tw - 5, ty + 5);
        rivet(tx + 5, ty + th - 5);
        rivet(tx + tw - 5, ty + th - 5);

        // the light running out
        int bw = 150, bx = cx - bw / 2, by = ty + th + 8;
        float lit = Mth.clamp(timer / lightTime, 0f, 1f);
        p.roundRect(bx - 1, by - 1, bw + 2, 7, t.wellEdge);
        p.roundRect(bx, by, bw, 5, t.well);
        int col = lit < 0.3f ? COL_BAD : t.accent;
        p.rect(bx, by + 1, bx + Math.round(bw * lit), by + 4, col);

        // the plate
        int y = cardY();
        for (int i = 0; i < CARDS; i++) {
            int x = cardX(i);
            boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
            boolean hit = i == pressed && pressTimer > 0f;

            p.roundRect(x - 2, y - 2, CARD_W + 4, CARD_H + 4, t.edge);
            p.roundRect(x - 1, y - 1, CARD_W + 2, CARD_H + 2,
                    hit ? pressColor : (hover ? t.frame : t.wellEdge));
            p.roundRect(x, y, CARD_W, CARD_H, hover ? t.panelLite : t.well);
            p.rect(x + 1, y + 1, x + CARD_W - 1, y + 2, Painter.withAlpha(0xFFFFFF, 0x10));
            rivet(x + 5, y + 5);
            rivet(x + CARD_W - 5, y + CARD_H - 5);

            int body = hover ? t.text : t.dim;
            Sigils.drawCenteredFlipped(p, Sigils.TILE[cardGlyph[i]], x + CARD_W / 2, y + CARD_H / 2, 4,
                    body, Painter.withAlpha(t.faint, 0x99),
                    Painter.withAlpha(0x000000, 0x55), cardFlipX[i], cardFlipY[i]);

            // and the fog over it, on this plate's own cycle
            int haze = fog(i);
            if (haze > 0) {
                p.rect(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1,
                        Painter.withAlpha(0x0E1116, haze));
            }
        }

        progressDots(cx, y + CARD_H + 10, rounds, progress);
    }

    /**
     * The fog over a candidate.
     *
     * <p>Was a flat alpha reduction on every glyph, which is not a mechanic: a
     * slightly dimmer picture is the same picture. Now each plate clouds and
     * clears on its own slow cycle, and the cycles are out of phase, so the one
     * you have just finished reading is going under while you read the next.
     * The lock becomes about the order you look in.
     */
    private int fog(int i) {
        if (!clouded) return 0;
        float ph = fogPhase * (0.55f + i * 0.17f) + i * 1.9f;
        float f = (Mth.sin(ph) + 1f) * 0.5f;
        return Math.round(Mth.clamp(f * 1.35f - 0.15f, 0f, 1f) * 0xE0);
    }

    @Override
    protected String hintText() {
        return "Pick the sigil facing the same way as the lid";
    }
}
