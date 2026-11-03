package com.rubenverg.moldraw.core.pack;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

import com.google.gson.*;
import com.rubenverg.moldraw.core.ResourceJson;

/** Offline validator for the explicitly audited JSON-Schema vocabulary used by format v1. */
public final class ContractSchema {

    private record Problem(String pointer, String message) {}

    private final JsonObject root;
    private static final Set<String> VOCABULARY = Set.of(
        "$schema",
        "$id",
        "$defs",
        "$ref",
        "title",
        "description",
        "default",
        "type",
        "properties",
        "required",
        "additionalProperties",
        "propertyNames",
        "oneOf",
        "anyOf",
        "allOf",
        "not",
        "if",
        "then",
        "else",
        "const",
        "enum",
        "items",
        "prefixItems",
        "uniqueItems",
        "minItems",
        "maxItems",
        "minProperties",
        "maxProperties",
        "minLength",
        "maxLength",
        "pattern",
        "minimum",
        "maximum",
        "exclusiveMinimum",
        "exclusiveMaximum");

    public ContractSchema(JsonObject schema) {
        root = Jsonc.copy(schema)
            .getAsJsonObject();
        checkVocabulary(root);
    }

    public static ContractSchema bundled(String name) {
        try (var stream = ContractSchema.class
            .getResourceAsStream("/META-INF/gtmoldraw-schema/" + name + ".schema.json")) {
            if (stream == null) throw new PackException("Missing bundled format schema " + name);
            return new ContractSchema(
                ResourceJson.read(stream)
                    .getAsJsonObject());
        } catch (IOException error) {
            throw new PackException("Cannot read schema: " + error.getMessage());
        }
    }

    private void checkVocabulary(JsonObject schema) {
        if (schema.has("$ref") && schema.entrySet()
            .stream()
            .anyMatch(
                e -> !Set.of("$ref", "title", "description")
                    .contains(e.getKey())))
            throw new PackException("Schema ref siblings are not in the audited v1 vocabulary");
        for (var e : schema.entrySet()) {
            String key = e.getKey();
            if (!VOCABULARY.contains(key)) throw new PackException("Unsupported schema keyword: " + key);
            if (Set.of("$defs", "properties")
                .contains(key))
                for (var v : e.getValue()
                    .getAsJsonObject()
                    .entrySet())
                    checkVocabulary(
                        v.getValue()
                            .getAsJsonObject());
            else if (Set.of("oneOf", "anyOf", "allOf", "prefixItems")
                .contains(key))
                for (var v : e.getValue()
                    .getAsJsonArray()) checkVocabulary(v.getAsJsonObject());
            else if (Set.of("items", "additionalProperties", "propertyNames", "not", "if", "then", "else")
                .contains(key)
                && e.getValue()
                    .isJsonObject())
                checkVocabulary(
                    e.getValue()
                        .getAsJsonObject());
        }
    }

    JsonObject resolve(JsonObject schema) {
        if (!schema.has("$ref")) return schema;
        String path = schema.get("$ref")
            .getAsString();
        if (!path.startsWith("#/$defs/") || path.substring(8)
            .contains("/")) throw new PackException("Only local schema definitions are permitted");
        JsonElement target = root.getAsJsonObject("$defs")
            .get(path.substring(8));
        if (target == null) throw new PackException("Unknown local schema ref " + path);
        return target.getAsJsonObject();
    }

    public void validate(Jsonc.Document document) {
        List<Problem> errors = new ArrayList<>();
        check(root, document.raw(), "", errors, 0);
        if (!errors.isEmpty()) {
            Problem e = errors.getFirst();
            throw document.error(e.pointer, e.message);
        }
    }

    public void validate(JsonElement value) {
        List<Problem> errors = new ArrayList<>();
        check(root, value, "", errors, 0);
        if (!errors.isEmpty()) throw new PackException(
            errors.getFirst()
                .toString());
    }

    private static boolean equal(JsonElement a, JsonElement b) {
        if (a.isJsonPrimitive() && b.isJsonPrimitive()
            && a.getAsJsonPrimitive()
                .isNumber()
            && b.getAsJsonPrimitive()
                .isNumber())
            return a.getAsBigDecimal()
                .compareTo(b.getAsBigDecimal()) == 0;
        return a.equals(b);
    }

