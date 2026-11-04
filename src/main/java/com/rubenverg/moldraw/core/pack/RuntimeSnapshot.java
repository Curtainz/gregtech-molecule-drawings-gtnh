package com.rubenverg.moldraw.core.pack;

import java.math.BigInteger;
import java.util.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.*;
import com.rubenverg.moldraw.core.MaterialDefinition.*;

/** Immutable live binding of a parsed pack to copied provider data. No game objects or GL. */
public final class RuntimeSnapshot {

    public record ProviderMaterial(String id, String name, String formula, Set<String> names, int color,
        Fraction elementMass, Fraction density, List<Component> components, boolean elemental,
        List<String> diagnostics) {

        public ProviderMaterial(String id, String name, String formula, Set<String> names, int color,
            Fraction elementMass, Fraction density, List<Component> components, boolean elemental) {
            this(id, name, formula, names, color, elementMass, density, components, elemental, List.of());
        }

        public ProviderMaterial {
            names = Set.copyOf(names);
            components = List.copyOf(components);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record Input(Map<String, ProviderMaterial> materials, Set<String> fluids, Map<String, Integer> colors,
        int defaultColor, boolean optionalColors) {

        public Input {
            materials = Map.copyOf(materials);
            fluids = Set.copyOf(fluids);
            colors = Map.copyOf(colors);
        }
    }

    public record Entry(MaterialDefinition definition, DefinitionResult<MoleculeScene> molecule,
        boolean chartEligible) {}

    private record Selection(String material, String structure, boolean ambiguous) {

        static Selection none() {
            return new Selection(null, null, false);
        }
    }

    private record Draft(String id, String name, String formula, String structure, int color, State state, Basis basis,
        List<Component> parts, String source, List<String> diagnostics, MassResolver.Node massNode, String mode,
        boolean candidate) {}

    private final PackCatalog catalog;
    private final Input input;
    private final Map<String, Entry> entries;
    private final Map<String, String> exact, names, aliases, elements;
    private final Set<String> candidateIds, candidateNames;
    private final List<String> prefixes, contains;
    private final Map<String, MoleculeScene> scenes;
    private final List<String> diagnostics;

    private RuntimeSnapshot(PackCatalog catalog, Input input, Map<String, Entry> entries, Map<String, String> exact,
        Map<String, String> names, Map<String, String> aliases, Map<String, String> elements, Set<String> candidateIds,
        Set<String> candidateNames, List<String> prefixes, List<String> contains, Map<String, MoleculeScene> scenes,
        List<String> diagnostics) {
        this.catalog = catalog;
        this.input = input;
        this.entries = Map.copyOf(entries);
        this.exact = Map.copyOf(exact);
        this.names = Map.copyOf(names);
        this.aliases = Map.copyOf(aliases);
        this.elements = Map.copyOf(elements);
        this.candidateIds = Set.copyOf(candidateIds);
        this.candidateNames = Set.copyOf(candidateNames);
        this.prefixes = List.copyOf(prefixes);
        this.contains = List.copyOf(contains);
        this.scenes = Map.copyOf(scenes);
        this.diagnostics = List.copyOf(diagnostics);
    }

    public static RuntimeSnapshot empty() {
        return new RuntimeSnapshot(
            null,
            new Input(Map.of(), Set.of(), Map.of(), 0xFFFFFFFF, false),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Set.of(),
            Set.of(),
            List.of(),
            List.of(),
            Map.of(),
            List.of("NO_VALID_PACK: install config/gtmoldraw/packs/<id>/pack.jsonc then /gtmoldraw reload"));
    }

    public PackCatalog catalog() {
        return catalog;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public boolean enabled() {
        return catalog != null && catalog.packCount() > 0;
    }

    public int materialCount() {
        return entries.size();
    }

    public Entry entry(String id) {
        return entries.get(id);
    }

    public Entry lookup(String identity, String name, String providerId) {
        if (!enabled()) return absent(identity, name, diagnostics);
        String explicit = exact.get(identity);
        if (explicit != null) return entries.get(explicit);
        if (providerId != null && entries.containsKey(providerId)) return entries.get(providerId);
        Selection selection = select(Set.of(name), names, aliases, prefixes);
        if (selection.ambiguous) return absent(identity, name, List.of("AMBIGUOUS_BINDING:" + name));
        if (selection.material != null) return entries.get(selection.material);
        if (selection.structure != null) return visual(identity, name, selection.structure, scenes);
        return absent(identity, name, List.of());
    }

    public Entry provider(String id) {
        return entries.getOrDefault(id, absent(id, id, List.of("UNAVAILABLE_PROVIDER:" + id)));
    }

    private static Entry absent(String id, String name, List<String> why) {
        return new Entry(
            new MaterialDefinition(
                id,
                name,
                "",
                "",
                null,
                0xFFFFFFFF,
                State.ABSENT,
                Basis.MIXTURE_PARTS,
                List.of(),
                "external packs",
                why),
            DefinitionResult.absent(),
            false);
    }

    private static Entry visual(String id, String name, String structure, Map<String, MoleculeScene> scenes) {
        return new Entry(
            new MaterialDefinition(
                id,
                name,
                "",
                structure,
                null,
                0xFFFFFFFF,
                State.ABSENT,
                Basis.MIXTURE_PARTS,
                List.of(),
                structure,
                List.of()),
            new DefinitionResult<>(State.PRESENT, scenes.get(structure), structure, List.of()),
            false);
    }

    private static String nameKey(String s) {
        return s.replaceAll("(?i)§[0-9a-fk-or]", "")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private static Set<String> variants(String name) {
        String clean = name.replaceAll("(?i)§[0-9a-fk-or]", "")
            .trim(), normalized = PackCompiler.normalize(clean);
        return new HashSet<>(List.of(nameKey(clean), normalized, normalized.replace("_", "")));
    }

    private static Selection direct(Set<String> queries, Map<String, String> names, Map<String, String> aliases) {
        Set<String> targets = new TreeSet<>();
        for (String query : queries) if (names.containsKey(query)) targets.add(names.get(query));
        if (targets.size() > 1) return new Selection(null, null, true);
        if (targets.size() == 1) return new Selection(
            targets.iterator()
                .next(),
            null,
            false);
        for (String query : queries) {
            String target = aliases.get(PackCompiler.normalize(query));
            if (target != null) targets.add(target);
        }
        return targets.size() > 1 ? new Selection(null, null, true)
            : targets.size() == 1 ? new Selection(
                null,
                targets.iterator()
                    .next(),
                false) : Selection.none();
    }

    private static Selection select(Set<String> raw, Map<String, String> names, Map<String, String> aliases,
        List<String> prefixes) {
        Set<String> queries = new HashSet<>();
        for (String s : raw) queries.addAll(variants(s));
        Selection direct = direct(queries, names, aliases);
        if (direct.ambiguous || direct.material != null || direct.structure != null) return direct;
        Set<String> stripped = new HashSet<>();
        for (String q : queries) for (String p : prefixes) if (q.startsWith(p)) stripped.add(q.substring(p.length()));
        return direct(stripped, names, aliases);
    }

    public static RuntimeSnapshot bind(PackCatalog catalog, Input input) {
        if (catalog.packCount() == 0) return empty();
        boolean providerEnabled = catalog.providers()
            .contains("gregtech");
        Map<String, ProviderMaterial> provider = providerEnabled ? input.materials : Map.of();
        Map<String, String> exact = new HashMap<>(), names = new HashMap<>(), aliases = new HashMap<>(),
            elements = new HashMap<>();
        Set<String> candidateIds = new HashSet<>(), candidateNames = new HashSet<>();
        List<String> prefixes = new ArrayList<>(), contains = new ArrayList<>(), diagnostics = new ArrayList<>();
        Map<String, JsonObject> materials = new TreeMap<>();
        Map<String, PackCatalog.Definition> sources = new HashMap<>();
        for (var d : catalog.definitions()
            .values()) {
            JsonObject json = d.json();
            if (d.type()
                .equals("material")) {
                materials.put(d.id(), json);
                sources.put(d.id(), d);
                for (var b : json.getAsJsonArray("bindings")) {
                    String binding = b.getAsString();
                    if (binding.startsWith("name:")) names.put(nameKey(binding.substring(5)), d.id());
                    else exact.put(binding, d.id());
                    if (binding.startsWith("gt:") && !provider.containsKey(binding))
                        diagnostics.add("UNRESOLVED_GT_BINDING:" + d.id() + ":" + binding);
                }
            }
            for (var rule : PackCompiler.array(json, "rules")) {
                var r = rule.getAsJsonObject();
                switch (r.get("kind")
                    .getAsString()) {
                    case "alias" -> aliases.put(
                        PackCompiler.normalize(
                            r.get("from")
                                .getAsString()),
                        r.get("to")
                            .getAsString());
                    case "element_binding" -> {
                        String id = r.get("material")
                            .getAsString();
                        elements.put(
                            r.get("symbol")
                                .getAsString(),
                            id);
                        if (id.startsWith("gt:") && !provider.containsKey(id)) diagnostics.add(
                            "UNRESOLVED_ELEMENT:" + r.get("symbol")
                                .getAsString() + ":" + id);
                    }
                    case "strip_prefix" -> {
                        if (r.get("target")
                            .getAsString()
                            .equals("structure"))
                            for (var p : r.getAsJsonArray("prefixes")) prefixes.add(p.getAsString());
                    }
                    case "composition_candidate" -> {
                        for (var id : PackCompiler.array(r, "materials")) candidateIds.add(id.getAsString());
                        for (var n : PackCompiler.array(r, "names"))
                            candidateNames.add(PackCompiler.normalize(n.getAsString()));
                        for (var n : PackCompiler.array(r, "containsAny"))
                            contains.add(PackCompiler.normalize(n.getAsString()));
                    }
                    default -> throw d.error("/rules", "Unsupported runtime rule");
                }
            }
        }
        Map<String, Selection> choices = new TreeMap<>();
        Map<String, Set<String>> linked = new HashMap<>();
        Set<String> active = new HashSet<>();
        for (var gt : provider.values()) {
            Selection choice = exact.containsKey(gt.id) ? new Selection(exact.get(gt.id), null, false)
                : select(gt.names, names, aliases, prefixes);
            choices.put(gt.id, choice);
            if (choice.material != null) {
                linked.computeIfAbsent(choice.material, k -> new HashSet<>())
                    .add(gt.id);
                active.add(choice.material);
            }
        }
        for (var material : materials.entrySet()) {
            boolean hasGT = false;
            for (var b : material.getValue()
                .getAsJsonArray("bindings"))
                if (b.getAsString()
                    .startsWith("gt:")) hasGT = true;
            for (var b : material.getValue()
                .getAsJsonArray("bindings")) {
                String key = b.getAsString();
                if (key.startsWith("name:") && !hasGT
                    || key.startsWith("fluid:") && input.fluids.contains(key.substring(6)))
                    active.add(material.getKey());
                if (key.startsWith("gt:") && provider.containsKey(key)) {
                    linked.computeIfAbsent(material.getKey(), k -> new HashSet<>())
                        .add(key);
                    active.add(material.getKey());
                }
            }
        }
        // Package dependencies used by an active material must also pass live GT-reference validation.
        ArrayDeque<String> pending = new ArrayDeque<>(active);
        while (!pending.isEmpty()) {
            String id = pending.remove();
            var c = materials.get(id)
                .getAsJsonObject("composition");
            if (c != null && c.get("source")
                .getAsString()
                .equals("explicit")) for (var part : c.getAsJsonArray("components")) {
                    String target = part.getAsJsonObject()
                        .get("material")
                        .getAsString();
                    if (target.startsWith("gt:")) {
                        if (!provider.containsKey(target)) throw sources.get(id)
                            .error("/composition", "Missing required GT component " + target);
                    } else if (active.add(target)) pending.add(target);
                }
        }
        Map<String, Draft> drafts = new TreeMap<>();
        for (var p : provider.values()) {
            Selection selection = choices.get(p.id);
            JsonObject override = selection.material == null ? null : materials.get(selection.material);
            List<String> errors = new ArrayList<>();
            if (selection.ambiguous) errors.add("AMBIGUOUS_BINDING:" + p.id);
            Draft draft = draft(
                p.id,
                p,
                override,
                selection.structure,
                elements,
                provider,
                sources.get(selection.material),
                candidate(p, candidateIds, candidateNames, contains),
                errors);
            drafts.put(p.id, draft);
        }
        for (var pair : materials.entrySet()) {
            Set<String> targets = linked.getOrDefault(pair.getKey(), Set.of());
            ProviderMaterial context = targets.size() == 1 ? provider.get(
                targets.iterator()
                    .next())
                : null;
            JsonObject c = pair.getValue()
                .getAsJsonObject("composition");
            boolean needsGT = c != null && c.get("source")
                .getAsString()
                .equals("gregtech");
            if (needsGT && context == null && active.contains(pair.getKey())) throw sources.get(pair.getKey())
                .error("/composition", "GT derivation is not uniquely bound in the current registry");
            List<String> errors = new ArrayList<>();
            if (targets.size() > 1) errors.add("AMBIGUOUS_GT_CONTEXT:" + pair.getKey());
            drafts.put(
                pair.getKey(),
                draft(
                    pair.getKey(),
                    context,
                    pair.getValue(),
                    null,
                    elements,
                    provider,
                    sources.get(pair.getKey()),
                    false,
                    errors));
        }
        // Unknown symbols stay separate leaves. Never manufacture an elemental mass.
        Set<String> unknown = new HashSet<>();
        for (var d : drafts.values()) for (var c : d.parts) if (c.id()
            .startsWith("symbol:")) unknown.add(c.id());
        for (String symbol : unknown) drafts.put(
            symbol,
            new Draft(
                symbol,
                symbol.substring(7),
                symbol.substring(7),
                "",
                0xFFBB66FF,
                State.ABSENT,
                Basis.STOICHIOMETRIC,
                List.of(),
                "formula",
                List.of("UNKNOWN_SYMBOL:" + symbol.substring(7)),
                null,
                "text",
                false));
        Map<String, MassResolver.Result> masses = new HashMap<>();
        MassResolver mass = new MassResolver(id -> {
            Draft d = drafts.get(id);
            return d == null ? null : d.massNode;
        });
        for (String id : drafts.keySet()) {
            var resolved = mass.resolve(id);
            if (resolved.mass() != null) {
                PackCompiler.numericBudget(
                    resolved.mass()
                        .numerator());
                PackCompiler.numericBudget(
                    resolved.mass()
                        .denominator());
            }
            masses.put(id, resolved);
        }
        Map<String, MoleculeScene> scenes = new HashMap<>();
        for (var graph : catalog.molecules()
            .entrySet())
            scenes.put(
                graph.getKey(),
                graph.getValue()
                    .scene(symbol -> {
                        String target = elements.get(symbol);
                        int fallback = input.colors.getOrDefault("symbol:" + symbol, input.defaultColor);
                        return target != null && provider.containsKey(target)
                            ? input.colors.getOrDefault(target, fallback)
                            : fallback;
                    }, input.optionalColors));
        Map<String, Entry> result = new HashMap<>();
        for (var d : drafts.values()) {
            List<String> errors = new ArrayList<>(d.diagnostics);
            var resolvedMass = masses.get(d.id);
            errors.addAll(resolvedMass.diagnostics());
            MaterialDefinition definition = new MaterialDefinition(
                d.id,
                d.name,
                d.formula,
                d.structure,
                resolvedMass.mass(),
                d.color,
                d.state,
                d.basis,
                d.parts,
                d.source,
                errors);
            boolean suppressed = d.state == State.DISABLED || d.mode.equals("text");
            MoleculeScene geometry = suppressed || d.mode.equals("composition") ? null : scenes.get(d.structure);
            var molecule = new DefinitionResult<MoleculeScene>(
                d.diagnostics.stream()
                    .anyMatch(s -> s.startsWith("AMBIGUOUS_BINDING:")) ? State.INVALID
                        : suppressed ? State.DISABLED : geometry != null ? State.PRESENT : State.ABSENT,
                geometry,
                d.source,
                d.diagnostics);
            result.put(
                d.id,
                new Entry(
                    definition,
                    molecule,
                    !suppressed && geometry == null
                        && !d.mode.equals("molecule")
                        && (d.candidate || d.mode.equals("composition") || d.source.startsWith("pack:"))));
        }
        return new RuntimeSnapshot(
            catalog,
            input,
            result,
            exact,
            names,
            aliases,
            elements,
            candidateIds,
            candidateNames,
            prefixes,
            contains,
            scenes,
            diagnostics);
    }

    private static boolean candidate(ProviderMaterial p, Set<String> ids, Set<String> names, List<String> contains) {
        if (p.elemental) return false;
        if (ids.contains(p.id)) return true;
        for (String raw : p.names) {
            String name = PackCompiler.normalize(raw);
            if (names.contains(name) || contains.stream()
                .anyMatch(name::contains)) return true;
        }
        return false;
    }

    private static Draft draft(String id, ProviderMaterial gt, JsonObject override, String alias,
        Map<String, String> elements, Map<String, ProviderMaterial> provider, PackCatalog.Definition source,
        boolean candidate, List<String> errors) {
        String name = gt == null ? id : gt.name, formula = gt == null ? "" : gt.formula,
            structure = alias == null ? "" : alias, origin = "GT registry", mode = "auto";
        int color = gt == null ? 0xFFFFFFFF : gt.color;
        State state = gt != null && !gt.diagnostics.isEmpty() ? State.INVALID : State.PRESENT;
        if (gt != null) errors.addAll(gt.diagnostics);
        Basis basis = Basis.MIXTURE_PARTS;
        List<Component> parts = new ArrayList<>();
        JsonObject composition = null;
        if (override != null) {
            if (gt == null) for (var binding : override.getAsJsonArray("bindings")) {
                String b = binding.getAsString();
                if (b.startsWith("name:")) {
                    name = b.substring(5);
                    break;
                }
            }
            origin = "pack:" + source.owner() + ":" + source.id();
            if (override.has("disabled") && override.get("disabled")
                .getAsBoolean())
                return new Draft(
                    id,
                    name,
                    "",
                    "",
                    color,
                    State.DISABLED,
                    basis,
                    List.of(),
                    origin,
                    errors,
                    null,
                    "text",
                    false);
            if (override.has("formula")) {
                errors.add("PACK_FORMULA_OVERRIDE");
                errors.add("GT_ORIGINAL_FORMULA:" + formula);
                formula = override.get("formula")
                    .getAsString();
            }
            if (override.has("structure")) structure = override.get("structure")
                .getAsString();
            if (override.has("display")) mode = override.getAsJsonObject("display")
                .get("mode")
                .getAsString();
            composition = override.getAsJsonObject("composition");
        }
        if (errors.stream()
            .anyMatch(s -> s.startsWith("AMBIGUOUS_BINDING:"))) {
            mode = "text";
            state = State.INVALID;
        }
        String sourceType = composition == null ? "gregtech"
            : composition.get("source")
                .getAsString();
        if (override != null && override.has("terminal")
            && override.get("terminal")
                .getAsBoolean()) {
            errors.add("TERMINAL_SPECIES:" + id);
        } else if (sourceType.equals("explicit")) {
            basis = composition.get("basis")
                .getAsString()
                .equals("stoichiometric") ? Basis.STOICHIOMETRIC : Basis.MIXTURE_PARTS;
            List<Fraction> amounts = new ArrayList<>();
            BigInteger denominator = BigInteger.ONE;
            for (var value : composition.getAsJsonArray("components")) {
                Fraction f = PackCompiler.amount(
                    value.getAsJsonObject()
                        .get("amount"));
                amounts.add(f);
                denominator = denominator.divide(denominator.gcd(f.denominator()))
                    .multiply(f.denominator());
                PackCompiler.numericBudget(denominator);
            }
            int i = 0;
            for (var value : composition.getAsJsonArray("components")) {
                Fraction f = amounts.get(i++);
                BigInteger count = f.numerator()
                    .multiply(denominator.divide(f.denominator()));
                PackCompiler.numericBudget(count);
                parts.add(
                    new Component(
                        value.getAsJsonObject()
                            .get("material")
                            .getAsString(),
                        count));
            }
        } else if (sourceType.equals("formula")
            || gt != null && !gt.elemental && gt.components.isEmpty() && !formula.isEmpty()) {
                basis = Basis.STOICHIOMETRIC;
                try {
                    for (var atom : ChemicalFormula.parseExact(formula, elements.keySet())
                        .entrySet()) {
                        String target = elements.get(atom.getKey());
                        parts.add(
                            new Component(
                                target != null && (!target.startsWith("gt:") || provider.containsKey(target)) ? target
                                    : "symbol:" + atom.getKey(),
                                atom.getValue()));
                    }
                } catch (IllegalArgumentException failure) {
                    state = State.INVALID;
                    errors.add("UNSUPPORTED_FORMULA:" + id + ":" + failure.getMessage());
                }
            } else if (gt != null) parts.addAll(gt.components);
        else {
            state = State.ABSENT;
            errors.add("NO_GT_CONTEXT:" + id);
        }
        if (state == State.INVALID) parts.clear();
        MassResolver.Node massNode;
        if (composition == null && gt != null && gt.elementMass != null)
            massNode = new MassResolver.Node(gt.elementMass, Fraction.ONE, List.of(), false);
        else if (parts.isEmpty()) massNode = null;
        else massNode = new MassResolver.Node(
            null,
            basis == Basis.MIXTURE_PARTS && gt != null ? gt.density : Fraction.ONE,
            parts,
            basis == Basis.MIXTURE_PARTS);
        return new Draft(
            id,
            name,
            formula,
            structure,
            color,
            state,
            basis,
            List.copyOf(parts),
            origin,
            List.copyOf(errors),
            massNode,
            mode,
            candidate);
    }
}
