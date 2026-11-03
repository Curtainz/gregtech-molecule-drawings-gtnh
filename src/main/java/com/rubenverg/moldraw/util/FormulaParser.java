package com.rubenverg.moldraw.util;

import java.util.*;

import gregtech.api.enums.Materials;

public class FormulaParser {

    private static final Map<String, Materials> SYMBOL_TO_MATERIAL = new HashMap<>();
    private static boolean initialized = false;

    // 虚拟材料占位符：表示化学式中的未知或神秘基团
    public static final Materials UNKNOWN_COMPONENT = Materials._NULL;

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        // 1. 现实基础元素注册
        registerBaseElement("H", "Hydrogen");
        registerBaseElement("He", "Helium");
        registerBaseElement("Li", "Lithium");
        registerBaseElement("Be", "Beryllium");
        registerBaseElement("B", "Boron");
        registerBaseElement("C", "Carbon");
        registerBaseElement("N", "Nitrogen");
        registerBaseElement("O", "Oxygen");
        registerBaseElement("F", "Fluorine");
        registerBaseElement("Ne", "Neon");
        registerBaseElement("Na", "Sodium");
        registerBaseElement("Mg", "Magnesium");
        registerBaseElement("Al", "Aluminium");
        registerBaseElement("Si", "Silicon");
        registerBaseElement("P", "Phosphorus");
        registerBaseElement("S", "Sulfur");
        registerBaseElement("Cl", "Chlorine");
        registerBaseElement("Ar", "Argon");
        registerBaseElement("K", "Potassium");
        registerBaseElement("Ca", "Calcium");
        registerBaseElement("Sc", "Scandium");
        registerBaseElement("Ti", "Titanium");
        registerBaseElement("V", "Vanadium");
        registerBaseElement("Cr", "Chrome");
        registerBaseElement("Mn", "Manganese");
        registerBaseElement("Fe", "Iron");
        registerBaseElement("Co", "Cobalt");
        registerBaseElement("Ni", "Nickel");
        registerBaseElement("Cu", "Copper");
        registerBaseElement("Zn", "Zinc");
        registerBaseElement("Ga", "Gallium");
        registerBaseElement("Ge", "Germanium");
        registerBaseElement("As", "Arsenic");
        registerBaseElement("Se", "Selenium");
        registerBaseElement("Br", "Bromine");
        registerBaseElement("Kr", "Krypton");
        registerBaseElement("Rb", "Rubidium");
        registerBaseElement("Sr", "Strontium");
        registerBaseElement("Y", "Yttrium");
        registerBaseElement("Zr", "Zirconium");
        registerBaseElement("Nb", "Niobium");
        registerBaseElement("Mo", "Molybdenum");
        registerBaseElement("Tc", "Technetium");
        registerBaseElement("Ru", "Ruthenium");
        registerBaseElement("Rh", "Rhodium");
        registerBaseElement("Pd", "Palladium");
        registerBaseElement("Ag", "Silver");
        registerBaseElement("Cd", "Cadmium");
        registerBaseElement("In", "Indium");
        registerBaseElement("Sn", "Tin");
        registerBaseElement("Sb", "Antimony");
        registerBaseElement("Te", "Tellurium");
        registerBaseElement("I", "Iodine");
        registerBaseElement("Xe", "Xenon");
        registerBaseElement("Cs", "Caesium");
        registerBaseElement("Ba", "Barium");
        registerBaseElement("La", "Lanthanum");
        registerBaseElement("Ce", "Cerium");
        registerBaseElement("Pr", "Praseodymium");
        registerBaseElement("Nd", "Neodymium");
        registerBaseElement("Sm", "Samarium");
        registerBaseElement("Eu", "Europium");
        registerBaseElement("Gd", "Gadolinium");
        registerBaseElement("Tb", "Terbium");
        registerBaseElement("Dy", "Dysprosium");
        registerBaseElement("Ho", "Holmium");
        registerBaseElement("Er", "Erbium");
        registerBaseElement("Tm", "Thulium");
        registerBaseElement("Yb", "Ytterbium");
        registerElementAlias("Lu", "Lutetium");
        registerElementAlias("Hf", "Hafnium");
        registerElementAlias("Ta", "Tantalum");
        registerBaseElement("W", "Tungsten");
        registerElementAlias("Re", "Rhenium");
        registerBaseElement("Os", "Osmium");
        registerBaseElement("Ir", "Iridium");
        registerBaseElement("Pt", "Platinum");
        registerBaseElement("Au", "Gold");
        registerBaseElement("Hg", "Mercury");
        registerElementAlias("Tl", "Thallium");
        registerBaseElement("Pb", "Lead");
        registerBaseElement("Bi", "Bismuth");
        registerElementAlias("Po", "Polonium");
        registerElementAlias("Rn", "Radon");
        registerElementAlias("Ra", "Radium");
        registerElementAlias("Ac", "Actinium");
        registerBaseElement("Th", "Thorium");
        registerElementAlias("Pa", "Protactinium");
        registerBaseElement("U", "Uranium");
        registerElementAlias("Np", "Neptunium");
        registerBaseElement("Pu", "Plutonium");
        registerBaseElement("Am", "Americium");

