package com.rubenverg.moldraw.tool;

import java.util.*;

import org.joml.Vector3f;

import com.google.gson.*;
import com.rubenverg.moldraw.core.pack.*;

/** Offline conversion of the actual legacy resource subset; no GT class initialization. */
public final class LegacyGeometry {

    private LegacyGeometry() {}

    static JsonArray array(Object... values) {
        JsonArray result = new JsonArray();
        for (Object value : values) {
            if (value instanceof Number n) result.add(new JsonPrimitive(n));
            else result.add(new JsonPrimitive(value.toString()));
        }
        return result;
    }

    static JsonObject object(String type) {
        JsonObject value = new JsonObject();
        value.addProperty("type", type);
        return value;
    }

    public static JsonObject convert(JsonObject legacy, String id) {
        fields(legacy, "contents");
        JsonObject result = object("molecule");
        result.addProperty("schemaVersion", 1);
        result.addProperty("id", id);
        JsonArray atoms = new JsonArray(), bonds = new JsonArray(), notes = new JsonArray();
        for (JsonElement entry : legacy.getAsJsonArray("contents")) {
            JsonObject c = entry.getAsJsonObject();
            switch (c.get("type")
                .getAsString()) {
                case "atom" -> {
                    fields(c, "type", "index", "element", "x", "y", "right", "left", "above", "below");
                    JsonObject atom = atom(
                        c.get("index")
                            .getAsInt(),
                        c.get("x")
                            .getAsFloat(),
                        c.get("y")
                            .getAsFloat());
                    label(c.get("element"), atom, false);
                    JsonObject labels = new JsonObject();
                    for (String side : List.of("above", "right", "below", "left")) if (c.has(side)) {
                        JsonObject decoration = new JsonObject();
                        label(c.get(side), decoration, true);
                        labels.add(side, decoration);
                    }
                    if (labels.entrySet()
                        .size() > 0) atom.add("labels", labels);
                    atoms.add(atom);
                }
                case "bond" -> {
                    // stereo was ignored by the old Bond.Json adapter. Recorded separately in the report.
                    fields(c, "type", "a", "b", "lines", "centered", "stereo");
                    bonds.add(
                        bond(
                            c.get("a")
                                .getAsInt(),
                            c.get("b")
                                .getAsInt(),
                            c.getAsJsonArray("lines"),
                            c.has("centered") && c.get("centered")
                                .getAsBoolean()));
                }
                case "benzene" -> {
                    fields(c, "type", "indices", "x0", "y0", "x1", "y1", "angle");
                    int[] indices = new int[c.getAsJsonArray("indices")
                        .size()];
                    for (int i = 0; i < indices.length; i++) indices[i] = c.getAsJsonArray("indices")
                        .get(i)
                        .getAsInt();
                    Vector3f[] points = ring(c, indices.length);
                    for (int i = 0; i < indices.length; i++) {
                        JsonObject atom = atom(indices[i], points[i].x, points[i].y);
                        atom.add("element", JsonNull.INSTANCE);
                        atom.addProperty("labelMode", "hidden");
                        atoms.add(atom);
                        bonds.add(
                            bond(
                                indices[i],
                                indices[(i + 1) % indices.length],
                                i % 2 == 0 ? array("solid") : array("solid", "solid"),
                                false));
                    }
                }
                case "parens", "circle" -> {
                    boolean bracket = c.get("type")
                        .getAsString()
                        .equals("parens");
                    fields(
                        c,
                        bracket ? new String[] { "type", "atoms", "sub", "sup" }
                            : new String[] { "type", "atoms", "a00", "a01", "a10", "a11" });
                    JsonObject note = object(bracket ? "bracket" : "ellipse");
                    JsonArray refs = new JsonArray();
                    for (var ref : c.getAsJsonArray("atoms")) refs.add(new JsonPrimitive(atomId(ref.getAsInt())));
                    note.add("atoms", refs);
                    if (bracket) for (String key : List.of("sub", "sup")) {
                        if (c.has(key)) note.addProperty(
                            key,
                            c.get(key)
                                .getAsString());
                    }
                    else note.add(
                        "matrix",
                        array(
                            c.get("a00")
                                .getAsFloat(),
                            c.get("a01")
                                .getAsFloat(),
                            c.get("a10")
                                .getAsFloat(),
                            c.get("a11")
                                .getAsFloat()));
                    notes.add(note);
                }
                default -> throw new PackException("Unsupported legacy shape " + c.get("type"));
            }
        }
        result.add("atoms", atoms);
        // Legacy ring resources can redraw an existing solid stroke. Preserve its raster union,
        // rather than representing the same pair as contradictory chemical bonds.
        JsonArray unique = new JsonArray();
        for (JsonElement value : bonds) {
            JsonObject edge = value.getAsJsonObject();
            boolean redundant = false;
            if (edge.getAsJsonArray("strokes")
                .size() == 1 && edge.getAsJsonArray("strokes")
                    .get(0)
                    .getAsString()
                    .equals("solid")
                && (!edge.has("centered") || !edge.get("centered")
                    .getAsBoolean())) {
                for (JsonElement other : bonds) {
                    var multi = other.getAsJsonObject();
                    var lines = multi.getAsJsonArray("strokes");
                    boolean same = (edge.get("from")
                        .equals(multi.get("from"))
                        && edge.get("to")
                            .equals(multi.get("to")))
                        || (edge.get("from")
                            .equals(multi.get("to"))
                            && edge.get("to")
                                .equals(multi.get("from")));
                    if (same && lines.size() > 1
                        && !multi.has("centered")
                        && lines.get(0)
                            .getAsString()
                            .equals("solid")) {
                        redundant = true;
                        break;
                    }
                }
            }
            if (!redundant) unique.add(value);
        }
        result.add("bonds", unique);
        if (notes.size() > 0) result.add("annotations", notes);
        return result;
    }

