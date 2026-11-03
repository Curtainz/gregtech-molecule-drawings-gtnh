package com.rubenverg.moldraw.core.pack;

import java.math.BigDecimal;
import java.util.*;

import com.google.gson.*;

/** Comment-aware canonical formatting; the input is validated before any file is written. */
public final class JsoncFormatter {

    private final Jsonc.Document document;
    private final ContractSchema contract;
    private final Map<String, List<String>> before = new HashMap<>(), after = new HashMap<>(),
        closing = new HashMap<>();
    private final StringBuilder out = new StringBuilder();

    private JsoncFormatter(Jsonc.Document document, ContractSchema contract) {
        this.document = document;
        this.contract = contract;
        comments();
    }

    public static String format(Jsonc.Document document, ContractSchema schema) {
        schema.validate(document);
        JsoncFormatter formatter = new JsoncFormatter(document, schema);
        formatter.prefix("", 0);
        formatter.node(document.raw(), schema.root(), "", 0);
        formatter.suffix("", 0);
        formatter.out.append('\n');
        return formatter.out.toString();
    }

    private void comments() {
        var spans = document.spans();
        for (var token : document.tokens()) if (token.kind()
            .equals("comment")) {
                String previous = null;
                int previousEnd = -1;
                for (var e : spans.entrySet()) if (e.getValue()
                    .end() <= token.start()
                    && (e.getValue()
                        .end() > previousEnd
                        || (e.getValue()
                            .end() == previousEnd
                            && (previous == null || e.getKey()
                                .length() < previous.length())))) {
                                    previous = e.getKey();
                                    previousEnd = e.getValue()
                                        .end();
                                }
                if (previous != null && document.text()
                    .substring(previousEnd, token.start())
                    .indexOf('\n') < 0) {
                    after.computeIfAbsent(previous, k -> new ArrayList<>())
                        .add(token.raw());
                    continue;
                }
                String container = null;
                int range = Integer.MAX_VALUE;
                for (var e : spans.entrySet()) if (e.getValue()
                    .start() < token.start()
                    && e.getValue()
                        .end() > token.end()
                    && e.getValue()
                        .end()
                        - e.getValue()
                            .start()
                        < range) {
                            container = e.getKey();
                            range = e.getValue()
                                .end()
                                - e.getValue()
                                    .start();
                        }
                String next = null;
                int nextStart = Integer.MAX_VALUE;
                for (var e : spans.entrySet()) {
                    int start = document.starts()
                        .getOrDefault(
                            e.getKey(),
                            e.getValue()
                                .start());
                    if (start >= token.end() && (container == null || start < spans.get(container)
                        .end())
                        && (start < nextStart || (start == nextStart && (next == null || e.getKey()
                            .length() < next.length())))) {
                        next = e.getKey();
                        nextStart = start;
                    }
                }
                if (next != null) before.computeIfAbsent(next, k -> new ArrayList<>())
                    .add(token.raw());
                else if (container != null) closing.computeIfAbsent(container, k -> new ArrayList<>())
                    .add(token.raw());
                else after.computeIfAbsent("", k -> new ArrayList<>())
                    .add(token.raw());
            }
    }

    private void indent(int depth) {
        out.append("  ".repeat(depth));
    }

    private List<String> lines(String text) {
        return Arrays.stream(
            text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .split("\n", -1))
            .map(String::strip)
            .toList();
    }

    private void prefix(String pointer, int depth) {
        for (String comment : before.getOrDefault(pointer, List.of())) for (String line : lines(comment)) {
            indent(depth);
            out.append(line)
                .append('\n');
        }
    }

    private void dangling(String pointer, int depth) {
        for (String comment : closing.getOrDefault(pointer, List.of())) for (String line : lines(comment)) {
            indent(depth);
            out.append(line)
                .append('\n');
        }
    }

    private void suffix(String pointer, int depth) {
        for (String comment : after.getOrDefault(pointer, List.of())) {
            var lines = lines(comment);
            out.append(' ')
                .append(lines.getFirst());
            for (int i = 1; i < lines.size(); i++) {
                out.append('\n');
                indent(depth);
                out.append(lines.get(i));
            }
        }
    }

    private void node(JsonElement value, JsonObject input, String pointer, int depth) {
        JsonObject schema = contract.branch(input, value);
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject(),
                properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            for (var p : properties.entrySet()) if (object.has(p.getKey())) keys.add(p.getKey());
            object.entrySet()
                .stream()
                .map(Map.Entry::getKey)
                .sorted()
                .forEach(keys::add);
            if (keys.isEmpty() && closing.getOrDefault(pointer, List.of())
                .isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int i = 0;
            for (String key : keys) {
                String child = Jsonc.child(pointer, key);
                prefix(child, depth + 1);
                indent(depth + 1);
                out.append(quote(key))
                    .append(": ");
                JsonObject childSchema = properties.has(key) ? properties.getAsJsonObject(key)
                    : schema.has("additionalProperties") && schema.get("additionalProperties")
                        .isJsonObject() ? schema.getAsJsonObject("additionalProperties") : new JsonObject();
                node(object.get(key), childSchema, child, depth + 1);
                if (++i < keys.size()) out.append(',');
                suffix(child, depth + 1);
                out.append('\n');
            }
            dangling(pointer, depth + 1);
            indent(depth);
            out.append('}');
        } else if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray(),
                prefix = schema.has("prefixItems") ? schema.getAsJsonArray("prefixItems") : new JsonArray();
            if (array.size() == 0 && closing.getOrDefault(pointer, List.of())
                .isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int i = 0; i < array.size(); i++) {
                String child = Jsonc.child(pointer, String.valueOf(i));
                prefix(child, depth + 1);
                indent(depth + 1);
                JsonObject item = i < prefix.size() ? prefix.get(i)
                    .getAsJsonObject()
                    : schema.has("items") && schema.get("items")
                        .isJsonObject() ? schema.getAsJsonObject("items") : new JsonObject();
                node(array.get(i), item, child, depth + 1);
                if (i + 1 < array.size()) out.append(',');
                suffix(child, depth + 1);
                out.append('\n');
            }
            dangling(pointer, depth + 1);
            indent(depth);
            out.append(']');
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive()
            .isString()) out.append(quote(value.getAsString()));
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive()
            .isNumber()) {
                BigDecimal number = value.getAsBigDecimal()
                    .stripTrailingZeros();
                out.append(number.signum() == 0 ? "0" : number.toString());
            } else out.append(value);
    }

    public static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32 || (Character.isSurrogate(c)
                        && !(Character.isHighSurrogate(c) && i + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(i + 1)))
                        && !(Character.isLowSurrogate(c) && i > 0 && Character.isHighSurrogate(value.charAt(i - 1)))))
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"')
            .toString();
    }
}
