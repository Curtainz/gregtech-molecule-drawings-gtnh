package com.rubenverg.moldraw;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class MolDrawConfig {

    public static MolDrawConfig INSTANCE = new MolDrawConfig();
    public static Configuration config;

    public boolean enabled = true;
    public boolean onlyShowOnShift = true;
    public boolean debugMode = false;

    public final ColorConfig color = new ColorConfig();
    public final MoleculeConfig molecule = new MoleculeConfig();
    public final AlloyConfig alloy = new AlloyConfig();

    public static class ColorConfig {

        public boolean colors = true;
        public boolean useMaterialColors = true;
        public String defaultColor = "§e";
        public float minimumBrightness = 0.1f;
    }

    public static class MoleculeConfig {

        public enum AromaticMode {
            CIRCLE,
            DOUBLE_BONDS
        }

        public boolean showMolecules = true;
        public int moleculeScale = 20;
        public boolean spinMolecules = true;
        public float spinSpeedMultiplier = 1.0f;
        public AromaticMode benzeneCircle = AromaticMode.CIRCLE;
    }

    public static class AlloyConfig {

        public boolean showAlloys = true;
        public int pieChartRadius = 32;
        public int pieChartComplexity = 32;
        public int maxComponentsDisplayed = 8;
        public double minPercentageThreshold = 1.0;
        public boolean recursive = true;
        public boolean partsByMass = true;
    }

    public static void load(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
        }
        syncConfig();
    }

    public static void syncConfig() {
        if (config == null) return;
        try {
            config.load();

            INSTANCE.enabled = config
                .getBoolean("enabled", Configuration.CATEGORY_GENERAL, true, "Whether MolDraw is enabled");
            INSTANCE.onlyShowOnShift = config.getBoolean(
                "onlyShowOnShift",
                Configuration.CATEGORY_GENERAL,
                true,
                "Only show drawings when holding Shift");
            INSTANCE.debugMode = config
                .getBoolean("debugMode", Configuration.CATEGORY_GENERAL, false, "Enable debug logging");

            // Color
            INSTANCE.color.colors = config.getBoolean("colors", "color", true, "Enable coloring");
            INSTANCE.color.useMaterialColors = config
                .getBoolean("useMaterialColors", "color", true, "Use GregTech material colors");
            INSTANCE.color.defaultColor = config
                .getString("defaultColor", "color", "§e", "Default text formatting color");
            INSTANCE.color.minimumBrightness = (float) config
                .get("color", "minimumBrightness", 0.1, "Minimum color brightness")
                .getDouble();

            // Molecule
            INSTANCE.molecule.showMolecules = config
                .getBoolean("showMolecules", "molecule", true, "Display molecule diagrams");
            INSTANCE.molecule.moleculeScale = config
                .getInt("moleculeScale", "molecule", 20, 10, 50, "Scale factor for molecules");
            INSTANCE.molecule.spinMolecules = config
                .getBoolean("spinMolecules", "molecule", true, "Enable molecule 3D rotation animation");
            INSTANCE.molecule.spinSpeedMultiplier = (float) config
                .get("molecule", "spinSpeedMultiplier", 1.0, "Rotation speed multiplier")
                .getDouble();

            String modeStr = config
                .getString("benzeneCircle", "molecule", "CIRCLE", "Benzene display mode: CIRCLE or DOUBLE_BONDS");
            try {
                INSTANCE.molecule.benzeneCircle = MoleculeConfig.AromaticMode.valueOf(modeStr.toUpperCase());
            } catch (Exception e) {
                INSTANCE.molecule.benzeneCircle = MoleculeConfig.AromaticMode.CIRCLE;
            }

            // Alloy
            INSTANCE.alloy.showAlloys = config.getBoolean("showAlloys", "alloy", true, "Display alloy pie charts");
            INSTANCE.alloy.pieChartRadius = config
                .getInt("pieChartRadius", "alloy", 32, 20, 64, "Radius of alloy pie charts");
            INSTANCE.alloy.pieChartComplexity = config
                .getInt("pieChartComplexity", "alloy", 32, 8, 128, "Segments count of alloy pie chart");
            INSTANCE.alloy.maxComponentsDisplayed = config.getInt(
                "maxComponentsDisplayed",
                "alloy",
                8,
                1,
                30,
                "Max components displayed in pie chart before grouping into 'Other'");
            INSTANCE.alloy.minPercentageThreshold = config.get(
                "alloy",
                "minPercentageThreshold",
                1.0,
                "Minimum percentage (0.0 - 100.0) for a component to be displayed separately; smaller components will be grouped into 'Other'")
                .getDouble();
            INSTANCE.alloy.recursive = config
                .getBoolean("recursive", "alloy", true, "Recursively decompose sub-alloys");
            INSTANCE.alloy.partsByMass = config
                .getBoolean("partsByMass", "alloy", true, "Calculate proportions by mass instead of molar count");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 无条件强制保存配置文件，确保首次运行即在 config 目录下生成实体 .cfg 文件
            config.save();
        }
    }
}
