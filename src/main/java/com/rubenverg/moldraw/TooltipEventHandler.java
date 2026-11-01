package com.rubenverg.moldraw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.rubenverg.moldraw.component.*;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.enums.Materials;

public class TooltipEventHandler {

    public static final String PLACEHOLDER_PREFIX = "§m§o§l§d";

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!MolDrawConfig.INSTANCE.enabled) return;
        ItemStack stack = event.itemStack;
        if (stack == null) return;

        Materials mat = GT5MaterialLookup.getMaterial(stack);
        if (mat == null) return;

        // 优先寻找 3D 分子结构
        var mol = MoleculeManager.getMolecule(mat);
        // 只有当没有分子结构，且为合金或含有可解析成分的化合物时，才展示成分饼图
        boolean isAlloyOrCompound = (mol == null)
            && (AlloyManager.isAlloy(mat) || AlloyRenderer.hasValidComponents(mat));

        if (mol == null && !isAlloyOrCompound) return;

        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (MolDrawConfig.INSTANCE.onlyShowOnShift && !shiftDown) {
            if (mol != null && MolDrawConfig.INSTANCE.molecule.showMolecules) {
                event.toolTip.add(EnumChatFormatting.DARK_GRAY + "[按住 Shift 查看分子结构]");
            } else if (isAlloyOrCompound && MolDrawConfig.INSTANCE.alloy.showAlloys) {
                event.toolTip.add(EnumChatFormatting.DARK_GRAY + "[按住 Shift 查看成分分析]");
            }
            return;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        MoleculeRenderer molRenderer = null;
        AlloyRenderer alloyRenderer = null;

        if (mol != null && MolDrawConfig.INSTANCE.molecule.showMolecules) {
            molRenderer = new MoleculeRenderer(mol);
        } else if (isAlloyOrCompound && MolDrawConfig.INSTANCE.alloy.showAlloys) {
            alloyRenderer = new AlloyRenderer(mat, font);
        }

        if (molRenderer == null && alloyRenderer == null) return;

        // 优先尝试接入 CodeChickenCore / NEI 原生 ITooltipLineHandler 接口
        // 该方案可以与 NEI 的屏幕防遮挡位移 100% 绝对同步，并杜绝错位和闪烁！
        if (NEITooltipHelper.isNEIAvailable()) {
            String token = null;
            if (molRenderer != null) {
                token = NEITooltipHelper.registerMoleculeHandler(molRenderer, font);
            } else {
                token = NEITooltipHelper.registerAlloyHandler(alloyRenderer, font);
            }

            if (token != null) {
                // 成功接管，清除可能残留的原生通道绘制缓存
                TooltipRenderContext.clear();

                // 寻找化学式行或关键位置插入
                String formula = mat.getChemicalFormula();
                int targetIdx = -1;
                if (formula != null && !formula.isEmpty()) {
                    String cleanFormula = com.rubenverg.moldraw.util.FormulaParser.normalizeFormula(formula)
                        .toLowerCase();
                    for (int i = 0; i < event.toolTip.size(); i++) {
                        String line = com.rubenverg.moldraw.util.FormulaParser.normalizeFormula(
                            event.toolTip.get(i)
                                .toString())
                            .toLowerCase();
                        if (line.contains(cleanFormula)) {
                            targetIdx = i + 1;
                            break;
                        }
                    }
                }
                if (targetIdx >= 0 && targetIdx <= event.toolTip.size()) {
                    event.toolTip.add(targetIdx, token);
                } else {
                    event.toolTip.add(token);
                }
                return;
            }
        }

        // 回退逻辑：无 NEI 时的原生事件占位模式
        int renderWidth = molRenderer != null ? molRenderer.getWidth(font) : alloyRenderer.getWidth(font);
        int renderHeight = molRenderer != null ? molRenderer.getHeight(font) : alloyRenderer.getHeight(font);

        int startLine = event.toolTip.size();

        // 撑开宽度
        StringBuilder widthPad = new StringBuilder(PLACEHOLDER_PREFIX);
        while (font.getStringWidth(widthPad.toString()) < renderWidth) {
            widthPad.append(" ");
        }
        event.toolTip.add(widthPad.toString());

        // 撑开高度 (每行 10px)
        int linesToInsert = (renderHeight + 9) / 10;
        for (int i = 1; i < linesToInsert; i++) {
            event.toolTip.add(PLACEHOLDER_PREFIX);
        }

        int maxTooltipWidth = 0;
        for (Object line : event.toolTip) {
            if (line != null) {
                int w = font.getStringWidth(line.toString());
                if (w > maxTooltipWidth) maxTooltipWidth = w;
            }
        }

        int totalLines = event.toolTip.size();

        if (molRenderer != null) {
            TooltipRenderContext
                .setPendingMolecule(molRenderer, renderWidth, renderHeight, startLine, totalLines, maxTooltipWidth);
        } else {
            TooltipRenderContext
                .setPendingAlloy(alloyRenderer, renderWidth, renderHeight, startLine, totalLines, maxTooltipWidth);
        }
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!TooltipRenderContext.isStillActive(event.mouseX, event.mouseY)) return;

        renderPendingTooltipGraphics(event.mouseX, event.mouseY);
    }

    public static void renderPendingTooltipGraphics(int mouseX, int mouseY) {
        if (!TooltipRenderContext.hasPending()) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        int targetLine = TooltipRenderContext.getTargetLineIndex();
        int totalLines = TooltipRenderContext.getTotalTooltipLines();
        int maxTooltipWidth = TooltipRenderContext.getMaxTooltipWidth();

        // 尝试从 GTNHLib / NEI 的 RenderTooltipEvent 抓取真实的 X 和 Y 坐标！
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

            // 智能防遮挡：如果超出屏幕右边界，或者鼠标在右半屏，切换到鼠标左侧！
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

        if (TooltipRenderContext.getPendingMolecule() != null) {
            TooltipRenderContext.getPendingMolecule()
                .render(font, drawX, drawY);
        } else if (TooltipRenderContext.getPendingAlloy() != null) {
            TooltipRenderContext.getPendingAlloy()
                .render(font, drawX, drawY);
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();

        TooltipRenderContext.updateMousePosition(mouseX, mouseY);
    }
}
