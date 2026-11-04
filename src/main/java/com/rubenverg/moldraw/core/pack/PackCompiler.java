package com.rubenverg.moldraw.core.pack;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.*;

/** File/identity/override compilation shared by the offline tool and the future game loader. */
public final class PackCompiler {

    private record Pack(String id, String version, List<String> dependencies, Jsonc.Document document) {}

    private long sourceBytes;
    private int scannedEntries;

    private void remember(Jsonc.Document document) {
        sourceBytes += document.text()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (sourceBytes > 67_108_864) throw document.error("", "Catalog source exceeds 64 MiB");
        documents.add(document);
    }

    private final ContractSchema manifestSchema = ContractSchema.bundled("pack"),
        definitionSchema = ContractSchema.bundled("definition");
    private final Map<String, Pack> packs = new TreeMap<>();
    private final Map<String, List<PackCatalog.Definition>> groups = new TreeMap<>();
    private final Map<String, PackCatalog.Definition> effective = new TreeMap<>();
    private final Map<String, String> origin = new HashMap<>();
    private final List<Jsonc.Document> documents = new ArrayList<>();
    private final Set<String> providers = new TreeSet<>();
    private final List<String> warnings = new ArrayList<>();

    public static PackCatalog compile(Path root, String modVersion) throws IOException {
        return new PackCompiler().load(root, SemVer.parse(modVersion));
    }

