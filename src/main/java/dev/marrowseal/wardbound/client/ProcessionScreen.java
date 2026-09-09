package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Last Procession: arrange six witnesses from a set of relational statements.
 * The generator enumerates all 720 orders and only ships a clue set that leaves
 * exactly one valid permutation. A wrong verdict costs a life and highlights a
 * violated testimony instead of silently becoming a guessing game.
 */
@OnlyIn(Dist.CLIENT)
public final class ProcessionScreen extends BaseMinigameScreen {
    private static final int N = 6;
    private static final int TILE = 42;
    private static final int GAP = 8;
    private static final String[] NAME = {"I", "II", "III", "IV", "V", "VI"};
    private static final int BEFORE = 0, ADJ = 1, GAP_ONE = 2, EDGE = 3, BETWEEN = 4, AT = 5, NOT_ADJ = 6;
    private record Clue(int type, int a, int b, int c, int n) {}

    private final int[] target = new int[N];
    private final int[] current = new int[N];
    private final int[] glyph = new int[N];
    private final List<Clue> clues = new ArrayList<>();
    private final int rounds;
    private final boolean strictInk;
    private final boolean redactedWitness;

    private int redactedIndex = -1;
    private int sealedSlot = -1;
    private int selected = -1;
    private int moves;
    private int ideal;
    private int moveLimit;
    private int wrongSubmits;
    private int totalMoves;
    private int totalIdeal;
    private int violatedFlash = -1;
    private float flash;

