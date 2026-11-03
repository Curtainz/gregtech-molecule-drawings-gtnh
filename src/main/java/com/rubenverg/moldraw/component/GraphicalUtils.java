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
        if (!com.rubenverg.moldraw.MolDraw.isClientThread()) return;
        Gui.drawRect(minX, minY, maxX, maxY, color);
    }

    public static void screenPixel(int x, int y, int color) {
        fill(x, y, x + 1, y + 1, color);
    }

    public static void plotLine(int x0, int y0, int x1, int y1, PixelPredicate shouldDraw, DrawPixel doDraw) {
        com.rubenverg.moldraw.core.PixelRaster.plotLine(x0, y0, x1, y1, shouldDraw::test, doDraw::draw);
    }

    public static void plotLine(int x0, int y0, int x1, int y1, PixelPredicate shouldDraw, IntBinaryOperator color) {
        drawBatch(com.rubenverg.moldraw.core.PixelBatch.line(x0, y0, x1, y1, shouldDraw::test, color));
    }

    public static void plotCircle(int xm, int ym, int r, PixelPredicate shouldDraw, DrawPixel doDraw) {
        com.rubenverg.moldraw.core.PixelRaster.plotCircle(xm, ym, r, shouldDraw::test, doDraw::draw);
    }

    public static void plotCircle(int xm, int ym, int r, PixelPredicate shouldDraw, IntBinaryOperator color) {
        drawBatch(com.rubenverg.moldraw.core.PixelBatch.circle(xm, ym, r, shouldDraw::test, color));
    }

    private static void drawBatch(com.rubenverg.moldraw.core.PixelBatch batch) {
        if (!com.rubenverg.moldraw.MolDraw.isClientThread() || batch.pixels()
            .isEmpty()) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            try {
                for (var pixel : batch.pixels()) {
                    int c = pixel.color();
                    float a = ((c >>> 24) & 255) / 255.0F;
                    if (a <= 0) a = 1;
                    tess.setColorRGBA_F(((c >>> 16) & 255) / 255.0F, ((c >>> 8) & 255) / 255.0F, (c & 255) / 255.0F, a);
                    int x = pixel.x(), y = pixel.y();
                    tess.addVertex(x, y + 1, 0);
                    tess.addVertex(x + 1, y + 1, 0);
                    tess.addVertex(x + 1, y, 0);
                    tess.addVertex(x, y, 0);
                }
            } finally {
                // startDrawingQuads succeeded; always close this batch before releasing GL state.
                tess.draw();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }
}
