package com.rubenverg.moldraw.core;

import java.util.*;
import java.util.regex.Pattern;

import com.google.gson.*;

public final class DisplayNames {

    private static final Pattern CHINESE = Pattern.compile("[\\p{IsHan}]");
    private static final Map<String, String> NAMES = load();

    private DisplayNames() {}

    public static boolean chinese(String text) {
        return text != null && CHINESE.matcher(text)
            .find();
    }

    public static String key(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    public static String reference(String name) {
        String normalized = key(name), direct = NAMES.get(normalized);
        if (direct != null) return direct;
        for (var prefix : Map.of("molten", "熔融", "liquid", "液态", "gas", "气态", "fluid", "", "plasma", "等离子体")
            .entrySet()) if (normalized.startsWith(prefix.getKey())) {
                String base = NAMES.get(
                    normalized.substring(
                        prefix.getKey()
                            .length()));
                if (base != null) return prefix.getValue() + base;
            }
        return "";
    }

    private static Map<String, String> load() {
        try (var stream = DisplayNames.class.getResourceAsStream("/META-INF/gtmoldraw-names-zh.json")) {
            if (stream == null) return Map.of();
            Map<String, String> names = new HashMap<>();
            for (var e : ResourceJson.read(stream)
                .getAsJsonObject()
                .entrySet())
                names.put(
                    e.getKey(),
                    e.getValue()
                        .getAsString());
            return Map.copyOf(names);
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException("Cannot load Chinese display names", invalid);
        }
    }
}
