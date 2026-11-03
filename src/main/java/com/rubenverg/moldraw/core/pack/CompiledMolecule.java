package com.rubenverg.moldraw.core.pack;

import java.util.*;
import java.util.function.Function;

import com.rubenverg.moldraw.core.MoleculeScene;

/** Immutable, fully expanded graph including chemical metadata and canonical shared atom IDs. */
public record CompiledMolecule(String id, List<Atom> atoms, List<Bond> bonds, List<Annotation> annotations,
    Map<String, String> ports) {

    public record Decoration(String text, String element, String color, boolean optionalColor) {

        public Decoration(String text, String element, String color) {
            this(text, element, color, false);
        }
    }

    public record Style(String element, String label, String mode, Integer hydrogens, Integer isotope, int charge,
        String color, Map<String, Decoration> labels, boolean optionalColor) {

        public Style(String element, String label, String mode, Integer hydrogens, Integer isotope, int charge,
            String color, Map<String, Decoration> labels) {
            this(element, label, mode, hydrogens, isotope, charge, color, labels, false);
        }

        public Style {
            labels = Map.copyOf(labels);
        }
    }

    public record Atom(String id, double x, double y, Style style) {}

    public record Bond(String from, String to, boolean centered, List<MoleculeScene.Line> lines) {

        public Bond {
            lines = List.copyOf(lines);
        }
    }

    public record Annotation(String type, List<String> atoms, String sub, String sup, String text, double x, double y,
        List<Double> matrix, String color) {

        public Annotation {
            atoms = List.copyOf(atoms);
            matrix = List.copyOf(matrix);
        }
    }

    public CompiledMolecule {
        atoms = List.copyOf(atoms);
        bonds = List.copyOf(bonds);
        annotations = List.copyOf(annotations);
        ports = Collections.unmodifiableMap(new TreeMap<>(ports));
    }

    public MoleculeScene scene(Function<String, Integer> colors) {
        return scene(colors, true);
    }

    public MoleculeScene scene(Function<String, Integer> colors, boolean useOptionalColors) {
        List<MoleculeScene.Shape> shapes = new ArrayList<>();
        Map<String, Integer> indices = new HashMap<>();
        int i = 0;
        for (var atom : atoms) {
            indices.put(atom.id, i);
            Style s = atom.style;
            int color = color(s.optionalColor && !useOptionalColors ? null : s.color, s.element, colors);
            String text = s.label != null ? s.label : s.element == null ? "" : s.element;
            if (s.label == null) {
                if (s.isotope != null) text = superscript(s.isotope.toString()) + text;
                if (s.mode.equals("symbol") && s.hydrogens != null && s.hydrogens > 0)
                    text += "H" + (s.hydrogens == 1 ? "" : subscript(s.hydrogens.toString()));
                if (s.charge != 0)
                    text += superscript((Math.abs(s.charge) == 1 ? "" : String.valueOf(Math.abs(s.charge))))
                        + (s.charge < 0 ? "⁻" : "⁺");
            }
            boolean hidden = s.mode.equals("hidden")
                || (s.mode.equals("skeletal") && "C".equals(s.element) && s.label == null)
                || text.isEmpty();
            shapes.add(
                new MoleculeScene.Atom(
                    i++,
                    new MoleculeScene.Label(text, hidden, color),
                    decoration(s, "above", color, colors, useOptionalColors),
                    decoration(s, "right", color, colors, useOptionalColors),
                    decoration(s, "below", color, colors, useOptionalColors),
                    decoration(s, "left", color, colors, useOptionalColors),
                    (float) atom.x,
                    (float) atom.y,
                    0));
        }
        for (var bond : bonds)
            shapes.add(new MoleculeScene.Bond(indices.get(bond.from), indices.get(bond.to), bond.centered, bond.lines));
        for (var a : annotations) {
            List<Integer> refs = a.atoms.stream()
                .map(indices::get)
                .toList();
            int color = color(a.color, null, colors);
            switch (a.type) {
                case "bracket" -> shapes
                    .add(new MoleculeScene.Parens(a.sub, a.sup, refs, a.color == null ? null : color));
                case "ellipse" -> shapes.add(
                    new MoleculeScene.CircleTransformation(
                        a.matrix.get(0)
                            .floatValue(),
                        a.matrix.get(1)
                            .floatValue(),
                        a.matrix.get(2)
                            .floatValue(),
                        a.matrix.get(3)
                            .floatValue(),
                        refs,
                        a.color == null ? null : color));
                case "text" -> shapes.add(
                    new MoleculeScene.TextAnnotation(a.text, (float) a.x, (float) a.y, a.color == null ? null : color));
                default -> throw new PackException("Unknown annotation " + a.type);
            }
        }
        return new MoleculeScene(shapes);
    }

    private static Optional<MoleculeScene.Label> decoration(Style style, String side, int inherited,
        Function<String, Integer> colors, boolean useOptionalColors) {
        Decoration d = style.labels.get(side);
        return d == null ? Optional.empty()
            : Optional.of(
                new MoleculeScene.Label(
                    d.text,
                    false,
                    d.color == null && d.element == null ? inherited
                        : color(d.optionalColor && !useOptionalColors ? null : d.color, d.element, colors)));
    }

    private static int color(String literal, String element, Function<String, Integer> colors) {
        return literal != null
            ? (int) (Long.parseLong(literal.substring(1), 16) | (literal.length() == 7 ? 0xFF000000L : 0))
            : colors.apply(element == null ? "" : element);
    }

    private static String superscript(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) out.append("⁰¹²³⁴⁵⁶⁷⁸⁹".charAt(c - '0'));
        return out.toString();
    }

    private static String subscript(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) out.append((char) ('₀' + c - '0'));
        return out.toString();
    }
}
