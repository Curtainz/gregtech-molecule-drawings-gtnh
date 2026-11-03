package com.rubenverg.moldraw.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import com.rubenverg.moldraw.MolDraw;
import com.rubenverg.moldraw.MolDrawConfig;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;

public class MolDrawGuiConfig extends GuiConfig {

    public MolDrawGuiConfig(GuiScreen parent) {
        super(
            parent,
            getConfigElements(),
            MolDraw.MODID,
            false,
            false,
            MolDrawConfig.config != null ? GuiConfig.getAbridgedConfigPath(
                MolDrawConfig.config.getConfigFile()
                    .getAbsolutePath())
                : "MolDraw Configuration");
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> list = new ArrayList<>();
        if (MolDrawConfig.config != null) {
            list.addAll(
                new ConfigElement(MolDrawConfig.config.getCategory(Configuration.CATEGORY_GENERAL)).getChildElements());
            list.addAll(new ConfigElement(MolDrawConfig.config.getCategory("color")).getChildElements());
            list.addAll(new ConfigElement(MolDrawConfig.config.getCategory("molecule")).getChildElements());
            list.addAll(new ConfigElement(MolDrawConfig.config.getCategory("alloy")).getChildElements());
        }
        return list;
    }
}
