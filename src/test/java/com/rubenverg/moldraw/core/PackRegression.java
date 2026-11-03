package com.rubenverg.moldraw.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

import com.google.gson.*;
import com.rubenverg.moldraw.core.pack.*;
import com.rubenverg.moldraw.tool.PackTool;

/** Headless end-to-end contract/assembly/format/preview checks. All mutations are confined to fresh build fixtures. */
public final class PackRegression {

    private static int assertions, fixtures;
    private static Path source, workspace;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void reject(Runnable action, String expected) {
        try {
            action.run();
            throw new AssertionError("Accepted invalid input: " + expected);
        } catch (PackException error) {
            check(
                error.getMessage()
                    .contains(expected),
                "Wrong diagnostic: " + error.getMessage() + " expected " + expected);
        }
    }

    private static void save(Path path, JsonObject data) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                path,
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(data) + "\n");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void edit(Path root, String file, Consumer<JsonObject> change) {
        try {
            Path path = root.resolve(file);
            JsonObject data = Jsonc.read(path)
                .object();
            change.accept(data);
            save(path, data);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static Path fixture() {
        Path root = workspace.resolve("fixture_" + (fixtures++));
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path target = root.resolve(source.relativize(file));
                if (Files.isDirectory(file)) Files.createDirectories(target);
                else Files.copy(file, target);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return root;
    }

    private static PackCatalog compile(Path root) {
        try {
            return PackCompiler.compile(root, "0.6.0-stage.2");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void bad(String file, Consumer<JsonObject> change, String diagnostic) {
        Path root = fixture();
        edit(root, file, change);
        reject(() -> compile(root), diagnostic);
    }

    private static JsonArray array(String source) {
        return Jsonc.parse("test", source)
            .object()
            .getAsJsonArray("a");
    }

    private static void lexical() {
        String input = "\uFEFF{/* hi */\"url\":\"https://example.test/a//b\",\"arr\":[1,2,],\"big\":999999999999999999999999999999999999,}";
        var d = Jsonc.parse("memory", input);
        check(
            d.object()
                .get("big")
                .getAsBigInteger()
                .toString()
                .length() == 36,
            "Number lost precision");
        check(
            d.object()
                .get("url")
                .getAsString()
                .contains("//b"),
            "Comment removed inside string");
        for (String invalid : List.of(
            "{'a':1}",
            "{a:1}",
            "[1,,]",
            "true false",
            "NaN",
            "Infinity",
            "/*",
            "/* abc",
            "\"bad\\q\"",
            "01",
            "1e999999",
            "{\"a\":1,\"\\u0061\":2}")) reject(() -> Jsonc.parse("lexical", invalid), "lexical:");
        reject(() -> Jsonc.parse("depth", "[".repeat(65) + "0" + "]".repeat(65)), "nesting");
        var error = Jsonc.parse(
            "position.jsonc",
            "{\n \"schemaVersion\": 2,\n \"id\":\"official:x\", \"type\":\"material\", \"bindings\":[\"gt:Water\"]}");
        reject(
            () -> ContractSchema.bundled("definition")
                .validate(error),
            "position.jsonc:2:2");
        check(
            SemVer.parse("1.0.0-preview.10")
                .compareTo(SemVer.parse("1.0.0-preview.2")) > 0,
            "Lexical numeric version ordering");
        check(
            SemVer.parse("1.0.0")
                .compareTo(SemVer.parse("1.0.0-rc")) > 0,
            "Release before prerelease");
        reject(() -> SemVer.parse("1.0.0-01"), "prerelease");
    }

    private static void formatter() {
        String text = "// header\n{\n\"type\":\"material\", // kind\n\"id\": /* id note */ \"official:test\",\n\"schemaVersion\":1,\n/* binds */ \"bindings\":[\"name:test\", /* item note */],\n\"composition\":{\"components\":[{\"amount\":999999999999999999999999999999999999,\"material\":\"gt:Copper\",}],\"basis\":\"mixture_parts\",\"source\":\"explicit\"},\n/* closing */\n}\n// footer";
        var schema = ContractSchema.bundled("definition");
        String first = JsoncFormatter.format(Jsonc.parse("format", text), schema),
            second = JsoncFormatter.format(Jsonc.parse("format", first), schema);
        check(first.equals(second), "Formatter not idempotent:\n" + first + "\n--\n" + second);
        for (String marker : List.of("header", "kind", "id note", "binds", "item note", "closing", "footer"))
            check(first.contains(marker), "Comment lost " + marker);
        check(first.contains("999999999999999999999999999999999999"), "Formatter rounded integer");
        check(first.indexOf("\"schemaVersion\"") < first.indexOf("\"composition\""), "Canonical field ordering");
        String quoted = JsoncFormatter.quote("中文\\\"\n\uD800");
        check(
            Jsonc.parse("quote", quoted)
                .text()
                .equals(quoted),
            "Quoting failed");
    }

    private static void schemaAndPacks() {
        String water = "official/molecules/water.jsonc", benzene = "official/molecules/benzene.jsonc",
            naphthalene = "official/molecules/naphthalene.jsonc";
        bad(water, j -> j.addProperty("unexpected", 1), "Unknown field");
        bad(water, j -> j.addProperty("schemaVersion", 2), "Expected 1");
        bad(
            water,
            j -> j.getAsJsonArray("atoms")
                .get(0)
                .getAsJsonObject()
                .add("position", array("{\"a\":[0]}")),
            "minItems");
        bad(
            water,
            j -> j.getAsJsonArray("bonds")
                .get(0)
                .getAsJsonObject()
                .addProperty("order", 4),
            "Unsupported value");
        bad(
            water,
            j -> j.getAsJsonArray("atoms")
                .add(
                    j.getAsJsonArray("atoms")
                        .get(0)),
            "Duplicate local");
        bad(
            water,
            j -> j.getAsJsonArray("bonds")
                .get(0)
                .getAsJsonObject()
                .addProperty("from", "missing"),
            "Unknown atom/port");
        bad(
            benzene,
            j -> j.getAsJsonArray("instances")
                .get(0)
                .getAsJsonObject()
                .addProperty("module", "core:execute"),
            "Expected");
        bad(
            naphthalene,
            j -> j.getAsJsonArray("instances")
                .get(1)
                .getAsJsonObject()
                .add("origin", array("{\"a\":[5,0]}")),
            "position conflict");
        bad(
            naphthalene,
            j -> j.getAsJsonArray("instances")
                .get(1)
                .getAsJsonObject()
                .getAsJsonObject("parameters")
                .addProperty("doubleBondStart", 0),
            "Conflicting duplicate bond");
        bad(
            "official/materials/review_alloy.jsonc",
            j -> j.getAsJsonObject("composition")
                .getAsJsonArray("components")
                .get(0)
                .getAsJsonObject()
                .addProperty("amount", "1/0"),
            "Invalid identifier/value");
        bad(
            "official/materials/benzene.jsonc",
            j -> j.addProperty("structure", "official:missing"),
            "Missing/wrong-kind");
        bad("user/materials/water_text_override.jsonc", j -> j.remove("overrides"), "Namespace not owned");
        bad(
            "user/pack.jsonc",
            j -> j.getAsJsonArray("dependencies")
                .get(0)
                .getAsJsonObject()
                .addProperty("version", "9.0.0"),
            "version mismatch");
        bad(
            "official/pack.jsonc",
            j -> j.getAsJsonObject("modVersion")
                .addProperty("min", "0.7.0"),
            "Incompatible");
        bad(
            "official/pack.jsonc",
            j -> j.getAsJsonArray("dependencies")
                .add(
                    Jsonc.parse("dependency", "{\"id\":\"user\",\"version\":\"1.0.0-preview.1\"}")
                        .object()),
            "Dependency cycle");
        bad(
            "official/fragments/hydroxyl.jsonc",
            j -> j.add("instances", array("{\"a\":[{\"id\":\"recursive\",\"fragment\":\"official:hydroxyl\"}]}")),
            "Fragment cycle");
        bad(
            "official/materials/review_alloy.jsonc",
            j -> j.getAsJsonObject("composition")
                .getAsJsonArray("components")
                .get(0)
                .getAsJsonObject()
                .addProperty("material", "official:review_nested"),
            "Composition cycle");
        Path duplicate = fixture();
        try {
            JsonObject j = Jsonc.read(duplicate.resolve("official/materials/benzene.jsonc"))
                .object();
            j.addProperty("id", "official:duplicate");
            save(duplicate.resolve("official/materials/duplicate.jsonc"), j);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        reject(() -> compile(duplicate), "Duplicate binding");
        Path fork = fixture();
        try {
            JsonObject manifest = Jsonc.read(fork.resolve("user/pack.jsonc"))
                .object();
            manifest.addProperty("id", "other");
            save(fork.resolve("other/pack.jsonc"), manifest);
            JsonObject override = Jsonc.read(fork.resolve("user/materials/water_text_override.jsonc"))
                .object();
            save(fork.resolve("other/materials/override.jsonc"), override);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        reject(() -> compile(fork), "override branches");
        Path chain = fixture();
        try {
            JsonObject manifest = Jsonc.read(chain.resolve("user/pack.jsonc"))
                .object();
            manifest.addProperty("id", "last");
            manifest.getAsJsonArray("dependencies")
                .get(0)
                .getAsJsonObject()
                .addProperty("id", "user");
            save(chain.resolve("last/pack.jsonc"), manifest);
            JsonObject override = Jsonc.read(chain.resolve("user/materials/water_text_override.jsonc"))
                .object();
            override.getAsJsonObject("overrides")
                .addProperty("pack", "user");
            save(chain.resolve("last/materials/override.jsonc"), override);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        check(
            compile(chain).definitions()
                .get("official:water")
                .owner()
                .equals("last"),
            "Explicit override chain failed");
        Path utf = workspace.resolve("bad_utf8.jsonc");
        try {
            Files.write(utf, new byte[] { (byte) 0xC3, 0x28 });
            try {
                Jsonc.read(utf);
                throw new AssertionError("Invalid UTF8 accepted");
            } catch (PackException expected) {
                assertions++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assembly() {
        var catalog = compile(source);
        check(
            catalog.packCount() == 2 && catalog.definitions()
                .size() == 14
                && catalog.molecules()
                    .size() == 6,
            "Review catalog counts");
        var ring = catalog.molecules()
            .get("official:naphthalene_structure");
        check(
            ring.atoms()
                .size() == 10
                && ring.bonds()
                    .size() == 11
                && ring.bonds()
                    .stream()
                    .filter(
                        b -> b.lines()
                            .size() == 2)
                    .count() == 5,
            "Fused ring graph");
        check(
            catalog.definitions()
                .get("official:water")
                .owner()
                .equals("user"),
            "Override did not replace original provider");
        var phenol = catalog.molecules()
            .get("official:phenol_structure");
        check(
            phenol.atoms()
                .size() == 7
                && phenol.bonds()
                    .size() == 7
                && phenol.ports()
                    .containsKey("hydroxyl.attach"),
            "Fragment attachment failed");
        var data = catalog.definitions()
            .get("official:water");
        data.json()
            .addProperty("id", "mutated");
        check(
            data.json()
                .get("id")
                .getAsString()
                .equals("official:water"),
            "Mutable catalog escaped");
        try {
            ring.atoms()
                .clear();
            throw new AssertionError("Mutable graph");
        } catch (UnsupportedOperationException expected) {
            assertions++;
        }
        Path root = fixture();
        JsonObject fragment = Jsonc.parse(
            "transform",
            "{\"schemaVersion\":1,\"type\":\"fragment\",\"id\":\"official:wedge\",\"atoms\":[{\"id\":\"a\",\"element\":\"C\",\"position\":[0,0]},{\"id\":\"b\",\"element\":\"O\",\"position\":[1,0]}],\"bonds\":[{\"from\":\"a\",\"to\":\"b\",\"order\":1,\"style\":\"outward\"}],\"ports\":{\"attach\":\"a\"}}")
            .object();
        save(root.resolve("official/fragments/wedge.jsonc"), fragment);
        JsonObject molecule = Jsonc.parse(
            "transform",
            "{\"schemaVersion\":1,\"type\":\"molecule\",\"id\":\"official:water_structure\",\"instances\":[{\"id\":\"piece\",\"fragment\":\"official:wedge\",\"origin\":[3,4],\"rotation\":90,\"scale\":2,\"mirror\":\"x\"}]}")
            .object();
        save(root.resolve("official/molecules/water.jsonc"), molecule);
        var transformed = compile(root).molecules()
            .get("official:water_structure");
        var b = transformed.atoms()
            .stream()
            .filter(
                a -> a.id()
                    .equals("piece.b"))
            .findFirst()
            .orElseThrow();
        check(Math.abs(b.x() - 3) < 1e-9 && Math.abs(b.y() - 2) < 1e-9, "Transform order incorrect");
        check(
            transformed.bonds()
                .getFirst()
                .lines()
                .equals(List.of(MoleculeScene.Line.INWARD)),
            "Mirror did not swap wedge");
        bad("official/molecules/benzene.jsonc", j -> {
            j.getAsJsonArray("instances")
                .get(0)
                .getAsJsonObject()
                .add(
                    "atomMap",
                    Jsonc.parse("m", "{\"a0\":\"anchor\"}")
                        .object());
            j.add(
                "atoms",
                array("{\"a\":[{\"id\":\"anchor\",\"element\":\"O\",\"position\":[1,0],\"labelMode\":\"skeletal\"}]}"));
        }, "attributes/position conflict");
        bad("official/molecules/benzene.jsonc", j -> {
            j.add(
                "instances",
                array(
                    "{\"a\":[{\"id\":\"a\",\"module\":\"core:benzene\",\"atomMap\":{\"a0\":\"b.a0\"}},{\"id\":\"b\",\"module\":\"core:benzene\",\"atomMap\":{\"a0\":\"a.a0\"}}]}"));
        }, "Cyclic atomMap");
        bad(
            "official/fragments/hydroxyl.jsonc",
            j -> {
                j.add(
                    "ports",
                    Jsonc.parse("p", "{\"x\":\"y\",\"y\":\"x\"}")
                        .object());
            },
            "Port alias cycle");
    }

    private static void sceneMetadata() {
        var style = new com.rubenverg.moldraw.core.pack.CompiledMolecule.Style(
            "N",
            null,
            "symbol",
            4,
            15,
            1,
            "#123456",
            Map.of());
        var graph = new com.rubenverg.moldraw.core.pack.CompiledMolecule(
            "test:metadata",
            List.of(new com.rubenverg.moldraw.core.pack.CompiledMolecule.Atom("n", 0, 0, style)),
            List.of(),
            List.of(
                new com.rubenverg.moldraw.core.pack.CompiledMolecule.Annotation(
                    "text",
                    List.of(),
                    null,
                    null,
                    "caption",
                    1,
                    2,
                    List.of(),
                    "#80112233")),
            Map.of());
        var scene = graph.scene(e -> 0xFFFFFFFF);
        var atom = (MoleculeScene.Atom) scene.contents()
            .getFirst();
        check(
            atom.element()
                .text()
                .equals("¹⁵NH₄⁺"),
            "Explicit chemical metadata lost");
        check(
            atom.element()
                .color() == 0xFF123456,
            "Atom color lost");
        var caption = (MoleculeScene.TextAnnotation) scene.contents()
            .getLast();
        check(caption.color() == 0x80112233, "Annotation alpha lost");
        var layout = new MoleculeLayoutBuilder(scene, 24, 0xFFFFFFFF).build(new TextMeasure() {

            public int width(String value) {
                return value.length() * 6;
            }

            public int lineHeight() {
                return 9;
            }
        });
        check(
            layout.commands()
                .stream()
                .anyMatch(
                    c -> c instanceof TooltipLayout.Text t && t.text()
                        .equals("caption") && t.color() == 0x80112233),
            "Annotation missing from pure layout");
    }

    private static int tool(String... args) {
        return PackTool
            .run(args, new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
    }

    private static void cli() throws IOException {
        Path root = fixture();
        Path noncanonical = root.resolve("official/materials/benzene.jsonc");
        Files.writeString(noncanonical, "\uFEFF" + Files.readString(noncanonical));
        var before = compile(root).molecules();
        check(tool("format", "--check", root.toString()) == 1, "format --check exit");
        check(tool("format", "--write", root.toString()) == 0, "format --write failed");
        check(tool("format", "--check", root.toString()) == 0, "format not stable");
        check(
            compile(root).molecules()
                .equals(before),
            "Formatting changed geometry");
        Path invalid = fixture();
        edit(invalid, "official/materials/benzene.jsonc", j -> j.addProperty("structure", "official:missing"));
        Map<Path, byte[]> bytes = new LinkedHashMap<>();
        try (var files = Files.walk(invalid)) {
            for (Path f : files.filter(Files::isRegularFile)
                .toList()) bytes.put(f, Files.readAllBytes(f));
        }
        check(tool("format", "--write", invalid.toString()) == 2, "Invalid format should fail");
        for (var e : bytes.entrySet())
            check(Arrays.equals(e.getValue(), Files.readAllBytes(e.getKey())), "Invalid format changed source");
        Path preview = workspace.resolve("preview");
        check(tool("preview", root.toString(), preview.toString()) == 0, "Preview failed");
        check(
            Files.readString(preview.resolve("index.html"))
                .contains("<img"),
            "Missing visual gallery");
        var report = Jsonc.read(preview.resolve("report.json"))
            .object();
        JsonObject nested = null;
        for (var p : report.getAsJsonArray("quantityPies")) if (p.getAsJsonObject()
            .get("id")
            .getAsString()
            .equals("official:review_nested"))
            nested = p.getAsJsonObject()
                .getAsJsonObject("shares");
        check(
            nested != null && nested.get("gt:Copper")
                .getAsString()
                .equals("3/8")
                && nested.get("gt:Zinc")
                    .getAsString()
                    .equals("1/8")
                && nested.get("gt:Tin")
                    .getAsString()
                    .equals("1/2"),
            "Offline pie normalization changed");
        check(
            tool(
                "preview",
                root.toString(),
                root.resolve("bad_preview")
                    .toString())
                == 2,
            "Preview wrote into source packs");
        check(tool("validate", root.toString(), "--mod-version", "0.7.0") == 2, "Mod range not checked");
        check(tool("validate", root.toString(), "--id", "official:water") == 2, "Preview-only option accepted");
    }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0])
            .toAbsolutePath()
            .normalize();
        source = project.resolve("data-packs/review");
        workspace = Files.createTempDirectory(project.resolve("build"), "pack-regression-");
        lexical();
        formatter();
        schemaAndPacks();
        assembly();
        sceneMetadata();
        cli();
        System.out.println("Pack stage-2 regression: " + assertions + " assertions passed; fixtures: " + workspace);
    }
}
