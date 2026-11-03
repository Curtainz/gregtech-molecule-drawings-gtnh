package com.rubenverg.moldraw.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.pack.*;

/** Builds a new pack directory, validates it in isolation, then publishes by directory rename. */
public final class LegacyMigrator {

    private LegacyMigrator() {}

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .serializeNulls()
        .create();

    private record Source(Path path, String relative, String namespace, String name, JsonObject json) {}

    public static void migrate(Path assets, Path metadata, Path output, String version) throws IOException {
        assets = assets.toAbsolutePath()
            .normalize();
        output = output.toAbsolutePath()
            .normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS))
            throw new PackException("Migration output must not exist: " + output);
        Path parent = output.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new PackException("Migration output parent must exist");
        for (Path p = parent; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new PackException("Symlink in migration output path");
        Path realAssets = assets.toRealPath(), realOutput = parent.toRealPath()
            .resolve(output.getFileName());
        if (realOutput.startsWith(realAssets) || realAssets.startsWith(realOutput))
            throw new PackException("Migration source/output overlap");
        JsonObject mappings = Jsonc.read(metadata)
            .object();
        LegacyGeometry.fields(
            mappings,
            "sourceHashes",
            "namespaces",
            "aliases",
            "prefixes",
            "candidates",
            "containsAny",
            "elements");
        List<String> namespaces = new ArrayList<>();
        for (var ns : mappings.getAsJsonArray("namespaces")) namespaces.add(ns.getAsString());
        if (new HashSet<>(namespaces).size() != namespaces.size())
            throw new PackException("Duplicate source namespace");
        List<Source> structures = new ArrayList<>(), compositions = new ArrayList<>();
        try (var paths = Files.walk(assets, 8)) {
            for (Path file : paths.sorted()
                .toList()) {
                if (Files.isSymbolicLink(file)) throw new PackException("Symlink in legacy assets " + file);
                if (!Files.isRegularFile(file)) continue;
                String relative = assets.relativize(file)
                    .toString()
                    .replace('\\', '/');
                if (!relative.matches("[^/]+/(?:molecules|alloys)/.*\\.json")) continue;
                String[] parts = relative.split("/");
                if (parts.length != 3 || !namespaces.contains(parts[0]) || !parts[2].matches("[a-z0-9_]+\\.json"))
                    throw new PackException("Unmapped legacy resource path " + relative);
                Source source = new Source(
                    file,
                    relative,
                    parts[0],
                    parts[2].replace(".json", ""),
                    Jsonc.read(file)
                        .object());
                (parts[1].equals("molecules") ? structures : compositions).add(source);
                if (structures.size() + compositions.size() > 8192)
                    throw new PackException("Too many legacy resources");
            }
        }
        if (structures.isEmpty() || compositions.isEmpty())
            throw new PackException("Expected both legacy structure and composition resources");
        Comparator<Source> priority = Comparator.comparingInt((Source s) -> namespaces.indexOf(s.namespace))
            .thenComparing(Source::relative);
        structures.sort(priority);
        compositions.sort(priority);
        Map<String, Source> geometryWinners = new TreeMap<>(), compositionWinners = new TreeMap<>();
        structures.forEach(s -> geometryWinners.putIfAbsent(s.name, s));
        compositions.forEach(s -> compositionWinners.putIfAbsent(s.name, s));
        Map<String, JsonObject> files = new TreeMap<>();
        JsonArray report = new JsonArray(), issues = new JsonArray();
        JsonObject manifest = LegacyGeometry.object("pack");
        manifest.addProperty("schemaVersion", 1);
        manifest.addProperty("id", "official");
        manifest.addProperty("version", "1.0.0");
        JsonObject range = new JsonObject();
        range.addProperty("min", "0.6.0-stage.3");
        range.addProperty("maxExclusive", "0.7.0");
        manifest.add("modVersion", range);
        manifest.add("dependencies", new JsonArray());
        manifest.add("providers", LegacyGeometry.array("gregtech"));
        manifest.addProperty("description", "Migrated legacy definitions; runtime external loading begins in stage 4.");
        files.put("official/pack.jsonc", manifest);
        for (Source s : structures) {
            String id = geometryId(s);
            JsonObject converted = LegacyGeometry.convert(s.json, id);
            files.put("official/molecules/" + s.namespace + "/" + s.name + ".jsonc", converted);
            int originalBonds = 0;
            for (var content : s.json.getAsJsonArray("contents")) {
                var shape = content.getAsJsonObject();
                String type = shape.get("type")
                    .getAsString();
                if (type.equals("bond")) originalBonds++;
                if (type.equals("benzene")) originalBonds += shape.getAsJsonArray("indices")
                    .size();
            }
            int removed = originalBonds - converted.getAsJsonArray("bonds")
                .size();
            if (removed > 0) issues.add(
                new JsonPrimitive(
                    s.relative + ": removed "
                        + removed
                        + " redundant single-stroke overlays already drawn by an uncentered multi-stroke bond; raster union retained"));
            sourceReport(
                report,
                s,
                id,
                s == geometryWinners.get(s.name) ? "bound_by_name" : "shadowed_structure_preserved");
            for (var shape : s.json.getAsJsonArray("contents")) if (shape.getAsJsonObject()
                .has("stereo"))
                issues.add(
                    new JsonPrimitive(
                        s.relative + ": legacy stereo property was ignored by Bond.Json; strokes retained unchanged"));
        }
        Set<String> names = new TreeSet<>(geometryWinners.keySet());
        names.addAll(compositionWinners.keySet());
        for (String name : names) {
            Source shape = geometryWinners.get(name), composition = compositionWinners.get(name);
            boolean explicit = composition != null && !derived(composition.json);
            if (shape == null && !explicit) continue;
            JsonObject material = definition("material", "official:materials/" + name);
            material.add("bindings", LegacyGeometry.array("name:" + name));
            if (shape != null) material.addProperty("structure", geometryId(shape));
            if (explicit) material.add("composition", composition(composition.json, mappings));
            files.put("official/materials/" + name + ".jsonc", material);
        }
        for (Source s : compositions) {
            LegacyGeometry.fields(s.json, "derive", "components");
            boolean derive = derived(s.json);
            String id = "official:" + s.namespace + "/" + s.name + "_composition_rule";
            if (derive) {
                JsonObject rule = rule("composition_candidate");
                rule.add("names", LegacyGeometry.array(s.name));
                JsonObject definition = definition("bindings", id);
                JsonArray rules = new JsonArray();
                rules.add(rule);
                definition.add("rules", rules);
                files.put("official/bindings/" + s.namespace + "/" + s.name + ".jsonc", definition);
            }
            sourceReport(
                report,
                s,
                derive ? id : "official:materials/" + s.name,
                s == compositionWinners.get(s.name) ? (derive ? "dynamic_gt_candidate" : "explicit_mixture_parts")
                    : "shadowed_composition");
            if (!derive && s != compositionWinners.get(s.name))
                throw new PackException("Shadowed explicit composition requires manual resolution: " + s.relative);
        }
        JsonObject rulesDefinition = definition("bindings", "official:legacy_compatibility");
        JsonArray rules = new JsonArray();
        for (var alias : mappings.getAsJsonObject("aliases")
            .entrySet()) {
            Source target = geometryWinners.get(
                alias.getValue()
                    .getAsString());
            if (target == null) {
                issues.add(
                    new JsonPrimitive(
                        "Unresolved legacy alias: " + alias.getKey()
                            + " -> "
                            + alias.getValue()
                                .getAsString()));
                continue;
            }
            JsonObject rule = rule("alias");
            rule.addProperty("from", alias.getKey());
            rule.addProperty("to", geometryId(target));
            rules.add(rule);
        }
        JsonObject prefix = rule("strip_prefix");
        prefix.add("prefixes", mappings.get("prefixes"));
        prefix.addProperty("target", "structure");
        rules.add(prefix);
        JsonObject candidate = rule("composition_candidate");
        candidate.add("names", mappings.get("candidates"));
        candidate.add("containsAny", mappings.get("containsAny"));
        rules.add(candidate);
        for (var element : mappings.getAsJsonObject("elements")
            .entrySet()) {
            JsonObject rule = rule("element_binding");
            rule.addProperty("symbol", element.getKey());
            rule.addProperty(
                "material",
                "gt:" + element.getValue()
                    .getAsString());
            rules.add(rule);
        }
        rulesDefinition.add("rules", rules);
        files.put("official/bindings/legacy_compatibility.jsonc", rulesDefinition);
        Path staging = Files.createTempDirectory(parent, ".gtmoldraw-migrate-");
        for (var entry : files.entrySet()) {
            Path target = staging.resolve(entry.getKey())
                .normalize();
            if (!target.startsWith(staging)) throw new PackException("Escaping generated path");
            Files.createDirectories(target.getParent());
            ContractSchema schema = ContractSchema.bundled(
                entry.getKey()
                    .endsWith("/pack.jsonc") ? "pack" : "definition");
            String text = JsoncFormatter.format(Jsonc.parse(entry.getKey(), JSON.toJson(entry.getValue())), schema);
            Files.writeString(target, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        PackCatalog catalog = PackCompiler.compile(staging, version);
        JsonObject summary = new JsonObject();
        summary.addProperty("kind", "legacy-migration-stage-3");
        summary.addProperty("structureSources", structures.size());
        summary.addProperty("compositionSources", compositions.size());
        summary.addProperty("effectiveStructureNames", geometryWinners.size());
        summary.addProperty(
            "definitions",
            catalog.definitions()
                .size());
        summary.add("sources", report);
        summary.add("reviewNotes", issues);
        summary.add("legacyJavaSourceHashes", mappings.get("sourceHashes"));
        Files
            .writeString(staging.resolve("migration-report.json"), JSON.toJson(summary) + "\n", StandardCharsets.UTF_8);
        MigrationReport.write(staging, summary);
        // Both resolved paths share the explicitly selected output parent. Never replace an existing directory.
        if (!staging.toRealPath()
            .getParent()
            .equals(parent.toRealPath())
            || !realOutput.getParent()
                .equals(parent.toRealPath()))
            throw new PackException("Unexpected migration publication paths");
        Files.move(staging, realOutput, StandardCopyOption.ATOMIC_MOVE);
    }

    private static JsonObject rule(String kind) {
        JsonObject rule = new JsonObject();
        rule.addProperty("kind", kind);
        return rule;
    }

    private static String geometryId(Source s) {
        return "official:" + s.namespace + "/" + s.name + "_structure";
    }

    private static JsonObject definition(String type, String id) {
        var value = LegacyGeometry.object(type);
        value.addProperty("schemaVersion", 1);
        value.addProperty("id", id);
        return value;
    }

    private static boolean derived(JsonObject json) {
        if (json.has("derive") && json.get("derive")
            .getAsBoolean()) {
            if (json.has("components")) throw new PackException("Ambiguous derive and components");
            return true;
        }
        if (!json.has("components")) throw new PackException("Missing legacy composition");
        return false;
    }

    private static JsonObject composition(JsonObject legacy, JsonObject mappings) {
        var decoded = com.rubenverg.moldraw.core.LegacyComposition.decode(legacy, "migration");
        if (decoded.value() == null || decoded.value()
            .isEmpty()) throw new PackException("Invalid legacy composition " + decoded.diagnostics());
        JsonObject result = new JsonObject();
        result.addProperty("source", "explicit");
        result.addProperty("basis", "mixture_parts");
        JsonArray components = new JsonArray();
        for (var part : decoded.value()) {
            String raw = part.id()
                .substring(
                    part.id()
                        .indexOf(':') + 1),
                key = PackCompiler.normalize(raw)
                    .replace("_", "");
            Set<String> matches = new TreeSet<>();
            for (var element : mappings.getAsJsonObject("elements")
                .entrySet()) {
                String material = element.getValue()
                    .getAsString();
                if (PackCompiler.normalize(material)
                    .replace("_", "")
                    .equals(key)) matches.add(material);
            }
            if (matches.size() != 1) throw new PackException("Legacy component needs exact GT mapping: " + part.id());
            JsonObject c = new JsonObject();
            c.addProperty(
                "material",
                "gt:" + matches.iterator()
                    .next());
            c.addProperty("amount", part.amount());
            components.add(c);
        }
        result.add("components", components);
        return result;
    }

    private static void sourceReport(JsonArray report, Source s, String id, String status) throws IOException {
        JsonObject item = new JsonObject();
        item.addProperty("source", s.relative);
        item.addProperty("definition", id);
        item.addProperty("status", status);
        try {
            item.addProperty(
                "sha256",
                HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest(
                                Files.readString(s.path, StandardCharsets.UTF_8)
                                    .replace("\r\n", "\n")
                                    .getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        report.add(item);
    }
}
