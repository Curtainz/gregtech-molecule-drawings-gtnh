package com.rubenverg.moldraw;

/** Server-safe entry point; client classes are loaded only by the sided proxy. */
public class CommonLifecycle {

    public void postInit() {}

    public boolean isClientThread() {
        return false;
    }
}
