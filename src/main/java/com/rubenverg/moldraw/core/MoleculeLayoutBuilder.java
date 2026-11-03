package com.rubenverg.moldraw.core;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.rubenverg.moldraw.core.MoleculeScene.*;
import com.rubenverg.moldraw.util.Pair;

public final class MoleculeLayoutBuilder {

    private final MoleculeScene molecule;
    private final int scale, defaultColor;
    private TooltipLayout.Canvas canvas;
    private final Vector2i xySize;
    private final Vector2f xyStart;
    private final boolean atomAtTop;
    private final boolean atomAtTopTop;
    private final boolean atomAtBotBot;
    private final boolean atomAtLefLef;
    private final boolean parenAtLef;
    private final Map<Label, Integer> elementWidths = new HashMap<>();

    public MoleculeLayoutBuilder(MoleculeScene molecule, int scale, int defaultColor) {
        this.molecule = molecule;
        this.scale = scale;
        this.defaultColor = defaultColor;
        final var bounds = molecule.bounds();
        final Vector2f diff = new Vector2f();
        bounds.getSecond()
            .sub(bounds.getFirst(), diff);
        diff.mul(scale);
        diff.ceil();
        this.xySize = new Vector2i((int) diff.x, (int) diff.y);
        this.xyStart = new Vector2f(bounds.getFirst().x, bounds.getSecond().y);
        this.atomAtTop = molecule.atoms()
            .stream()
            .anyMatch(atom -> {
                final var distanceFromTop = Math.abs(xyStart.y - atom.position().y);
                return distanceFromTop < 0.1 && !atom.isInvisible();
            });
        this.atomAtTopTop = molecule.atoms()
            .stream()
            .anyMatch(atom -> {
                final var distanceFromTop = Math.abs(xyStart.y - atom.position().y);
                return distanceFromTop < 0.1 && !atom.isInvisible()
                    && atom.above()
                        .isPresent();
            });
        this.atomAtBotBot = molecule.atoms()
            .stream()
            .anyMatch(atom -> {
                final var distanceFromBot = Math.abs(bounds.getFirst().y - atom.position().y);
                return distanceFromBot < 0.1 && !atom.isInvisible()
                    && atom.below()
                        .isPresent();
            });
        this.atomAtLefLef = molecule.atoms()
            .stream()
            .anyMatch(atom -> {
                final var distanceFromLef = Math.abs(bounds.getFirst().x - atom.position().x);
                return distanceFromLef < 0.1 && !atom.isInvisible()
                    && atom.left()
                        .isPresent();
            });
        this.parenAtLef = molecule.atoms()
            .stream()
            .anyMatch(atom -> {
                final var distanceFromLef = Math.abs(bounds.getFirst().x - atom.position().x);
                if (distanceFromLef > 0.1) return false;
                return molecule.flatChildren()
                    .stream()
                    .anyMatch(
                        el -> el instanceof Parens parens && Arrays.stream(parens.atoms())
                            .anyMatch(d -> d == atom.index()));
            });
    }

    private UnaryOperator<Vector2f> toScaledFactory(int lineHeight) {
        return xy -> {
            var result = new Vector2f();
            new Vector2f(xy.x, xy.y).sub(xyStart, result);
            result.mul(scale);
            return new Vector2f(
                result.x + 8 + (atomAtLefLef ? 12 : 0) + (parenAtLef ? 6 : 0),
                -result.y + (atomAtTopTop ? lineHeight * 3 / 2f : atomAtTop ? lineHeight / 2f : 3));
        };
    }

    private Vector2f project(Vector3fc xyz) {
        return new Vector2f(xyz.x(), xyz.y());
    }

    public int getWidth(TooltipLayout.Canvas font) {
        return xySize.x + 32 + (atomAtLefLef ? 12 : 0) + (parenAtLef ? 6 : 0);
    }

    public int getHeight(TooltipLayout.Canvas font) {
        return xySize.y + 20 + (atomAtBotBot ? 10 : 0) + (atomAtTopTop ? 10 : 0);
    }

