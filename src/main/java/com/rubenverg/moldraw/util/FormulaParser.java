package com.rubenverg.moldraw.util;

import java.util.*;

import gregtech.api.enums.Materials;

public class FormulaParser {

    private static final Map<String, Materials> SYMBOL_TO_MATERIAL = new HashMap<>();
    private static final Map<Materials, Double> MATERIAL_WEIGHTS = new HashMap<>();
    private static List<String> SORTED_SYMBOLS = new ArrayList<>();
    private static boolean initialized = false;

    // 虚拟材料占位符：表示化学式中的未知或神秘基团
    public static final Materials UNKNOWN_COMPONENT = Materials._NULL;

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        // 1. 现实基础元素注册
        registerBaseElement("H", "Hydrogen", 1.008);
        registerBaseElement("He", "Helium", 4.003);
        registerBaseElement("Li", "Lithium", 6.941);
        registerBaseElement("Be", "Beryllium", 9.012);
        registerBaseElement("B", "Boron", 10.811);
        registerBaseElement("C", "Carbon", 12.011);
        registerBaseElement("N", "Nitrogen", 14.007);
        registerBaseElement("O", "Oxygen", 15.999);
        registerBaseElement("F", "Fluorine", 18.998);
        registerBaseElement("Ne", "Neon", 20.180);
        registerBaseElement("Na", "Sodium", 22.990);
        registerBaseElement("Mg", "Magnesium", 24.305);
        registerBaseElement("Al", "Aluminium", 26.982);
        registerBaseElement("Si", "Silicon", 28.086);
        registerBaseElement("P", "Phosphorus", 30.974);
        registerBaseElement("S", "Sulfur", 32.065);
        registerBaseElement("Cl", "Chlorine", 35.453);
        registerBaseElement("Ar", "Argon", 39.948);
        registerBaseElement("K", "Potassium", 39.098);
        registerBaseElement("Ca", "Calcium", 40.078);
        registerBaseElement("Sc", "Scandium", 44.956);
        registerBaseElement("Ti", "Titanium", 47.867);
        registerBaseElement("V", "Vanadium", 50.942);
        registerBaseElement("Cr", "Chrome", 51.996);
        registerBaseElement("Mn", "Manganese", 54.938);
        registerBaseElement("Fe", "Iron", 55.845);
        registerBaseElement("Co", "Cobalt", 58.933);
        registerBaseElement("Ni", "Nickel", 58.693);
        registerBaseElement("Cu", "Copper", 63.546);
        registerBaseElement("Zn", "Zinc", 65.380);
        registerBaseElement("Ga", "Gallium", 69.723);
        registerBaseElement("Ge", "Germanium", 72.630);
        registerBaseElement("As", "Arsenic", 74.922);
        registerBaseElement("Se", "Selenium", 78.960);
        registerBaseElement("Br", "Bromine", 79.904);
        registerBaseElement("Kr", "Krypton", 83.798);
        registerBaseElement("Rb", "Rubidium", 85.468);
        registerBaseElement("Sr", "Strontium", 87.620);
        registerBaseElement("Y", "Yttrium", 88.906);
        registerBaseElement("Zr", "Zirconium", 91.224);
        registerBaseElement("Nb", "Niobium", 92.906);
        registerBaseElement("Mo", "Molybdenum", 95.950);
        registerBaseElement("Tc", "Technetium", 98.0);
        registerBaseElement("Ru", "Ruthenium", 101.07);
        registerBaseElement("Rh", "Rhodium", 102.91);
        registerBaseElement("Pd", "Palladium", 106.42);
        registerBaseElement("Ag", "Silver", 107.87);
        registerBaseElement("Cd", "Cadmium", 112.41);
        registerBaseElement("In", "Indium", 114.82);
        registerBaseElement("Sn", "Tin", 118.71);
        registerBaseElement("Sb", "Antimony", 121.76);
        registerBaseElement("Te", "Tellurium", 127.60);
        registerBaseElement("I", "Iodine", 126.90);
        registerBaseElement("Xe", "Xenon", 131.29);
        registerBaseElement("Cs", "Caesium", 132.91);
        registerBaseElement("Ba", "Barium", 137.33);
        registerBaseElement("La", "Lanthanum", 138.91);
        registerBaseElement("Ce", "Cerium", 140.12);
        registerBaseElement("Pr", "Praseodymium", 140.91);
        registerBaseElement("Nd", "Neodymium", 144.24);
        registerBaseElement("Sm", "Samarium", 150.36);
        registerBaseElement("Eu", "Europium", 151.96);
        registerBaseElement("Gd", "Gadolinium", 157.25);
        registerBaseElement("Tb", "Terbium", 158.93);
        registerBaseElement("Dy", "Dysprosium", 162.50);
        registerBaseElement("Ho", "Holmium", 164.93);
        registerBaseElement("Er", "Erbium", 167.26);
        registerBaseElement("Tm", "Thulium", 168.93);
        registerBaseElement("Yb", "Ytterbium", 173.05);
        registerElementAlias("Lu", "Lutetium", 174.97);
        registerElementAlias("Hf", "Hafnium", 178.49);
        registerElementAlias("Ta", "Tantalum", 180.95);
        registerBaseElement("W", "Tungsten", 183.84);
        registerElementAlias("Re", "Rhenium", 186.21);
        registerBaseElement("Os", "Osmium", 190.23);
        registerBaseElement("Ir", "Iridium", 192.22);
        registerBaseElement("Pt", "Platinum", 195.08);
        registerBaseElement("Au", "Gold", 196.97);
        registerBaseElement("Hg", "Mercury", 200.59);
        registerElementAlias("Tl", "Thallium", 204.38);
        registerBaseElement("Pb", "Lead", 207.2);
        registerBaseElement("Bi", "Bismuth", 208.98);
        registerElementAlias("Po", "Polonium", 209.0);
        registerElementAlias("Rn", "Radon", 222.0);
        registerElementAlias("Ra", "Radium", 226.0);
        registerElementAlias("Ac", "Actinium", 227.0);
        registerBaseElement("Th", "Thorium", 232.04);
        registerElementAlias("Pa", "Protactinium", 231.04);
        registerBaseElement("U", "Uranium", 238.03);
        registerElementAlias("Np", "Neptunium", 237.0);
        registerBaseElement("Pu", "Plutonium", 244.0);
        registerBaseElement("Am", "Americium", 243.0);

