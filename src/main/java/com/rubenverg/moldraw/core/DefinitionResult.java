package com.rubenverg.moldraw.core;

import java.util.List;

public record DefinitionResult<T> (MaterialDefinition.State state, T value, String source, List<String> diagnostics) {

    public DefinitionResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public static <T> DefinitionResult<T> absent() {
        return new DefinitionResult<>(MaterialDefinition.State.ABSENT, null, "", List.of());
    }
}
