package com.rubenverg.moldraw;

import java.io.InputStream;
import java.util.*;

import com.rubenverg.moldraw.core.DefinitionResult;
import com.rubenverg.moldraw.core.LegacyComposition;
import com.rubenverg.moldraw.core.MaterialDefinition.Component;
import com.rubenverg.moldraw.core.MaterialDefinition.State;
import com.rubenverg.moldraw.core.ResourceJson;

import gregtech.api.enums.Materials;

public final class AlloyManager {

    private static final Set<String> KNOWN_ALLOYS = new HashSet<>();

    private static final String[] NAMESPACES = new String[] { "gtceu", "gcyr", "gtca", "gtocore", "cosmiccore", "tfg",
        "gtmoldraw" };

    static {
        // 预定义常用与 GTNH/GT5u 合金名（小写下划线）
        String[] defaultAlloys = new String[] { "battery_alloy", "brass", "bronze", "cupronickel", "electrum", "invar",
            "kanthal", "magnalium", "nichrome", "niobium_titanium", "sterling_silver", "rose_gold", "black_bronze",
            "bismuth_bronze", "rtm_alloy", "ruridit", "soldering_alloy", "stainless_steel", "tin_alloy", "ultimet",
            "vanadium_gallium", "yttrium_barium_cuprate", "osmiridium", "gallium_arsenide", "indium_gallium_phosphide",
            "nickel_zinc_ferrite", "tungsten_carbide", "manganese_phosphide", "magnesium_diboride",
            "mercury_barium_calcium_cuprate", "uranium_triplatinum", "samarium_iron_arsenic_oxide",
            "indium_tin_barium_titanium_cuprate", "uranium_rhodium_dinaquadide",
            "enriched_naquadah_trinium_europium_duranide", "ruthenium_trinium_americium_neutronate", "black_steel",
            "tungsten_steel", "cobalt_brass", "vanadium_steel", "potin", "naquadah_alloy", "rhodium_plated_palladium",
            "red_steel", "blue_steel", "hssg", "red_alloy", "hsse", "hsss", "blue_alloy", "tantalum_carbide",
            "hsla_steel", "molybdenum_disilicide", "zeron_100", "watertight_steel", "incoloy_ma_956",
            "maraging_steel_300", "hastelloy_x", "stellite_100", "titanium_carbide", "titanium_tungsten_carbide",
            "hastelloy_c_276", "steel", "conductive_alloy", "sodium_lead_alloy", "neptunium_palladium_aluminium",
            "dark_soularium", "microversium", "trinaquadalloy", "end_steel", "lumium", "darconite", "electrum_flux",
            "manyullyn", "advanced_soldering_alloy", "signalum", "energetic_alloy", "mythril", "vibrant_alloy",
            "enderium", "dark_steel", "electrical_steel", "desh", "ostrum", "rocket_alloy_t1", "rocket_alloy_t2",
            "manasteel", "annealed_manasteel", "galvanized_ethersteel", "trinium_naqide", "prismatic_tungstensteel",
            "resonant_virtue_meld", "naquadric_superalloy", "mar_m_200_steel", "artherium_sn", "hg_1223",
            "nimonic_80_a", "mar_ce_m_200", "cinobite_a_241", "adamantium_alloy", "inconel_792", "dural", "berwollium",
            "incoloy_846", "melodic_alloy", "tritanium", "pikyonium", "indalloy_140", "babbitt_alloy", "staballoy",
            "damascus_steel" };
        Collections.addAll(KNOWN_ALLOYS, defaultAlloys);
    }

    public static boolean isAlloy(Materials material) {
        if (material == null || material == Materials._NULL) return false;
        if (material.mElement != null) return false; // 单质绝对不是合金

        // 如果存在分子图（如聚合物、有机化学品、水等），绝不作为合金展示
        if (MoleculeManager.getMolecule(material) != null) return false;

        String name = getNormalizedName(material);

        if (KNOWN_ALLOYS.contains(name)) return true;

        // 名字包含 alloy/steel/bronze/brass/invar 等特征
        if (name.contains("alloy") || name.contains("steel")
            || name.contains("bronze")
            || name.contains("brass")
            || name.contains("invar")
            || name.contains("solder")
            || name.contains("cuprate")
            || name.contains("superalloy")
            || name.contains("cupronickel")
            || name.contains("superconductor")) {
            return true;
        }

        // 检查是否有对应的 alloy json
        if (loadAlloyJson(name) != null) {

            return true;
        }

        return false;
    }

    public static void clearCache() {
        JSON_CACHE.clear();
    }

    private static String getNormalizedName(Materials material) {
        return MaterialNames.normalize(material.mName);
    }

    private static final Map<String, DefinitionResult<List<Component>>> JSON_CACHE = new LinkedHashMap<>() {

        protected boolean removeEldestEntry(Map.Entry<String, DefinitionResult<List<Component>>> entry) {
            return size() > 4096;
        }
    };

    public static DefinitionResult<List<Component>> definition(Materials material) {
        return JSON_CACHE.computeIfAbsent(getNormalizedName(material), AlloyManager::readAlloyJson);
    }

    private static List<Component> loadAlloyJson(String name) {
        return JSON_CACHE.computeIfAbsent(name, AlloyManager::readAlloyJson)
            .value();
    }

    private static DefinitionResult<List<Component>> readAlloyJson(String name) {
        for (String ns : NAMESPACES) {
            try (InputStream is = ResourceData.open(ns, "alloys/" + name + ".json")) {
                if (is == null) continue;
                String source = ns + ":" + name;
                var decoded = LegacyComposition.decode(ResourceJson.read(is), source);
                if (decoded.value() == null || decoded.value()
                    .isEmpty()) return decoded;
                List<Component> result = new ArrayList<>();
                for (var component : decoded.value()) {
                    Materials material = MaterialNames.resolve(component.id());
                    if (!GT5MaterialLookup.isMaterialValid(material))
                        throw new IllegalArgumentException("Unresolved component: " + component.id());
                    result.add(new Component("gt:" + material.mName, component.amount()));
                }
                return new DefinitionResult<>(State.PRESENT, List.copyOf(result), source, List.of());
            } catch (java.io.FileNotFoundException missing) {
                // Absent in this namespace.
            } catch (Exception failure) {
                MolDraw.LOGGER.warn("Invalid alloy resource {}:{}", ns, name, failure);
                return new DefinitionResult<>(
                    State.INVALID,
                    null,
                    ns + ":" + name,
                    List.of("INVALID_COMPOSITION:" + ns + ":" + name + ":" + failure.getMessage()));
            }
        }
        return DefinitionResult.absent();
    }
}
