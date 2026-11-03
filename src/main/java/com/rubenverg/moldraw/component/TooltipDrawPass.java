package com.rubenverg.moldraw.component;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import com.rubenverg.moldraw.MolDraw;

/** Shared GL boundary for NEI and the fallback tooltip path. */
public final class TooltipDrawPass {

    private TooltipDrawPass() {}

    public static void render(ITooltipDrawable drawable, FontRenderer font, int x, int y) {
        if (!MolDraw.isClientThread() || drawable == null || font == null) return;
        GL11.glPushMatrix();
        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1, 1, 1, 1);
                drawable.render(font, x, y);
            } finally {
                GL11.glPopAttrib();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }
}
