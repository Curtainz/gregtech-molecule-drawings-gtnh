package com.rubenverg.moldraw;

import java.util.Locale;

import gregtech.api.enums.Materials;

/** Shared, locale-independent material names; never use chemical formula as an identity. */
public final class MaterialNames {

    private MaterialNames() {}

    public static String normalize(String name) {
        if (name == null) return "";
        return name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("[ .-]+", "_")
            .toLowerCase(Locale.ROOT);
    }

    public static Materials resolve(String name) {
        String local = name.substring(name.indexOf(':') + 1);
        Materials direct = Materials.get(local);
        if (GT5MaterialLookup.isMaterialValid(direct)) return direct;
        String key = normalize(local).replace("_", "");
        Materials result = null;
        for (Materials material : Materials.getAll()) {
            if (normalize(material.mName).replace("_", "")
                .equals(key)) {
                if (result != null && result != material) return null;
                result = material;
            }
        }
        return result;
    }
}
