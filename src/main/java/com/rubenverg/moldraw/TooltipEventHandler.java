package com.rubenverg.moldraw;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.rubenverg.moldraw.component.TooltipRenderContext;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class TooltipEventHandler {

    public static final String PLACEHOLDER_PREFIX = TooltipPipeline.PLACEHOLDER_PREFIX;

    public static boolean hasMolDrawTokenOrHint(List<?> tip) {
        return TooltipPipeline.hasMolDrawTokenOrHint(tip);
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!MolDraw.isClientThread()) {
            return;
        }

        if (!MolDrawConfig.INSTANCE.enabled || event.itemStack == null || event.toolTip == null) {
            return;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        DisplayPlan plan = MaterialTargetResolver.resolve(event.itemStack, font);
        TooltipPipeline.apply(event.toolTip, plan, font);
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!MolDraw.isClientThread()) {
            return;
        }

        // 当由 NEI / ChromaticTooltips 托管渲染时，绝不在后处理事件中重复渲染或干扰状态
        if (NEITooltipHelper.isNEIAvailable()) {
            return;
        }

        if (!TooltipRenderContext.isStillActive(event.mouseX, event.mouseY)) {
            return;
        }

        TooltipPipeline.renderPendingGraphics(event.mouseX, event.mouseY);
        TooltipRenderContext.clear();
    }
}
