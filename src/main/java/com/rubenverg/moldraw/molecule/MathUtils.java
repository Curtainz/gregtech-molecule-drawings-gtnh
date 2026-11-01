package com.rubenverg.moldraw.molecule;

import net.minecraft.util.EnumChatFormatting;

import org.joml.Matrix2d;
import org.joml.Matrix2dc;

public class MathUtils {

    public static Matrix2dc UVtoXY = new Matrix2d(Math.sqrt(3) / 2, 0.5, Math.sqrt(3) / 2, -0.5);

    public static double COS18 = Math.cos(Math.toRadians(18));
    public static double SIN18 = Math.sin(Math.toRadians(18));
    public static float COS18f = (float) COS18;
    public static float SIN18f = (float) SIN18;

    public static double COS30 = Math.cos(Math.toRadians(30));
    public static double SIN30 = Math.sin(Math.toRadians(30));
    public static float COS30f = (float) COS30;
    public static float SIN30f = (float) SIN30;

    public static double COS54 = Math.cos(Math.toRadians(54));
    public static double SIN54 = Math.sin(Math.toRadians(54));
    public static float COS54f = (float) COS54;
    public static float SIN54f = (float) SIN54;

    public static double PHI = (1 + Math.sqrt(5)) / 2;
    public static float PHIf = (float) PHI;

    private static final int[] CHAT_COLORS = new int[] { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA,
        0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    public static int chatFormattingColor(EnumChatFormatting formatting) {
        if (formatting == null) return 0xFFFFFFFF;
        char code = formatting.getFormattingCode();
        int index = "0123456789abcdef".indexOf(Character.toLowerCase(code));
        if (index >= 0 && index < CHAT_COLORS.length) {
            return CHAT_COLORS[index] | (0xFF << 24);
        }
        return 0xFFFFFFFF;
    }
}
