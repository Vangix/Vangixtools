package com.vangix.tools;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ControlPanelScreen extends Screen {

    private static final int PANEL_W = 104;
    private static final int PANEL_GAP = 6;
    private static final int HEADER_H = 14;
    private static final int ROW_H = 12;
    private static final int MAX_VISIBLE_ROWS = 9;
    private static final int START_X = 8;
    private static final int START_Y = 8;

    private static final int BODY_BG = 0xCC121622;
    private static final int ROW_HOVER = 0x662B354D;
    private static final int INACTIVE_TEXT = 0xFFE2E8F0;
    private static final int HEADER_TEXT = 0xFFFFFFFF;
    private static final int BORDER_COL = 0x99262D40;

    private final Map<String, List<ModuleEntry>> panels = new LinkedHashMap<>();
    private final Map<String, Integer> panelScroll = new LinkedHashMap<>();
    private int horizontalScroll = 0;

    private ModuleEntry activeColorTarget = null;
    private float pickerHue = 0.42f;
    private float pickerSat = 0.65f;
    private float pickerVal = 0.65f;
    private boolean draggingWheel = false;
    private boolean draggingBrightness = false;

    private static final int PICKER_BOX_W = 136;
    private static final int PICKER_BOX_H = 142;
    private static final int WHEEL_RADIUS = 44;

    public ControlPanelScreen() {
        super(Component.literal("Vangix Tools"));
        initModules();
    }

    private void initModules() {
        addPanel("Movement", List.of(
                new ModuleEntry("Anti Levitate", () -> false, v -> {}),
                new ModuleEntry("Anti Void", () -> false, v -> {}),
                new ModuleEntry("Auto Walk", () -> false, v -> {}),
                new ModuleEntry("Boat Fly", () -> false, v -> {}),
                new ModuleEntry("Boat Phase", () -> false, v -> {}),
                new ModuleEntry("Click TP", () -> false, v -> {}),
                new ModuleEntry("Elytra +", () -> false, v -> {})
        ));

        addPanel("Player", List.of(
                new ModuleEntry("Auto Tool", () -> FeatureState.quickHarvest, v -> FeatureState.quickHarvest = v),
                new ModuleEntry("Auto Totem", () -> FeatureState.offhandGuard, v -> FeatureState.offhandGuard = v),
                new ModuleEntry("Entity Tag", () -> FeatureState.showEntityTag, v -> FeatureState.showEntityTag = v),
                new ModuleEntry("Entity Gear", () -> FeatureState.showEntityGear, v -> FeatureState.showEntityGear = v)
        ));

        addPanel("Combat", List.of(
                new ModuleEntry("Aim Assist", () -> false, v -> {}),
                new ModuleEntry("Anti-Knockback", () -> false, v -> {}),
                new ModuleEntry("Auto Clicker", () -> false, v -> {}),
                new ModuleEntry("Auto Crystal", () -> false, v -> {}),
                new ModuleEntry("Auto Log", () -> false, v -> {})
        ));

        addPanel("Render", List.of(
                new ModuleEntry("Player ESP", () -> FeatureState.entitySight, v -> FeatureState.entitySight = v),
                new ModuleEntry("Block ESP", () -> FeatureState.containerSight, v -> FeatureState.containerSight = v),
                new ModuleEntry("Tracers",
                        () -> FeatureState.lineGuides,
                        v -> FeatureState.lineGuides = v,
                        FeatureState::getGuideColor,
                        FeatureState::setGuideColor),
                new ModuleEntry("Fullbright", () -> FeatureState.fullbrightFlag, v -> FeatureState.fullbrightFlag = v),
                new ModuleEntry("Status HUD",
                        () -> FeatureState.statusHud,
                        v -> FeatureState.statusHud = v,
                        FeatureState::getHudColor,
                        FeatureState::setHudColor)
        ));

        addPanel("World", List.of(
                new ModuleEntry("Chunk Finder", () -> FeatureState.areaScanner, v -> FeatureState.areaScanner = v),
                new ModuleEntry("Tracer Blocks", () -> FeatureState.tracerBlocks, v -> FeatureState.tracerBlocks = v),
                new ModuleEntry("Storage Filter", () -> FeatureState.filterStorage, v -> FeatureState.filterStorage = v),
                new ModuleEntry("Diamond Filter", () -> FeatureState.filterDiamondBlocks, v -> FeatureState.filterDiamondBlocks = v),
                new ModuleEntry("Netherite Filter", () -> FeatureState.filterNetheriteBlocks, v -> FeatureState.filterNetheriteBlocks = v),
                new ModuleEntry("Emerald Filter", () -> FeatureState.filterEmeraldBlocks, v -> FeatureState.filterEmeraldBlocks = v),
                new ModuleEntry("Redstone Filter", () -> FeatureState.filterRedstoneBlocks, v -> FeatureState.filterRedstoneBlocks = v)
        ));

        addPanel("Gui", List.of(
                new ModuleEntry("Theme Color",
                        () -> true,
                        v -> openColorPickerByName("Theme Color"),
                        () -> FeatureState.accentColor,
                        c -> FeatureState.accentColor = c),
                new ModuleEntry("Reset Scroll", () -> false, v -> {
                    horizontalScroll = 0;
                    panelScroll.replaceAll((k, val) -> 0);
                })
        ));

        addPanel("Presets", List.of(
                new ModuleEntry("Legit Default", () -> false, v -> {
                    FeatureState.quickHarvest = true;
                    FeatureState.offhandGuard = true;
                    FeatureState.statusHud = true;
                }),
                new ModuleEntry("Visuals Only", () -> false, v -> {
                    FeatureState.entitySight = true;
                    FeatureState.containerSight = true;
                    FeatureState.lineGuides = true;
                }),
                new ModuleEntry("Clear All", () -> false, v -> {
                    FeatureState.entitySight = false;
                    FeatureState.containerSight = false;
                    FeatureState.lineGuides = false;
                    FeatureState.fullbrightFlag = false;
                    FeatureState.areaScanner = false;
                    FeatureState.quickHarvest = false;
                    FeatureState.offhandGuard = false;
                })
        ));
    }

    private void openColorPickerByName(String name) {
        for (List<ModuleEntry> list : panels.values()) {
            for (ModuleEntry e : list) {
                if (e.name.equals(name) && e.colorGetter != null) {
                    openColorPicker(e);
                    return;
                }
            }
        }
    }

    private void openColorPicker(ModuleEntry entry) {
        this.activeColorTarget = entry;
        int argb = entry.colorGetter.getAsInt();
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.pickerHue = hsb[0];
        this.pickerSat = hsb[1];
        this.pickerVal = hsb[2];
    }

    private void applyPickerColor() {
        if (activeColorTarget == null || activeColorTarget.colorSetter == null) return;
        int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0x00FFFFFF;
        activeColorTarget.colorSetter.accept(0xFF000000 | rgb);
    }

    private void addPanel(String title, List<ModuleEntry> entries) {
        panels.put(title, new ArrayList<>(entries));
        panelScroll.putIfAbsent(title, 0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int headerBg = (FeatureState.accentColor & 0x00FFFFFF) | 0xD9000000;
        int activeText = lightenColor(FeatureState.accentColor, 0.35f);

        int idx = 0;
        for (Map.Entry<String, List<ModuleEntry>> entry : panels.entrySet()) {
            String title = entry.getKey();
            List<ModuleEntry> items = entry.getValue();

            int px = START_X + idx * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y;

            int visibleCount = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int bodyH = visibleCount * ROW_H + 2;
            int maxScroll = Math.max(0, items.size() - MAX_VISIBLE_ROWS);
            int scroll = Math.max(0, Math.min(panelScroll.getOrDefault(title, 0), maxScroll));
            panelScroll.put(title, scroll);

            graphics.fill(px, py, px + PANEL_W, py + HEADER_H, headerBg);
            graphics.drawString(this.font, title, px + 4, py + 3, HEADER_TEXT, false);

            int bodyTop = py + HEADER_H;
            int bodyBottom = bodyTop + bodyH;
            graphics.fill(px, bodyTop, px + PANEL_W, bodyBottom, BODY_BG);
            renderBorder(graphics, px, py, px + PANEL_W, bodyBottom, BORDER_COL);

            for (int r = 0; r < visibleCount; r++) {
                int itemIndex = r + scroll;
                if (itemIndex >= items.size()) break;

                ModuleEntry mod = items.get(itemIndex);
                int ry = bodyTop + 1 + r * ROW_H;
                boolean hovered = mouseX >= px + 1 && mouseX <= px + PANEL_W - 1
                        && mouseY >= ry && mouseY < ry + ROW_H;

                if (hovered) {
                    graphics.fill(px + 1, ry, px + PANEL_W - 1, ry + ROW_H, ROW_HOVER);
                }

                boolean enabled = mod.getter.getAsBoolean();
                int textColor = enabled ? activeText : INACTIVE_TEXT;
                String label = enabled ? "▸ " + mod.name : mod.name;
                graphics.drawString(this.font, label, px + 4, ry + 2, textColor, false);

                if (mod.colorGetter != null) {
                    int swX = px + PANEL_W - 12;
                    int swY = ry + 3;
                    int col = mod.colorGetter.getAsInt() | 0xFF000000;
                    graphics.fill(swX, swY, swX + 6, swY + 6, col);
                    renderBorder(graphics, swX - 1, swY - 1, swX + 7, swY + 7, 0xCCFFFFFF);
                }
            }

            if (maxScroll > 0) {
                int trackTop = bodyTop + 2;
                int trackH = Math.max(8, bodyH - 4);
                int thumbH = Math.max(8, trackH * visibleCount / items.size());
                int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
                graphics.fill(px + PANEL_W - 3, thumbY, px + PANEL_W - 1, thumbY + thumbH, headerBg);
            }

            idx++;
        }

        if (activeColorTarget != null) {
            renderColorPickerModal(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderColorPickerModal(GuiGraphics graphics) {
        int boxX = (this.width - PICKER_BOX_W) / 2;
        int boxY = Math.max(START_Y + 118, (this.height - PICKER_BOX_H) / 2);

        graphics.fill(boxX, boxY, boxX + PICKER_BOX_W, boxY + PICKER_BOX_H, 0xF02B2D31);
        int headerBg = (FeatureState.accentColor & 0x00FFFFFF) | 0xE6000000;
        graphics.fill(boxX, boxY, boxX + PICKER_BOX_W, boxY + 14, headerBg);
        graphics.drawString(this.font, activeColorTarget.name + " Color", boxX + 5, boxY + 3, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "✕", boxX + PICKER_BOX_W - 10, boxY + 3, 0xFFFFFFFF, false);
        renderBorder(graphics, boxX, boxY, boxX + PICKER_BOX_W, boxY + PICKER_BOX_H, 0xCC4A4F5A);

        int cx = boxX + 56;
        int cy = boxY + 64;

        drawFilledCircle(graphics, cx, cy, WHEEL_RADIUS + 2, 0xFFE6E6E6);

        for (int dy = -WHEEL_RADIUS; dy <= WHEEL_RADIUS; dy++) {
            int maxDx = (int) Math.sqrt(WHEEL_RADIUS * WHEEL_RADIUS - dy * dy);
            for (int dx = -maxDx; dx <= maxDx; dx++) {
                double dist = Math.sqrt(dx * dx + dy * dy);
                float sat = (float) Math.min(1.0, dist / WHEEL_RADIUS);
                double angle = Math.atan2(dy, dx);
                float hue = (float) ((angle / (2.0 * Math.PI)) + 1.0) % 1.0f;
                int argb = Color.HSBtoRGB(hue, sat, 1.0f);
                graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, argb);
            }
        }

        double selAngle = pickerHue * 2.0 * Math.PI;
        int selX = cx + (int) Math.round(Math.cos(selAngle) * pickerSat * WHEEL_RADIUS);
        int selY = cy + (int) Math.round(Math.sin(selAngle) * pickerSat * WHEEL_RADIUS);
        drawCircleOutline(graphics, selX, selY, 4, 0xFFFFFFFF);

        int barX = boxX + 114;
        int barY = boxY + 20;
        int barW = 10;
        int barH = 88;
        for (int y = 0; y < barH; y++) {
            float v = 1.0f - (y / (float) (barH - 1));
            int col = Color.HSBtoRGB(pickerHue, pickerSat, v);
            graphics.fill(barX, barY + y, barX + barW, barY + y + 1, col);
        }
        renderBorder(graphics, barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF1A1B1E);

        int arrowY = barY + Math.round((1.0f - pickerVal) * (barH - 1));
        graphics.fill(barX - 5, arrowY - 2, barX - 2, arrowY + 3, 0xFF1E88E5);
        graphics.fill(barX - 2, arrowY - 1, barX, arrowY + 2, 0xFF1E88E5);

        int hexBoxX = boxX + 12;
        int hexBoxY = boxY + 116;
        int hexBoxW = 92;
        int hexBoxH = 18;
        graphics.fill(hexBoxX, hexBoxY, hexBoxX + hexBoxW, hexBoxY + hexBoxH, 0xFF1E1F22);

        int currentRgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0x00FFFFFF;
        String hexText = String.format("#%06x", currentRgb);
        int textW = this.font.width(hexText);
        graphics.drawString(this.font, hexText, hexBoxX + (hexBoxW - textW) / 2, hexBoxY + 5, 0xFFE2E8F0, false);

        graphics.fill(barX - 1, hexBoxY, barX + barW + 1, hexBoxY + hexBoxH, 0xFF000000 | currentRgb);
        renderBorder(graphics, barX - 1, hexBoxY, barX + barW + 1, hexBoxY + hexBoxH, 0xCCFFFFFF);
    }

    private void updatePickerFromMouse(double mouseX, double mouseY) {
        int boxX = (this.width - PICKER_BOX_W) / 2;
        int boxY = Math.max(START_Y + 118, (this.height - PICKER_BOX_H) / 2);
        int cx = boxX + 56;
        int cy = boxY + 64;
        int barY = boxY + 20;
        int barH = 88;

        if (draggingWheel) {
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            this.pickerSat = (float) Math.min(1.0, dist / WHEEL_RADIUS);
            double angle = Math.atan2(dy, dx);
            this.pickerHue = (float) ((angle / (2.0 * Math.PI)) + 1.0) % 1.0f;
            applyPickerColor();
        } else if (draggingBrightness) {
            float norm = (float) ((mouseY - barY) / (barH - 1));
            this.pickerVal = Math.max(0.0f, Math.min(1.0f, 1.0f - norm));
            applyPickerColor();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeColorTarget != null) {
            return true;
        }

        int step = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);

        int idx = 0;
        for (Map.Entry<String, List<ModuleEntry>> entry : panels.entrySet()) {
            String title = entry.getKey();
            List<ModuleEntry> items = entry.getValue();

            int px = START_X + idx * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y;
            int visibleCount = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int bodyBottom = py + HEADER_H + visibleCount * ROW_H + 2;

            boolean overPanel = mouseX >= px && mouseX <= px + PANEL_W && mouseY >= py && mouseY <= bodyBottom;
            if (overPanel && step != 0) {
                int maxScroll = Math.max(0, items.size() - MAX_VISIBLE_ROWS);
                int current = panelScroll.getOrDefault(title, 0);
                panelScroll.put(title, Math.max(0, Math.min(maxScroll, current + step)));
                return true;
            }
            idx++;
        }

        int totalWidth = panels.size() * (PANEL_W + PANEL_GAP);
        int maxHoriz = Math.max(0, totalWidth - this.width + START_X * 2);
        int hDelta = (int) Math.round((-scrollY - scrollX) * 24);
        if (hDelta != 0) {
            horizontalScroll = Math.max(0, Math.min(maxHoriz, horizontalScroll + hDelta));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeColorTarget != null) {
            int boxX = (this.width - PICKER_BOX_W) / 2;
            int boxY = Math.max(START_Y + 118, (this.height - PICKER_BOX_H) / 2);

            boolean insideBox = mouseX >= boxX && mouseX <= boxX + PICKER_BOX_W
                    && mouseY >= boxY && mouseY <= boxY + PICKER_BOX_H;
            boolean onCloseBtn = mouseX >= boxX + PICKER_BOX_W - 14 && mouseX <= boxX + PICKER_BOX_W
                    && mouseY >= boxY && mouseY <= boxY + 14;

            if (!insideBox || onCloseBtn) {
                activeColorTarget = null;
                draggingWheel = false;
                draggingBrightness = false;
                return true;
            }

            int cx = boxX + 56;
            int cy = boxY + 64;
            double dist = Math.hypot(mouseX - cx, mouseY - cy);
            if (dist <= WHEEL_RADIUS + 4) {
                draggingWheel = true;
                updatePickerFromMouse(mouseX, mouseY);
                return true;
            }

            int barX = boxX + 114;
            int barY = boxY + 20;
            if (mouseX >= barX - 6 && mouseX <= barX + 14 && mouseY >= barY - 2 && mouseY <= barY + 90) {
                draggingBrightness = true;
                updatePickerFromMouse(mouseX, mouseY);
                return true;
            }

            return true;
        }

        int idx = 0;
        for (Map.Entry<String, List<ModuleEntry>> entry : panels.entrySet()) {
            String title = entry.getKey();
            List<ModuleEntry> items = entry.getValue();

            int px = START_X + idx * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y;
            int visibleCount = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int bodyTop = py + HEADER_H;
            int scroll = panelScroll.getOrDefault(title, 0);

            for (int r = 0; r < visibleCount; r++) {
                int itemIndex = r + scroll;
                if (itemIndex >= items.size()) break;

                int ry = bodyTop + 1 + r * ROW_H;
                if (mouseX >= px + 1 && mouseX <= px + PANEL_W - 1
                        && mouseY >= ry && mouseY < ry + ROW_H) {
                    ModuleEntry mod = items.get(itemIndex);
                    if (button == 0) {
                        mod.setter.accept(!mod.getter.getAsBoolean());
                        return true;
                    } else if (button == 1 && mod.colorGetter != null) {
                        openColorPicker(mod);
                        return true;
                    }
                }
            }
            idx++;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeColorTarget != null && (draggingWheel || draggingBrightness)) {
            updatePickerFromMouse(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingWheel = false;
        draggingBrightness = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderBorder(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y1 + 1, color);
        graphics.fill(x1, y2 - 1, x2, y2, color);
        graphics.fill(x1, y1, x1 + 1, y2, color);
        graphics.fill(x2 - 1, y1, x2, y2, color);
    }

    private void drawFilledCircle(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int deg = 0; deg < 360; deg += 10) {
            double rad = Math.toRadians(deg);
            int x = cx + (int) Math.round(Math.cos(rad) * r);
            int y = cy + (int) Math.round(Math.sin(rad) * r);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private int lightenColor(int argb, float factor) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) + 255 * factor));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) + 255 * factor));
        int b = Math.min(255, (int) ((argb & 0xFF) + 255 * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static final class ModuleEntry {
        final String name;
        final BooleanSupplier getter;
        final Consumer<Boolean> setter;
        final IntSupplier colorGetter;
        final IntConsumer colorSetter;

        ModuleEntry(String name, BooleanSupplier getter, Consumer<Boolean> setter) {
            this(name, getter, setter, null, null);
        }

        ModuleEntry(String name, BooleanSupplier getter, Consumer<Boolean> setter,
                    IntSupplier colorGetter, IntConsumer colorSetter) {
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            this.colorGetter = colorGetter;
            this.colorSetter = colorSetter;
        }
    }
}
