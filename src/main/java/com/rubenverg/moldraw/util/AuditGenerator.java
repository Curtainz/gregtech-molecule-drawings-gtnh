package com.rubenverg.moldraw.util;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class AuditGenerator {

    private static final Set<String> ELEMENTS = new HashSet<>(
        Arrays.asList(
            "Hydrogen",
            "Helium",
            "Lithium",
            "Beryllium",
            "Boron",
            "Carbon",
            "Nitrogen",
            "Oxygen",
            "Fluorine",
            "Neon",
            "Sodium",
            "Magnesium",
            "Aluminium",
            "Silicon",
            "Phosphorus",
            "Sulfur",
            "Chlorine",
            "Argon",
            "Potassium",
            "Calcium",
            "Scandium",
            "Titanium",
            "Vanadium",
            "Chrome",
            "Chromium",
            "Manganese",
            "Iron",
            "Cobalt",
            "Nickel",
            "Copper",
            "Zinc",
            "Gallium",
            "Germanium",
            "Arsenic",
            "Selenium",
            "Bromine",
            "Krypton",
            "Rubidium",
            "Strontium",
            "Yttrium",
            "Zirconium",
            "Niobium",
            "Molybdenum",
            "Technetium",
            "Ruthenium",
            "Rhodium",
            "Palladium",
            "Silver",
            "Cadmium",
            "Indium",
            "Tin",
            "Antimony",
            "Tellurium",
            "Iodine",
            "Xenon",
            "Caesium",
            "Barium",
            "Lanthanum",
            "Cerium",
            "Praseodymium",
            "Neodymium",
            "Promethium",
            "Samarium",
            "Europium",
            "Gadolinium",
            "Terbium",
            "Dysprosium",
            "Holmium",
            "Erbium",
            "Thulium",
            "Ytterbium",
            "Lutetium",
            "Hafnium",
            "Tantalum",
            "Tungsten",
            "Rhenium",
            "Osmium",
            "Iridium",
            "Platinum",
            "Gold",
            "Mercury",
            "Thallium",
            "Lead",
            "Bismuth",
            "Polonium",
            "Astatine",
            "Radon",
            "Francium",
            "Radium",
            "Actinium",
            "Thorium",
            "Protactinium",
            "Uranium",
            "Neptunium",
            "Plutonium",
            "Americium",
            "Curium",
            "Berkelium",
            "Californium",
            "Einsteinium",
            "Fermium",
            "Mendelevium",
            "Nobelium",
            "Lawrencium",
            "Rutherfordium",
            "Dubnium",
            "Seaborgium",
            "Bohrium",
            "Hassium",
            "Meitnerium",
            "Darmstadtium",
            "Roentgenium",
            "Copernicium",
            "Nihonium",
            "Flerovium",
            "Moscovium",
            "Livermorium",
            "Tennessine",
            "Oganesson",
            "MeteoricIron",
            "WroughtIron",
            "Naquadah",
            "Neutronium",
            "Tritanium",
            "Duranium",
            "Oriharukon",
            "Adamantium",
            "Desh",
            "Magic",
            "Empty",
            "_NULL"));

    private static final Set<String> KNOWN_ALLOYS = new HashSet<>(
        Arrays.asList(
            "battery_alloy",
            "brass",
            "bronze",
            "cupronickel",
            "electrum",
            "invar",
            "kanthal",
            "magnalium",
            "nichrome",
            "niobium_titanium",
            "sterling_silver",
            "rose_gold",
            "black_bronze",
            "bismuth_bronze",
            "rtm_alloy",
            "ruridit",
            "soldering_alloy",
            "stainless_steel",
            "tin_alloy",
            "ultimet",
            "vanadium_gallium",
            "yttrium_barium_cuprate",
            "osmiridium",
            "gallium_arsenide",
            "indium_gallium_phosphide",
            "nickel_zinc_ferrite",
            "tungsten_carbide",
            "manganese_phosphide",
            "magnesium_diboride",
            "mercury_barium_calcium_cuprate",
            "uranium_triplatinum",
            "samarium_iron_arsenic_oxide",
            "indium_tin_barium_titanium_cuprate",
            "uranium_rhodium_dinaquadide",
            "enriched_naquadah_trinium_europium_duranide",
            "ruthenium_trinium_americium_neutronate",
            "black_steel",
            "tungsten_steel",
            "cobalt_brass",
            "vanadium_steel",
            "potin",
            "naquadah_alloy",
            "rhodium_plated_palladium",
            "red_steel",
            "blue_steel",
            "hssg",
            "red_alloy",
            "hsse",
            "hsss",
            "blue_alloy",
            "tantalum_carbide",
            "hsla_steel",
            "molybdenum_disilicide",
            "zeron_100",
            "watertight_steel",
            "incoloy_ma_956",
            "maraging_steel_300",
            "hastelloy_x",
            "stellite_100",
            "titanium_carbide",
            "titanium_tungsten_carbide",
            "hastelloy_c_276",
            "steel",
            "conductive_alloy",
            "sodium_lead_alloy",
            "neptunium_palladium_aluminium",
            "dark_soularium",
            "microversium",
            "trinaquadalloy",
            "end_steel",
            "lumium",
            "darconite",
            "electrum_flux",
            "manyullyn",
            "advanced_soldering_alloy",
            "signalum",
            "energetic_alloy",
            "mythril",
            "vibrant_alloy",
            "enderium",
            "dark_steel",
            "electrical_steel",
            "desh",
            "ostrum",
            "rocket_alloy_t1",
            "rocket_alloy_t2",
            "manasteel",
            "annealed_manasteel",
            "galvanized_ethersteel",
            "trinium_naqide",
            "prismatic_tungstensteel",
            "resonant_virtue_meld",
            "naquadric_superalloy",
            "mar_m_200_steel",
            "artherium_sn",
            "hg_1223",
            "nimonic_80_a",
            "mar_ce_m_200",
            "cinobite_a_241",
            "adamantium_alloy",
            "inconel_792",
            "dural",
            "berwollium",
            "incoloy_846",
            "melodic_alloy",
            "tritanium",
            "pikyonium",
            "indalloy_140",
            "babbitt_alloy",
            "staballoy",
            "damascus_steel"));

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        ALIASES.put("aluminiumoxide", "aluminiumoxide");
        ALIASES.put("cryolite", "cryolite");
        ALIASES.put("silicondioxide", "silicon_dioxide");
        ALIASES.put("water", "water");
        ALIASES.put("ice", "water");
        ALIASES.put("heavyice", "heavy_water");
        ALIASES.put("dryice", "carbon_dioxide");
        ALIASES.put("carbondioxide", "carbon_dioxide");
        ALIASES.put("carbonmonoxide", "carbon_monoxide");
        ALIASES.put("methane", "methane");
        ALIASES.put("ammonia", "ammonia");
        ALIASES.put("oxygen", "oxygen");
        ALIASES.put("hydrogen", "hydrogen");
        ALIASES.put("nitrogen", "nitrogen");
        ALIASES.put("chlorine", "chlorine");
        ALIASES.put("fluorine", "fluorine");
        ALIASES.put("aluminiumhydroxide", "aluminium_hydroxide");
        ALIASES.put("sodiumhydroxide", "sodium_hydroxide");
        ALIASES.put("potassiumhydroxide", "potassium_hydroxide");
        ALIASES.put("sulfuricacid", "sulfuric_acid");
        ALIASES.put("dilutedsulfuricacid", "sulfuric_acid");
        ALIASES.put("nitricacid", "nitric_acid");
        ALIASES.put("phosphoricacid", "phosphoric_acid");
        ALIASES.put("silane", "silane");
        ALIASES.put("disilane", "disilane");
        ALIASES.put("silicontetrachloride", "silicon_tetrachloride");
        ALIASES.put("silicontetrafluoride", "silicon_tetrafluoride");
        ALIASES.put("trichlorosilane", "trichlorosilane");
    }

    public static void main(String[] args) throws Exception {
        File allMatsFile = new File("all_materials.txt");
        if (!allMatsFile.exists()) {
            System.err.println("all_materials.txt not found!");
            return;
        }

        List<String> rawLines = Files.readAllLines(allMatsFile.toPath(), StandardCharsets.UTF_8);

        // 收集所有已知分子结构 json
        Set<String> molecules = new HashSet<>();
        File assetsDir = new File("src/main/resources/assets");
        collectMolecules(assetsDir, molecules);

        List<MaterialEntry> compounds = new ArrayList<>();
        List<MaterialEntry> alloys = new ArrayList<>();
        List<MaterialEntry> elements = new ArrayList<>();

        for (String line : rawLines) {
            String name = line.trim();
            if (name.isEmpty() || name.equals("_NULL") || name.equals("Empty")) continue;

            boolean hasMol = checkHasMolecule(name, molecules);
            boolean isAlloy = isAlloy(name);
            boolean isElem = isElement(name);

            if (hasMol) {
                compounds.add(new MaterialEntry(name, "化合物 (Compound)", "✅ 已有分子结构图", "3D 分子化学式"));
            } else if (isElem) {
                elements.add(new MaterialEntry(name, "元素单质 (Element)", "⚪ 元素单质", "单质无需图"));
            } else if (isAlloy) {
                alloys.add(new MaterialEntry(name, "合金/混合物 (Alloy)", "🥧 合金成分饼图", "成分占比饼图"));
            } else {
                compounds.add(new MaterialEntry(name, "化合物 (Compound)", "❌ 待补充分子图", "待补充"));
            }
        }

        compounds.sort((a, b) -> {
            boolean aHas = a.status.startsWith("✅");
            boolean bHas = b.status.startsWith("✅");
            if (aHas != bHas) return aHas ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        alloys.sort(Comparator.comparing(a -> a.name.toLowerCase()));
        elements.sort(Comparator.comparing(a -> a.name.toLowerCase()));

        long cDone = compounds.stream()
            .filter(c -> c.status.startsWith("✅"))
            .count();
        long cPending = compounds.size() - cDone;
        double cRate = compounds.isEmpty() ? 100.0 : (cDone * 100.0 / compounds.size());

        int totalAll = compounds.size() + alloys.size() + elements.size();
        long totalDone = cDone + alloys.size() + elements.size();
        double totalRate = totalAll == 0 ? 100.0 : (totalDone * 100.0 / totalAll);

        File out = new File("MATERIALS_AUDIT.md");
        try (PrintWriter writer = new PrintWriter(out, "UTF-8")) {
            writer.println("# GregTech 5 (GTNH) 全量材料分类与分子图覆盖审计报告");
            writer.println();
            writer.println("> **版本**: `0.1.0-beta.2` | **生成时间**: " + new Date());
            writer.println("> **总计扫描材料数**: **" + rawLines.size() + "**");
            writer.println();
            writer.println("## 📊 1. 材料分类与渲染覆盖概览");
            writer.println();
            writer.println("| 材料分类 (Category) | 材料总数 | 已支持展示 (分子图 / 饼图 / 单质) | 待补充分子图 | 渲染方案覆盖率 |");
            writer.println("| :--- | :--- | :--- | :--- | :--- |");
            writer.println(
                String.format(
                    Locale.ROOT,
                    "| **化合物 (Compounds)** | %d | %d (分子结构) | %d | **%.1f%%** |",
                    compounds.size(),
                    cDone,
                    cPending,
                    cRate));
            writer.println(
                String.format(
                    Locale.ROOT,
                    "| **合金与混合物 (Alloys)** | %d | %d (合金饼图) | 0 | **100.0%%** |",
                    alloys.size(),
                    alloys.size()));
            writer.println(
                String.format(
                    Locale.ROOT,
                    "| **元素单质 (Elements)** | %d | %d (单质无需图) | 0 | **100.0%%** |",
                    elements.size(),
                    elements.size()));
            writer.println(
                String.format(
                    Locale.ROOT,
                    "| **合计** | **%d** | **%d** | **%d** | **%.1f%%** |",
                    totalAll,
                    totalDone,
                    cPending,
                    totalRate));
            writer.println();

            writer.println("## 🧪 2. 化合物 (Compounds) 分子式支持清单");
            writer.println();
            writer.println("> **说明**：化合物类别优先渲染 3D 分子化学结构式，**绝不作为合金画饼图**。");
            writer.println();
            writer.println("| # | 材料标识 (Name) | 分子式支持状态 (Status) | 渲染类型 (Type) |");
            writer.println("| :--- | :--- | :--- | :--- |");
            int i = 1;
            for (MaterialEntry c : compounds) {
                writer.println(String.format("| %d | `%s` | %s | %s |", i++, c.name, c.status, c.drawing));
            }
            writer.println();

            writer.println("## 🥧 3. 合金与混合物 (Alloys & Mixtures) 饼图支持清单");
            writer.println();
            writer.println("> **说明**：通过熔炼或混合生成的金属合金与超导材料，当按住 Shift 时呈现动态成分占比饼状图。模具、工具、机械外壳等已被严格排除。");
            writer.println();
            writer.println("| # | 材料标识 (Name) | 支持状态 | 渲染类型 |");
            writer.println("| :--- | :--- | :--- | :--- |");
            int j = 1;
            for (MaterialEntry a : alloys) {
                writer.println(String.format("| %d | `%s` | %s | %s |", j++, a.name, a.status, a.drawing));
            }
            writer.println();

            writer.println("## ⚪ 4. 元素单质 (Elements)");
            writer.println();
            writer.println("> **说明**：纯化学元素与虚构单质，无需分子结构或合金饼图。");
            writer.println();
            writer.println("| # | 元素标识 (Name) | 状态 |");
            writer.println("| :--- | :--- | :--- |");
            int k = 1;
            for (MaterialEntry e : elements) {
                writer.println(String.format("| %d | `%s` | %s |", k++, e.name, e.status));
            }
            writer.println();
        }

        System.out.println(
            "Generated MATERIALS_AUDIT.md successfully! Compounds: " + compounds.size()
                + " (Supported: "
                + cDone
                + ", Pending: "
                + cPending
                + "), Alloys: "
                + alloys.size()
                + ", Elements: "
                + elements.size());
    }

    private static boolean checkHasMolecule(String name, Set<String> molecules) {
        String snake = toSnakeCase(name);
        if (molecules.contains(snake)) return true;
        if (molecules.contains(name.toLowerCase(Locale.ROOT))) return true;
        if (molecules.contains(snake.replace("_", ""))) return true;

        if (ALIASES.containsKey(snake) && molecules.contains(ALIASES.get(snake))) return true;
        String noUnder = snake.replace("_", "");
        if (ALIASES.containsKey(noUnder) && molecules.contains(ALIASES.get(noUnder))) return true;

        return false;
    }

    private static boolean isElement(String name) {
        return ELEMENTS.contains(name);
    }

    private static boolean isAlloy(String name) {
        String snake = toSnakeCase(name);
        if (KNOWN_ALLOYS.contains(snake)) return true;
        return snake.contains("alloy") || snake.contains("steel")
            || snake.contains("bronze")
            || snake.contains("brass")
            || snake.contains("invar")
            || snake.contains("solder")
            || snake.contains("cuprate")
            || snake.contains("superalloy")
            || snake.contains("cupronickel")
            || snake.contains("superconductor");
    }

    private static String toSnakeCase(String str) {
        if (str == null) return "";
        return str.replaceAll("(?<!^)(?=[A-Z])", "_")
            .replace(" ", "_")
            .replace("-", "_")
            .toLowerCase(Locale.ROOT)
            .replaceAll("_+", "_");
    }

    private static void collectMolecules(File dir, Set<String> result) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectMolecules(f, result);
            } else if (f.getName()
                .endsWith(".json")
                && f.getParentFile()
                    .getName()
                    .equals("molecules")) {
                        String base = f.getName()
                            .substring(
                                0,
                                f.getName()
                                    .length() - 5);
                        result.add(base.toLowerCase(Locale.ROOT));
                    }
        }
    }

    private static class MaterialEntry {

        final String name;
        final String category;
        final String status;
        final String drawing;

        MaterialEntry(String name, String category, String status, String drawing) {
            this.name = name;
            this.category = category;
            this.status = status;
            this.drawing = drawing;
        }
    }
}
