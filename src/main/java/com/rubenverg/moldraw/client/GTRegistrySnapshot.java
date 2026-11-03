package com.rubenverg.moldraw.client;

import java.math.BigInteger;
import java.util.*;

import net.minecraftforge.fluids.FluidRegistry;

import com.rubenverg.moldraw.*;
import com.rubenverg.moldraw.core.*;
import com.rubenverg.moldraw.core.MaterialDefinition.Component;
import com.rubenverg.moldraw.core.pack.RuntimeSnapshot;

import gregtech.api.enums.Materials;

/** Client-thread boundary: only immutable IDs, numbers and strings leave this adapter. */
final class GTRegistrySnapshot {

    private GTRegistrySnapshot() {}

    static RuntimeSnapshot.Input capture() {
        if (!MolDraw.isClientThread()) throw new IllegalStateException("GT capture requires client thread");
        Map<String, RuntimeSnapshot.ProviderMaterial> data = new HashMap<>();
        Map<String, Integer> colors = new HashMap<>();
        ArrayDeque<Materials> pending = new ArrayDeque<>();
        for (Materials material : Materials.getAll()) if (material != null) pending.add(material);
        Set<Materials> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!pending.isEmpty()) {
            Materials material = pending.remove();
            if (!visited.add(material)) continue;
            if (visited.size() > 65536)
                throw new IllegalArgumentException("GT registry capture exceeds 65536 materials");
            if (!GT5MaterialLookup.isMaterialValid(material) || material.mName == null) continue;
            String id = "gt:" + material.mName;
            List<Component> parts = new ArrayList<>();
            List<String> diagnostics = new ArrayList<>();
            if (material.mMaterialList != null) for (var part : material.mMaterialList) {
                if (part == null || !GT5MaterialLookup.isMaterialValid(part.mMaterial) || part.mAmount <= 0) {
                    diagnostics.add("INVALID_GT_COMPONENT:" + id);
                    continue;
                }
                parts.add(new Component("gt:" + part.mMaterial.mName, BigInteger.valueOf(part.mAmount)));
                pending.add(part.mMaterial);
            }
            if (!diagnostics.isEmpty()) parts.clear();
            boolean elemental = material.mElement != null;
            long mass = elemental ? material.mElement.getMass() : 0;
            int color = MoleculeColorize.getColorForMaterial(material);
            data.put(
                id,
                new RuntimeSnapshot.ProviderMaterial(
                    id,
                    Objects.requireNonNullElse(material.mDefaultLocalName, material.mName),
                    Objects.requireNonNullElse(material.getChemicalFormula(), ""),
                    new HashSet<>(List.of(material.mName, material.name())),
                    color,
                    mass > 0 ? Fraction.of(mass) : null,
                    new Fraction(BigInteger.valueOf(Math.max(0, material.getDensity())), BigInteger.valueOf(3_628_800)),
                    parts,
                    elemental,
                    diagnostics));
            colors.put(
                id,
                MolDrawConfig.INSTANCE.color.colors && MolDrawConfig.INSTANCE.color.useMaterialColors ? color
                    : MoleculeColorize.configColor(null));
        }
        // The generic element palette is a drawing module, not a table of material-specific structures.
        for (var color : MoleculeColorize.symbolPalette()
            .entrySet()) colors.put("symbol:" + color.getKey(), color.getValue());
        if (MolDrawConfig.INSTANCE.color.colors && !MolDrawConfig.INSTANCE.color.useMaterialColors) {
            // Element identity is still external; runtime binding selects its symbol palette below.
            colors.keySet()
                .removeIf(key -> key.startsWith("gt:"));
        }
        return new RuntimeSnapshot.Input(
            data,
            FluidRegistry.getRegisteredFluids()
                .keySet(),
            colors,
            MoleculeColorize.configColor(null),
            MolDrawConfig.INSTANCE.color.colors && !MolDrawConfig.INSTANCE.color.useMaterialColors);
    }
}
