package com.rubenverg.moldraw.nei;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.rubenverg.moldraw.DisplayPlan;
import com.rubenverg.moldraw.MaterialTargetResolver;
import com.rubenverg.moldraw.MolDraw;
import com.rubenverg.moldraw.MolDrawConfig;
import com.rubenverg.moldraw.TooltipPipeline;

import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;

public class NEIRecipeTooltipHandler implements IContainerTooltipHandler {

    public static void register() {
        try {
            GuiContainerManager.addTooltipHandler(new NEIRecipeTooltipHandler());
            MolDraw.LOGGER.info("Registered NEIRecipeTooltipHandler to GuiContainerManager successfully!");
        } catch (Throwable t) {
            MolDraw.LOGGER.error("Failed to register NEIRecipeTooltipHandler", t);
        }
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack stack, int mousex, int mousey,
        List<String> currenttip) {
        // 所有的 ItemStack 均由 Forge 的原生 ItemTooltipEvent (TooltipEventHandler) 统一处理。
        // 此处绝不处理 ItemStack，彻底消除双重注册导致的重复两行 Shift 提示与分子图双重绘制！
        return currenttip;
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        if (!MolDrawConfig.INSTANCE.enabled || currenttip == null || currenttip.isEmpty()) {
            return currenttip;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        DisplayPlan plan = MaterialTargetResolver.resolveFromTooltipLines(gui, currenttip, font);
        TooltipPipeline.apply(currenttip, plan, font);
        return currenttip;
    }

    @Override
    public List<String> handleItemDisplayName(GuiContainer gui, ItemStack itemstack, List<String> currenttip) {
        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey,
        Map<String, String> currentkeys) {
        return currentkeys;
    }
}