        registerBaseElement("D", "Deuterium");
        registerBaseElement("T", "Tritium");

        // 2. GTNH 幻想/虚构与魔法元素精确绑定
        bindFantasyMaterial("D*", "DraconiumAwakened", "AwakenedDraconium");
        bindFantasyMaterial("If*", "Infinity", "InfinityCatalyst");
        bindFantasyMaterial("If", "Infinity", "Infinity");
        bindFantasyMaterial("Nq*", "Naquadria", "Naquadria");
        bindFantasyMaterial("Nq+", "NaquadahEnriched", "NaquadahEnriched");
        bindFantasyMaterial("Nq", "Naquadah", "Naquadah");
        bindFantasyMaterial("Nt", "Neutronium", "CosmicNeutronium");
        bindFantasyMaterial("Sp", "Spacetime", "Spacetime");
        bindFantasyMaterial("Tn", "Tritanium", "Tritanium");
        bindFantasyMaterial("Ke", "Trinium", "Trinium");
        bindFantasyMaterial("Tr", "Trinium", "Trinium");
        bindFantasyMaterial("Sh", "Shadow", "ShadowMetal");
        bindFantasyMaterial("Ma", "Magic", "Magnalium");
        bindFantasyMaterial("Ms", "Manasteel", "Manasteel");
        bindFantasyMaterial("Ts", "TranscendentMetal", "TranscendentMetal");
        bindFantasyMaterial("Un", "Universium", "Universium");
        bindFantasyMaterial("Om", "Omnium", "Omnium");
        bindFantasyMaterial("Bd", "Bedrockium", "Bedrockium");
        bindFantasyMaterial("Ad", "Adamantium", "Adamantium");
        bindFantasyMaterial("Vi", "Vibranium", "Vibranium");
        bindFantasyMaterial("Dr", "Dragonstone", "Dragonstone");
        bindFantasyMaterial("El", "ElvenElementium", "ElvenElementium");
        bindFantasyMaterial("Gs", "GaiaSpirit", "GaiaSpirit");
        bindFantasyMaterial("Md", "ManaDiamond", "ManaDiamond");

    }

    private static void registerBaseElement(String symbol, String name) {
        Materials mat = Materials.get(name);
        if (mat == null || mat == Materials._NULL) {
            mat = Materials.get(symbol);
        }
        if (mat != null && mat != Materials._NULL) {
            SYMBOL_TO_MATERIAL.put(symbol, mat);
        }
    }

    private static void registerElementAlias(String symbol, String name) {
        Materials mat = Materials.get(name);
        if (mat == null || mat == Materials._NULL) {
            mat = Materials.get(symbol);
        }
        if (mat != null && mat != Materials._NULL) {
            SYMBOL_TO_MATERIAL.put(symbol, mat);
        }
    }

    private static void bindFantasyMaterial(String symbol, String primaryName, String fallbackName) {
        Materials mat = Materials.get(primaryName);
        if (mat == null || mat == Materials._NULL) {
            mat = Materials.get(fallbackName);
        }
        if (mat == null || mat == Materials._NULL) {
            mat = Materials.get(symbol);
        }
        if (mat != null && mat != Materials._NULL) {
            SYMBOL_TO_MATERIAL.put(symbol, mat);
        }
    }

    public static Materials materialForSymbol(String symbol) {
        ensureInitialized();
        return SYMBOL_TO_MATERIAL.get(symbol);
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

}
