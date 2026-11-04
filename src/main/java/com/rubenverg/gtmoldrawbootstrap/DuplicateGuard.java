package com.rubenverg.gtmoldrawbootstrap;

import java.io.File;
import java.util.Map;

import com.rubenverg.moldraw.core.InstallationScanner;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/** Installation validation only: no transformers, mixins, rendering or material initialization. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("GTMolDraw installation check")
public final class DuplicateGuard implements IFMLLoadingPlugin {

    public String[] getASMTransformerClass() {
        return new String[0];
    }

    public String getModContainerClass() {
        return null;
    }

    public String getSetupClass() {
        return null;
    }

    public String getAccessTransformerClass() {
        return null;
    }

    public void injectData(Map<String, Object> data) {
        Object location = data.get("mcLocation");
        if (!(location instanceof File)) {
            Object[] injected = cpw.mods.fml.relauncher.FMLInjectionData.data();
            if (injected != null && injected.length > 6) location = injected[6];
        }
        if (location instanceof File game) try {
            InstallationScanner.requireSingle(
                game.toPath()
                    .resolve("mods"));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot verify GTMolDraw installations", failure);
        }
        else throw new IllegalStateException("GTMolDraw installation check: game directory unavailable");
    }
}
