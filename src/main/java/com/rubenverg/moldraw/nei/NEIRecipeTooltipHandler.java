package com.rubenverg.moldraw.nei;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.input.Keyboard;

import com.rubenverg.moldraw.AlloyManager;
import com.rubenverg.moldraw.GT5MaterialLookup;
import com.rubenverg.moldraw.MolDraw;
import com.rubenverg.moldraw.MolDrawConfig;
import com.rubenverg.moldraw.MoleculeManager;
import com.rubenverg.moldraw.component.AlloyRenderer;
import com.rubenverg.moldraw.component.MoleculeRenderer;
import com.rubenverg.moldraw.molecule.Molecule;
import com.rubenverg.moldraw.util.FormulaParser;

import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import gregtech.api.enums.Materials;

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
        if (!MolDrawConfig.INSTANCE.enabled || currenttip == null || stack == null) {
            return currenttip;
        }

        // 避免重复注入（如果 Forge 的 ItemTooltipEvent 已经处理过此 Tooltip）
        if (hasExistingTokenOrHint(currenttip)) {
            return currenttip;
        }

        Materials mat = GT5MaterialLookup.getMaterial(stack);
        if (mat != null) {
            return processTooltipWithMaterial(mat, currenttip);
        }

        return currenttip;
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        if (!MolDrawConfig.INSTANCE.enabled || currenttip == null || currenttip.isEmpty()) {
            return currenttip;
        }

        // 避免重复注入
        if (hasExistingTokenOrHint(currenttip)) {
            return currenttip;
        }

        // 尝试从纯流体槽位或文本 Tooltip 中提取材料
        Materials mat = resolveMaterialFromTooltip(currenttip);
        if (mat != null) {
            return processTooltipWithMaterial(mat, currenttip);
        }

        // 回退尝试：从文本/化学式直接提取分子模型
        Molecule mol = resolveMoleculeFromTooltip(currenttip);
        if (mol != null && MolDrawConfig.INSTANCE.molecule.showMolecules) {
            return processTooltipWithMolecule(mol, currenttip);
        }

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

    private static boolean hasExistingTokenOrHint(List<String> tip) {
        for (String line : tip) {
            if (line == null) continue;
            if (line.startsWith("\u00a7x") || line.contains("Shift")
                || line.startsWith(com.rubenverg.moldraw.TooltipEventHandler.PLACEHOLDER_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private Materials resolveMaterialFromTooltip(List<String> tip) {
        if (tip.isEmpty()) return null;

        String firstLine = EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(0))
            .trim();
        if (firstLine.isEmpty()) return null;

        // 1. 尝试匹配 FluidRegistry
        Map<String, Fluid> fluids = FluidRegistry.getRegisteredFluids();
        if (fluids != null) {
            for (Fluid f : fluids.values()) {
                if (f == null) continue;
                String fName = f.getName();
                if (firstLine.equalsIgnoreCase(fName)) {
                    Materials mat = GT5MaterialLookup.getMaterialFromFluid(f);
                    if (mat != null) return mat;
                }
                try {
                    String loc = f.getLocalizedName(new FluidStack(f, 1000));
                    if (loc != null && firstLine.equalsIgnoreCase(
                        EnumChatFormatting.getTextWithoutFormattingCodes(loc)
                            .trim())) {
                        Materials mat = GT5MaterialLookup.getMaterialFromFluid(f);
                        if (mat != null) return mat;
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. 尝试匹配 GT Materials
        Materials byName = Materials.get(firstLine);
        if (GT5MaterialLookup.isMaterialValid(byName)) {
            return byName;
        }

        for (Materials m : Materials.getAll()) {
            if (!GT5MaterialLookup.isMaterialValid(m)) continue;
            if (firstLine.equalsIgnoreCase(m.mName) || firstLine.equalsIgnoreCase(m.name())) {
                return m;
            }
            if (m.mDefaultLocalName != null && firstLine.equalsIgnoreCase(m.mDefaultLocalName)) {
                return m;
            }
            try {
                String loc = m.getDefaultLocalizedNameForItem("");
                if (loc != null && firstLine.equalsIgnoreCase(loc)) {
                    return m;
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private Molecule resolveMoleculeFromTooltip(List<String> tip) {
        if (tip.isEmpty()) return null;

        String firstLine = EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(0))
            .trim();
        Molecule byName = MoleculeManager.getMoleculeByName(firstLine);
        if (byName != null) return byName;

        // 遍历每行寻找化学式（例如 "Formula: CH4" 或 "§7CH4"）
        for (String line : tip) {
            if (line == null) continue;
            String clean = EnumChatFormatting.getTextWithoutFormattingCodes(line)
                .trim();
            if (clean.toLowerCase(Locale.ROOT)
                .startsWith("formula:") || clean.startsWith("化学式:")
                || clean.startsWith("分子式:")) {
                int colon = clean.indexOf(':');
                if (colon >= 0 && colon < clean.length() - 1) {
                    String f = clean.substring(colon + 1)
                        .trim();
                    Molecule byFormula = MoleculeManager.getMoleculeByFormula(f);
                    if (byFormula != null) return byFormula;
                }
            }
            // 单独为化学式缩写行尝试
            Molecule byFormulaDirect = MoleculeManager.getMoleculeByFormula(clean);
            if (byFormulaDirect != null) return byFormulaDirect;
        }

        return null;
    }

    private List<String> processTooltipWithMaterial(Materials mat, List<String> currenttip) {
        Molecule mol = MoleculeManager.getMolecule(mat);
        boolean isAlloy = (mol == null) && AlloyManager.isAlloy(mat) && AlloyRenderer.hasValidComponents(mat);

        if (mol == null && !isAlloy) {
            return currenttip;
        }

        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (MolDrawConfig.INSTANCE.onlyShowOnShift && !shiftDown) {
            if (mol != null && MolDrawConfig.INSTANCE.molecule.showMolecules) {
                currenttip.add(EnumChatFormatting.DARK_GRAY + "[按住 Shift 查看分子结构]");
            } else if (isAlloy && MolDrawConfig.INSTANCE.alloy.showAlloys) {
                currenttip.add(EnumChatFormatting.DARK_GRAY + "[按住 Shift 查看成分分析]");
            }
            return currenttip;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        MoleculeRenderer molRenderer = null;
        AlloyRenderer alloyRenderer = null;

        if (mol != null && MolDrawConfig.INSTANCE.molecule.showMolecules) {
            molRenderer = new MoleculeRenderer(mol);
        } else if (isAlloy && MolDrawConfig.INSTANCE.alloy.showAlloys) {
            alloyRenderer = new AlloyRenderer(mat, font);
        }

        if (molRenderer == null && alloyRenderer == null) {
            return currenttip;
        }

        return applyRenderPlaceholders(currenttip, molRenderer, alloyRenderer, font, mat.getChemicalFormula());
    }

    private List<String> processTooltipWithMolecule(Molecule mol, List<String> currenttip) {
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (MolDrawConfig.INSTANCE.onlyShowOnShift && !shiftDown) {
            currenttip.add(EnumChatFormatting.DARK_GRAY + "[按住 Shift 查看分子结构]");
            return currenttip;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        MoleculeRenderer molRenderer = new MoleculeRenderer(mol);
        return applyRenderPlaceholders(currenttip, molRenderer, null, font, null);
    }

    private static List<String> applyRenderPlaceholders(List<String> currenttip, MoleculeRenderer molRenderer,
        AlloyRenderer alloyRenderer, FontRenderer font, String formula) {
        int renderWidth = molRenderer != null ? molRenderer.getWidth(font) : alloyRenderer.getWidth(font);
        int renderHeight = molRenderer != null ? molRenderer.getHeight(font) : alloyRenderer.getHeight(font);

        int targetIdx = getTargetInsertIndex(currenttip, formula);

        // 撑开宽度
        StringBuilder widthPad = new StringBuilder(com.rubenverg.moldraw.TooltipEventHandler.PLACEHOLDER_PREFIX);
        while (font.getStringWidth(widthPad.toString()) < renderWidth) {
            widthPad.append(" ");
        }
        currenttip.add(targetIdx, widthPad.toString());

        // 撑开高度 (每行 10px)
        int linesToInsert = (renderHeight + 9) / 10;
        for (int i = 1; i < linesToInsert; i++) {
            currenttip.add(targetIdx + i, com.rubenverg.moldraw.TooltipEventHandler.PLACEHOLDER_PREFIX);
        }

        int maxTooltipWidth = 0;
        for (String line : currenttip) {
            if (line != null) {
                int w = font.getStringWidth(line);
                if (w > maxTooltipWidth) maxTooltipWidth = w;
            }
        }

        int totalLines = currenttip.size();

        if (molRenderer != null) {
            com.rubenverg.moldraw.component.TooltipRenderContext
                .setPendingMolecule(molRenderer, renderWidth, renderHeight, targetIdx, totalLines, maxTooltipWidth);
        } else {
            com.rubenverg.moldraw.component.TooltipRenderContext
                .setPendingAlloy(alloyRenderer, renderWidth, renderHeight, targetIdx, totalLines, maxTooltipWidth);
        }

        return currenttip;
    }

    private static int getTargetInsertIndex(List<String> tip, String formula) {
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
            return targetIdx;
        }
        return tip.size();
    }
}