    public TooltipLayout build(TextMeasure measure) {
        canvas = new TooltipLayout.Canvas(measure);
        TooltipLayout.Canvas font = canvas;
        int x = 0, y = 0;
        final var ts = toScaledFactory(font.lineHeight());

        // 阶段 1：先测量所有原子的文字尺寸（仅测量，不绘制），填入 elementWidths
        elementWidths.clear();
        for (final var elem : this.molecule.contents()) {
            measureText(elem, font);
        }

        // Build skeleton commands first; this layer never submits GL calls.
        for (final var elem : this.molecule.contents()) {
            renderOneImage(elem, font, x, y, ts, defaultColor, null);
        }

        // Append text/mask commands after skeleton commands.
        for (final var elem : this.molecule.contents()) {
            renderOneText(elem, font, x, y, ts, defaultColor);
        }
        return canvas.finish(getWidth(font), getHeight(font));
    }

    private void measureText(Shape elem, TooltipLayout.Canvas font) {
        if (elem instanceof Atom atom) {
            if (!atom.element()
                .element()
                .invisible()) {
                int width = font.width(
                    atom.element()
                        .toString());
                elementWidths.put(atom.element(), width);
            } else {
                elementWidths.put(atom.element(), 0);
            }
            if (atom.right()
                .isPresent()) {
                elementWidths.put(
                    atom.right()
                        .get(),
                    font.width(
                        atom.right()
                            .get()
                            .toString()));
            }
            if (atom.left()
                .isPresent()) {
                elementWidths.put(
                    atom.left()
                        .get(),
                    font.width(
                        atom.left()
                            .get()
                            .toString()));
            }
            if (atom.above()
                .isPresent()) {
                elementWidths.put(
                    atom.above()
                        .get(),
                    font.width(
                        atom.above()
                            .get()
                            .toString()));
            }
            if (atom.below()
                .isPresent()) {
                elementWidths.put(
                    atom.below()
                        .get(),
                    font.width(
                        atom.below()
                            .get()
                            .toString()));
            }
        } else if (elem instanceof Group composite) {
            for (final var child : composite.children()) {
                measureText(child, font);
            }
        }
    }

    private java.util.function.Function<Atom, Pair<Vector2f, Vector2f>> sizeOfAtomFactory(int lineHeight) {
        return atom -> {
            float x0 = 0, x1 = 0, y0 = 0, y1 = 0;
            x0 += elementWidths.getOrDefault(atom.element(), 0) / 2f;
            x1 += elementWidths.getOrDefault(atom.element(), 0) / 2f;
            y0 += 1;
            y1 += lineHeight + 1;
            if (atom.right()
                .isPresent()) {
                x1 += 1 + elementWidths.getOrDefault(
                    atom.right()
                        .get(),
                    0);
            }
            if (atom.left()
                .isPresent()) {
                x0 += 1 + elementWidths.getOrDefault(
                    atom.left()
                        .get(),
                    0);
            }
            if (atom.above()
                .isPresent()) {
                y0 += 1 + lineHeight;
                x0 = Math.max(
                    x0,
                    elementWidths.getOrDefault(
                        atom.above()
                            .get(),
                        0) / 2f);
                x1 = Math.max(
                    x1,
                    elementWidths.getOrDefault(
                        atom.above()
                            .get(),
                        0) / 2f);
            }
            if (atom.below()
                .isPresent()) {
                y1 += 1 + lineHeight;
                x0 = Math.max(
                    x0,
                    elementWidths.getOrDefault(
                        atom.below()
                            .get(),
                        0) / 2f);
                x1 = Math.max(
                    x1,
                    elementWidths.getOrDefault(
                        atom.below()
                            .get(),
                        0) / 2f);
            }
            return new Pair<>(new Vector2f(x0, y0), new Vector2f(x1, y1));
        };
    }