    public static Vector3f[] ring(JsonObject c, int count) {
        if (count < 3 || count > 64) throw new PackException("Invalid legacy ring size");
        Vector3f[] points = new Vector3f[count];
        points[0] = new Vector3f(
            c.get("x0")
                .getAsFloat(),
            c.get("y0")
                .getAsFloat(),
            0);
        points[1] = new Vector3f(
            c.get("x1")
                .getAsFloat(),
            c.get("y1")
                .getAsFloat(),
            0);
        Vector3f delta = new Vector3f(points[1]).sub(points[0]);
        float angle = c.get("angle")
            .getAsFloat(), twoPi = (float) (Math.PI * 2);
        for (int i = 2; i < count; i++) {
            Vector3f old = new Vector3f(delta);
            delta.rotateAxis(twoPi / count, 0, 0, 1);
            delta.rotateAxis(angle, old.x, old.y, old.z);
            points[i] = new Vector3f(points[i - 1]).add(delta);
        }
        return points;
    }

    public static String atomId(int index) {
        if (index < 0) throw new PackException("Negative legacy atom index");
        return "a" + index;
    }

    private static JsonObject atom(int id, float x, float y) {
        JsonObject atom = new JsonObject();
        atom.addProperty("id", atomId(id));
        atom.add("position", array(x, y));
        return atom;
    }

    private static JsonObject bond(int a, int b, JsonArray lines, boolean centered) {
        JsonObject bond = new JsonObject();
        bond.addProperty("from", atomId(a));
        bond.addProperty("to", atomId(b));
        bond.add("strokes", lines);
        if (centered) bond.addProperty("centered", true);
        return bond;
    }

    private static void label(JsonElement input, JsonObject output, boolean decoration) {
        int count = 1;
        if (input.isJsonArray()) {
            count = input.getAsJsonArray()
                .get(1)
                .getAsInt();
            input = input.getAsJsonArray()
                .get(0);
        }
        String symbol;
        boolean invisible = false;
        if (input.isJsonObject()) {
            JsonObject o = input.getAsJsonObject();
            fields(o, "symbol", "invisible", "color", "material");
            symbol = o.get("symbol")
                .getAsString();
            invisible = o.has("invisible") && o.get("invisible")
                .getAsBoolean();
            if (o.has("color")) {
                JsonElement color = o.get("color");
                if (color.isJsonObject()) {
                    var colorObject = color.getAsJsonObject();
                    fields(colorObject, "color", "optional");
                    output.addProperty(
                        "optionalColor",
                        !colorObject.has("optional") || colorObject.get("optional")
                            .getAsBoolean());
                    color = colorObject.get("color");
                }
                int argb = color.getAsJsonPrimitive()
                    .isNumber() ? color.getAsInt() : Integer.decode(color.getAsString()) | 0xFF000000;
                output.addProperty("color", String.format(Locale.ROOT, "#%08X", argb));
            }
        } else symbol = input.getAsString();
        String text = symbol;
        if (count != 1) for (char c : Integer.toString(count)
            .toCharArray()) {
                if (c < '0' || c > '9') throw new PackException("Negative legacy label count");
                text += (char) ('₀' + c - '0');
            }
        if (decoration) {
            output.addProperty("text", text);
            if (!symbol.isEmpty()) output.addProperty("element", symbol);
        } else {
            output.add("element", symbol.isEmpty() ? JsonNull.INSTANCE : new JsonPrimitive(symbol));
            output.addProperty("label", text);
            output.addProperty("labelMode", invisible || symbol.isEmpty() ? "hidden" : "symbol");
        }
    }

    static void fields(JsonObject object, String... allowed) {
        Set<String> keys = Set.of(allowed);
        for (var e : object.entrySet())
            if (!keys.contains(e.getKey())) throw new PackException("Unmigrated legacy field: " + e.getKey());
    }
}
