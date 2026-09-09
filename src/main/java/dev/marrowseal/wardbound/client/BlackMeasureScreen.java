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
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Three black-glass measures. Select a source, then a destination; all liquid
 * that can move does so. Reach the inscribed amount before the allowed pours
 * run out. Reachability and the move budget come from a tiny BFS, so every
 * generated round is solvable under its active variant. */
@OnlyIn(Dist.CLIENT)
public final class BlackMeasureScreen extends BaseMinigameScreen {
    private static final int[][] CAPACITY_SETS = {{8, 5, 3}, {11, 7, 4}, {9, 6, 4}};
    private static final int VESSELS = 3;
    private static final int V_W = 64;
    private static final int V_H = 112;
    private static final int GAP = 28;

    private final boolean blackGlass;
    private final boolean sealedSpout;
    private final boolean pairedMeasure;
    private final boolean variedVessels;
    private final boolean falseBottom;
    private final boolean seepingVessel;
    private final int rounds;

    private final int[] cap = {8, 5, 3};
    private final int[] amount = {8, 0, 0};
    private final int[] bgGlyph = new int[14];
    private final int[] bgX = new int[14];
    private final int[] bgY = new int[14];
    private int source = -1;
    private int blockedPair = -1;
    private int hiddenVessel = -1;
    private int target = 4;
    private boolean pairedGoalActive;
    private int reserveVessel = -1;
    private int leakVessel = -1;
    private int movesLeft;
    private int idealMoves;
    private int usedMoves;
    private int totalIdeal;
    private int totalUsed;
    private float pulse;
    private float flash;
    private int lastVessel = -1;

