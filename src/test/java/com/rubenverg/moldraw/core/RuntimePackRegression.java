package com.rubenverg.moldraw.core;

import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import com.rubenverg.moldraw.core.MaterialDefinition.*;
import com.rubenverg.moldraw.core.pack.*;

/** Live binding and publication contracts, without Minecraft, a render context or a native library. */
public final class RuntimePackRegression {

    private static int assertions;
    private static final Map<String, RuntimeSnapshot.ProviderMaterial> GT = new HashMap<>();

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static Component part(String id, long amount) {
        return new Component("gt:" + id, BigInteger.valueOf(amount));
    }

    private static void element(String name, String formula, long mass) {
        GT.put(
            "gt:" + name,
            new RuntimeSnapshot.ProviderMaterial(
                "gt:" + name,
                name,
                formula,
                Set.of(name),
                0xFFFFFFFF,
                Fraction.of(mass),
                Fraction.ONE,
                List.of(),
                true));
    }

    private static RuntimeSnapshot.Input input() {
        return new RuntimeSnapshot.Input(
            GT,
            Set.of("test_water"),
            Map.of("gt:Oxygen", 0xFFFF0000, "symbol:H", 0xFFFFFFFF),
            0xFFABCDEF,
            false);
    }

    private static void save(Path root, String file, String text) throws Exception {
        Path target = root.resolve("custom/" + file);
        Files.createDirectories(target.getParent());
        Files.writeString(target, text);
    }

    private static Path pack(Path scratch) throws Exception {
        Path root = Files.createTempDirectory(scratch, "pack-");
        save(
            root,
            "pack.jsonc",
            "{\"schemaVersion\":1,\"type\":\"pack\",\"id\":\"custom\",\"version\":\"1.0.0\",\"modVersion\":{\"min\":\"0.6.0-stage.4\",\"maxExclusive\":\"0.7.0\"},\"dependencies\":[],\"providers\":[\"gregtech\"]}");
        save(
            root,
            "molecules/oxygen.jsonc",
            "{\"schemaVersion\":1,\"type\":\"molecule\",\"id\":\"custom:oxygen_structure\",\"atoms\":[{\"id\":\"o\",\"element\":\"O\",\"position\":[0,0]}]}");
        save(
            root,
            "bindings/core.jsonc",
            "{\"schemaVersion\":1,\"type\":\"bindings\",\"id\":\"custom:rules\",\"rules\":[{\"kind\":\"element_binding\",\"symbol\":\"H\",\"material\":\"gt:Hydrogen\"},{\"kind\":\"element_binding\",\"symbol\":\"O\",\"material\":\"gt:Oxygen\"},{\"kind\":\"composition_candidate\",\"names\":[\"water\"]},{\"kind\":\"strip_prefix\",\"prefixes\":[\"raw_\"],\"target\":\"structure\"}]}");
        return root;
    }

    private static RuntimeSnapshot bind(Path root) throws Exception {
        return RuntimeSnapshot.bind(PackCompiler.compile(root, "0.6.0-stage.4"), input());
    }

