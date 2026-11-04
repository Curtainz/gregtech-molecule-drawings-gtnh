package com.rubenverg.moldraw.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fluids.FluidStack;

import com.rubenverg.moldraw.MolDraw;

/** Optional ModularUI 1 bridge: reads only the hovered fluid slot through public APIs. */
public final class HoveredFluid {

    private static final java.util.Set<Class<?>> WARNED = new java.util.HashSet<>();

    private HoveredFluid() {}

    public static FluidStack find(GuiContainer gui) {
        if (gui == null || !MolDraw.isClientThread()) return null;
        if (!inherits(gui.getClass(), "com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui")) return null;
        try {
            Object cursor = gui.getClass()
                .getMethod("getCursor")
                .invoke(gui);
            Object widget = cursor.getClass()
                .getMethod("getHovered")
                .invoke(cursor);
            if (widget == null
                || !inherits(widget.getClass(), "com.gtnewhorizons.modularui.common.widget.FluidSlotWidget"))
                return null;
            Object content = widget.getClass()
                .getMethod("getContent")
                .invoke(widget);
            return content instanceof FluidStack stack ? stack : null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (WARNED.add(gui.getClass())) MolDraw.LOGGER.warn("ModularUI hovered fluid adapter unavailable", failure);
            return null;
        }
    }

    private static boolean inherits(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) if (c.getName()
            .equals(name)) return true;
        return false;
    }
}
