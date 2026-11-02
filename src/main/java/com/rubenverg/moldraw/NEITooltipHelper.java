package com.rubenverg.moldraw;

import java.awt.Dimension;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import com.rubenverg.moldraw.component.ITooltipDrawable;

public class NEITooltipHelper {

    private static boolean initialized = false;
    private static boolean neiAvailable = false;
    private static int lineId = -1;
    private static String lineToken = null;

    private static ITooltipDrawable currentDrawable = null;
    private static FontRenderer currentFont = null;
    private static boolean renderedByNEI = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> guiDrawClass = Class.forName("codechicken.lib.gui.GuiDraw");
            Class<?> tooltipLineHandlerClass = null;
            Method getTipLineIdMethod = null;

            for (Class<?> inner : guiDrawClass.getDeclaredClasses()) {
                if ("ITooltipLineHandler".equals(inner.getSimpleName())) {
                    tooltipLineHandlerClass = inner;
                    break;
                }
            }

            if (tooltipLineHandlerClass == null) {
                tooltipLineHandlerClass = Class.forName("codechicken.lib.gui.GuiDraw$ITooltipLineHandler");
            }

            if (tooltipLineHandlerClass != null) {
                getTipLineIdMethod = guiDrawClass.getMethod("getTipLineId", tooltipLineHandlerClass);
            }

            if (getTipLineIdMethod != null && tooltipLineHandlerClass != null) {
                Object proxy = Proxy.newProxyInstance(
                    tooltipLineHandlerClass.getClassLoader(),
                    new Class<?>[] { tooltipLineHandlerClass },
                    new InvocationHandler() {

                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String mName = method.getName();
                            if ("getSize".equals(mName)) {
                                if (currentDrawable != null && currentFont != null) {
                                    return new Dimension(
                                        currentDrawable.getWidth(currentFont),
                                        currentDrawable.getHeight(currentFont));
                                }
                                return new Dimension(0, 0);
                            } else if ("draw".equals(mName)) {
                                if (currentDrawable != null && currentFont != null) {
                                    int x = (Integer) args[0];
                                    int y = (Integer) args[1];
                                    renderWithGL(currentDrawable, currentFont, x, y);
                                    renderedByNEI = true;
                                }
                                return null;
                            } else if ("hashCode".equals(mName)) {
                                return System.identityHashCode(proxy);
                            } else if ("equals".equals(mName)) {
                                return args != null && args.length > 0 && args[0] == proxy;
                            } else if ("toString".equals(mName)) {
                                return "MolDrawSingletonLineHandler";
                            }
                            return null;
                        }
                    });

                lineId = (Integer) getTipLineIdMethod.invoke(null, proxy);
                lineToken = "\u00a7x" + lineId;
                neiAvailable = true;
                MolDraw.LOGGER.info("Registered singleton NEI ITooltipLineHandler with token: \\u00a7x" + lineId);
            }
        } catch (Throwable t) {
            neiAvailable = false;
            lineId = -1;
            lineToken = null;
            MolDraw.LOGGER.info("NEI ITooltipLineHandler not available: " + t.getMessage());
        }
    }

    public static boolean isNEIAvailable() {
        if (!initialized) init();
        return neiAvailable;
    }

    public static String getLineToken() {
        if (!initialized) init();
        return lineToken;
    }

    public static void setCurrentDrawable(ITooltipDrawable drawable, FontRenderer font) {
        currentDrawable = drawable;
        currentFont = font;
        renderedByNEI = false;
    }

    public static boolean wasRenderedByNEI() {
        return renderedByNEI;
    }

    public static void clear() {
        currentDrawable = null;
        currentFont = null;
        renderedByNEI = false;
    }

    private static void renderWithGL(ITooltipDrawable drawable, FontRenderer font, int x, int y) {
        if (drawable == null) return;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawable.render(font, x, y);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
