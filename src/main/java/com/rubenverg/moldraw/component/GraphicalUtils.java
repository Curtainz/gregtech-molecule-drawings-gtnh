package com.rubenverg.moldraw.component;

import java.util.function.IntBinaryOperator;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

public class GraphicalUtils {

    @FunctionalInterface
    public interface DrawPixel {

        void draw(int x, int y);
    }

    @FunctionalInterface
    public interface PixelPredicate {

        boolean test(int x, int y, int count);

        default PixelPredicate not() {
            return (x, y, count) -> !this.test(x, y, count);
        }

        default PixelPredicate and(PixelPredicate that) {
            return (x, y, count) -> this.test(x, y, count) && that.test(x, y, count);
        }

        default PixelPredicate or(PixelPredicate that) {
            return (x, y, count) -> this.test(x, y, count) || that.test(x, y, count);
        }

        default PixelPredicate xor(PixelPredicate that) {
            return (x, y, count) -> this.test(x, y, count) ^ that.test(x, y, count);
        }

        static boolean always(int x, int y, int count) {
            return true;
        }

        static boolean never(int x, int y, int count) {
            return false;
        }
    }

    public static boolean alwaysDraw(int _x, int _y, int _count) {
        return true;
    }

    public static void fill(int minX, int minY, int maxX, int maxY, int color) {
        Gui.drawRect(minX, minY, maxX, maxY, color);
    }

    public static void screenPixel(int x, int y, int color) {
        fill(x, y, x + 1, y + 1, color);
    }

    public static void plotLine(int x0, int y0, int x1, int y1, PixelPredicate shouldDraw, DrawPixel doDraw) {
        final int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        final int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int count = 0;
        while (true) {
            if (shouldDraw.test(x0, y0, count++)) doDraw.draw(x0, y0);
            final int e2 = 2 * error;
            if (e2 >= dy) {
                if (x0 == x1) break;
                error += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                if (y0 == y1) break;
                error += dx;
                y0 += sy;
            }
        }
    }

    public static void plotLine(int x0, int y0, int x1, int y1, PixelPredicate shouldDraw, IntBinaryOperator color) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        plotLine(x0, y0, x1, y1, shouldDraw, (DrawPixel) (xp, yp) -> {
            int c = color.applyAsInt(xp, yp);
            float a = (float) ((c >> 24) & 255) / 255.0F;
            float r = (float) ((c >> 16) & 255) / 255.0F;
            float g = (float) ((c >> 8) & 255) / 255.0F;
            float b = (float) (c & 255) / 255.0F;
            if (a <= 0.0f) a = 1.0f;
            tess.setColorRGBA_F(r, g, b, a);
            tess.addVertex(xp, yp + 1, 0.0);
            tess.addVertex(xp + 1, yp + 1, 0.0);
            tess.addVertex(xp + 1, yp, 0.0);
            tess.addVertex(xp, yp, 0.0);
        });
        tess.draw();

        GL11.glPopAttrib();
    }

    public static void plotCircle(int xm, int ym, int r, PixelPredicate shouldDraw, DrawPixel doDraw) {
        int x0 = 0, y0 = r, d = 3 - 2 * r;
        while (y0 >= x0) {
            GraphicalUtils.plotLine(xm - y0, ym - x0, xm + y0, ym - x0, shouldDraw, doDraw);
            if (x0 > 0) GraphicalUtils.plotLine(xm - y0, ym + x0, xm + y0, ym + x0, shouldDraw, doDraw);
            if (d < 0) d += 4 * x0++ + 6;
            else {
                if (x0 != y0) {
                    GraphicalUtils.plotLine(xm - x0, ym - y0, xm + x0, ym - y0, shouldDraw, doDraw);
                    GraphicalUtils.plotLine(xm - x0, ym + y0, xm + x0, ym + y0, shouldDraw, doDraw);
                }
                d += 4 * (x0++ - y0--) + 10;
            }
        }
    }

    public static void plotCircle(int xm, int ym, int r, PixelPredicate shouldDraw, IntBinaryOperator color) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        plotCircle(xm, ym, r, shouldDraw, (DrawPixel) (xp, yp) -> {
            int c = color.applyAsInt(xp, yp);
            float a = (float) ((c >> 24) & 255) / 255.0F;
            float rCol = (float) ((c >> 16) & 255) / 255.0F;
            float g = (float) ((c >> 8) & 255) / 255.0F;
            float b = (float) (c & 255) / 255.0F;
            if (a <= 0.0f) a = 1.0f;
            tess.setColorRGBA_F(rCol, g, b, a);
            tess.addVertex(xp, yp + 1, 0.0);
            tess.addVertex(xp + 1, yp + 1, 0.0);
            tess.addVertex(xp + 1, yp, 0.0);
            tess.addVertex(xp, yp, 0.0);
        });
        tess.draw();

        GL11.glPopAttrib();
    }
}
