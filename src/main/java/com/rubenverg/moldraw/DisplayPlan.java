package com.rubenverg.moldraw;

import com.rubenverg.moldraw.component.LayoutRenderer;
import com.rubenverg.moldraw.core.*;

/** Client binding of frozen presentation data to an already measured immutable layout. */
public final class DisplayPlan {

    public enum Type {
        NONE,
        MOLECULE,
        ALLOY
    }

    public static final DisplayPlan EMPTY = new DisplayPlan(
        new PresentationData(PresentationData.Kind.NONE, null),
        null);
    private final PresentationData data;
    private final LayoutRenderer drawable;

    public DisplayPlan(PresentationData data, LayoutRenderer drawable) {
        this.data = data;
        this.drawable = drawable;
    }

    public Type getType() {
        return Type.valueOf(
            data.kind()
                .name());
    }

    public LayoutRenderer getDrawable() {
        return drawable;
    }

    public String getFormula() {
        return data.material() == null ? null
            : data.material()
                .formula();
    }

    public PresentationData data() {
        return data;
    }

    public TooltipLayout layout() {
        return drawable == null ? null : drawable.layout();
    }

    public boolean isValid() {
        return getType() != Type.NONE && drawable != null && layout().width() > 0 && layout().height() > 0;
    }
}
