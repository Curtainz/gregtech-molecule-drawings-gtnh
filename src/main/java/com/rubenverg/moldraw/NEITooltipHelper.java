package com.rubenverg.moldraw;

import java.awt.Dimension;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import com.rubenverg.moldraw.component.AlloyRenderer;
import com.rubenverg.moldraw.component.MoleculeRenderer;

public class NEITooltipHelper {

    private static boolean checked = false;
    private static Class<?> guiDrawClass = null;
    private static Class<?> tooltipLineHandlerClass = null;
    private static Method getTipLineIdMethod = null;

    public static boolean isNEIAvailable() {
        if (!checked) {
            checked = true;
            try {
                guiDrawClass = Class.forName("codechicken.lib.gui.GuiDraw");
                tooltipLineHandlerClass = Class.forName("codechicken.lib.gui.GuiDraw$ITooltipLineHandler");
                if (tooltipLineHandlerClass != null && guiDrawClass != null) {
                    getTipLineIdMethod = guiDrawClass.getMethod("getTipLineId", tooltipLineHandlerClass);
                }
            } catch (Throwable t1) {
                // 回退尝试寻找可能存在的不同路径或类名
                try {
                    if (guiDrawClass != null) {
                        for (Class<?> inner : guiDrawClass.getDeclaredClasses()) {
                            if (inner.getSimpleName()
                                .equals("ITooltipLineHandler")) {
                                tooltipLineHandlerClass = inner;
                                getTipLineIdMethod = guiDrawClass.getMethod("getTipLineId", inner);
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return getTipLineIdMethod != null && tooltipLineHandlerClass != null;
    }

    public static String registerMoleculeHandler(MoleculeRenderer renderer, FontRenderer font) {
        if (!isNEIAvailable()) return null;
        final int w = renderer.getWidth(font);
        final int h = renderer.getHeight(font);

        try {
            Object proxy = Proxy.newProxyInstance(
                tooltipLineHandlerClass.getClassLoader(),
                new Class<?>[] { tooltipLineHandlerClass },
                new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String mName = method.getName();
                        if (mName.equals("getSize")) {
                            return new Dimension(w, h);
                        } else if (mName.equals("draw")) {
                            int x = (Integer) args[0];
                            int y = (Integer) args[1];
                            renderWithGL(renderer, null, font, x, y);
                            return null;
                        } else if (mName.equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        } else if (mName.equals("equals")) {
                            return args != null && args.length > 0 && args[0] == proxy;
                        } else if (mName.equals("toString")) {
                            return "MolDrawTooltipHandler@" + Integer.toHexString(System.identityHashCode(proxy));
                        }
                        return null;
                    }
                });

            int id = (Integer) getTipLineIdMethod.invoke(null, proxy);
            return "\u00a7x" + id;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String registerAlloyHandler(AlloyRenderer renderer, FontRenderer font) {
        if (!isNEIAvailable()) return null;
        final int w = renderer.getWidth(font);
        final int h = renderer.getHeight(font);

        try {
            Object proxy = Proxy.newProxyInstance(
                tooltipLineHandlerClass.getClassLoader(),
                new Class<?>[] { tooltipLineHandlerClass },
                new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String mName = method.getName();
                        if (mName.equals("getSize")) {
                            return new Dimension(w, h);
                        } else if (mName.equals("draw")) {
                            int x = (Integer) args[0];
                            int y = (Integer) args[1];
                            renderWithGL(null, renderer, font, x, y);
                            return null;
                        } else if (mName.equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        } else if (mName.equals("equals")) {
                            return args != null && args.length > 0 && args[0] == proxy;
                        } else if (mName.equals("toString")) {
                            return "MolDrawAlloyTooltipHandler@" + Integer.toHexString(System.identityHashCode(proxy));
                        }
                        return null;
                    }
                });

            int id = (Integer) getTipLineIdMethod.invoke(null, proxy);
            return "\u00a7x" + id;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void renderWithGL(MoleculeRenderer mol, AlloyRenderer alloy, FontRenderer font, int x, int y) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (mol != null) {
            mol.render(font, x, y);
        } else if (alloy != null) {
            alloy.render(font, x, y);
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
