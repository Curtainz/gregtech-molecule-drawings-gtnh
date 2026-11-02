package com.rubenverg.moldraw;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@Mod(
    modid = MolDraw.MOD_ID,
    name = "GregTech Molecule Drawings",
    version = MolDraw.VERSION,
    guiFactory = "com.rubenverg.moldraw.gui.MolDrawGuiFactory",
    acceptableRemoteVersions = "*")
public class MolDraw {

    public static final String MOD_ID = "moldraw";
    public static final String MODID = MOD_ID;
    public static final String VERSION = "0.2.0-beta.2";
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
        FMLCommonHandler.instance()
            .bus()
            .register(this);

        if (cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems")) {
            com.rubenverg.moldraw.nei.NEIRecipeTooltipHandler.register();
            NEITooltipHelper.init();
        }

        LOGGER.info("GregTech Molecule Drawings for 1.7.10 GTNH initialized successfully!");
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.modID != null && event.modID.equals(MOD_ID)) {
            MolDrawConfig.syncConfig();
        }
    }
}
