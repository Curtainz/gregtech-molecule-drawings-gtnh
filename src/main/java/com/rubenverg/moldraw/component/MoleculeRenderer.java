package com.rubenverg.moldraw.component;

import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.MathHelper;

import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.rubenverg.moldraw.MolDrawConfig;
import com.rubenverg.moldraw.MoleculeColorize;
import com.rubenverg.moldraw.molecule.*;
import com.rubenverg.moldraw.util.Pair;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public class MoleculeRenderer implements ITooltipDrawable {

    private final Molecule molecule;
    private final Vector2i xySize;
    private final Vector2f xyStart;
    private final boolean atomAtTop;
    private final boolean atomAtTopTop;
    private final boolean atomAtBotBot;
    private final boolean atomAtLefLef;
    private final boolean parenAtLef;
    private final List<Vector3f> centers;
    private final Map<Element.Counted, Integer> elementWidths = new HashMap<>();

    public MoleculeRenderer(Molecule molecule) {
        this.molecule = molecule;
        final var bounds = molecule.bounds();
        final Vector2f diff = new Vector2f();
        bounds.getSecond()
            .sub(bounds.getFirst(), diff);
        diff.mul(MolDrawConfig.INSTANCE.molecule.moleculeScale);
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

        centers = molecule.spinGroups()
            .doubleStream()
            .mapToObj(freq -> new Vector3f(0, 0, 0))
            .toList();
        final IntList counts = molecule.spinGroups()
            .doubleStream()
            .mapToObj(freq -> 0)
            .collect(Collectors.toCollection(IntArrayList::new));
        for (final var elem : this.molecule.contents()) {
            if (elem instanceof Atom atom) {
                if (atom.spinGroup() >= 0 && atom.spinGroup() < centers.size()) {
                    centers.get(atom.spinGroup())
                        .add(atom.position());
                    counts.set(atom.spinGroup(), counts.getInt(atom.spinGroup()) + 1);
                }
            }
        }
        IntStream.range(0, centers.size())
            .forEach(i -> {
                int c = counts.getInt(i);
                if (c > 0) centers.get(i)
                    .div(c);
            });
    }

    private UnaryOperator<Vector2f> toScaledFactory(int lineHeight) {
        final int topPad = 12 + (atomAtTopTop ? lineHeight * 2 + 6 : atomAtTop ? lineHeight + 4 : 4);
        final int leftPad = 12 + (atomAtLefLef ? 14 : 0) + (parenAtLef ? 8 : 0);
        return xy -> {
            var result = new Vector2f();
            new Vector2f(xy.x, xy.y).sub(xyStart, result);
            result.mul(MolDrawConfig.INSTANCE.molecule.moleculeScale);
            return new Vector2f(result.x + leftPad, -result.y + topPad);
        };
    }

    private Vector2f project(Vector3fc xyz, int group) {
        final var vec = new Vector3f(xyz);
        if (MolDrawConfig.INSTANCE.molecule.spinMolecules && group >= 0
            && group < molecule.spinGroups()
                .size()) {
            final var freq = 1000 / (molecule.spinGroups()
                .getFloat(group) * MolDrawConfig.INSTANCE.molecule.spinSpeedMultiplier);
            vec.sub(centers.get(group));
            vec.mul(
                new Matrix3f().rotationY((float) (System.currentTimeMillis() % (long) freq / freq * (Math.PI * 2))));
            vec.add(centers.get(group));
        }
        return new Vector2f(vec.x, vec.y);
    }

    public int getWidth(FontRenderer font) {
        return xySize.x + 36 + (atomAtLefLef ? 14 : 0) + (parenAtLef ? 10 : 0);
    }

    public int getHeight(FontRenderer font) {
        return xySize.y + 32 + (atomAtBotBot ? 16 : 0) + (atomAtTopTop ? 22 : atomAtTop ? 14 : 6);
    }

    public void render(FontRenderer font, int x, int y) {
        final var ts = toScaledFactory(font.FONT_HEIGHT);
        final int defaultColor = MoleculeColorize.configColor(null);

        // 阶段 1：先测量所有原子的文字尺寸（仅测量，不绘制），填入 elementWidths
        elementWidths.clear();
        for (final var elem : this.molecule.contents()) {
            measureText(elem, font);
        }

        // 阶段 2：绘制底层骨架结构（化学键、圆环、括号），依赖准确的 elementWidths 避让
        for (final var elem : this.molecule.contents()) {
            renderOneImage(elem, font, x, y, ts, defaultColor);
        }

        // 阶段 3：绘制顶层文字（盖在连线之上），文字永不被黄线覆盖！
        for (final var elem : this.molecule.contents()) {
            renderOneText(elem, font, x, y, ts, defaultColor);
        }
    }

    private void measureText(MoleculeElement<?> elem, FontRenderer font) {
        if (elem instanceof Atom atom) {
            if (!atom.element()
                .element().invisible) {
                int width = font.getStringWidth(
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
                    font.getStringWidth(
                        atom.right()
                            .get()
                            .toString()));
            }
            if (atom.left()
                .isPresent()) {
                elementWidths.put(
                    atom.left()
                        .get(),
                    font.getStringWidth(
                        atom.left()
                            .get()
                            .toString()));
            }
            if (atom.above()
                .isPresent()) {
                elementWidths.put(
                    atom.above()
                        .get(),
                    font.getStringWidth(
                        atom.above()
                            .get()
                            .toString()));
            }
            if (atom.below()
                .isPresent()) {
                elementWidths.put(
                    atom.below()
                        .get(),
                    font.getStringWidth(
                        atom.below()
                            .get()
                            .toString()));
            }
        } else if (elem instanceof CompositeElement<?>composite) {
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

    private void renderOneImage(MoleculeElement<?> elem, FontRenderer font, int x, int y,
        UnaryOperator<Vector2f> toScaled, int defaultColor) {
        if (elem instanceof Bond bond) {
            if (this.molecule.getAtom(bond.a())
                .isEmpty()
                || this.molecule.getAtom(bond.b())
                    .isEmpty())
                return;

            final var atomA = this.molecule.getAtom(bond.a())
                .orElseThrow();
            final var atomB = this.molecule.getAtom(bond.b())
                .orElseThrow();

            final int atomAWidth = elementWidths.getOrDefault(atomA.element(), 0);
            final int atomBWidth = elementWidths.getOrDefault(atomB.element(), 0);
            final boolean atomAInvisible = atomA.isInvisible();
            final boolean atomBInvisible = atomB.isInvisible();

            final var start = floored(toScaled.apply(project(atomA.position(), atomA.spinGroup())));
            start.add(x, y);
            start.add(0, font.FONT_HEIGHT / 2);

            final var end = floored(toScaled.apply(project(atomB.position(), atomB.spinGroup())));
            end.add(x, y);
            end.add(0, font.FONT_HEIGHT / 2);

            final GraphicalUtils.PixelPredicate notCloseToAtom = (xt, yt, _c) -> {
                if (atomAInvisible && atomBInvisible) return true;
                Vector2ic t = new Vector2i(xt, yt);
                var diffA = new Vector2i();
                start.sub(t, diffA);
                diffA.absolute();
                if (diffA.x < atomAWidth * 2 / 3 && (!atomAInvisible && diffA.y < font.FONT_HEIGHT * 2 / 3))
                    return false;

                var diffB = new Vector2i();
                end.sub(t, diffB);
                diffB.absolute();
                if (diffB.x < atomBWidth * 2 / 3 && (!atomBInvisible && diffB.y < font.FONT_HEIGHT * 2 / 3))
                    return false;

                return true;
            };

            int colorA = MoleculeColorize.getColorForElement(
                atomA.element()
                    .element());
            int colorB = MoleculeColorize.getColorForElement(
                atomB.element()
                    .element());
            IntBinaryOperator colorOp = (xp, yp) -> {
                double d2a = Math.pow(xp - start.x, 2) + Math.pow(yp - start.y, 2);
                double d2b = Math.pow(xp - end.x, 2) + Math.pow(yp - end.y, 2);
                return d2a < d2b ? colorA : colorB;
            };

            final float dy = end.y - start.y;
            final float dx = end.x - start.x;
            final float length = (float) Math.hypot(dx, dy);
            if (length == 0) return;

            final float nx = -dy / length;
            final float ny = dx / length;
            final float ux = dx / length;
            final float uy = dy / length;

            final int lineCount = bond.lines().length;
            final float separation = 3.6f;

            for (int i = 0; i < lineCount; i++) {
                float offset;
                float shorten = 0.0f;
                if (lineCount == 1) {
                    offset = 0.0f;
                } else if (lineCount == 2) {
                    if (bond.centered()) {
                        offset = (i == 0 ? -1.0f : 1.0f) * (separation / 2.0f);
                    } else {
                        // 环内双键或不对称双键：第一条为骨架主键(0偏移)，第二条为内侧副键(偏移+两端微缩进2.5px以防与拐角重叠)
                        if (i == 0) {
                            offset = 0.0f;
                        } else {
                            offset = separation;
                            shorten = 2.5f;
                        }
                    }
                } else if (lineCount == 3) {
                    offset = (i - 1) * separation;
                    shorten = (i == 1) ? 0.0f : 2.5f;
                } else {
                    offset = (i - (lineCount - 1) / 2.0f) * separation;
                }

                int sX = Math.round(nx * offset);
                int sY = Math.round(ny * offset);
                // 确保只要 offset != 0，至少有 1 个像素偏移，绝不与主键重叠合并
                if (offset != 0 && sX == 0 && sY == 0) {
                    if (Math.abs(nx) >= Math.abs(ny)) {
                        sX = offset > 0 ? 1 : -1;
                    } else {
                        sY = offset > 0 ? 1 : -1;
                    }
                }

                int lx0 = Math.round(start.x + sX + ux * shorten);
                int ly0 = Math.round(start.y + sY + uy * shorten);
                int lx1 = Math.round(end.x + sX - ux * shorten);
                int ly1 = Math.round(end.y + sY - uy * shorten);

                Bond.Line line = bond.lines()[i];
                if (line == Bond.Line.SOLID) {
                    GraphicalUtils.plotLine(lx0, ly0, lx1, ly1, notCloseToAtom, colorOp);
                } else if (line == Bond.Line.DOTTED) {
                    GraphicalUtils
                        .plotLine(lx0, ly0, lx1, ly1, notCloseToAtom.and((_x, _y, count) -> count % 4 < 2), colorOp);
                } else {
                    GraphicalUtils.plotLine(lx0, ly0, lx1, ly1, notCloseToAtom, colorOp);
                }
            }
        } else if (elem instanceof Parens pp) {
            // 使用静态无自转投影 project(xyz, -1)，确保外括号与原点严格锁定，不随旋转晃动！
            final var bounds = this.molecule.subset(pp.atoms())
                .boundsWithSize(xyz -> toScaled.apply(project(xyz, -1)), sizeOfAtomFactory(font.FONT_HEIGHT));
            final int minX = MathHelper.floor_float(bounds.getFirst().x) + x - 8;
            final int minY = MathHelper.floor_float(bounds.getFirst().y) + y - 4;
            final int maxX = MathHelper.floor_float(bounds.getSecond().x) + x + 8;
            final int maxY = MathHelper.floor_float(bounds.getSecond().y) + y + 4;
            final int arm = 6; // 方括号横线向内伸展臂长

            // 左方括号 [ ：竖线在 minX，上下横线向内(右)伸展
            GraphicalUtils.plotLine(minX, minY, minX + arm, minY, GraphicalUtils::alwaysDraw, (_x, _y) -> defaultColor);
            GraphicalUtils.plotLine(minX, maxY, minX + arm, maxY, GraphicalUtils::alwaysDraw, (_x, _y) -> defaultColor);
            GraphicalUtils.plotLine(minX, minY, minX, maxY, GraphicalUtils::alwaysDraw, (_x, _y) -> defaultColor);

            // 右方括号 ] ：竖线在 maxX，上下横线向内(左)伸展
            GraphicalUtils.plotLine(maxX - arm, minY, maxX, minY, GraphicalUtils::alwaysDraw, (_x, _y) -> defaultColor);
            GraphicalUtils.plotLine(maxX - arm, maxY, maxX, maxY, GraphicalUtils::alwaysDraw, (_x, _y) -> defaultColor);
            GraphicalUtils.plotLine(maxX, minY, maxX, maxY, GraphicalUtils::alwaysDraw, (_x, _y) -> defaultColor);
        } else if (elem instanceof CircleTransformation ct) {
            for (int atomIdx : ct.atoms()) {
                final var atom = this.molecule.getAtom(atomIdx)
                    .orElse(null);
                if (atom != null) {
                    final var pos = toScaled.apply(project(atom.position(), atom.spinGroup()));
                    final int cx = MathHelper.floor_float(pos.x) + x;
                    final int cy = MathHelper.floor_float(pos.y) + y + font.FONT_HEIGHT / 2;
                    GraphicalUtils.plotCircle(cx, cy, 3, GraphicalUtils::alwaysDraw, (_x, _y) -> defaultColor);
                }
            }
        } else if (elem instanceof CompositeElement<?>composite) {
            for (final var child : composite.children()) {
                renderOneImage(child, font, x, y, toScaled, defaultColor);
            }
        }
    }

    private void renderOneText(MoleculeElement<?> elem, FontRenderer font, int x, int y,
        UnaryOperator<Vector2f> toScaled, int defaultColor) {
        if (elem instanceof Atom atom) {
            final var pos = toScaled.apply(project(atom.position(), atom.spinGroup()));
            final int px = MathHelper.floor_float(pos.x) + x;
            final int py = MathHelper.floor_float(pos.y) + y + font.FONT_HEIGHT / 2;

            if (!atom.element()
                .element().invisible) {
                String symbol = atom.element()
                    .toString();
                int width = font.getStringWidth(symbol);
                int color = MoleculeColorize.getColorForElement(
                    atom.element()
                        .element());
                GraphicalUtils.fill(
                    px - width / 2 - 1,
                    py - font.FONT_HEIGHT / 2,
                    px + width / 2 + 1,
                    py + font.FONT_HEIGHT / 2,
                    0xDD100010);
                font.drawStringWithShadow(symbol, px - width / 2, py - font.FONT_HEIGHT / 2, color);
            }

            if (atom.right()
                .isPresent()
                && !atom.right()
                    .get()
                    .element().invisible) {
                String str = atom.right()
                    .get()
                    .toString();
                int color = MoleculeColorize.getColorForElement(
                    atom.right()
                        .get()
                        .element());
                int mainW = elementWidths.getOrDefault(atom.element(), 0);
                int w = font.getStringWidth(str);
                GraphicalUtils.fill(
                    px + mainW / 2,
                    py - font.FONT_HEIGHT / 2,
                    px + mainW / 2 + w + 2,
                    py + font.FONT_HEIGHT / 2,
                    0xDD100010);
                font.drawStringWithShadow(str, px + mainW / 2 + 1, py - font.FONT_HEIGHT / 2, color);
            }
            if (atom.left()
                .isPresent()
                && !atom.left()
                    .get()
                    .element().invisible) {
                String str = atom.left()
                    .get()
                    .toString();
                int color = MoleculeColorize.getColorForElement(
                    atom.left()
                        .get()
                        .element());
                int w = font.getStringWidth(str);
                int mainW = elementWidths.getOrDefault(atom.element(), 0);
                GraphicalUtils.fill(
                    px - mainW / 2 - w - 2,
                    py - font.FONT_HEIGHT / 2,
                    px - mainW / 2,
                    py + font.FONT_HEIGHT / 2,
                    0xDD100010);
                font.drawStringWithShadow(str, px - mainW / 2 - w - 1, py - font.FONT_HEIGHT / 2, color);
            }
            if (atom.above()
                .isPresent()
                && !atom.above()
                    .get()
                    .element().invisible) {
                String str = atom.above()
                    .get()
                    .toString();
                int color = MoleculeColorize.getColorForElement(
                    atom.above()
                        .get()
                        .element());
                int w = font.getStringWidth(str);
                GraphicalUtils.fill(
                    px - w / 2 - 1,
                    py - font.FONT_HEIGHT * 3 / 2,
                    px + w / 2 + 1,
                    py - font.FONT_HEIGHT / 2,
                    0xDD100010);
                font.drawStringWithShadow(str, px - w / 2, py - font.FONT_HEIGHT * 3 / 2, color);
            }
            if (atom.below()
                .isPresent()
                && !atom.below()
                    .get()
                    .element().invisible) {
                String str = atom.below()
                    .get()
                    .toString();
                int color = MoleculeColorize.getColorForElement(
                    atom.below()
                        .get()
                        .element());
                int w = font.getStringWidth(str);
                GraphicalUtils.fill(
                    px - w / 2 - 1,
                    py + font.FONT_HEIGHT / 2,
                    px + w / 2 + 1,
                    py + font.FONT_HEIGHT * 3 / 2,
                    0xDD100010);
                font.drawStringWithShadow(str, px - w / 2, py + font.FONT_HEIGHT / 2 + 1, color);
            }
        } else if (elem instanceof Parens pp) {
            // 使用静态包围盒计算下标与上标位置，严格锁定在右方括号 ] 右侧，纹丝不动！
            final var bounds = this.molecule.subset(pp.atoms())
                .boundsWithSize(xyz -> toScaled.apply(project(xyz, -1)), sizeOfAtomFactory(font.FONT_HEIGHT));
            final int maxX = MathHelper.floor_float(bounds.getSecond().x) + x + 8;
            final int maxY = MathHelper.floor_float(bounds.getSecond().y) + y + 4;
            final int minY = MathHelper.floor_float(bounds.getFirst().y) + y - 4;
            if (!pp.sub()
                .isEmpty()) {
                final int subX = maxX + 4;
                final int subY = maxY - font.FONT_HEIGHT + 2;
                font.drawStringWithShadow(pp.sub(), subX, subY, defaultColor);
            }
            if (!pp.sup()
                .isEmpty()) {
                final int supX = maxX + 4;
                final int supY = minY - 2;
                font.drawStringWithShadow(pp.sup(), supX, supY, defaultColor);
            }
        } else if (elem instanceof CompositeElement<?>composite) {
            for (final var child : composite.children()) {
                renderOneText(child, font, x, y, toScaled, defaultColor);
            }
        }
    }

    private static Vector2i floored(Vector2f vec) {
        return new Vector2i(MathHelper.floor_float(vec.x), MathHelper.floor_float(vec.y));
    }
}
