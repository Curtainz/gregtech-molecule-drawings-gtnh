package com.rubenverg.moldraw;

import java.awt.Dimension;

import net.minecraft.client.gui.FontRenderer;

import com.rubenverg.moldraw.component.ITooltipDrawable;
import com.rubenverg.moldraw.component.TooltipDrawPass;

import codechicken.lib.gui.GuiDraw;

/** Version-pinned CodeChicken public API adapter. No global handler-list ownership. */
public final class NEITooltipHelper {

    private static boolean available;

    private NEITooltipHelper() {}

    public static void init() {
        if (!MolDraw.isClientThread()) return;
        available = cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems");
    }

    public static boolean isNEIAvailable() {
        return MolDraw.isClientThread() && available;
    }

    public static String obtainLineToken(ITooltipDrawable drawable, FontRenderer font) {
        if (!isNEIAvailable() || drawable == null || font == null) return null;
        return "\u00a7x" + GuiDraw.getTipLineId(new LineHandler(drawable, font, MaterialRegistry.version()));
    }

    public static boolean isOwnToken(String token) {
        if (!isNEIAvailable() || token == null || !token.startsWith("\u00a7x")) return false;
        try {
            return GuiDraw.getTipLine(token) instanceof LineHandler;
        } catch (RuntimeException staleOrMalformedToken) {
            return false;
        }
    }

    public static boolean isStaleOwnToken(String token) {
        if (!isNEIAvailable() || token == null || !token.startsWith("\u00a7x")) return false;
        try {
            return GuiDraw.getTipLine(token) instanceof LineHandler line
                && line.version() != MaterialRegistry.version();
        } catch (RuntimeException stale) {
            return false;
        }
    }

    private record LineHandler(ITooltipDrawable drawable, FontRenderer font, long version)
        implements GuiDraw.ITooltipLineHandler {

        @Override
        public Dimension getSize() {
            if (!MolDraw.isClientThread() || version != MaterialRegistry.version() || !MaterialRegistry.enabled())
                return new Dimension(0, 0);
            return new Dimension(drawable.getWidth(font), drawable.getHeight(font));
        }

        @Override
        public void draw(int x, int y) {
            if (!MolDraw.isClientThread() || version != MaterialRegistry.version() || !MaterialRegistry.enabled())
                return;
            TooltipDrawPass.render(drawable, font, x, y);
        }
    }
}
