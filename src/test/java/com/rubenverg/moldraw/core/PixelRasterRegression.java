package com.rubenverg.moldraw.core;

import java.util.HashSet;
import java.util.Set;

/** Headless geometry/callback failure tests: the runtime classpath needs no Minecraft or GL. */
public final class PixelRasterRegression {

    public static void main(String[] args) {
        var line = PixelBatch.line(-2, 3, 2, 3, (x, y, n) -> true, (x, y) -> x);
        if (line.pixels()
            .size() != 5
            || line.pixels()
                .get(0)
                .x() != -2
            || line.pixels()
                .get(4)
                .color() != 2)
            throw new AssertionError("horizontal endpoints/colors");
        for (int x = -5; x <= 5; x++) for (int y = -5; y <= 5; y++) {
            var pixels = PixelBatch.line(0, 0, x, y, (a, b, n) -> true, (a, b) -> 0)
                .pixels();
            if (pixels.size() != Math.max(Math.abs(x), Math.abs(y)) + 1 || pixels.getLast()
                .x() != x
                || pixels.getLast()
                    .y() != y)
                throw new AssertionError("line octant");
        }
        for (int radius : new int[] { 0, 1, 2, 32, 64, 256 }) {
            Set<String> points = new HashSet<>();
            var circle = PixelBatch.circle(10, 20, radius, (x, y, n) -> true, (x, y) -> 0);
            for (var p : circle.pixels()) points.add((p.x() - 10) + "," + (p.y() - 20));
            for (var p : circle.pixels()) {
                if (!points.contains((10 - p.x()) + "," + (p.y() - 20))
                    || !points.contains((p.x() - 10) + "," + (20 - p.y()))) throw new AssertionError("circle symmetry");
            }
        }
        if (!PixelBatch.line(0, 0, 5, 5, (x, y, n) -> false, (x, y) -> { throw new AssertionError(); })
            .pixels()
            .isEmpty()) throw new AssertionError("masked callback executed");
        try {
            line.pixels()
                .clear();
            throw new AssertionError("mutable batch");
        } catch (UnsupportedOperationException expected) {}
        rejects(() -> PixelBatch.line(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0, (x, y, n) -> true, (x, y) -> 0));
        rejects(() -> PixelBatch.line(0, 0, 8193, 0, (x, y, n) -> true, (x, y) -> 0));
        rejects(() -> PixelBatch.circle(0, 0, -1, (x, y, n) -> true, (x, y) -> 0));
        rejects(() -> PixelBatch.circle(0, 0, 257, (x, y, n) -> true, (x, y) -> 0));
        try {
            PixelBatch.line(0, 0, 3, 0, (x, y, n) -> true, (x, y) -> {
                if (x == 2) throw new IllegalStateException("test callback");
                return 0;
            });
            throw new AssertionError("callback failure lost");
        } catch (IllegalStateException expected) {}
        System.out.println("Raster regression passed: 121 octants, 6 circle sizes, callbacks and limits");
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            throw new AssertionError("unbounded raster accepted");
        } catch (IllegalArgumentException expected) {}
    }
}
