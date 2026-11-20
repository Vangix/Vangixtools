package com.vangix.tools;

import java.util.ArrayList;
import java.util.List;

public class FeatureState {
    public static boolean entitySight = false;
    public static boolean containerSight = false;
    public static boolean lineGuides = false;
    public static boolean fullbrightFlag = false;
    public static boolean statusHud = true;
    public static boolean areaScanner = false;

    public static boolean quickHarvest = false;
    public static boolean offhandGuard = false;

    public static boolean showEntityTag = true;
    public static boolean showEntityGear = true;

    public static boolean filterContainers = true;
    public static boolean filterShulkers = true;
    public static boolean filterLogicBlocks = true;
    public static boolean filterSpawners = true;
    public static boolean filterVaultBlocks = true;

    public static int minGuardDelay = 2;
    public static int maxGuardDelay = 5;

    public static final String[] PALETTE_NAMES = {"Cyan", "Green", "Red", "Yellow", "White"};
    public static final int[] PALETTE_HEX = {0xFF38BDF8, 0xFF22C55E, 0xFFEF4444, 0xFFFACC15, 0xFFFFFFFF};

    public static int guidePaletteIndex = 0;
    public static int hudPaletteIndex = 0;

    public static int getGuideColor() {
        return PALETTE_HEX[guidePaletteIndex % PALETTE_HEX.length];
    }

    public static String getGuideColorName() {
        return PALETTE_NAMES[guidePaletteIndex % PALETTE_NAMES.length];
    }

    public static void cycleGuideColor() {
        guidePaletteIndex = (guidePaletteIndex + 1) % PALETTE_NAMES.length;
    }

    public static int getHudColor() {
        return PALETTE_HEX[hudPaletteIndex % PALETTE_HEX.length];
    }

    public static String getHudColorName() {
        return PALETTE_NAMES[hudPaletteIndex % PALETTE_NAMES.length];
    }

    public static void cycleHudColor() {
        hudPaletteIndex = (hudPaletteIndex + 1) % PALETTE_NAMES.length;
    }

    public static List<String> getEnabledTools() {
        List<String> enabled = new ArrayList<>();
        if (entitySight) enabled.add("Player ESP");
        if (containerSight) enabled.add("Block ESP");
        if (lineGuides) enabled.add("Tracers");
        if (fullbrightFlag) enabled.add("Fullbright");
        if (areaScanner) enabled.add("Chunk Finder");
        if (quickHarvest) enabled.add("Auto Tool");
        if (offhandGuard) enabled.add("Auto Totem");
        return enabled;
    }
}