    private void renderOneImage(Shape elem, TooltipLayout.Canvas font, int x, int y, UnaryOperator<Vector2f> toScaled,
        int defaultColor, @Nullable IntBinaryOperator overrideColor) {
        if (elem instanceof Bond bond) {
            if (this.molecule.getAtom(bond.a())
                .isEmpty()
                || this.molecule.getAtom(bond.b())
                    .isEmpty())
                return;

            final var atomA = this.molecule.getAtom(bond.a())
                .orElseThrow();
            final var atomAWidth = elementWidths.getOrDefault(atomA.element(), 0);
            final var atomAAbove = atomA.above()
                .map(elementWidths::get);
            final var atomARight = atomA.right()
                .map(elementWidths::get);
            final var atomABelow = atomA.below()
                .map(elementWidths::get);
            final var atomALeft = atomA.left()
                .map(elementWidths::get);
            final var atomAInvisible = atomA.isInvisible();

            final var atomB = this.molecule.getAtom(bond.b())
                .orElseThrow();
            final var atomBWidth = elementWidths.getOrDefault(atomB.element(), 0);
            final var atomBAbove = atomB.above()
                .map(elementWidths::get);
            final var atomBRight = atomB.right()
                .map(elementWidths::get);
            final var atomBBelow = atomB.below()
                .map(elementWidths::get);
            final var atomBLeft = atomB.left()
                .map(elementWidths::get);
            final var atomBInvisible = atomB.isInvisible();

            final var start = floored(toScaled.apply(project(atomA.position())));
            start.add(x, y);
            start.add(0, font.lineHeight() / 2);

            final var end = floored(toScaled.apply(project(atomB.position())));
            end.add(x, y);
            end.add(0, font.lineHeight() / 2);

            final PixelRaster.PixelPredicate notCloseToAtom = (xt, yt, _c) -> {
                if (atomAInvisible && atomBInvisible) return true;
                Vector2ic t = new Vector2i(xt, yt);
                var diff = new Vector2i();
                start.sub(t, diff);
                diff.absolute();
                if (diff.x < atomAWidth * 2 / 3 && (!atomAInvisible && diff.y < font.lineHeight() * 2 / 3))
                    return false;
                if (atomAAbove.isPresent() && diff.x < atomAAbove.get() * 2 / 3
                    && Math.abs((start.y - font.lineHeight() - 1) - yt) < font.lineHeight() * 2 / 3) return false;
                if (atomABelow.isPresent() && diff.x < atomABelow.get() * 2 / 3
                    && Math.abs((start.y + font.lineHeight() + 1) - yt) < font.lineHeight() * 2 / 3) return false;
                if (atomARight.isPresent()
                    && Math.abs((start.x + (atomAWidth + atomARight.get()) / 2 + 1) - xt) < atomARight.get() * 2 / 3
                    && diff.y < font.lineHeight() * 2 / 3) return false;
                if (atomALeft.isPresent()
                    && Math.abs((start.x - (atomAWidth + atomALeft.get()) / 2 - 1) - xt) < atomALeft.get() * 2 / 3
                    && diff.y < font.lineHeight() * 2 / 3) return false;
                end.sub(t, diff);
                diff.absolute();
                if (diff.x < atomBWidth * 2 / 3 && (!atomBInvisible && diff.y < font.lineHeight() * 2 / 3))
                    return false;
                if (atomBAbove.isPresent() && diff.x < atomBAbove.get() * 2 / 3
                    && Math.abs((end.y - font.lineHeight() - 1) - yt) < font.lineHeight() * 2 / 3) return false;
                if (atomBBelow.isPresent() && diff.x < atomBBelow.get() * 2 / 3
                    && Math.abs((end.y + font.lineHeight() + 1) - yt) < font.lineHeight() * 2 / 3) return false;
                if (atomBRight.isPresent()
                    && Math.abs((end.x + (atomBWidth + atomBRight.get()) / 2 + 1) - xt) < atomBRight.get() * 2 / 3
                    && diff.y < font.lineHeight() * 2 / 3) return false;
                if (atomBLeft.isPresent()
                    && Math.abs((end.x - (atomBWidth + atomBLeft.get()) / 2 - 1) - xt) < atomBLeft.get() * 2 / 3
                    && diff.y < font.lineHeight() * 2 / 3) return false;
                return true;
            };

            final var startEnd = new Vector2f(end).sub(new Vector2f(start));
            final float dy = startEnd.y, dx = startEnd.x, length = startEnd.length();
            if (length == 0) return;

            final BiFunction<Integer, Integer, PixelRaster.PixelPredicate> notCloseToAtomAndDot = (m,
                b) -> notCloseToAtom.and((xt, yt, count) -> count % m < b);
            int addX = Math.round(dy / length * 2), addY = -Math.round(dx / length * 2);
            int addHX = Math.round(dy / length), addHY = -Math.round(dx / length);

            int colorA = atomA.element()
                .element()
                .color();
            int colorB = atomB.element()
                .element()
                .color();
            IntBinaryOperator color = Objects.requireNonNullElse(overrideColor, (xp, yp) -> {
                final var d2a = Math.pow(xp - start.x, 2) + Math.pow(yp - start.y, 2);
                final var d2b = Math.pow(xp - end.x, 2) + Math.pow(yp - end.y, 2);
                return d2a < d2b ? colorA : colorB;
            });

            List<Vector2i> allTargets = new ArrayList<>();
            canvas.plotLine(
                addX * 3 / 2,
                addY * 3 / 2,
                -addX * 3 / 2,
                -addY * 3 / 2,
                PixelRaster.PixelPredicate::always,
                (xp, yp) -> {
                    allTargets.add(new Vector2i(xp / 2, yp / 2));
                    allTargets.add(new Vector2i((xp + 1) / 2, yp / 2));
                    allTargets.add(new Vector2i(xp / 2, (yp + 1) / 2));
                    allTargets.add(new Vector2i((xp + 1) / 2, (yp + 1) / 2));
                });

            final var aboveEnd = new Vector2f(end).sub(new Vector2f(start))
                .perpendicular()
                .normalize(2)
                .add(new Vector2f(end));
            List<Vector2i> above = new ArrayList<>();
            canvas.plotLine(
                start.x,
                start.y,
                Math.round(aboveEnd.x),
                Math.round(aboveEnd.y),
                PixelRaster.PixelPredicate::always,
                (PixelRaster.DrawPixel) (xp, yp) -> above.add(new Vector2i(xp, yp)));

            final var thickness = bond.totalThickness();
            final var starting = bond.centered() ? (thickness - 1) / 2f : (float) ((thickness - 1) / 2);
            var done = bond.lines().length > 0 && bond.lines()[0].thick ? 1 : 0;
            for (var i = 0; i < bond.lines().length; done += bond.lines()[i].thick ? 3 : 1, i++) {
                final var delta = done - starting;
                final var sX = (int) Math.floor(delta) * addX + (int) ((delta - Math.floor(delta)) * 2) * addHX;
                final var sY = (int) Math.floor(delta) * addY + (int) ((delta - Math.floor(delta)) * 2) * addHY;
                switch (bond.lines()[i]) {
                    case SOLID -> canvas
                        .plotLine(start.x + sX, start.y + sY, end.x + sX, end.y + sY, notCloseToAtom, color);
                    case DOTTED -> canvas.plotLine(
                        start.x + sX,
                        start.y + sY,
                        end.x + sX,
                        end.y + sY,
                        notCloseToAtomAndDot.apply(2, 1),
                        color);
                    case INWARD -> {
                        for (int j = 0;; j++) {
                            if (j >= above.size()) break;
                            if (j % 3 != 0) continue;
                            final var abovePoint = above.get(j);
                            final var a = new Vector2f(abovePoint);
                            final var startA = new Vector2f(a).sub(new Vector2f(start));
                            final var b = new Vector2f(start)
                                .add(new Vector2f(startEnd).mul(startEnd.dot(startA) / startEnd.dot(startEnd)))
                                .mul(2)
                                .sub(a);
                            final var belowPoint = new Vector2i(Math.round(b.x), Math.round(b.y));
                            canvas.plotLine(
                                abovePoint.x + sX,
                                abovePoint.y + sY,
                                belowPoint.x + sX,
                                belowPoint.y + sY,
                                notCloseToAtom,
                                color);
                        }
                    }
                    case OUTWARD -> {
                        for (final var pair : allTargets) {
                            canvas.plotLine(
                                start.x + sX,
                                start.y + sY,
                                end.x + pair.x + sX,
                                end.y + pair.y + sY,
                                notCloseToAtom,
                                color);
                        }
                    }
                    case THICK -> {
                        for (final var pair : allTargets) {
                            canvas.plotLine(
                                start.x + pair.x + sX,
                                start.y + pair.y + sY,
                                end.x + pair.x + sX,
                                end.y + pair.y + sY,
                                notCloseToAtom,
                                color);
                        }
                    }
                }
            }
        } else if (elem instanceof Parens pp) {
            final var bounds = this.molecule.subset(pp.atoms())
                .boundsWithSize(xyz -> toScaled.apply(project(xyz)), sizeOfAtomFactory(font.lineHeight()));
            final var xyMin = floored(bounds.getFirst());
            xyMin.add(x, y);
            xyMin.add(-2, -1);
            final var xyMax = floored(bounds.getSecond());
            xyMax.add(x, y);
            xyMax.add(2, 1);
            canvas.plotLine(
                xyMin.x - 2,
                xyMin.y,
                xyMin.x + 2,
                xyMin.y,
                PixelRaster.PixelPredicate::always,
                Objects.requireNonNullElse(overrideColor, (_x, _y) -> defaultColor));
            canvas.plotLine(
                xyMin.x - 2,
                xyMax.y,
                xyMin.x + 2,
                xyMax.y,
                PixelRaster.PixelPredicate::always,
                Objects.requireNonNullElse(overrideColor, (_x, _y) -> defaultColor));
            canvas.plotLine(
                xyMax.x + 2,
                xyMin.y,
                xyMax.x - 2,
                xyMin.y,
                PixelRaster.PixelPredicate::always,
                Objects.requireNonNullElse(overrideColor, (_x, _y) -> defaultColor));
            canvas.plotLine(
                xyMax.x + 2,
                xyMax.y,
                xyMax.x - 2,
                xyMax.y,
                PixelRaster.PixelPredicate::always,
                Objects.requireNonNullElse(overrideColor, (_x, _y) -> defaultColor));
            canvas.plotLine(
                xyMin.x - 2,
                xyMin.y,
                xyMin.x - 2,
                xyMax.y,
                PixelRaster.PixelPredicate::always,
                Objects.requireNonNullElse(overrideColor, (_x, _y) -> defaultColor));
            canvas.plotLine(
                xyMax.x + 2,
                xyMin.y,
                xyMax.x + 2,
                xyMax.y,
                PixelRaster.PixelPredicate::always,
                Objects.requireNonNullElse(overrideColor, (_x, _y) -> defaultColor));
        } else if (elem instanceof CircleTransformation ct) {
            final var centroid = Arrays.stream(ct.atoms())
                .mapToObj(
                    idx -> this.molecule.getAtom(idx)
                        .orElseThrow()
                        .position())
                .reduce(new Vector3f(), (a, b) -> new Vector3f(a).add(new Vector3f(b)))
                .div(ct.atoms().length);
            for (int part = 0; part < 128; part++) {
                final var angle = (float) part / 64 * (float) Math.PI;
                final var u = new Vector3f((float) Math.cos(angle), (float) Math.sin(angle), 0);
                final var p = u.mul(new Matrix3f(ct.A()))
                    .add(centroid.x, centroid.y, centroid.z);
                final var r = floored(toScaled.apply(project(p))).add(x, y + font.lineHeight() / 2);
                canvas.screenPixel(
                    r.x,
                    r.y,
                    Objects.isNull(overrideColor) ? defaultColor : overrideColor.applyAsInt(r.x, r.y));
            }
        } else if (elem instanceof Group composite) {
            for (final var child : composite.children()) {
                renderOneImage(child, font, x, y, toScaled, defaultColor, overrideColor);
            }
        }
    }

