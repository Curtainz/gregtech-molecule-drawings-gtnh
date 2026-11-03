package com.rubenverg.moldraw.core;

import java.util.*;
import java.util.function.Function;

import org.joml.*;

import com.rubenverg.moldraw.util.Pair;

/** Frozen geometry. Mutable JOML values are created only as temporary calculations. */
public record MoleculeScene(List<Shape> contents) {

    public MoleculeScene {
        contents = flatten(contents, 0);
        if (contents.size() > 16_384) throw new IllegalArgumentException("Too many geometry elements");
    }

    private static List<Shape> flatten(List<Shape> shapes, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Geometry nesting exceeds 32");
        List<Shape> flat = new ArrayList<>();
        for (var shape : shapes) {
            if (shape instanceof Group group) flat.addAll(flatten(group.children(), depth + 1));
            else flat.add(shape);
            if (flat.size() > 16_384) throw new IllegalArgumentException("Too many geometry elements");
        }
        return List.copyOf(flat);
    }

    public sealed interface Shape permits Atom,Bond,Parens,CircleTransformation,Group,TextAnnotation {
    }

    public record TextAnnotation(String text, float x, float y, Integer color) implements Shape {}

    public record Label(String text, boolean invisible, int color) {

        public Label element() {
            return this;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public record Atom(int index, Label element, Optional<Label> above, Optional<Label> right, Optional<Label> below,
        Optional<Label> left, float x, float y, float z) implements Shape {

        public Atom {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
                throw new IllegalArgumentException("Non-finite atom");
        }

        public boolean isInvisible() {
            return element.invisible();
        }

        public Vector3f position() {
            return new Vector3f(x, y, z);
        }
    }

    public record Bond(int a, int b, boolean centered, List<Line> strokes) implements Shape {

        public Bond {
            strokes = List.copyOf(strokes);
        }

        public Line[] lines() {
            return strokes.toArray(Line[]::new);
        }

        public int totalThickness() {
            return strokes.stream()
                .mapToInt(l -> l.thick ? 3 : 1)
                .sum();
        }
    }

    public enum Line {

        SOLID(false),
        DOTTED(false),
        THICK(true),
        INWARD(true),
        OUTWARD(true);

        public final boolean thick;

        Line(boolean thick) {
            this.thick = thick;
        }
    }

    public record Parens(String sub, String sup, List<Integer> indices, Integer color) implements Shape {

        public Parens(String sub, String sup, List<Integer> indices) {
            this(sub, sup, indices, null);
        }

        public Parens {
            indices = List.copyOf(indices);
        }

        public int[] atoms() {
            return indices.stream()
                .mapToInt(i -> i)
                .toArray();
        }
    }

    public record CircleTransformation(float a00, float a01, float a10, float a11, List<Integer> indices, Integer color)
        implements Shape {

        public CircleTransformation(float a00, float a01, float a10, float a11, List<Integer> indices) {
            this(a00, a01, a10, a11, indices, null);
        }

        public CircleTransformation {
            indices = List.copyOf(indices);
        }

        public int[] atoms() {
            return indices.stream()
                .mapToInt(i -> i)
                .toArray();
        }

        public Matrix2f A() {
            return new Matrix2f(a00, a01, a10, a11);
        }
    }

    public record Group(List<Shape> children) implements Shape {

        public Group {
            children = List.copyOf(children);
        }
    }

    public List<Shape> flatChildren() {
        return contents;
    }

    public List<Atom> atoms() {
        return contents.stream()
            .filter(s -> s instanceof Atom)
            .map(s -> (Atom) s)
            .toList();
    }

    public Optional<Atom> getAtom(int id) {
        return atoms().stream()
            .filter(a -> a.index() == id)
            .findFirst();
    }

    public MoleculeScene subset(int... indices) {
        return new MoleculeScene(
            atoms().stream()
                .filter(
                    a -> Arrays.stream(indices)
                        .anyMatch(i -> i == a.index()))
                .map(a -> (Shape) a)
                .toList());
    }

    public Pair<Vector2f, Vector2f> bounds() {
        return boundsWithSize(v -> new Vector2f(v.x, v.y), a -> new Pair<>(new Vector2f(), new Vector2f()));
    }

    public Pair<Vector2f, Vector2f> boundsWithSize(Function<Vector3f, Vector2f> translate,
        Function<Atom, Pair<Vector2f, Vector2f>> sizes) {
        if (atoms().isEmpty()) return new Pair<>(new Vector2f(), new Vector2f());
        Vector2f min = new Vector2f(Float.POSITIVE_INFINITY), max = new Vector2f(Float.NEGATIVE_INFINITY);
        for (var a : atoms()) {
            var p = translate.apply(a.position());
            var size = sizes.apply(a);
            min.min(new Vector2f(p).sub(size.getFirst()));
            max.max(new Vector2f(p).add(size.getSecond()));
        }
        return new Pair<>(min, max);
    }
}
