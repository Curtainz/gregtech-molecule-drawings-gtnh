package com.rubenverg.moldraw.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/** Fully evaluate user/resource-dependent callbacks before touching the shared rendering backend. */
public final class PixelBatch {

    public record Pixel(int x, int y, int color) {}

    private final List<Pixel> pixels;

    private PixelBatch(List<Pixel> pixels) {
        this.pixels = List.copyOf(pixels);
    }

    public List<Pixel> pixels() {
        return pixels;
    }

    public static PixelBatch line(int x0, int y0, int x1, int y1, PixelRaster.PixelPredicate predicate,
        IntBinaryOperator color) {
        List<Pixel> pixels = new ArrayList<>();
        PixelRaster.plotLine(x0, y0, x1, y1, predicate, (x, y) -> pixels.add(new Pixel(x, y, color.applyAsInt(x, y))));
        return new PixelBatch(pixels);
    }

    public static PixelBatch circle(int x, int y, int radius, PixelRaster.PixelPredicate predicate,
        IntBinaryOperator color) {
        List<Pixel> pixels = new ArrayList<>();
        PixelRaster
            .plotCircle(x, y, radius, predicate, (xp, yp) -> pixels.add(new Pixel(xp, yp, color.applyAsInt(xp, yp))));
        return new PixelBatch(pixels);
    }
}
