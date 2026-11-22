package com.vangix.tools;

import java.util.ArrayList;
import java.util.List;

public class FeatureState {
    public enum SkyPreset {
        PURPLE_GALAXY("Purple Galaxy", 0xFF1A0836, 0xFF6D28D9, 0xFFF472B6, true),
        RAINY_GRAY("Rainy Gray", 0xFF2B303A, 0xFF4B5563, 0xFF9CA3AF, false),
        BRIGHT_DAY("Bright Day", 0xFF38BDF8, 0xFFBAE6FD, 0xFFFFFFFF, false),
        STARRY_NIGHT("Starry Night", 0xFF050816, 0xFF0F172A, 0xFFE2E8F0, true),
        HEAVENLY_WHITE("Heavenly White", 0xFFDCE7F7, 0xFFF8FAFC, 0xFFFFFFFF, false);

        public final String label;
        public final int zenithArgb;
        public final int horizonArgb;
        public final int accentArgb;
        public final boolean stars;

        SkyPreset(String label, int zenithArgb, int horizonArgb, int accentArgb, boolean stars) {
            this.label = label;
            this.zenithArgb = zenithArgb;
            this.horizonArgb = horizonArgb;
            this.accentArgb = accentArgb;
            this.stars = stars;
        }
    }

    public static boolean customSky = false;
    public static int skyPresetIndex = 0;

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

    public static boolean filterStorage = true;
    public static boolean filterDiamondBlocks = true;
    public static boolean filterNetheriteBlocks = true;
    public static boolean filterEmeraldBlocks = true;
    public static boolean filterRedstoneBlocks = true;
    public static boolean filterGildedBlackstone = true;
    public static boolean filterCustomSpawners = true;

    public static boolean tracerBlocks = true;

    public static int minGuardDelay = 2;
    public static int maxGuardDelay = 5;

    public static final String[] PALETTE_NAMES = {"Cyan", "Green", "Red", "Yellow", "White"};
    public static final int[] PALETTE_HEX = {0xFF38BDF8, 0xFF22C55E, 0xFFEF4444, 0xFFFACC15, 0xFFFFFFFF};

    public static int guidePaletteIndex = 0;
    public static int hudPaletteIndex = 0;

    private static Integer customGuideColor = null;
    private static Integer customHudColor = null;
    public static int accentColor = 0xFF8E2430;

    public static SkyPreset getSkyPreset() {
        SkyPreset[] values = SkyPreset.values();
        int idx = Math.floorMod(skyPresetIndex, values.length);
        return values[idx];
    }

    public static String getSkyPresetName() {
        return getSkyPreset().label;
    }

    public static void cycleSkyPreset() {
        skyPresetIndex = (skyPresetIndex + 1) % SkyPreset.values().length;
    }

    public static int getGuideColor() {
        if (customGuideColor != null) {
            return customGuideColor;
        }
        return PALETTE_HEX[guidePaletteIndex % PALETTE_HEX.length];
    }

    public static void setGuideColor(int argb) {
        customGuideColor = 0xFF000000 | argb;
    }

    public static String getGuideColorName() {
        if (customGuideColor != null) {
            return String.format("#%06X", customGuideColor & 0x00FFFFFF);
        }
        return PALETTE_NAMES[guidePaletteIndex % PALETTE_NAMES.length];
    }

    public static void cycleGuideColor() {
        customGuideColor = null;
        guidePaletteIndex = (guidePaletteIndex + 1) % PALETTE_NAMES.length;
    }

    public static int getHudColor() {
        if (customHudColor != null) {
            return customHudColor;
        }
        return PALETTE_HEX[hudPaletteIndex % PALETTE_HEX.length];
    }

    public static void setHudColor(int argb) {
        customHudColor = 0xFF000000 | argb;
    }

    public static String getHudColorName() {
        if (customHudColor != null) {
            return String.format("#%06X", customHudColor & 0x00FFFFFF);
        }
        return PALETTE_NAMES[hudPaletteIndex % PALETTE_NAMES.length];
    }

    public static void cycleHudColor() {
        customHudColor = null;
        hudPaletteIndex = (hudPaletteIndex + 1) % PALETTE_NAMES.length;
    }

    public static List<String> getEnabledTools() {
        List<String> enabled = new ArrayList<>();
        if (customSky) enabled.add("Custom Sky (" + getSkyPresetName() + ")");
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
