package com.rubenverg.moldraw.client;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fluids.*;

import com.google.gson.*;
import com.rubenverg.moldraw.*;
import com.rubenverg.moldraw.component.TooltipDrawPass;

/** Explicit developer smoke mode. Runs only on a client GUI draw event, then exits its test client. */
public final class ClientSmokeTest {

    private static boolean enabled, ran;

    private ClientSmokeTest() {}

    public static void prepare() {
        enabled = Files.isRegularFile(
            Minecraft.getMinecraft().mcDataDir.toPath()
                .resolve("gtmoldraw-smoke-test.enabled"));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void onDraw() {
        var mc = Minecraft.getMinecraft();
        if (!enabled || ran
            || !MolDraw.isClientThread()
            || !MaterialRegistry.enabled()
            || mc.currentScreen == null
            || mc.theWorld != null) return;
        ran = true;
        JsonObject report = new JsonObject();
        JsonArray scenarios = new JsonArray();
        report.addProperty("modVersion", MolDraw.VERSION);
        report.addProperty("test", "real client native fluid render smoke; not mouse-hover GUI automation");
        boolean passed = false;
        try {
            if (!ChromaticIntegration.available())
                throw new IllegalStateException("Native Chromatic adapter not registered");
            Class<?> handler = Class.forName("com.slprime.chromatictooltips.TooltipHandler"),
                registry = Class.forName("com.slprime.chromatictooltips.TooltipRegistry"),
                enricherApi = Class.forName("com.slprime.chromatictooltips.api.ITooltipEnricher"),
                requestApi = Class.forName("com.slprime.chromatictooltips.api.TooltipRequest"),
                contextApi = Class.forName("com.slprime.chromatictooltips.api.TooltipContext"),
                rendererApi = Class.forName("com.slprime.chromatictooltips.api.ITooltipRenderer"),
                modifierApi = Class.forName("com.slprime.chromatictooltips.api.TooltipModifier"),
                linesApi = Class.forName("com.slprime.chromatictooltips.api.TooltipLines");
            Object enricher = null;
            for (Object candidate : (List<?>) registry.getMethod("getEnrichers")
                .invoke(null))
                if ("gtmoldraw".equals(
                    enricherApi.getMethod("sectionId")
                        .invoke(candidate)))
                    enricher = candidate;
            if (enricher == null) throw new IllegalStateException("Native section missing");
            for (String name : List
                .of("water", "molten.steel", "molten.bisphenol_a", "molten.polybenzimidazole", "1,3dimethylbenzene")) {
                Fluid fluid = FluidRegistry.getFluid(name);
                if (fluid == null) throw new IllegalStateException("Missing fixture fluid: " + name);
                for (boolean textOnly : new boolean[] { false, true })
                    for (String mode : List.of("NONE", "SHIFT", "NONE", "SHIFT")) {
                        Object builder = handler.getMethod("builder")
                            .invoke(null);
                        Class<?> builderApi = builder.getClass();
                        builderApi.getMethod("context", String.class)
                            .invoke(builder, "fluid");
                        if (textOnly) builderApi.getMethod("line", String.class)
                            .invoke(builder, "1000 mB " + fluid.getLocalizedName(new FluidStack(fluid, 1000)));
                        else builderApi.getMethod("target", FluidStack.class)
                            .invoke(builder, new FluidStack(fluid, 1000));
                        Object request = builderApi.getMethod("build")
                            .invoke(builder);
                        Object renderer = handler.getMethod("getRendererFor", requestApi)
                            .invoke(null, request);
                        if (renderer == null) throw new IllegalStateException("No native tooltip renderer");
                        Object context = contextApi.getConstructor(requestApi, rendererApi)
                            .newInstance(request, renderer);
                        contextApi.getMethod("setScaleFactor", int.class)
                            .invoke(context, 1);
                        contextApi.getMethod("setActiveModifier", modifierApi)
                            .invoke(context, Enum.valueOf((Class) modifierApi, mode));
                        Object lines = enricherApi.getMethod("build", contextApi)
                            .invoke(enricher, context);
                        List<?> components = (List<?>) linesApi.getMethod("build", contextApi)
                            .invoke(lines, context);
                        if (components.size() != 1) throw new IllegalStateException(
                            "Expected exactly one native hint/graphic for " + name
                                + "/"
                                + mode
                                + "/text="
                                + textOnly
                                + "; got "
                                + components.size());
                        boolean graph = !MolDrawConfig.INSTANCE.onlyShowOnShift || mode.equals("SHIFT");
                        if (Proxy.isProxyClass(
                            components.get(0)
                                .getClass())
                            != graph) throw new IllegalStateException("Wrong Shift component state");
                        contextApi.getMethod("addSection", String.class, List.class)
                            .invoke(context, "gtmoldraw", components);
                        java.awt.Dimension size = (java.awt.Dimension) contextApi.getMethod("getTooltipSize")
                            .invoke(context);
                        if (size.width < 1 || size.height < 1)
                            throw new IllegalStateException("Empty native tooltip dimensions");
                        for (int i = 0; i < 16 && org.lwjgl.opengl.GL11.glGetError() != 0; i++) {}
                        contextApi.getMethod("drawAt", int.class, int.class)
                            .invoke(context, 24, 32);
                        int gl = org.lwjgl.opengl.GL11.glGetError();
                        if (gl != 0 || TooltipDrawPass.failureCount() != 0) throw new IllegalStateException(
                            "Render failure: GL=" + gl + ", draw failures=" + TooltipDrawPass.failureCount());
                        JsonObject result = new JsonObject();
                        result.addProperty("fluid", name);
                        result.addProperty("textOnly", textOnly);
                        result.addProperty("modifier", mode);
                        result.addProperty("width", size.width);
                        result.addProperty("height", size.height);
                        result.addProperty("graph", graph);
                        scenarios.add(result);
                    }
            }
            passed = true;
        } catch (Exception | LinkageError failure) {
            report.addProperty("failure", failure.toString());
            MolDraw.LOGGER.error("Client render smoke failed", failure);
        } finally {
            report.addProperty("passed", passed);
            report.add("scenarios", scenarios);
            report.addProperty("adapter", ChromaticIntegration.status());
            try {
                Files.writeString(
                    mc.mcDataDir.toPath()
                        .resolve("gtmoldraw-smoke-results.json"),
                    new GsonBuilder().setPrettyPrinting()
                        .create()
                        .toJson(report));
            } catch (java.io.IOException failure) {
                MolDraw.LOGGER.error("Cannot write client smoke report", failure);
            }
            MolDraw.LOGGER.info("Client render smoke: passed={}, scenarios={}", passed, scenarios.size());
            mc.shutdown();
        }
    }
}
