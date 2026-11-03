package com.rubenverg.moldraw.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict counting grammar. Unsupported notation is reported instead of silently discarded. */
public final class ChemicalFormula {

    private final String text;
    private int position;

    private ChemicalFormula(String text) {
        this.text = text;
    }

    public static Map<String, Long> parse(String formula) {
        if (formula == null || formula.length() > 4096) throw new IllegalArgumentException("Invalid formula length");
        StringBuilder normalized = new StringBuilder();
        for (char c : formula.toCharArray()) {
            if (Character.isWhitespace(c)) continue;
            normalized.append(c >= '\u2080' && c <= '\u2089' ? (char) ('0' + c - '\u2080') : c);
        }
        ChemicalFormula parser = new ChemicalFormula(normalized.toString());
        Map<String, Long> result = parser.group('\0', 0);
        if (result.isEmpty()) throw new IllegalArgumentException("Empty formula");
        return result;
    }

    private Map<String, Long> group(char closing, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Formula nesting too deep");
        Map<String, Long> result = new LinkedHashMap<>();
        while (position < text.length()) {
            char c = text.charAt(position);
            if (closing != '\0' && c == closing) {
                position++;
                return result;
            }
            Map<String, Long> part;
            if (c == '(' || c == '[' || c == '{') {
                position++;
                part = group(c == '(' ? ')' : c == '[' ? ']' : '}', depth + 1);
                if (part.isEmpty()) throw new IllegalArgumentException("Empty group");
            } else if (c >= 'A' && c <= 'Z') {
                int start = position++;
                while (position < text.length() && text.charAt(position) >= 'a' && text.charAt(position) <= 'z')
                    position++;
                if (position < text.length() && text.charAt(position) == '*') position++;
                part = Map.of(text.substring(start, position), 1L);
            } else throw new IllegalArgumentException("Unsupported formula at " + position);
            long count = number();
            for (Map.Entry<String, Long> entry : part.entrySet()) {
                long value = Math.multiplyExact(entry.getValue(), count);
                result.put(entry.getKey(), Math.addExact(result.getOrDefault(entry.getKey(), 0L), value));
            }
        }
        if (closing != '\0') throw new IllegalArgumentException("Unclosed formula group");
        return result;
    }

    private long number() {
        int start = position;
        while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') position++;
        long value = start == position ? 1 : Long.parseLong(text.substring(start, position));
        if (value <= 0) throw new IllegalArgumentException("Nonpositive count");
        return value;
    }
}
