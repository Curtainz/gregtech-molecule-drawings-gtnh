package com.rubenverg.moldraw.component;

import net.minecraft.client.gui.FontRenderer;

import com.rubenverg.moldraw.core.TooltipLayout;

/** Rendering only: no material lookup, formula parsing, font measurement or layout. */
public class LayoutRenderer implements ITooltipDrawable {

    private final TooltipLayout layout;

    public LayoutRenderer(TooltipLayout layout) {
        this.layout = layout;
    }

    public int getWidth(FontRenderer font) {
        return layout.width();
    }

    public int getHeight(FontRenderer font) {
        return layout.height();
    }

    public TooltipLayout layout() {
        return layout;
    }

    public void render(FontRenderer font, int x, int y) {
        if (!com.rubenverg.moldraw.MolDraw.isClientThread()) return;
        for (var command : layout.commands()) {
            if (command instanceof TooltipLayout.Fill f)
                GraphicalUtils.fill(x + f.x0(), y + f.y0(), x + f.x1(), y + f.y1(), f.color());
            else if (command instanceof TooltipLayout.Text t)
                font.drawStringWithShadow(t.text(), x + t.x(), y + t.y(), t.color());
            else if (command instanceof TooltipLayout.Pixels p) GraphicalUtils.drawPixels(p.pixels(), x, y);
        }
    }
}
