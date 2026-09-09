package dev.marrowseal.wardbound.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Hand-drawn pixel art, stored as text so it lives in the jar with no resource files, no atlas and
 * no texture binding. Every glyph is painted as solid blocks through {@link Painter}, which means
 * it costs a handful of quads inside the batch and scales to any size without blurring.
 *
 * <p>Legend: {@code #} solid, {@code +} half-tone, {@code .} empty.
 */
@OnlyIn(Dist.CLIENT)
public final class Sigils {

    private Sigils() {
    }

    // ------------------------------------------------------------------ 9x9 tile sigils

    private static final String[] EYE = {
            ".........",
            "...###...",
            ".##+++##.",
            "#++###++#",
            "#+#####+#",
            "#++###++#",
            ".##+++##.",
            "...###...",
            "........."};

    private static final String[] TRIDENT = {
            "#...#...#",
            "#...#...#",
            "#...#...#",
            "##..#..##",
            ".#######.",
            "....#....",
            "....#....",
            "...###...",
            "..#####.."};

    private static final String[] SPIRAL = {
            "..#####..",
            ".#+++++#.",
            "#+.###.+#",
            "#+#+++#+#",
            "#+#+#.#+#",
            "#+#+###+#",
            "#+#+...+#",
            ".#++++##.",
            "..####..."};

    private static final String[] TENTACLE = {
            "....##...",
            "..##++#..",
            ".#+...#..",
            ".#...##..",
            ".#+.##...",
            ".##+#....",
            "..###....",
            "...##+...",
            "..####..."};

    private static final String[] STAR = {
            "....#....",
            "...###...",
            "#########",
            ".#######.",
            "..#####..",
            "..##+##..",
            ".##...##.",
            ".#.....#.",
            "........."};

    private static final String[] GATE = {
            ".#######.",
            ".#+++++#.",
            ".#.###.#.",
            ".#.#+#.#.",
            ".#.#+#.#.",
            ".#.#+#.#.",
            ".#.#+#.#.",
            ".#######.",
            "#########"};

    private static final String[] CLAW = {
            "#...#...#",
            "#...#...#",
            "#+..#..+#",
            "##..#..##",
            ".#+.#.+#.",
            ".#..#..#.",
            "..#####..",
            "...###...",
            "....#...."};

    private static final String[] ANKH = {
            "..#####..",
            ".#+...+#.",
            ".#.....#.",
            "..#####..",
            "..##+##..",
            ".#######.",
            "....#....",
            "....#....",
            "...###..."};

    private static final String[] IDOL = {
            "..#####..",
            ".#+++++#.",
            "#.##.##.#",
            "#.##.##.#",
            "#+++++++#",
            "#..###..#",
            ".#+#+#+#.",
            ".#######.",
            "..#.#.#.."};

    private static final String[] WAVE = {
            ".........",
            "..##...##",
            ".#++#.#++",
            "#+..#+#..",
            "#....#...",
            ".........",
            "..##...##",
            ".#++#.#++",
            "#+..#+#.."};

    private static final String[] HAND = {
            ".#.#.#...",
            ".#.#.#.#.",
            ".#.#.#.#.",
            ".###+###.",
            "#+#####+#",
            "#++###++#",
            ".#+++++#.",
            "..#####..",
            "...###..."};

    private static final String[] MOON = {
            "...###...",
            ".##+++##.",
            "##+..##..",
            "#+..##...",
            "#+..#....",
            "#+..##...",
            "##+..##..",
            ".##+++##.",
            "...###..."};

    /** The tile sigils, used by the grids, the cipher rings and the scattered background marks. */
    public static final String[][] TILE = {
            EYE, TRIDENT, SPIRAL, TENTACLE, STAR, GATE, CLAW, ANKH, IDOL, WAVE, HAND, MOON};

    // ------------------------------------------------------------------ frame ornaments

    /** Curled arm for the top-left corner. The other corners are mirrored at draw time. */
    private static final String[] CORNER_TENTACLE = {
            "#####.....",
            "#++.......",
            "#+.##.....",
            "#..#+#....",
            "#..#.##...",
            ".#.#..#+..",
            "..##..+#..",
            "...#...##.",
            "........#.",
            ".........."};

    private static final String[] CORNER_SCALE = {
            "######....",
            "#++++#....",
            "#+##+#....",
            "#+#.##....",
            "#+#.......",
            "##........",
            "#.........",
            "#.........",
            "..........",
            ".........."};

    private static final String[] CORNER_BONE = {
            "###.......",
            "#+#.......",
            "###.##....",
            "..#.#+#...",
            "..#.###...",
            "..#...#...",
            "..#...#...",
            "......###.",
            "......#+#.",
            "......###."};

    private static final String[] CORNER_RIVET = {
            "#######...",
            "#+++++#...",
            "#+###+#...",
            "#+#.#+#...",
            "#+###+#...",
            "#+++++#...",
            "#######...",
            "..........",
            "..........",
            ".........."};

    public static final String[][] CORNERS = {CORNER_TENTACLE, CORNER_SCALE, CORNER_BONE, CORNER_RIVET};

    /** Small watching eye used in the title bar. */
    private static final String[] HEADER_EYE = {
            "..#####..",
            ".#+++++#.",
            "#++###++#",
            "#+#####+#",
            "#++###++#",
            ".#+++++#.",
            "..#####.."};

    // ------------------------------------------------------------------ drawing

    /**
     * Text form of a glyph at a given orientation. Used by the mirror lock to make sure the four
     * candidates really do read differently: several sigils are symmetric about one axis, and a
     * plate with two identical cards has no right answer.
     */
    public static String signature(String[] glyph, boolean flipX, boolean flipY) {
        int rows = glyph.length, cols = glyph[0].length();
        StringBuilder sb = new StringBuilder(rows * cols);
        for (int r = 0; r < rows; r++) {
            String row = glyph[flipY ? rows - 1 - r : r];
            for (int c = 0; c < cols; c++) sb.append(row.charAt(flipX ? cols - 1 - c : c));
        }
        return sb.toString();
    }

    /** True when all four mirrorings of this glyph look different. */
    public static boolean fullyAsymmetric(String[] glyph) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 4; i++) {
            if (!seen.add(signature(glyph, (i & 1) != 0, (i & 2) != 0))) return false;
        }
        return true;
    }

    public static int width(String[] glyph) {
        return glyph[0].length();
    }

    public static int height(String[] glyph) {
        return glyph.length;
    }

    /**
     * Draws a glyph with its top-left corner at (x, y).
     *
     * @param scale  size of one glyph pixel
     * @param body   colour of {@code #}
     * @param half   colour of {@code +}; pass 0 to skip half-tones
     * @param shadow drop shadow colour offset one scale unit down-right; pass 0 for none
     */
    public static void draw(Painter p, String[] glyph, int x, int y, int scale,
                            int body, int half, int shadow) {
        if (shadow != 0) paint(p, glyph, x + scale, y + scale, scale, shadow, shadow, false, false);
        paint(p, glyph, x, y, scale, body, half, false, false);
    }

    /** Same as {@link #draw} but centred on (cx, cy). */
    public static void drawCentered(Painter p, String[] glyph, int cx, int cy, int scale,
                                    int body, int half, int shadow) {
        int w = width(glyph) * scale;
        int h = height(glyph) * scale;
        draw(p, glyph, cx - w / 2, cy - h / 2, scale, body, half, shadow);
    }

    /** Corner ornament, mirrored into whichever corner is asked for. */
    public static void corner(Painter p, String[] glyph, int x, int y, int scale,
                              int body, int half, boolean flipX, boolean flipY) {
        paint(p, glyph, x, y, scale, body, half, flipX, flipY);
    }

    /** Centred draw with optional mirroring, used by the mirror-sigil lock. */
    public static void drawCenteredFlipped(Painter p, String[] glyph, int cx, int cy, int scale,
                                           int body, int half, int shadow,
                                           boolean flipX, boolean flipY) {
        int w = width(glyph) * scale;
        int h = height(glyph) * scale;
        int x = cx - w / 2, y = cy - h / 2;
        if (shadow != 0) paint(p, glyph, x + scale, y + scale, scale, shadow, shadow, flipX, flipY);
        paint(p, glyph, x, y, scale, body, half, flipX, flipY);
    }

    public static void headerEye(Painter p, int cx, int cy, int scale, int body, int half, int shadow) {
        drawCentered(p, HEADER_EYE, cx, cy, scale, body, half, shadow);
    }

    private static void paint(Painter p, String[] glyph, int x, int y, int scale,
                              int body, int half, boolean flipX, boolean flipY) {
        int rows = glyph.length;
        int cols = glyph[0].length();
        for (int r = 0; r < rows; r++) {
            String row = glyph[flipY ? rows - 1 - r : r];
            for (int c = 0; c < cols; c++) {
                char ch = row.charAt(flipX ? cols - 1 - c : c);
                int colour;
                if (ch == '#') {
                    colour = body;
                } else if (ch == '+') {
                    if (half == 0) continue;
                    colour = half;
                } else {
                    continue;
                }
                p.rect(x + c * scale, y + r * scale, x + (c + 1) * scale, y + (r + 1) * scale, colour);
            }
        }
    }
}
