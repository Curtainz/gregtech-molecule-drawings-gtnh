package com.rubenverg.moldraw;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.Nullable;

import com.rubenverg.moldraw.molecule.Element;
import com.rubenverg.moldraw.molecule.MathUtils;

import gregtech.api.enums.Materials;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class MoleculeColorize {

    public static int FALLBACK_COLOR = MathUtils.chatFormattingColor(EnumChatFormatting.YELLOW);

    private static final Map<String, Integer> CPK_COLORS = new HashMap<>();

    static {
        CPK_COLORS.put("H", 0xFFFFFFFF);
        CPK_COLORS.put("C", 0xFF666666);
        CPK_COLORS.put("N", 0xFF3050F8);
        CPK_COLORS.put("O", 0xFFFF0D0D);
        CPK_COLORS.put("F", 0xFF90E050);
        CPK_COLORS.put("Cl", 0xFF1FF01F);
        CPK_COLORS.put("Br", 0xFFA62929);
        CPK_COLORS.put("I", 0xFF940094);
        CPK_COLORS.put("He", 0xFFD9FFFF);
        CPK_COLORS.put("Ne", 0xFFB3E3F5);
        CPK_COLORS.put("Ar", 0xFF80D1E3);
        CPK_COLORS.put("Kr", 0xFF5CB8D1);
        CPK_COLORS.put("Xe", 0xFF429EB0);
        CPK_COLORS.put("P", 0xFFFF8000);
        CPK_COLORS.put("S", 0xFFFFFF30);
        CPK_COLORS.put("B", 0xFFFFB5B5);
        CPK_COLORS.put("Li", 0xFFCC80FF);
        CPK_COLORS.put("Na", 0xFFAB5CF2);
        CPK_COLORS.put("K", 0xFF8F40D4);
        CPK_COLORS.put("Rb", 0xFF702EB0);
        CPK_COLORS.put("Cs", 0xFF571E80);
        CPK_COLORS.put("Be", 0xFFC2FF00);
        CPK_COLORS.put("Mg", 0xFF8AFF00);
        CPK_COLORS.put("Ca", 0xFF3DFF00);
        CPK_COLORS.put("Sr", 0xFF00FF2C);
        CPK_COLORS.put("Ba", 0xFF00C9FF);
        CPK_COLORS.put("Ra", 0xFF007D24);
        CPK_COLORS.put("Ti", 0xFFBFC2C7);
        CPK_COLORS.put("Fe", 0xFFE06633);
        CPK_COLORS.put("Cu", 0xFFC88033);
        CPK_COLORS.put("Zn", 0xFF7D80B0);
        CPK_COLORS.put("Al", 0xFFBFA6A6);
        CPK_COLORS.put("Si", 0xFFF0C8A0);
        CPK_COLORS.put("Au", 0xFFFFD123);
        CPK_COLORS.put("Ag", 0xFFC0C0C0);
        CPK_COLORS.put("Pt", 0xFFD0D0E0);
        CPK_COLORS.put("U", 0xFF008FFF);
    }

    public static int configColor(@Nullable String config) {
        final var str = Objects.requireNonNullElse(config, MolDrawConfig.INSTANCE.color.defaultColor);
        if (str.length() == 2 && str.charAt(0) == '§') {
            char code = str.charAt(1);
            int idx = "0123456789abcdef".indexOf(Character.toLowerCase(code));
            if (idx >= 0) {
                EnumChatFormatting f = EnumChatFormatting.values()[idx];
                return MathUtils.chatFormattingColor(f);
            }
            return FALLBACK_COLOR;
        } else if (str.length() == 7 && str.charAt(0) == '#') {
            return Color.decode(str)
                .getRGB() | (0xff << 24);
        } else {
            return FALLBACK_COLOR;
        }
    }

    public static double brightness(int color) {
        final int red = (color >> 16) & 0xff, green = (color >> 8) & 0xff, blue = color & 0xff;
        return 0.21 * red + 0.72 * green + 0.07 * blue;
    }

    public static int lightenColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        // 保证明度至少达到 0.40，且如果接近无彩色纯黑，赋予一定灰阶
        float minBrightness = (float) Math.max(0.40, MolDrawConfig.INSTANCE.color.minimumBrightness);
        if (hsb[2] < minBrightness) {
            hsb[2] = minBrightness;
        }
        return (0xFF << 24) | (Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0x00FFFFFF);
    }

    private static final Object2IntMap<Materials> COLOR_CACHE = new Object2IntOpenHashMap<>();

    public static void invalidateColorCache() {
        COLOR_CACHE.clear();
    }

    private static int doGetColorForMaterial(Materials material) {
        if (material == null) return 0xFFFFFFFF;

        String formula = material.getChemicalFormula();
        if (formula != null && CPK_COLORS.containsKey(formula)) {
            // 如果是纯碳 (C)，直接返回 CPK 石墨灰色 0xFF666666，避免 GT5u 的黑色 0x101010
            if (formula.equals("C")) {
                return 0xFF777777;
            }
        }

        if (material.mRGBa == null || material.mRGBa.length < 3) {
            if (formula != null && CPK_COLORS.containsKey(formula)) {
                return CPK_COLORS.get(formula);
            }
            return 0xFFFFFFFF;
        }

        int a = material.mRGBa.length > 3 ? (material.mRGBa[3] & 0xFF) : 0xFF;
        if (a == 0) a = 0xFF;
        int r = material.mRGBa[0] & 0xFF;
        int g = material.mRGBa[1] & 0xFF;
        int b = material.mRGBa[2] & 0xFF;

        int rawColor = (a << 24) | (r << 16) | (g << 8) | b;
        return lightenColor(rawColor);
    }

    public static int getColorForMaterial(Materials material) {
        if (!COLOR_CACHE.containsKey(material)) {
            COLOR_CACHE.put(material, doGetColorForMaterial(material));
        }
        return COLOR_CACHE.getInt(material);
    }

    public static int getColorForElement(Element element) {
        if (MolDrawConfig.INSTANCE.color.colors) {
            if (MolDrawConfig.INSTANCE.color.useMaterialColors && element.material != null
                && element.material != Materials._NULL) {
                return getColorForMaterial(element.material);
            }
            if (element.color instanceof Element.Color.Always always) {
                return lightenColor(always.color());
            } else if (element.color instanceof Element.Color.Optional opt) {
                return lightenColor(opt.color());
            }
            if (element.symbol != null && CPK_COLORS.containsKey(element.symbol)) {
                return CPK_COLORS.get(element.symbol);
            }
        }
        return configColor(null);
    }
}
