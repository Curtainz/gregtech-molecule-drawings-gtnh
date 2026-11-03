package com.rubenverg.moldraw.component;

import com.rubenverg.moldraw.core.*;

public final class MoleculeRenderer extends LayoutRenderer {

    public MoleculeRenderer(MoleculeScene scene, TextMeasure measure, int scale, int color) {
        super(new MoleculeLayoutBuilder(scene, scale, color).build(measure));
    }
}
