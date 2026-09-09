package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Why this class exists.
 *
 * <p>{@code GuiGraphics.fill} is a convenient one-liner but on 1.20.1 it is not batched: every
 * single call opens a buffer, writes four vertices, closes it and issues a draw call. The old
 * screens drew circles and rings one pixel at a time, so a single dial was several thousand draw
 * calls per frame and the whole game dropped to 15-20 fps while a lock was open.
 *
 * <p>Painter queues every rectangle into two flat arrays and empties the whole queue in ONE
 * {@code Tesselator} pass. Ten thousand rectangles cost one draw call instead of ten thousand.
 *
 * <p>The only rule: text and other vanilla drawing is <b>not</b> queued, so anything drawn through
 * {@code GuiGraphics} directly must be preceded by {@link #flush()} or it will end up underneath
 * the shapes. {@link BaseMinigameScreen} wraps the text calls so subclasses do not have to think
 * about it.
 */
@OnlyIn(Dist.CLIENT)
public final class Painter {

    /** x1, y1, x2, y2 per rectangle. */
    private float[] coords = new float[4 * 4096];
    private int[] colors = new int[4096];
    private int count;

    private GuiGraphics graphics;

    /** Rectangles are snapped to this grid, which is what gives the UI its chunky pixel-art look. */
    private int pixel = 1;

    public void bind(GuiGraphics g) {
        this.graphics = g;
        this.count = 0;
    }

    /** Size of one "art pixel". 2 means everything lands on an even 2x2 grid. */
    public void setPixel(int p) {
        this.pixel = Math.max(1, p);
    }

    public int pixel() {
        return pixel;
    }

    // ------------------------------------------------------------------ queue

    public void rect(float x1, float y1, float x2, float y2, int argb) {
        if ((argb >>> 24) == 0) return;
        if (x2 < x1) {
            float t = x1;
            x1 = x2;
            x2 = t;
        }
        if (y2 < y1) {
            float t = y1;
            y1 = y2;
            y2 = t;
        }
        if (x2 - x1 < 0.01f || y2 - y1 < 0.01f) return;

        if (count == colors.length) grow();
        int i = count * 4;
        coords[i] = x1;
        coords[i + 1] = y1;
        coords[i + 2] = x2;
        coords[i + 3] = y2;
        colors[count] = argb;
        count++;
    }

    /** Rectangle given as position + size. */
    public void box(int x, int y, int w, int h, int argb) {
        rect(x, y, x + w, y + h, argb);
    }

    /** One art-pixel block, snapped to the pixel grid. */
    public void px(int x, int y, int argb) {
        rect(x, y, x + pixel, y + pixel, argb);
    }

    private void grow() {
        int n = colors.length * 2;
        float[] nc = new float[n * 4];
        int[] ncol = new int[n];
        System.arraycopy(coords, 0, nc, 0, coords.length);
        System.arraycopy(colors, 0, ncol, 0, colors.length);
        coords = nc;
        colors = ncol;
    }

    // ------------------------------------------------------------------ flush

    /**
     * Empties the queue in a single draw call. Safe to call at any time; a no-op when nothing is
     * queued. Must be called before any vanilla drawing (text, items, widgets) so the layers end up
     * in the right order, and before the pose stack is changed.
     */
    public void flush() {
        if (count == 0 || graphics == null) return;

        // Push anything vanilla has buffered (text from an earlier call) before we draw on top.
        graphics.flush();

        Matrix4f matrix = graphics.pose().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < count; i++) {
            int c = colors[i];
            int a = (c >>> 24) & 0xFF;
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            int o = i * 4;
            float x1 = coords[o], y1 = coords[o + 1], x2 = coords[o + 2], y2 = coords[o + 3];
            // same winding vanilla uses in GuiGraphics#innerFill, so back-face culling is happy
            buffer.vertex(matrix, x1, y1, 0f).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x1, y2, 0f).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x2, y2, 0f).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x2, y1, 0f).color(r, g, b, a).endVertex();
        }

        tesselator.end();
        RenderSystem.disableBlend();

        count = 0;
    }

    // ------------------------------------------------------------------ shapes
    //
    // Everything below is span based: a circle is drawn as one rectangle per scanline rather than
    // one rectangle per pixel. A radius-70 disc costs 141 quads instead of ~15000.

    /** Filled circle. */
    public void disc(int cx, int cy, int r, int argb) {
        if (r <= 0) return;
        int rr = r * r;
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) (Math.sqrt(Math.max(0, rr - dy * dy)) + 0.5);
            if (half <= 0) continue;
            rect(cx - half, cy + dy, cx + half, cy + dy + 1, argb);
        }
    }

    /** Filled circle snapped to the art-pixel grid, for the blockier look. */
    public void discPixel(int cx, int cy, int r, int argb) {
        if (r <= 0) return;
        int p = pixel;
        int rr = r * r;
        for (int dy = -r; dy <= r; dy += p) {
            int half = (int) (Math.sqrt(Math.max(0, rr - dy * dy)) + 0.5);
            if (half <= 0) continue;
            half = (half / p) * p;
            if (half <= 0) half = p;
            rect(cx - half, cy + dy, cx + half, cy + dy + p, argb);
        }
    }

    /** Ring between two radii, drawn as at most two spans per scanline. */
    public void ring(int cx, int cy, int outer, int inner, int argb) {
        if (outer <= 0) return;
        if (inner < 0) inner = 0;
        if (inner >= outer) inner = outer - 1;
        int or2 = outer * outer;
        int ir2 = inner * inner;
        for (int dy = -outer; dy <= outer; dy++) {
            int o = (int) (Math.sqrt(Math.max(0, or2 - dy * dy)) + 0.5);
            if (o <= 0) continue;
            int i = Math.abs(dy) <= inner ? (int) (Math.sqrt(Math.max(0, ir2 - dy * dy)) + 0.5) : 0;
            int y = cy + dy;
            if (i <= 0) {
                rect(cx - o, y, cx + o, y + 1, argb);
            } else {
                rect(cx - o, y, cx - i, y + 1, argb);
                rect(cx + i, y, cx + o, y + 1, argb);
            }
        }
    }

    /** Convenience: ring of the given line thickness. */
    public void ringThick(int cx, int cy, int radius, int thickness, int argb) {
        ring(cx, cy, radius, radius - Math.max(1, thickness), argb);
    }

    /**
     * Partial ring. Stepped along the arc rather than per scanline, with the step chosen so the
     * blocks just touch - roughly one quad per pixel of arc length, which is cheap in a batch.
     */
    public void arc(int cx, int cy, int radius, float fromDeg, float toDeg, int thickness, int argb) {
        if (radius <= 0 || thickness <= 0) return;
        float span = toDeg - fromDeg;
        if (span <= 0f) return;
        float step = Math.max(0.4f, 45f / radius);
        int steps = (int) Math.ceil(span / step);
        int half = Math.max(1, thickness / 2);
        int rMid = radius - half;
        for (int i = 0; i <= steps; i++) {
            double rad = Math.toRadians(fromDeg + span * i / steps);
            float x = cx + (float) Math.cos(rad) * rMid;
            float y = cy + (float) Math.sin(rad) * rMid;
            rect(x - half, y - half, x + half + 1, y + half + 1, argb);
        }
    }

    /** Thick line between two points. */
    public void line(float x1, float y1, float x2, float y2, int thickness, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = (int) Math.max(2, len);
        float half = thickness / 2f;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float px = x1 + dx * t;
            float py = y1 + dy * t;
            rect(px - half, py - half, px + half, py + half, argb);
        }
    }

    /** Rectangle with the corner pixels knocked out - the classic 1px pixel-art rounding. */
    public void roundRect(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        int c = 1;
        rect(x + c, y, x + w - c, y + h, argb);
        rect(x, y + c, x + c, y + h - c, argb);
        rect(x + w - c, y + c, x + w, y + h - c, argb);
    }

    /** 1px outline version of {@link #roundRect}. */
    public void roundOutline(int x, int y, int w, int h, int argb) {
        if (w <= 2 || h <= 2) return;
        rect(x + 1, y, x + w - 1, y + 1, argb);
        rect(x + 1, y + h - 1, x + w - 1, y + h, argb);
        rect(x, y + 1, x + 1, y + h - 1, argb);
        rect(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** Plain hollow rectangle of the given border thickness. */
    public void outline(int x, int y, int w, int h, int t, int argb) {
        rect(x, y, x + w, y + t, argb);
        rect(x, y + h - t, x + w, y + h, argb);
        rect(x, y + t, x + t, y + h - t, argb);
        rect(x + w - t, y + t, x + w, y + h - t, argb);
    }

    /**
     * Checkerboard fill. Cheap way to fake a texture and a soft gradient without shipping any
     * image files, and it is a big part of why the panels read as pixel art.
     */
    public void dither(int x, int y, int w, int h, int argb, int cell, int phase) {
        for (int yy = 0; yy < h; yy += cell) {
            for (int xx = ((yy / cell + phase) % 2) * cell; xx < w; xx += cell * 2) {
                rect(x + xx, y + yy, x + Math.min(w, xx + cell), y + Math.min(h, yy + cell), argb);
            }
        }
    }

    /** Horizontal scanline shading, one line every other row. */
    public void scanlines(int x, int y, int w, int h, int argb) {
        for (int yy = 0; yy < h; yy += 2) {
            rect(x, y + yy, x + w, y + yy + 1, argb);
        }
    }

    // ------------------------------------------------------------------ colour maths

    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }

    public static int scaleAlpha(int argb, float factor) {
        int a = (int) (((argb >>> 24) & 0xFF) * factor);
        return withAlpha(argb, a);
    }

    public static int lighten(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int darken(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * (1f - amount));
        int g = (int) (((argb >> 8) & 0xFF) * (1f - amount));
        int b = (int) ((argb & 0xFF) * (1f - amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int rr = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }
}
