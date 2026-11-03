package com.rubenverg.moldraw.core;

import java.nio.file.*;
import java.util.*;

import org.joml.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.pack.*;
import com.rubenverg.moldraw.molecule.BenzeneRing;
import com.rubenverg.moldraw.tool.*;

/** Compares migrated geometry against the old coordinate/ring conventions without initializing GT. */
public final class MigrationRegression {

    private static int assertions;
    private static final TextMeasure FONT = new TextMeasure() {

        public int width(String s) {
            return s.codePointCount(0, s.length()) * 6;
        }

        public int lineHeight() {
            return 9;
        }
    };

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static MoleculeScene.Label label(JsonElement element) {
        int count = 1;
        if (element.isJsonArray()) {
            count = element.getAsJsonArray()
                .get(1)
                .getAsInt();
            element = element.getAsJsonArray()
                .get(0);
        }
        String text = element.isJsonObject() ? element.getAsJsonObject()
            .get("symbol")
            .getAsString() : element.getAsString();
        boolean hidden = text.isEmpty() || element.isJsonObject() && element.getAsJsonObject()
            .has("invisible")
            && element.getAsJsonObject()
                .get("invisible")
                .getAsBoolean();
        if (count != 1) for (char c : String.valueOf(count)
            .toCharArray()) text += (char) ('₀' + c - '0');
        return new MoleculeScene.Label(text, hidden, 0xFFFFFFFF);
    }

    private static Optional<MoleculeScene.Label> side(JsonObject o, String key) {
        return o.has(key) ? Optional.of(label(o.get(key))) : Optional.empty();
    }

    private static MoleculeScene baseline(JsonObject source) {
        List<MoleculeScene.Shape> shapes = new ArrayList<>();
        for (var value : source.getAsJsonArray("contents")) {
            JsonObject o = value.getAsJsonObject();
            switch (o.get("type")
                .getAsString()) {
                case "atom" -> shapes.add(
                    new MoleculeScene.Atom(
                        o.get("index")
                            .getAsInt(),
                        label(o.get("element")),
                        side(o, "above"),
                        side(o, "right"),
                        side(o, "below"),
                        side(o, "left"),
                        o.get("x")
                            .getAsFloat(),
                        o.get("y")
                            .getAsFloat(),
                        0));
                case "benzene" -> {
                    int[] indices = new int[o.getAsJsonArray("indices")
                        .size()];
                    for (int i = 0; i < indices.length; i++) indices[i] = o.getAsJsonArray("indices")
                        .get(i)
                        .getAsInt();
                    // Actual legacy point generator, not the converter's implementation.
                    var ring = new BenzeneRing(
                        indices,
                        new Vector3f(
                            o.get("x0")
                                .getAsFloat(),
                            o.get("y0")
                                .getAsFloat(),
                            0),
                        new Vector3f(
                            o.get("x1")
                                .getAsFloat(),
                            o.get("y1")
                                .getAsFloat(),
                            0),
                        0,
                        o.get("angle")
                            .getAsFloat());
                    var points = ring.points();
                    for (int i = 0; i < indices.length; i++) {
                        shapes.add(
                            new MoleculeScene.Atom(
                                indices[i],
                                new MoleculeScene.Label("", true, 0xFFFFFFFF),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                points[i].x(),
                                points[i].y(),
                                0));
                        shapes.add(
                            new MoleculeScene.Bond(
                                indices[i],
                                indices[(i + 1) % indices.length],
                                false,
                                i % 2 == 0 ? List.of(MoleculeScene.Line.SOLID)
                                    : List.of(MoleculeScene.Line.SOLID, MoleculeScene.Line.SOLID)));
                    }
                }
                case "bond" -> {
                    List<MoleculeScene.Line> lines = new ArrayList<>();
                    for (var line : o.getAsJsonArray("lines")) lines.add(
                        MoleculeScene.Line.valueOf(
                            line.getAsString()
                                .toUpperCase(Locale.ROOT)));
                    shapes.add(
                        new MoleculeScene.Bond(
                            o.get("a")
                                .getAsInt(),
                            o.get("b")
                                .getAsInt(),
                            o.has("centered") && o.get("centered")
                                .getAsBoolean(),
                            lines));
                }
                case "parens", "circle" -> {
                    List<Integer> refs = new ArrayList<>();
                    for (var a : o.getAsJsonArray("atoms")) refs.add(a.getAsInt());
                    if (o.get("type")
                        .getAsString()
                        .equals("parens"))
                        shapes.add(
                            new MoleculeScene.Parens(
                                o.has("sub") ? o.get("sub")
                                    .getAsString() : "",
                                o.has("sup") ? o.get("sup")
                                    .getAsString() : "",
                                refs));
                    else shapes.add(
                        new MoleculeScene.CircleTransformation(
                            o.get("a00")
                                .getAsFloat(),
                            o.get("a01")
                                .getAsFloat(),
                            o.get("a10")
                                .getAsFloat(),
                            o.get("a11")
                                .getAsFloat(),
                            refs));
                }
                default -> throw new AssertionError("Unknown baseline shape");
            }
        }
        return new MoleculeScene(shapes);
    }

