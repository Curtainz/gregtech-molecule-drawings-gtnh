package com.rubenverg.moldraw.core;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.google.gson.*;

/** Bound resource size and JSON depth before the legacy recursive Gson parser is invoked. */
public final class ResourceJson {

    private ResourceJson() {}

    public static JsonElement read(InputStream input) throws IOException {
        byte[] data = input.readNBytes(2_097_153);
        if (data.length > 2_097_152) throw new IOException("Resource exceeds 2 MiB");
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
