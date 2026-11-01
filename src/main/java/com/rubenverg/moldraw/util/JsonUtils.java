package com.rubenverg.moldraw.util;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public final class JsonUtils {

    private JsonUtils() {}

    public static List<JsonElement> toList(JsonArray array) {
        if (array == null) return List.of();
        List<JsonElement> list = new ArrayList<>(array.size());
        for (JsonElement e : array) {
            list.add(e);
        }
        return list;
    }
}
