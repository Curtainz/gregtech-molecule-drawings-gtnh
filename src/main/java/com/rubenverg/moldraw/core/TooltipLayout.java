package com.rubenverg.moldraw.core;

import java.util.*;
import java.util.function.IntBinaryOperator;

/** Coordinates are local to the tooltip; measurement and rendering consume the same immutable commands. */
public record TooltipLayout(int width, int height, List<Command> commands) {

    public TooltipLayout {
        commands = List.copyOf(commands);
        if (width < 0 || height < 0 || width > 16384 || height > 16384)
            throw new IllegalArgumentException("Layout too large");
    }

    public sealed interface Command permits Pixels,Fill,Text {
    }

    public record Pixels(List<PixelBatch.Pixel> pixels) implements Command {

        public Pixels {
            pixels = List.copyOf(pixels);
        }
    }

    public record Fill(int x0, int y0, int x1, int y1, int color) implements Command {}

    public record Text(String text, int x, int y, int color) implements Command {}

    public static final class Canvas implements TextMeasure {

        private final TextMeasure measure;
        private final List<Command> commands = new ArrayList<>();
        private int pixelCount;

        private void addPixels(PixelBatch batch) {
            pixelCount = Math.addExact(
                pixelCount,
                batch.pixels()
                    .size());
            if (pixelCount > 262_144) throw new IllegalArgumentException("Layout pixel budget exceeded");
            commands.add(new Pixels(batch.pixels()));
        }

        public Canvas(TextMeasure measure) {
            this.measure = measure;
        }

        public int width(String text) {
            return measure.width(text);
        }

        public int lineHeight() {
            return measure.lineHeight();
        }

        public void drawStringWithShadow(String text, int x, int y, int color) {
            if (!text.isEmpty()) commands.add(new Text(text, x, y, color));
        }

        public void fill(int x0, int y0, int x1, int y1, int color) {
            commands.add(new Fill(x0, y0, x1, y1, color));
        }

        public void screenPixel(int x, int y, int color) {
            fill(x, y, x + 1, y + 1, color);
        }

        public void plotLine(int x0, int y0, int x1, int y1, PixelRaster.PixelPredicate predicate,
            PixelRaster.DrawPixel sink) {
            PixelRaster.plotLine(x0, y0, x1, y1, predicate, sink);
        }

        public void plotLine(int x0, int y0, int x1, int y1, PixelRaster.PixelPredicate predicate,
            IntBinaryOperator color) {
            addPixels(PixelBatch.line(x0, y0, x1, y1, predicate, color));
        }

        public void plotCircle(int x, int y, int r, PixelRaster.PixelPredicate predicate, IntBinaryOperator color) {
            addPixels(PixelBatch.circle(x, y, r, predicate, color));
        }

        public TooltipLayout finish(int suggestedWidth, int suggestedHeight) {
            int minX = 0, minY = 0, maxX = suggestedWidth, maxY = suggestedHeight;
            for (var c : commands) {
                if (c instanceof Fill f) {
                    minX = Math.min(minX, Math.min(f.x0, f.x1));
                    minY = Math.min(minY, Math.min(f.y0, f.y1));
                    maxX = Math.max(maxX, Math.max(f.x0, f.x1));
                    maxY = Math.max(maxY, Math.max(f.y0, f.y1));
                } else if (c instanceof Text t) {
                    minX = Math.min(minX, t.x);
                    minY = Math.min(minY, t.y);
                    maxX = Math.max(maxX, t.x + width(t.text) + 1);
                    maxY = Math.max(maxY, t.y + lineHeight() + 1);
                } else if (c instanceof Pixels ps) for (var p : ps.pixels) {
                    minX = Math.min(minX, p.x());
                    minY = Math.min(minY, p.y());
                    maxX = Math.max(maxX, p.x() + 1);
                    maxY = Math.max(maxY, p.y() + 1);
                }
            }
            List<Command> shifted = new ArrayList<>();
            final int dx = -minX, dy = -minY;
            for (var c : commands) {
                if (c instanceof Fill f) shifted.add(new Fill(f.x0 + dx, f.y0 + dy, f.x1 + dx, f.y1 + dy, f.color));
                else if (c instanceof Text t) shifted.add(new Text(t.text, t.x + dx, t.y + dy, t.color));
                else if (c instanceof Pixels ps) shifted.add(
                    new Pixels(
                        ps.pixels.stream()
                            .map(p -> new PixelBatch.Pixel(p.x() + dx, p.y() + dy, p.color()))
                            .toList()));
            }
            return new TooltipLayout(maxX - minX, maxY - minY, shifted);
        }
    }
}