    private static void material(Path root, String file, String id, String fields) throws Exception {
        save(
            root,
            "materials/" + file + ".jsonc",
            "{\"schemaVersion\":1,\"type\":\"material\",\"id\":\"custom:" + id + "\"," + fields + "}");
    }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]), scratch = Files.createTempDirectory(project.resolve("build"), "runtime-pack-");
        check(ElementPalette.COLORS.size() > 90, "Generic atom palette failed to initialize");
        var formulaLines = new ArrayList<>(List.of("Water", "§7H₂O"));
        check(
            FormulaText.applyOverride(formulaLines, "D2O", "H2O") && formulaLines.size() == 2,
            "Declared formula did not replace native formula line");
        check(
            !FormulaText.applyOverride(formulaLines, "D2O", "H2O"),
            "Declared formula duplicated on repeated tooltip passes");
        var cobalt = new ArrayList<>(List.of("Co"));
        check(
            FormulaText.applyOverride(cobalt, "CO", "") && cobalt.size() == 2,
            "Formula text comparison confused Co with CO");
        element("Hydrogen", "H", 1);
        element("Oxygen", "O", 16);
        element("Iron", "Fe", 56);
        element("Carbon", "C", 12);
        element("Copper", "Cu", 64);
        element("Zinc", "Zn", 65);
        element("Tin", "Sn", 119);
        GT.put(
            "gt:Water",
            new RuntimeSnapshot.ProviderMaterial(
                "gt:Water",
                "Water",
                "H2O",
                Set.of("Water"),
                0xFF8888FF,
                null,
                Fraction.ONE,
                List.of(part("Hydrogen", 2), part("Oxygen", 1)),
                false));
        Path root = pack(scratch);
        material(
            root,
            "water",
            "water",
            "\"bindings\":[\"gt:Water\"],\"formula\":\"H2O\",\"structure\":\"custom:oxygen_structure\"");
        var value = bind(root);
        check(value.enabled(), "Pack was not enabled");
        var water = value.provider("gt:Water");
        check(
            water.molecule()
                .value() != null,
            "Exact GT binding was not mounted");
        check(
            water.molecule()
                .value()
                .atoms()
                .getFirst()
                .element()
                .color() == 0xFFFF0000,
            "External element/GT color binding lost");
        check(!water.chartEligible(), "Auto mode displayed a chart despite valid structure");
        check(
            value.lookup("fluid:unbound", "unbound", "gt:Water")
                .molecule()
                .value() != null,
            "Provider fallback failed");
        material(root, "fluid", "fluid", "\"bindings\":[\"fluid:test_water\"],\"disabled\":true");
        value = bind(root);
        check(
            value.lookup("fluid:test_water", "test_water", "gt:Water")
                .molecule()
                .state() == State.DISABLED,
            "Exact fluid disable lost to GT fallback");
        material(root, "name", "named", "\"bindings\":[\"name:Example\"],\"structure\":\"custom:oxygen_structure\"");
        value = bind(root);
        check(
            value.lookup("name:Example", "  EXAMPLE  ", null)
                .molecule()
                .value() != null,
            "Case-insensitive name binding failed");
        check(
            value.lookup("name:raw", "raw_Example", null)
                .molecule()
                .value() != null,
            "Single prefix fallback failed");
        check(
            value.lookup("name:rawraw", "raw_raw_Example", null)
                .molecule()
                .value() == null,
            "Prefix stripped recursively");
        material(
            root,
            "other",
            "other",
            "\"bindings\":[\"name:Some Thing\"],\"structure\":\"custom:oxygen_structure\"");
        material(
            root,
            "collision",
            "collision",
            "\"bindings\":[\"name:some_thing\"],\"structure\":\"custom:oxygen_structure\"");
        value = bind(root);
        var ambiguous = value.lookup("name:test", "Some Thing", null);
        check(
            ambiguous.molecule()
                .value() == null && !ambiguous.chartEligible()
                && ambiguous.definition()
                    .diagnostics()
                    .getFirst()
                    .contains("AMBIGUOUS_BINDING"),
            "Ambiguous name selected first result");
        material(
            root,
            "missing",
            "missing",
            "\"bindings\":[\"gt:NotRegistered\"],\"composition\":{\"source\":\"gregtech\"}");
        value = bind(root);
        check(
            value.diagnostics()
                .stream()
                .anyMatch(s -> s.contains("UNRESOLVED_GT_BINDING")),
            "Unknown optional GT binding not diagnosed");
        material(
            root,
            "missing_alias",
            "missing_alias",
            "\"bindings\":[\"gt:AnotherMissing\",\"name:unavailable_alias\"],\"composition\":{\"source\":\"gregtech\"}");
        check(bind(root).enabled(), "An inactive optional GT/name binding blocked unrelated materials");
        material(
            root,
            "invalid",
            "invalid",
            "\"bindings\":[\"name:invalid\"],\"composition\":{\"source\":\"explicit\",\"basis\":\"mixture_parts\",\"components\":[{\"material\":\"gt:Missing\",\"amount\":1}]}");
        try {
            bind(root);
            throw new AssertionError("Missing live GT reference accepted");
        } catch (PackException correct) {
            check(
                correct.getMessage()
                    .contains("Missing required GT component"),
                "Wrong live diagnostic");
        }
        Files.delete(root.resolve("custom/materials/invalid.jsonc"));
        material(
            root,
            "water",
            "water",
            "\"bindings\":[\"gt:Water\"],\"formula\":\"H2O\",\"composition\":{\"source\":\"formula\"},\"display\":{\"mode\":\"composition\"}");
        value = bind(root);
        water = value.provider("gt:Water");
        var formulaMass = water.definition()
            .mass();
        check(formulaMass.equals(Fraction.of(18)), "Formula mass was not stoichiometric");
        final var snapshot = value;
        var quantity = new CompositionResolver(id -> {
            var e = snapshot.entry(id);
            return e == null ? null : e.definition();
        }).resolve("gt:Water", false, true);
        var mass = new CompositionResolver(id -> {
            var e = snapshot.entry(id);
            return e == null ? null : e.definition();
        }).resolve("gt:Water", true, true);
        check(
            quantity.shares()
                .get("gt:Hydrogen")
                .equals(new Fraction(BigInteger.TWO, BigInteger.valueOf(3))),
            "Atomic counts changed");
        check(
            mass.shares()
                .get("gt:Oxygen")
                .equals(new Fraction(BigInteger.valueOf(8), BigInteger.valueOf(9))),
            "Mass converted more than once");
        material(
            root,
            "unknown",
            "unknown",
            "\"bindings\":[\"name:unknown\"],\"formula\":\"Xx2Yy\",\"composition\":{\"source\":\"formula\"},\"display\":{\"mode\":\"composition\"}");
        final var symbols = bind(root);
        var unknown = new CompositionResolver(id -> {
            var e = symbols.entry(id);
            return e == null ? null : e.definition();
        });
        check(
            unknown.resolve("custom:unknown", false, true)
                .shares()
                .size() == 2,
            "Unknown symbols were merged");
        check(
            !unknown.resolve("custom:unknown", true, true)
                .valid(),
            "Unknown mass fabricated");
        SnapshotStore<RuntimeSnapshot> store = new SnapshotStore<>(RuntimeSnapshot.empty());
        long first = store.request();
        check(store.publish(first, value), "Valid candidate not published");
        var installed = store.value();
        long version = store.version(), stale = store.request(), latest = store.request();
        check(
            !store.publish(stale, RuntimeSnapshot.empty()) && store.value() == installed,
            "Stale worker result published");
        check(
            store.fail(latest, "bad file:4:2") && store.value() == installed && store.version() == version,
            "Failure discarded previous snapshot");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            check(executor.submit(() -> {
                try {
                    store.publish(latest, RuntimeSnapshot.empty());
                    return false;
                } catch (IllegalStateException expected) {
                    return true;
                }
            })
                .get(), "Worker thread published snapshot");
        } finally {
            executor.shutdown();
        }
        Files.delete(root.resolve("custom/materials/water.jsonc"));
        var removed = bind(root);
        check(
            removed.provider("gt:Water")
                .molecule()
                .value() == null,
            "Deleting binding retained old structure");
        check(
            store.publish(store.request(), removed) && store.version() == version + 1,
            "Deletion not committed atomically");
        Path empty = Files.createDirectory(scratch.resolve("empty"));
        check(!bind(empty).enabled(), "No-pack state retained graphics");
        var official = RuntimeSnapshot
            .bind(PackCompiler.compile(project.resolve("data-packs/migrated"), "0.6.0-stage.4"), input());
        check(
            official.catalog()
                .definitions()
                .size() == 1039,
            "Full official pack cannot be mounted");
        check(
            official.provider("gt:Water")
                .molecule()
                .value() != null,
            "Migrated name binding did not match GT Water");
        check(
            official.lookup("name:ice", "ice", null)
                .molecule()
                .value() != null,
            "Migrated alias not loaded");
        check(
            official.lookup("name:benzene", "benzene", null)
                .molecule()
                .value()
                .atoms()
                .getFirst()
                .element()
                .color() == 0xFFABCDEF,
            "Invisible legacy anchor ignored the configured skeleton color");
        var steel = official.lookup("name:steel", "steel", null);
        check(steel.chartEligible(), "Migrated explicit steel chart unavailable");
        var steelShares = new CompositionResolver(id -> {
            var e = official.entry(id);
            return e == null ? null : e.definition();
        }).resolve(
            steel.definition()
                .id(),
            false,
            true);
        check(
            steelShares.shares()
                .get("gt:Iron")
                .equals(new Fraction(BigInteger.valueOf(39), BigInteger.valueOf(44))),
            "Migrated mixture parts changed");
        Path extended = Files.createDirectory(scratch.resolve("extended"));
        for (Path source : List
            .of(project.resolve("data-packs/migrated/official"), project.resolve("data-packs/runtime-demo/user"))) {
            try (var paths = Files.walk(source)) {
                for (Path file : paths.toList()) {
                    Path destination = extended.resolve(source.getFileName())
                        .resolve(source.relativize(file));
                    if (Files.isDirectory(file)) Files.createDirectories(destination);
                    else Files.copy(file, destination);
                }
            }
        }
        check(
            bind(extended).provider("gt:Water")
                .molecule()
                .value() == null,
            "Published optional override demo does not mount");
        Files.delete(extended.resolve("user/materials/water_text.jsonc"));
        check(
            bind(extended).provider("gt:Water")
                .molecule()
                .value() != null,
            "Removing the user override did not restore official binding");
        System.out.println(
            "Runtime pack regression: " + assertions
                + " assertions passed; binding, live validation, deletion, failure retention and thread publication");
    }
}
