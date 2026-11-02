package com.rubenverg.moldraw.component;

import java.util.*;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;

import com.rubenverg.moldraw.AlloyManager;
import com.rubenverg.moldraw.MolDrawConfig;
import com.rubenverg.moldraw.MoleculeColorize;
import com.rubenverg.moldraw.util.FormulaParser;
import com.rubenverg.moldraw.util.Pair;

import gregtech.api.enums.Materials;
import gregtech.api.objects.MaterialStack;

public class AlloyRenderer implements ITooltipDrawable {

    public static final int BASE_WIDTH = 190;
    private final int baseHeight;

    private final Materials material;
    private final List<Pair<Materials, Long>> components;
    private final long total;
    private final List<Pair<Double, Materials>> stops;
    private final List<Pair<Double, Materials>> centers;
    private final List<Pair<Vector2i, Materials>> textStarts;
    private final List<String> textLines;
    private final List<Integer> textColors;

    private final int addTop, addBottom;
    private final int addLeft, addRight;

    private static long maybeMultiplyByMass(Materials mat, long count) {
        if (mat == null || mat == Materials._NULL) return count;
        if (MolDrawConfig.INSTANCE.alloy.partsByMass) {
            long mass = mat.getMass();
            return count * (mass > 0 ? mass : 100);
        }
        return count;
    }

    public static boolean hasValidComponents(Materials material) {
        if (material == null || material == Materials._NULL) return false;
        List<Pair<Materials, Long>> comps = deriveComponents(material);
        return comps != null && comps.size() > 1;
    }

    public static List<Pair<Materials, Long>> deriveComponents(Materials material) {
        if (material == null || material == Materials._NULL) return Collections.emptyList();

        List<Pair<Materials, Long>> raw = decomposeRecursive(material, new HashSet<>());
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        // 合并相同材料
        Map<Materials, Long> merged = new LinkedHashMap<>();
        for (Pair<Materials, Long> p : raw) {
            if (p != null && p.getA() != null && p.getB() != null && p.getB() > 0) {
                merged.put(p.getA(), merged.getOrDefault(p.getA(), 0L) + p.getB());
            }
        }

        List<Pair<Materials, Long>> finalComponents = new ArrayList<>();
        for (Map.Entry<Materials, Long> entry : merged.entrySet()) {
            finalComponents.add(new Pair<>(entry.getKey(), entry.getValue()));
        }

        finalComponents.sort(
            (a, b) -> Long.compare(maybeMultiplyByMass(b.getA(), b.getB()), maybeMultiplyByMass(a.getA(), a.getB())));
        return finalComponents;
    }

