package com.rubenverg.moldraw.util;

import java.io.Serializable;

public record Pair<A, B> (A first, B second) implements Serializable {

    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    public A getA() {
        return first;
    }

    public B getB() {
        return second;
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }
}
