package com.rubenverg.moldraw.molecule;

import java.util.Arrays;
import java.util.List;

/** Check atom order and relabeling after replacing primitive collections with JDK collections. */
public final class MoleculeCollectionsRegression {

    public static void main(String[] args) {
        Molecule nested = new Molecule().addNoTransform(new Indices(9, 3));
        Molecule molecule = new Molecule().addNoTransform(nested)
            .addNoTransform(new Indices(3, 7));
        expect(molecule.coveredAtoms(), 9, 3, 3, 7);
        expect(
            molecule.subset(9, 3, 7)
                .coveredAtoms(),
            0,
            1,
            1,
            2);
        expect(
            molecule.subset(3, 7)
                .coveredAtoms(),
            0,
            1);
        expect(
            molecule.subset()
                .coveredAtoms());
        expect(molecule.coveredAtoms(), 9, 3, 3, 7);
        molecule.spinGroups(List.of(0.25f, 0.5f));
        if (!molecule.spinGroups()
            .equals(List.of(0.25f, 0.5f))) throw new AssertionError("spin groups changed");
        System.out.println("Molecule collection regression: 6 scenarios passed");
    }

    private static void expect(int[] actual, int... expected) {
        if (!Arrays.equals(actual, expected)) throw new AssertionError(Arrays.toString(actual));
    }

    private record Indices(int... coveredAtoms) implements MoleculeElement<Indices> {

        @Override
        public String type() {
            return "test";
        }

        @Override
        public Indices replaceInOrder(int[] indices) {
            return new Indices(indices);
        }
    }
}
