package com.rubenverg.moldraw.core;

import java.util.Map;

/** Parser examples for regression testing, NOT a runtime formula registry or whitelist. No Minecraft/GL needed. */
public final class ChemicalFormulaRegression {

    public static void main(String[] args) {
        expect("H₂O", Map.of("H", 2L, "O", 1L));
        expect("Al(OH)3", Map.of("Al", 1L, "O", 3L, "H", 3L));
        expect("K4[Fe(CN)6]", Map.of("K", 4L, "Fe", 1L, "C", 6L, "N", 6L));
        expect("Co", Map.of("Co", 1L));
        expect("CO", Map.of("C", 1L, "O", 1L));
        expect("D2O", Map.of("D", 2L, "O", 1L));
        for (String invalid : new String[] { "", "H0", "(OH", "H2O)", "()", "H?", "(H999999999999999999)99" }) {
            try {
                ChemicalFormula.parse(invalid);
                throw new AssertionError("Accepted " + invalid);
            } catch (IllegalArgumentException | ArithmeticException expected) {}
        }
        System.out.println("Chemical formula regression: 13 scenarios passed");
    }

    private static void expect(String formula, Map<String, Long> expected) {
        Map<String, Long> actual = ChemicalFormula.parse(formula);
        if (!actual.equals(expected)) throw new AssertionError(formula + ": " + actual);
    }
}
