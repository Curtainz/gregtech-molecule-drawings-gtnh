package com.rubenverg.moldraw;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = MolDraw.MOD_ID,
    name = "GregTech Molecule Drawings",
    version = MolDraw.VERSION,
    dependencies = "required-after:gregtech;after:NotEnoughItems",
    guiFactory = "com.rubenverg.moldraw.gui.MolDrawGuiFactory",
    acceptableRemoteVersions = "*")
public class MolDraw {

    public static final String MOD_ID = "gtmoldraw";
    public static final String MODID = MOD_ID;
    public static final String VERSION = Tags.VERSION;
    public static final Logger LOGGER = LogManager.getLogger("MolDraw");

    @SidedProxy(
        clientSide = "com.rubenverg.moldraw.client.ClientLifecycle",
        serverSide = "com.rubenverg.moldraw.CommonLifecycle")
    public static CommonLifecycle lifecycle;

    @Mod.Instance(MOD_ID)
    public static MolDraw INSTANCE;

    public static boolean isClientThread() {
        return lifecycle != null && lifecycle.isClientThread();
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            com.rubenverg.moldraw.core.InstallationScanner.requireSingle(
                event.getModConfigurationDirectory()
                    .toPath()
                    .getParent()
                    .resolve("mods"));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot verify GTMolDraw installations", failure);
        }
        LOGGER.info("[lifecycle] preInit: loading configuration");
        MolDrawConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        LOGGER.info("[lifecycle] postInit: scheduling client integration");
        lifecycle.postInit();
    }
}
