package com.rubenverg.moldraw.core;

/** Pure bounded rasterization, independent of Minecraft and OpenGL. */
public final class PixelRaster {

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

    public static void plotLine(int x0, int y0, int x1, int y1, PixelPredicate shouldDraw, DrawPixel doDraw) {
        checkPosition(x0, y0);
        checkPosition(x1, y1);
        if (Math.abs((long) x1 - x0) > 8192 || Math.abs((long) y1 - y0) > 8192)
            throw new IllegalArgumentException("Line exceeds raster limits");
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

    public static void plotCircle(int xm, int ym, int r, PixelPredicate shouldDraw, DrawPixel doDraw) {
        checkPosition(xm, ym);
        if (r < 0 || r > 256) throw new IllegalArgumentException("Circle exceeds raster limits");
        int x0 = 0, y0 = r, d = 3 - 2 * r;
        while (y0 >= x0) {
            PixelRaster.plotLine(xm - y0, ym - x0, xm + y0, ym - x0, shouldDraw, doDraw);
            if (x0 > 0) PixelRaster.plotLine(xm - y0, ym + x0, xm + y0, ym + x0, shouldDraw, doDraw);
            if (d < 0) d += 4 * x0++ + 6;
            else {
                if (x0 != y0) {
                    PixelRaster.plotLine(xm - x0, ym - y0, xm + x0, ym - y0, shouldDraw, doDraw);
                    PixelRaster.plotLine(xm - x0, ym + y0, xm + x0, ym + y0, shouldDraw, doDraw);
                }
                d += 4 * (x0++ - y0--) + 10;
            }
        }
    }

    private static void checkPosition(int x, int y) {
        if (Math.abs((long) x) > 1_000_000 || Math.abs((long) y) > 1_000_000)
            throw new IllegalArgumentException("Position exceeds raster limits");
    }

}
