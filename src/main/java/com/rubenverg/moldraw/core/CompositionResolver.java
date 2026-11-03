package com.rubenverg.moldraw.core;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

/** Resolve one node at a time; a child always receives precisely its parent's allocated share. */
public final class CompositionResolver {

    public record Result(Map<String, Fraction> shares, List<String> diagnostics, boolean valid) {

        public Result {
            shares = Collections.unmodifiableMap(new LinkedHashMap<>(shares));
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private final Function<String, MaterialDefinition> definitions;

    public CompositionResolver(Function<String, MaterialDefinition> definitions) {
        this.definitions = definitions;
    }

    public Result resolve(String id, boolean mass, boolean recursive) {

        Map<String, Fraction> shares = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        boolean valid = expand(
            id,
            Fraction.ONE,
            mass,
            recursive,
            new HashSet<>(),
            0,
            shares,
            diagnostics,
            new int[] { 10_000 });
        return new Result(valid ? shares : Map.of(), diagnostics, valid);
    }

    private boolean expand(String id, Fraction share, boolean mass, boolean recursive, Set<String> path, int depth,
        Map<String, Fraction> out, List<String> diagnostics, int[] remaining) {
        if (--remaining[0] < 0) {
            diagnostics.add("WORK_LIMIT:" + id);
            return false;
        }
        MaterialDefinition node = definitions.apply(id);
        if (node == null) {
            diagnostics.add("ABSENT:" + id);
            leaf(out, id, share);
            return true;
        }
        diagnostics.addAll(node.diagnostics());
        if (node.state() == MaterialDefinition.State.DISABLED) {
            diagnostics.add("DISABLED:" + id);
            return false;
        }
        if (depth >= 32 || path.contains(id)
            || node.state() == MaterialDefinition.State.INVALID
            || node.components()
                .isEmpty()) {
            if (depth >= 32) diagnostics.add("DEPTH_LIMIT:" + id);
            else if (path.contains(id)) diagnostics.add("CYCLE:" + id);
            leaf(out, id, share);
            return true;
        }
        path.add(id);
        try {
            List<Fraction> weights = new ArrayList<>();
            Fraction total = Fraction.ZERO;
            for (var component : node.components()) {
                Fraction weight = new Fraction(component.amount(), BigInteger.ONE);
                if (mass) {
                    MaterialDefinition child = definitions.apply(component.id());
                    if (child == null || child.mass() == null
                        || child.mass()
                            .compareTo(Fraction.ZERO) <= 0) {
                        diagnostics.add("MISSING_MASS:" + component.id());
                        return false;
                    }
                    weight = weight.multiply(child.mass());
                }

                weights.add(weight);
                total = total.add(weight);
            }
            for (int i = 0; i < weights.size(); i++) {
                String child = node.components()
                    .get(i)
                    .id();
                Fraction allocated = share.multiply(
                    weights.get(i)
                        .divide(total));
                if (recursive) {
                    if (!expand(child, allocated, mass, true, path, depth + 1, out, diagnostics, remaining))
                        return false;
                } else leaf(out, child, allocated);
            }
            return true;
        } finally {
            path.remove(id);
        }
    }

    private static void leaf(Map<String, Fraction> out, String id, Fraction share) {
        out.merge(id, share, Fraction::add);
    }

    public static Map<String, Fraction> fold(Map<String, Fraction> shares, int limit, double threshold) {
        if (limit < 1 || !Double.isFinite(threshold) || threshold < 0 || threshold > 100)
            throw new IllegalArgumentException("Invalid folding settings");
        List<Map.Entry<String, Fraction>> sorted = new ArrayList<>(shares.entrySet());
        sorted.sort(
            Map.Entry.<String, Fraction>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry::getKey));
        LinkedHashMap<String, Fraction> result = new LinkedHashMap<>();
        Fraction other = Fraction.ZERO;
        var decimal = java.math.BigDecimal.valueOf(threshold);
        Fraction cutoff = decimal.scale()
            >= 0 ? new Fraction(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale())
                .multiply(BigInteger.valueOf(100)))
                : new Fraction(
                    decimal.unscaledValue()
                        .multiply(BigInteger.TEN.pow(-decimal.scale())),
                    BigInteger.valueOf(100));
        for (var e : sorted) {
            if (limit == 1 || e.getValue()
                .compareTo(cutoff) < 0) other = other.add(e.getValue());
            else result.put(e.getKey(), e.getValue());
        }
        while (result.size() + (other.compareTo(Fraction.ZERO) > 0 ? 1 : 0) > limit) {
            String last = new ArrayList<>(result.keySet()).getLast();
            other = other.add(result.remove(last));
        }
        if (other.compareTo(Fraction.ZERO) > 0) result.put("gtmoldraw:other", other);
        return Collections.unmodifiableMap(result);
    }
}
