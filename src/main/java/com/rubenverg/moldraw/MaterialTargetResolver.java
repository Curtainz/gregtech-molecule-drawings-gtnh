package com.rubenverg.moldraw;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.rubenverg.moldraw.component.AlloyRenderer;
import com.rubenverg.moldraw.molecule.Molecule;

import codechicken.nei.guihook.GuiContainerManager;
import gregtech.api.enums.Materials;

public final class MaterialTargetResolver {

    private MaterialTargetResolver() {}

    public static DisplayPlan resolve(ItemStack stack, FontRenderer font) {
        if (!MolDrawConfig.INSTANCE.enabled || stack == null || stack.getItem() == null) {
            return DisplayPlan.EMPTY;
        }

        Materials mat = GT5MaterialLookup.getMaterial(stack);
        if (mat == null) {
            return DisplayPlan.EMPTY;
        }

        return resolveFromMaterial(mat, font);
    }

    public static DisplayPlan resolveFromMaterial(Materials mat, FontRenderer font) {
        if (!MolDrawConfig.INSTANCE.enabled || mat == null || mat == Materials._NULL) {
            return DisplayPlan.EMPTY;
        }

        // 1. 单质元素不需要显示合金成分，也不绘制分子模型（如单质金、铜等）
        if (mat.mElement != null) {
            return DisplayPlan.EMPTY;
        }

        // 2. 优先检查 3D 分子结构
        Molecule mol = MoleculeManager.getMolecule(mat);
        if (mol != null && MolDrawConfig.INSTANCE.molecule.showMolecules) {
            return new DisplayPlan(
                DisplayPlan.Type.MOLECULE,
                RendererCache.getMoleculeRenderer(mol),
                mat.getChemicalFormula(),
                mat);
        }

        // 3. 只有当没有分子结构，且为明确定义的真正多元合金时，才展示成分分析饼图
        if (AlloyManager.isAlloy(mat) && AlloyRenderer.hasValidComponents(mat)
            && MolDrawConfig.INSTANCE.alloy.showAlloys) {
            return new DisplayPlan(
                DisplayPlan.Type.ALLOY,
                RendererCache.getAlloyRenderer(mat, font),
                mat.getChemicalFormula(),
                mat);
        }

        return DisplayPlan.EMPTY;
    }

    public static DisplayPlan resolveFromFluid(Fluid fluid, FontRenderer font) {
        if (!MolDrawConfig.INSTANCE.enabled || fluid == null) {
            return DisplayPlan.EMPTY;
        }

        Materials mat = GT5MaterialLookup.getMaterialFromFluid(fluid);
        if (mat != null) {
            return resolveFromMaterial(mat, font);
        }

        // 回退检查：流体名是否直接对应某种分子模型
        Molecule mol = MoleculeManager.getMoleculeByName(fluid.getName());
        if (mol != null && MolDrawConfig.INSTANCE.molecule.showMolecules) {
            return new DisplayPlan(DisplayPlan.Type.MOLECULE, RendererCache.getMoleculeRenderer(mol), null, null);
        }

        return DisplayPlan.EMPTY;
    }

    public static DisplayPlan resolveFromTooltipLines(GuiContainer gui, List<String> tip, FontRenderer font) {
        if (!MolDrawConfig.INSTANCE.enabled || tip == null || tip.isEmpty()) {
            return DisplayPlan.EMPTY;
        }

        // 如果鼠标当前悬停在某个真实的物品 Slot 上，跳过纯文本解析（物品由 ItemTooltipEvent 统一处理）
        if (gui != null) {
            try {
                if (GuiContainerManager.getStackMouseOver(gui) != null) {
                    return DisplayPlan.EMPTY;
                }
            } catch (Throwable ignored) {}
        }

        // 1. 尝试从 Tooltip 文本行解析材料
        Materials mat = resolveMaterialFromTooltipText(tip);
        if (mat != null) {
            return resolveFromMaterial(mat, font);
        }

        // 2. 尝试从 Tooltip 中的化学式或名称反查分子模型
        Molecule mol = resolveMoleculeFromTooltipText(tip);
        if (mol != null && MolDrawConfig.INSTANCE.molecule.showMolecules) {
            return new DisplayPlan(DisplayPlan.Type.MOLECULE, RendererCache.getMoleculeRenderer(mol), null, null);
        }

        return DisplayPlan.EMPTY;
    }

    private static String cleanFluidName(String raw) {
        if (raw == null) return "";
        String s = EnumChatFormatting.getTextWithoutFormattingCodes(raw)
            .trim();
        s = s.replaceAll("^[0-9,]+(\\.[0-9]+)?\\s*(mB|mb|MB|L|l)?\\s*", "");
        s = s.replaceAll("\\s*\\(?[0-9,]+(\\.[0-9]+)?\\s*(mB|mb|MB|L|l)?\\)?$", "");
        if (s.toLowerCase(Locale.ROOT)
            .startsWith("fluid:")) {
            s = s.substring(6)
                .trim();
        }
        return s.trim();
    }

    private static Materials resolveMaterialFromTooltipText(List<String> tip) {
        if (tip.isEmpty()) return null;

        String rawFirst = EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(0))
            .trim();
        String cleanedFirst = cleanFluidName(tip.get(0));

        String[] candidates = new String[] { rawFirst, cleanedFirst };

        // 匹配 FluidRegistry
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

        // 匹配 GT Materials
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

    private static Molecule resolveMoleculeFromTooltipText(List<String> tip) {
        if (tip.isEmpty()) return null;

        String rawFirst = EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(0))
            .trim();
        String cleanedFirst = cleanFluidName(tip.get(0));

        for (String cand : new String[] { rawFirst, cleanedFirst }) {
            if (cand.isEmpty()) continue;
            Molecule byName = MoleculeManager.getMoleculeByName(cand);
            if (byName != null) return byName;
        }

        // 遍历每行寻找化学式
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
            Molecule byFormulaDirect = MoleculeManager.getMoleculeByFormula(clean);
            if (byFormulaDirect != null) return byFormulaDirect;
        }

        return null;
    }
}