    private boolean matches(JsonObject schema, JsonElement value, int depth) {
        List<Problem> errors = new ArrayList<>();
        check(schema, value, "", errors, depth);
        return errors.isEmpty();
    }

    private boolean type(String type, JsonElement value) {
        return switch (type) {
            case "null" -> value.isJsonNull();
            case "object" -> value.isJsonObject();
            case "array" -> value.isJsonArray();
            case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive()
                .isString();
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive()
                .isBoolean();
            case "number" -> value.isJsonPrimitive() && value.getAsJsonPrimitive()
                .isNumber();
            case "integer" -> value.isJsonPrimitive() && value.getAsJsonPrimitive()
                .isNumber()
                && value.getAsBigDecimal()
                    .stripTrailingZeros()
                    .scale() <= 0;
            default -> throw new PackException("Unknown schema type " + type);
        };
    }

    private void check(JsonObject input, JsonElement value, String pointer, List<Problem> errors, int depth) {
        if (depth > 128) throw new PackException("Schema traversal budget exceeded");
        JsonObject schema = resolve(input);
        if (schema.has("type")) {
            JsonElement types = schema.get("type");
            boolean valid = false;
            if (types.isJsonArray()) {
                for (var t : types.getAsJsonArray()) valid |= type(t.getAsString(), value);
            } else valid = type(types.getAsString(), value);
            if (!valid) {
                errors.add(new Problem(pointer, "Expected type " + types));
                return;
            }
        }
        if (schema.has("const") && !equal(schema.get("const"), value))
            errors.add(new Problem(pointer, "Expected " + schema.get("const")));
        if (schema.has("enum")) {
            boolean found = false;
            for (var v : schema.getAsJsonArray("enum")) found |= equal(v, value);
            if (!found) errors.add(new Problem(pointer, "Unsupported value " + value));
        }
        for (String combinator : List.of("oneOf", "anyOf")) if (schema.has(combinator)) {
            int matches = 0;
            List<Problem> best = null;
            int bestScore = Integer.MIN_VALUE;
            for (var branch : schema.getAsJsonArray(combinator)) {
                List<Problem> branchErrors = new ArrayList<>();
                JsonObject option = resolve(branch.getAsJsonObject());
                check(option, value, pointer, branchErrors, depth + 1);
                if (branchErrors.isEmpty()) matches++;
                int score = -branchErrors.size();
                if (option.has("type")) {
                    var expected = option.get("type");
                    if (expected.isJsonArray()) {
                        for (var t : expected.getAsJsonArray()) if (type(t.getAsString(), value)) score += 100;
                    } else if (type(expected.getAsString(), value)) score += 100;
                }
                if (value.isJsonObject() && option.has("properties"))
                    for (String discriminator : List.of("type", "module", "source", "kind")) {
                        var property = option.getAsJsonObject("properties")
                            .get(discriminator);
                        var actual = value.getAsJsonObject()
                            .get(discriminator);
                        if (property != null && actual != null
                            && property.isJsonObject()
                            && property.getAsJsonObject()
                                .has("const")
                            && equal(
                                property.getAsJsonObject()
                                    .get("const"),
                                actual))
                            score += 1000;
                    }
                if (best == null || score > bestScore) {
                    best = branchErrors;
                    bestScore = score;
                }
            }
            if (matches == 0) {
                if (best != null && !best.isEmpty()) errors.addAll(best);
                else errors.add(new Problem(pointer, "No matching " + combinator + " form"));
            } else if (combinator.equals("oneOf") && matches != 1)
                errors.add(new Problem(pointer, "Ambiguous schema form"));
        }
        if (schema.has("allOf"))
            for (var v : schema.getAsJsonArray("allOf")) check(v.getAsJsonObject(), value, pointer, errors, depth + 1);
        if (schema.has("not") && matches(schema.getAsJsonObject("not"), value, depth + 1))
            errors.add(new Problem(pointer, "Forbidden field combination"));
        if (schema.has("if")) {
            String branch = matches(schema.getAsJsonObject("if"), value, depth + 1) ? "then" : "else";
            if (schema.has(branch)) check(schema.getAsJsonObject(branch), value, pointer, errors, depth + 1);
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject(),
                properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
            if (schema.has("required"))
                for (var key : schema.getAsJsonArray("required")) if (!object.has(key.getAsString()))
                    errors.add(new Problem(pointer, "Missing field " + key.getAsString()));
            for (var e : object.entrySet()) {
                String child = Jsonc.child(pointer, e.getKey());
                if (schema.has("propertyNames")) check(
                    schema.getAsJsonObject("propertyNames"),
                    new JsonPrimitive(e.getKey()),
                    child,
                    errors,
                    depth + 1);
                if (properties.has(e.getKey()))
                    check(properties.getAsJsonObject(e.getKey()), e.getValue(), child, errors, depth + 1);
                else if (schema.has("additionalProperties")) {
                    var additional = schema.get("additionalProperties");
                    if (additional.isJsonObject())
                        check(additional.getAsJsonObject(), e.getValue(), child, errors, depth + 1);
                    else if (!additional.getAsBoolean()) errors.add(new Problem(child, "Unknown field " + e.getKey()));
                }
            }
            count(
                schema,
                "minProperties",
                "maxProperties",
                object.entrySet()
                    .size(),
                pointer,
                errors);
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray(),
                prefix = schema.has("prefixItems") ? schema.getAsJsonArray("prefixItems") : new JsonArray();
            count(schema, "minItems", "maxItems", array.size(), pointer, errors);
            for (int i = 0; i < array.size(); i++) {
                String child = Jsonc.child(pointer, String.valueOf(i));
                if (i < prefix.size()) check(
                    prefix.get(i)
                        .getAsJsonObject(),
                    array.get(i),
                    child,
                    errors,
                    depth + 1);
                else if (schema.has("items")) {
                    var items = schema.get("items");
                    if (items.isJsonObject()) check(items.getAsJsonObject(), array.get(i), child, errors, depth + 1);
                    else if (!items.getAsBoolean()) errors.add(new Problem(child, "Unexpected array item"));
                }
            }
            if (schema.has("uniqueItems") && schema.get("uniqueItems")
                .getAsBoolean()) {
                Set<String> seen = new HashSet<>();
                for (var e : array)
                    if (!seen.add(e.toString())) errors.add(new Problem(pointer, "Duplicate array value"));
            }
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive()
            .isString()) {
            String text = value.getAsString();
            count(schema, "minLength", "maxLength", text.codePointCount(0, text.length()), pointer, errors);
            if (schema.has("pattern") && !Pattern.compile(
                schema.get("pattern")
                    .getAsString())
                .matcher(text)
                .find()) errors.add(new Problem(pointer, "Invalid identifier/value: " + text));
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive()
            .isNumber()) {
            BigDecimal number = value.getAsBigDecimal();
            for (String bound : List.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum"))
                if (schema.has(bound)) {
                    int comparison = number.compareTo(
                        schema.get(bound)
                            .getAsBigDecimal());
                    boolean invalid = switch (bound) {
                        case "minimum" -> comparison < 0;
                        case "maximum" -> comparison > 0;
                        case "exclusiveMinimum" -> comparison <= 0;
                        default -> comparison >= 0;
                    };
                    if (invalid) errors.add(new Problem(pointer, "Number violates " + bound + " " + schema.get(bound)));
                }
        }
    }

    private void count(JsonObject schema, String min, String max, int size, String pointer, List<Problem> errors) {
        if (schema.has(min) && size < schema.get(min)
            .getAsInt()) errors.add(new Problem(pointer, "Below " + min));
        if (schema.has(max) && size > schema.get(max)
            .getAsInt()) errors.add(new Problem(pointer, "Above " + max));
    }

    JsonObject root() {
        return root;
    }

    JsonObject branch(JsonObject input, JsonElement value) {
        JsonObject schema = resolve(input);
        for (String k : List.of("oneOf", "anyOf")) if (schema.has(k)) for (var b : schema.getAsJsonArray(k))
            if (matches(b.getAsJsonObject(), value, 0)) return branch(b.getAsJsonObject(), value);
        return schema;
    }
}
