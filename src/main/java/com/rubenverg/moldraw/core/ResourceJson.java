package com.rubenverg.moldraw.core;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.google.gson.*;

/** Bound resource size and JSON depth before the legacy recursive Gson parser is invoked. */
public final class ResourceJson {

    private ResourceJson() {}

    public static JsonElement read(InputStream input) throws IOException {
        return read(input, 2_097_152);
    }

    public static JsonElement read(InputStream input, int limit) throws IOException {
        if (limit < 1 || limit > 67_108_864) throw new IllegalArgumentException("Invalid JSON size limit");
        byte[] data = input.readNBytes(limit + 1);
        if (data.length > limit) throw new IOException("JSON exceeds size limit " + limit);
        String text = new String(data, StandardCharsets.UTF_8);
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (char c : text.toCharArray()) {
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 64) throw new IOException("JSON nesting exceeds 64");
            } else if (c == '}' || c == ']') depth--;
        }
        return new JsonParser().parse(text);
    }
}
