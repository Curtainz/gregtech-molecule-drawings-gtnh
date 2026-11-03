package com.rubenverg.moldraw;

import java.util.*;

import com.rubenverg.moldraw.core.MoleculeScene;
import com.rubenverg.moldraw.molecule.*;

/** Client adapter freezes legacy resource geometry and its resolved colors. */
public final class MoleculeSnapshots {

    private MoleculeSnapshots() {}

    private static MoleculeScene.Label label(Element.Counted label) {
        return new MoleculeScene.Label(
            label.toString(),
            label.element().invisible,
            MoleculeColorize.symbolPalette()
                .getOrDefault(label.element().symbol, MoleculeColorize.configColor(null)));
    }

    public static MoleculeScene freeze(Molecule molecule) {
        List<MoleculeScene.Shape> shapes = new ArrayList<>();
        for (var shape : molecule.flatChildren()) {
            if (shape instanceof Atom a) shapes.add(
                new MoleculeScene.Atom(
                    a.index(),
                    label(a.element()),
                    a.above()
                        .map(MoleculeSnapshots::label),
                    a.right()
                        .map(MoleculeSnapshots::label),
                    a.below()
                        .map(MoleculeSnapshots::label),
                    a.left()
                        .map(MoleculeSnapshots::label),
                    a.position().x,
                    a.position().y,
                    a.position().z));
            else if (shape instanceof Bond b) shapes.add(
                new MoleculeScene.Bond(
                    b.a(),
                    b.b(),
                    b.centered(),
                    Arrays.stream(b.lines())
                        .map(l -> MoleculeScene.Line.valueOf(l.name()))
                        .toList()));
            else if (shape instanceof Parens p) shapes.add(
                new MoleculeScene.Parens(
                    p.sub(),
                    p.sup(),
                    Arrays.stream(p.atoms())
                        .boxed()
                        .toList()));
            else if (shape instanceof CircleTransformation c) shapes.add(
                new MoleculeScene.CircleTransformation(
                    c.A()
                        .m00(),
                    c.A()
                        .m01(),
                    c.A()
                        .m10(),
                    c.A()
                        .m11(),
                    Arrays.stream(c.atoms())
                        .boxed()
                        .toList()));
            else throw new IllegalArgumentException("Unsupported geometry " + shape.type());
        }
        Set<Integer> ids = new HashSet<>();
        for (var a : shapes) if (a instanceof MoleculeScene.Atom atom && !ids.add(atom.index()))
            throw new IllegalArgumentException("Duplicate atom " + atom.index());
        for (var s : shapes) {
            if (s instanceof MoleculeScene.Bond b && (!ids.contains(b.a()) || !ids.contains(b.b())))
                throw new IllegalArgumentException("Missing bond atom");
            int[] refs = s instanceof MoleculeScene.Parens p ? p.atoms()
                : s instanceof MoleculeScene.CircleTransformation c ? c.atoms() : new int[0];
            for (int ref : refs) if (!ids.contains(ref)) throw new IllegalArgumentException("Missing group atom");
        }
        return new MoleculeScene(shapes);
    }
}
