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
import com.rubenverg.moldraw.NEITooltipHelper;
import com.rubenverg.moldraw.TooltipEventHandler;
import com.rubenverg.moldraw.component.AlloyRenderer;
import com.rubenverg.moldraw.component.MoleculeRenderer;
import com.rubenverg.moldraw.component.TooltipRenderContext;
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
        // 核心架构原则：所有的 ItemStack 均由 Forge 的原生 ItemTooltipEvent (TooltipEventHandler) 统一处理。
        // 此处绝不处理 ItemStack，彻底消除双重注册导致的重复两行 Shift 提示与分子图双重绘制！
        return currenttip;
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        if (!MolDrawConfig.INSTANCE.enabled || currenttip == null || currenttip.isEmpty()) {
            return currenttip;
        }

        // 如果鼠标当前悬停在某个真实的物品 Slot 上，跳过处理（物品已由 ItemTooltipEvent 统一处理）
        if (gui != null) {
            try {
                if (GuiContainerManager.getStackMouseOver(gui) != null) {
                    return currenttip;
                }
            } catch (Throwable ignored) {}
        }

        // 避免重复注入
        if (TooltipEventHandler.hasMolDrawTokenOrHint(currenttip)) {
            return currenttip;
        }

        // 尝试从流体槽位或文本 Tooltip 中提取材料
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

    private static String cleanFluidName(String raw) {
        if (raw == null) return "";
        String s = EnumChatFormatting.getTextWithoutFormattingCodes(raw)
            .trim();
        // 去除开头数量，如 "1,000 L ", "1000mB ", "1000L "
        s = s.replaceAll("^[0-9,]+(\\.[0-9]+)?\\s*(mB|mb|MB|L|l)?\\s*", "");
        // 去除结尾数量，如 " (1,000 L)", " 1000mB"
        s = s.replaceAll("\\s*\\(?[0-9,]+(\\.[0-9]+)?\\s*(mB|mb|MB|L|l)?\\)?$", "");
        // 去除冒号前缀如 "Fluid: "
        if (s.toLowerCase(Locale.ROOT)
            .startsWith("fluid:")) {
            s = s.substring(6)
                .trim();
        }
        return s.trim();
    }

    private Materials resolveMaterialFromTooltip(List<String> tip) {
        if (tip.isEmpty()) return null;

        String rawFirst = EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(0))
            .trim();
        String cleanedFirst = cleanFluidName(tip.get(0));

        String[] candidates = new String[] { rawFirst, cleanedFirst };

        // 1. 尝试匹配 FluidRegistry
        Map<String, Fluid> fluids = FluidRegistry.getRegisteredFluids();
        if (fluids != null) {
            for (String cand : candidates) {
                if (cand.isEmpty()) continue;
                for (Fluid f : fluids.values()) {
                    if (f == null) continue;
                    String fName = f.getName();
                    if (cand.equalsIgnoreCase(fName)) {
                        Materials mat = GT5MaterialLookup.getMaterialFromFluid(f);
                        if (mat != null) return mat;
                    }
                    try {
                        String loc = f.getLocalizedName(new FluidStack(f, 1000));
                        if (loc != null && cand.equalsIgnoreCase(
                            EnumChatFormatting.getTextWithoutFormattingCodes(loc)
                                .trim())) {
                            Materials mat = GT5MaterialLookup.getMaterialFromFluid(f);
                            if (mat != null) return mat;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        // 2. 尝试匹配 GT Materials
        for (String cand : candidates) {
            if (cand.isEmpty()) continue;
            Materials byName = Materials.get(cand);
            if (GT5MaterialLookup.isMaterialValid(byName)) {
                return byName;
            }

            for (Materials m : Materials.getAll()) {
                if (!GT5MaterialLookup.isMaterialValid(m)) continue;
                if (cand.equalsIgnoreCase(m.mName) || cand.equalsIgnoreCase(m.name())) {
                    return m;
                }
                if (m.mDefaultLocalName != null && cand.equalsIgnoreCase(m.mDefaultLocalName)) {
                    return m;
                }
                try {
                    String loc = m.getDefaultLocalizedNameForItem("");
                    if (loc != null && cand.equalsIgnoreCase(loc)) {
                        return m;
                    }
                } catch (Throwable ignored) {}
            }
        }

        return null;
    }

    private Molecule resolveMoleculeFromTooltip(List<String> tip) {
        if (tip.isEmpty()) return null;

        String rawFirst = EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(0))
            .trim();
        String cleanedFirst = cleanFluidName(tip.get(0));

        for (String cand : new String[] { rawFirst, cleanedFirst }) {
            if (cand.isEmpty()) continue;
            Molecule byName = MoleculeManager.getMoleculeByName(cand);
            if (byName != null) return byName;
        }

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

        // 在 NEI 环境下，统一注册为 ITooltipLineHandler 原生行指令
        // 由 NEI 的 GuiDraw.drawMultilineTip 负责计算精确高度并原位绘制，绝对杜绝错位和侵占空间！
        if (NEITooltipHelper.isNEIAvailable()) {
            String token = null;
            if (molRenderer != null) {
                token = NEITooltipHelper.registerMoleculeHandler(molRenderer, font);
            } else {
                token = NEITooltipHelper.registerAlloyHandler(alloyRenderer, font);
            }

            if (token != null) {
                TooltipRenderContext.clear();
                insertToken(currenttip, token, mat.getChemicalFormula());
                return currenttip;
            }
        }

        return currenttip;
    }

    private List<String> processTooltipWithMolecule(Molecule mol, List<String> currenttip) {
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (MolDrawConfig.INSTANCE.onlyShowOnShift && !shiftDown) {
            currenttip.add(EnumChatFormatting.DARK_GRAY + "[按住 Shift 查看分子结构]");
            return currenttip;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        MoleculeRenderer molRenderer = new MoleculeRenderer(mol);

        if (NEITooltipHelper.isNEIAvailable()) {
            String token = NEITooltipHelper.registerMoleculeHandler(molRenderer, font);
            if (token != null) {
                TooltipRenderContext.clear();
                insertToken(currenttip, token, null);
                return currenttip;
            }
        }

        return currenttip;
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
}
