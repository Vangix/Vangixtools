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

    private static final int PANEL_W = 104;
    private static final int PANEL_GAP = 6;
    private static final int HEADER_H = 13;
    private static final int ROW_H = 12;
    private static final int SUB_ROW_H = 11;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int START_X = 8;
    private static final int START_Y = 8;

    // Спокойная тёмно-графитовая палитра без кислотного неона
    private static final int PANEL_BG = 0xD8141820;
    private static final int HEADER_BG = 0xE6222938;
    private static final int HEADER_ACCENT = 0xFF5B7CFA;
    private static final int ROW_HOVER = 0x443B4761;
    private static final int ACTIVE_ROW_BG = 0x335B7CFA;
    private static final int ACTIVE_TEXT = 0xFF8AB4F8;
    private static final int INACTIVE_TEXT = 0xFFD8DEE9;
    private static final int SUB_BG = 0xCC1B202C;
    private static final int BORDER_COL = 0xAA2E3648;
    private static final int MUTED_HINT = 0xFF8892A6;

    private final Map<String, List<ModuleNode>> panels = new LinkedHashMap<>();
    private final Map<String, Integer> panelScroll = new LinkedHashMap<>();
    private int horizontalScroll = 0;

    // Раскрытая функция и прогресс анимации [0..1]
    private ModuleNode expandedNode = null;
    private float expandAnim = 0.0f;

    // Модальный цветовой круг (HSV + шкала яркости + HEX)
    private IntSupplier activeColorGetter = null;
    private IntConsumer activeColorSetter = null;
    private String activeColorTitle = "";
    private float pickerHue = 0.58f;
    private float pickerSat = 0.55f;
    private float pickerVal = 0.90f;
    private boolean draggingWheel = false;
    private boolean draggingBrightness = false;

    private static final int WHEEL_R = 38;

    public ControlPanelScreen() {
        super(Component.literal("Vangix Tools"));
        setupPanels();
    }

    private void setupPanels() {
        List<SubSetting> espSub = List.of(
                SubSetting.toggle("Entity Tag", () -> FeatureState.showEntityTag, v -> FeatureState.showEntityTag = v),
                SubSetting.toggle("Entity Gear", () -> FeatureState.showEntityGear, v -> FeatureState.showEntityGear = v),
                SubSetting.color("Line Color", FeatureState::getGuideColor, FeatureState::setGuideColor)
        );

        List<SubSetting> blockEspSub = List.of(
                SubSetting.toggle("Storage", () -> FeatureState.filterStorage, v -> FeatureState.filterStorage = v),
                SubSetting.toggle("Diamond", () -> FeatureState.filterDiamondBlocks, v -> FeatureState.filterDiamondBlocks = v),
                SubSetting.toggle("Netherite", () -> FeatureState.filterNetheriteBlocks, v -> FeatureState.filterNetheriteBlocks = v),
                SubSetting.toggle("Emerald", () -> FeatureState.filterEmeraldBlocks, v -> FeatureState.filterEmeraldBlocks = v),
                SubSetting.toggle("Redstone", () -> FeatureState.filterRedstoneBlocks, v -> FeatureState.filterRedstoneBlocks = v),
                SubSetting.toggle("Gilded Black.", () -> FeatureState.filterGildedBlackstone, v -> FeatureState.filterGildedBlackstone = v),
                SubSetting.toggle("Spawners", () -> FeatureState.filterCustomSpawners, v -> FeatureState.filterCustomSpawners = v)
        );

        List<SubSetting> tracersSub = List.of(
                SubSetting.toggle("Tracer Blocks", () -> FeatureState.tracerBlocks, v -> FeatureState.tracerBlocks = v),
                SubSetting.color("Tracer Color", FeatureState::getGuideColor, FeatureState::setGuideColor),
                SubSetting.action("Next Palette", FeatureState::cycleGuideColor)
        );

        List<SubSetting> hudSub = List.of(
                SubSetting.color("HUD Color", FeatureState::getHudColor, FeatureState::setHudColor),
                SubSetting.action("Next HUD Col.", FeatureState::cycleHudColor)
        );

        List<SubSetting> totemSub = List.of(
                SubSetting.action("Delay -1", () -> {
                    if (FeatureState.minGuardDelay > 1) FeatureState.minGuardDelay--;
                    if (FeatureState.maxGuardDelay > FeatureState.minGuardDelay) FeatureState.maxGuardDelay--;
                }),
                SubSetting.action("Delay +1", () -> {
                    FeatureState.maxGuardDelay++;
                    if (FeatureState.minGuardDelay < FeatureState.maxGuardDelay - 1) FeatureState.minGuardDelay++;
                })
        );

        addPanel("Visuals", List.of(
                new ModuleNode("Player ESP", () -> FeatureState.entitySight, v -> FeatureState.entitySight = v, espSub),
                new ModuleNode("Block ESP", () -> FeatureState.containerSight, v -> FeatureState.containerSight = v, blockEspSub),
                new ModuleNode("Tracers", () -> FeatureState.lineGuides, v -> FeatureState.lineGuides = v, tracersSub),
                new ModuleNode("Fullbright", () -> FeatureState.fullbrightFlag, v -> FeatureState.fullbrightFlag = v, List.of()),
                new ModuleNode("Status HUD", () -> FeatureState.statusHud, v -> FeatureState.statusHud = v, hudSub),
                new ModuleNode("Chunk Finder", () -> FeatureState.areaScanner, v -> FeatureState.areaScanner = v, List.of())
        ));

        addPanel("Player", List.of(
                new ModuleNode("Auto Tool", () -> FeatureState.quickHarvest, v -> FeatureState.quickHarvest = v, List.of()),
                new ModuleNode("Auto Totem", () -> FeatureState.offhandGuard, v -> FeatureState.offhandGuard = v, totemSub),
                new ModuleNode("Entity Tag", () -> FeatureState.showEntityTag, v -> FeatureState.showEntityTag = v, List.of()),
                new ModuleNode("Entity Gear", () -> FeatureState.showEntityGear, v -> FeatureState.showEntityGear = v, List.of())
        ));

        addPanel("Filters", List.of(
                new ModuleNode("Storage", () -> FeatureState.filterStorage, v -> FeatureState.filterStorage = v, List.of()),
                new ModuleNode("Diamond", () -> FeatureState.filterDiamondBlocks, v -> FeatureState.filterDiamondBlocks = v, List.of()),
                new ModuleNode("Netherite", () -> FeatureState.filterNetheriteBlocks, v -> FeatureState.filterNetheriteBlocks = v, List.of()),
                new ModuleNode("Emerald", () -> FeatureState.filterEmeraldBlocks, v -> FeatureState.filterEmeraldBlocks = v, List.of()),
                new ModuleNode("Redstone", () -> FeatureState.filterRedstoneBlocks, v -> FeatureState.filterRedstoneBlocks = v, List.of()),
                new ModuleNode("Gilded Black.", () -> FeatureState.filterGildedBlackstone, v -> FeatureState.filterGildedBlackstone = v, List.of()),
                new ModuleNode("Spawners", () -> FeatureState.filterCustomSpawners, v -> FeatureState.filterCustomSpawners = v, List.of())
        ));

        addPanel("Style", List.of(
                new ModuleNode("Guide Color",
                        () -> true,
                        v -> openColorWheel("Guide Color", FeatureState::getGuideColor, FeatureState::setGuideColor),
                        List.of(SubSetting.color("Pick Color", FeatureState::getGuideColor, FeatureState::setGuideColor))),
                new ModuleNode("HUD Color",
                        () -> true,
                        v -> openColorWheel("HUD Color", FeatureState::getHudColor, FeatureState::setHudColor),
                        List.of(SubSetting.color("Pick Color", FeatureState::getHudColor, FeatureState::setHudColor))),
                new ModuleNode("Accent Color",
                        () -> true,
                        v -> openColorWheel("Accent Color", () -> FeatureState.accentColor, c -> FeatureState.accentColor = c),
                        List.of(SubSetting.color("Pick Color", () -> FeatureState.accentColor, c -> FeatureState.accentColor = c))),
                new ModuleNode("Reset Scroll", () -> false, v -> {
                    horizontalScroll = 0;
                    panelScroll.replaceAll((k, val) -> 0);
                }, List.of())
        ));
    }

    private void addPanel(String title, List<ModuleNode> nodes) {
        panels.put(title, new ArrayList<>(nodes));
        panelScroll.putIfAbsent(title, 0);
    }

    private void openColorWheel(String title, IntSupplier getter, IntConsumer setter) {
        this.activeColorTitle = title;
        this.activeColorGetter = getter;
        this.activeColorSetter = setter;

        int argb = getter.getAsInt();
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.pickerHue = hsb[0];
        this.pickerSat = hsb[1];
        this.pickerVal = hsb[2];
    }

    private void applyWheelColor() {
        if (activeColorSetter == null) return;
        int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0x00FFFFFF;
        activeColorSetter.accept(0xFF000000 | rgb);
    }

    private void stepExpandAnimation() {
        float target = (expandedNode != null && !expandedNode.subSettings.isEmpty()) ? 1.0f : 0.0f;
        float speed = 0.18f;
        if (expandAnim < target) {
            expandAnim = Math.min(target, expandAnim + speed);
        } else if (expandAnim > target) {
            expandAnim = Math.max(target, expandAnim - speed);
        }
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(this.font, Component.literal(text), x, y, color);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        stepExpandAnimation();

        int accent = (FeatureState.accentColor & 0x00FFFFFF) | 0xFF000000;
        int headerAccent = blendRgb(HEADER_ACCENT, accent, 0.45f);

        int colIdx = 0;
        for (Map.Entry<String, List<ModuleNode>> entry : panels.entrySet()) {
            String title = entry.getKey();
            List<ModuleNode> items = entry.getValue();

            int px = START_X + colIdx * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y;

            int visibleCount = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int maxScroll = Math.max(0, items.size() - MAX_VISIBLE_ROWS);
            int scroll = Math.max(0, Math.min(panelScroll.getOrDefault(title, 0), maxScroll));
            panelScroll.put(title, scroll);

            int extraH = 0;
            for (int r = 0; r < visibleCount; r++) {
                int idx = r + scroll;
                if (idx >= items.size()) break;
                ModuleNode node = items.get(idx);
                if (node == expandedNode && !node.subSettings.isEmpty() && expandAnim > 0.01f) {
                    extraH += Math.round(node.subSettings.size() * SUB_ROW_H * expandAnim);
                }
            }

            int bodyH = visibleCount * ROW_H + extraH + 2;
            int bodyTop = py + HEADER_H;
            int bodyBottom = bodyTop + bodyH;

            // Шапка панели
            graphics.fill(px, py, px + PANEL_W, py + HEADER_H, HEADER_BG);
            graphics.fill(px, py + HEADER_H - 1, px + PANEL_W, py + HEADER_H, headerAccent);
            drawText(graphics, title, px + 4, py + 2, 0xFFE6ECF5);

            // Тело панели
            graphics.fill(px, bodyTop, px + PANEL_W, bodyBottom, PANEL_BG);
            drawBox(graphics, px, py, px + PANEL_W, bodyBottom, BORDER_COL);

            int curY = bodyTop + 1;
            for (int r = 0; r < visibleCount; r++) {
                int idx = r + scroll;
                if (idx >= items.size()) break;

                ModuleNode node = items.get(idx);
                int ry = curY;
                boolean hovered = mouseX >= px + 1 && mouseX <= px + PANEL_W - 1
                        && mouseY >= ry && mouseY < ry + ROW_H;
                boolean enabled = node.getter.getAsBoolean();

                if (enabled) {
                    graphics.fill(px + 1, ry, px + PANEL_W - 1, ry + ROW_H, ACTIVE_ROW_BG);
                }
                if (hovered) {
                    graphics.fill(px + 1, ry, px + PANEL_W - 1, ry + ROW_H, ROW_HOVER);
                }

                String label = enabled ? "▸ " + node.name : node.name;
                drawText(graphics, label, px + 4, ry + 2, enabled ? ACTIVE_TEXT : INACTIVE_TEXT);

                if (!node.subSettings.isEmpty()) {
                    boolean open = expandedNode == node;
                    drawText(graphics, open ? "▾" : "▸", px + PANEL_W - 9, ry + 2, MUTED_HINT);
                }

                curY += ROW_H;

                // Анимированное раскрытие доп. настроек прямо под функцией
                if (node == expandedNode && !node.subSettings.isEmpty() && expandAnim > 0.01f) {
                    int fullSubH = node.subSettings.size() * SUB_ROW_H;
                    int animSubH = Math.round(fullSubH * expandAnim);
                    if (animSubH > 0) {
                        graphics.fill(px + 2, curY, px + PANEL_W - 2, curY + animSubH, SUB_BG);
                        int maxVisibleSubRows = animSubH / SUB_ROW_H;
                        for (int s = 0; s < maxVisibleSubRows; s++) {
                            SubSetting sub = node.subSettings.get(s);
                            int sy = curY + s * SUB_ROW_H;
                            boolean subHover = mouseX >= px + 2 && mouseX <= px + PANEL_W - 2
                                    && mouseY >= sy && mouseY < sy + SUB_ROW_H;
                            if (subHover) {
                                graphics.fill(px + 2, sy, px + PANEL_W - 2, sy + SUB_ROW_H, ROW_HOVER);
                            }

                            if (sub.kind == SubKind.TOGGLE) {
                                boolean on = sub.boolGetter.getAsBoolean();
                                drawText(graphics, (on ? "✓ " : "· ") + sub.label, px + 7, sy + 2,
                                        on ? ACTIVE_TEXT : MUTED_HINT);
                            } else if (sub.kind == SubKind.COLOR) {
                                int c = sub.colorGetter.getAsInt() | 0xFF000000;
                                drawText(graphics, "· " + sub.label, px + 7, sy + 2, INACTIVE_TEXT);
                                graphics.fill(px + PANEL_W - 13, sy + 2, px + PANEL_W - 6, sy + 8, c);
                                drawBox(graphics, px + PANEL_W - 14, sy + 1, px + PANEL_W - 5, sy + 9, BORDER_COL);
                            } else {
                                drawText(graphics, "· " + sub.label, px + 7, sy + 2, INACTIVE_TEXT);
                            }
                        }
                        curY += animSubH;
                    }
                }
            }

            if (maxScroll > 0) {
                int trackTop = bodyTop + 2;
                int trackH = Math.max(6, bodyH - 4);
                int thumbH = Math.max(6, trackH * visibleCount / items.size());
                int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
                graphics.fill(px + PANEL_W - 2, thumbY, px + PANEL_W - 1, thumbY + thumbH, headerAccent);
            }

            colIdx++;
        }

        if (activeColorSetter != null) {
            renderColorWheelModal(graphics);
        }
    }

    private void renderColorWheelModal(GuiGraphicsExtractor graphics) {
        int boxW = 132;
        int boxH = 128;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xF5141820);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 13, HEADER_BG);
        graphics.fill(boxX, boxY + 12, boxX + boxW, boxY + 13, HEADER_ACCENT);
        drawText(graphics, activeColorTitle, boxX + 5, boxY + 2, 0xFFE6ECF5);
        drawText(graphics, "✕", boxX + boxW - 10, boxY + 2, INACTIVE_TEXT);
        drawBox(graphics, boxX, boxY, boxX + boxW, boxY + boxH, BORDER_COL);

        int cx = boxX + 50;
        int cy = boxY + 58;

        drawFilledCircle(graphics, cx, cy, WHEEL_R + 2, 0xFFCBD5E1);

        for (int dy = -WHEEL_R; dy <= WHEEL_R; dy++) {
            int maxDx = (int) Math.sqrt(WHEEL_R * WHEEL_R - dy * dy);
            for (int dx = -maxDx; dx <= maxDx; dx++) {
                double dist = Math.sqrt(dx * dx + dy * dy);
                float sat = (float) Math.min(1.0, dist / WHEEL_R);
                double angle = Math.atan2(dy, dx);
                float hue = (float) ((angle / (2.0 * Math.PI)) + 1.0) % 1.0f;
                graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, Color.HSBtoRGB(hue, sat, 1.0f));
            }
        }

        double selAngle = pickerHue * 2.0 * Math.PI;
        int selX = cx + (int) Math.round(Math.cos(selAngle) * pickerSat * WHEEL_R);
        int selY = cy + (int) Math.round(Math.sin(selAngle) * pickerSat * WHEEL_R);
        drawCircleOutline(graphics, selX, selY, 3, 0xFFFFFFFF);

        int barX = boxX + 102;
        int barY = boxY + 21;
        int barW = 9;
        int barH = 74;
        for (int y = 0; y < barH; y++) {
            float v = 1.0f - (y / (float) (barH - 1));
            graphics.fill(barX, barY + y, barX + barW, barY + y + 1, Color.HSBtoRGB(pickerHue, pickerSat, v));
        }
        drawBox(graphics, barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, BORDER_COL);

        int arrowY = barY + Math.round((1.0f - pickerVal) * (barH - 1));
        graphics.fill(barX - 4, arrowY - 1, barX - 1, arrowY + 2, 0xFF8AB4F8);

        int hexX = boxX + 10;
        int hexY = boxY + 103;
        int hexW = 86;
        int hexH = 16;
        graphics.fill(hexX, hexY, hexX + hexW, hexY + hexH, 0xFF1D2330);
        drawBox(graphics, hexX, hexY, hexX + hexW, hexY + hexH, BORDER_COL);

        int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0x00FFFFFF;
        String hex = String.format("#%06x", rgb);
        drawText(graphics, hex, hexX + (hexW - this.font.width(hex)) / 2, hexY + 4, 0xFFE6ECF5);

        graphics.fill(barX - 1, hexY, barX + barW + 1, hexY + hexH, 0xFF000000 | rgb);
        drawBox(graphics, barX - 1, hexY, barX + barW + 1, hexY + hexH, BORDER_COL);
    }

    private void updatePickerFromMouse(double mouseX, double mouseY) {
        int boxW = 132;
        int boxH = 128;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;
        int cx = boxX + 50;
        int cy = boxY + 58;
        int barY = boxY + 21;
        int barH = 74;

        if (draggingWheel) {
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double dist = Math.hypot(dx, dy);
            this.pickerSat = (float) Math.min(1.0, dist / WHEEL_R);
            double angle = Math.atan2(dy, dx);
            this.pickerHue = (float) ((angle / (2.0 * Math.PI)) + 1.0) % 1.0f;
            applyWheelColor();
        } else if (draggingBrightness) {
            float norm = (float) ((mouseY - barY) / (barH - 1));
            this.pickerVal = Math.max(0.0f, Math.min(1.0f, 1.0f - norm));
            applyWheelColor();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeColorSetter != null) {
            return true;
        }

        int step = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);

        int colIdx = 0;
        for (Map.Entry<String, List<ModuleNode>> entry : panels.entrySet()) {
            String title = entry.getKey();
            List<ModuleNode> items = entry.getValue();

            int px = START_X + colIdx * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y;
            int visibleCount = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int bodyBottom = py + HEADER_H + visibleCount * ROW_H + 36;

            boolean overPanel = mouseX >= px && mouseX <= px + PANEL_W && mouseY >= py && mouseY <= bodyBottom;
            if (overPanel && step != 0) {
                int maxScroll = Math.max(0, items.size() - MAX_VISIBLE_ROWS);
                int cur = panelScroll.getOrDefault(title, 0);
                panelScroll.put(title, Math.max(0, Math.min(maxScroll, cur + step)));
                return true;
            }
            colIdx++;
        }

        int totalW = panels.size() * (PANEL_W + PANEL_GAP);
        int maxHoriz = Math.max(0, totalW - this.width + START_X * 2);
        int hDelta = (int) Math.round((-scrollY - scrollX) * 22);
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

        // Клик в модальном цветовом круге
        if (activeColorSetter != null) {
            int boxW = 132;
            int boxH = 128;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2;

            boolean inside = mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;
            boolean closeHit = mouseX >= boxX + boxW - 15 && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + 13;

            if (!inside || closeHit) {
                activeColorSetter = null;
                activeColorGetter = null;
                draggingWheel = false;
                draggingBrightness = false;
                return true;
            }

            int cx = boxX + 50;
            int cy = boxY + 58;
            if (Math.hypot(mouseX - cx, mouseY - cy) <= WHEEL_R + 3) {
                draggingWheel = true;
                updatePickerFromMouse(mouseX, mouseY);
                return true;
            }

            int barX = boxX + 102;
            int barY = boxY + 21;
            if (mouseX >= barX - 5 && mouseX <= barX + 12 && mouseY >= barY - 2 && mouseY <= barY + 76) {
                draggingBrightness = true;
                updatePickerFromMouse(mouseX, mouseY);
                return true;
            }

            return true;
        }

        // Клик по колонкам и раскрытым подпунктам под функцией
        int colIdx = 0;
        for (Map.Entry<String, List<ModuleNode>> entry : panels.entrySet()) {
            String title = entry.getKey();
            List<ModuleNode> items = entry.getValue();

            int px = START_X + colIdx * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y;
            int visibleCount = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int scroll = panelScroll.getOrDefault(title, 0);

            int curY = py + HEADER_H + 1;
            for (int r = 0; r < visibleCount; r++) {
                int idx = r + scroll;
                if (idx >= items.size()) break;

                ModuleNode node = items.get(idx);
                int ry = curY;

                if (mouseX >= px + 1 && mouseX <= px + PANEL_W - 1
                        && mouseY >= ry && mouseY < ry + ROW_H) {

                    // ПКМ открывает/закрывает доп. настройки (если они есть)
                    if (button == 1) {
                        if (!node.subSettings.isEmpty()) {
                            expandedNode = (expandedNode == node) ? null : node;
                            return true;
                        }
                        return true;
                    }

                    // ЛКМ по самой строке функции включает/выключает функцию
                    if (button == 0) {
                        node.setter.accept(!node.getter.getAsBoolean());
                        return true;
                    }
                }

                curY += ROW_H;

                // Проверка кликов по раскрытым подпунктам под функцией
                if (node == expandedNode && !node.subSettings.isEmpty() && expandAnim > 0.25f) {
                    int fullSubH = node.subSettings.size() * SUB_ROW_H;
                    int animSubH = Math.round(fullSubH * expandAnim);
                    int maxVisibleSubRows = animSubH / SUB_ROW_H;

                    for (int s = 0; s < maxVisibleSubRows; s++) {
                        SubSetting sub = node.subSettings.get(s);
                        int sy = curY + s * SUB_ROW_H;
                        if (mouseX >= px + 2 && mouseX <= px + PANEL_W - 2
                                && mouseY >= sy && mouseY < sy + SUB_ROW_H) {
                            if (sub.kind == SubKind.TOGGLE) {
                                sub.boolSetter.accept(!sub.boolGetter.getAsBoolean());
                            } else if (sub.kind == SubKind.COLOR) {
                                openColorWheel(sub.label, sub.colorGetter, sub.colorSetter);
                            } else if (sub.action != null) {
                                sub.action.run();
                            }
                            return true;
                        }
                    }

                    curY += animSubH;
                }
            }
            colIdx++;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeColorSetter != null && (draggingWheel || draggingBrightness)) {
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

    private int blendRgb(int baseArgb, int tintArgb, float ratio) {
        float t = Math.max(0.0f, Math.min(1.0f, ratio));
        int a = (baseArgb >> 24) & 0xFF;
        int r1 = (baseArgb >> 16) & 0xFF;
        int g1 = (baseArgb >> 8) & 0xFF;
        int b1 = baseArgb & 0xFF;

        int r2 = (tintArgb >> 16) & 0xFF;
        int g2 = (tintArgb >> 8) & 0xFF;
        int b2 = tintArgb & 0xFF;

        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawBox(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y1 + 1, color);
        graphics.fill(x1, y2 - 1, x2, y2, color);
        graphics.fill(x1, y1, x1 + 1, y2, color);
        graphics.fill(x2 - 1, y1, x2, y2, color);
    }

    private void drawFilledCircle(GuiGraphicsExtractor graphics, int cx, int
