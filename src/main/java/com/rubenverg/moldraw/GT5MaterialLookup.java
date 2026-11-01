package com.rubenverg.moldraw;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.Materials;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

public final class GT5MaterialLookup {

    private GT5MaterialLookup() {}

    public static boolean isMaterialValid(Materials mat) {
        return mat != null && mat != Materials._NULL;
    }

    public static Materials getMaterial(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        // 1. 检查 OreDict 数据 (覆盖大部分 GT 原生材料物品)
        try {
            ItemData data = GTOreDictUnificator.getItemData(stack);
            if (data != null && data.mMaterial != null && isMaterialValid(data.mMaterial.mMaterial)) {
                return data.mMaterial.mMaterial;
            }
        } catch (Throwable ignored) {}

        // 2. 检查流体容器 (单元、显示物品、储罐等)
        try {
            FluidStack fluid = GTUtility.getFluidFromContainerOrFluidDisplay(stack);
            if (fluid != null && fluid.getFluid() != null) {
                String fluidName = fluid.getFluid()
                    .getName();
                Materials mat = Materials.get(fluidName);
                if (isMaterialValid(mat)) {
                    return mat;
                }
                if (fluidName.startsWith("fluid.")) {
                    mat = Materials.get(fluidName.substring(6));
                    if (isMaterialValid(mat)) {
                        return mat;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
