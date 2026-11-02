package com.rubenverg.moldraw;

import java.awt.Dimension;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import com.rubenverg.moldraw.component.ITooltipDrawable;

public class NEITooltipHelper {

    private static boolean initialized = false;
    private static boolean neiAvailable = false;
    private static Method getTipLineIdMethod = null;
    private static Field tipLineHandlersField = null;
    private static Class<?> tooltipLineHandlerClass = null;

    // 针对每个具体的 ITooltipDrawable 实例独立缓存其代理 Handler，
    // 彻底解耦全局共享状态，杜绝由于帧交替清空与状态覆盖导致的闪烁与组件塌陷！
    private static final Map<ITooltipDrawable, Object> handlerProxyCache = Collections
        .synchronizedMap(new WeakHashMap<>());

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> guiDrawClass = Class.forName("codechicken.lib.gui.GuiDraw");

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

            try {
                tipLineHandlersField = guiDrawClass.getDeclaredField("tipLineHandlers");
                tipLineHandlersField.setAccessible(true);
            } catch (Throwable ignored) {}

            if (getTipLineIdMethod != null && tooltipLineHandlerClass != null) {
                neiAvailable = true;
                MolDraw.LOGGER.info("Initialized NEITooltipHelper with dynamic per-drawable line handler proxy.");
            }
        } catch (Throwable t) {
            neiAvailable = false;
            MolDraw.LOGGER.info("NEI ITooltipLineHandler not available: " + t.getMessage());
        }
    }

    public static boolean isNEIAvailable() {
        if (!initialized) init();
        return neiAvailable && tooltipLineHandlerClass != null;
    }

    private static Object createProxy(final ITooltipDrawable drawable, final FontRenderer font) {
        return Proxy.newProxyInstance(
            tooltipLineHandlerClass.getClassLoader(),
            new Class<?>[] { tooltipLineHandlerClass },
            new InvocationHandler() {

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String mName = method.getName();
                    if ("getSize".equals(mName)) {
                        return new Dimension(drawable.getWidth(font), drawable.getHeight(font));
                    } else if ("draw".equals(mName)) {
                        int x = (Integer) args[0];
                        int y = (Integer) args[1];
                        renderWithGL(drawable, font, x, y);
                        return null;
                    } else if ("hashCode".equals(mName)) {
                        return System.identityHashCode(proxy);
                    } else if ("equals".equals(mName)) {
                        return args != null && args.length > 0 && args[0] == proxy;
                    } else if ("toString".equals(mName)) {
                        return "MolDrawLineHandler[" + drawable.getClass()
                            .getSimpleName() + "]";
                    }
                    return null;
                }
            });
    }

    @SuppressWarnings("unchecked")
    public static synchronized String obtainLineToken(ITooltipDrawable drawable, FontRenderer font) {
        if (!isNEIAvailable() || drawable == null || font == null) return null;

        Object proxy = handlerProxyCache.get(drawable);
        if (proxy == null) {
            proxy = createProxy(drawable, font);
            handlerProxyCache.put(drawable, proxy);
        }

        // 优先直接同步到 tipLineHandlers 列表
        if (tipLineHandlersField != null) {
            try {
                List<Object> list = (List<Object>) tipLineHandlersField.get(null);
                if (list != null) {
                    int existing = list.indexOf(proxy);
                    if (existing >= 0) {
                        return "\u00a7x" + existing;
                    } else {
                        list.add(proxy);
                        return "\u00a7x" + (list.size() - 1);
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 回退逻辑：调用 GuiDraw.getTipLineId
        try {
            int id = (Integer) getTipLineIdMethod.invoke(null, proxy);
            return "\u00a7x" + id;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void clear() {
        // 无需清除持久的 handlerProxyCache，避免破坏正在被 ChromaticTooltips 缓存重用的渲染周期
    }

    private static void renderWithGL(ITooltipDrawable drawable, FontRenderer font, int x, int y) {
        if (drawable == null || font == null) return;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        drawable.render(font, x, y);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
