package com.vangix.tools;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ControlPanelScreen extends Screen {

    private static final int PANEL_W = 104;
    private static final int PANEL_GAP = 6;
    private static final int HEADER_H = 14;
    private static final int ROW_H = 12;
    private static final int MAX_VISIBLE_ROWS = 9;
    private static final int START_X = 8;
    private static final int START_Y = 8;

    private static final int HEADER_BG = 0xD98E2430;
    private static final int BODY_BG = 0xCC121622;
    private static final int ROW_HOVER = 0x662B354D;
    private static final int ACTIVE_TEXT = 0xFFFF6B7A;
    private static final int INACTIVE_TEXT = 0xFFE2E8F0;
    private static final int HEADER_TEXT = 0xFFFFFFFF;
    private static final int BORDER_COL = 0x99262D40;

    private final Map<String, List<ModuleEntry>> panels = new LinkedHashMap<>();
    private final Map<String, Integer> panelScroll = new LinkedHashMap<>();
    private int horizontalScroll = 0;

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
                new ModuleEntry("Auto Tool", () -> FeatureState.autoTool, v -> FeatureState.autoTool = v),
                new ModuleEntry("Auto Totem", () -> FeatureState.autoTotem, v -> FeatureState.autoTotem = v),
                new ModuleEntry("Auto Eat", () -> false, v -> {}),
                new ModuleEntry("Auto Armor", () -> false, v -> {}),
                new ModuleEntry("Auto Respawn", () -> false, v -> {})
        ));

        addPanel("Combat", List.of(
                new ModuleEntry("Aim Assist", () -> false, v -> {}),
                new ModuleEntry("Anti-Knockback", () -> false, v -> {}),
                new ModuleEntry("Auto Clicker", () -> false, v -> {}),
                new ModuleEntry("Auto Crystal", () -> false, v -> {}),
                new ModuleEntry("Auto Log", () -> false, v -> {})
        ));

        addPanel("Render", List.of(
                new ModuleEntry("Block ESP", () -> FeatureState.blockEsp, v -> FeatureState.blockEsp = v),
                new ModuleEntry("Tracers", () -> FeatureState.tracers, v -> FeatureState.tracers = v),
                new ModuleEntry("Full Bright", () -> false, v -> {}),
                new ModuleEntry("Camera Clip", () -> false, v -> {}),
                new ModuleEntry("Chest ESP", () -> false, v -> {})
        ));

        addPanel("World", List.of(
                new ModuleEntry("Area Scanner", () -> FeatureState.areaScanner, v -> FeatureState.areaScanner = v),
                new ModuleEntry("Anti Weather", () -> false, v -> {}),
                new ModuleEntry("Auto Farm", () -> false, v -> {}),
                new ModuleEntry("Liquid Interact", () -> false, v -> {})
        ));

        addPanel("Gui", List.of(
                new ModuleEntry("Reset Scroll", () -> false, v -> {
                    horizontalScroll = 0;
                    panelScroll.replaceAll((k, val) -> 0);
                })
        ));

        addPanel("Presets", List.of(
                new ModuleEntry("Legit Default", () -> false, v -> {
                    FeatureState.autoTool = true;
                    FeatureState.autoTotem = true;
                    FeatureState.blockEsp = false;
                    FeatureState.tracers = false;
                }),
                new ModuleEntry("Visuals Only", () -> false, v -> {
                    FeatureState.blockEsp = true;
                    FeatureState.tracers = true;
                }),
                new ModuleEntry("Clear All", () -> false, v -> {
                    FeatureState.autoTool = false;
                    FeatureState.autoTotem = false;
                    FeatureState.blockEsp = false;
                    FeatureState.tracers = false;
                    FeatureState.areaScanner = false;
                })
        ));
    }

    private void addPanel(String title, List<ModuleEntry> entries) {
        panels.put(title, new ArrayList<>(entries));
        panelScroll.putIfAbsent(title, 0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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

            // Шапка колонки
            graphics.fill(px, py, px + PANEL_W, py + HEADER_H, HEADER_BG);
            graphics.drawString(this.font, title, px + 4, py + 3, HEADER_TEXT, false);

            // Тело колонки
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

                boolean enabled = mod.getter.get();
                int textColor = enabled ? ACTIVE_TEXT : INACTIVE_TEXT;
                String label = enabled ? "▸ " + mod.name : mod.name;
                graphics.drawString(this.font, label, px + 4, ry + 2, textColor, false);
            }

            // Индикатор скролла внутри колонки
            if (maxScroll > 0) {
                int trackTop = bodyTop + 2;
                int trackH = Math.max(8, bodyH - 4);
                int thumbH = Math.max(8, trackH * visibleCount / items.size());
                int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
                graphics.fill(px + PANEL_W - 3, thumbY, px + PANEL_W - 1, thumbY + thumbH, HEADER_BG);
            }

            idx++;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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

        // Если колесо крутят вне колонок или зажат Shift — двигаем ряды колонок по горизонтали
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
        if (button == 0) {
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
                        mod.setter.accept(!mod.getter.get());
                        return true;
                    }
                }
                idx++;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private record ModuleEntry(
            String name,
            java.util.function.BooleanSupplier getter,
            java.util.function.Consumer<Boolean> setter
    ) {}
}
