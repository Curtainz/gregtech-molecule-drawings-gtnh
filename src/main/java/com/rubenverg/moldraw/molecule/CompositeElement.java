package com.rubenverg.moldraw.molecule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import java.util.stream.IntStream;

public interface CompositeElement<This extends CompositeElement<?>> extends MoleculeElement<This> {

    Collection<MoleculeElement<?>> children();

    default Collection<MoleculeElement<?>> flatChildren() {
        final List<MoleculeElement<?>> children = new ArrayList<>();
        for (final var child : children())
            if (child instanceof CompositeElement<?>composite) children.addAll(composite.flatChildren());
            else children.add(child);
        return children;
    }

    @Override
    default int[] coveredAtoms() {
        final IntStream.Builder covered = IntStream.builder();
        for (final var child : children()) for (final var atom : child.coveredAtoms()) covered.add(atom);
        return covered.build()
            .toArray();
    }

    @Override
    default void beforeAdd(Molecule to) {
        for (final var child : children()) child.beforeAdd(to);
    }
}
