package com.rubenverg.moldraw;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rubenverg.moldraw.molecule.*;

import gregtech.api.enums.Materials;

public final class MoleculeManager {

    private static final Map<Materials, Molecule> MOLECULE_CACHE = new HashMap<>();

    public static final Gson GSON = new GsonBuilder().registerTypeAdapter(Molecule.class, Molecule.Json.INSTANCE)
        .registerTypeAdapter(Element.class, Element.Json.INSTANCE)
        .registerTypeAdapter(Element.Color.class, Element.Color.Json.INSTANCE)
        .registerTypeAdapter(Element.Counted.class, Element.Counted.Json.INSTANCE)
        .registerTypeAdapter(Atom.class, Atom.Json.INSTANCE)
        .registerTypeAdapter(Bond.class, Bond.Json.INSTANCE)
        .registerTypeAdapter(Bond.Line.class, Bond.Line.Json.INSTANCE)
        .registerTypeAdapter(Parens.class, Parens.Json.INSTANCE)
        .registerTypeAdapter(CircleTransformation.class, CircleTransformation.Json.INSTANCE)
        .create();

    private static final String[] NAMESPACES = new String[] { "gtceu", "gcyr", "gtca", "gtocore", "cosmiccore", "tfg",
        "moldraw" };

    private static final Map<String, String> ALIASES = new HashMap<>();
    private static final Map<String, String> FORMULA_MAP = new HashMap<>();

    private static final String[] PREFIXES_TO_STRIP = new String[] { "raw_", "impure_", "purified_", "centrifuged_",
        "liquid_", "gas_", "solid_", "molten_", "crushed_", "packed_" };

