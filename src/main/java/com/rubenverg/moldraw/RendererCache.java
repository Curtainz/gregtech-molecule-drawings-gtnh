package com.rubenverg.moldraw;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

import com.rubenverg.moldraw.component.*;
import com.rubenverg.moldraw.core.*;

/** Bounded presentation cache keyed by stable identity, resource/config epoch, metrics and layout settings. */
public final class RendererCache {

    private record FontKey(FontRenderer font, int height, boolean unicode, boolean bidi, String language) {}

    private record Settings(int scale, int radius, int limit, double threshold, boolean mass, boolean recursive,
        int color) {}

    private record Key(String id, long version, FontKey font, Settings settings, DisplayPlan.Type type) {}

    private static final BoundedCache<Key, LayoutRenderer> CACHE = new BoundedCache<>(256);
    private static final Map<Key, String> FAILURES = new LinkedHashMap<>() {

        protected boolean removeEldestEntry(Map.Entry<Key, String> e) {
            return size() > 256;
        }
    };

    private static LayoutRenderer attempt(Key key, java.util.function.Supplier<LayoutRenderer> build) {
        try {
            return build.get();
        } catch (RuntimeException failure) {
            FAILURES.put(key, "LAYOUT_FAILED:" + key.id() + ":" + failure.getMessage());
            MolDraw.LOGGER.error("Cannot lay out material {}", key.id(), failure);
            return new LayoutRenderer(new TooltipLayout(0, 0, List.of()));
        }
    }

    public static List<String> diagnostics(String id) {
        return FAILURES.entrySet()
            .stream()
            .filter(
                e -> e.getKey()
                    .id()
                    .equals(id))
            .map(Map.Entry::getValue)
            .toList();
    }

    private RendererCache() {}

    public static TextMeasure measure(FontRenderer font) {
        return new TextMeasure() {

            public int width(String text) {
                return font.getStringWidth(text);
            }

            public int lineHeight() {
                return font.FONT_HEIGHT;
            }
        };
    }

    private static Key key(String id, FontRenderer font, DisplayPlan.Type type) {
        var c = MolDrawConfig.INSTANCE;
        var f = new FontKey(
            font,
            font.FONT_HEIGHT,
            font.getUnicodeFlag(),
            font.getBidiFlag(),
            Minecraft.getMinecraft()
                .getLanguageManager()
                .getCurrentLanguage()
                .getLanguageCode());
        var s = new Settings(
            c.molecule.moleculeScale,
            c.alloy.pieChartRadius,
            c.alloy.maxComponentsDisplayed,
            c.alloy.minPercentageThreshold,
            c.alloy.partsByMass,
            c.alloy.recursive,
            MoleculeColorize.configColor(null));
        return new Key(id, MaterialRegistry.version(), f, s, type);
    }

    public static LayoutRenderer molecule(MaterialRegistry.Entry entry, FontRenderer font) {
        return CACHE.get(
            key(
                entry.definition()
                    .id(),
                font,
                DisplayPlan.Type.MOLECULE),
            k -> attempt(
                k,
                () -> new MoleculeRenderer(
                    entry.molecule()
                        .value(),
                    measure(font),
                    MolDrawConfig.INSTANCE.molecule.moleculeScale,
                    MoleculeColorize.configColor(null))));
    }

    public static CompositionResolver.Result composition(MaterialRegistry.Entry entry) {
        return MaterialRegistry.composition(
            entry.definition()
                .id(),
            MolDrawConfig.INSTANCE.alloy.partsByMass,
            MolDrawConfig.INSTANCE.alloy.recursive);
    }

    public static LayoutRenderer alloy(MaterialRegistry.Entry entry, FontRenderer font,
        CompositionResolver.Result composition) {
        return CACHE.get(
            key(
                entry.definition()
                    .id(),
                font,
                DisplayPlan.Type.ALLOY),
            k -> attempt(k, () -> {
                var c = MolDrawConfig.INSTANCE.alloy;
                var folded = CompositionResolver
                    .fold(composition.shares(), c.maxComponentsDisplayed, c.minPercentageThreshold);
                List<PieLayout.Slice> slices = new ArrayList<>();
                for (var part : folded.entrySet()) {
                    var d = MaterialRegistry.definition(part.getKey());
                    boolean other = part.getKey()
                        .equals("gtmoldraw:other");
                    String label = other ? StatCollector.translateToLocal("tooltip.gtmoldraw.other")
                        : d == null ? part.getKey()
                            : d.formula()
                                .isEmpty() ? d.name() : d.formula();
                    slices.add(
                        new PieLayout.Slice(
                            part.getKey(),
                            part.getValue(),
                            label,
                            other ? 0xFFAAAAAA : d == null ? 0xFFBB66FF : d.color()));
                }
                var layout = PieLayout.build(slices, c.pieChartRadius, measure(font));
                if (c.partsByMass && !MaterialRegistry.composition(
                    entry.definition()
                        .id(),
                    true,
                    c.recursive)
                    .valid()) {
                    var commands = new ArrayList<TooltipLayout.Command>(layout.commands());
                    String caption = StatCollector.translateToLocal("tooltip.gtmoldraw.quantity_fallback");
                    commands.add(new TooltipLayout.Text(caption, 0, layout.height() + 2, 0xFFFFCC66));
                    layout = new TooltipLayout(
                        Math.max(layout.width(), font.getStringWidth(caption) + 1),
                        layout.height() + font.FONT_HEIGHT + 4,
                        commands);
                }
                return new AlloyRenderer(layout);
            }));
    }

    public static void clear() {
        CACHE.clear();
        FAILURES.clear();
    }
}
