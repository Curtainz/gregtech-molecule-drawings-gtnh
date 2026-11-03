package com.rubenverg.moldraw.core;

/** Text matching only; never assigns a structure or material identity. */
public final class FormulaText {

    private FormulaText() {}

    public static boolean applyOverride(java.util.List<String> lines, String formula, String original) {
        if (formula == null || formula.isEmpty()) return false;
        String wanted = normalizeFormula(formula), old = normalizeFormula(original);
        int originalLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) == null) continue;
            String clean = normalizeFormula(
                lines.get(i)
                    .replaceAll("(?i)§[0-9a-fk-or]", ""));
            if (clean.equals(wanted)) return false;
            if (!old.isEmpty() && clean.equals(old)) originalLine = i;
        }
        if (originalLine >= 0) lines.set(originalLine, "§7" + formula);
        else lines.add("§7" + formula);
        return true;
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