    private PackCatalog load(Path input, SemVer modVersion) throws IOException {
        Path root = input.toAbsolutePath()
            .normalize();
        for (Path p = root; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new PackException("Symlink in pack root: " + p);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new PackException("Pack root is not a directory: " + root);
        Path real = root.toRealPath();
        List<Path> directories;
        try (var stream = Files.list(root)) {
            directories = stream.sorted()
                .toList();
        }
        for (Path directory : directories) {
            if (Files.isSymbolicLink(directory)) throw new PackException("Symlink in pack root: " + directory);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            if (packs.size() >= 32) throw new PackException("More than 32 packs");
            Path manifest = directory.resolve("pack.jsonc");
            Jsonc.Document document = Jsonc.read(manifest);
            manifestSchema.validate(document);
            remember(document);
            JsonObject json = document.object();
            String id = json.get("id")
                .getAsString(),
                version = json.get("version")
                    .getAsString();
            if (!directory.getFileName()
                .toString()
                .equals(id) || packs.containsKey(id)) throw document.error("/id", "Pack ID/directory conflict");
            var range = json.getAsJsonObject("modVersion");
            SemVer min = SemVer.parse(
                range.get("min")
                    .getAsString()),
                max = SemVer.parse(
                    range.get("maxExclusive")
                        .getAsString());
            if (min.compareTo(max) >= 0 || modVersion.compareTo(min) < 0 || modVersion.compareTo(max) >= 0)
                throw document.error("/modVersion", "Incompatible mod version or empty range");
            SemVer.parse(version);
            List<String> dependencies = new ArrayList<>();
            for (var dep : json.getAsJsonArray("dependencies")) {
                String target = dep.getAsJsonObject()
                    .get("id")
                    .getAsString();
                if (target.equals(id) || dependencies.contains(target))
                    throw document.error("/dependencies", "Duplicate/self dependency");
                dependencies.add(target);
            }
            packs.put(id, new Pack(id, version, List.copyOf(dependencies), document));
            for (var p : json.getAsJsonArray("providers")) providers.add(p.getAsString());
            List<Path> files = new ArrayList<>();
            Set<String> foldedPaths = new HashSet<>();
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {

                private void safe(Path path) throws IOException {
                    if (++scannedEntries > 65_536) throw new PackException("Pack scan exceeds 65536 entries");
                    if (Files.isSymbolicLink(path) || !path.toRealPath()
                        .startsWith(real)) throw new PackException("Symlink or escaping path: " + path);
                }

                @Override
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                    safe(path);
                    if (directory.relativize(path)
                        .getNameCount() > 64) throw new PackException("Directory nesting exceeds 64: " + path);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    safe(path);
                    String relative = directory.relativize(path)
                        .toString()
                        .replace('\\', '/');
                    if (!foldedPaths.add(relative.toLowerCase(Locale.ROOT)))
                        throw new PackException("Case-insensitive file collision: " + path);
                    if (!path.equals(manifest) && relative.endsWith(".jsonc")) files.add(path);
                    return FileVisitResult.CONTINUE;
                }
            });
            files.sort(Comparator.comparing(Path::toString));
            for (Path file : files) {
                if (documents.size() - packs.size() >= 8192) throw new PackException("More than 8192 definitions");
                String relative = directory.relativize(file)
                    .toString()
                    .replace('\\', '/');
                if (!relative.matches("(?:materials|molecules|fragments|bindings)/(?:[a-z0-9_]+/)*[a-z0-9_]+\\.jsonc"))
                    throw new PackException("Invalid definition path: " + file);
                Jsonc.Document data = Jsonc.read(file);
                definitionSchema.validate(data);
                remember(data);
                JsonObject object = data.object();
                String type = object.get("type")
                    .getAsString(),
                    definitionId = object.get("id")
                        .getAsString();
                String expected = type.equals("bindings") ? "bindings/" : type + "s/";
                if (!relative.startsWith(expected))
                    throw data.error("/type", "Definition kind does not match directory");
                var definition = new PackCatalog.Definition(id, definitionId, type, data);
                var group = groups.computeIfAbsent(definitionId, k -> new ArrayList<>());
                if (group.stream()
                    .anyMatch(
                        d -> d.owner()
                            .equals(id)))
                    throw definition.error("/id", "Duplicate definition in pack");
                if (!object.has("overrides") && !definitionId.startsWith(id + ":"))
                    throw definition.error("/id", "Namespace not owned by pack");
                group.add(definition);
            }
        }
        for (var pack : packs.values()) for (var dep : pack.document.object()
            .getAsJsonArray("dependencies")) {
                var object = dep.getAsJsonObject();
                Pack target = packs.get(
                    object.get("id")
                        .getAsString());
                if (target == null || !target.version.equals(
                    object.get("version")
                        .getAsString()))
                    throw pack.document.error("/dependencies", "Missing dependency/exact version mismatch");
            }
        for (var pack : packs.keySet()) packOrder(pack, new LinkedHashSet<>(), new HashSet<>());
        for (var group : groups.entrySet()) overrides(group.getKey(), group.getValue());
        references();
        bindings();
        ModuleAssembler assembler = new ModuleAssembler(effective);
        Map<String, CompiledMolecule> compiled = new TreeMap<>();
        long graphElements = 0;
        for (var definition : effective.values()) if (Set.of("molecule", "fragment")
            .contains(definition.type())) {
                var geometry = assembler.compile(definition.id());
                graphElements += (long) geometry.atoms()
                    .size() + geometry.bonds()
                        .size()
                    + geometry.annotations()
                        .size();
                if (graphElements > 1_000_000)
                    throw definition.error("", "Catalog expanded geometry exceeds one million elements");
                compiled.put(definition.id(), geometry);
            }
        if (packs.isEmpty()) warnings.add("NO_PACKS: graph definitions are empty");
        if (providers.contains("gregtech")) warnings
            .add("OFFLINE_GT: external GT identities and masses require game validation; no values are fabricated");
        return new PackCatalog(effective, compiled, documents, providers, warnings, packs.size());
    }

    private void packOrder(String id, Set<String> path, Set<String> done) {
        if (path.size() > 32 || path.contains(id))
            throw packs.get(id).document.error("/dependencies", "Dependency cycle: " + path + " -> " + id);
        if (done.contains(id)) return;
        path.add(id);
        for (String child : packs.get(id).dependencies) packOrder(child, path, done);
        path.remove(id);
        done.add(id);
    }

    private void overrides(String id, List<PackCatalog.Definition> group) {
        var roots = group.stream()
            .filter(
                d -> !d.json()
                    .has("overrides"))
            .toList();
        if (roots.size() != 1) throw group.getFirst()
            .error("/id", "Missing or ambiguous original definition " + id);
        var root = roots.getFirst();
        origin.put(id, root.owner());
        Map<String, PackCatalog.Definition> children = new HashMap<>();
        for (var definition : group) if (definition.json()
            .has("overrides")) {
                var override = definition.json()
                    .getAsJsonObject("overrides");
                String parent = override.get("pack")
                    .getAsString();
                if (!override.get("id")
                    .getAsString()
                    .equals(id) || !packs.get(definition.owner()).dependencies.contains(parent))
                    throw definition.error("/overrides", "Override must retain ID and directly depend on provider");
                var previous = group.stream()
                    .filter(
                        d -> d.owner()
                            .equals(parent))
                    .findFirst()
                    .orElseThrow(() -> definition.error("/overrides", "Missing target provider"));
                if (!previous.type()
                    .equals(definition.type()))
                    throw definition.error("/overrides", "Override changed definition kind");
                if (children.putIfAbsent(parent, definition) != null)
                    throw definition.error("/overrides", "Independent override branches are ambiguous");
            }
        Set<String> visited = new HashSet<>();
        var current = root;
        while (true) {
            if (!visited.add(current.owner())) throw current.error("/overrides", "Override cycle");
            var next = children.get(current.owner());
            if (next == null) break;
            current = next;
        }
        if (visited.size() != group.size()) throw current.error("/overrides", "Disconnected override chain");
        effective.put(id, current);
    }

    private boolean depends(String pack, String target, Set<String> seen) {
        if (pack.equals(target)) return true;
        if (!seen.add(pack)) return false;
        for (String dep : packs.get(pack).dependencies) if (depends(dep, target, seen)) return true;
        return false;
    }

    private void reference(PackCatalog.Definition from, String target, String expected, String pointer) {
        var definition = effective.get(target);
        if (definition == null || !definition.type()
            .equals(expected)) throw from.error(pointer, "Missing/wrong-kind reference " + target);
        if (!depends(from.owner(), origin.get(target), new HashSet<>()))
            throw from.error(pointer, "Undeclared cross-pack reference " + target);
    }

    private void references() {
        Map<String, List<String>> materialGraph = new TreeMap<>();
        for (var definition : effective.values()) {
            JsonObject json = definition.json();
            if (json.has("structure")) reference(
                definition,
                json.get("structure")
                    .getAsString(),
                "molecule",
                "/structure");
            int i = 0;
            for (var instance : array(json, "instances")) {
                var object = instance.getAsJsonObject();
                if (object.has("fragment")) reference(
                    definition,
                    object.get("fragment")
                        .getAsString(),
                    "fragment",
                    "/instances/" + i + "/fragment");
                i++;
            }
            i = 0;
            for (var rule : array(json, "rules")) {
                var object = rule.getAsJsonObject();
                if (object.get("kind")
                    .getAsString()
                    .equals("alias"))
                    reference(
                        definition,
                        object.get("to")
                            .getAsString(),
                        "molecule",
                        "/rules/" + i + "/to");
                i++;
            }
            if (!definition.type()
                .equals("material")) continue;
            Set<String> gt = new HashSet<>();
            for (var binding : json.getAsJsonArray("bindings")) if (binding.getAsString()
                .startsWith("gt:")) gt.add(binding.getAsString());
            if (json.has("terminal") && json.get("terminal")
                .getAsBoolean() && json.has("composition"))
                throw definition.error("/terminal", "Terminal species cannot declare composition");
            var composition = json.has("composition") ? json.getAsJsonObject("composition") : null;
            if (composition != null) {
                String source = composition.get("source")
                    .getAsString();
                if (source.equals("gregtech") && gt.size() != 1)
                    throw definition.error("/composition", "GT composition requires exactly one GT identity");
                if (source.equals("formula")) {
                    try {
                        ChemicalFormula.parseExact(
                            json.has("formula") ? json.get("formula")
                                .getAsString() : "");
                    } catch (IllegalArgumentException error) {
                        throw definition.error("/formula", error.getMessage());
                    }
                }
                if (source.equals("explicit")) {
                    List<String> children = new ArrayList<>();
                    i = 0;
                    BigInteger lcm = BigInteger.ONE;
                    for (var component : composition.getAsJsonArray("components")) {
                        var c = component.getAsJsonObject();
                        String target = c.get("material")
                            .getAsString();
                        Fraction amount;
                        try {
                            amount = amount(c.get("amount"));
                        } catch (PackException failure) {
                            throw definition.error("/composition/components/" + i + "/amount", failure.getMessage());
                        }
                        lcm = lcm.divide(lcm.gcd(amount.denominator()))
                            .multiply(amount.denominator());
                        numericBudget(lcm);
                        if (!target.startsWith("gt:")) {
                            reference(definition, target, "material", "/composition/components/" + i + "/material");
                            children.add(target);
                        }
                        i++;
                    }
                    materialGraph.put(definition.id(), children);
                }
            } else if (gt.size() > 1) throw definition
                .error("/bindings", "Implicit GT composition/identity is ambiguous; split the material definition");
            String mode = json.has("display") ? json.getAsJsonObject("display")
                .get("mode")
                .getAsString() : "auto";
            if (mode.equals("molecule") && !json.has("structure"))
                throw definition.error("/display", "Molecule display needs structure");
            if (mode.equals("composition") && composition == null && gt.size() != 1)
                throw definition.error("/display", "Composition display needs composition or one GT target");
        }
        for (String id : materialGraph.keySet())
            materialPath(id, materialGraph, new LinkedHashSet<>(), 0, new int[] { 10000 });
    }

    private void materialPath(String id, Map<String, List<String>> graph, Set<String> path, int depth, int[] budget) {
        if (--budget[0] < 0 || depth > 32 || !path.add(id)) throw effective.get(id)
            .error("/composition", "Composition cycle/depth/work limit: " + path + " -> " + id);
        for (String child : graph.getOrDefault(id, List.of())) materialPath(child, graph, path, depth + 1, budget);
        path.remove(id);
    }

    private void bindings() {
        Map<String, String> bindings = new HashMap<>(), aliases = new HashMap<>(), elements = new HashMap<>();
        for (var definition : effective.values()) {
            var json = definition.json();
            for (var binding : array(json, "bindings")) {
                String key = binding.getAsString();
                if (key.startsWith("name:")) key = "name:" + key.substring(5)
                    .replaceAll("(?i)§[0-9a-fk-or]", "")
                    .trim()
                    .toLowerCase(Locale.ROOT);
                unique(bindings, key, definition, "/bindings");
            }
            for (var rule : array(json, "rules")) {
                var r = rule.getAsJsonObject();
                String kind = r.get("kind")
                    .getAsString();
                if (kind.equals("alias")) unique(
                    aliases,
                    normalize(
                        r.get("from")
                            .getAsString()),
                    definition,
                    "/rules");
                if (kind.equals("element_binding") && !r.get("material")
                    .getAsString()
                    .startsWith("gt:")) reference(
                        definition,
                        r.get("material")
                            .getAsString(),
                        "material",
                        "/rules");
                if (kind.equals("element_binding")) unique(
                    elements,
                    r.get("symbol")
                        .getAsString(),
                    definition,
                    "/rules");
                if (kind.equals("strip_prefix")) {
                    Set<String> prefixes = new HashSet<>();
                    for (var prefix : r.getAsJsonArray("prefixes"))
                        if (!prefixes.add(prefix.getAsString())) throw definition.error("/rules", "Repeated prefix");
                }
            }
        }
    }

    public static String normalize(String s) {
        return s.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("[ .-]+", "_")
            .toLowerCase(Locale.ROOT);
    }

    private void unique(Map<String, String> map, String key, PackCatalog.Definition definition, String pointer) {
        if (map.putIfAbsent(key, definition.id()) != null)
            throw definition.error(pointer, "Duplicate binding/rule " + key);
    }

    public static JsonArray array(JsonObject json, String key) {
        return json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
    }

    public static Fraction amount(JsonElement value) {
        String text = value.getAsString();
        if (text.length() > 4096) throw new PackException("Amount exceeds digit budget");
        try {
            String[] terms = text.split("/", -1);
            Fraction result = value.getAsJsonPrimitive()
                .isNumber()
                    ? new Fraction(
                        value.getAsBigDecimal()
                            .toBigIntegerExact(),
                        BigInteger.ONE)
                    : new Fraction(
                        new BigInteger(terms[0]),
                        terms.length == 2 ? new BigInteger(terms[1]) : BigInteger.ONE);
            if (terms.length > 2 || result.compareTo(Fraction.ZERO) <= 0) throw new IllegalArgumentException();
            numericBudget(result.numerator());
            numericBudget(result.denominator());
            return result;
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            throw new PackException("Invalid exact positive amount " + text);
        }
    }

    public static void numericBudget(BigInteger number) {
        if (number.bitLength() > 131072) throw new PackException("Exact arithmetic exceeds 131072-bit budget");
    }
}
