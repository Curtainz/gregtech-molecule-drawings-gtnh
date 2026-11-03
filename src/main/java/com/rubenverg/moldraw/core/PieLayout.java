package com.rubenverg.moldraw.core;

import java.util.*;

/** Frozen pie layout using exact shares and injected font metrics. */
public final class PieLayout {

    public record Slice(String id, Fraction share, String label, int color) {}

    public static TooltipLayout build(List<Slice> slices, int radius, TextMeasure measure) {
        if (radius < 1 || radius > 256) throw new IllegalArgumentException("Invalid radius");
        var canvas = new TooltipLayout.Canvas(measure);
        if (slices.isEmpty()) return canvas.finish(0, 0);
        Fraction total = slices.stream()
            .map(Slice::share)
            .reduce(Fraction.ZERO, Fraction::add);
        if (!total.equals(Fraction.ONE)) throw new IllegalArgumentException("Pie shares must sum to one");
        List<Double> ends = new ArrayList<>(), centers = new ArrayList<>();
        Fraction current = Fraction.ZERO;
        for (var s : slices) {
            double start = current.value() * 2 * Math.PI;
            current = current.add(s.share);
            double end = current.value() * 2 * Math.PI;
            ends.add(end);
            centers.add((start + end) / 2);
        }
        int xm = 95, ym = radius * 5 / 4;
        canvas.plotCircle(xm, ym, radius, PixelRaster.PixelPredicate::always, (x, y) -> {
            double angle = Math.atan2(x - xm, -(y - ym));
            if (angle < 0) angle += 2 * Math.PI;
            for (int i = 0; i < ends.size(); i++) if (angle <= ends.get(i)) return slices.get(i).color;
            return slices.getLast().color;
        });
        int leftNext = Integer.MAX_VALUE, rightNext = Integer.MIN_VALUE;
        for (int i = 0; i < slices.size(); i++) {
            var s = slices.get(i);
            double angle = centers.get(i);
            boolean left = angle > Math.PI;
            int cy = (int) (-Math.cos(angle) * 0.9 * radius), ex = (left ? -1 : 1) * (radius + 10);
            double pct = s.share.value() * 100;
            String text = (pct < 0.1 ? "<0.1%" : String.format(Locale.ROOT, "%.1f%%", pct)) + " " + s.label;
            int top = left ? Math.min(cy - measure.lineHeight() / 2, leftNext)
                : Math.max(cy - measure.lineHeight() / 2, rightNext);
            if (left) leftNext = top - measure.lineHeight() - 1;
            else rightNext = top + measure.lineHeight() + 1;
            int textX = xm + (left ? ex - 5 - measure.width(text) : ex + 5);
            int centerY = ym + top + measure.lineHeight() / 2;
            int cx = xm + (int) (Math.sin(angle) * 0.9 * radius), startY = ym + (int) (-Math.cos(angle) * 0.9 * radius);
            canvas.plotLine(cx, startY, cx, centerY, PixelRaster.PixelPredicate::always, (_x, _y) -> 0xFFFFFFFF);
            canvas.plotLine(cx, centerY, xm + ex, centerY, PixelRaster.PixelPredicate::always, (_x, _y) -> 0xFFFFFFFF);
            canvas.drawStringWithShadow(text, textX, ym + top, s.color);
        }
        return canvas.finish(190, radius * 5 / 2);
    }
}
