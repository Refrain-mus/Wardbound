package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Screen-space card VFX. Lodestone's current Wardbound usage is world-space, so
 * this layer keeps card motion pinned to the GUI while the companion Lodestone
 * burst remains in the 3D scene behind/after the hand.
 */
public final class CardUiVfx {
    private CardUiVfx() {}

    public static float smooth01(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static int alpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    public static void ambient(GuiGraphics g, int width, int height, double time, int accent, float intensity, boolean reduceMotion) {
        if (intensity <= 0.01f) return;
        int count = reduceMotion ? 6 : 22;
        double speed = reduceMotion ? 0.0 : 1.0;
        for (int i = 0; i < count; i++) {
            double phase = i * 2.399963229728653 + time * (0.10 + (i % 5) * 0.014) * speed;
            double radiusX = width * (0.23 + (i % 7) * 0.027);
            double radiusY = height * (0.16 + (i % 5) * 0.024);
            double wobble = reduceMotion ? 0.0 : Math.sin(time * 0.7 + i * 1.37) * 8.0;
            float px = (float) (width * 0.5 + Math.cos(phase) * radiusX);
            float py = (float) (height * 0.5 + Math.sin(phase * 0.83) * radiusY + wobble);
            int a = Math.round((22 + (i % 4) * 10) * intensity);
            float size = (i % 6 == 0 ? 2.0f : 1.0f) * (0.8f + intensity * 0.25f);
            mote(g, px, py, size, alpha(accent, a));
        }
    }

    public static void cardAura(GuiGraphics g, int x, int y, int w, int h, double time, long seed,
                                int accent, float hover, float rarity, boolean reduceMotion) {
        float h01 = Mth.clamp(hover, 0f, 1f);
        float strength = Mth.clamp(0.28f + h01 * 0.72f + rarity * 0.35f, 0f, 1.35f);
        double pulse = reduceMotion ? 0.5 : 0.5 + 0.5 * Math.sin(time * (2.2 + rarity * 1.4) + seed * 0.17);
        int outer = (int)Math.round((18.0 + 28.0 * pulse) * strength);
        int inner = Math.round((34 + 40 * h01) * strength);
        int expand = 2 + Math.round((float) pulse * 2f + h01 * 2f);
        g.fill(x - expand, y - expand, x + w + expand, y - expand + 1, alpha(accent, outer));
        g.fill(x - expand, y + h + expand - 1, x + w + expand, y + h + expand, alpha(accent, outer));
        g.fill(x - expand, y - expand, x - expand + 1, y + h + expand, alpha(accent, outer));
        g.fill(x + w + expand - 1, y - expand, x + w + expand, y + h + expand, alpha(accent, outer));

        int count = reduceMotion ? 4 : 10 + Math.round(rarity * 5f);
        double perimeter = 2.0 * (w + h);
        for (int i = 0; i < count; i++) {
            double t = (i / (double) count + (reduceMotion ? 0.0 : time * (0.055 + rarity * 0.018))
                    + (seed & 255L) / 997.0) % 1.0;
            double p = t * perimeter;
            float px, py;
            if (p < w) { px = x + (float) p; py = y - 3; }
            else if ((p -= w) < h) { px = x + w + 3; py = y + (float) p; }
            else if ((p -= h) < w) { px = x + w - (float) p; py = y + h + 3; }
            else { p -= w; px = x - 3; py = y + h - (float) p; }
            float drift = reduceMotion ? 0f : (float) Math.sin(time * 2.0 + i * 1.71 + seed * 0.013) * (1.5f + rarity);
            int a = Math.round((44 + 72 * h01 + 22 * rarity) * strength);
            mote(g, px + drift, py, (i % 4 == 0 ? 2.0f : 1.2f) + rarity * 0.25f, alpha(accent, a));
        }

        if (h01 > 0.01f) {
            int cx = x + w / 2;
            int cy = y + h / 2;
            int rayA = Math.round(28 + 58 * h01);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3.0 + (reduceMotion ? 0.0 : time * 0.32);
                int r0 = Math.round(14 + 4 * h01);
                int r1 = Math.round(24 + 10 * h01);
                int x0 = cx + (int) (Math.cos(a) * r0);
                int y0 = cy + (int) (Math.sin(a) * r0);
                int x1 = cx + (int) (Math.cos(a) * r1);
                int y1 = cy + (int) (Math.sin(a) * r1);
                lineApprox(g, x0, y0, x1, y1, alpha(accent, rayA));
            }
        }

        if (inner > 0) {
            g.fill(x + 8, y + 7, x + w - 8, y + 8, alpha(accent, inner / 2));
        }
    }

