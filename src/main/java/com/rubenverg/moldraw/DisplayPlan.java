package com.rubenverg.moldraw;

import com.rubenverg.moldraw.component.ITooltipDrawable;

import gregtech.api.enums.Materials;

public class DisplayPlan {

    public enum Type {
        NONE,
        MOLECULE,
        ALLOY
    }

    public static final DisplayPlan EMPTY = new DisplayPlan(Type.NONE, null, null, null);

    private final Type type;
    private final ITooltipDrawable drawable;
    private final String formula;
    private final Materials material;

    public DisplayPlan(Type type, ITooltipDrawable drawable, String formula, Materials material) {
        this.type = type != null ? type : Type.NONE;
        this.drawable = drawable;
        this.formula = formula;
        this.material = material;
    }

    public Type getType() {
        return type;
    }

    public ITooltipDrawable getDrawable() {
        return drawable;
    }

    public String getFormula() {
        return formula;
    }

    public Materials getMaterial() {
        return material;
    }

    public boolean isValid() {
        return type != Type.NONE && drawable != null;
    }
}
