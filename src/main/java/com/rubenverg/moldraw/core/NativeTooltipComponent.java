package com.rubenverg.moldraw.core;

import java.lang.reflect.*;
import java.util.function.IntSupplier;

/** Optional component ABI adapter. All layout and drawing callbacks are supplied by the client. */
public final class NativeTooltipComponent {

    @FunctionalInterface
    public interface Draw {

        void draw(int x, int y);
    }

    private NativeTooltipComponent() {}

    public static Object create(Class<?> api, IntSupplier width, IntSupplier height, Draw draw) {
        if (!api.isInterface()) throw new IllegalArgumentException("Tooltip component API must be an interface");
        try {
            api.getMethod("getWidth");
            api.getMethod("getHeight");
            api.getMethod("getSpacing");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Unsupported tooltip component ABI", e);
        }
        boolean drawMethod = java.util.Arrays.stream(api.getMethods())
            .anyMatch(
                m -> m.getName()
                    .equals("draw") && m.getParameterCount() == 4
                    && m.getParameterTypes()[0] == int.class
                    && m.getParameterTypes()[1] == int.class
                    && m.getParameterTypes()[2] == int.class);
        if (!drawMethod) throw new IllegalArgumentException("Unsupported native drawing ABI");
        return Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] { api }, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getWidth" -> width.getAsInt();
                case "getHeight" -> height.getAsInt();
                case "getSpacing" -> 2;
                case "draw" -> {
                    draw.draw((int) args[0], (int) args[1]);
                    yield null;
                }
                case "paginate" -> {
                    Object result = Array.newInstance(api, 1);
                    Array.set(result, 0, proxy);
                    yield result;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "GTMolDraw native component";
                default -> throw new UnsupportedOperationException(method.toString());
            };
        });
    }

    public enum State {
        NONE,
        HINT,
        GRAPH
    }

    public static State state(boolean enabled, boolean valid, boolean shiftOnly, boolean shifted) {
        return !enabled || !valid ? State.NONE : shiftOnly && !shifted ? State.HINT : State.GRAPH;
    }
}
