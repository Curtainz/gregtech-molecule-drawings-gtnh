package com.rubenverg.moldraw;

import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import gregtech.api.enums.Materials;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

public final class GT5MaterialLookup {

    private static final String[] ORE_PREFIXES = new String[] { "cellMolten", "cellPlasma", "cell", "capsuleMolten",
        "capsulePlasma", "capsule", "bottleMolten", "bottlePlasma", "bottle", "fluid", "liquid", "gas", "plasma",
        "molten" };

    private GT5MaterialLookup() {}

    public static boolean isMaterialValid(Materials mat) {
        return mat != null && mat != Materials._NULL;
    }

    public static Materials getMaterial(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        // 预检：排除模具、工具、机械外壳等非基础材料物品
        if (isNonMaterialItem(stack, null)) {
            return null;
        }

        // 1. 检查 OreDict 数据 (覆盖大部分 GT 原生材料物品，如锭、粉、板、棒、导线等)
        try {
            ItemData data = GTOreDictUnificator.getItemData(stack);
            if (data != null && data.mMaterial != null && isMaterialValid(data.mMaterial.mMaterial)) {
                if (isNonMaterialItem(stack, data)) {
                    return null;
                }
                return data.mMaterial.mMaterial;
            }
        } catch (Throwable ignored) {}

        // 2. 检查 GT 流体槽/流体物品 (单元、流体显示物品、储罐等)
        try {
            FluidStack fluid = GTUtility.getFluidFromContainerOrFluidDisplay(stack);
            if (fluid != null && fluid.getFluid() != null) {
                Materials mat = getMaterialFromFluid(fluid.getFluid());
                if (isMaterialValid(mat)) {
                    return mat;
                }
            }
        } catch (Throwable ignored) {}

        // 3. 检查通用流体容器 (IC2 流体单元、Forestry 胶囊、BC 储罐等)
        try {
            FluidStack fs = FluidContainerRegistry.getFluidForFilledItem(stack);
            if (fs != null && fs.getFluid() != null) {
                Materials mat = getMaterialFromFluid(fs.getFluid());
                if (isMaterialValid(mat)) {
                    return mat;
                }
            }
        } catch (Throwable ignored) {}

        // 4. 遍历矿物词典 OreDictionary 批量匹配流体/容器词条
        try {
            int[] oreIDs = OreDictionary.getOreIDs(stack);
            for (int id : oreIDs) {
                String oreName = OreDictionary.getOreName(id);
                if (oreName != null && !oreName.isEmpty()) {
                    String oLower = oreName.toLowerCase(Locale.ROOT);
                    if (oLower.contains("shape") || oLower.contains("mold")
                        || oLower.contains("tool")
                        || oLower.contains("casing")) {
                        continue;
                    }
                    Materials mat = resolveOreName(oreName);
                    if (isMaterialValid(mat)) {
                        return mat;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 5. 检查未本地化名与注册名 Fallback
        try {
            String unlocalized = stack.getUnlocalizedName();
            if (unlocalized != null) {
                Materials mat = resolveOreName(unlocalized);
                if (isMaterialValid(mat)) {
                    return mat;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static boolean isNonMaterialItem(ItemStack stack, ItemData data) {
        if (stack == null) return true;

        if (data != null && data.mPrefix != null) {
            String pName = data.mPrefix.name()
                .toLowerCase(Locale.ROOT);
            if (pName.contains("shape") || pName.contains("mold")
                || pName.contains("tool")
                || pName.contains("crafting")
                || pName.contains("casing")
                || pName.contains("circuit")
                || pName.contains("machine")) {
                return true;
            }
        }

        String unloc = stack.getUnlocalizedName();
        if (unloc != null) {
            String u = unloc.toLowerCase(Locale.ROOT);
            if (u.contains("shape") || u.contains("mold")
                || u.contains(".tool.")
                || u.contains("toolhead")
                || u.contains("wrench")
                || u.contains("hammer")
                || u.contains("crowbar")
                || u.contains("file")
                || u.contains("screwdriver")
                || u.contains("cutter")
                || u.contains("saw")
                || u.contains("slicer")) {
                return true;
            }
        }

        try {
            String disp = stack.getDisplayName();
            if (disp != null) {
                String d = disp.toLowerCase(Locale.ROOT);
                if (d.contains("mold") || d.contains("模具")
                    || d.contains("shape")
                    || d.contains("extruder shape")
                    || d.contains("挤压模具")
                    || d.contains("切片")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public static Materials getMaterialFromFluid(Fluid fluid) {
        if (fluid == null) return null;

        // 优先使用 GT 原生材料流体映射
        try {
            Materials mat = Materials.getGtMaterialFromFluid(fluid);
            if (isMaterialValid(mat)) {
                return mat;
            }
        } catch (Throwable ignored) {}

        String fluidName = fluid.getName();
        if (fluidName == null || fluidName.isEmpty()) return null;

        // 直接通过名称查找
        Materials mat = Materials.get(fluidName);
        if (isMaterialValid(mat)) return mat;

        // 剥离常见流体前缀
        String[] prefixes = new String[] { "fluid.", "gas.", "liquid.", "molten.", "plasma." };
        for (String p : prefixes) {
            if (fluidName.startsWith(p)) {
                mat = Materials.get(fluidName.substring(p.length()));
                if (isMaterialValid(mat)) return mat;
            }
        }

        // 下划线转驼峰首字母大写匹配
        String normalized = fluidName.replace("fluid.", "")
            .replace("gas.", "")
            .replace("liquid.", "")
            .replace("molten.", "");
        mat = Materials.get(toCamelCase(normalized));
        if (isMaterialValid(mat)) return mat;

        return null;
    }

    private static Materials resolveOreName(String name) {
        if (name == null || name.isEmpty()) return null;

        for (String prefix : ORE_PREFIXES) {
            if (name.startsWith(prefix) && name.length() > prefix.length()) {
                String matPart = name.substring(prefix.length());
                Materials mat = Materials.get(matPart);
                if (isMaterialValid(mat)) return mat;

                mat = Materials.get(toCamelCase(matPart));
                if (isMaterialValid(mat)) return mat;
            }
        }

        return Materials.get(name);
    }

    private static String toCamelCase(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_' || c == '-' || c == '.') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