    private static List<Pair<Materials, Long>> decomposeRecursive(Materials material, Set<Materials> visited) {
        if (material == null || material == Materials._NULL) return Collections.emptyList();
        if (visited.contains(material)) {
            return Collections.singletonList(new Pair<>(material, 1L));
        }
        visited.add(material);

        // 1. 优先检查外部覆写配置
        List<Pair<Materials, Long>> override = AlloyManager.getAlloyComponents(material);
        if (override != null && !override.isEmpty()) {
            if (!MolDrawConfig.INSTANCE.alloy.recursive) {
                return override;
            }
            List<Pair<Materials, Long>> result = new ArrayList<>();
            for (Pair<Materials, Long> p : override) {
                if (p.getA() != null && p.getA().mElement == null && p.getA() != material) {
                    List<Pair<Materials, Long>> sub = decomposeRecursive(p.getA(), new HashSet<>(visited));
                    for (Pair<Materials, Long> sp : sub) {
                        result.add(new Pair<>(sp.getA(), sp.getB() * p.getB()));
                    }
                } else {
                    result.add(p);
                }
            }
            return result;
        }

        // 2. 如果本身就是不可再分的基础单质元素，直接返回
        if (material.mElement != null) {
            return Collections.singletonList(new Pair<>(material, 1L));
        }

        List<Pair<Materials, Long>> result = new ArrayList<>();

        // 3. 检查是否有配方材料列表 mMaterialList
        if (material.mMaterialList != null && !material.mMaterialList.isEmpty()) {
            for (MaterialStack ms : material.mMaterialList) {
                if (ms.mMaterial != null && ms.mMaterial != Materials._NULL) {
                    if (MolDrawConfig.INSTANCE.alloy.recursive && ms.mMaterial != material) {
                        List<Pair<Materials, Long>> sub = decomposeRecursive(ms.mMaterial, new HashSet<>(visited));
                        for (Pair<Materials, Long> p : sub) {
                            result.add(new Pair<>(p.getA(), p.getB() * ms.mAmount));
                        }
                    } else {
                        result.add(new Pair<>(ms.mMaterial, ms.mAmount));
                    }
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }

        // 4. 若无 mMaterialList 或为空，但存在复合化学式（如红石、二氧化硅、脉冲混合物等），通过化学式解析为基础元素
        String formula = material.getChemicalFormula();
        if (formula != null && !formula.isEmpty()) {
            List<Pair<Materials, Long>> parsed = FormulaParser.parseToWeightedComponents(formula);
            if (parsed != null && !parsed.isEmpty()) {
                return parsed;
            }
        }

        return Collections.singletonList(new Pair<>(material, 1L));
    }

    public AlloyRenderer(Materials material, FontRenderer font) {
        this.material = material;
        this.baseHeight = MolDrawConfig.INSTANCE.alloy.pieChartRadius * 5 / 2;

        List<Pair<Materials, Long>> raw = deriveComponents(material);
        List<Pair<Materials, Long>> weighted = new ArrayList<>();
        for (Pair<Materials, Long> p : raw) {
            weighted.add(new Pair<>(p.getA(), maybeMultiplyByMass(p.getA(), p.getB())));
        }

        // 关键：按质量降序排序（大份额元素在最前）
        weighted.sort((a, b) -> Long.compare(b.getB(), a.getB()));

        long rawTotal = weighted.stream()
            .mapToLong(Pair::getB)
            .sum();
        long safeTotal = rawTotal > 0 ? rawTotal : 1;

        // 简略显示阈值折叠：根据 maxComponentsDisplayed 与 minPercentageThreshold
        int maxDisplayed = Math.max(2, MolDrawConfig.INSTANCE.alloy.maxComponentsDisplayed);
        double minPercent = Math.max(0.0, MolDrawConfig.INSTANCE.alloy.minPercentageThreshold);

        List<Pair<Materials, Long>> folded = new ArrayList<>();
        long otherTotal = 0L;

        for (int i = 0; i < weighted.size(); i++) {
            Pair<Materials, Long> p = weighted.get(i);
            double pct = p.getB() * 100.0 / safeTotal;
            // 如果超出最大显示数量，或者占比低于阈值（且后面还有更多微量元素），折叠进“其他”
            if (i >= maxDisplayed - 1 || (pct < minPercent && weighted.size() - i > 1)) {
                otherTotal += p.getB();
            } else {
                folded.add(p);
            }
        }

        if (otherTotal > 0) {
            folded.add(new Pair<>(null, otherTotal));
        }

        this.components = folded;
        this.total = safeTotal;

        final List<Pair<Long, Materials>> s = new ArrayList<>(), c = new ArrayList<>();
        long current = 0L;
        for (final var comp : this.components) {
            s.add(new Pair<>(current, comp.getA()));
            c.add(new Pair<>(current, comp.getA()));
            current += comp.getB();
        }

        this.stops = s.stream()
            .map(pair -> new Pair<>(Math.PI * 2 * pair.getA() / safeTotal, pair.getB()))
            .toList();

        if (!c.isEmpty()) c.remove(0);
        c.add(new Pair<>(safeTotal, Materials._NULL));
        this.centers = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) {
            double avg = Math.PI * (s.get(i)
                .getA()
                + c.get(i)
                    .getA())
                / safeTotal;
            this.centers.add(
                new Pair<>(
                    avg,
                    s.get(i)
                        .getB()));
        }

        final List<Pair<Vector2i, Materials>> ts = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        int atMostY = Integer.MAX_VALUE, atLeastY = Integer.MIN_VALUE;
        int al = 0, ar = 0;
        int radius = MolDrawConfig.INSTANCE.alloy.pieChartRadius;

        for (int i = 0; i < this.components.size(); i++) {
            final var count = this.components.get(i)
                .getB();
            final var mat = this.components.get(i)
                .getA();
            final var center = this.centers.get(i)
                .getA();
            final int cy = (int) (-Math.cos(center) * 0.9 * radius);

            final boolean left = center > Math.PI;
            final int ex = (left ? -1 : 1) * (radius + 10);
            final double percentage = count * 100d / safeTotal;
            final String percentageString = percentage < 0.1 ? "<0.1%"
                : String.format(Locale.ROOT, "%.1f%%", percentage);

            String formula;
            int textColor;
            if (mat == null) {
                formula = StatCollector.translateToLocal("tooltip.moldraw.other");
                textColor = 0xFFAAAAAA;
            } else if (mat == Materials._NULL) {
                formula = StatCollector.translateToLocal("tooltip.moldraw.unknown");
                textColor = 0xFFBB66FF;
            } else {
                formula = mat.getChemicalFormula();
                if (formula == null || formula.isEmpty()) {
                    formula = mat.mName != null ? mat.mName : mat.name();
                }
                textColor = MoleculeColorize.getColorForMaterial(mat);
            }

            final String text = percentageString + " " + formula;
            final int width = font.getStringWidth(text);
            lines.add(text);
            colors.add(textColor);

            if (left) {
                final int topY = Math.min(cy - font.FONT_HEIGHT / 2, atMostY);
                ts.add(new Pair<>(new Vector2i(ex - 5 - width, topY), mat));
                atMostY = topY - font.FONT_HEIGHT - 1;
                al = Math.max(al, width);
            } else {
                final int topY = Math.max(cy - font.FONT_HEIGHT / 2, atLeastY);
                ts.add(new Pair<>(new Vector2i(ex + 5, topY), mat));
                atLeastY = topY + font.FONT_HEIGHT + 1;
                ar = Math.max(ar, width);
            }
        }

        this.textStarts = ts;
        this.textLines = lines;
        this.textColors = colors;
        this.addTop = Math.max(0, -atMostY - baseHeight / 2);
        this.addBottom = Math.max(0, atLeastY - baseHeight / 2);
        this.addLeft = Math.max(0, al + radius + 15 - BASE_WIDTH / 2);
        this.addRight = Math.max(0, ar + radius + 15 - BASE_WIDTH / 2);
    }

    public int getWidth(FontRenderer font) {
        return BASE_WIDTH + addLeft + addRight;
    }

    public int getHeight(FontRenderer font) {
        return baseHeight + addBottom + addTop;
    }

    private int getColor(Materials mat) {
        if (mat == null) return 0xFF555555;
        if (mat == Materials._NULL) return 0xFF8833BB; // 神秘虚空深紫色
        return MoleculeColorize.getColorForMaterial(mat);
    }

    @Override
    public void render(FontRenderer font, int x, int y) {
        if (total <= 0 || components.isEmpty()) return;

        final int xm = BASE_WIDTH / 2 + addLeft + x;
        final int ym = baseHeight / 2 + addTop + y;
        final int radius = MolDrawConfig.INSTANCE.alloy.pieChartRadius;

        // 1. 绘制各成分扇区 (OpenGL GL_TRIANGLE_FAN)，严格受 glPushAttrib 保护
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int i = 0; i < stops.size(); i++) {
            double angleStart = stops.get(i)
                .getA();
            double angleEnd = (i + 1 < stops.size()) ? stops.get(i + 1)
                .getA() : (2.0 * Math.PI);
            int color = getColor(
                stops.get(i)
                    .getB());

            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float a = ((color >> 24) & 0xFF) / 255.0f;
            if (a <= 0.0f) a = 1.0f;

            GL11.glColor4f(r, g, b, a);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(xm, ym);
            int segments = Math.max(3, (int) Math.ceil((angleEnd - angleStart) / (Math.PI / 18.0)));
            for (int s = 0; s <= segments; s++) {
                double theta = angleStart + (angleEnd - angleStart) * s / segments;
                float vx = (float) (xm + Math.sin(theta) * radius);
                float vy = (float) (ym - Math.cos(theta) * radius);
                GL11.glVertex2f(vx, vy);
            }
            GL11.glEnd();
        }

        // 2. 扇区分割线（仅当成分多于1个时）
        if (stops.size() > 1) {
            GL11.glColor4f(0.15f, 0.15f, 0.15f, 0.6f);
            GL11.glLineWidth(1.0f);
            GL11.glBegin(GL11.GL_LINES);
            for (int i = 0; i < stops.size(); i++) {
                double theta = stops.get(i)
                    .getA();
                GL11.glVertex2f(xm, ym);
                GL11.glVertex2f((float) (xm + Math.sin(theta) * radius), (float) (ym - Math.cos(theta) * radius));
            }
            GL11.glEnd();
        }

        // 3. 饼图外边框圆形 (GL_LINE_LOOP)
        GL11.glColor4f(0.1f, 0.1f, 0.1f, 0.85f);
        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        int borderSegments = 48;
        for (int i = 0; i < borderSegments; i++) {
            double theta = i * (2.0 * Math.PI / borderSegments);
            GL11.glVertex2f((float) (xm + Math.sin(theta) * radius), (float) (ym - Math.cos(theta) * radius));
        }
        GL11.glEnd();

        // 4. 引出线 (GL_LINES)
        GL11.glColor4f(0.66f, 0.66f, 0.66f, 0.8f);
        GL11.glLineWidth(1.2f);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < components.size(); i++) {
            final double center = centers.get(i)
                .getA();
            final var textStart = textStarts.get(i)
                .getFirst();
            final String text = textLines.get(i);
            final boolean left = center > Math.PI;

            final float lx0 = xm + (float) (Math.sin(center) * 0.9 * radius);
            final float ly0 = ym - (float) (Math.cos(center) * 0.9 * radius);
            final float lx1 = xm + textStart.x + (left ? font.getStringWidth(text) + 2 : -2);
            final float ly1 = ym + textStart.y + font.FONT_HEIGHT / 2.0f;

            GL11.glVertex2f(lx0, ly0);
            GL11.glVertex2f(lx1, ly1);
        }
        GL11.glEnd();
        GL11.glPopAttrib();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // 5. 文字渲染
        for (int i = 0; i < components.size(); i++) {
            final var textStart = textStarts.get(i)
                .getFirst();
            final String text = textLines.get(i);
            final int color = textColors.get(i);
            font.drawStringWithShadow(text, xm + textStart.x, ym + textStart.y, color);
        }
    }
}
