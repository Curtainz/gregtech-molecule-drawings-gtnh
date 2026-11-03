package com.rubenverg.moldraw.core;

import java.util.*;
import java.util.function.Function;

/** Safe equivalent of known GT mass metadata; never substitutes a default element for absent data. */
public final class MassResolver {

    public record Node(Fraction elementMass, Fraction density, List<MaterialDefinition.Component> components,
        boolean average) {

        public Node {
            components = List.copyOf(components);
        }
    }

    public record Result(Fraction mass, List<String> diagnostics) {

        public Result {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private final Function<String, Node> nodes;

    public MassResolver(Function<String, Node> nodes) {
        this.nodes = nodes;
    }

    public Result resolve(String id) {
        List<String> errors = new ArrayList<>();
        return new Result(expand(id, new HashSet<>(), 0, new int[] { 10_000 }, errors), errors);
    }

    private Fraction expand(String id, Set<String> path, int depth, int[] remaining, List<String> errors) {
        if (--remaining[0] < 0 || depth >= 32 || path.contains(id)) {
            errors.add("MASS_CYCLE_OR_LIMIT:" + id);
            return null;
        }
        Node node = nodes.apply(id);
        if (node == null) {
            errors.add("MISSING_MASS:" + id);
            return null;
        }
        if (node.elementMass != null && node.elementMass.compareTo(Fraction.ZERO) > 0) return node.elementMass;
        if (node.components.isEmpty() || node.density == null || node.density.compareTo(Fraction.ZERO) <= 0) {
            errors.add("MISSING_MASS:" + id);
            return null;
        }
        path.add(id);
        try {
            Fraction sum = Fraction.ZERO, total = Fraction.ZERO;
            for (var c : node.components) {
                Fraction child = expand(c.id(), path, depth + 1, remaining, errors);
                if (child == null) return null;
                Fraction count = new Fraction(c.amount(), java.math.BigInteger.ONE);
                sum = sum.add(count.multiply(child));
                total = total.add(count);
            }
            return (node.average ? sum.divide(total) : sum).multiply(node.density);
        } finally {
            path.remove(id);
        }
    }
}
