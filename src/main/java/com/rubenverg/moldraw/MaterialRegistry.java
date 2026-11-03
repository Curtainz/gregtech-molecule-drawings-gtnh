package com.rubenverg.moldraw;

import java.math.BigInteger;
import java.util.*;

import com.rubenverg.moldraw.core.*;
import com.rubenverg.moldraw.core.MaterialDefinition.*;
import com.rubenverg.moldraw.util.FormulaParser;

import gregtech.api.enums.Materials;

/** GT/resource adapters converge here. Consumers see immutable definitions, never GT mutable lists. */
public final class MaterialRegistry {

    public record Entry(MaterialDefinition definition, DefinitionResult<MoleculeScene> molecule,
        boolean chartEligible) {}

    private static final BoundedCache<String, Entry> ENTRIES = new BoundedCache<>(4096);
    private static final BoundedCache<String, CompositionResolver.Result> COMPOSITIONS = new BoundedCache<>(1024);
    private static final BoundedCache<String, MassResolver.Result> MASSES = new BoundedCache<>(4096);
    private static long version;

    private MaterialRegistry() {}

    public static long version() {
        return version;
    }

    public static void clear() {
        ENTRIES.clear();
        COMPOSITIONS.clear();
        MASSES.clear();
        version++;
    }

    public static String id(Materials material) {
        return "gt:" + material.mName;
    }

    public static Entry entry(Materials material) {
        if (!MolDraw.isClientThread()) throw new IllegalStateException("Registry adapter requires client thread");
        return ENTRIES.get(id(material), ignored -> adapt(material));
    }

    public static Entry named(String id, String name) {
        if (!MolDraw.isClientThread()) throw new IllegalStateException("Registry adapter requires client thread");
        return ENTRIES.get(id, ignored -> {
            var model = MoleculeManager.namedDefinition(name);
            var geometry = new DefinitionResult<MoleculeScene>(
                model.state(),
                model.value() == null ? null : MoleculeSnapshots.freeze(model.value()),
                model.source(),
                model.diagnostics());
            var d = new MaterialDefinition(
                id,
                name,
                "",
                model.source(),
                null,
                0xFFFFFFFF,
                State.ABSENT,
                Basis.MIXTURE_PARTS,
                List.of(),
                model.source(),
                List.of());
            return new Entry(d, geometry, false);
        });
    }

    public static MaterialDefinition definition(String id) {
        if (id.startsWith("symbol:")) return new MaterialDefinition(
            id,
            id.substring(7),
            id.substring(7),
            "",
            null,
            0xFFBB66FF,
            State.ABSENT,
            Basis.STOICHIOMETRIC,
            List.of(),
            "formula",
            List.of("UNKNOWN_SYMBOL:" + id.substring(7)));
        if (!id.startsWith("gt:")) return null;
        Materials material = Materials.get(id.substring(3));
        if (!GT5MaterialLookup.isMaterialValid(material) || !id(material).equals(id)) {
            material = Materials.getAll()
                .stream()
                .filter(m -> id(m).equals(id))
                .findFirst()
                .orElse(null);
        }
        return GT5MaterialLookup.isMaterialValid(material) ? entry(material).definition() : null;
    }

    public static CompositionResolver.Result composition(String id, boolean mass, boolean recursive) {
        return COMPOSITIONS.get(
            id + ":" + mass + ":" + recursive,
            ignored -> new CompositionResolver(MaterialRegistry::definition).resolve(id, mass, recursive));
    }

