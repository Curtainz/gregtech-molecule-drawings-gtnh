package com.rubenverg.moldraw.client;

import java.lang.reflect.*;
import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.*;

import com.rubenverg.moldraw.*;
import com.rubenverg.moldraw.component.TooltipDrawPass;
import com.rubenverg.moldraw.core.NativeTooltipComponent;

/** Native ChromaticTooltips sections, independent of transient CodeChicken line IDs. */
public final class ChromaticIntegration {

    private static boolean available;
    private static Class<?> linesApi, componentApi, modifierApi;
    private static String error = "none", lastSubject = "none";
    private static long builds, draws;

    private ChromaticIntegration() {}

    public static boolean available() {
        return available;
    }

    public static String status() {
        return "chromaticNative=" + available
            + ", builds="
            + builds
            + ", draws="
            + draws
            + ", subject="
            + lastSubject
            + ", error="
            + error;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void initialize() {
        if (!MolDraw.isClientThread() || available || !cpw.mods.fml.common.Loader.isModLoaded("chromatictooltips"))
            return;
        try {
            Class<?> enricher = Class.forName("com.slprime.chromatictooltips.api.ITooltipEnricher");
            Class<?> registry = Class.forName("com.slprime.chromatictooltips.TooltipRegistry");
            Class<?> place = Class.forName("com.slprime.chromatictooltips.api.EnricherPlace");
            modifierApi = Class.forName("com.slprime.chromatictooltips.api.TooltipModifier");
            linesApi = Class.forName("com.slprime.chromatictooltips.api.TooltipLines");
            componentApi = Class.forName("com.slprime.chromatictooltips.api.ITooltipComponent");
            Class<?> contextApi = Class.forName("com.slprime.chromatictooltips.api.TooltipContext");
            for (String method : List.of("getFluid", "getItem", "getActiveModifier")) contextApi.getMethod(method);
            linesApi.getConstructor();
            linesApi.getMethod("line", String.class);
            linesApi.getMethod("line", componentApi);
            NativeTooltipComponent.create(componentApi, () -> 0, () -> 0, (x, y) -> {});
            Object body = Enum.valueOf((Class) place, "BODY");
            Object proxy = Proxy.newProxyInstance(
                enricher.getClassLoader(),
                new Class<?>[] { enricher },
                (self, method, args) -> switch (method.getName()) {
                case "sectionId" -> "gtmoldraw";
                case "place" -> body;
                case "mode" -> EnumSet.allOf((Class) modifierApi);
                case "build" -> build(args[0]);
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                case "toString" -> "GTMolDraw native enricher";
                default -> throw new UnsupportedOperationException(method.toString());
                });
            registry.getMethod("addEnricher", enricher)
                .invoke(null, proxy);
            available = true;
            MolDraw.LOGGER.info("ChromaticTooltips native GTMolDraw section registered");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            fail(failure);
        }
    }

    private static Object build(Object context) throws ReflectiveOperationException {
        Object lines = linesApi.getConstructor()
            .newInstance();
        if (!MolDraw.isClientThread() || !MolDrawConfig.INSTANCE.enabled || !MaterialRegistry.enabled()) return lines;
        try {
            builds++;
            var font = Minecraft.getMinecraft().fontRenderer;
            Object fluid = context.getClass()
                .getMethod("getFluid")
                .invoke(context);
            Object item = context.getClass()
                .getMethod("getItem")
                .invoke(context);
            DisplayPlan plan = DisplayPlan.EMPTY;
            if (fluid instanceof FluidStack stack && stack.getFluid() != null) {
                lastSubject = "fluid:" + stack.getFluid()
                    .getName();
                plan = MaterialTargetResolver.resolveFromFluid(stack.getFluid(), font);
            } else if (item instanceof ItemStack stack) {
                lastSubject = "item:" + stack.getUnlocalizedName();
                plan = MaterialTargetResolver.resolve(stack, font);
            } else if (Minecraft
                .getMinecraft().currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer gui) {
                    var stack = HoveredFluid.find(gui);
                    if (stack != null && stack.getFluid() != null) {
                        lastSubject = "fluid:" + stack.getFluid()
                            .getName();
                        plan = MaterialTargetResolver.resolveFromFluid(stack.getFluid(), font);
                    }
                }
            if (!plan.isValid()) {
                List<String> text = new ArrayList<>();
                Object raw = context.getClass()
                    .getMethod("getContextTooltip")
                    .invoke(context);
                if (raw instanceof List<?>components) for (Object component : components) {
                    try {
                        Object values = component.getClass()
                            .getMethod("getLines")
                            .invoke(component);
                        if (values instanceof List<?>strings) for (Object value : strings)
                            if (value instanceof String line && line.length() <= 4096 && text.size() < 128)
                                text.add(line);
                    } catch (NoSuchMethodException notText) {}
                }
                if (!text.isEmpty()) {
                    plan = MaterialTargetResolver.resolveFromTooltipLines(null, text, font);
                    if (plan.isValid()) lastSubject = "text:" + text.get(0)
                        + " -> "
                        + plan.data()
                            .material()
                            .id();
                }
            }
            Object modifier = context.getClass()
                .getMethod("getActiveModifier")
                .invoke(context);
            var state = NativeTooltipComponent.state(
                true,
                plan.isValid(),
                MolDrawConfig.INSTANCE.onlyShowOnShift,
                "SHIFT".equals(String.valueOf(modifier)));
            if (state == NativeTooltipComponent.State.HINT) {
                String key = plan.getType() == DisplayPlan.Type.MOLECULE ? "tooltip.gtmoldraw.shift_view_molecule"
                    : "tooltip.gtmoldraw.shift_view_alloy";
                linesApi.getMethod("line", String.class)
                    .invoke(lines, "§8[" + StatCollector.translateToLocal(key) + "]");
            } else if (state == NativeTooltipComponent.State.GRAPH) {
                var drawable = plan.getDrawable();
                long version = MaterialRegistry.version();
                Object component = NativeTooltipComponent.create(
                    componentApi,
                    () -> live(version) ? drawable.getWidth(font) : 0,
                    () -> live(version) ? drawable.getHeight(font) : 0,
                    (x, y) -> {
                        if (live(version)) {
                            draws++;
                            TooltipDrawPass.render(drawable, font, x, y);
                        }
                    });
                linesApi.getMethod("line", componentApi)
                    .invoke(lines, component);
            }
            error = "none";
            return lines;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            fail(failure);
            return lines;
        }
    }

    private static boolean live(long version) {
        return MolDraw.isClientThread() && MaterialRegistry.enabled() && version == MaterialRegistry.version();
    }

    private static void fail(Throwable failure) {
        String message = failure.toString();
        if (!message.equals(error)) MolDraw.LOGGER.error("ChromaticTooltips native adapter failure", failure);
        error = message;
    }

}
