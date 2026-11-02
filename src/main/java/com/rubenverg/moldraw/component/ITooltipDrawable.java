package com.rubenverg.moldraw.component;

import net.minecraft.client.gui.FontRenderer;

public interface ITooltipDrawable {

    int getWidth(FontRenderer font);

    int getHeight(FontRenderer font);

    void render(FontRenderer font, int x, int y);
}
