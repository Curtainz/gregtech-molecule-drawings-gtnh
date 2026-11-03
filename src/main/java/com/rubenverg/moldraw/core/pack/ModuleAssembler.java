package com.rubenverg.moldraw.core.pack;

import java.util.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.MoleculeScene;
import com.rubenverg.moldraw.core.pack.CompiledMolecule.*;

/** Explicit assembly only: no valence guessing, layout inference, network or game access. */
public final class ModuleAssembler {

    private record Transform(double a, double b, double c, double d, double x, double y) {

        static Transform identity() {
            return new Transform(1, 0, 0, 1, 0, 0);
        }

        Transform then(Transform local) {
            return new Transform(
                a * local.a + b * local.c,
                a * local.b + b * local.d,
                c * local.a + d * local.c,
                c * local.b + d * local.d,
                a * local.x + b * local.y + x,
                c * local.x + d * local.y + y);
        }

        double px(double u, double v) {
            return a * u + b * v + x;
        }

        double py(double u, double v) {
            return c * u + d * v + y;
        }

        boolean mirrored() {
            return a * d - b * c < 0;
        }
    }

    private record Origin(PackCatalog.Definition definition, String pointer) {

        PackException error(String message) {
            return definition.error(pointer, message);
        }
    }

    private record Merge(String from, String to, PackCatalog.Definition definition, String pointer) {}

    private static final class Graph {

        final LinkedHashMap<String, Atom> atoms = new LinkedHashMap<>();
        final List<Bond> bonds = new ArrayList<>();
        final Map<Bond, Origin> bondSources = new IdentityHashMap<>();
        final List<Annotation> annotations = new ArrayList<>();
        final Map<String, String> ports = new LinkedHashMap<>();
        final List<Merge> merges = new ArrayList<>();
        int remaining = 10000;

        void step(PackCatalog.Definition d, String pointer) {
            if (--remaining < 0) throw d.error(pointer, "Assembly work budget exceeded");
        }
    }

    private final Map<String, PackCatalog.Definition> definitions;

