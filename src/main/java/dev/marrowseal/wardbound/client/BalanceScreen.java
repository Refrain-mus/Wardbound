package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.Random;

/**
 * An unstable axis. The beam will not sit still; lean on it from either side and keep the bubble
 * inside the seat long enough for the bolt to seat itself.
 *
 * <p>Every other lock is a series of moments. This one is a stretch of time you have to survive,
 * which makes it the odd one out on purpose.
 *
 * <p>Quirks:
 * <ul>
 *   <li>0 heavy axis - the beam answers slowly and carries its momentum a long way</li>
 *   <li>1 wandering seat - the safe wedge slides along the beam</li>
 *   <li>2 shoves - something on the far side of the lid hits it, hard, now and then</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class BalanceScreen extends BaseMinigameScreen {

    private static final int KEY_A = 65, KEY_D = 68, KEY_LEFT = 263, KEY_RIGHT = 262;

    private final boolean heavy;
    private final boolean wandering;
    private final boolean shoves;

    private final float holdTime;
    private final float safeHalf;
    private final Random rng;

    private float tilt;
    private float vel;
    private float seat;
    private float seatDrift;
    private float charge;
    /** Countdown to the next shove. See {@link #step}. */
    private float kickTimer = 1.4f;
    private float noise;
    private float shoveTimer;
    private float flashTimer;
    private int flashColor = COL_GOOD;

    private int pushLeft;
    private int pushRight;
    private int slips;

    public BalanceScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.balance"), msg);

        boolean[] v = pickVariants(3, 9);
        heavy = v[0];
        wandering = v[1];
        shoves = v[2];
        if (heavy) modifierLabels.add("heavy axis");
        if (wandering) modifierLabels.add("wandering seat");
        if (shoves) modifierLabels.add("shoves");

        this.rng = new Random(seed ^ 0xBA1A11CEL);
        this.holdTime = Mth.clamp(2.6f + value * 0.022f * difficulty, 2.4f, 6.5f);
        // Was 0.30 wide with a 0.13 floor, which on an ordinary chest is nearly
        // a third of the beam: you could park the seat and stop paying
        // attention. Narrower, and with a much wider spread from the seed, so
        // two balance locks are not the same lock at different speeds.
        this.safeHalf = Mth.clamp(0.185f / difficulty * (0.70f + seedVariance(71) * 0.75f),
                0.075f, 0.26f);
        this.seat = (rng.nextFloat() - 0.5f) * 0.5f;
        this.seatDrift = wandering ? (rng.nextBoolean() ? 1f : -1f) * 0.11f : 0f;
        this.noise = rng.nextFloat() * 10f;
        this.shoveTimer = 1.8f + rng.nextFloat() * 2f;
        this.charge = Mth.clamp(progress / 100f, 0f, 0.9f) * holdTime;
    }

    /**
     * Holding steady is easy given forever.
     */
    @Override
    protected float timeBudget() {
        return super.timeBudget() * 0.30f;
    }

    @Override
    protected Theme createTheme() {
        return Theme.slate();
    }

    private float push() {
        float k = heavy ? 1.25f : 1.9f;
        return (pushRight - pushLeft) * k;
    }

    @Override
    protected void step(float dt) {
        noise += dt;

        // a slow wandering torque, so the beam is never simply falling one way
        float torque = (float) (Math.sin(noise * 0.9) * 0.55 + Math.sin(noise * 2.3 + 1.7) * 0.35)
                * (0.55f + difficulty * 0.35f);

        if (shoves) {
            shoveTimer -= dt;
            if (shoveTimer <= 0f) {
                shoveTimer = 1.6f + rng.nextFloat() * 2.2f;
                vel += (rng.nextBoolean() ? 1f : -1f) * (0.9f + rng.nextFloat() * 0.7f);
                Sfx.play(WardSounds.OVERSET, 0.45f, 1.5f);
                flashTimer = 0.2f;
                flashColor = theme().accentLite;
            }
        }

        vel += (torque + push()) * dt;
        vel *= heavy ? (1f - 1.1f * dt) : (1f - 2.4f * dt);
        tilt += vel * dt * (heavy ? 0.75f : 1f);

        if (wandering) {
            seat += seatDrift * dt;
            float lim = 0.55f - safeHalf;
            if (seat < -lim) {
                seat = -lim;
                seatDrift = -seatDrift;
            } else if (seat > lim) {
                seat = lim;
                seatDrift = -seatDrift;
            }
        }

        // Was 1.0, i.e. the very ends of the beam, which meant the red blocks
        // drawn at the edges were nearly unreachable and losing felt arbitrary:
        // you were told "the beam went over" without ever seeing it get there.
        // The kill zone now starts where the red actually is.
        // Something knocks the beam.
        //
        // Balanced at the middle with a steady hand, the lock had nothing left
        // to do: you could park it and wait out the hold. A shove every second
        // or two, hard enough to matter and from an unpredictable side, means
        // the hold has to be actively kept rather than merely achieved.
        kickTimer -= dt;
        if (kickTimer <= 0f) {
            kickTimer = 1.5f + rng.nextFloat() * 1.9f;
            // Softer, and never on top of a beam that is already moving fast.
            // A shove landing while the last one is still travelling is what
            // threw the ball straight to the edge with nothing you could do.
            if (Math.abs(vel) > 0.55f) return;
            float force = (0.28f + rng.nextFloat() * 0.34f) * Math.max(0.7f, difficulty);
            vel += (rng.nextBoolean() ? force : -force);
            flashTimer = 0.18f;
            Sfx.play(WardSounds.PIN_DROP, 0.35f, 0.8f + rng.nextFloat() * 0.3f);
        }

        if (Math.abs(tilt) >= WardConfig.balanceEdge) {
            tilt = Math.signum(tilt) * (WardConfig.balanceEdge - 0.001f);
            vel = 0f;
            // And it takes progress with it, not just a life. Holding for eight
            // seconds and then tipping should hurt more than a heartbeat of red.
            charge = 0f;
            slips++;
            charge = Math.max(0f, charge - holdTime * 0.4f);
            flashTimer = 0.4f;
            flashColor = COL_BAD;
            loseLife("The beam went over \u2014 the hold is lost");
            return;
        }

        if (Math.abs(tilt - seat) <= safeHalf) {
            charge += dt;
            if (charge >= holdTime) {
                succeed(Mth.clamp(1f - slips * 0.22f, 0.25f, 1f));
                return;
            }
        } else {
            charge = Math.max(0f, charge - dt * 0.45f);
        }

        progress = Math.round(Mth.clamp(charge / holdTime, 0f, 1f) * 100f);
        if (flashTimer > 0f) flashTimer -= dt;
    }

    @Override
    protected boolean onKey(int key) {
        if (key == KEY_A || key == KEY_LEFT) {
            pushLeft = 1;
            return true;
        }
        if (key == KEY_D || key == KEY_RIGHT) {
            pushRight = 1;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyRelease(int key) {
        if (key == KEY_A || key == KEY_LEFT) {
            pushLeft = 0;
            return true;
        }
        if (key == KEY_D || key == KEY_RIGHT) {
            pushRight = 0;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (mx < centerX()) pushLeft = 1;
        else pushRight = 1;
        return true;
    }

    @Override
    protected boolean onRelease(double mx, double my, int button) {
        if (button != 0) return false;
        pushLeft = 0;
        pushRight = 0;
        return true;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int cx = centerX();
        int cy = contentCenterY() - 12;
        int halfW = panelWidth() / 2 - 36;

        // the beam, tipped by the current lean
        float lean = tilt * 12f;
        int lx = cx - halfW, rx = cx + halfW;
        int ly = Math.round(cy + lean), ry = Math.round(cy - lean);

        p.line(lx, ly + 4, rx, ry + 4, 8, Painter.withAlpha(0x000000, 0x50));
        p.line(lx, ly, rx, ry, 8, t.frameDark);
        p.line(lx, ly - 1, rx, ry - 1, 5, t.frame);
        p.line(lx, ly - 2, rx, ry - 2, 2, t.frameLite);

        // danger caps
        p.rect(lx - 8, ly - 8, lx + 2, ly + 8, COL_BAD);
        p.rect(rx - 2, ry - 8, rx + 8, ry + 8, COL_BAD);
        Sigils.drawCentered(p, Sigils.TILE[6], lx - 3, ly, 1, Painter.withAlpha(0x160B0B, 0xCC), 0, 0);
        Sigils.drawCentered(p, Sigils.TILE[6], rx + 3, ry, 1, Painter.withAlpha(0x160B0B, 0xCC), 0, 0);

        // the seat
        int seatX = cx + Math.round(seat * halfW);
        int seatW = Math.max(6, Math.round(safeHalf * halfW));
        int seatY = cy - 26;
        p.rect(seatX - seatW, seatY, seatX + seatW, seatY + 46, Painter.withAlpha(COL_GOOD, 0x1C));
        p.rect(seatX - seatW, seatY, seatX - seatW + 2, seatY + 46, Painter.withAlpha(COL_GOOD, 0xAA));
        p.rect(seatX + seatW - 2, seatY, seatX + seatW, seatY + 46, Painter.withAlpha(COL_GOOD, 0xAA));
        p.rect(seatX - 1, seatY, seatX + 1, seatY + 6, COL_GOOD);

        // the bubble
        int bx = cx + Math.round(tilt * halfW);
        float slope = (ry - ly) / (float) (rx - lx);
        int by = Math.round(ly + (bx - lx) * slope) - 9;
        boolean seated = Math.abs(tilt - seat) <= safeHalf;
        int bub = flashTimer > 0f ? flashColor : (seated ? COL_GOOD : t.accent);
        p.disc(bx, by, 8, 0xFF0C0906);
        p.disc(bx, by, 7, Painter.darken(bub, 0.4f));
        p.disc(bx, by, 5, bub);
        Sigils.drawCentered(p, Sigils.TILE[0], bx, by, 1, Painter.withAlpha(0x0B0806, 0xEE), 0, 0);

        // pivot
        int py = cy + 12;
        for (int i = 0; i < 12; i++) {
            p.rect(cx - i, py + i, cx + i, py + i + 1, i < 3 ? t.frameLite : t.frame);
        }
        p.rect(cx - 16, py + 12, cx + 16, py + 15, t.frameDark);

        // lean indicators, so a held key reads on screen
        int arrowY = py + 22;
        drawPush(cx - 46, arrowY, pushLeft > 0, false, t);
        drawPush(cx + 46, arrowY, pushRight > 0, true, t);

        // charge meter
        int mw = panelWidth() - 96;
        int mx = cx - mw / 2;
        int my = contentBottom() - 14;
        p.roundRect(mx - 2, my - 2, mw + 4, 14, t.wellEdge);
        p.roundRect(mx, my, mw, 10, t.well);
        int filled = Math.round(mw * Mth.clamp(charge / holdTime, 0f, 1f));
        for (int i = 0; i < filled; i += 4) {
            p.rect(mx + i, my + 1, mx + Math.min(filled, i + 3), my + 9,
                    seated ? COL_GOOD : Painter.withAlpha(t.accent, 0xCC));
        }
        smallCentered(g, "seat the bolt   " + Math.round(charge * 10) / 10f + " / "
                + Math.round(holdTime * 10) / 10f + "s", cx, my - 14, t.faint, 0.85f);
    }

    private void drawPush(int cx, int cy, boolean on, boolean right, Theme t) {
        int col = on ? t.accentLite : Painter.withAlpha(t.faint, 0x77);
        for (int i = 0; i < 6; i++) {
            int w = 6 - i;
            int x = right ? cx - 6 + i : cx + 6 - i;
            p.rect(Math.min(x, x + 1), cy - w, Math.max(x, x + 1) + 1, cy + w, col);
        }
    }

    @Override
    protected String hintText() {
        return "A / D or click a side to lean \u00b7 keep the bubble seated";
    }
}