        registerBaseElement("D", "Deuterium", 2.014);
        registerBaseElement("T", "Tritium", 3.016);

        // 2. GTNH 幻想/虚构与魔法元素精确绑定
        bindFantasyMaterial("D*", "DraconiumAwakened", "AwakenedDraconium", 320.0);
        bindFantasyMaterial("If*", "Infinity", "InfinityCatalyst", 500.0);
        bindFantasyMaterial("If", "Infinity", "Infinity", 500.0);
        bindFantasyMaterial("Nq*", "Naquadria", "Naquadria", 420.0);
        bindFantasyMaterial("Nq+", "NaquadahEnriched", "NaquadahEnriched", 380.0);
        bindFantasyMaterial("Nq", "Naquadah", "Naquadah", 360.0);
        bindFantasyMaterial("Nt", "Neutronium", "CosmicNeutronium", 1000.0);
        bindFantasyMaterial("Sp", "Spacetime", "Spacetime", 800.0);
        bindFantasyMaterial("Tn", "Tritanium", "Tritanium", 250.0);
        bindFantasyMaterial("Ke", "Trinium", "Trinium", 210.0);
        bindFantasyMaterial("Tr", "Trinium", "Trinium", 210.0);
        bindFantasyMaterial("Sh", "Shadow", "ShadowMetal", 150.0);
        bindFantasyMaterial("Ma", "Magic", "Magnalium", 100.0);
        bindFantasyMaterial("Ms", "Manasteel", "Manasteel", 90.0);
        bindFantasyMaterial("Ts", "TranscendentMetal", "TranscendentMetal", 750.0);
        bindFantasyMaterial("Un", "Universium", "Universium", 900.0);
        bindFantasyMaterial("Om", "Omnium", "Omnium", 1100.0);
        bindFantasyMaterial("Bd", "Bedrockium", "Bedrockium", 600.0);
        bindFantasyMaterial("Ad", "Adamantium", "Adamantium", 300.0);
        bindFantasyMaterial("Vi", "Vibranium", "Vibranium", 280.0);
        bindFantasyMaterial("Dr", "Dragonstone", "Dragonstone", 160.0);
        bindFantasyMaterial("El", "ElvenElementium", "ElvenElementium", 140.0);
        bindFantasyMaterial("Gs", "GaiaSpirit", "GaiaSpirit", 200.0);
        bindFantasyMaterial("Md", "ManaDiamond", "ManaDiamond", 120.0);