    private void renderOneText(Shape elem, TooltipLayout.Canvas font, int x, int y, UnaryOperator<Vector2f> toScaled,
        int defaultColor) {
        if (elem instanceof Atom atom) {
            final var pos = toScaled.apply(project(atom.position()));
            final int px = (int) Math.floor(pos.x) + x;
            final int py = (int) Math.floor(pos.y) + y + font.lineHeight() / 2;

            if (!atom.element()
                .element()
                .invisible()) {
                String symbol = atom.element()
                    .toString();
                int width = font.width(symbol);
                int color = atom.element()
                    .element()
                    .color();
                canvas.fill(
                    px - width / 2 - 1,
                    py - font.lineHeight() / 2,
                    px + width / 2 + 1,
                    py + font.lineHeight() / 2,
                    0xDD100010);
                font.drawStringWithShadow(symbol, px - width / 2, py - font.lineHeight() / 2, color);
            }

            if (atom.right()
                .isPresent()
                && !atom.right()
                    .get()
                    .element()
                    .invisible()) {
                String str = atom.right()
                    .get()
                    .toString();
                int color = atom.right()
                    .get()
                    .element()
                    .color();
                int mainW = elementWidths.getOrDefault(atom.element(), 0);
                int w = font.width(str);
                canvas.fill(
                    px + mainW / 2,
                    py - font.lineHeight() / 2,
                    px + mainW / 2 + w + 2,
                    py + font.lineHeight() / 2,
                    0xDD100010);
                font.drawStringWithShadow(str, px + mainW / 2 + 1, py - font.lineHeight() / 2, color);
            }
            if (atom.left()
                .isPresent()
                && !atom.left()
                    .get()
                    .element()
                    .invisible()) {
                String str = atom.left()
                    .get()
                    .toString();
                int color = atom.left()
                    .get()
                    .element()
                    .color();
                int w = font.width(str);
                int mainW = elementWidths.getOrDefault(atom.element(), 0);
                canvas.fill(
                    px - mainW / 2 - w - 2,
                    py - font.lineHeight() / 2,
                    px - mainW / 2,
                    py + font.lineHeight() / 2,
                    0xDD100010);
                font.drawStringWithShadow(str, px - mainW / 2 - w - 1, py - font.lineHeight() / 2, color);
            }
            if (atom.above()
                .isPresent()
                && !atom.above()
                    .get()
                    .element()
                    .invisible()) {
                String str = atom.above()
                    .get()
                    .toString();
                int color = atom.above()
                    .get()
                    .element()
                    .color();
                int w = font.width(str);
                canvas.fill(
                    px - w / 2 - 1,
                    py - font.lineHeight() * 3 / 2,
                    px + w / 2 + 1,
                    py - font.lineHeight() / 2,
                    0xDD100010);
                font.drawStringWithShadow(str, px - w / 2, py - font.lineHeight() * 3 / 2, color);
            }
            if (atom.below()
                .isPresent()
                && !atom.below()
                    .get()
                    .element()
                    .invisible()) {
                String str = atom.below()
                    .get()
                    .toString();
                int color = atom.below()
                    .get()
                    .element()
                    .color();
                int w = font.width(str);
                canvas.fill(
                    px - w / 2 - 1,
                    py + font.lineHeight() / 2,
                    px + w / 2 + 1,
                    py + font.lineHeight() * 3 / 2,
                    0xDD100010);
                font.drawStringWithShadow(str, px - w / 2, py + font.lineHeight() / 2 + 1, color);
            }
        } else if (elem instanceof Parens pp) {
            final var bounds = this.molecule.subset(pp.atoms())
                .boundsWithSize(xyz -> toScaled.apply(project(xyz)), sizeOfAtomFactory(font.lineHeight()));
            final var xySub = new Vector2i((int) bounds.getSecond().x, (int) bounds.getSecond().y);
            xySub.add(7, -2);
            font.drawStringWithShadow(pp.sub(), x + xySub.x, y + xySub.y, defaultColor);
            final var xySup = new Vector2i((int) bounds.getSecond().x, (int) bounds.getFirst().y);
            xySup.add(7, -4);
            font.drawStringWithShadow(pp.sup(), x + xySup.x, y + xySup.y, defaultColor);
        } else if (elem instanceof Group composite) {
            for (final var child : composite.children()) {
                renderOneText(child, font, x, y, toScaled, defaultColor);
            }
        }
    }

    private static Vector2i floored(Vector2f vec) {
        return new Vector2i((int) Math.floor(vec.x), (int) Math.floor(vec.y));
    }
}
