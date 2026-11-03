package com.rubenverg.moldraw.core;

import java.math.BigInteger;
import java.util.*;

import com.rubenverg.moldraw.core.MaterialDefinition.*;
import com.rubenverg.moldraw.core.MoleculeScene.*;

public final class ArchitectureRegression {

    private static final Map<String, MaterialDefinition> definitions = new LinkedHashMap<>();
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static Fraction fraction(long a, long b) {
        return new Fraction(BigInteger.valueOf(a), BigInteger.valueOf(b));
    }

    private static Component part(String id, long amount) {
        return new Component(id, BigInteger.valueOf(amount));
    }

    private static void define(String id, long mass, Component... parts) {
        definitions.put(
            id,
            new MaterialDefinition(
                id,
                id,
                "",
                "",
                mass > 0 ? Fraction.of(mass) : null,
                0xFFFFFFFF,
                State.PRESENT,
                Basis.MIXTURE_PARTS,
                List.of(parts),
                "fixture",
                List.of()));
    }

    private static CompositionResolver.Result resolve(String id, boolean mass, boolean recursive) {
        return new CompositionResolver(definitions::get).resolve(id, mass, recursive);
    }

    private static void sum(CompositionResolver.Result r) {
        check(r.valid(), "composition invalid: " + r.diagnostics());
        check(
            r.shares()
                .values()
                .stream()
                .reduce(Fraction.ZERO, Fraction::add)
                .equals(Fraction.ONE),
            "shares do not sum to one");
    }

