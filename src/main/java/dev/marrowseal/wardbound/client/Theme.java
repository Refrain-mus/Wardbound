package dev.marrowseal.wardbound.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Palette and frame flavour for one lock. Every minigame picks its own, so the eight locks no
 * longer look like the same window with a different toy inside: the drum sits in oiled oak, the
 * forge in scorched iron, the seal in void basalt, and so on.
 *
 * <p>Most values are derived from three colours so a new theme is one line.
 */
@OnlyIn(Dist.CLIENT)
public final class Theme {

    /** Interior fill pattern for the panel. */
    public static final int PATTERN_DITHER = 0;
    public static final int PATTERN_SCANLINE = 1;
    public static final int PATTERN_PLANK = 2;
    public static final int PATTERN_PLAIN = 3;

    public final int edge;
    public final int frame;
    public final int frameLite;
    public final int frameDark;

    public final int panel;
    public final int panelLite;
    public final int panelDark;

    public final int well;
    public final int wellEdge;

    public final int text;
    public final int dim;
    public final int faint;

    public final int accent;
    public final int accentLite;
    public final int accentDark;

    public final int good;
    public final int bad;

    /** Index into {@link Sigils#CORNERS}. */
    public final int corner;
    /** One of the PATTERN_* constants. */
    public final int pattern;

    private Theme(int frame, int panel, int accent, int text, int corner, int pattern) {
        this.frame = frame;
        this.frameLite = Painter.lighten(frame, 0.38f);
        this.frameDark = Painter.darken(frame, 0.45f);
        this.edge = Painter.darken(panel, 0.72f) | 0xFF000000;

        this.panel = panel;
        this.panelLite = Painter.lighten(panel, 0.13f);
        this.panelDark = Painter.darken(panel, 0.30f);

        this.well = Painter.darken(panel, 0.52f) | 0xFF000000;
        this.wellEdge = Painter.darken(panel, 0.68f) | 0xFF000000;

        this.text = text;
        this.dim = Painter.mix(text, this.panel, 0.42f) | 0xFF000000;
        this.faint = Painter.mix(text, this.panel, 0.70f) | 0xFF000000;

        this.accent = accent;
        this.accentLite = Painter.lighten(accent, 0.42f);
        this.accentDark = Painter.darken(accent, 0.45f);

        this.good = 0xFF8FC96E;
        this.bad = 0xFFCB5340;

        this.corner = corner;
        this.pattern = pattern;
    }

    public static Theme of(int frame, int panel, int accent, int text, int corner, int pattern) {
        return new Theme(frame, panel, accent, text, corner, pattern);
    }

    /** Accessibility palette that keeps the lock's accent but raises luminance contrast. */
    public static Theme highContrast(Theme base) {
        int accent = Painter.lighten(base.accent, 0.48f);
        return of(0xFFB8B8B8, 0xFF050607, accent, 0xFFFFFFFF, base.corner, PATTERN_PLAIN);
    }

    // ------------------------------------------------------------------ the eight looks

    /** Drum. Oiled oak and warm brass. */
    public static Theme oak() {
        return of(0xFF8A6A31, 0xFF221A13, 0xFFD9A441, 0xFFEEDDBB, 0, PATTERN_PLANK);
    }

    /** Timing. Scorched iron, soot and ember light. */
    public static Theme forge() {
        return of(0xFF6E5A50, 0xFF1E1512, 0xFFE2733A, 0xFFF0DCCB, 3, PATTERN_DITHER);
    }

    /** Memory. End basalt, cold violet wards. */
    public static Theme basalt() {
        return of(0xFF54486E, 0xFF17131F, 0xFF9B6FD9, 0xFFE4DAF2, 1, PATTERN_DITHER);
    }

    /** Runes. Drowned bronze, verdigris glow. */
    public static Theme verdigris() {
        return of(0xFF3F6B60, 0xFF111A18, 0xFF54C4A6, 0xFFD8F0E8, 1, PATTERN_SCANLINE);
    }

    /** Cipher. Old ivory and bone, faded gold. */
    public static Theme bone() {
        return of(0xFF8C8367, 0xFF1D1B16, 0xFFD8C27A, 0xFFF2E9D2, 2, PATTERN_PLAIN);
    }

    /** Pressure. Riveted copper under steam. */
    public static Theme copper() {
        return of(0xFF9A5A34, 0xFF1F1610, 0xFFE08A46, 0xFFF3DFCB, 3, PATTERN_DITHER);
    }

    /** Resonance. Deep water, phosphor green trace. */
    public static Theme abyss() {
        return of(0xFF2E5570, 0xFF0D1319, 0xFF5FD6A0, 0xFFCFE6F0, 0, PATTERN_SCANLINE);
    }

    /** Balance. Cold slate and a pale green spirit level. */
    public static Theme slate() {
        return of(0xFF5C6672, 0xFF14171B, 0xFF8FD3C4, 0xFFDCE6EC, 2, PATTERN_PLANK);
    }

    /** Constellation. Ink-dark sky, cold starlight. */
    public static Theme nightSky() {
        return of(0xFF3B4E7A, 0xFF0B0D16, 0xFF8FA8FF, 0xFFDCE2FA, 1, PATTERN_PLAIN);
    }

    /** Mirror. Stained vellum and a violet scrying glass. */
    public static Theme vellum() {
        return of(0xFF9A8B62, 0xFF1A1712, 0xFFB48CE0, 0xFFF0E6CE, 3, PATTERN_SCANLINE);
    }

    /** Rootway. Wet granite, deep moss, and something pale in the cracks. */
    public static Theme rootway() {
        return of(0xFF5A6152, 0xFF10140F, 0xFF8FA678, 0xFFDCE2CE, 2, PATTERN_DITHER);
    }

    /** Vessel. Wet stone and a green that is not quite algae. */
    public static Theme vessel() {
        return of(0xFF5C6B62, 0xFF0D1513, 0xFF7FB89A, 0xFFDCE6DE, 2, PATTERN_DITHER);
    }

    /** Gyre. Cold steel orrery, one brass needle. */
    public static Theme gyre() {
        return of(0xFF6B7280, 0xFF121418, 0xFFD9A441, 0xFFE8EAF0, 3, PATTERN_DITHER);
    }

    /** Augury. Verdigris on old bronze, read by candlelight. */
    public static Theme augury() {
        return of(0xFF6E7A6A, 0xFF14181A, 0xFF9FC2A8, 0xFFE4E7D9, 0, PATTERN_DITHER);
    }

    /** Keyway. Blued steel housing, brass pins, machine oil. */
    public static Theme keyway() {
        return of(0xFF7A7168, 0xFF17161A, 0xFFC49A4E, 0xFFE7E1D4, 3, PATTERN_DITHER);
    }

    /** Black Lattice. Oil-dark cyclopean stone with sickly green incisions. */
    public static Theme blackLattice() {
        return of(0xFF4A554E, 0xFF090D0B, 0xFF93B46F, 0xFFDCE6D0, 1, PATTERN_DITHER);
    }

    /** Parallax. Green-black glass over bruised violet geometry. */
    public static Theme parallax() {
        return of(0xFF536267, 0xFF0A0D10, 0xFF76C7B4, 0xFFD9E8E6, 1, PATTERN_SCANLINE);
    }

    /** Pulse. Wet flesh and bruised red. */
    public static Theme flesh() {
        return of(0xFF7A3A44, 0xFF1B1013, 0xFFD4576B, 0xFFF3D8DC, 0, PATTERN_DITHER);
    }
}