        // 按符号长度严格降序排序，保证最长前缀贪婪匹配（例如 "D*" 优先于 "D"，"Nq*" 优先于 "Nq"）
        SORTED_SYMBOLS = new ArrayList<>(SYMBOL_TO_MATERIAL.keySet());
        SORTED_SYMBOLS.sort((a, b) -> Integer.compare(b.length(), a.length()));
    }

    private static void registerBaseElement(String symbol, String name, double weight) {
        Materials mat = Materials.get(name);
        if (mat == null || mat == Materials._NULL) {
            mat = Materials.get(symbol);
        }
        if (mat != null && mat != Materials._NULL) {
            SYMBOL_TO_MATERIAL.put(symbol, mat);
            double mass = mat.getMass() > 0 ? (double) mat.getMass() : weight;
            MATERIAL_WEIGHTS.put(mat, mass);
        }
    }

    private static void registerElementAlias(String symbol, String name, double weight) {
        Materials mat = Materials.get(name);
        if (mat == null || mat == Materials._NULL) {
            mat = Materials.get(symbol);
        }
        if (mat != null && mat != Materials._NULL) {
            SYMBOL_TO_MATERIAL.put(symbol, mat);
            double mass = mat.getMass() > 0 ? (double) mat.getMass() : weight;
            MATERIAL_WEIGHTS.put(mat, mass);
        }
    }

    private static void bindFantasyMaterial(String symbol, String primaryName, String fallbackName,
        double defaultWeight) {
        Materials mat = Materials.get(primaryName);
        if (mat == null || mat == Materials._NULL) {
            mat = Materials.get(fallbackName);
        }
        if (mat == null || mat == Materials._NULL) {
            mat = Materials.get(symbol);
        }
        if (mat != null && mat != Materials._NULL) {
            SYMBOL_TO_MATERIAL.put(symbol, mat);
            double mass = mat.getMass() > 0 ? (double) mat.getMass() : defaultWeight;
            MATERIAL_WEIGHTS.put(mat, mass);
        }
    }

    public static String normalizeFormula(String formula) {
        if (formula == null || formula.isEmpty()) return "";

        // 统一括号
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < formula.length(); i++) {
            char c = formula.charAt(i);
            if (c == '[' || c == '{' || c == '<') {
                sb.append('(');
            } else if (c == ']' || c == '}' || c == '>') {
                sb.append(')');
            } else if (c >= '\u2080' && c <= '\u2089') {
                // Unicode 下标数字 ₀ ~ ₉ 转为 ASCII 数字
                sb.append((char) ('0' + (c - '\u2080')));
            } else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                // 忽略空白
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static Map<Materials, Long> parseFormulaToCounts(String rawFormula) {
        ensureInitialized();
        if (rawFormula == null || rawFormula.isEmpty()) return Collections.emptyMap();

        try {
            Map<Materials, Long> result = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : com.rubenverg.moldraw.core.ChemicalFormula.parse(rawFormula)
                .entrySet()) {
                Materials material = SYMBOL_TO_MATERIAL.getOrDefault(entry.getKey(), UNKNOWN_COMPONENT);
                result.put(material, Math.addExact(result.getOrDefault(material, 0L), entry.getValue()));
            }
            return result;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return Collections.emptyMap();
        }
    }

    public static List<Pair<Materials, Long>> parseToWeightedComponents(String formula) {
        Map<Materials, Long> counts = parseFormulaToCounts(formula);
        if (counts.isEmpty()) return Collections.emptyList();

        List<Pair<Materials, Long>> result = new ArrayList<>();
        for (Map.Entry<Materials, Long> entry : counts.entrySet()) {
            Materials mat = entry.getKey();
            long count = entry.getValue();
            if (count > 0) result.add(new Pair<>(mat, count));
        }
        return result;
    }
}
