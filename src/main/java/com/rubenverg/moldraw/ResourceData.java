package com.rubenverg.moldraw;

import java.io.IOException;
import java.io.InputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/** Resolve through Minecraft so selected resource packs can override bundled data. */
public final class ResourceData {

    private ResourceData() {}

    public static InputStream open(String namespace, String path) throws IOException {
        if (!MolDraw.isClientThread()) throw new IllegalStateException("Resources must be read on the client thread");
        try {
            return Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(new ResourceLocation(namespace, path))
                .getInputStream();
        } catch (java.io.FileNotFoundException missing) {
            return ResourceData.class.getResourceAsStream("/assets/" + namespace + "/" + path);
        }
    }

    public static void invalidate() {
        if (!MolDraw.isClientThread())
            throw new IllegalStateException("Caches must be invalidated on the client thread");
        MoleculeManager.clearCache();
        AlloyManager.clearCache();
        MoleculeColorize.invalidateColorCache();
        MaterialRegistry.clear();
        RendererCache.clear();
        com.rubenverg.moldraw.component.TooltipDrawPass.clearFailures();
        com.rubenverg.moldraw.component.TooltipRenderContext.clear();
    }
}