    public BlackMeasureScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.measure"), msg);
        boolean[] q = pickVariants(3, 2591);
        boolean gentleIntro = familiarity <= 1 && value < 55;
        blackGlass = !gentleIntro && q[0];
        sealedSpout = !gentleIntro && q[1];
        pairedMeasure = q[2] && (familiarity > 0 || value >= 48);
        variedVessels = expertVariant(2601, 2, 72, gentleIntro ? 0.0f : 0.46f);
        falseBottom = expertVariant(2602, 3, 84, gentleIntro ? 0.0f : 0.30f);
        seepingVessel = expertVariant(2603, 4, 96, gentleIntro ? 0.0f : 0.24f);
        if (blackGlass) modifierLabels.add("black glass");
        if (sealedSpout) modifierLabels.add("sealed spout");
        if (pairedMeasure) modifierLabels.add("paired measure");
        if (variedVessels) modifierLabels.add("foreign vessels");
        if (falseBottom) modifierLabels.add("false bottom");
        if (seepingVessel) modifierLabels.add("weeping glass");
        rounds = Mth.clamp(2 + (value >= 62 || difficulty > 1.28f ? 1 : 0), 2, 3);
        buildRound();
    }

    @Override protected Theme createTheme() { return Theme.vessel(); }
    @Override protected int panelHeight() { return 286; }
    @Override protected float timeBudget() { return super.timeBudget() * 1.22f; }
    @Override protected boolean allows(Anomaly a) { return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING; }

    @Override
    protected void init() {
        super.init();
        Random r = new Random(seed ^ 0x4D454153555245L);
        for (int i = 0; i < bgGlyph.length; i++) {
            bgGlyph[i] = r.nextInt(Sigils.TILE.length);
            bgX[i] = left() + 24 + r.nextInt(Math.max(1, panelWidth() - 48));
            bgY[i] = contentTop() + 12 + r.nextInt(Math.max(1, contentBottom() - contentTop() - 34));
        }
    }

    private void buildRound() {
        Random r = new Random(seed ^ (progress * 0x9E3779B97F4A7C15L) ^ 0x4D454153L);
        int set = variedVessels ? 1 + Math.floorMod((int)(seed + progress * 3L), CAPACITY_SETS.length - 1) : 0;
        System.arraycopy(CAPACITY_SETS[set], 0, cap, 0, VESSELS);
        amount[0] = cap[0]; amount[1] = 0; amount[2] = 0;
        reserveVessel = falseBottom ? r.nextInt(VESSELS) : -1;
        leakVessel = seepingVessel ? 1 + r.nextInt(VESSELS - 1) : -1;
        source = -1;
        hiddenVessel = blackGlass ? r.nextInt(VESSELS) : -1;
        blockedPair = sealedSpout ? randomBlockedPair(r) : -1;
        pairedGoalActive = pairedMeasure;

        Map<Integer,Integer> dist = bfs(blockedPair);
        List<int[]> candidates = targetCandidates(dist, pairedGoalActive);
        if (candidates.isEmpty() && pairedGoalActive) {
            pairedGoalActive = false;
            candidates = targetCandidates(dist, false);
        }
        if (candidates.isEmpty()) {
            target = 4;
            idealMoves = 6;
        } else {
            int desired = Mth.clamp(3 + progress + Math.round((difficulty - 1f) * 1.2f), 3, 7);
            int[] best = candidates.get(0);
            int bestDelta = Math.abs(best[1] - desired);
            for (int[] c : candidates) {
                int delta = Math.abs(c[1] - desired);
                if (delta < bestDelta || (delta == bestDelta && r.nextBoolean())) { best = c; bestDelta = delta; }
            }
            target = best[0];
            idealMoves = best[1];
        }
        movesLeft = idealMoves + 2 + Math.min(2, familiarity / 8);
        usedMoves = 0;
        flash = 0f;
        lastVessel = -1;
    }

    private int randomBlockedPair(Random r) {
        // Pair 1<->2 leaves the classic 8/5/3 measure with no non-trivial
        // single-vessel target. Seal only channels that retain useful puzzles.
        return r.nextBoolean() ? pairCode(0, 1) : pairCode(0, 2);
    }

    private int pairCode(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return lo * 3 + hi;
    }

    private boolean blocked(int a, int b, int pair) {
        return pair >= 0 && pairCode(a, b) == pair;
    }

    private int encode(int a, int b, int c) {
        return (a * (cap[1] + 1) + b) * (cap[2] + 1) + c;
    }

    private int[] decode(int code) {
        int c = code % (cap[2] + 1);
        code /= (cap[2] + 1);
        int b = code % (cap[1] + 1);
        int a = code / (cap[1] + 1);
        return new int[]{a, b, c};
    }

    private int pourable(int vessel, int quantity) {
        int floor = vessel == reserveVessel && quantity > 0 ? 1 : 0;
        return Math.max(0, quantity - floor);
    }

    private int[] pouredState(int[] state, int source, int destination) {
        if (source == destination || blocked(source, destination, blockedPair)) return null;
        int move = Math.min(pourable(source, state[source]), cap[destination] - state[destination]);
        if (move <= 0) return null;
        int[] next = state.clone();
        next[source] -= move;
        next[destination] += move;
        if (leakVessel >= 0) {
            int floor = leakVessel == reserveVessel && next[leakVessel] > 0 ? 1 : 0;
            if (next[leakVessel] > floor) next[leakVessel]--;
        }
        return next;
    }

    private Map<Integer,Integer> bfs(int blocked) {
        Map<Integer,Integer> dist = new HashMap<>();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int start = encode(cap[0],0,0);
        dist.put(start, 0); q.add(start);
        while (!q.isEmpty()) {
            int code = q.removeFirst();
            int[] a = decode(code);
            int nd = dist.get(code) + 1;
            for (int s = 0; s < VESSELS; s++) for (int d = 0; d < VESSELS; d++) {
                if (s == d || blocked(s, d, blocked)) continue;
                int[] n = pouredState(a, s, d);
                if (n == null) continue;
                int nc = encode(n[0], n[1], n[2]);
                if (!dist.containsKey(nc)) { dist.put(nc, nd); q.addLast(nc); }
            }
        }
        return dist;
    }

    private List<int[]> targetCandidates(Map<Integer,Integer> dist, boolean paired) {
        List<int[]> out = new ArrayList<>();
        int maxTarget = Math.max(cap[0], Math.max(cap[1], cap[2])) - 1;
        for (int t = 1; t <= maxTarget; t++) {
            int min = Integer.MAX_VALUE;
            for (Map.Entry<Integer,Integer> e : dist.entrySet()) {
                int[] a = decode(e.getKey());
                int count = 0;
                for (int v : a) if (v == t) count++;
                boolean ok = paired ? count >= 2 : count >= 1;
                if (ok) min = Math.min(min, e.getValue());
            }
            if (min != Integer.MAX_VALUE && min >= 2) out.add(new int[]{t, min});
        }
        return out;
    }

    private boolean goalSatisfied() {
        int count = 0;
        for (int v : amount) if (v == target) count++;
        return pairedGoalActive ? count >= 2 : count >= 1;
    }

    private int totalW() { return VESSELS * V_W + (VESSELS - 1) * GAP; }
    private int vesselX(int i) { return centerX() - totalW()/2 + i * (V_W + GAP); }
    private int vesselY() { return contentCenterY() - 48; }
    private int vesselAt(double mx, double my) {
        int y = vesselY();
        for (int i = 0; i < VESSELS; i++) {
            int x = vesselX(i);
            if (mx >= x && mx < x + V_W && my >= y && my < y + V_H) return i;
        }
        return -1;
    }

    @Override protected void step(float dt) { pulse += dt; if (flash > 0f) flash = Math.max(0f, flash - dt); }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        int v = vesselAt(mx,my);
        if (v < 0) return false;
        if (source < 0) {
            if (amount[v] <= 0) { Sfx.markBad(); showBanner("An empty measure cannot pour", COL_BAD, 620); return true; }
            source = v;
            Sfx.play(WardSounds.SURFACE_STILL, 0.28f, 0.88f + v * 0.08f);
            return true;
        }
        if (v == source) { source = -1; return true; }
        if (blocked(source, v, blockedPair)) {
            mistakes++; lastVessel = v; flash = 0.28f;
            Sfx.markBad(); showBanner("Wax seals that spout", COL_BAD, 650); source = -1; return true;
        }
        int move = Math.min(pourable(source, amount[source]), cap[v] - amount[v]);
        if (move <= 0) {
            mistakes++; lastVessel = v; flash = 0.28f;
            Sfx.markBad(); showBanner("Nothing can pass between those measures", COL_BAD, 650); source = -1; return true;
        }
        amount[source] -= move;
        amount[v] += move;
        if (leakVessel >= 0) {
            int floor = leakVessel == reserveVessel && amount[leakVessel] > 0 ? 1 : 0;
            if (amount[leakVessel] > floor) amount[leakVessel]--;
        }
        usedMoves++; movesLeft--;
        lastVessel = v; flash = 0.24f;
        Sfx.play(WardSounds.SURFACE_STILL, 0.42f, 0.92f + move * 0.035f);
        source = -1;

        if (goalSatisfied()) {
            totalIdeal += idealMoves; totalUsed += usedMoves; progress++;
            Sfx.mark(progress + 1); burst(centerX(), vesselY() + V_H/2, COL_GOOD);
            if (progress >= rounds) {
                float eff = totalUsed <= 0 ? 1f : Mth.clamp((float)totalIdeal / totalUsed, 0.42f, 1f);
                succeed(eff);
            } else {
                showBanner("The measure accepts the quantity", COL_GOOD, 740);
                buildRound();
            }
            return true;
        }
        if (movesLeft <= 0) {
            totalUsed += usedMoves;
            if (loseLife("The black glass keeps the wrong measure")) buildRound();
        }
        return true;
    }

    @Override protected boolean onKey(int key) { return false; }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = theme();
        int hover = vesselAt(mouseX,mouseY);
        for (int i = 0; i < bgGlyph.length; i++) {
            int a = 0x10 + (i % 3) * 5;
            if (!WardConfig.accessibilityReduceMotion) a += Math.round((0.5f + 0.5f*Mth.sin(pulse*0.48f+i))*4f);
            Sigils.drawCentered(p, Sigils.TILE[bgGlyph[i]], bgX[i], bgY[i], 1,
                    Painter.withAlpha(t.frameLite,a), Painter.withAlpha(t.accentDark,a/2), 0);
        }
        int targetY = contentTop() + 15;
        smallCentered(g, pairedGoalActive ? "INSCRIBED MEASURE · make " + target + " in TWO vessels"
                        : "INSCRIBED MEASURE · make exactly " + target,
                centerX(), targetY, t.accentLite, 0.86f);

        for (int i = 0; i < VESSELS; i++) drawVessel(g,i,hover==i);
        drawSpouts();

        int y = vesselY() + V_H + 17;
        progressDots(centerX(), y, rounds, progress);
        smallCentered(g, "moves " + movesLeft + "   ·   click the vessel you pour FROM, then the vessel you pour INTO", centerX(), y+15,
                movesLeft <= 2 ? COL_BAD : t.dim, 0.70f);
    }

    private void drawVessel(GuiGraphics g, int i, boolean hot) {
        Theme t = theme();
        int x = vesselX(i), y = vesselY();
        boolean selected = source == i;
        int edge = selected ? t.accentLite : (hot ? t.frameLite : t.frameDark);
        p.roundRect(x-3,y-3,V_W+6,V_H+6,edge);
        p.roundRect(x,y,V_W,V_H,0xFF090B0D);
        p.dither(x+2,y+2,V_W-4,V_H-4,Painter.withAlpha(t.frameLite,0x0C),3,i*7+progress);
        int innerX=x+10, innerY=y+10, innerW=V_W-20, innerH=V_H-26;
        p.outline(innerX,innerY,innerW,innerH,1,Painter.withAlpha(t.frameLite,0x70));
        int fillH = Math.round(innerH * (amount[i] / (float)cap[i]));
        if (fillH > 0) {
            int fy=innerY+innerH-fillH;
            p.rect(innerX+2,fy,innerX+innerW-2,innerY+innerH-2,Painter.withAlpha(t.accentDark,0xB8));
            p.dither(innerX+2,fy,innerW-4,fillH-2,Painter.withAlpha(t.accentLite,0x22),2,i+progress);
        }
        Sigils.drawCentered(p,Sigils.TILE[(i*3+progress)%Sigils.TILE.length],x+V_W/2,y+34,1,
                Painter.withAlpha(t.accentLite,0xA0),Painter.withAlpha(t.accent,0x45),0);
        String amt = (blackGlass && i==hiddenVessel) ? "? / " + cap[i] : amount[i] + " / " + cap[i];
        centered(g,amt,x+V_W/2,y+V_H-14,(blackGlass&&i==hiddenVessel)?t.faint:t.frameLite);
        if (i == reserveVessel) {
            int ry = innerY + innerH - Math.round(innerH / (float)cap[i]);
            p.line(innerX + 2, ry, innerX + innerW - 2, ry, 1, Painter.withAlpha(0xFFD8B778, 0xB0));
            smallCentered(g, "1 remains", x + V_W / 2, y + V_H - 27, 0xFFD8B778, 0.62f);
        }
        if (i == leakVessel) {
            p.disc(x + V_W - 7, y + V_H - 8, 2, Painter.withAlpha(t.accentLite, 0xA8));
            p.line(x + V_W - 7, y + V_H - 12, x + V_W - 7, y + V_H - 18, 1, Painter.withAlpha(t.accentLite, 0x70));
        }
        if (flash>0f && lastVessel==i) p.roundOutline(x-5,y-5,V_W+10,V_H+10,Painter.scaleAlpha(COL_BAD,flash));
    }

    private void drawSpouts() {
        Theme t=theme();
        int y=vesselY()+V_H/2;
        for (int a=0;a<VESSELS;a++) for (int b=a+1;b<VESSELS;b++) {
            int ax=vesselX(a)+V_W, bx=vesselX(b);
            int mid=(ax+bx)/2;
            p.line(ax+2,y,bx-2,y,1,Painter.withAlpha(t.frame,0x55));
            boolean sealed = blocked(a, b, blockedPair);
            if (sealed) {
                p.line(mid-5,y-5,mid+5,y+5,1,COL_BAD);
                p.line(mid-5,y+5,mid+5,y-5,1,COL_BAD);
            } else {
                p.disc(mid,y,2,Painter.withAlpha(t.accent,0xA0));
            }
        }
    }

    @Override
    protected String hintText() {
        if (pairedGoalActive) return "The inscription demands the same amount in two black measures";
        if (seepingVessel) return "The marked glass loses one unit after every pour; the inscription was generated with the leak already accounted for";
        if (falseBottom) return "The gold line is a false bottom: once that vessel holds liquid, one unit can never be poured out";
        if (variedVessels) return "Do not assume eight, five and three. Read the capacities cut into this particular set";
        if (sealedSpout) return "One waxed channel is sealed in both directions; the remaining pours stay exact";
        if (blackGlass) return "One measure has no numerals; its fill line still tells the truth";
        return "Pour between vessels until the target amount exists exactly where the inscription demands";
    }
}
