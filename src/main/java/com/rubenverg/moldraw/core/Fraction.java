package com.rubenverg.moldraw.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** Exact nonnegative shares; no long multiplication or intermediate rounding. */
public record Fraction(BigInteger numerator, BigInteger denominator) implements Comparable<Fraction> {

    public static final Fraction ZERO = of(0), ONE = of(1);

    public Fraction {
        if (numerator.signum() < 0 || denominator.signum() <= 0) throw new IllegalArgumentException("Invalid fraction");
        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd);
        denominator = denominator.divide(gcd);
    }

    public static Fraction of(long value) {
        return new Fraction(BigInteger.valueOf(value), BigInteger.ONE);
    }

    public Fraction add(Fraction b) {
        return new Fraction(
            numerator.multiply(b.denominator)
                .add(b.numerator.multiply(denominator)),
            denominator.multiply(b.denominator));
    }

    public Fraction multiply(Fraction b) {
        return new Fraction(numerator.multiply(b.numerator), denominator.multiply(b.denominator));
    }

    public Fraction divide(Fraction b) {
        return new Fraction(numerator.multiply(b.denominator), denominator.multiply(b.numerator));
    }

    public double value() {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), MathContext.DECIMAL64)
            .doubleValue();
    }

    public int compareTo(Fraction b) {
        return numerator.multiply(b.denominator)
            .compareTo(b.numerator.multiply(denominator));
    }
}
