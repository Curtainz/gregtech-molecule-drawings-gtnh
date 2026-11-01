package com.rubenverg.moldraw;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = MolDraw.MOD_ID, name = "GregTech Molecule Drawings", version = "1.0.0", acceptableRemoteVersions = "*")
public class MolDraw {

    public static final String MOD_ID = "moldraw";
    public static final Logger LOGGER = LogManager.getLogger("MolDraw");

    @Mod.Instance(MOD_ID)
    public static MolDraw INSTANCE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MolDrawConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new TooltipEventHandler());
        LOGGER.info("GregTech Molecule Drawings for 1.7.10 GTNH initialized successfully!");
    }
}