    public ModuleAssembler(Map<String, PackCatalog.Definition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public CompiledMolecule compile(String id) {
        var definition = definitions.get(id);
        if (definition == null) throw new PackException("Unknown geometry " + id);
        Graph graph = new Graph();
        expand(definition, "", Transform.identity(), new LinkedHashSet<>(), graph);
        for (var port : graph.ports.keySet())
            if (graph.atoms.containsKey(port)) throw definition.error("/ports", "Port shadows atom " + port);
        Map<String, String> resolvedPorts = new TreeMap<>();
        for (String p : graph.ports.keySet()) resolvedPorts.put(p, endpoint(p, graph, new HashSet<>(), definition));
        Map<String, String> parents = new HashMap<>();
        for (String atom : graph.atoms.keySet()) parents.put(atom, atom);
        Map<String, Set<String>> directed = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (var merge : graph.merges) {
            if (!graph.atoms.containsKey(merge.from) || !graph.atoms.containsKey(merge.to))
                throw merge.definition.error(merge.pointer, "atomMap source/target must be real atoms, not ports");
            if (merge.from.equals(merge.to)) throw merge.definition.error(merge.pointer, "Self mapping");
            if (directed.computeIfAbsent(merge.from, k -> new HashSet<>())
                .add(merge.to)) {
                indegree.putIfAbsent(merge.from, 0);
                indegree.merge(merge.to, 1, Integer::sum);
            }
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        indegree.forEach((key, n) -> { if (n == 0) ready.add(key); });
        int visited = 0;
        while (!ready.isEmpty()) {
            String n = ready.remove();
            visited++;
            for (String next : directed.getOrDefault(n, Set.of()))
                if (indegree.compute(next, (k, v) -> v - 1) == 0) ready.add(next);
        }
        if (visited != indegree.size()) throw definition.error("/instances", "Cyclic atomMap aliases");
        for (var merge : graph.merges) {
            String a = find(parents, merge.from), b = find(parents, merge.to);
            if (a.compareTo(b) < 0) parents.put(b, a);
            else parents.put(a, b);
        }
        LinkedHashMap<String, Atom> atoms = new LinkedHashMap<>();
        for (var atom : graph.atoms.values()) {
            String root = find(parents, atom.id());
            Atom canonical = graph.atoms.get(root);
            if (!atom.style()
                .equals(canonical.style()) || Math.hypot(atom.x() - canonical.x(), atom.y() - canonical.y()) > 1e-6)
                throw definition
                    .error("/instances", "Shared atom attributes/position conflict: " + atom.id() + " vs " + root);
            atoms.putIfAbsent(root, new Atom(root, canonical.x(), canonical.y(), canonical.style()));
        }
        LinkedHashMap<String, Bond> bonds = new LinkedHashMap<>();
        for (var edge : graph.bonds) {
            Origin where = graph.bondSources.get(edge);
            String from = find(parents, locatedEndpoint(edge.from(), graph, where, id)),
                to = find(parents, locatedEndpoint(edge.to(), graph, where, id));
            if (from.equals(to)) throw where.error("Sharing created a self bond in " + id + ": " + from);
            boolean directedBond = edge.lines()
                .stream()
                .anyMatch(l -> l == MoleculeScene.Line.INWARD || l == MoleculeScene.Line.OUTWARD);
            String key = from.compareTo(to) < 0 ? from + "\n" + to : to + "\n" + from;
            Bond canonical = new Bond(from, to, edge.centered(), edge.lines());
            Bond previous = bonds.get(key);
            if (previous != null && (!previous.lines()
                .equals(canonical.lines())
                || previous.centered() != canonical.centered()
                || (directedBond && (!previous.from()
                    .equals(from)
                    || !previous.to()
                        .equals(to)))))
                throw where.error("Conflicting duplicate bond in " + id + " " + key.replace('\n', '-'));
            bonds.putIfAbsent(key, canonical);
        }
        List<Annotation> annotations = new ArrayList<>();
        for (var a : graph.annotations) {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (String ref : a.atoms()) ids.add(find(parents, endpoint(ref, graph, new HashSet<>(), definition)));
            if (!Double.isFinite(a.x()) || !Double.isFinite(a.y())
                || Math.abs(a.x()) > 1_000_000
                || Math.abs(a.y()) > 1_000_000
                || a.matrix()
                    .stream()
                    .anyMatch(v -> !Double.isFinite(v) || Math.abs(v) > 1_000_000))
                throw definition.error("/annotations", "Transformed annotation exceeds numeric bounds");
            annotations.add(
                new Annotation(
                    a.type(),
                    new ArrayList<>(ids),
                    a.sub(),
                    a.sup(),
                    a.text(),
                    a.x(),
                    a.y(),
                    a.matrix(),
                    a.color()));
        }
        resolvedPorts.replaceAll((key, value) -> find(parents, value));
        if (atoms.size() + bonds.size() + annotations.size() > 16384)
            throw definition.error("", "Too many geometry elements");
        return new CompiledMolecule(
            id,
            new ArrayList<>(atoms.values()),
            new ArrayList<>(bonds.values()),
            annotations,
            resolvedPorts);
    }

    private String locatedEndpoint(String ref, Graph graph, Origin where, String root) {
        try {
            return endpoint(ref, graph, new HashSet<>(), where.definition);
        } catch (PackException invalid) {
            throw where.error("While expanding " + root + " via " + ref + ": " + invalid.getMessage());
        }
    }

    private static String find(Map<String, String> parents, String id) {
        String root = id;
        while (!parents.get(root)
            .equals(root)) root = parents.get(root);
        while (!id.equals(root)) {
            String next = parents.get(id);
            parents.put(id, root);
            id = next;
        }
        return root;
    }

    private String endpoint(String id, Graph graph, Set<String> path, PackCatalog.Definition definition) {
        String current = id;
        while (!graph.atoms.containsKey(current)) {
            if (!path.add(current) || path.size() > 32)
                throw definition.error("/ports", "Port alias cycle/depth limit: " + path);
            current = graph.ports.get(current);
            if (current == null) throw definition.error("", "Unknown atom/port " + id);
        }
        return current;
    }

    private void expand(PackCatalog.Definition definition, String prefix, Transform transform, Set<String> stack,
        Graph graph) {
        if (stack.size() >= 32 || !stack.add(definition.id()))
            throw definition.error("/instances", "Fragment cycle/depth limit: " + stack + " -> " + definition.id());
        graph.step(definition, "/instances");
        JsonObject json = definition.json();
        Set<String> local = new HashSet<>();
        try {
            int i = 0;
            for (var value : PackCompiler.array(json, "atoms")) {
                JsonObject atom = value.getAsJsonObject();
                String name = atom.get("id")
                    .getAsString();
                unique(local, name, definition, "/atoms/" + i + "/id");
                double[] position = position(atom, "position");
                addAtom(
                    prefix + name,
                    position[0],
                    position[1],
                    style(atom, definition, "/atoms/" + i),
                    transform,
                    definition,
                    graph);
                i++;
            }
            i = 0;
            for (var value : PackCompiler.array(json, "instances")) {
                JsonObject instance = value.getAsJsonObject();
                String name = instance.get("id")
                    .getAsString(), childPrefix = prefix + name + ".";
                String pointer = "/instances/" + i;
                unique(local, name, definition, pointer + "/id");
                graph.step(definition, pointer);
                Transform world = transform.then(transform(instance));
                if (instance.has("fragment")) {
                    var child = definitions.get(
                        instance.get("fragment")
                            .getAsString());
                    if (child == null || !child.type()
                        .equals("fragment")) throw definition.error(pointer, "Unknown fragment");
                    expand(child, childPrefix, world, stack, graph);
                } else ring(instance, childPrefix, world, definition, pointer, graph);
                if (instance.has("atomMap")) for (var mapping : instance.getAsJsonObject("atomMap")
                    .entrySet()) {
                        String from = childPrefix + mapping.getKey(), to = prefix + mapping.getValue()
                            .getAsString();
                        if (!graph.atoms.containsKey(from) || to.startsWith(childPrefix)) throw definition.error(
                            pointer + "/atomMap",
                            "Source must be an internal real atom; target must be outside the instance");
                        graph.merges.add(new Merge(from, to, definition, pointer + "/atomMap"));
                    }
                i++;
            }
            i = 0;
            for (var value : PackCompiler.array(json, "bonds")) {
                JsonObject bond = value.getAsJsonObject();
                List<MoleculeScene.Line> lines = new ArrayList<>();
                if (bond.has("strokes")) for (var line : bond.getAsJsonArray("strokes")) lines.add(
                    MoleculeScene.Line.valueOf(
                        line.getAsString()
                            .toUpperCase(Locale.ROOT)));
                else for (int n = 0; n < bond.get("order")
                    .getAsInt(); n++)
                    lines.add(MoleculeScene.Line.valueOf(text(bond, "style", "solid").toUpperCase(Locale.ROOT)));
                addBond(
                    prefix + bond.get("from")
                        .getAsString(),
                    prefix + bond.get("to")
                        .getAsString(),
                    bond.has("centered") && bond.get("centered")
                        .getAsBoolean(),
                    lines,
                    transform,
                    definition,
                    graph);
                graph.bondSources.put(graph.bonds.getLast(), new Origin(definition, "/bonds/" + i));
                i++;
            }
            if (json.has("ports")) for (var port : json.getAsJsonObject("ports")
                .entrySet()) {
                    unique(local, port.getKey(), definition, "/ports");
                    String key = prefix + port.getKey();
                    if (graph.ports.putIfAbsent(
                        key,
                        prefix + port.getValue()
                            .getAsString())
                        != null) throw definition.error("/ports", "Duplicate port " + key);
                }
            for (var value : PackCompiler.array(json, "annotations")) {
                JsonObject a = value.getAsJsonObject();
                String type = a.get("type")
                    .getAsString();
                List<String> ids = new ArrayList<>();
                for (var ref : PackCompiler.array(a, "atoms")) ids.add(prefix + ref.getAsString());
                double[] p = a.has("position") ? position(a, "position") : new double[] { 0, 0 };
                List<Double> matrix = List.of();
                if (a.has("matrix")) {
                    var m = a.getAsJsonArray("matrix");
                    double m00 = m.get(0)
                        .getAsDouble(),
                        m01 = m.get(1)
                            .getAsDouble(),
                        m10 = m.get(2)
                            .getAsDouble(),
                        m11 = m.get(3)
                            .getAsDouble();
                    matrix = List.of(
                        transform.a * m00 + transform.b * m01,
                        transform.c * m00 + transform.d * m01,
                        transform.a * m10 + transform.b * m11,
                        transform.c * m10 + transform.d * m11);
                }
                graph.step(definition, "/annotations");
                graph.annotations.add(
                    new Annotation(
                        type,
                        ids,
                        text(a, "sub", ""),
                        text(a, "sup", ""),
                        text(a, "text", ""),
                        transform.px(p[0], p[1]),
                        transform.py(p[0], p[1]),
                        matrix,
                        color(a)));
            }
        } finally {
            stack.remove(definition.id());
        }
    }

    private void ring(JsonObject instance, String prefix, Transform transform, PackCatalog.Definition definition,
        String pointer, Graph graph) {
        String module = instance.get("module")
            .getAsString();
        JsonObject p = instance.has("parameters") ? instance.getAsJsonObject("parameters") : new JsonObject();
        int n = module.equals("core:benzene") ? 6
            : p.getAsJsonArray("elements")
                .size();
        double length = p.has("bondLength") ? p.get("bondLength")
            .getAsDouble() : 1;
        if (!module.equals("core:benzene") && !module.equals("core:ring"))
            throw definition.error(pointer, "Unknown native module " + module);
        if (module.equals("core:ring") && p.getAsJsonArray("bondOrders")
            .size() != n)
            throw definition.error(pointer + "/parameters", "Ring elements and bondOrders lengths differ");
        double radius = length / (2 * Math.sin(Math.PI / n));
        for (int i = 0; i < n; i++) {
            String element = module.equals("core:benzene") ? "C"
                : p.getAsJsonArray("elements")
                    .get(i)
                    .getAsString();
            Style style = new Style(
                element,
                null,
                element.equals("C") ? "skeletal" : "symbol",
                null,
                null,
                0,
                null,
                Map.of());
            addAtom(
                prefix + "a" + i,
                Math.cos(2 * Math.PI * i / n) * radius,
                Math.sin(2 * Math.PI * i / n) * radius,
                style,
                transform,
                definition,
                graph);
        }
        for (int i = 0; i < n; i++) {
            int order = module.equals("core:benzene") ? ((i + (p.has("doubleBondStart") ? p.get("doubleBondStart")
                .getAsInt() : 1)) % 2 == 0 ? 2 : 1)
                : p.getAsJsonArray("bondOrders")
                    .get(i)
                    .getAsInt();
            addBond(
                prefix + "a" + i,
                prefix + "a" + ((i + 1) % n),
                false,
                Collections.nCopies(order, MoleculeScene.Line.SOLID),
                transform,
                definition,
                graph);
        }
    }

    private Style style(JsonObject atom, PackCatalog.Definition definition, String pointer) {
        Map<String, Decoration> labels = new LinkedHashMap<>();
        if (atom.has("labels")) for (var label : atom.getAsJsonObject("labels")
            .entrySet()) {
                var d = label.getValue()
                    .getAsJsonObject();
                labels.put(
                    label.getKey(),
                    new Decoration(
                        d.get("text")
                            .getAsString(),
                        text(d, "element", null),
                        color(d)));
            }
        Integer hydrogens = atom.has("hydrogens") ? atom.get("hydrogens")
            .getAsInt() : null;
        if (hydrogens != null && hydrogens > 0 && labels.containsKey("right"))
            throw definition.error(pointer, "Hydrogen label conflicts with explicit right decoration");
        return new Style(
            atom.get("element")
                .isJsonNull() ? null
                    : atom.get("element")
                        .getAsString(),
            text(atom, "label", null),
            text(atom, "labelMode", "symbol"),
            hydrogens,
            atom.has("isotope") ? atom.get("isotope")
                .getAsInt() : null,
            atom.has("charge") ? atom.get("charge")
                .getAsInt() : 0,
            color(atom),
            labels);
    }

    private void addAtom(String id, double x, double y, Style style, Transform transform,
        PackCatalog.Definition definition, Graph graph) {
        graph.step(definition, "/atoms");
        double px = transform.px(x, y), py = transform.py(x, y);
        if (!Double.isFinite(px) || !Double.isFinite(py) || Math.abs(px) > 1_000_000 || Math.abs(py) > 1_000_000)
            throw definition.error("/atoms", "Transformed atom exceeds coordinate budget");
        if (graph.atoms.putIfAbsent(id, new Atom(id, px, py, style)) != null)
            throw definition.error("/atoms", "Duplicate expanded atom " + id);
    }

    private void addBond(String from, String to, boolean centered, List<MoleculeScene.Line> lines, Transform transform,
        PackCatalog.Definition definition, Graph graph) {
        graph.step(definition, "/bonds");
        List<MoleculeScene.Line> adjusted = lines.stream()
            .map(
                l -> transform.mirrored() ? (l == MoleculeScene.Line.INWARD ? MoleculeScene.Line.OUTWARD
                    : l == MoleculeScene.Line.OUTWARD ? MoleculeScene.Line.INWARD : l) : l)
            .toList();
        Bond edge = new Bond(from, to, centered, adjusted);
        graph.bonds.add(edge);
        graph.bondSources.put(edge, new Origin(definition, "/bonds"));
    }

    private static Transform transform(JsonObject instance) {
        double[] origin = instance.has("origin") ? position(instance, "origin") : new double[] { 0, 0 };
        double angle = Math.toRadians(
            instance.has("rotation") ? instance.get("rotation")
                .getAsDouble() : 0),
            scale = instance.has("scale") ? instance.get("scale")
                .getAsDouble() : 1;
        String mirror = text(instance, "mirror", "none");
        double sx = scale * (mirror.equals("x") ? -1 : 1), sy = scale * (mirror.equals("y") ? -1 : 1),
            cos = Math.cos(angle), sin = Math.sin(angle);
        return new Transform(cos * sx, -sin * sy, sin * sx, cos * sy, origin[0], origin[1]);
    }

    private static void unique(Set<String> names, String name, PackCatalog.Definition definition, String pointer) {
        if (!names.add(name)) throw definition.error(pointer, "Duplicate local atom/instance/port ID " + name);
    }

    static String text(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key)
            .getAsString() : fallback;
    }

    private static String color(JsonObject object) {
        return object.has("color") ? object.get("color")
            .getAsString()
            .toUpperCase(Locale.ROOT) : null;
    }

    private static double[] position(JsonObject object, String key) {
        var p = object.getAsJsonArray(key);
        return new double[] { p.get(0)
            .getAsDouble(),
            p.get(1)
                .getAsDouble() };
    }
}