    private static Set<String> primitives(TooltipLayout layout) {
        Set<String> result = new TreeSet<>();
        for (var c : layout.commands()) {
            if (c instanceof TooltipLayout.Pixels p) for (var px : p.pixels()) result.add("pixel:" + px);
            else result.add(c.toString());
        }
        return result;
    }

    private static Map<String, byte[]> contents(Path directory) throws Exception {
        Map<String, byte[]> result = new TreeMap<>();
        try (var files = Files.walk(directory)) {
            for (var p : files.filter(Files::isRegularFile)
                .toList())
                result.put(
                    directory.relativize(p)
                        .toString(),
                    Files.readAllBytes(p));
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]), assets = project.resolve("src/main/resources/assets"),
            metadata = project.resolve("tools/migration/legacy-bindings.json");
        Path scratch = Files.createTempDirectory(project.resolve("build"), "migration-regression-");
        Path migrated = scratch.resolve("packs");
        LegacyMigrator.migrate(assets, metadata, migrated, "0.6.0-stage.3");
        var expected = contents(project.resolve("data-packs/migrated"));
        var actual = contents(migrated);
        check(
            actual.keySet()
                .equals(expected.keySet()),
            "Generated migration inventory drift");
        for (var file : expected.entrySet()) check(
            Arrays.equals(file.getValue(), actual.get(file.getKey())),
            "Generated migration changed: " + file.getKey());
        PackCatalog catalog = PackCompiler.compile(migrated, "0.6.0-stage.3");
        int structures = 0, rings = 0;
        try (var files = Files.walk(assets)) {
            for (var file : files.filter(
                p -> p.toString()
                    .replace('\\', '/')
                    .matches(".*/molecules/[^/]+\\.json"))
                .sorted()
                .toList()) {
                String namespace = file.getParent()
                    .getParent()
                    .getFileName()
                    .toString(),
                    name = file.getFileName()
                        .toString()
                        .replace(".json", "");
                JsonObject json = Jsonc.read(file)
                    .object();
                var old = baseline(json);
                var graph = catalog.molecules()
                    .get("official:" + namespace + "/" + name + "_structure");
                check(graph != null, "Unmigrated structure " + file);
                var scene = graph.scene(e -> 0xFFFFFFFF, false);
                check(
                    old.atoms()
                        .size()
                        == scene.atoms()
                            .size(),
                    "Atom count changed " + file);
                for (int i = 0; i < old.atoms()
                    .size(); i++) {
                    var a = old.atoms()
                        .get(i);
                    var b = scene.atoms()
                        .get(i);
                    check(
                        Float.floatToIntBits(a.x()) == Float.floatToIntBits(b.x())
                            && Float.floatToIntBits(a.y()) == Float.floatToIntBits(b.y()),
                        "Coordinates changed " + file + " atom " + a.index());
                    check(
                        a.element()
                            .equals(b.element())
                            && a.above()
                                .equals(b.above())
                            && a.right()
                                .equals(b.right())
                            && a.below()
                                .equals(b.below())
                            && a.left()
                                .equals(b.left()),
                        "Labels changed " + file + " atom " + a.index());
                }
                for (int scale : List.of(16, 24, 32)) {
                    var before = new MoleculeLayoutBuilder(old, scale, 0xFFFFFFFF).build(FONT);
                    var after = new MoleculeLayoutBuilder(scene, scale, 0xFFFFFFFF).build(FONT);
                    check(
                        before.width() == after.width() && before.height() == after.height(),
                        "Layout bounds changed " + file);
                    check(
                        primitives(before).equals(primitives(after)),
                        "Raster/text primitives changed " + file + " scale " + scale);
                }
                for (var c : json.getAsJsonArray("contents")) if (c.getAsJsonObject()
                    .get("type")
                    .getAsString()
                    .equals("benzene")) rings++;
                structures++;
            }
        }
        check(structures == 368 && rings == 82, "Unexpected migration source totals");
        var report = Jsonc.read(migrated.resolve("migration-report.json"))
            .object();
        check(
            report.getAsJsonArray("sources")
                .size() == 685,
            "Not all sources are accounted for");
        try {
            LegacyMigrator.migrate(assets, metadata, migrated, "0.6.0-stage.3");
            throw new AssertionError("Overwrote existing output");
        } catch (IllegalArgumentException good) {
            assertions++;
        }
        var sulfate = catalog.molecules()
            .values()
            .stream()
            .flatMap(
                g -> g.atoms()
                    .stream())
            .filter(
                a -> a.style()
                    .optionalColor())
            .findFirst()
            .orElseThrow();
        check(
            sulfate.style()
                .color() != null,
            "Optional color lost");
        var colored = catalog.molecules()
            .values()
            .stream()
            .filter(
                g -> g.atoms()
                    .contains(sulfate))
            .findFirst()
            .orElseThrow();
        int index = colored.atoms()
            .indexOf(sulfate);
        check(
            colored.scene(e -> 0xFF010203, false)
                .atoms()
                .get(index)
                .element()
                .color() == 0xFF010203,
            "Optional colors did not fall back");
        check(
            colored.scene(e -> 0xFF010203, true)
                .atoms()
                .get(index)
                .element()
                .color() != (0xFF010203),
            "Optional colors not enabled");
        Path gallery = scratch.resolve("gallery");
        PreviewWriter.write(catalog, gallery, null);
        JsonObject pages = Jsonc.read(gallery.resolve("report.json"))
            .object();
        int cards = 0;
        check(
            pages.getAsJsonArray("pages")
                .size() == 8,
            "Full gallery page count changed");
        for (var p : pages.getAsJsonArray("pages")) {
            JsonObject page = p.getAsJsonObject();
            cards += page.get("count")
                .getAsInt();
            check(
                Files.isRegularFile(
                    gallery.resolve(
                        page.get("path")
                            .getAsString())),
                "Broken gallery link");
        }
        check(cards == 722, "Some migrated geometry/material cards were omitted");
        Path bad = scratch.resolve("bad_assets");
        Files.createDirectories(bad.resolve("gtceu/molecules"));
        Files.createDirectories(bad.resolve("gtceu/alloys"));
        Files.writeString(bad.resolve("gtceu/molecules/water.json"), "{\"spin\":true,\"contents\":[]}");
        Files.writeString(bad.resolve("gtceu/alloys/test.json"), "{\"derive\":true}");
        Path rejected = scratch.resolve("rejected");
        try {
            LegacyMigrator.migrate(bad, metadata, rejected, "0.6.0-stage.3");
            throw new AssertionError("Unknown legacy field accepted");
        } catch (IllegalArgumentException good) {
            check(!Files.exists(rejected), "Invalid migration was published");
        }
        System.out.println(
            "Migration regression: " + assertions
                + " assertions passed; 368 structures / 82 rings / 3 layout scales / 685 sources");
    }
}