    public static void main(String[] args) {
        define("A", 1);
        define("B", 2);
        define("C", 3);
        define("F", 18, part("A", 2), part("B", 1));
        define("mix", 21, part("F", 1), part("C", 1));
        var quantity = resolve("mix", false, true);
        sum(quantity);
        check(
            quantity.shares()
                .equals(Map.of("A", fraction(1, 3), "B", fraction(1, 6), "C", fraction(1, 2))),
            "nested node inflated");
        var mass = resolve("mix", true, true);
        sum(mass);
        check(
            mass.shares()
                .equals(Map.of("A", fraction(3, 7), "B", fraction(3, 7), "C", fraction(1, 7))),
            "mass conversion repeated");
        check(
            resolve("mix", false, false).shares()
                .equals(Map.of("F", fraction(1, 2), "C", fraction(1, 2))),
            "recursive off");
        BigInteger huge = BigInteger.TEN.pow(100);
        definitions.put(
            "huge",
            new MaterialDefinition(
                "huge",
                "huge",
                "",
                "",
                null,
                0,
                State.PRESENT,
                Basis.STOICHIOMETRIC,
                List.of(new Component("A", huge), new Component("B", huge)),
                "fixture",
                List.of()));
        check(
            resolve("huge", true, true).shares()
                .equals(Map.of("A", fraction(1, 3), "B", fraction(2, 3))),
            "large weights overflow");
        check(
            ChemicalFormula.parseExact("(H999999999999999999999999999999999999)99")
                .get("H")
                .equals(new BigInteger("999999999999999999999999999999999999").multiply(BigInteger.valueOf(99))),
            "formula narrowed to long");
        define("unknown", 0);
        define("badMass", 1, part("unknown", 1), part("A", 1));
        check(
            !resolve("badMass", true, true).valid() && resolve("badMass", true, true).shares()
                .isEmpty(),
            "fabricated unknown mass");
        check(
            resolve("badMass", true, true).diagnostics()
                .contains("MISSING_MASS:unknown"),
            "missing mass diagnostic");
        define("symbols", 0, part("symbol:Xx", 1), part("symbol:Yy", 1));
        check(
            resolve("symbols", false, true).shares()
                .size() == 2,
            "unknown symbols merged");
        define("cycleX", 1, part("cycleY", 1));
        define("cycleY", 1, part("cycleX", 1));
        var cycle = resolve("cycleX", false, true);
        sum(cycle);
        check(
            cycle.diagnostics()
                .contains("CYCLE:cycleX"),
            "cycle lost");
        for (int i = 0; i < 35; i++) define("depth" + i, 1, part("depth" + (i + 1), 1));
        var depth = resolve("depth0", false, true);
        sum(depth);
        check(
            depth.diagnostics()
                .contains("DEPTH_LIMIT:depth32"),
            "depth bound");
        definitions.put(
            "invalid",
            new MaterialDefinition(
                "invalid",
                "invalid",
                "?",
                "",
                Fraction.ONE,
                0,
                State.INVALID,
                Basis.STOICHIOMETRIC,
                List.of(),
                "fixture",
                List.of("INVALID_FORMULA")));
        var invalid = resolve("invalid", false, true);
        sum(invalid);
        check(
            invalid.diagnostics()
                .contains("INVALID_FORMULA"),
            "invalid terminal reason");
        definitions.put(
            "disabled",
            new MaterialDefinition(
                "disabled",
                "disabled",
                "",
                "",
                null,
                0,
                State.DISABLED,
                Basis.MIXTURE_PARTS,
                List.of(),
                "fixture",
                List.of()));
        check(!resolve("disabled", false, true).valid(), "explicit disabled ignored");
        var shares = Map.of("a", fraction(1, 2), "b", fraction(1, 4), "c", fraction(1, 8), "d", fraction(1, 8));
        check(
            CompositionResolver.fold(shares, 1, 0)
                .equals(Map.of("gtmoldraw:other", Fraction.ONE)),
            "single display slot");
        var folded = CompositionResolver.fold(shares, 3, 0);
        check(
            folded.size() == 3 && folded.get("gtmoldraw:other")
                .equals(fraction(1, 4)),
            "other exceeds slot budget");
        check(
            CompositionResolver.fold(shares, 10, 13)
                .get("gtmoldraw:other")
                .equals(fraction(1, 4)),
            "percentage folding");
        var cache = new BoundedCache<String, Integer>(2);
        int[] loads = { 0 };
        cache.get("a", k -> ++loads[0]);
        cache.get("b", k -> ++loads[0]);
        cache.get("a", k -> ++loads[0]);
        cache.get("c", k -> ++loads[0]);
        cache.get("b", k -> ++loads[0]);
        check(cache.size() == 2 && loads[0] == 4, "LRU eviction");
        cache.clear();
        check(cache.size() == 0, "cache clear");
        massMetadata();
        resourceAdapters();
        var limited = new TooltipLayout.Canvas(FONT);
        try {
            for (int i = 0; i < 33; i++)
                limited.plotLine(0, 0, 8192, 0, PixelRaster.PixelPredicate::always, (x, y) -> 0);
            throw new AssertionError("pixel budget unbounded");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
        layouts();
        System.out.println("Architecture regression: " + assertions + " assertions passed");
    }

    private static void massMetadata() {
        Map<String, MassResolver.Node> nodes = new HashMap<>();
        nodes.put("H", new MassResolver.Node(Fraction.ONE, Fraction.ONE, List.of(), false));
        nodes.put("O", new MassResolver.Node(Fraction.of(16), Fraction.ONE, List.of(), false));
        nodes.put("water", new MassResolver.Node(null, Fraction.ONE, List.of(part("H", 2), part("O", 1)), false));
        nodes.put("gtAverage", new MassResolver.Node(null, fraction(3, 2), List.of(part("H", 2), part("O", 1)), true));
        var resolver = new MassResolver(nodes::get);
        check(
            resolver.resolve("water")
                .mass()
                .equals(Fraction.of(18)),
            "formula mass");
        check(
            resolver.resolve("gtAverage")
                .mass()
                .equals(Fraction.of(9)),
            "GT density/average mass");
        nodes.put("unknown", new MassResolver.Node(null, Fraction.ONE, List.of(), true));
        check(
            resolver.resolve("unknown")
                .mass() == null,
            "GT default element mass substituted");
        nodes.put("cycle", new MassResolver.Node(null, Fraction.ONE, List.of(part("cycle", 1)), true));
        check(
            resolver.resolve("cycle")
                .mass() == null,
            "mass recursion not bounded");
        check(
            !resolver.resolve("cycle")
                .diagnostics()
                .isEmpty(),
            "mass cycle diagnostic lost");
        var shares = Map.of("A", fraction(1, 8), "B", fraction(7, 8));
        check(
            CompositionResolver.fold(shares, 5, 12.5000001)
                .containsKey("gtmoldraw:other"),
            "threshold prematurely rounded");
        for (int i = 0; i < 16; i++) define("branch" + i, 1, part("branch" + (i + 1), 1), part("branch" + (i + 1), 1));
        define("branch16", 1);
        var large = resolve("branch0", false, true);
        check(
            !large.valid() && large.diagnostics()
                .stream()
                .anyMatch(d -> d.startsWith("WORK_LIMIT:")),
            "unbounded graph work");
    }

    private static com.google.gson.JsonElement json(String text) {
        try {
            return ResourceJson
                .read(new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.io.IOException bad) {
            throw new IllegalArgumentException(bad);
        }
    }

    private static void resourceAdapters() {
        var legacy = LegacyComposition
            .decode(json("{\"components\":[\"a\",[\"b\",2],{\"material\":\"c\",\"amount\":3}]}"), "fixture");
        check(
            legacy.state() == State.PRESENT && legacy.value()
                .equals(List.of(part("a", 1), part("b", 2), part("c", 3))),
            "legacy shapes");
        check(
            LegacyComposition.decode(json("{\"derive\":true}"), "f")
                .state() == State.PRESENT,
            "derive sentinel lost");
        check(
            LegacyComposition.decode(json("{\"disabled\":true}"), "f")
                .state() == State.DISABLED,
            "disabled resource lost");
        for (String invalid : new String[] { "{}", "{\"components\":[]}", "{\"components\":[[\"a\",0]]}",
            "{\"components\":[[\"a\",1.5]]}" })
            check(
                LegacyComposition.decode(json(invalid), "f")
                    .state() == State.INVALID,
                "invalid resource accepted");
        var huge = LegacyComposition.decode(json("{\"components\":[[\"a\",999999999999999999999999999999]]}"), "f");
        check(
            huge.value()
                .getFirst()
                .amount()
                .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0,
            "resource count narrowed");
        try {
            json("[".repeat(65) + "0" + "]".repeat(65));
            throw new AssertionError("deep JSON accepted");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
        try {
            ResourceJson.read(new java.io.ByteArrayInputStream(new byte[2_097_153]));
            throw new AssertionError("oversized JSON accepted");
        } catch (java.io.IOException expected) {
            assertions++;
        }
        check(
            new MoleculeLayoutBuilder(fixture("water"), 20, 0xFFFFFFFF).build(FONT)
                .equals(new MoleculeLayoutBuilder(water(), 20, 0xFFFFFFFF).build(FONT)),
            "water resource/layout changed");
        bounds(new MoleculeLayoutBuilder(fixture("polyethylene"), 20, 0xFFFFFFFF).build(FONT));
        bounds(new MoleculeLayoutBuilder(fixture("benzene"), 20, 0xFFFFFFFF).build(FONT));
    }

    /** Only representative checked-in fixtures; this decoder is not used by the mod. */
    private static MoleculeScene fixture(String name) {
        try (
            var input = ArchitectureRegression.class.getResourceAsStream("/assets/gtceu/molecules/" + name + ".json")) {
            if (input == null) throw new AssertionError("missing fixture " + name);
            var root = ResourceJson.read(input)
                .getAsJsonObject();
            List<Shape> shapes = new ArrayList<>();
            for (var e : root.getAsJsonArray("contents")) {
                var c = e.getAsJsonObject();
                String type = c.get("type")
                    .getAsString();
                switch (type) {
                    case "atom" -> shapes.add(
                        atom(
                            c.get("index")
                                .getAsInt(),
                            c.get("element")
                                .getAsString(),
                            c.get("x")
                                .getAsFloat(),
                            c.get("y")
                                .getAsFloat()));
                    case "bond" -> {
                        List<Line> lines = new ArrayList<>();
                        for (var l : c.getAsJsonArray("lines")) lines.add(
                            Line.valueOf(
                                l.getAsString()
                                    .toUpperCase(Locale.ROOT)));
                        shapes.add(
                            new Bond(
                                c.get("a")
                                    .getAsInt(),
                                c.get("b")
                                    .getAsInt(),
                                false,
                                lines));
                    }
                    case "parens" -> {
                        List<Integer> ids = new ArrayList<>();
                        for (var i : c.getAsJsonArray("atoms")) ids.add(i.getAsInt());
                        shapes.add(
                            new Parens(
                                c.has("sub") ? c.get("sub")
                                    .getAsString() : "",
                                c.has("sup") ? c.get("sup")
                                    .getAsString() : "",
                                ids));
                    }
                    case "benzene" -> {
                        int[] ids = new int[6];
                        for (int i = 0; i < 6; i++) ids[i] = c.getAsJsonArray("indices")
                            .get(i)
                            .getAsInt();
                        var ring = com.rubenverg.moldraw.molecule.BenzeneRing.from(
                            ids[0],
                            new org.joml.Vector3f(
                                c.get("x0")
                                    .getAsFloat(),
                                c.get("y0")
                                    .getAsFloat(),
                                0),
                            new org.joml.Vector3f(
                                c.get("x1")
                                    .getAsFloat(),
                                c.get("y1")
                                    .getAsFloat(),
                                0),
                            0,
                            c.get("angle")
                                .getAsFloat());
                        var points = ring.points();
                        for (int i = 0; i < 6; i++) {
                            shapes.add(atom(ids[i], "", points[i].x(), points[i].y()));
                            shapes.add(
                                new Bond(
                                    ids[i],
                                    ids[(i + 1) % 6],
                                    false,
                                    i % 2 == 0 ? List.of(Line.SOLID) : List.of(Line.SOLID, Line.SOLID)));
                        }
                    }
                    default -> throw new AssertionError("unsupported fixture " + type);
                }
            }
            return new MoleculeScene(shapes);
        } catch (java.io.IOException bad) {
            throw new AssertionError(bad);
        }
    }

    private static final TextMeasure FONT = new TextMeasure() {

        public int width(String s) {
            return s.length() * 6;
        }

        public int lineHeight() {
            return 9;
        }
    };

    private static Atom atom(int id, String label, float x, float y) {
        return new Atom(
            id,
            new Label(label, label.isEmpty(), 0xFFFFFFFF),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            x,
            y,
            0);
    }

    private static MoleculeScene water() {
        return new MoleculeScene(
            List.of(
                atom(0, "O", 0, .3f),
                atom(1, "H", -.8f, -.3f),
                atom(2, "H", .8f, -.3f),
                new Bond(0, 1, false, List.of(Line.SOLID)),
                new Bond(0, 2, false, List.of(Line.SOLID))));
    }

    private static void layouts() {
        var scene = water();
        var builder = new MoleculeLayoutBuilder(scene, 20, 0xFFFFFFFF);
        var a = builder.build(FONT);
        var b = builder.build(FONT);
        check(a.equals(b), "layout nondeterministic");
        check(a.width() == 64 && a.height() == 32, "water dimensions changed: " + a.width() + "x" + a.height());
        var texts = a.commands()
            .stream()
            .filter(c -> c instanceof TooltipLayout.Text)
            .map(c -> (TooltipLayout.Text) c)
            .toList();
        check(
            texts.equals(
                List.of(
                    new TooltipLayout.Text("O", 21, 4, 0xFFFFFFFF),
                    new TooltipLayout.Text("H", 5, 16, 0xFFFFFFFF),
                    new TooltipLayout.Text("H", 37, 16, 0xFFFFFFFF))),
            "water labels changed: " + texts);
        var v = scene.atoms()
            .getFirst()
            .position();
        v.x = 999;
        check(
            scene.atoms()
                .getFirst()
                .position().x == 0,
            "scene mutable vector escaped");
        List<Shape> polymer = new ArrayList<>();
        polymer.add(atom(0, "", 0, 0));
        polymer.add(atom(1, "", .8660254f, .5f));
        polymer.add(atom(2, "", 1.7320508f, 0));
        polymer.add(atom(3, "", 2.598076f, .5f));
        for (int i = 0; i < 3; i++) polymer.add(new Bond(i, i + 1, false, List.of(Line.SOLID)));
        polymer.add(new Parens("n", "", List.of(1, 2)));
        var p = new MoleculeLayoutBuilder(new MoleculeScene(polymer), 20, 0xFFFFFFFF).build(FONT);
        check(
            p.commands()
                .stream()
                .anyMatch(
                    c -> c instanceof TooltipLayout.Text t && t.text()
                        .equals("n")),
            "polymer subscript lost");
        List<Shape> ring = new ArrayList<>();
        var legacy = com.rubenverg.moldraw.molecule.BenzeneRing.counterClockwise(0, 0, 0, 0, -1, 0);
        var points = legacy.points();
        for (int i = 0; i < 6; i++) {
            ring.add(atom(i, "", points[i].x(), points[i].y()));
            ring.add(
                new Bond(i, (i + 1) % 6, false, i % 2 == 0 ? List.of(Line.SOLID) : List.of(Line.SOLID, Line.SOLID)));
        }
        var r = new MoleculeLayoutBuilder(new MoleculeScene(ring), 20, 0xFFFFFFFF).build(FONT);
        check(
            !r.commands()
                .isEmpty(),
            "benzene empty");
        for (Line style : Line.values()) {
            var styled = new MoleculeScene(
                List.of(atom(0, "C", -2, -1), atom(1, "O", 0, 1), new Bond(0, 1, false, List.of(style))));
            bounds(new MoleculeLayoutBuilder(styled, 20, 0xFFFFFFFF).build(FONT));
        }
        bounds(a);
        bounds(p);
        bounds(r);
        var pie = PieLayout.build(
            List.of(
                new PieLayout.Slice("A", fraction(1, 2), "long label", 1),
                new PieLayout.Slice("B", fraction(1, 2), "B", 2)),
            32,
            FONT);
        bounds(pie);
        check(
            pie.equals(
                PieLayout.build(
                    List.of(
                        new PieLayout.Slice("A", fraction(1, 2), "long label", 1),
                        new PieLayout.Slice("B", fraction(1, 2), "B", 2)),
                    32,
                    FONT)),
            "pie nondeterministic");
        try {
            a.commands()
                .clear();
            throw new AssertionError("mutable layout");
        } catch (UnsupportedOperationException expected) {
            assertions++;
        }
        var canvas = new TooltipLayout.Canvas(FONT);
        canvas.fill(-8, -4, 2, 3, 0);
        canvas.drawStringWithShadow("negative", -10, -7, 0);
        bounds(canvas.finish(0, 0));
    }

    private static void bounds(TooltipLayout layout) {
        for (var c : layout.commands()) {
            if (c instanceof TooltipLayout.Pixels ps) for (var p : ps.pixels()) check(
                p.x() >= 0 && p.y() >= 0 && p.x() < layout.width() && p.y() < layout.height(),
                "pixel outside bounds");
            else if (c instanceof TooltipLayout.Text t) check(
                t.x() >= 0 && t.y() >= 0
                    && t.x() + FONT.width(t.text()) < layout.width()
                    && t.y() + FONT.lineHeight() < layout.height(),
                "text outside bounds");
        }
    }
}
