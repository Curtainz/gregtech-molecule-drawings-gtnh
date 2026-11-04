package com.rubenverg.moldraw.component;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import com.rubenverg.moldraw.MolDraw;

/** Shared GL boundary for NEI and the fallback tooltip path. */
public final class TooltipDrawPass {

    private static final java.util.Map<ITooltipDrawable, Boolean> failed = new java.util.WeakHashMap<>();

    public static void clearFailures() {
        failed.clear();
    }

    public static int failureCount() {
        return failed.size();
    }

    private TooltipDrawPass() {}

    public static void render(ITooltipDrawable drawable, FontRenderer font, int x, int y) {
        if (!MolDraw.isClientThread() || drawable == null || font == null) return;
        if (failed.containsKey(drawable)) return;
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1, 1, 1, 1);
                try {
                    drawable.render(font, x, y);
                } catch (RuntimeException invalidDrawing) {
                    failed.put(drawable, Boolean.TRUE);
                    MolDraw.LOGGER
                        .error("Drawing disabled for this tooltip until resource/config reload", invalidDrawing);
                }
            } finally {
                GL11.glPopAttrib();
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrixMode);
        }
    }
}
