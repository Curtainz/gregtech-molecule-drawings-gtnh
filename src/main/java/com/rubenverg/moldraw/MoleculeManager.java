package com.rubenverg.moldraw;

import java.io.InputStream;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rubenverg.moldraw.core.DefinitionResult;
import com.rubenverg.moldraw.core.MaterialDefinition.State;
import com.rubenverg.moldraw.molecule.*;

import gregtech.api.enums.Materials;

public final class MoleculeManager {

    public static final Gson GSON = new GsonBuilder().registerTypeAdapter(Molecule.class, Molecule.Json.INSTANCE)
        .registerTypeAdapter(Element.class, Element.Json.INSTANCE)
        .registerTypeAdapter(Element.Color.class, Element.Color.Json.INSTANCE)
        .registerTypeAdapter(Element.Counted.class, Element.Counted.Json.INSTANCE)
        .registerTypeAdapter(Atom.class, Atom.Json.INSTANCE)
        .registerTypeAdapter(Bond.class, Bond.Json.INSTANCE)
        .registerTypeAdapter(Bond.Line.class, Bond.Line.Json.INSTANCE)
        .registerTypeAdapter(Parens.class, Parens.Json.INSTANCE)
        .registerTypeAdapter(CircleTransformation.class, CircleTransformation.Json.INSTANCE)
        .registerTypeAdapter(BenzeneRing.class, BenzeneRing.Json.INSTANCE)
        .create();

    private static final String[] NAMESPACES = new String[] { "gtceu", "gcyr", "gtca", "gtocore", "cosmiccore", "tfg",
        "gtmoldraw" };

    private static final Map<String, String> ALIASES = new HashMap<>();

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
        ALIASES.put("aluminiumhydroxide", "aluminium_hydroxide");
        ALIASES.put("aluminium_hydroxide", "aluminium_hydroxide");
        ALIASES.put("aluminum_hydroxide", "aluminium_hydroxide");
        ALIASES.put("sodium_hydroxide", "sodium_hydroxide");
        ALIASES.put("sodiumhydroxide", "sodium_hydroxide");
        ALIASES.put("potassium_hydroxide", "potassium_hydroxide");
        ALIASES.put("potassiumhydroxide", "potassium_hydroxide");
        ALIASES.put("sulfuric_acid", "sulfuric_acid");
        ALIASES.put("sulfuricacid", "sulfuric_acid");
        ALIASES.put("diluted_sulfuric_acid", "sulfuric_acid");
        ALIASES.put("nitric_acid", "nitric_acid");
        ALIASES.put("nitricacid", "nitric_acid");
        ALIASES.put("phosphoric_acid", "phosphoric_acid");
        ALIASES.put("phosphoricacid", "phosphoric_acid");
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

        // Structures are bound by material identity/name only.
    }

    private static DefinitionResult<Molecule> lastFailure;
    private static final Map<Molecule, String> SOURCES = new WeakHashMap<>();

    public static Molecule getMolecule(Materials material) {
        return definition(material).value();
    }

    public static DefinitionResult<Molecule> definition(Materials material) {
        if (material == null || material == Materials._NULL) return DefinitionResult.absent();
        lastFailure = null;
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
            if (loaded != null || lastFailure != null) break;

            // 2. 剥离常见前缀后匹配
            for (String prefix : PREFIXES_TO_STRIP) {
                if (name.startsWith(prefix)) {
                    String stripped = name.substring(prefix.length());
                    loaded = loadMoleculeFromJson(stripped);
                    if (loaded != null || lastFailure != null) break;
                }
            }
            if (loaded != null || lastFailure != null) break;

            // 3. 去除下划线后匹配
            if (name.contains("_")) {
                loaded = loadMoleculeFromJson(name.replace("_", ""));
                if (loaded != null || lastFailure != null) break;
            }
        }

        return loaded != null
            ? new DefinitionResult<>(State.PRESENT, loaded, SOURCES.getOrDefault(loaded, ""), List.of())
            : lastFailure != null ? lastFailure : DefinitionResult.absent();
    }

    private static String toSnakeCase(String str) {
        return MaterialNames.normalize(str);
    }

    private static final Map<String, DefinitionResult<Molecule>> RESOURCE_CACHE = new LinkedHashMap<>() {

        protected boolean removeEldestEntry(Map.Entry<String, DefinitionResult<Molecule>> entry) {
            return size() > 4096;
        }
    };

    private static Molecule loadMoleculeFromJson(String name) {
        DefinitionResult<Molecule> result = RESOURCE_CACHE.computeIfAbsent(name, MoleculeManager::readMolecule);
        if (result.state() == State.INVALID || result.state() == State.DISABLED) lastFailure = result;
        return result.value();
    }

    private static DefinitionResult<Molecule> readMolecule(String name) {
        for (String ns : NAMESPACES) {
            String path = "/assets/" + ns + "/molecules/" + name + ".json";
            try (InputStream is = ResourceData.open(ns, "molecules/" + name + ".json")) {
                if (is != null) {
                    var json = com.rubenverg.moldraw.core.ResourceJson.read(is);
                    if (json.isJsonObject() && json.getAsJsonObject()
                        .has("disabled")
                        && json.getAsJsonObject()
                            .get("disabled")
                            .getAsBoolean())
                        return new DefinitionResult<>(
                            State.DISABLED,
                            null,
                            ns + ":" + name,
                            List.of("DISABLED_STRUCTURE:" + ns + ":" + name));
                    Molecule model = GSON.fromJson(json, Molecule.class);
                    MoleculeSnapshots.freeze(model);
                    SOURCES.put(model, ns + ":" + name);
                    return new DefinitionResult<>(State.PRESENT, model, ns + ":" + name, List.of());
                }
            } catch (java.io.FileNotFoundException missing) {
                // Try next namespace.
            } catch (Exception e) {
                if (MolDrawConfig.INSTANCE.debugMode) {
                    MolDraw.LOGGER.warn("Failed to load molecule " + path, e);
                }
                return new DefinitionResult<>(
                    State.INVALID,
                    null,
                    ns + ":" + name,
                    List.of("INVALID_STRUCTURE:" + path + ":" + e.getMessage()));
            }
        }
        return DefinitionResult.absent();
    }

    public static DefinitionResult<Molecule> namedDefinition(String name) {
        lastFailure = null;
        Molecule model = getMoleculeByName(name);
        return model != null ? new DefinitionResult<>(State.PRESENT, model, SOURCES.getOrDefault(model, ""), List.of())
            : lastFailure != null ? lastFailure : DefinitionResult.absent();
    }

    private static Molecule getMoleculeByName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return null;
        String name = ALIASES.getOrDefault(toSnakeCase(rawName), toSnakeCase(rawName));
        Molecule loaded = loadMoleculeFromJson(name);
        if (loaded != null || lastFailure != null) return loaded;
        for (String prefix : PREFIXES_TO_STRIP) {
            if (name.startsWith(prefix)) {
                String stripped = name.substring(prefix.length());
                loaded = loadMoleculeFromJson(stripped);
                if (loaded != null || lastFailure != null) return loaded;
            }
        }
        return null;
    }

    public static void clearCache() {

        RESOURCE_CACHE.clear();
        SOURCES.clear();
        lastFailure = null;
    }
}