    private static Entry adapt(Materials material) {
        String id = id(material), formula = Objects.requireNonNullElse(material.getChemicalFormula(), "");
        var model = MoleculeManager.definition(material);
        DefinitionResult<MoleculeScene> geometry = new DefinitionResult<>(
            model.state(),
            model.value() == null ? null : MoleculeSnapshots.freeze(model.value()),
            model.source(),
            model.diagnostics());
        var override = AlloyManager.definition(material);
        List<String> diagnostics = new ArrayList<>();
        List<Component> components = new ArrayList<>();
        State state = State.PRESENT;
        Basis basis = Basis.MIXTURE_PARTS;
        String source = "GT registry";
        diagnostics.addAll(override.diagnostics());
        if (override.state() == State.DISABLED || override.state() == State.INVALID) state = override.state();
        else if (override.value() != null && !override.value()
            .isEmpty()) {
                source = override.source();
                components.addAll(override.value());
            } else if (material.mElement == null) {
                if (material.mMaterialList != null && !material.mMaterialList.isEmpty()) {
                    for (var c : material.mMaterialList) {
                        if (!GT5MaterialLookup.isMaterialValid(c.mMaterial) || c.mAmount <= 0) {
                            state = State.INVALID;
                            diagnostics.add("INVALID_GT_COMPONENT:" + id);
                            break;
                        }
                        components.add(new Component(id(c.mMaterial), BigInteger.valueOf(c.mAmount)));
                    }
                } else if (!formula.isEmpty()) {
                    basis = Basis.STOICHIOMETRIC;
                    source = "GT formula";
                    try {
                        for (var c : ChemicalFormula.parseExact(formula)
                            .entrySet()) {
                            Materials atom = FormulaParser.materialForSymbol(c.getKey());
                            components.add(
                                new Component(
                                    GT5MaterialLookup.isMaterialValid(atom) ? id(atom) : "symbol:" + c.getKey(),
                                    c.getValue()));
                        }
                    } catch (IllegalArgumentException invalid) {
                        state = State.INVALID;
                        diagnostics.add("UNSUPPORTED_FORMULA:" + id + ":" + invalid.getMessage());
                    }
                } else {
                    state = State.ABSENT;
                    diagnostics.add("NO_COMPOSITION:" + id);
                }
            }
        if (state == State.INVALID) components.clear();
        var massResult = MASSES.get(id, key -> new MassResolver(MaterialRegistry::massNode).resolve(key));
        diagnostics.addAll(massResult.diagnostics());
        var definition = new MaterialDefinition(
            id,
            Objects.requireNonNullElse(material.mDefaultLocalName, material.mName),
            formula,
            model.source(),
            massResult.mass(),
            MoleculeColorize.getColorForMaterial(material),
            state,
            basis,
            components,
            source,
            diagnostics);
        return new Entry(definition, geometry, AlloyManager.isAlloy(material) && state != State.DISABLED);
    }

    private static Materials rawMaterial(String id) {
        if (!id.startsWith("gt:")) return null;
        Materials m = Materials.get(id.substring(3));
        if (GT5MaterialLookup.isMaterialValid(m) && id(m).equals(id)) return m;
        return Materials.getAll()
            .stream()
            .filter(v -> id(v).equals(id))
            .findFirst()
            .orElse(null);
    }

    private static MassResolver.Node massNode(String id) {
        Materials material = rawMaterial(id);
        if (!GT5MaterialLookup.isMaterialValid(material)) return null;
        if (material.mElement != null) {
            long mass = material.mElement.getMass();
            return mass > 0 ? new MassResolver.Node(Fraction.of(mass), Fraction.ONE, List.of(), false) : null;
        }
        List<Component> components = new ArrayList<>();
        if (material.mMaterialList != null && !material.mMaterialList.isEmpty()) {
            if (material.getDensity() <= 0) return null;
            for (var c : material.mMaterialList) {
                if (!GT5MaterialLookup.isMaterialValid(c.mMaterial) || c.mAmount <= 0) return null;
                components.add(new Component(id(c.mMaterial), BigInteger.valueOf(c.mAmount)));
            }
            return new MassResolver.Node(
                null,
                new Fraction(BigInteger.valueOf(material.getDensity()), BigInteger.valueOf(3_628_800)),
                components,
                true);
        }
        try {
            for (var c : ChemicalFormula.parseExact(material.getChemicalFormula())
                .entrySet()) {
                Materials atom = FormulaParser.materialForSymbol(c.getKey());
                // A self-symbol on a material with no elemental metadata is not a known mass.
                if (!GT5MaterialLookup.isMaterialValid(atom) || atom == material) return null;
                components.add(new Component(id(atom), c.getValue()));
            }
            return new MassResolver.Node(null, Fraction.ONE, components, false);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
