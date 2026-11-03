package com.rubenverg.moldraw;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rubenverg.moldraw.util.Pair;

import gregtech.api.enums.Materials;

public final class AlloyManager {

    private static final Set<String> KNOWN_ALLOYS = new HashSet<>();
    private static final Map<Materials, List<Pair<Materials, Long>>> ALLOY_OVERRIDE_CACHE = new HashMap<>();
    private static final Set<Materials> NON_ALLOYS = new HashSet<>();

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
        if (NON_ALLOYS.contains(material)) return false;
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
            KNOWN_ALLOYS.add(name);
            return true;
        }

        NON_ALLOYS.add(material);
        return false;
    }

    public static List<Pair<Materials, Long>> getAlloyComponents(Materials material) {
        return loadAlloyJson(getNormalizedName(material));
    }

    public static void clearCache() {
        NON_ALLOYS.clear();
        ALLOY_OVERRIDE_CACHE.clear();
        JSON_CACHE.clear();
    }

    private static String getNormalizedName(Materials material) {
        return MaterialNames.normalize(material.mName);
    }

    private static final Map<String, Optional<List<Pair<Materials, Long>>>> JSON_CACHE = new HashMap<>();

    private static List<Pair<Materials, Long>> loadAlloyJson(String name) {
        return JSON_CACHE.computeIfAbsent(name, key -> Optional.ofNullable(readAlloyJson(key)))
            .orElse(null);
    }

    private static List<Pair<Materials, Long>> readAlloyJson(String name) {
        for (String ns : NAMESPACES) {
            try (InputStream is = ResourceData.open(ns, "alloys/" + name + ".json")) {
                if (is == null) continue;
                JsonObject obj = new JsonParser().parse(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .getAsJsonObject();
                if (obj.has("derive") && obj.get("derive")
                    .getAsBoolean()) return Collections.emptyList();
                List<Pair<Materials, Long>> result = new ArrayList<>();
                for (JsonElement el : obj.getAsJsonArray("components")) {
                    String id;
                    long count = 1;
                    if (el.isJsonPrimitive()) id = el.getAsString();
                    else if (el.isJsonArray()) {
                        JsonArray pair = el.getAsJsonArray();
                        if (pair.size() != 2) throw new IllegalArgumentException("Expected material/count pair");
                        id = pair.get(0)
                            .getAsString();
                        count = pair.get(1)
                            .getAsLong();
                    } else {
                        id = el.getAsJsonObject()
                            .get("material")
                            .getAsString();
                        if (el.getAsJsonObject()
                            .has("amount"))
                            count = el.getAsJsonObject()
                                .get("amount")
                                .getAsLong();
                    }
                    Materials material = MaterialNames.resolve(id);
                    if (!GT5MaterialLookup.isMaterialValid(material) || count <= 0)
                        throw new IllegalArgumentException("Invalid component: " + id);
                    result.add(new Pair<>(material, count));
                }
                if (result.isEmpty()) throw new IllegalArgumentException("Empty components");
                return Collections.unmodifiableList(result);
            } catch (java.io.FileNotFoundException missing) {
                // Absent in this namespace.
            } catch (Exception failure) {
                MolDraw.LOGGER.warn("Invalid alloy resource {}:{}", ns, name, failure);
            }
        }
        return null;
    }
}