    /**
     * Rarity-aware deal reveal. It is intentionally screen-space and visual only:
     * no offer timing, selection window or card legality is changed.
     */
    public static void revealSweep(GuiGraphics g, int x, int y, int w, int h, float progress,
                                   int accent, float rarity, boolean dangerous, boolean reduceMotion) {
        float p = smooth01(progress);
        if (p >= 0.999f) return;
        int veil = Math.round((1f - p) * (reduceMotion ? 120f : 205f));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, alpha(0xFF050408, veil));

        // The card reads from the centre outward like a page being exposed under a shutter.
        int half = Math.round((w / 2f) * p);
        int cx = x + w / 2;
        if (half > 0) {
            int edgeA = Math.round((1f - p) * (84 + rarity * 110));
            g.fill(cx - half, y - 2, cx - half + 1, y + h + 2, alpha(accent, edgeA));
            g.fill(cx + half - 1, y - 2, cx + half, y + h + 2, alpha(accent, edgeA));
        }

        if (rarity >= 0.54f) {
            float flare = 1f - Math.abs(p - 0.58f) / 0.42f;
            flare = Mth.clamp(flare, 0f, 1f);
            int a = Math.round(flare * (55 + rarity * 95));
            int expand = 2 + Math.round(rarity * 4f);
            g.fill(x - expand, y - expand, x + w + expand, y - expand + 1, alpha(accent, a));
            g.fill(x - expand, y + h + expand - 1, x + w + expand, y + h + expand, alpha(accent, a));
            if (dangerous) {
                g.fill(x - expand, y - expand, x - expand + 1, y + h + expand, alpha(0xFF9B2034, Math.round(a * 0.85f)));
                g.fill(x + w + expand - 1, y - expand, x + w + expand, y + h + expand, alpha(0xFF9B2034, Math.round(a * 0.85f)));
            }
        }
    }

    public static void selectionBurst(GuiGraphics g, int cx, int cy, double time, float progress,
                                      int accent, boolean dangerous, boolean reduceMotion) {
        float p = smooth01(progress);
        if (p <= 0f) return;
        float inv = 1f - p;
        int ringR = Math.round(12 + p * 74);
        int a = Math.round(180 * inv);
        ring(g, cx, cy, ringR, alpha(accent, a));
        if (dangerous) ring(g, cx, cy, Math.max(4, ringR - 8), alpha(0xFFB32035, Math.round(a * 0.75f)));
        int count = reduceMotion ? 8 : 22;
        for (int i = 0; i < count; i++) {
            double ang = i * (Math.PI * 2.0 / count) + (reduceMotion ? 0.0 : time * 0.45);
            float radius = 8f + p * (38f + (i % 5) * 7f);
            float px = cx + (float) Math.cos(ang) * radius;
            float py = cy + (float) Math.sin(ang) * radius * 0.74f;
            mote(g, px, py, i % 5 == 0 ? 2.8f : 1.5f, alpha(accent, Math.round((210 - i * 3) * inv)));
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        if (((color >>> 24) & 255) <= 0 || r <= 0) return;
        int points = Math.max(18, Math.min(72, r * 2));
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2.0 / points;
            mote(g, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r * 0.72f, 1.15f, color);
        }
    }

    private static void lineApprox(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i += 2) {
            float t = i / (float) steps;
            float x = Mth.lerp(t, x0, x1);
            float y = Mth.lerp(t, y0, y1);
            mote(g, x, y, 1f, color);
        }
    }

    private static void mote(GuiGraphics g, float x, float y, float size, int color) {
        if (((color >>> 24) & 255) <= 0) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float s = Math.max(0.75f, size);
        g.pose().scale(s, s, 1f);
        g.fill(-1, -1, 1, 1, color);
        g.pose().popPose();
    }
}
