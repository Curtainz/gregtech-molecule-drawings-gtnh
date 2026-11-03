package com.rubenverg.moldraw.core;

import java.math.BigInteger;
import java.util.*;

import com.google.gson.*;
import com.rubenverg.moldraw.core.MaterialDefinition.*;

/** Compatibility decoder for all existing composition JSON shapes. IDs are resolved by the GT adapter. */
public final class LegacyComposition {

    private LegacyComposition() {}

    public static DefinitionResult<List<Component>> decode(JsonElement json, String source) {
        try {
            JsonObject obj = json.getAsJsonObject();
            if (obj.has("disabled") && obj.get("disabled")
                .getAsBoolean())
                return new DefinitionResult<>(State.DISABLED, null, source, List.of("DISABLED:" + source));
            if (obj.has("derive") && obj.get("derive")
                .getAsBoolean()) return new DefinitionResult<>(State.PRESENT, List.of(), source, List.of());
            List<Component> components = new ArrayList<>();
            for (JsonElement value : obj.getAsJsonArray("components")) {
                String id;
                BigInteger amount = BigInteger.ONE;
                if (value.isJsonPrimitive()) id = value.getAsString();
                else if (value.isJsonArray()) {
                    JsonArray pair = value.getAsJsonArray();
                    if (pair.size() != 2) throw new IllegalArgumentException("Expected material/count pair");
                    id = pair.get(0)
                        .getAsString();
                    amount = pair.get(1)
                        .getAsBigDecimal()
                        .toBigIntegerExact();
                } else {
                    JsonObject component = value.getAsJsonObject();
                    id = component.get("material")
                        .getAsString();
                    if (component.has("amount")) amount = component.get("amount")
                        .getAsBigDecimal()
                        .toBigIntegerExact();
                }
                if (id.isBlank()) throw new IllegalArgumentException("Empty material id");
                components.add(new Component(id, amount));
            }
            if (components.isEmpty()) throw new IllegalArgumentException("Empty components");
            return new DefinitionResult<>(State.PRESENT, List.copyOf(components), source, List.of());
        } catch (RuntimeException invalid) {
            return new DefinitionResult<>(
                State.INVALID,
                null,
                source,
                List.of("INVALID_COMPOSITION:" + source + ":" + invalid.getMessage()));
        }
    }
}
