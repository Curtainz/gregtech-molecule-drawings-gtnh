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
        "moldraw" };

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
        if (material.mMaterialList == null || material.mMaterialList.size() <= 1) return false;

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
            || name.contains("cupronickel")) {
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
        if (!isAlloy(material)) return null;

        if (ALLOY_OVERRIDE_CACHE.containsKey(material)) {
            return ALLOY_OVERRIDE_CACHE.get(material);
        }

        String name = getNormalizedName(material);
        List<Pair<Materials, Long>> fromJson = loadAlloyJson(name);
        if (fromJson != null && !fromJson.isEmpty()) {
            ALLOY_OVERRIDE_CACHE.put(material, fromJson);
            return fromJson;
        }

        return null;
    }

    private static String getNormalizedName(Materials material) {
        String name = material.mName != null ? material.mName.toLowerCase(Locale.ROOT)
            : material.name()
                .toLowerCase(Locale.ROOT);
        return name.replace(" ", "_")
            .replace("-", "_");
    }

    private static List<Pair<Materials, Long>> loadAlloyJson(String name) {
        for (String ns : NAMESPACES) {
            String path = "/assets/" + ns + "/alloys/" + name + ".json";
            try (InputStream is = AlloyManager.class.getResourceAsStream(path)) {
                if (is != null) {
                    JsonObject obj = new JsonParser().parse(new InputStreamReader(is, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                    if (obj.has("components") && obj.get("components")
                        .isJsonArray()) {
                        JsonArray arr = obj.getAsJsonArray("components");
                        List<Pair<Materials, Long>> list = new ArrayList<>();
                        for (JsonElement el : arr) {
                            if (el.isJsonObject()) {
                                JsonObject comp = el.getAsJsonObject();
                                String matName = comp.get("material")
                                    .getAsString();
                                if (matName.contains(":")) matName = matName.substring(matName.indexOf(':') + 1);
                                Materials m = Materials.get(matName);
                                long amount = comp.has("amount") ? comp.get("amount")
                                    .getAsLong() : 1L;
                                if (m != null && m != Materials._NULL) {
                                    list.add(new Pair<>(m, amount));
                                }
                            }
                        }
                        if (!list.isEmpty()) return list;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
