package com.rubenverg.moldraw;

/** Cache invalidation only. Legacy resource mounting has been removed from the production artifact. */
public final class ResourceData {

    private ResourceData() {}

    /** Source compatibility for the archived migration adapters; never reads bundled resources. */
    public static java.io.InputStream open(String namespace, String path) {
        throw new UnsupportedOperationException("Legacy resources are not mounted; install external packs");
    }

    public static void invalidate() {
        if (!MolDraw.isClientThread()) throw new IllegalStateException("Cache invalidation requires client thread");
        MoleculeColorize.invalidateColorCache();
        MaterialRegistry.clear();
        RendererCache.clear();
        com.rubenverg.moldraw.component.TooltipDrawPass.clearFailures();
        com.rubenverg.moldraw.component.TooltipRenderContext.clear();
    }
}
