package com.rubenverg.moldraw;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.guihook.GuiContainerManager;
import gregtech.api.enums.Materials;

public final class MaterialTargetResolver {

    private MaterialTargetResolver() {}

    public static DisplayPlan resolve(ItemStack stack, FontRenderer font) {
        if (!MolDrawConfig.INSTANCE.enabled || !MaterialRegistry.enabled()
            || stack == null
            || stack.getItem() == null) {
            return DisplayPlan.EMPTY;
        }

        try {
            var fluid = gregtech.api.util.GTUtility.getFluidFromContainerOrFluidDisplay(stack);
            if (fluid != null && fluid.getFluid() != null) return resolveFromFluid(fluid.getFluid(), font);
        } catch (RuntimeException ignored) {}
        Materials mat = GT5MaterialLookup.getMaterial(stack);
        if (mat == null) {
            return DisplayPlan.EMPTY;
        }

        return resolveFromMaterial(mat, font);
    }

    public static DisplayPlan resolveFromMaterial(Materials mat, FontRenderer font) {
        if (!MolDrawConfig.INSTANCE.enabled || !MaterialRegistry.enabled() || mat == null || mat == Materials._NULL) {
            return DisplayPlan.EMPTY;
        }

        return resolveEntry(MaterialRegistry.entry(mat), font);
    }

    private static DisplayPlan resolveEntry(MaterialRegistry.Entry entry, FontRenderer font) {
        if (font == null) return DisplayPlan.EMPTY;
        if (entry.molecule()
            .value() != null && MolDrawConfig.INSTANCE.molecule.showMolecules) {
            var renderer = RendererCache.molecule(entry, font);
            return new DisplayPlan(
                new com.rubenverg.moldraw.core.PresentationData(
                    com.rubenverg.moldraw.core.PresentationData.Kind.MOLECULE,
                    entry.definition()),
                renderer);
        }
        if (entry.chartEligible() && MolDrawConfig.INSTANCE.alloy.showAlloys) {
            var result = RendererCache.composition(entry);
            if (result.valid() && result.shares()
                .size() > 1)
                return new DisplayPlan(
                    new com.rubenverg.moldraw.core.PresentationData(
                        com.rubenverg.moldraw.core.PresentationData.Kind.ALLOY,
                        entry.definition()),
                    RendererCache.alloy(entry, font, result));
        }
        if (entry.definition()
            .diagnostics()
            .contains("PACK_FORMULA_OVERRIDE"))
            return new DisplayPlan(
                new com.rubenverg.moldraw.core.PresentationData(
                    com.rubenverg.moldraw.core.PresentationData.Kind.NONE,
                    entry.definition()),
                null);
        return DisplayPlan.EMPTY;
    }

    public static DisplayPlan resolveFromFluid(Fluid fluid, FontRenderer font) {
        if (!MolDrawConfig.INSTANCE.enabled || !MaterialRegistry.enabled() || fluid == null) {
            return DisplayPlan.EMPTY;
        }

        Materials mat = GT5MaterialLookup.getMaterialFromFluid(fluid);
        return resolveEntry(MaterialRegistry.bound("fluid:" + fluid.getName(), fluid.getName(), mat), font);
    }

    public static DisplayPlan resolveFromTooltipLines(GuiContainer gui, List<String> tip, FontRenderer font) {
        if (!MolDrawConfig.INSTANCE.enabled || !MaterialRegistry.enabled() || tip == null || tip.isEmpty()) {
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

        DisplayPlan fluidPlan = resolveFluidTooltip(tip, font);
        if (fluidPlan != null) return fluidPlan;

        // 1. 尝试从 Tooltip 文本行解析材料
        Materials mat = resolveMaterialFromTooltipText(tip);
        if (mat != null) {
            return resolveFromMaterial(mat, font);
        }

        for (String candidate : new String[] { EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(0))
            .trim(), cleanFluidName(tip.get(0)) }) {
            DisplayPlan plan = resolveEntry(MaterialRegistry.named("name:" + candidate, candidate), font);
            if (plan.isValid() || plan.hasFormulaOverride()) return plan;
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

    /** Null means no fluid identity; EMPTY also deliberately preserves a fluid's disabled binding. */
    private static DisplayPlan resolveFluidTooltip(List<String> tip, FontRenderer font) {
        for (String candidate : new String[] { EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(0))
            .trim(), cleanFluidName(tip.get(0)) }) {
            if (candidate.isEmpty()) continue;
            Fluid exact = FluidRegistry.getFluid(candidate);
            if (exact != null) return resolveFromFluid(exact, font);
            java.util.Set<Fluid> matches = new java.util.HashSet<>();
            for (Fluid fluid : FluidRegistry.getRegisteredFluids()
                .values()) {
                if (fluid == null) continue;
                if (candidate.equalsIgnoreCase(fluid.getName())) matches.add(fluid);
                try {
                    String localized = fluid.getLocalizedName(new FluidStack(fluid, 1000));
                    if (localized != null && candidate.equalsIgnoreCase(
                        EnumChatFormatting.getTextWithoutFormattingCodes(localized)
                            .trim()))
                        matches.add(fluid);
                } catch (RuntimeException ignored) {}
            }
            if (matches.size() > 1) {
                MolDraw.LOGGER.debug("AMBIGUOUS_BINDING: fluid tooltip {}", candidate);
                return DisplayPlan.EMPTY;
            }
            if (matches.size() == 1) return resolveFromFluid(
                matches.iterator()
                    .next(),
                font);
        }
        return null;
    }

    private static Materials resolveMaterialFromTooltipText(List<String> tip) {
        if (tip.isEmpty()) return null;

        String rawFirst = EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(0))
            .trim();
        String cleanedFirst = cleanFluidName(tip.get(0));

        String[] candidates = new String[] { rawFirst, cleanedFirst };

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

}
