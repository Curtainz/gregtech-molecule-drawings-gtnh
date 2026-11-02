package com.rubenverg.moldraw;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.gui.FontRenderer;

import com.rubenverg.moldraw.component.AlloyRenderer;
import com.rubenverg.moldraw.component.MoleculeRenderer;
import com.rubenverg.moldraw.molecule.Molecule;

import gregtech.api.enums.Materials;

public final class RendererCache {

    private static final Map<Materials, AlloyRenderer> ALLOY_CACHE = new ConcurrentHashMap<>();
    private static final Map<Molecule, MoleculeRenderer> MOLECULE_CACHE = new ConcurrentHashMap<>();

    private RendererCache() {}

    public static MoleculeRenderer getMoleculeRenderer(Molecule molecule) {
        if (molecule == null) return null;
        return MOLECULE_CACHE.computeIfAbsent(molecule, MoleculeRenderer::new);
    }

    public static AlloyRenderer getAlloyRenderer(Materials material, FontRenderer font) {
        if (material == null) return null;
        return ALLOY_CACHE.computeIfAbsent(material, mat -> new AlloyRenderer(mat, font));
    }

    public static void clear() {
        ALLOY_CACHE.clear();
        MOLECULE_CACHE.clear();
    }
}