    public ProcessionScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.procession"), msg);
        boolean[] v = pickVariants(2, 0x51524F43);
        strictInk = v[0];
        redactedWitness = v[1] && familiarity >= 2;
        if (strictInk) modifierLabels.add("narrow ink");
        if (redactedWitness) modifierLabels.add("redacted witness");
        if (makerBindsLogic()) modifierLabels.add("maker · seated witness");
        if (cardBindsOne()) modifierLabels.add("card · seated witness");
        rounds = Mth.clamp(2 + (value >= 72 || difficulty > 1.24f ? 1 : 0), 2, 3);
        buildRound();
    }

    @Override protected Theme createTheme() {
        return Theme.of(0xFF665458, 0xFF0D0A0C, 0xFFB28A96, 0xFFE4D4B2, 1, Theme.PATTERN_SCANLINE);
    }
    @Override protected int panelWidth() {
        if (width <= 0) return 376;
        return Math.min(376, Math.max(286, width - 8));
    }
    @Override protected int panelHeight() {
        if (height <= 0) return 282;
        return Math.min(282, Math.max(220, height - 8));
    }
    private boolean compactLayout() { return panelHeight() < 264 || panelWidth() < 350; }
    private int tileSize() {
        if (!compactLayout()) return TILE;
        int gap = tileGap();
        return Math.max(30, Math.min(35, (panelWidth() - 34 - (N - 1) * gap) / N));
    }
    private int tileGap() { return compactLayout() ? 5 : GAP; }
    @Override protected float timeBudget() { return super.timeBudget() * 1.14f; }

    private void buildRound() {
        Random r = new Random(seed ^ (progress * 0xD1B54A32D192ED03L) ^ 0x50524F43455353L);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < N; i++) ids.add(i);
        Collections.shuffle(ids, r);
        for (int i = 0; i < N; i++) target[i] = ids.get(i);
        for (int i = 0; i < N; i++) glyph[i] = r.nextInt(Sigils.TILE.length);

        sealedSlot = (makerBindsLogic() || cardBindsOne()) ? r.nextInt(N) : -1;
        System.arraycopy(target, 0, current, 0, N);
        int scramble = 3 + progress + (value >= 70 ? 1 : 0);
        for (int i = 0; i < scramble; i++) {
            int a, b;
            do a = r.nextInt(N); while (a == sealedSlot);
            do b = r.nextInt(N); while (b == a || b == sealedSlot);
            int t = current[a]; current[a] = current[b]; current[b] = t;
        }
        if (matchesTarget()) {
            int a = sealedSlot == 0 ? 1 : 0, b = sealedSlot == 1 ? 2 : 1;
            int t = current[a]; current[a] = current[b]; current[b] = t;
        }
        ideal = minimumSwaps(current, target, sealedSlot);
        moveLimit = Math.max(ideal + (strictInk ? 1 : 3), 3);
        moves = 0; selected = -1; wrongSubmits = 0; violatedFlash = -1; flash = 0f;
        buildUniqueClues(r);
    }

    private static int[] positions(int[] order) {
        int[] p = new int[N];
        for (int i = 0; i < N; i++) p[order[i]] = i;
        return p;
    }

    private boolean trueFor(Clue clue, int[] order) {
        int[] p = positions(order);
        return switch (clue.type) {
            case BEFORE -> p[clue.a] < p[clue.b];
            case ADJ -> p[clue.b] == p[clue.a] + 1;
            case GAP_ONE -> Math.abs(p[clue.a] - p[clue.b]) == 2;
            case EDGE -> p[clue.a] == 0 || p[clue.a] == N - 1;
            case BETWEEN -> (p[clue.a] < p[clue.b] && p[clue.b] < p[clue.c]) || (p[clue.c] < p[clue.b] && p[clue.b] < p[clue.a]);
            case AT -> p[clue.a] == clue.n;
            case NOT_ADJ -> Math.abs(p[clue.a] - p[clue.b]) != 1;
            default -> false;
        };
    }

    private void permute(List<int[]> out, int[] a, int idx) {
        if (idx == N) { out.add(a.clone()); return; }
        for (int i = idx; i < N; i++) {
            int t = a[idx]; a[idx] = a[i]; a[i] = t;
            permute(out, a, idx + 1);
            t = a[idx]; a[idx] = a[i]; a[i] = t;
        }
    }

    private List<int[]> allOrders() {
        List<int[]> out = new ArrayList<>(720);
        permute(out, new int[]{0,1,2,3,4,5}, 0);
        return out;
    }

    private List<int[]> filtered(List<Clue> active) {
        List<int[]> out = allOrders();
        out.removeIf(order -> { for (Clue c : active) if (!trueFor(c, order)) return true; return false; });
        return out;
    }

    private void buildUniqueClues(Random r) {
        clues.clear(); redactedIndex = -1;
        int[] p = positions(target);
        List<Clue> facts = new ArrayList<>();
        for (int a = 0; a < N; a++) for (int b = 0; b < N; b++) if (a != b) {
            if (p[a] < p[b]) facts.add(new Clue(BEFORE, a, b, -1, -1));
            if (p[b] == p[a] + 1) facts.add(new Clue(ADJ, a, b, -1, -1));
            if (Math.abs(p[a] - p[b]) == 2 && a < b) facts.add(new Clue(GAP_ONE, a, b, -1, -1));
            if (Math.abs(p[a] - p[b]) != 1 && a < b) facts.add(new Clue(NOT_ADJ, a, b, -1, -1));
        }
        facts.add(new Clue(EDGE, target[0], -1, -1, -1));
        facts.add(new Clue(EDGE, target[N-1], -1, -1, -1));
        for (int i = 0; i < N - 2; i++) facts.add(new Clue(BETWEEN, target[i], target[i+1], target[i+2], -1));
        Collections.shuffle(facts, r);

        List<int[]> candidates = allOrders();
        while (candidates.size() > 1 && clues.size() < 7) {
            Clue best = null; int bestCount = candidates.size();
            for (Clue c : facts) {
                if (clues.contains(c)) continue;
                int count = 0;
                for (int[] order : candidates) if (trueFor(c, order)) count++;
                if (count > 0 && count < bestCount) { best = c; bestCount = count; }
            }
            if (best == null) break;
            clues.add(best);
            Clue chosen = best;
            candidates.removeIf(order -> !trueFor(chosen, order));
        }
        for (int slot = 0; candidates.size() > 1 && slot < N; slot++) {
            Clue exact = new Clue(AT, target[slot], -1, -1, slot);
            clues.add(exact);
            candidates.removeIf(order -> !trueFor(exact, order));
        }
        if (cardRevealsOne()) {
            int slot = r.nextInt(N);
            Clue exact = new Clue(AT, target[slot], -1, -1, slot);
            if (!clues.contains(exact)) clues.add(0, exact);
        }

        if (redactedWitness && clues.size() >= 5) {
            for (int i = clues.size() - 1; i >= 0; i--) {
                List<Clue> copy = new ArrayList<>(clues);
                copy.remove(i);
                if (filtered(copy).size() == 1) { redactedIndex = i; break; }
            }
        }
    }

    private static int minimumSwaps(int[] from, int[] to, int sealed) {
        int[] posTo = positions(to);
        boolean[] seen = new boolean[N];
        int swaps = 0;
        for (int i = 0; i < N; i++) {
            if (seen[i] || from[i] == to[i] || i == sealed) { seen[i] = true; continue; }
            int len = 0, j = i;
            while (!seen[j]) {
                seen[j] = true; len++;
                int item = from[j];
                j = posTo[item];
            }
            if (len > 1) swaps += len - 1;
        }
        return Math.max(1, swaps);
    }

    private boolean matchesTarget() {
        for (int i = 0; i < N; i++) if (current[i] != target[i]) return false;
        return true;
    }

    private int boardX() {
        int tile = tileSize(), gap = tileGap();
        return centerX() - (N * tile + (N - 1) * gap) / 2;
    }
    private int boardY() { return contentTop() + (compactLayout() ? 37 : 49); }

    private int tileAt(double mx, double my) {
        int by = boardY(), tile = tileSize(), gap = tileGap();
        for (int i = 0; i < N; i++) {
            int x = boardX() + i * (tile + gap);
            if (mx >= x && mx <= x + tile && my >= by && my <= by + tile) return i;
        }
        return -1;
    }

    private void swap(int a, int b) {
        if (a == b) { selected = -1; return; }
        if (a == sealedSlot || b == sealedSlot) {
            showBanner("That witness has already been seated", theme().dim, 650);
            selected = -1; return;
        }
        int t = current[a]; current[a] = current[b]; current[b] = t;
        moves++; selected = -1;
        Sfx.play(WardSounds.STONE_TURN, 0.32f, 0.88f + moves * 0.025f);
        if (moves >= moveLimit && !matchesTarget()) {
            if (loseLife("The procession exhausts its remaining ink")) {
                showBanner("The witnesses return to their first testimony", COL_BAD, 760);
                buildRound();
            }
        }
    }

    private boolean submitArea(double mx, double my) {
        int y = contentBottom() - (compactLayout() ? 24 : 29);
        return mx >= centerX() - 55 && mx <= centerX() + 55 && my >= y && my <= y + 18;
    }

    private void submit() {
        if (matchesTarget()) {
            totalMoves += moves; totalIdeal += Math.max(1, ideal); progress++;
            Sfx.mark(progress + 3); burst(centerX(), contentCenterY(), COL_GOOD);
            if (progress >= rounds) {
                float efficiency = Mth.clamp(totalIdeal / (float)Math.max(totalIdeal, totalMoves + wrongSubmits), 0.46f, 1f);
                succeed(efficiency);
            } else {
                showBanner("The testimony agrees. Another procession enters.", COL_GOOD, 760);
                buildRound();
            }
            return;
        }
        violatedFlash = -1;
        for (int i = 0; i < clues.size(); i++) {
            if (i == redactedIndex) continue;
            if (!trueFor(clues.get(i), current)) { violatedFlash = i; break; }
        }
        wrongSubmits++;
        flash = 0.8f;
        Sfx.markBad();
        if (loseLife("The witnesses reject that order"))
            showBanner("One testimony burns brighter: your order contradicts it", COL_BAD, 820);
    }

    @Override protected void step(float dt) {
        if (flash > 0f) flash = Math.max(0f, flash - dt);
        else violatedFlash = -1;
    }

    @Override protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        int tile = tileAt(mx, my);
        if (tile >= 0) {
            if (selected < 0) { selected = tile; Sfx.mark(tile); }
            else swap(selected, tile);
            return true;
        }
        if (submitArea(mx, my)) { submit(); return true; }
        return false;
    }

    @Override protected boolean onKey(int key) {
        if (isActionKey(key)) { submit(); return true; }
        if (selected >= 0 && (key == 263 || key == 65) && selected > 0) { swap(selected, selected - 1); return true; }
        if (selected >= 0 && (key == 262 || key == 68) && selected < N - 1) { swap(selected, selected + 1); return true; }
        return false;
    }

    private String clueText(Clue c) {
        return switch (c.type) {
            case BEFORE -> NAME[c.a] + " precedes " + NAME[c.b];
            case ADJ -> NAME[c.a] + " stands directly before " + NAME[c.b];
            case GAP_ONE -> "one witness stands between " + NAME[c.a] + " and " + NAME[c.b];
            case EDGE -> NAME[c.a] + " stands at an outer end";
            case BETWEEN -> NAME[c.b] + " stands somewhere between " + NAME[c.a] + " and " + NAME[c.c];
            case AT -> NAME[c.a] + " occupies place " + (c.n + 1);
            case NOT_ADJ -> NAME[c.a] + " does not stand beside " + NAME[c.b];
            default -> "unread testimony";
        };
    }

    private String compactClueText(Clue c) {
        return switch (c.type) {
            case BEFORE -> NAME[c.a] + " < " + NAME[c.b];
            case ADJ -> NAME[c.a] + " immediately < " + NAME[c.b];
            case GAP_ONE -> "1 between " + NAME[c.a] + "/" + NAME[c.b];
            case EDGE -> NAME[c.a] + " at edge";
            case BETWEEN -> NAME[c.b] + " between " + NAME[c.a] + "/" + NAME[c.c];
            case AT -> NAME[c.a] + " = place " + (c.n + 1);
            case NOT_ADJ -> NAME[c.a] + " not beside " + NAME[c.b];
            default -> "unread";
        };
    }

    @Override protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bx = boardX(), by = boardY();
        boolean compact = compactLayout();
        int tile = tileSize(), gap = tileGap();
        progressDots(centerX(), contentTop() + (compact ? 9 : 15), rounds, progress);
        smallCentered(g, compact ? "MAKE EVERY TESTIMONY TRUE" : "ARRANGE THE SIX WITNESSES SO EVERY SURVIVING STATEMENT IS TRUE",
                centerX(), contentTop() + (compact ? 21 : 28), theme().dim, compact ? 0.55f : 0.64f);
        for (int i = 0; i < N; i++) {
            int x = bx + i * (tile + gap);
            boolean hover = mouseX >= x && mouseX <= x + tile && mouseY >= by && mouseY <= by + tile;
            boolean sel = selected == i;
            int frame = i == sealedSlot ? 0xFF8D6D75 : sel ? theme().accentLite : hover ? theme().accent : theme().frame;
            p.roundRect(x, by, tile, tile, 0xFF151116);
            p.roundOutline(x, by, tile, tile, frame);
            int witness = current[i];
            Sigils.drawCentered(p, Sigils.TILE[glyph[witness]], x + tile / 2, by + (compact ? 13 : 17), 1,
                    i == sealedSlot ? 0xFFC5A7A9 : 0xFFE3D4B7, Painter.withAlpha(theme().accent, 0x72), 0x44000000);
            smallCentered(g, NAME[witness], x + tile / 2, by + tile - (compact ? 10 : 11), frame, compact ? 0.58f : 0.68f);
            smallCentered(g, Integer.toString(i + 1), x + tile / 2, by + tile + (compact ? 2 : 4), theme().faint, compact ? 0.44f : 0.52f);
        }

        int clueTop = by + tile + (compact ? 11 : 21);
        int columns = compact ? (clues.size() > 8 ? 3 : 2) : 2;
        int colGap = compact ? 5 : 12;
        int colW = compact ? Math.max(82, (panelWidth() - 28 - (columns - 1) * colGap) / columns) : 166;
        int rows = Math.max(1, (clues.size() + columns - 1) / columns);
        int available = Math.max(34, (contentBottom() - 43) - clueTop);
        int rowH = compact ? Mth.clamp(available / rows, 10, 15) : 18;
        int totalW = columns * colW + (columns - 1) * colGap;
        int startX = centerX() - totalW / 2;
        for (int i = 0; i < clues.size(); i++) {
            int col = i % columns, row = i / columns;
            int x = startX + col * (colW + colGap);
            int y = clueTop + row * rowH;
            boolean redacted = i == redactedIndex;
            int color = i == violatedFlash ? COL_BAD : redacted ? 0xFF6A5058 : theme().text;
            int boxH = compact ? Math.max(9, rowH - 1) : 15;
            p.roundRect(x, y, colW, boxH, Painter.withAlpha(theme().well, 0xD0));
            p.roundOutline(x, y, colW, boxH, Painter.withAlpha(color, i == violatedFlash ? 0xE8 : 0x74));
            String text = redacted ? (compact ? "[REDACTED]" : "[REDACTED — remaining testimony suffices]")
                    : (compact ? compactClueText(clues.get(i)) : clueText(clues.get(i)));
            smallCentered(g, text, x + colW / 2, y + (compact ? 2 : 4), color, compact ? 0.43f : 0.53f);
        }
        int sy = contentBottom() - (compact ? 24 : 29);
        p.roundRect(centerX() - 55, sy, 110, 18, Painter.withAlpha(theme().accentDark, 0xAA));
        p.roundOutline(centerX() - 55, sy, 110, 18, theme().accent);
        smallCentered(g, "ENTER VERDICT", centerX(), sy + 5, theme().accentLite, 0.66f);
        smallCentered(g, compact ? "SWAPS " + moves + "/" + moveLimit + " · MIN " + ideal : "SWAPS " + moves + " / " + moveLimit + " · MINIMUM " + ideal, centerX(), sy - (compact ? 9 : 12), theme().faint, compact ? 0.48f : 0.58f);
    }

    @Override protected String hintText() {
        return "Select two witnesses to exchange them; submit only when every visible testimony agrees";
    }
}
