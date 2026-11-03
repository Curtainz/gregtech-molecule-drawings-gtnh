package com.rubenverg.moldraw.core;

import java.math.BigInteger;
import java.util.List;

/** Immutable adapter boundary: never retains a GregTech material object. */
public record MaterialDefinition(String id, String name, String formula, String structure, Fraction mass, int color,
    State state, Basis basis, List<Component> components, String source, List<String> diagnostics) {

    public enum State {
        ABSENT,
        PRESENT,
        DISABLED,
        INVALID
    }

    public enum Basis {
        STOICHIOMETRIC,
        MIXTURE_PARTS
    }

    public record Component(String id, BigInteger amount) {

        public Component {
            if (amount.signum() <= 0) throw new IllegalArgumentException("Nonpositive amount");
        }
    }

    public MaterialDefinition {
        components = List.copyOf(components);
        diagnostics = List.copyOf(diagnostics);
    }
}
