package com.rubenverg.moldraw.client;

import java.util.*;

import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.*;

import com.rubenverg.moldraw.*;

import gregtech.api.enums.Materials;

/** Client-owned, epoch-scoped indexes; translated-name collisions never pick the first material. */
public final class TooltipIdentityIndex {

    private static long epoch = Long.MIN_VALUE;
    private static Map<String, Set<Fluid>> fluids = Map.of();
    private static Map<String, Set<Materials>> materials = Map.of();

    private TooltipIdentityIndex() {}

    private static String key(String name) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(name)
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private static <T> void add(Map<String, Set<T>> index, String name, T value) {
        if (name != null && !name.isBlank()) index.computeIfAbsent(key(name), k -> new HashSet<>())
            .add(value);
    }

    private static void refresh() {
        if (!MolDraw.isClientThread()) throw new IllegalStateException("Tooltip identity index requires client thread");
        if (epoch == MaterialRegistry.version()) return;
        Map<String, Set<Fluid>> f = new HashMap<>();
        Map<String, Set<Materials>> m = new HashMap<>();
        for (var fluid : FluidRegistry.getRegisteredFluids()
            .values()) if (fluid != null) {
                add(f, fluid.getName(), fluid);
                try {
                    add(f, fluid.getLocalizedName(new FluidStack(fluid, 1000)), fluid);
                } catch (RuntimeException ignored) {}
            }
        for (var material : Materials.getAll()) if (GT5MaterialLookup.isMaterialValid(material)) {
            add(m, material.mName, material);
            add(m, material.name(), material);
            add(m, material.mDefaultLocalName, material);
            try {
                add(m, material.getLocalizedNameForItem("%material"), material);
            } catch (RuntimeException ignored) {}
        }
        fluids = f;
        materials = m;
        epoch = MaterialRegistry.version();
    }

    public static Set<Fluid> fluids(String name) {
        refresh();
        return Set.copyOf(fluids.getOrDefault(key(name), Set.of()));
    }

    public static Set<Materials> materials(String name) {
        refresh();
        return Set.copyOf(materials.getOrDefault(key(name), Set.of()));
    }
}