    static {
        ALIASES.put("ice", "water");
        ALIASES.put("heavy_ice", "heavy_water");
        ALIASES.put("crushed_ice", "water");
        ALIASES.put("dry_ice", "carbon_dioxide");
        ALIASES.put("pbi", "polybenzimidazole");
        ALIASES.put("sbr", "styrene_butadiene_rubber");
        ALIASES.put("raw_sbr", "raw_styrene_butadiene_rubber");
        ALIASES.put("rubber_raw", "raw_rubber");
        ALIASES.put("rubber_silicone", "silicone_rubber");
        ALIASES.put("ptfe", "polytetrafluoroethylene");
        ALIASES.put("pvc", "polyvinyl_chloride");
        ALIASES.put("peek", "polyether_ether_ketone");
        ALIASES.put("pmma", "polymethyl_methacrylate");
        ALIASES.put("pps", "polyphenylene_sulfide");
        ALIASES.put("pvb", "polyvinyl_butyral");
        ALIASES.put("pva", "polyvinyl_acetate");
        ALIASES.put("silane", "silane");
        ALIASES.put("disilane", "disilane");
        ALIASES.put("silicon_tetrachloride", "silicon_tetrachloride");
        ALIASES.put("silicon_tetrafluoride", "silicon_tetrafluoride");
        ALIASES.put("trichlorosilane", "trichlorosilane");
        ALIASES.put("aluminiumoxide", "aluminiumoxide");
        ALIASES.put("aluminium_oxide", "aluminiumoxide");
        ALIASES.put("aluminum_oxide", "aluminiumoxide");
        ALIASES.put("alumina", "aluminiumoxide");
        ALIASES.put("cryolite", "cryolite");
        ALIASES.put("silicon_dioxide", "silicon_dioxide");
        ALIASES.put("silicondioxide", "silicon_dioxide");
        ALIASES.put("silica", "silicon_dioxide");
        ALIASES.put("quartz", "silicon_dioxide");
        ALIASES.put("quartzite", "silicon_dioxide");
        ALIASES.put("quartz_sand", "silicon_dioxide");
        ALIASES.put("oxygen", "oxygen");
        ALIASES.put("hydrogen", "hydrogen");
        ALIASES.put("nitrogen", "nitrogen");
        ALIASES.put("chlorine", "chlorine");
        ALIASES.put("fluorine", "fluorine");
        ALIASES.put("carbon_dioxide", "carbon_dioxide");
        ALIASES.put("carbon_monoxide", "carbon_monoxide");
        ALIASES.put("ammonia", "ammonia");
        ALIASES.put("water", "water");
        ALIASES.put("hydrogen_sulfide", "hydrogen_sulfide");
        ALIASES.put("sulfur_dioxide", "sulfur_dioxide");
        ALIASES.put("sulfur_trioxide", "sulfur_trioxide");
        ALIASES.put("hydrogen_chloride", "hydrogen_chloride");
        ALIASES.put("hydrogen_fluoride", "hydrogen_fluoride");
        ALIASES.put("hydrochloric_acid", "hydrogen_chloride");
        ALIASES.put("hydrofluoric_acid", "hydrogen_fluoride");
        ALIASES.put("nitric_oxide", "nitric_oxide");
        ALIASES.put("nitrogen_dioxide", "nitrogen_dioxide");

        // 化学式反查分子结构
        FORMULA_MAP.put("h2o", "water");
        FORMULA_MAP.put("ch4", "methane");
        FORMULA_MAP.put("co2", "carbon_dioxide");
        FORMULA_MAP.put("co", "carbon_monoxide");
        FORMULA_MAP.put("nh3", "ammonia");
        FORMULA_MAP.put("o2", "oxygen");
        FORMULA_MAP.put("n2", "nitrogen");
        FORMULA_MAP.put("h2", "hydrogen");
        FORMULA_MAP.put("cl2", "chlorine");
        FORMULA_MAP.put("f2", "fluorine");
        FORMULA_MAP.put("h2s", "hydrogen_sulfide");
        FORMULA_MAP.put("so2", "sulfur_dioxide");
        FORMULA_MAP.put("so3", "sulfur_trioxide");
        FORMULA_MAP.put("hcl", "hydrogen_chloride");
        FORMULA_MAP.put("hf", "hydrogen_fluoride");
        FORMULA_MAP.put("no", "nitric_oxide");
        FORMULA_MAP.put("no2", "nitrogen_dioxide");
        FORMULA_MAP.put("al2o3", "aluminiumoxide");
        FORMULA_MAP.put("na3alf6", "cryolite");
        FORMULA_MAP.put("sio2", "silicon_dioxide");
        FORMULA_MAP.put("c2h6osi", "polydimethylsiloxane");
        FORMULA_MAP.put("c20n4h12", "polybenzimidazole");
        FORMULA_MAP.put("c8h8c4h63", "styrene_butadiene_rubber");
        FORMULA_MAP.put("c2h4", "ethylene");
        FORMULA_MAP.put("c2h6", "ethane");
        FORMULA_MAP.put("c3h8", "propane");
        FORMULA_MAP.put("c4h10", "butane");
        FORMULA_MAP.put("c6h6", "benzene");
        FORMULA_MAP.put("c7h8", "toluene");
        FORMULA_MAP.put("c3h6o", "acetone");
        FORMULA_MAP.put("c2h4o2", "acetic_acid");
        FORMULA_MAP.put("c2h4o", "ethylene_oxide");
        FORMULA_MAP.put("c2h6o", "ethanol");
        FORMULA_MAP.put("c2h5oh", "ethanol");
        FORMULA_MAP.put("ch4o", "methanol");
        FORMULA_MAP.put("ch3oh", "methanol");
        FORMULA_MAP.put("c6h6o", "phenol");
        FORMULA_MAP.put("c6h7n", "aniline");
        FORMULA_MAP.put("c4h6", "butadiene");
        FORMULA_MAP.put("c5h8", "isoprene");
        FORMULA_MAP.put("c2h4cl2", "dichloroethane");
        FORMULA_MAP.put("c2h3cl", "vinyl_chloride");
        FORMULA_MAP.put("c2f4", "tetrafluoroethylene");
        FORMULA_MAP.put("sih4", "silane");
        FORMULA_MAP.put("si2h6", "disilane");
        FORMULA_MAP.put("sicl4", "silicon_tetrachloride");
        FORMULA_MAP.put("sif4", "silicon_tetrafluoride");
        FORMULA_MAP.put("sihcl3", "trichlorosilane");
    }

