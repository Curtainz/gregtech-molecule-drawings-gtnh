package com.rubenverg.moldraw.component;

public final class TooltipRenderContext {

    private static MoleculeRenderer pendingMolecule;
    private static AlloyRenderer pendingAlloy;
    private static int renderWidth;
    private static int renderHeight;
    private static int targetLineIndex;
    private static int totalTooltipLines;
    private static int maxTooltipWidth;

    private static long lastActiveTime = 0L;
    private static int lastMouseX = -1;
    private static int lastMouseY = -1;

    private TooltipRenderContext() {}

    public static void setPendingMolecule(MoleculeRenderer renderer, int w, int h, int lineIdx, int totalLines,
        int maxWidth) {
        pendingMolecule = renderer;
        pendingAlloy = null;
        renderWidth = w;
        renderHeight = h;
        targetLineIndex = lineIdx;
        totalTooltipLines = totalLines;
        maxTooltipWidth = maxWidth;
        lastActiveTime = System.currentTimeMillis();
    }

    public static void setPendingAlloy(AlloyRenderer renderer, int w, int h, int lineIdx, int totalLines,
        int maxWidth) {
        pendingAlloy = renderer;
        pendingMolecule = null;
        renderWidth = w;
        renderHeight = h;
        targetLineIndex = lineIdx;
        totalTooltipLines = totalLines;
        maxTooltipWidth = maxWidth;
        lastActiveTime = System.currentTimeMillis();
    }

    public static void updateMousePosition(int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public static boolean isStillActive(int mouseX, int mouseY) {
        if (!hasPending()) return false;
        long now = System.currentTimeMillis();
        if (now - lastActiveTime > 200) {
            clear();
            return false;
        }
        if (lastMouseX >= 0 && lastMouseY >= 0) {
            int dx = mouseX - lastMouseX;
            int dy = mouseY - lastMouseY;
            if (dx * dx + dy * dy > 30 * 30) {
                clear();
                return false;
            }
        }
        return true;
    }

    public static boolean hasPending() {
        return pendingMolecule != null || pendingAlloy != null;
    }

    public static MoleculeRenderer getPendingMolecule() {
        return pendingMolecule;
    }

    public static AlloyRenderer getPendingAlloy() {
        return pendingAlloy;
    }

    public static int getRenderWidth() {
        return renderWidth;
    }

    public static int getRenderHeight() {
        return renderHeight;
    }

    public static int getTargetLineIndex() {
        return targetLineIndex;
    }

    public static int getTotalTooltipLines() {
        return totalTooltipLines;
    }

    public static int getMaxTooltipWidth() {
        return maxTooltipWidth;
    }

    public static void clear() {
        pendingMolecule = null;
        pendingAlloy = null;
        renderWidth = 0;
        renderHeight = 0;
        targetLineIndex = 0;
        totalTooltipLines = 0;
        maxTooltipWidth = 0;
        lastActiveTime = 0L;
        lastMouseX = -1;
        lastMouseY = -1;
    }
}
