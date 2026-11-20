package com.vangix.tools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ControlPanelScreen extends Screen {

    // Компактные размеры, чтобы оставалось место под 3-4 дополнительных раздела
    private static final int PANEL_W = 102;
    private static final int PANEL_GAP = 5;
    private static final int HEADER_H = 13;
    private static final int ROW_H = 11;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int START_X = 6;
    private static final int START_Y = 6;

    private static final int BODY_BG = 0xCC121622;
    private static final int ROW_HOVER = 0x662B354D;
    private static final int INACTIVE_TEXT = 0xFFE2E8F0;
    private static final int HEADER_TEXT = 0xFFFFFFFF;
    private static final int BORDER_COL = 0x99262D40;

    private final Map<String, List<MenuItem>> categories = new LinkedHashMap<>();
    private final Map<String, Integer> categoryScroll = new LinkedHashMap<>();
    private int horizontalScroll = 0;

    // Состояние модального Color Picker (круг + яркость + HEX)
    private MenuItem activeColorItem = null;
    private float pickerHue = 0.42f;
    private float pickerSat = 0.65f;
    private float pickerVal = 0.65f;
    private boolean draggingWheel = false;
    private boolean draggingBrightness = false;

    private static final int PICKER_W = 134;
    private static final int PICKER_H = 138;
    private static final int WHEEL_R = 42;

    public ControlPanelScreen() {
        super(Component.literal("Vangix Tools"));
        buildMenu();
    }

    private void buildMenu() {
        addCategory("Visuals", List.of(
                new MenuItem("Player ESP", () -> FeatureState.entitySight, v -> FeatureState.entitySight = v),
                new MenuItem("Block ESP", () -> FeatureState.containerSight, v -> FeatureState.containerSight = v),
                new MenuItem("Tracers",
                        () -> FeatureState.lineGuides,
                        v -> FeatureState.lineGuides = v,
                        FeatureState::getGuideColor,
                        FeatureState::setGuideColor),
                new MenuItem("Fullbright", () -> FeatureState.fullbrightFlag, v -> FeatureState.fullbrightFlag = v),
                new MenuItem("Status HUD",
                        () -> FeatureState.statusHud,
                        v -> FeatureState.statusHud = v,
                        FeatureState::getHudColor,
                        FeatureState::setHudColor),
                new MenuItem("Chunk Finder", () -> FeatureState.areaScanner, v -> FeatureState.areaScanner = v)
        ));

        addCategory("Player", List.of(
                new MenuItem("Auto Tool", () -> FeatureState.quickHarvest, v -> FeatureState.quickHarvest = v),
                new MenuItem("Auto Totem", () -> FeatureState.offhandGuard, v -> FeatureState.offhandGuard = v),
                new MenuItem("Entity Tag", () -> FeatureState.showEntityTag, v -> FeatureState.showEntityTag = v),
                new MenuItem("Entity Gear", () -> FeatureState.showEntityGear, v -> FeatureState.showEntityGear = v),
                new MenuItem("Tracer Blocks", () -> FeatureState.tracerBlocks, v -> FeatureState.tracerBlocks = v)
        ));

        addCategory("Filters", List.of(
                new MenuItem("Storage", () -> FeatureState.filterStorage, v -> FeatureState.filterStorage = v),
                new MenuItem("Diamond", () -> FeatureState.filterDiamondBlocks, v -> FeatureState.filterDiamondBlocks = v),
                new MenuItem("Netherite", () -> FeatureState.filterNetheriteBlocks, v -> FeatureState.filterNetheriteBlocks = v),
                new MenuItem("Emerald", () -> FeatureState.filterEmeraldBlocks, v -> FeatureState.filterEmeraldBlocks = v),
                new MenuItem("Redstone", () -> FeatureState.filterRedstoneBlocks, v -> FeatureState.filterRedstoneBlocks = v),
                new MenuItem("Gilded Black.", () -> FeatureState.filterGildedBlackstone, v -> FeatureState.filterGildedBlackstone = v),
                new MenuItem("Spawners", () -> FeatureState.filterCustomSpawners, v -> FeatureState.filterCustomSpawners = v)
        ));

        addCategory("Colors", List.of(
                new MenuItem("Tracer Color",
                        () -> true,
                        v -> openColorByTitle("Tracer Color"),
                        FeatureState::getGuideColor,
                        FeatureState::setGuideColor),
                new MenuItem("HUD Color",
                        () -> true,
                        v -> openColorByTitle("HUD Color"),
                        FeatureState::getHudColor,
                        FeatureState::setHudColor),
                new MenuItem("Accent Color",
                        () -> true,
                        v -> openColorByTitle("Accent Color"),
                        () -> FeatureState.accentColor,
                        c -> FeatureState.accentColor = c),
                new MenuItem("Cycle Tracer", () -> false, v -> FeatureState.cycleGuideColor()),
                new MenuItem("Cycle HUD", () -> false, v -> FeatureState.cycleHudColor())
        ));
    }

    private void addCategory(String name, List<MenuItem> items) {
        categories.put(name, new ArrayList<>(items));
        categoryScroll.putIfAbsent(name, 0);
    }

    private void openColorByTitle(String title) {
        for (List<MenuItem> list : categories.values()) {
            for (MenuItem item : list) {
                if (item.title.equals(title) && item.colorGetter != null) {
                    openColorPicker(item);
                    return;
                }
            }
        }
    }

    private void openColorPicker(MenuItem item) {
        this.activeColorItem = item;
        int argb = item.colorGetter.getAsInt();
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.pickerHue = hsb[0];
        this.pickerSat = hsb[1];
        this.pickerVal = hsb[2];
    }

    private void applyColor() {
        if (activeColorItem == null || activeColorItem.colorSetter == null) return;
        int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0x00FFFFFF;
        activeColorItem.colorSetter.accept(0xFF000000 | rgb);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(this.font, Component.literal(text), x, y, color);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int headerColor = (FeatureState.accentColor & 0x00FFFFFF) | 0xD9000000;
        int activeTextColor = lightenColor(FeatureState.accentColor, 0.35f);

        int colIndex = 0;
        for (Map.Entry<String, List<MenuItem>> entry : categories.entrySet()) {
            String title = entry.getKey();
            List<MenuItem> items = entry.getValue();

            int px = START_X + colIndex * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y;

            int visibleRows = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int bodyH = visibleRows * ROW_H + 2;
            int maxScroll = Math.max(0, items.size() - MAX_VISIBLE_ROWS);
            int scroll = Math.max(0, Math.min(categoryScroll.getOrDefault(title, 0), maxScroll));
            categoryScroll.put(title, scroll);

            graphics.fill(px, py, px + PANEL_W, py + HEADER_H, headerColor);
            drawText(graphics, title, px + 4, py + 3, HEADER_TEXT);

            int bodyTop = py + HEADER_H;
            int bodyBottom = bodyTop + bodyH;
            graphics.fill(px, bodyTop, px + PANEL_W, bodyBottom, BODY_BG);
            drawRect(graphics, px, py, px + PANEL_W, bodyBottom, BORDER_COL);

            for (int r = 0; r < visibleRows; r++) {
                int idx = r + scroll;
                if (idx >= items.size()) break;

                MenuItem item = items.get(idx);
                int ry = bodyTop + 1 + r * ROW_H;
                boolean hovered = mouseX >= px + 1 && mouseX <= px + PANEL_W - 1
                        && mouseY >= ry && mouseY < ry + ROW_H;

                if (hovered) {
                    graphics.fill(px + 1, ry, px + PANEL_W - 1, ry + ROW_H, ROW_HOVER);
                }

                boolean enabled = item.getter.getAsBoolean();
                int textCol = enabled ? activeTextColor : INACTIVE_TEXT;
                String label = enabled ? "▸ " + item.title : item.title;
                drawText(graphics, label, px + 4, ry + 2, textCol);

                if (item.colorGetter != null) {
                    int swX = px + PANEL_W - 11;
                    int swY = ry + 3;
                    int c = item.colorGetter.getAsInt() | 0xFF000000;
                    graphics.fill(swX, swY, swX + 5, swY + 5, c);
                    drawRect(graphics, swX - 1, swY - 1, swX + 6, swY + 6, 0xCCFFFFFF);
                }
            }

            if (maxScroll > 0) {
                int trackTop = bodyTop + 2;
                int trackH = Math.max(6, bodyH - 4);
                int thumbH = Math.max(6, trackH * visibleRows / items.size());
                int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
                graphics.fill(px + PANEL_W - 3, thumbY, px + PANEL_W - 1, thumbY + thumbH, headerColor);
            }

            colIndex++;
        }

        if (activeColorItem != null) {
            renderColorPicker(graphics);
        }
    }

    private void renderColorPicker(GuiGraphicsExtractor graphics) {
        int boxX = (this.width - PICKER_W) / 2;
        int boxY = Math.max(START_Y + 112, (this.height - PICKER_H) / 2);

        graphics.fill(boxX, boxY, boxX + PICKER_W, boxY + PICKER_H, 0xF02B2D31);
        int headerColor = (FeatureState.accentColor & 0x00FFFFFF) | 0xE6000000;
        graphics.fill(boxX, boxY, boxX + PICKER_W, boxY + 13, headerColor);
        drawText(graphics, activeColorItem.title, boxX + 5, boxY + 3, 0xFFFFFFFF);
        drawText(graphics, "✕", boxX + PICKER_W - 10, boxY + 3, 0xFFFFFFFF);
        drawRect(graphics, boxX, boxY, boxX + PICKER_W, boxY + PICKER_H, 0xCC4A4F5A);

        int cx = boxX + 55;
        int cy = boxY + 62;

        drawFilledCircle(graphics, cx, cy, WHEEL_R + 2, 0xFFE6E6E6);

        for (int dy = -WHEEL_R; dy <= WHEEL_R; dy++) {
            int maxDx = (int) Math.sqrt(WHEEL_R * WHEEL_R - dy * dy);
            for (int dx = -maxDx; dx <= maxDx; dx++) {
                double dist = Math.sqrt(dx * dx + dy * dy);
                float sat = (float) Math.min(1.0, dist / WHEEL_R);
                double angle = Math.atan2(dy, dx);
                float hue = (float) ((angle / (2.0 * Math.PI)) + 1.0) % 1.0f;
                int argb = Color.HSBtoRGB(hue, sat, 1.0f);
                graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, argb);
            }
        }

        double selAngle = pickerHue * 2.0 * Math.PI;
        int selX = cx + (int) Math.round(Math.cos(selAngle) * pickerSat * WHEEL_R);
        int selY = cy + (int) Math.round(Math.sin(selAngle) * pickerSat * WHEEL_R);
        drawCircleOutline(graphics, selX, selY, 4, 0xFFFFFFFF);

        int barX = boxX + 112;
        int barY = boxY + 20;
        int barW = 10;
        int barH = 84;
        for (int y = 0; y < barH; y++) {
            float v = 1.0f - (y / (float) (barH - 1));
            int col = Color.HSBtoRGB(pickerHue, pickerSat, v);
            graphics.fill(barX, barY + y, barX + barW, barY + y + 1, col);
        }
        drawRect(graphics, barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF1A1B1E);

        int arrowY = barY + Math.round((1.0f - pickerVal) * (barH - 1));
        graphics.fill(barX - 5, arrowY - 2, barX - 2, arrowY + 3, 0xFF1E88E5);
        graphics.fill(barX - 2, arrowY - 1, barX, arrowY + 2, 0xFF1E88E5);

        int hexBoxX = boxX + 12;
        int hexBoxY = boxY + 112;
        int hexBoxW = 90;
        int hexBoxH = 17;
        graphics.fill(hexBoxX, hexBoxY, hexBoxX + hexBoxW, hexBoxY + hexBoxH, 0xFF1E1F22);

        int currentRgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0x00FFFFFF;
        String hexText = String.format("#%06x", currentRgb);
        int textW = this.font.width(hexText);
        drawText(graphics, hexText, hexBoxX + (hexBoxW - textW) / 2, hexBoxY + 5, 0xFFE2E8F0);

        graphics.fill(barX - 1, hexBoxY, barX + barW + 1, hexBoxY + hexBoxH, 0xFF000000 | currentRgb);
        drawRect(graphics, barX - 1, hexBoxY, barX + barW + 1, hexBoxY + hexBoxH, 0xCCFFFFFF);
    }

    private void updatePickerFromMouse(double mouseX, double mouseY) {
        int boxX = (this.width - PICKER_W) / 2;
        int boxY = Math.max(START_Y + 112, (this.height - PICKER_H) / 2);
        int cx = boxX + 55;
        int cy = boxY + 62;
        int barY = boxY + 20;
        int barH = 84;

        if (draggingWheel) {
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            this.pickerSat = (float) Math.min(1.0, dist / WHEEL_R);
            double angle = Math.atan2(dy, dx);
            this.pickerHue = (float) ((angle / (2.0 * Math.PI)) + 1.0) % 1.0f;
            applyColor();
        } else if (draggingBrightness) {
            float norm = (float) ((mouseY - barY) / (barH - 1));
            this.pickerVal = Math.max(0.0f, Math.min(1.0f, 1.0f - norm));
            applyColor();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeColorItem != null) {
            return true;
        }

        int step = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);

        int colIndex = 0;
        for (Map.Entry<String, List<MenuItem>> entry : categories.entrySet()) {
            String title = entry.getKey();
            List<MenuItem> items = entry.getValue();

            int px = START_X + colIndex * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y;
            int visibleRows = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int bodyBottom = py + HEADER_H + visibleRows * ROW_H + 2;

            boolean overPanel = mouseX >= px && mouseX <= px + PANEL_W && mouseY >= py && mouseY <= bodyBottom;
            if (overPanel && step != 0) {
                int maxScroll = Math.max(0, items.size() - MAX_VISIBLE_ROWS);
                int cur = categoryScroll.getOrDefault(title, 0);
                categoryScroll.put(title, Math.max(0, Math.min(maxScroll, cur + step)));
                return true;
            }
            colIndex++;
        }

        int totalWidth = categories.size() * (PANEL_W + PANEL_GAP);
        int maxHoriz = Math.max(0, totalWidth - this.width + START_X * 2);
        int hDelta = (int) Math.round((-scrollY - scrollX) * 24);
        if (hDelta != 0) {
            horizontalScroll = Math.max(0, Math.min(maxHoriz, horizontalScroll + hDelta));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double extractX(MouseButtonEvent event) {
        Object v = invokeNoArg(event, "x", "getX");
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private double extractY(MouseButtonEvent event) {
        Object v = invokeNoArg(event, "y", "getY");
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private int extractButton(MouseButtonEvent event) {
        Object v = invokeNoArg(event, "button", "getButton", "buttonCode");
        if (v instanceof Number n) return n.intValue();
        Object info = invokeNoArg(event, "buttonInfo", "getButtonInfo");
        if (info != null) {
            Object b = invokeNoArg(info, "button", "getButton", "code");
            if (b instanceof Number n) return n.intValue();
        }
        return 0;
    }

    private Object invokeNoArg(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = extractX(event);
        double mouseY = extractY(event);
        int button = extractButton(event);

        if (activeColorItem != null) {
            int boxX = (this.width - PICKER_W) / 2;
            int boxY = Math.max(START_Y + 112, (this.height - PICKER_H) / 2);

            boolean insideBox = mouseX >= boxX && mouseX <= boxX + PICKER_W
                    && mouseY >= boxY && mouseY <= boxY + PICKER_H;
            boolean onClose = mouseX >= boxX + PICKER_W - 14 && mouseX <= boxX + PICKER_W
                    && mouseY >= boxY && mouseY <= boxY + 13;

            if (!insideBox || onClose) {
                activeColorItem = null;
                draggingWheel = false;
                draggingBrightness = false;
                return true;
            }

            int cx = boxX + 55;
            int cy = boxY + 62;
            if (Math.hypot(mouseX - cx, mouseY - cy) <= WHEEL_R + 4) {
                draggingWheel = true;
                updatePickerFromMouse(mouseX, mouseY);
                return true;
            }

            int barX = boxX + 112;
            int barY = boxY + 20;
            if (mouseX >= barX - 6 && mouseX <= barX + 14 && mouseY >= barY - 2 && mouseY <= barY + 86) {
                draggingBrightness = true;
                updatePickerFromMouse(mouseX, mouseY);
                return true;
            }

            return true;
        }

        int colIndex = 0;
        for (Map.Entry<String, List<MenuItem>> entry : categories.entrySet()) {
            String title = entry.getKey();
            List<MenuItem> items = entry.getValue();

            int px = START_X + colIndex * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y;
            int visibleRows = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int bodyTop = py + HEADER_H;
            int scroll = categoryScroll.getOrDefault(title, 0);

            for (int r = 0; r < visibleRows; r++) {
                int idx = r + scroll;
                if (idx >= items.size()) break;

                int ry = bodyTop + 1 + r * ROW_H;
                if (mouseX >= px + 1 && mouseX <= px + PANEL_W - 1
                        && mouseY >= ry && mouseY < ry + ROW_H) {
                    MenuItem item = items.get(idx);
                    if (button == 0) {
                        item.setter.accept(!item.getter.getAsBoolean());
                        return true;
                    } else if (button == 1 && item.colorGetter != null) {
                        openColorPicker(item);
                        return true;
                    }
                }
            }
            colIndex++;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeColorItem != null && (draggingWheel || draggingBrightness)) {
            updatePickerFromMouse(extractX(event), extractY(event));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingWheel = false;
        draggingBrightness = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawRect(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y1 + 1, color);
        graphics.fill(x1, y2 - 1, x2, y2, color);
        graphics.fill(x1, y1, x1 + 1, y2, color);
        graphics.fill(x2 - 1, y1, x2, y2, color);
    }

    private void drawFilledCircle(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleOutline(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
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

    private static final class MenuItem {
        final String title;
        final BooleanSupplier getter;
        final Consumer<Boolean> setter;
        final IntSupplier colorGetter;
        final IntConsumer colorSetter;

        MenuItem(String title, BooleanSupplier getter, Consumer<Boolean> setter) {
            this(title, getter, setter, null, null);
        }

        MenuItem(String title, BooleanSupplier getter, Consumer<Boolean> setter,
                 IntSupplier colorGetter, IntConsumer colorSetter) {
            this.title = title;
            this.getter = getter;
            this.setter = setter;
            this.colorGetter = colorGetter;
            this.colorSetter = colorSetter;
        }
    }
}