    public static Molecule getMolecule(Materials material) {
        if (material == null || material == Materials._NULL) return null;
        if (MOLECULE_CACHE.containsKey(material)) {
            return MOLECULE_CACHE.get(material);
        }

        // 尝试从枚举名称与 mName 提取多种候选名称
        List<String> candidates = new ArrayList<>();

        if (material.mName != null && !material.mName.isEmpty()) {
            candidates.add(toSnakeCase(material.mName));
            candidates.add(
                material.mName.toLowerCase(Locale.ROOT)
                    .replace(" ", "_")
                    .replace("-", "_"));
        }
        candidates.add(toSnakeCase(material.name()));
        candidates.add(
            material.name()
                .toLowerCase(Locale.ROOT));

        Molecule loaded = null;

        for (String rawName : candidates) {
            String name = ALIASES.getOrDefault(rawName, rawName);

            // 1. 直接匹配
            loaded = loadMoleculeFromJson(name);
            if (loaded != null) break;

            // 2. 剥离常见前缀后匹配
            for (String prefix : PREFIXES_TO_STRIP) {
                if (name.startsWith(prefix)) {
                    String stripped = name.substring(prefix.length());
                    loaded = loadMoleculeFromJson(stripped);
                    if (loaded != null) break;
                }
            }
            if (loaded != null) break;

            // 3. 去除下划线后匹配
            if (name.contains("_")) {
                loaded = loadMoleculeFromJson(name.replace("_", ""));
                if (loaded != null) break;
            }
        }

        // 4. 化学式精确查找
        if (loaded == null) {
            String formula = material.getChemicalFormula();
            if (formula != null && !formula.isEmpty()) {
                String cleanFormula = formula.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]", "");
                if (FORMULA_MAP.containsKey(cleanFormula)) {
                    loaded = loadMoleculeFromJson(FORMULA_MAP.get(cleanFormula));
                }
            }
        }

        MOLECULE_CACHE.put(material, loaded);
        return loaded;
    }

    private static String toSnakeCase(String str) {
        if (str == null) return "";
        return str.replaceAll("(?<!^)(?=[A-Z])", "_")
            .replace(" ", "_")
            .replace("-", "_")
            .toLowerCase(Locale.ROOT)
            .replaceAll("_+", "_");
    }

    private static Molecule loadMoleculeFromJson(String name) {
        for (String ns : NAMESPACES) {
            String path = "/assets/" + ns + "/molecules/" + name + ".json";
            try (InputStream is = MoleculeManager.class.getResourceAsStream(path)) {
                if (is != null) {
                    return GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), Molecule.class);
                }
            } catch (Exception e) {
                if (MolDrawConfig.INSTANCE.debugMode) {
                    MolDraw.LOGGER.warn("Failed to load molecule " + path, e);
                }
            }
        }
        return null;
    }

    public static Molecule getMoleculeByFormula(String formula) {
        if (formula == null || formula.isEmpty()) return null;
        String clean = formula.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
        String mapped = FORMULA_MAP.get(clean);
        if (mapped != null) {
            return loadMoleculeFromJson(mapped);
        }
        return null;
    }

    public static Molecule getMoleculeByName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return null;
        String name = ALIASES.getOrDefault(toSnakeCase(rawName), toSnakeCase(rawName));
        Molecule loaded = loadMoleculeFromJson(name);
        if (loaded != null) return loaded;
        for (String prefix : PREFIXES_TO_STRIP) {
            if (name.startsWith(prefix)) {
                String stripped = name.substring(prefix.length());
                loaded = loadMoleculeFromJson(stripped);
                if (loaded != null) return loaded;
            }
        }
        return null;
    }

    public static void clearCache() {
        MOLECULE_CACHE.clear();
    }
}
