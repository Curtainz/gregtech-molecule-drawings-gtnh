package com.rubenverg.moldraw.core;

import java.nio.file.*;
import java.util.*;

import com.rubenverg.moldraw.core.pack.*;

public final class SpeciesRegression {

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]),
            root = Files.createTempDirectory(project.resolve("build"), "species-regression-");
        for (Path source : List.of(
            project.resolve("data-packs/migrated/official"),
            project.resolve("data-packs/runtime-species/gtnh_species"))) try (var stream = Files.walk(source)) {
                for (Path p : stream.toList()) {
                    Path target = root.resolve(source.getFileName())
                        .resolve(source.relativize(p));
                    if (Files.isDirectory(p)) Files.createDirectories(target);
                    else Files.copy(p, target);
                }
            }
        var catalog = PackCompiler.compile(root, "0.7.0-test.B");
        Map<String, RuntimeSnapshot.ProviderMaterial> providers = new HashMap<>();
        Set<String> ids = new HashSet<>(List.of("gt:Iron", "gt:Carbon", "gt:Deuterium", "gt:Gold", "gt:InfusedGold"));
        for (var d : catalog.definitions()
            .values()) {
            var json = d.json();
            for (var binding : PackCompiler.array(json, "bindings")) if (binding.getAsString()
                .startsWith("gt:")) ids.add(binding.getAsString());
            for (var rule : PackCompiler.array(json, "rules")) {
                var r = rule.getAsJsonObject();
                if (r.get("kind")
                    .getAsString()
                    .equals("element_binding")
                    && r.get("material")
                        .getAsString()
                        .startsWith("gt:"))
                    ids.add(
                        r.get("material")
                            .getAsString());
            }
        }
        for (String id : ids) {
            String name = id.substring(3);
            boolean fantasy = List
                .of(
                    "Draconium",
                    "DraconiumAwakened",
                    "Naquadria",
                    "Infinity",
                    "InfinityCatalyst",
                    "Trinium",
                    "Adamantium",
                    "GaiaSpirit",
                    "Manasteel",
                    "Terrasteel",
                    "Quantium",
                    "InfusedGold")
                .contains(name);
            String formula = name.equals("Draconium") ? "D" : name.equals("InfusedGold") ? "AuMa*" : "";
            providers.put(
                id,
                new RuntimeSnapshot.ProviderMaterial(
                    id,
                    name,
                    formula,
                    Set.of(name),
                    -1,
                    fantasy ? null : Fraction.of(2),
                    Fraction.ONE,
                    List.of(),
                    !fantasy));
        }
        var snap = RuntimeSnapshot.bind(catalog, new RuntimeSnapshot.Input(providers, Set.of(), Map.of(), -1, false));
        if (!snap.provider("gt:Draconium")
            .definition()
            .components()
            .isEmpty()) throw new AssertionError("Draconium became deuterium");
        if (snap.provider("gt:Draconium")
            .definition()
            .mass() != null) throw new AssertionError("Invented mass");
        var gold = snap.provider("gt:InfusedGold")
            .definition();
        if (gold.components()
            .stream()
            .noneMatch(
                c -> c.id()
                    .equals("gtnh_species:materials/magic_energized")))
            throw new AssertionError("Ma* binding");
        var resolver = new CompositionResolver(id -> {
            var e = snap.entry(id);
            return e == null ? null : e.definition();
        });
        if (!resolver.resolve("gt:InfusedGold", false, true)
            .valid() || resolver.resolve("gt:InfusedGold", true, true)
                .valid())
            throw new AssertionError("Unknown mass mode");
        if (!ChemicalFormula.parseExact("Nq+(SO4)2", Set.of("Nq+"))
            .containsKey("Nq+")) throw new AssertionError("State token");
        if (!ChemicalFormula.parseExact("C1\uE01A")
            .get("C")
            .equals(java.math.BigInteger.TEN)) throw new AssertionError("GT subscript zero");
        System.out
            .println("Species regression passed: terminal identity, exact bindings, unknown mass and GT notation");
    }
}
