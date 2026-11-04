package com.rubenverg.moldraw.core;

/** Portable display text. Unknown GT glyphs remain explicit instead of becoming replacement characters. */
public final class FormulaGlyphs {

    private FormulaGlyphs() {}

    public static String display(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\uE01A') out.append('₀');
            else if (c >= '\uE010' && c <= '\uE019') out.append("⁰¹²³⁴⁵⁶⁷⁸⁹".charAt(c - '\uE010'));
            else if (c == '\uE020') out.append("[未知计量]");
            else if (c >= '\uE000' && c <= '\uE005')
                out.append(new String[] { "[风]", "[地]", "[火]", "[水]", "[混沌]", "[秩序]" }[c - '\uE000']);
            else if (c >= '\uE000' && c <= '\uF8FF') out.append(String.format("[U+%04X]", (int) c));
            else out.append(c);
        }
        return out.toString();
    }

    public static String escapedRaw(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray())
            if (c >= '\uE000' && c <= '\uF8FF') out.append(String.format("\\u%04X", (int) c));
            else out.append(c);
        return out.toString();
    }
}
