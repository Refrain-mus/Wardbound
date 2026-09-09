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
 * The Epitaph: infer a repeated transformation in a short runic inscription.
 * Nothing moves and nothing is memorised then hidden. Three witness lines remain
 * visible; the fourth is damaged. The player reconstructs its missing glyphs by
 * working out how each complete line transforms into the next.
 */
@OnlyIn(Dist.CLIENT)
public final class EpitaphScreen extends BaseMinigameScreen {
    private static final int ROWS = 4;
    private static final int COLS = 5;
    private static final int CELL = 30;
    private static final int GAP = 8;

    private final boolean mirrorVerse;
    private final boolean falseInk;
    private final boolean redactedWitness;
    private final boolean doubleGrammar;
    private final boolean toneWitness;
    private final int rounds;

    private int palette;
    private int grammarAlphabet;
    private int shift;
    private int runeDelta;
    private int secondShift;
    private int secondRuneDelta;
    private int[][] verse;
    private int[] finalAnswer;
    private boolean[][] hidden;
    private int[] editable;
    private int focus;
    private float flash;

    public EpitaphScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.epitaph"), msg);
        boolean[] v = pickVariants(3, 331);
        // The base grammar has to be learned before the puzzle starts deleting
        // or mirroring evidence. Early Epitaphs now teach the actual rule first.
        mirrorVerse = v[0] && (familiarity >= 2 || value >= 56);
        falseInk = v[1] && (familiarity >= 3 || value >= 66);
        redactedWitness = v[2] && (familiarity >= 4 || value >= 76);
        doubleGrammar = expertVariant(349, 3, 82, 0.42f);
        toneWitness = expertVariant(350, 2, 74, 0.46f);
        if (mirrorVerse) modifierLabels.add("mirrored grammar");
        if (falseInk) modifierLabels.add("false ink");
        if (redactedWitness) modifierLabels.add("redacted witness");
        if (doubleGrammar) modifierLabels.add("double grammar");
        if (toneWitness) modifierLabels.add("spoken rune");
        if (makerBindsLogic()) modifierLabels.add("maker · bound witness");
        if (cardBindsOne()) modifierLabels.add("card · bound witness");
        rounds = wobble(value >= 74 && difficulty > 1.04f ? 3 : 2, 0, 337, 2, 3);
        buildRound();
    }

    @Override
    protected Theme createTheme() { return Theme.augury(); }

    @Override
    protected int panelWidth() { return 344; }

    @Override
    protected int panelHeight() { return 310; }

    @Override
    protected float timeBudget() { return super.timeBudget() * 1.12f; }

    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING && a != Anomaly.UNLIT;
    }

    private int transformedRune(int[] row, int col, int testShift, int testDelta) {
        int source = Math.floorMod(col - testShift, COLS);
        if (mirrorVerse) source = COLS - 1 - source;
        return Math.floorMod(row[source] + testDelta, grammarAlphabet);
    }

    private int grammarMatchCount(int[] row, int expectedShift, int expectedDelta) {
        int[] witness = new int[COLS];
        for (int c = 0; c < COLS; c++) witness[c] = transformedRune(row, c, expectedShift, expectedDelta);
        int matches = 0;
        for (int sft = 1; sft <= 2; sft++) {
            for (int d = 1; d < grammarAlphabet; d++) {
                boolean same = true;
                for (int c = 0; c < COLS; c++) {
                    if (transformedRune(row, c, sft, d) != witness[c]) { same = false; break; }
                }
                if (same) matches++;
            }
        }
        return matches;
    }

    private boolean fixedGlyphHint() {
        return heartHint() || (cardRevealsOne() && progress == 0);
    }

    private void buildRound() {
        Random rng = new Random(seed ^ 0x455049544150484CL ^ (long) progress * 104729L);
        grammarAlphabet = Mth.clamp(5 + (value >= 78 ? 1 : 0), 5, Math.min(7, Sigils.TILE.length));
        palette = Math.min(Sigils.TILE.length, grammarAlphabet + (falseInk ? 2 : 0));
        shift = 1 + rng.nextInt(2);
        runeDelta = 1 + rng.nextInt(Math.max(1, grammarAlphabet - 1));

        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < grammarAlphabet; i++) pool.add(i);
        Collections.shuffle(pool, rng);
        int[] base = new int[COLS];
        for (int c = 0; c < COLS; c++) base[c] = pool.get(c % pool.size());

        // Never ship an inscription with two equally valid laws. The player is
        // supposed to infer a rule, not guess which hidden rule the generator meant.
        List<int[]> laws = new ArrayList<>();
        for (int sft = 1; sft <= 2; sft++) {
            for (int d = 1; d < grammarAlphabet; d++) laws.add(new int[]{sft, d});
        }
        Collections.shuffle(laws, rng);
        for (int[] law : laws) {
            if (grammarMatchCount(base, law[0], law[1]) == 1) {
                shift = law[0];
                runeDelta = law[1];
                break;
            }
        }

        if (doubleGrammar) {
            secondShift = shift == 1 ? 2 : 1;
            do secondRuneDelta = 1 + rng.nextInt(Math.max(1, grammarAlphabet - 1));
            while (grammarAlphabet > 2 && secondRuneDelta == runeDelta);
        } else {
            secondShift = shift;
            secondRuneDelta = runeDelta;
        }

        verse = new int[ROWS][COLS];
        System.arraycopy(base, 0, verse[0], 0, COLS);
        for (int r = 1; r < ROWS; r++) {
            // Expert verses alternate A/B/A. The final damaged line therefore
            // uses grammar A, which the player has already seen in row 0 -> 1.
            boolean grammarB = doubleGrammar && (r % 2 == 0);
            int activeShift = grammarB ? secondShift : shift;
            int activeDelta = grammarB ? secondRuneDelta : runeDelta;
            for (int c = 0; c < COLS; c++) {
                int source = Math.floorMod(c - activeShift, COLS);
                if (mirrorVerse) source = COLS - 1 - source;
                verse[r][c] = Math.floorMod(verse[r - 1][source] + activeDelta, grammarAlphabet);
            }
        }
        finalAnswer = verse[ROWS - 1].clone();

        hidden = new boolean[ROWS][COLS];
        List<Integer> targets = new ArrayList<>();
        for (int c = 0; c < COLS; c++) targets.add(c);
        Collections.shuffle(targets, rng);
        // First encounter teaches the grammar with one missing glyph. Once the
        // player has seen Epitaph, later wards may redact two or three glyphs.
        int holes = familiarity <= 1 ? 1 : (value >= 68 ? 3 : 2);
        editable = new int[holes];
        for (int i = 0; i < holes; i++) {
            int c = targets.get(i);
            editable[i] = c;
            hidden[ROWS - 1][c] = true;
        }
        if (redactedWitness || makerBindsLogic() || cardBindsOne()) {
            int clueRow = 1 + rng.nextInt(2);
            List<Integer> clueCols = new ArrayList<>();
            for (int c = 0; c < COLS; c++) if (!hidden[clueRow][c]) clueCols.add(c);
            if (!clueCols.isEmpty()) hidden[clueRow][clueCols.get(rng.nextInt(clueCols.size()))] = true;
        }

        // Entries live in the damaged final line. False Ink is now a real deduction
        // layer: two glyphs exist in the ink well but can never be produced by the
        // grammar, so blindly cycling carries a meaningful cost in search space.
        for (int c : editable) {
            int correct = finalAnswer[c];
            int wrong;
            do wrong = rng.nextInt(Math.max(1, palette)); while (palette > 1 && wrong == correct);
            verse[ROWS - 1][c] = wrong;
        }
        if (fixedGlyphHint() && editable.length > 0) {
            int c = editable[0];
            verse[ROWS - 1][c] = finalAnswer[c];
        }
        focus = fixedGlyphHint() && editable.length > 1 ? 1 : 0;
        flash = 0f;
    }

    private int correctAt(int row, int col) {
        if (row == ROWS - 1 && finalAnswer != null) return finalAnswer[col];
        return verse[row][col];
    }

    private int boardX() { return centerX() - (COLS * CELL + (COLS - 1) * GAP) / 2; }
    private int boardY() { return contentCenterY() - 92; }

    private int slotAt(double mx, double my) {
        int y = boardY() + (ROWS - 1) * (CELL + GAP);
        for (int i = 0; i < editable.length; i++) {
            int c = editable[i];
            int x = boardX() + c * (CELL + GAP);
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) return i;
        }
        return -1;
    }

    private boolean submitArea(double mx, double my) {
        int y = boardY() + ROWS * (CELL + GAP) + 31;
        return mx >= centerX() - 48 && mx <= centerX() + 48 && my >= y && my <= y + 18;
    }

    private void playWitnessTone(int slot) {
        if (!toneWitness || slot < 0 || slot >= editable.length) return;
        int c = editable[slot];
        float pitch = 0.68f + finalAnswer[c] * 0.075f;
        Sfx.play(WardSounds.VEILSTEP_CHIME, 0.20f, pitch);
    }

    private void cycle(int dir) {
        if (editable.length == 0) return;
        if (fixedGlyphHint() && focus == 0) {
            showBanner(cardRevealsOne() && !heartHint() ? "The card has fixed this glyph" : "The heart has fixed this glyph", theme().dim, 520);
            return;
        }
        int c = editable[focus];
        if (toneWitness) playWitnessTone(focus);
        verse[ROWS - 1][c] = Math.floorMod(verse[ROWS - 1][c] + dir, palette);
        Sfx.play(WardSounds.STONE_TURN, 0.32f, 0.68f + verse[ROWS - 1][c] * 0.075f);
    }

    private void submit() {
        boolean correct = true;
        for (int c : editable) if (verse[ROWS - 1][c] != correctAt(ROWS - 1, c)) correct = false;
        if (correct) {
            progress++;
            Sfx.mark(progress + 2);
            burst(centerX(), contentCenterY(), COL_GOOD);
            if (progress >= rounds) {
                float perf = Mth.clamp(1f - mistakes * 0.12f, 0.45f, 1f);
                succeed(perf);
            } else {
                showBanner("The inscription accepts one restored line", COL_GOOD, 720);
                buildRound();
            }
        } else {
            flash = 0.45f;
            Sfx.markBad();
            if (loseLife("The grammar rejects the false line")) showBanner("The erased line is still wrong", COL_BAD, 760);
        }
    }

    @Override
    protected void step(float dt) { if (flash > 0f) flash = Math.max(0f, flash - dt); }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        int slot = slotAt(mx, my);
        if (slot >= 0) {
            if (fixedGlyphHint() && slot == 0) {
                showBanner(cardRevealsOne() && !heartHint() ? "The card has already written this glyph" : "The heart has already written this glyph", theme().dim, 550);
                return true;
            }
            focus = slot;
            cycle(1);
            return true;
        }
        if (submitArea(mx, my)) { submit(); return true; }
        return false;
    }

    @Override
    protected boolean onKey(int key) {
        if (key == 263 || key == 65) { focus = Math.floorMod(focus - 1, editable.length); playWitnessTone(focus); return true; }
        if (key == 262 || key == 68) { focus = Math.floorMod(focus + 1, editable.length); playWitnessTone(focus); return true; }
        if (key == 265 || key == 87) { cycle(1); return true; }
        if (key == 264 || key == 83) { cycle(-1); return true; }
        if (isActionKey(key)) { submit(); return true; }
        return false;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bx = boardX(), by = boardY();
        progressDots(centerX(), by - 25, rounds, progress);
        smallCentered(g, "COMPARE I → II · FIND BOTH PARTS OF THE SAME LAW", centerX(), by - 16, theme().dim, 0.72f);
        smallCentered(g, "1 POSITION SHIFT   ·   2 STEP THROUGH RUNE ORDER", centerX(), by - 7, theme().faint, 0.64f);
        for (int r = 0; r < ROWS; r++) {
            String numeral = switch (r) { case 0 -> "I"; case 1 -> "II"; default -> "III"; };
            String rowTag = r == ROWS - 1 ? "RESTORE IV" : "WITNESS " + numeral;
            smallCentered(g, rowTag, bx - 38, by + r * (CELL + GAP) + 10,
                    r == ROWS - 1 ? theme().accent : theme().faint, 0.58f);
            for (int c = 0; c < COLS; c++) {
                int x = bx + c * (CELL + GAP);
                int y = by + r * (CELL + GAP);
                boolean edit = r == ROWS - 1 && hidden[r][c];
                int slot = -1;
                if (edit) for (int i = 0; i < editable.length; i++) if (editable[i] == c) slot = i;
                boolean focused = edit && slot == focus;
                int frame = focused ? theme().accent : edit ? 0xFF826551 : theme().faint;
                if (flash > 0f && edit) frame = COL_BAD;
                p.rect(x, y, x + CELL, y + CELL, 0x77100D11);
                p.outline(x, y, CELL, CELL, focused ? 2 : 1, frame);

                boolean clueRedacted = r < ROWS - 1 && hidden[r][c];
                if (clueRedacted) {
                    p.rect(x + 6, y + 13, x + CELL - 6, y + 17, 0xFF2B2024);
                    p.rect(x + 9, y + 10, x + CELL - 9, y + 12, theme().faint);
                } else {
                    int rune = verse[r][c];
                    int body = edit ? (focused ? theme().accent : 0xFFD7C4A2) : 0xFFB8A998;
                    Sigils.drawCentered(p, Sigils.TILE[rune % Sigils.TILE.length], x + CELL / 2, y + CELL / 2, 2,
                            body, Painter.withAlpha(body, 0x77), 0x55000000);
                }
            }
            if (r < ROWS - 1) {
                String law = doubleGrammar && r == 1 ? "LAW B" : (doubleGrammar ? "LAW A" : "SAME LAW");
                int boardRight = bx + COLS * CELL + (COLS - 1) * GAP;
                smallCentered(g, law, boardRight + 25, by + r * (CELL + GAP) + CELL + 1,
                        law.equals("LAW B") ? 0xFFA995D7 : theme().accent, 0.60f);
                p.line(bx - 8, by + r * (CELL + GAP) + CELL + 4,
                        boardRight + 8, by + r * (CELL + GAP) + CELL + 4, 1, 0x332F2630);
            }
        }
        int sy = by + ROWS * (CELL + GAP) + 1;
        int inkW = palette * 17;
        smallCentered(g, falseInk ? "RUNE ORDER · TWO OUTSIDER GLYPHS ARE FALSE INK" : "RUNE ORDER · CHANGES MOVE ALONG THIS ORDER",
                centerX(), sy + 1, falseInk ? 0xFFD1A38E : theme().faint, 0.64f);
        int ix = centerX() - inkW / 2;
        for (int i = 0; i < palette; i++) {
            int cx = ix + i * 17 + 8;
            Sigils.drawCentered(p, Sigils.TILE[i % Sigils.TILE.length], cx, sy + 15, 1,
                    0xFFCDBFA9, Painter.withAlpha(theme().accent, 0x44), 0x44000000);
            smallCentered(g, Integer.toString(i), cx, sy + 23, theme().faint, 0.48f);
        }
        int submitY = sy + 30;
        p.rect(centerX() - 48, submitY, centerX() + 48, submitY + 18, 0xFF211A21);
        p.outline(centerX() - 48, submitY, 96, 18, 1, theme().accent);
        smallCentered(g, "SEAL THE LINE", centerX(), submitY + 6, 0xFFE2D5C3, 0.82f);
        smallCentered(g, "click a redacted rune to cycle · arrows/WASD also work", centerX(), submitY + 27, theme().dim, 0.72f);
        if (toneWitness) smallCentered(g, "the witness tone names the true rune; cycling plays the ink tone", centerX(),
                submitY + 38, theme().faint, 0.64f);
    }

    @Override
    protected String hintText() {
        if (doubleGrammar && toneWitness) return "The laws alternate A/B/A. The final line uses A again; the quiet witness tone gives an independent rune channel";
        if (doubleGrammar) return "The laws alternate A/B/A: compare the first transition to the damaged final transition, not the middle law";
        if (toneWitness) return "Selecting a missing rune sounds its true pitch; cycling sounds the current ink pitch, so sight and hearing can cross-check";
        if (falseInk && mirrorVerse)
            return "The same shift, mirror and rune-change repeats each line. Read the ink well left-to-right as a cycle; two glyphs are decoys.";
        if (falseInk)
            return "The same shift and rune-change repeats each line. Read the ink well left-to-right as a cycle; two glyphs are decoys.";
        return mirrorVerse
                ? "Compare Witness I to II: positions mirror/shift and every rune moves the same number of steps in RUNE ORDER. Apply that law again."
                : "Compare Witness I to II: positions shift and every rune moves the same number of steps in RUNE ORDER. Apply that exact law again.";
    }
}
