package com.rubenverg.moldraw;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.rubenverg.moldraw.component.ITooltipDrawable;
import com.rubenverg.moldraw.component.TooltipRenderContext;
import com.rubenverg.moldraw.util.FormulaParser;

public final class TooltipPipeline {

    public static final String PLACEHOLDER_PREFIX = "§m§o§l§d";

    private TooltipPipeline() {}

    public static boolean hasMolDrawTokenOrHint(List<?> tip) {
        if (tip == null || tip.isEmpty()) return false;
        for (Object lineObj : tip) {
            if (lineObj == null) continue;
            String line = lineObj.toString();
            if (NEITooltipHelper.isOwnToken(line) || line.contains(PLACEHOLDER_PREFIX)
                || line.equals(
                    EnumChatFormatting.DARK_GRAY + "["
                        + StatCollector.translateToLocal("tooltip.gtmoldraw.shift_view_molecule")
                        + "]")
                || line.equals(
                    EnumChatFormatting.DARK_GRAY + "["
                        + StatCollector.translateToLocal("tooltip.gtmoldraw.shift_view_alloy")
                        + "]"))
                return true;
        }
        return false;
    }

    public static boolean apply(List<String> tip, DisplayPlan plan, FontRenderer font) {
        if (!MolDraw.isClientThread() || font == null || tip == null || plan == null || !plan.isValid()) {
            return false;
        }

        if (hasMolDrawTokenOrHint(tip)) {
            return false;
        }

        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (MolDrawConfig.INSTANCE.onlyShowOnShift && !shiftDown) {
            String hintKey = plan.getType() == DisplayPlan.Type.MOLECULE ? "tooltip.gtmoldraw.shift_view_molecule"
                : "tooltip.gtmoldraw.shift_view_alloy";
            String hint = EnumChatFormatting.DARK_GRAY + "[" + StatCollector.translateToLocal(hintKey) + "]";
            tip.add(hint);
            return true;
        }

        ITooltipDrawable drawable = plan.getDrawable();
        if (drawable == null) {
            return false;
        }

        // 1. NEI 环境：优先通过动态行指令注入
        if (NEITooltipHelper.isNEIAvailable()) {
            String token = NEITooltipHelper.obtainLineToken(drawable, font);
            if (token != null) {
                TooltipRenderContext.clear();
                insertToken(tip, token, plan.getFormula());
                return true;
            }
        }

        // 2. 原版回退环境：使用占位符与后处理绘制
        int renderWidth = drawable.getWidth(font);
        int renderHeight = drawable.getHeight(font);
        int startLine = tip.size();

        StringBuilder widthPad = new StringBuilder(PLACEHOLDER_PREFIX);
        while (font.getStringWidth(widthPad.toString()) < renderWidth) {
            widthPad.append(" ");
        }
        tip.add(widthPad.toString());

        int linesToInsert = (renderHeight + 9) / 10;
        for (int i = 1; i < linesToInsert; i++) {
            tip.add(PLACEHOLDER_PREFIX);
        }

        int maxTooltipWidth = 0;
        for (Object line : tip) {
            if (line != null) {
                int w = font.getStringWidth(line.toString());
                if (w > maxTooltipWidth) maxTooltipWidth = w;
            }
        }

        TooltipRenderContext.setPending(drawable, renderWidth, renderHeight, startLine, tip.size(), maxTooltipWidth);
        return true;
    }

    private static void insertToken(List<String> tip, String token, String formula) {
        int targetIdx = -1;
        if (formula != null && !formula.isEmpty()) {
            String cleanFormula = FormulaParser.normalizeFormula(formula)
                .toLowerCase(Locale.ROOT);
            for (int i = 0; i < tip.size(); i++) {
                String line = FormulaParser.normalizeFormula(tip.get(i))
                    .toLowerCase(Locale.ROOT);
                if (line.contains(cleanFormula)) {
                    targetIdx = i + 1;
                    break;
                }
            }
        }
        if (targetIdx >= 0 && targetIdx <= tip.size()) {
            tip.add(targetIdx, token);
        } else {
            tip.add(token);
        }
    }

    public static void renderPendingGraphics(int mouseX, int mouseY) {
        if (!TooltipRenderContext.hasPending()) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        int targetLine = TooltipRenderContext.getTargetLineIndex();
        int totalLines = TooltipRenderContext.getTotalTooltipLines();
        int maxTooltipWidth = TooltipRenderContext.getMaxTooltipWidth();

        int realX = -1;
        int realY = -1;
        try {
            Class<?> helperCls = Class.forName("codechicken.nei.util.RenderTooltipEventHelper");
            java.lang.reflect.Method getX = helperCls.getMethod("getX");
            java.lang.reflect.Method getY = helperCls.getMethod("getY");
            Object ox = getX.invoke(null);
            Object oy = getY.invoke(null);
            if (ox instanceof Integer && oy instanceof Integer) {
                realX = (Integer) ox;
                realY = (Integer) oy;
            }
        } catch (Throwable ignored) {}

        int tooltipX;
        int tooltipY;

        if (realX >= 0 && realY >= 0) {
            tooltipX = realX;
            tooltipY = realY;
        } else {
            tooltipX = mouseX + 12;
            tooltipY = mouseY - 12;
            int totalHeight = 8;
            if (totalLines > 1) {
                totalHeight += 2 + (totalLines - 1) * 10;
            }

            int screenWidth = mc.currentScreen != null ? mc.currentScreen.width : mc.displayWidth;
            int screenHeight = mc.currentScreen != null ? mc.currentScreen.height : mc.displayHeight;

            if (tooltipX + maxTooltipWidth > screenWidth
                || (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer
                    && mouseX > screenWidth / 2)) {
                tooltipX = mouseX - 16 - maxTooltipWidth;
            }
            if (tooltipY + totalHeight + 6 > screenHeight) {
                tooltipY = screenHeight - totalHeight - 6;
            }
            if (tooltipY < 6) {
                tooltipY = 6;
            }
        }

        int drawX = tooltipX;
        int drawY = tooltipY + (targetLine == 0 ? 0 : 2 + targetLine * 10);

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        ITooltipDrawable drawable = TooltipRenderContext.getPendingDrawable();
        if (drawable != null) {
            drawable.render(font, drawX, drawY);
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();

        TooltipRenderContext.updateMousePosition(mouseX, mouseY);
    }
}
