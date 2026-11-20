package com.vangix.tools;

import com.mojang.blaze3d.platform.InputConstants;
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

    // Ширина зоны стрелочки справа в строке модуля
    private static final int ARROW_HIT_W = 14;

    private static final int PANEL_BG = 0xD8141820;
    private static final int HEADER_BG = 0xE6222938;
    private static final int HEADER_ACCENT = 0xFF5B7CFA;
    private static final int ROW_HOVER = 0x443B4761;
    private static final int ACTIVE_ROW_BG = 0x335B7CFA;
    private static final int ACTIVE_TEXT = 0xFF8AB4F8;
    private static final int INACTIVE_TEXT = 0xFFD8DEE9;
    private static final int SUB_BG = 0xCC1B202C;
    private static final int BORDER_COL = 0xAA2E3648;
    private static final int ARROW_COLOR = 0xFF9BB8FF;
    private static final int MUTED_HINT = 0xFF8892A6;

    private final Map<String, List<ModuleNode>> panels = new LinkedHashMap<>();
    private final Map<String, Integer> panelScroll = new LinkedHashMap<>();
    private final Map<String, Float> hoverProgressMap = new LinkedHashMap<>();
    private int horizontalScroll = 0;

    private long lastFrameNs = System.nanoTime();
    private float screenAnim = 0.0f;
    private boolean closingAnimated = false;

    private ModuleNode expandedNode = null;
    private float expandAnim = 0.0f;

    private IntSupplier activeColorGetter = null;
    private IntConsumer activeColorSetter = null;
    private String activeColorTitle = "";
    private float pickerAnim = 0.0f;
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
        this.pickerAnim = 0.0f;

        int argb = getter.getAsInt();
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.pickerHue = hsb[0];
        this.pickerSat = hsb[1];
        this.pickerVal = hsb[2];
    }

    private void closeColorWheel() {
        this.activeColorSetter = null;
        this.activeColorGetter = null;
        this.draggingWheel = false;
        this.draggingBrightness = false;
        this.pickerAnim = 0.0f;
    }

    private void applyWheelColor() {
        if (activeColorSetter == null) return;
        int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0x00FFFFFF;
        activeColorSetter.accept(0xFF000000 | rgb);
    }

    private float stepTowards(float current, float target, float dtSeconds, float speedPerSec) {
        float maxDelta = speedPerSec * dtSeconds;
        if (current < target) return Math.min(target, current + maxDelta);
        if (current > target) return Math.max(target, current - maxDelta);
        return current;
    }

    private float easeOutCubic(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        float inv = 1.0f - clamped;
        return 1.0f - inv * inv * inv;
    }

    private void updateAnimations() {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastFrameNs) / 1_000_000_000.0f));
        lastFrameNs = now;

        float targetScreen = closingAnimated ? 0.0f : 1.0f;
        screenAnim = stepTowards(screenAnim, targetScreen, dt, 6.5f);

        if (closingAnimated && screenAnim <= 0.01f && this.minecraft != null) {
            closingAnimated = false;
            VangixClientEntry.setScreenSafe(this.minecraft, null);
            return;
        }

        float targetExpand = (expandedNode != null && !expandedNode.subSettings.isEmpty()) ? 1.0f : 0.0f;
        expandAnim = stepTowards(expandAnim, targetExpand, dt, 5.5f);

        float targetPicker = (activeColorSetter != null) ? 1.0f : 0.0f;
        pickerAnim = stepTowards(pickerAnim, targetPicker, dt, 7.0f);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(this.font, Component.literal(text), x, y, color);
    }

    private void drawArrowIcon(GuiGraphicsExtractor graphics, int px, int ry, boolean open, int color) {
        // Чуть более крупная и читаемая стрелочка справа в строке
        int arrowX = px + PANEL_W - 10;
        int midY = ry + ROW_H / 2;
        if (open) {
            // Стрелочка вниз (⌄)
            graphics.fill(arrowX - 2, midY - 1, arrowX - 1, midY, color);
            graphics.fill(arrowX - 1, midY, arrowX, midY + 1, color);
            graphics.fill(arrowX, midY + 1, arrowX + 1, midY + 2, color);
            graphics.fill(arrowX + 1, midY, arrowX + 2, midY + 1, color);
            graphics.fill(arrowX + 2, midY - 1, arrowX + 3, midY, color);
        } else {
            // Стрелочка вправо (›)
            graphics.fill(arrowX - 1, midY - 2, arrowX, midY - 1, color);
            graphics.fill(arrowX, midY - 1, arrowX + 1, midY, color);
            graphics.fill(arrowX + 1, midY, arrowX + 2, midY + 1, color);
            graphics.fill(arrowX, midY + 1, arrowX + 1, midY + 2, color);
            graphics.fill(arrowX - 1, midY + 2, arrowX, midY + 3, color);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateAnimations();

        float easedScreen = easeOutCubic(screenAnim);
        float easedExpand = easeOutCubic(expandAnim);

        int ySlide = Math.round((1.0f - easedScreen) * -10.0f);
        int accent = (FeatureState.accentColor & 0x00FFFFFF) | 0xFF000000;
        int headerAccent = blendRgb(HEADER_ACCENT, accent, 0.45f);

        int colIdx = 0;
        for (Map.Entry<String, List<ModuleNode>> entry : panels.entrySet()) {
            String title = entry.getKey();
            List<ModuleNode> items = entry.getValue();

            int colDelaySlide = Math.round((1.0f - easedScreen) * (-6.0f - colIdx * 3.0f));
            int px = START_X + colIdx * (PANEL_W + PANEL_GAP) - horizontalScroll;
            int py = START_Y + ySlide + colDelaySlide;

            int visibleCount = Math.min(items.size(), MAX_VISIBLE_ROWS);
            int maxScroll = Math.max(0, items.size() - MAX_VISIBLE_ROWS);
            int scroll = Math.max(0, Math.min(panelScroll.getOrDefault(title, 0), maxScroll));
            panelScroll.put(title, scroll);

            int extraH = 0;
            for (int r = 0; r < visibleCount; r++) {
                int idx = r + scroll;
                if (idx >= items.size()) break;
                ModuleNode node = items.get(idx);
                if (node == expandedNode && !node.subSettings.isEmpty() && easedExpand > 0.01f) {
                    extraH += Math.round(node.subSettings.size() * SUB_ROW_H * easedExpand);
                }
            }

            int bodyH = visibleCount * ROW_H + extraH + 2;
            int bodyTop = py + HEADER_H;
            int bodyBottom = bodyTop + bodyH;

            int panelBg = scaleAlpha(PANEL_BG, easedScreen);
            int headerBg = scaleAlpha(HEADER_BG, easedScreen);
            int borderCol = scaleAlpha(BORDER_COL, easedScreen);

            graphics.fill(px, py, px + PANEL_W, py + HEADER_H, headerBg);
            graphics.fill(px, py + HEADER_H - 1, px + PANEL_W, py + HEADER_H, scaleAlpha(headerAccent, easedScreen));
            drawText(graphics, title, px + 4, py + 2, scaleAlpha(0xFFE6ECF5, easedScreen));

            graphics.fill(px, bodyTop, px + PANEL_W, bodyBottom, panelBg);
            drawBox(graphics, px, py, px + PANEL_W, bodyBottom, borderCol);

            int curY = bodyTop + 1;
            for (int r = 0; r < visibleCount; r++) {
                int idx = r + scroll;
                if (idx >= items.size()) break;

                ModuleNode node = items.get(idx);
                int ry = curY;
                boolean hovered = !closingAnimated
                        && mouseX >= px + 1 && mouseX <= px + PANEL_W - 1
                        && mouseY >= ry && mouseY < ry + ROW_H;

                String hoverKey = title + ":" + node.name;
                float hProg = hoverProgressMap.getOrDefault(hoverKey, 0.0f);
                hProg = stepTowards(hProg, hovered ? 1.0f : 0.0f, 0.016f, 8.0f);
                hoverProgressMap.put(hoverKey, hProg);

                boolean enabled = node.getter.getAsBoolean();

                if (enabled) {
                    graphics.fill(px + 1, ry, px + PANEL_W - 1, ry + ROW_H, scaleAlpha(ACTIVE_ROW_BG, easedScreen));
                }
                if (hProg > 0.01f) {
                    int hoverFill = scaleAlpha(ROW_HOVER, hProg * easedScreen);
                    graphics.fill(px + 1, ry, px + PANEL_W - 1, ry + ROW_H, hoverFill);
                }

                int hoverShiftX = Math.round(hProg * 2.0f);
                String label = enabled ? "▸ " + node.name : node.name;
                int rawTextCol = enabled ? ACTIVE_TEXT : INACTIVE_TEXT;
                drawText(graphics, label, px + 4 + hoverShiftX, ry + 2, scaleAlpha(rawTextCol, easedScreen));

                if (!node.subSettings.isEmpty()) {
                    boolean open = expandedNode == node;
                    drawArrowIcon(graphics, px, ry, open, scaleAlpha(open ? ACTIVE_TEXT : ARROW_COLOR, easedScreen));
                }

                curY += ROW_H;

                if (node == expandedNode && !node.subSettings.isEmpty() && easedExpand > 0.01f) {
                    int fullSubH = node.subSettings.size() * SUB_ROW_H;
                    int animSubH = Math.round(fullSubH * easedExpand);
                    if (animSubH > 0) {
                        graphics.fill(px + 2, curY, px + PANEL_W - 2, curY + animSubH, scaleAlpha(SUB_BG, easedScreen * easedExpand));
                        int maxVisibleSubRows = animSubH / SUB_ROW_H;
                        for (int s = 0; s < maxVisibleSubRows; s++) {
                            SubSetting sub = node.subSettings.get(s);
                            int sy = curY + s * SUB_ROW_H;
                            boolean subHover = !closingAnimated
                                    && mouseX >= px + 2 && mouseX <= px + PANEL_W - 2
                                    && mouseY >= sy && mouseY < sy + SUB_ROW_H;
                            if (subHover) {
                                graphics.fill(px + 2, sy, px + PANEL_W - 2, sy + SUB_ROW_H, scaleAlpha(ROW_HOVER, easedScreen));
                            }

                            if (sub.kind == SubKind.TOGGLE) {
                                boolean on = sub.boolGetter.getAsBoolean();
                                drawText(graphics, (on ? "✓ " : "· ") + sub.label, px + 7, sy + 2,
                                        scaleAlpha(on ? ACTIVE_TEXT : MUTED_HINT, easedScreen));
                            } else if (sub.kind == SubKind.COLOR) {
                                int c = (sub.colorGetter.getAsInt() & 0x00FFFFFF) | 0xFF000000;
                                drawText(graphics, "· " + sub.label, px + 7, sy + 2, scaleAlpha(INACTIVE_TEXT, easedScreen));
                                graphics.fill(px + PANEL_W - 13, sy + 2, px + PANEL_W - 6, sy + 8, scaleAlpha(c, easedScreen));
                                drawBox(graphics, px + PANEL_W - 14, sy + 1, px + PANEL_W - 5, sy + 9, scaleAlpha(BORDER_COL, easedScreen));
                            } else {
                                drawText(graphics, "· " + sub.label, px + 7, sy + 2, scaleAlpha(INACTIVE_TEXT, easedScreen));
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
                graphics.fill(px + PANEL_W - 2, thumbY, px + PANEL_W - 1, thumbY + thumbH, scaleAlpha(headerAccent, easedScreen));
            }

            colIdx++;
        }

        if (activeColorSetter != null && pickerAnim > 0.01f) {
            renderColorWheelModal(graphics, easeOutCubic(pickerAnim));
        }
    }

    private void renderColorWheelModal(GuiGraphicsExtractor graphics, float anim) {
        int boxW = 132;
        int boxH = 128;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2 + Math.round((1.0f - anim) * 8.0f);

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, scaleAlpha(0xF5141820, anim));
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 13, scaleAlpha(HEADER_BG, anim));
        graphics.fill(boxX, boxY + 12, boxX + boxW, boxY + 13, scaleAlpha(HEADER_ACCENT, anim));
        drawText(graphics, activeColorTitle, boxX + 5, boxY + 2, scaleAlpha(0xFFE6ECF5, anim));
        drawText(graphics, "✕", boxX + boxW - 10, boxY + 2, scaleAlpha(INACTIVE_TEXT, anim));
        drawBox(graphics, boxX, boxY, boxX + boxW, boxY + boxH, scaleAlpha(BORDER_COL, anim));

        int cx = boxX + 50;
        int cy = boxY + 58;

        drawFilledCircle(graphics, cx, cy, WHEEL_R + 2, scaleAlpha(0xFFCBD5E1, anim));

        for (int dy = -WHEEL_R; dy <= WHEEL_R; dy++) {
            int maxDx = (int) Math.sqrt(WHEEL_R * WHEEL_R - dy * dy);
            for (int dx = -maxDx; dx <= maxDx; dx++) {
                double dist = Math.sqrt(dx * dx + dy * dy);
                float sat = (float) Math.min(1.0, dist / WHEEL_R);
                double angle = Math.atan2(dy, dx);
                float hue = (float) ((angle / (2.0 * Math.PI)) + 1.0) % 1.0f;
                graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, scaleAlpha(Color.HSBtoRGB(hue, sat, 1.0f), anim));
            }
        }

        double selAngle = pickerHue * 2.0 * Math.PI;
        int selX = cx + (int) Math.round(Math.cos(selAngle) * pickerSat * WHEEL_R);
        int selY = cy + (int) Math.round(Math.sin(selAngle) * pickerSat * WHEEL_R);
        drawCircleOutline(graphics, selX, selY, 3, scaleAlpha(0xFFFFFFFF, anim));

        int barX = boxX + 102;
        int barY = boxY + 21;
        int barW = 9;
        int barH = 74;
        for (int y = 0; y < barH; y++) {
            float v = 1.0f - (y / (float) (barH - 1));
            graphics.fill(barX, barY + y, barX + barW, barY + y + 1, scaleAlpha(Color.HSBtoRGB(pickerHue, pickerSat, v), anim));
        }
        drawBox(graphics, barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, scaleAlpha(BORDER_COL, anim));

        int arrowY = barY + Math.round((1.0f - pickerVal) * (barH - 1));
        graphics.fill(barX - 4, arrowY - 1, barX - 1, arrowY + 2, scaleAlpha(0xFF8AB4F8, anim));

        int hexX = boxX + 10;
        int hexY = boxY + 103;
        int hexW = 86;
        int hexH = 16;
        graphics.fill(hexX, hexY, hexX + hexW, hexY + hexH, scaleAlpha(0xFF1D2330, anim));
        drawBox(graphics, hexX, hexY, hexX + hexW, hexY + hexH, scaleAlpha(BORDER_COL, anim));

        int rgb = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0x00FFFFFF;
        String hex = String.format("#%06x", rgb);
        drawText(graphics, hex, hexX + (hexW - this.font.width(hex)) / 2, hexY + 4, scaleAlpha(0xFFE6ECF5, anim));

        graphics.fill(barX - 1, hexY, barX + barW + 1, hexY + hexH, scaleAlpha(0xFF000000 | rgb, anim));
        drawBox(graphics, barX - 1, hexY, barX + barW + 1, hexY + hexH, scaleAlpha(BORDER_COL, anim));
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
        if (closingAnimated) return true;
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
        if (closingAnimated) return true;

        double mouseX = extractX(event);
        double mouseY = extractY(event);
        int button = extractButton(event);

        if (activeColorSetter != null) {
            int boxW = 132;
            int boxH = 128;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2;

            boolean inside = mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;
            boolean closeHit = mouseX >= boxX + boxW - 15 && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + 13;

            if (!inside || closeHit) {
                closeColorWheel();
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

                    boolean hitArrow = !node.subSettings.isEmpty()
                            && mouseX >= px + PANEL_W - ARROW_HIT_W
                            && mouseX <= px + PANEL_W - 1;

                    // Нажатие по стрелочке (или ПКМ) открывает/закрывает доп. настройки
                    if (hitArrow || button == 1) {
                        if (!node.subSettings.isEmpty()) {
                            expandedNode = (expandedNode == node) ? null : node;
                        }
                        return true;
                    }

                    // Нажатие по тексту / остальной части строки включает/выключает функцию
                    if (button == 0) {
                        node.setter.accept(!node.getter.getAsBoolean());
                        return true;
                    }
                }

                curY += ROW_H;

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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = extractKeyCode(event);
        if (keyCode == InputConstants.KEY_RSHIFT) {
            if (activeColorSetter != null) {
                closeColorWheel();
                return true;
            }
            closingAnimated = true;
            return true;
        }
        return super.keyPressed(event);
    }

    private int extractKeyCode(Object event) {
        Object v = invokeNoArg(event, "key", "getKey", "keyCode", "getKeyCode", "input");
        return v instanceof Number n ? n.intValue() : -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int scaleAlpha(int argb, float factor) {
        float f = Math.max(0.0f, Math.min(1.0f, factor));
        int baseA = (argb >> 24) & 0xFF;
        int a = Math.round(baseA * f);
        return (a << 24) | (argb & 0x00FFFFFF);
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

    private void drawFilledCircle(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleOutline(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int deg = 0; deg < 360; deg += 12) {
            double rad = Math.toRadians(deg);
            int x = cx + (int) Math.round(Math.cos(rad) * r);
            int y = cy + (int) Math.round(Math.sin(rad) * r);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private enum SubKind {
        TOGGLE,
        COLOR,
        ACTION
    }

    private record SubSetting(
            String label,
            SubKind kind,
            BooleanSupplier boolGetter,
            Consumer<Boolean> boolSetter,
            IntSupplier colorGetter,
            IntConsumer colorSetter,
            Runnable action
    ) {
        static SubSetting toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
            return new SubSetting(label, SubKind.TOGGLE, getter, setter, null, null, null);
        }

        static SubSetting color(String label, IntSupplier getter, IntConsumer setter) {
            return new SubSetting(label, SubKind.COLOR, null, null, getter, setter, null);
        }

        static SubSetting action(String label, Runnable action) {
            return new SubSetting(label, SubKind.ACTION, null, null, null, null, action);
        }
    }

    private record ModuleNode(
            String name,
            BooleanSupplier getter,
            Consumer<Boolean> setter,
            List<SubSetting> subSettings
    ) {}
}
