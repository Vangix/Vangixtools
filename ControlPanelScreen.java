package com.vangix.tools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ControlPanelScreen extends Screen {

    private static final int COL_WIDTH = 192;
    private static final int COL_GAP = 20;
    private static final int TOP_BASE_Y = 38;
    private static final int HEADER_HEIGHT = 26;
    private static final int ROW_HEIGHT = 21;
    private static final int ROW_GAP = 2;
    private static final int SUB_ROW_HEIGHT = 16;
    private static final int SUB_BOTTOM_PAD = 4;
    private static final int ARROW_WIDTH = 17;
    private static final int ARROW_GAP = 2;
    private static final int SCROLL_STEP = 18;

    private sealed interface SubOption permits BoolOption, CycleOption {
        String label();
    }

    private record BoolOption(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) implements SubOption {}
    private record CycleOption(
            String label,
            Supplier<String> valueGetter,
            Supplier<Integer> colorGetter,
            Runnable cycler
    ) implements SubOption {}

    private static class ToolRow {
        final String title;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;
        final List<SubOption> options;

        boolean expanded = false;
        float expandAnim = 0.0f;
        float knobAnim = 0.0f;
        float activeAnim = 0.0f;
        float mainHoverAnim = 0.0f;
        float arrowHoverAnim = 0.0f;
        final List<Float> subHoverAnim;

        Button toggleButton;
        Button expandButton;
        final List<Button> subButtons;

        int lastMainLeft, lastMainTop, lastMainW, lastMainH;
        int lastArrowLeft, lastArrowTop, lastArrowW, lastArrowH;
        final List<int[]> lastSubBounds;

        ToolRow(String title, Supplier<Boolean> getter, Consumer<Boolean> setter, List<SubOption> options) {
            this.title = title;
            this.getter = getter;
            this.setter = setter;
            this.options = options;
            boolean initActive = Boolean.TRUE.equals(getter.get());
            this.knobAnim = initActive ? 1.0f : 0.0f;
            this.activeAnim = initActive ? 1.0f : 0.0f;
            this.subHoverAnim = new ArrayList<>(options.size());
            this.subButtons = new ArrayList<>(options.size());
            this.lastSubBounds = new ArrayList<>(options.size());
            for (int i = 0; i < options.size(); i++) {
                this.subHoverAnim.add(0.0f);
                this.subButtons.add(null);
                this.lastSubBounds.add(new int[4]);
            }
        }
    }

    private static final Map<String, List<ToolRow>> PANELS = new LinkedHashMap<>();

    static {
        PANELS.put("VISUALS", List.of(
                new ToolRow("Player ESP", () -> FeatureState.entitySight, v -> FeatureState.entitySight = v, List.of(
                        new BoolOption("Show Name", () -> FeatureState.showEntityTag, v -> FeatureState.showEntityTag = v),
                        new BoolOption("Show Gear", () -> FeatureState.showEntityGear, v -> FeatureState.showEntityGear = v)
                )),
                new ToolRow("Block ESP", () -> FeatureState.containerSight, v -> FeatureState.containerSight = v, List.of(
                        new BoolOption("Chests / Barrels / Shulkers", () -> FeatureState.filterStorage, v -> FeatureState.filterStorage = v),
                        new BoolOption("Diamond Block", () -> FeatureState.filterDiamondBlocks, v -> FeatureState.filterDiamondBlocks = v),
                        new BoolOption("Netherite Block", () -> FeatureState.filterNetheriteBlocks, v -> FeatureState.filterNetheriteBlocks = v),
                        new BoolOption("Emerald Block", () -> FeatureState.filterEmeraldBlocks, v -> FeatureState.filterEmeraldBlocks = v),
                        new BoolOption("Redstone Blocks", () -> FeatureState.filterRedstoneBlocks, v -> FeatureState.filterRedstoneBlocks = v),
                        new BoolOption("Gilded Blackstone", () -> FeatureState.filterGildedBlackstone, v -> FeatureState.filterGildedBlackstone = v),
                        new BoolOption("Custom Spawners", () -> FeatureState.filterCustomSpawners, v -> FeatureState.filterCustomSpawners = v)
                )),
                new ToolRow("Tracers", () -> FeatureState.lineGuides, v -> FeatureState.lineGuides = v, List.of(
                        new BoolOption("Block Tracers", () -> FeatureState.tracerBlocks, v -> FeatureState.tracerBlocks = v),
                        new CycleOption("Color", FeatureState::getGuideColorName, FeatureState::getGuideColor, FeatureState::cycleGuideColor)
                )),
                new ToolRow("HUD", () -> FeatureState.statusHud, v -> FeatureState.statusHud = v, List.of(
                        new CycleOption("Color", FeatureState::getHudColorName, FeatureState::getHudColor, FeatureState::cycleHudColor)
                )),
                new ToolRow("Fullbright", () -> FeatureState.fullbrightFlag, v -> FeatureState.fullbrightFlag = v, List.of()),
                new ToolRow("Chunk Finder", () -> FeatureState.areaScanner, v -> FeatureState.areaScanner = v, List.of())
        ));

        PANELS.put("UTILITY", List.of(
                new ToolRow("Auto Tool", () -> FeatureState.quickHarvest, v -> FeatureState.quickHarvest = v, List.of()),
                new ToolRow("Auto Totem", () -> FeatureState.offhandGuard, v -> FeatureState.offhandGuard = v, List.of())
        ));
    }

    private float openAnim = 0.0f;
    private String lastActionText = "Ready";
    private float scrollOffset = 0.0f;
    private float targetScrollOffset = 0.0f;
    private float maxScrollOffset = 0.0f;

    public ControlPanelScreen() {
        super(Component.literal("Vangix tools"));
    }

    @Override
    protected void init() {
        super.init();
        openAnim = 0.0f;
        scrollOffset = 0.0f;
        targetScrollOffset = 0.0f;
        maxScrollOffset = 0.0f;

        for (List<ToolRow> rows : PANELS.values()) {
            for (ToolRow row : rows) {
                boolean active = Boolean.TRUE.equals(row.getter.get());
                row.knobAnim = active ? 1.0f : 0.0f;
                row.activeAnim = active ? 1.0f : 0.0f;
                row.expandAnim = row.expanded ? 1.0f : 0.0f;

                row.toggleButton = this.addRenderableWidget(
                        Button.builder(Component.literal(row.title), b -> toggleRow(row))
                                .bounds(0, 0, 100, ROW_HEIGHT)
                                .build()
                );

                if (!row.options.isEmpty()) {
                    row.expandButton = this.addRenderableWidget(
                            Button.builder(Component.literal(row.expanded ? "v" : ">"), b -> toggleExpand(row))
                                    .bounds(0, 0, ARROW_WIDTH, ROW_HEIGHT)
                                    .build()
                    );
                }

                for (int i = 0; i < row.options.size(); i++) {
                    final int idx = i;
                    SubOption opt = row.options.get(i);
                    Button subBtn = this.addRenderableWidget(
                            Button.builder(Component.literal(opt.label()), b -> triggerSubOption(row, idx))
                                    .bounds(0, 0, 100, SUB_ROW_HEIGHT)
                                    .build()
                    );
                    subBtn.visible = false;
                    subBtn.active = false;
                    row.subButtons.set(i, subBtn);
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            targetScrollOffset -= (float) (scrollY * SCROLL_STEP);
            targetScrollOffset = Math.max(0.0f, Math.min(maxScrollOffset, targetScrollOffset));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void toggleRow(ToolRow row) {
        boolean next = !Boolean.TRUE.equals(row.getter.get());
        row.setter.accept(next);
        lastActionText = row.title + ": " + (next ? "ON" : "OFF");
        System.out.println("[Vangix] TOGGLE " + row.title + " -> " + (next ? "ON" : "OFF"));
    }

    private void toggleExpand(ToolRow row) {
        if (row.options.isEmpty()) return;
        row.expanded = !row.expanded;
        if (row.expandButton != null) {
            row.expandButton.setMessage(Component.literal(row.expanded ? "v" : ">"));
        }
        lastActionText = row.title + ": " + (row.expanded ? "EXPANDED" : "COLLAPSED");
        System.out.println("[Vangix] EXPAND " + row.title + " -> " + row.expanded);
    }

    private void triggerSubOption(ToolRow row, int idx) {
        if (idx < 0 || idx >= row.options.size()) return;
        SubOption opt = row.options.get(idx);
        if (opt instanceof BoolOption bOpt) {
            boolean next = !Boolean.TRUE.equals(bOpt.getter().get());
            bOpt.setter().accept(next);
            lastActionText = bOpt.label() + ": " + (next ? "ON" : "OFF");
            System.out.println("[Vangix] SUB " + row.title + "." + bOpt.label() + " -> " + next);
        } else if (opt instanceof CycleOption cOpt) {
            cOpt.cycler().run();
            lastActionText = cOpt.label() + ": " + cOpt.valueGetter().get();
            System.out.println("[Vangix] SUB " + row.title + "." + cOpt.label() + " -> " + cOpt.valueGetter().get());
        }
    }

    private static float approach(float current, float target, float speed) {
        if (current < target) {
            return Math.min(target, current + speed);
        } else if (current > target) {
            return Math.max(target, current - speed);
        }
        return current;
    }

    private static float easeOutCubic(float t) {
        float inv = 1.0f - Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - inv * inv * inv;
    }

    private static int withAlpha(int argb, float factor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int newAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * Math.max(0.0f, Math.min(1.0f, factor)))));
        return (newAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private int getStartX() {
        int totalWidth = PANELS.size() * COL_WIDTH + (PANELS.size() - 1) * COL_GAP;
        return Math.max(8, (this.width - totalWidth) / 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float step = Math.min(0.45f, Math.max(0.08f, delta * 0.24f));
        openAnim = approach(openAnim, 1.0f, step * 0.95f);
        float easedOpen = easeOutCubic(openAnim);

        int startX = getStartX();
        int baseStartY = TOP_BASE_Y + Math.round((1.0f - easedOpen) * 12.0f);

        float tallestPanel = 0.0f;
        for (List<ToolRow> rows : PANELS.values()) {
            float dynamicContentHeight = 0.0f;
            for (ToolRow row : rows) {
                boolean active = Boolean.TRUE.equals(row.getter.get());
                row.knobAnim = approach(row.knobAnim, active ? 1.0f : 0.0f, step * 1.3f);
                row.activeAnim = approach(row.activeAnim, active ? 1.0f : 0.0f, step * 1.1f);
                row.expandAnim = approach(row.expandAnim, row.expanded ? 1.0f : 0.0f, step * 1.15f);

                float easedExpand = easeOutCubic(row.expandAnim);
                dynamicContentHeight += ROW_HEIGHT + ROW_GAP;
                if (!row.options.isEmpty() && easedExpand > 0.001f) {
                    dynamicContentHeight += (row.options.size() * SUB_ROW_HEIGHT + SUB_BOTTOM_PAD) * easedExpand;
                }
            }
            float panelHeight = HEADER_HEIGHT + dynamicContentHeight + 6.0f;
            if (panelHeight > tallestPanel) {
                tallestPanel = panelHeight;
            }
        }

        int availableHeight = Math.max(80, this.height - baseStartY - 12);
        maxScrollOffset = Math.max(0.0f, tallestPanel - availableHeight);
        targetScrollOffset = Math.max(0.0f, Math.min(maxScrollOffset, targetScrollOffset));
        scrollOffset = approach(scrollOffset, targetScrollOffset, Math.max(4.0f, step * 36.0f));
        int scrollY = Math.round(scrollOffset);

        int panelIndex = 0;
        for (Map.Entry<String, List<ToolRow>> entry : PANELS.entrySet()) {
            List<ToolRow> rows = entry.getValue();
            int colX = startX + panelIndex * (COL_WIDTH + COL_GAP);
            int colY = baseStartY - scrollY;
            float yCursor = colY + HEADER_HEIGHT;

            for (ToolRow row : rows) {
                int y = Math.round(yCursor);
                boolean hasSub = !row.options.isEmpty();
                int arrowSlotW = hasSub ? (ARROW_WIDTH + ARROW_GAP) : 0;
                int mainLeft = colX + 5;
                int mainRight = colX + COL_WIDTH - 5 - arrowSlotW;
                int mainW = Math.max(10, mainRight - mainLeft);

                boolean rowVisible = y + ROW_HEIGHT >= baseStartY && y <= this.height - 4;

                row.lastMainLeft = mainLeft;
                row.lastMainTop = y;
                row.lastMainW = mainW;
                row.lastMainH = ROW_HEIGHT;

                if (row.toggleButton != null) {
                    row.toggleButton.setX(mainLeft);
                    row.toggleButton.setY(y);
                    row.toggleButton.setWidth(mainW);
                    row.toggleButton.setHeight(ROW_HEIGHT);
                    row.toggleButton.visible = rowVisible;
                    row.toggleButton.active = rowVisible;
                }

                if (hasSub && row.expandButton != null) {
                    int arrowX1 = mainRight + ARROW_GAP;
                    row.lastArrowLeft = arrowX1;
                    row.lastArrowTop = y;
                    row.lastArrowW = ARROW_WIDTH;
                    row.lastArrowH = ROW_HEIGHT;

                    row.expandButton.setX(arrowX1);
                    row.expandButton.setY(y);
                    row.expandButton.setWidth(ARROW_WIDTH);
                    row.expandButton.setHeight(ROW_HEIGHT);
                    row.expandButton.setMessage(Component.literal(row.expanded ? "v" : ">"));
                    row.expandButton.visible = rowVisible;
                    row.expandButton.active = rowVisible;
                }

                yCursor += ROW_HEIGHT + ROW_GAP;

                float easedExpand = easeOutCubic(row.expandAnim);
                int subTop = Math.round(yCursor);
                float subY = subTop;

                for (int i = 0; i < row.options.size(); i++) {
                    int sy = Math.round(subY);
                    int subLeft = colX + 9;
                    int subW = COL_WIDTH - 18;
                    int[] bounds = row.lastSubBounds.get(i);
                    bounds[0] = subLeft;
                    bounds[1] = sy;
                    bounds[2] = subW;
                    bounds[3] = SUB_ROW_HEIGHT;

                    Button subBtn = row.subButtons.get(i);
                    if (subBtn != null) {
                        boolean subVisible = row.expanded && easedExpand > 0.65f
                                && sy + SUB_ROW_HEIGHT >= baseStartY && sy <= this.height - 4;
                        subBtn.setX(subLeft);
                        subBtn.setY(sy);
                        subBtn.setWidth(subW);
                        subBtn.setHeight(SUB_ROW_HEIGHT);
                        subBtn.visible = subVisible;
                        subBtn.active = subVisible;
                    }
                    subY += SUB_ROW_HEIGHT;
                }

                if (hasSub && easedExpand > 0.001f) {
                    yCursor += (row.options.size() * SUB_ROW_HEIGHT + SUB_BOTTOM_PAD) * easedExpand;
                }
            }
            panelIndex++;
        }

        super.extractRenderState(context, mouseX, mouseY, delta);

        context.fill(0, 0, this.width, this.height, withAlpha(0x6605080F, easedOpen));

        int activeCount = FeatureState.getEnabledTools().size();
        String titleText = "Vangix tools";
        String badgeText = activeCount + " ON  |  " + lastActionText;
        int bannerW = Math.min(this.width - 16, Math.max(220, this.font.width(titleText) + this.font.width(badgeText) + 34));
        int bannerH = 22;
        int bannerX = (this.width - bannerW) / 2;
        int bannerY = 8 - Math.round((1.0f - easedOpen) * 16.0f);

        context.fill(bannerX + 2, bannerY + 2, bannerX + bannerW + 2, bannerY + bannerH + 2, withAlpha(0x88000000, easedOpen));
        context.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, withAlpha(0xF5101520, easedOpen));
        drawFrame(context, bannerX, bannerY, bannerW, bannerH, withAlpha(0xFF334155, easedOpen));

        int midX = bannerX + bannerW / 2;
        context.fill(bannerX + 1, bannerY + bannerH - 2, midX, bannerY + bannerH - 1, withAlpha(0xFF38BDF8, easedOpen));
        context.fill(midX, bannerY + bannerH - 2, bannerX + bannerW - 1, bannerY + bannerH - 1, withAlpha(0xFFA855F7, easedOpen));

        context.text(
                this.font,
                Component.literal(titleText),
                bannerX + 10,
                bannerY + 7,
                withAlpha(0xFFFFFFFF, easedOpen)
        );

        int badgeW = Math.min(bannerW - this.font.width(titleText) - 22, this.font.width(badgeText) + 8);
        int badgeX = bannerX + bannerW - badgeW - 6;
        int badgeY = bannerY + 5;
        context.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 12,
                withAlpha(activeCount > 0 ? 0x3322C55E : 0x2264748B, easedOpen));
        drawFrame(context, badgeX, badgeY, badgeW, 12,
                withAlpha(activeCount > 0 ? 0xFF4ADE80 : 0xFF475569, easedOpen));
        context.centeredText(
                this.font,
                Component.literal(badgeText),
                badgeX + badgeW / 2,
                badgeY + 2,
                withAlpha(activeCount > 0 ? 0xFF86EFAC : 0xFF94A3B8, easedOpen)
        );

        panelIndex = 0;
        for (Map.Entry<String, List<ToolRow>> entry : PANELS.entrySet()) {
            String header = entry.getKey();
            List<ToolRow> rows = entry.getValue();

            float dynamicContentHeight = 0.0f;
            for (ToolRow row : rows) {
                float easedExpand = easeOutCubic(row.expandAnim);
                dynamicContentHeight += ROW_HEIGHT + ROW_GAP;
                if (!row.options.isEmpty() && easedExpand > 0.001f) {
                    dynamicContentHeight += (row.options.size() * SUB_ROW_HEIGHT + SUB_BOTTOM_PAD) * easedExpand;
                }
            }

            int panelHeight = HEADER_HEIGHT + Math.round(dynamicContentHeight) + 6;
            int colX = startX + panelIndex * (COL_WIDTH + COL_GAP);
            int colY = baseStartY - scrollY;
            int accentColor = panelIndex == 0 ? 0xFF38BDF8 : 0xFFA855F7;

            context.fill(colX + 3, colY + 3, colX + COL_WIDTH + 3, colY + panelHeight + 3, withAlpha(0x88000000, easedOpen));
            context.fill(colX, colY, colX + COL_WIDTH, colY + panelHeight, withAlpha(0xF5111520, easedOpen));
            drawFrame(context, colX, colY, COL_WIDTH, panelHeight, withAlpha(0xFF2B3548, easedOpen));

            context.fill(colX + 1, colY + 1, colX + COL_WIDTH - 1, colY + 21, withAlpha(0xFF171D2B, easedOpen));
            context.fill(colX + 1, colY + 1, colX + COL_WIDTH - 1, colY + 3, withAlpha(accentColor, easedOpen));
            context.centeredText(
                    this.font,
                    Component.literal(header),
                    colX + COL_WIDTH / 2,
                    colY + 8,
                    withAlpha(0xFFF8FAFC, easedOpen)
            );
            context.fill(colX + 1, colY + 21, colX + COL_WIDTH - 1, colY + 22, withAlpha(0xFF283246, easedOpen));

            float yCursor = colY + HEADER_HEIGHT;

            for (ToolRow row : rows) {
                int y = Math.round(yCursor);
                boolean hasSub = !row.options.isEmpty();
                int arrowSlotW = hasSub ? (ARROW_WIDTH + ARROW_GAP) : 0;
                int mainLeft = colX + 5;
                int mainRight = colX + COL_WIDTH - 5 - arrowSlotW;

                boolean hoveredMain = mouseX >= mainLeft && mouseX <= mainRight
                        && mouseY >= y && mouseY < y + ROW_HEIGHT;
                row.mainHoverAnim = approach(row.mainHoverAnim, hoveredMain ? 1.0f : 0.0f, step * 1.5f);

                boolean active = Boolean.TRUE.equals(row.getter.get());
                float easedActive = easeOutCubic(row.activeAnim);

                context.fill(mainLeft, y, mainRight, y + ROW_HEIGHT, withAlpha(0xFF181E2C, easedOpen));
                if (easedActive > 0.01f) {
                    context.fill(mainLeft, y, mainRight, y + ROW_HEIGHT, withAlpha(0x2822C55E, easedActive * easedOpen));
                    context.fill(mainLeft, y, mainLeft + 2, y + ROW_HEIGHT, withAlpha(0xFF22C55E, easedActive * easedOpen));
                }
                if (row.mainHoverAnim > 0.01f) {
                    context.fill(mainLeft, y, mainRight, y + ROW_HEIGHT, withAlpha(0x26FFFFFF, row.mainHoverAnim * easedOpen));
                }
                drawFrame(context, mainLeft, y, mainRight - mainLeft, ROW_HEIGHT,
                        withAlpha(active ? 0x4422C55E : 0xFF252F42, easedOpen));

                int titleX = mainLeft + (active ? 6 : 5);
                int titleColor = active ? 0xFFFFFFFF : 0xFFCBD5E1;
                context.text(
                        this.font,
                        Component.literal(row.title),
                        titleX,
                        y + 6,
                        withAlpha(titleColor, easedOpen)
                );

                int toggleW = 22;
                int toggleH = 11;
                int toggleX = mainRight - toggleW - 5;
                int toggleY = y + 5;
                float easedKnob = easeOutCubic(row.knobAnim);

                context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH,
                        withAlpha(active ? 0xFF15803D : 0xFF283244, easedOpen));
                drawFrame(context, toggleX, toggleY, toggleW, toggleH,
                        withAlpha(active ? 0xFF4ADE80 : 0xFF475569, easedOpen));

                int travel = toggleW - toggleH - 2;
                int knobX = toggleX + 1 + Math.round(travel * easedKnob);
                context.fill(knobX, toggleY + 1, knobX + toggleH - 2, toggleY + toggleH - 1, withAlpha(0xFFFFFFFF, easedOpen));

                if (hasSub) {
                    int arrowX1 = mainRight + ARROW_GAP;
                    int arrowX2 = colX + COL_WIDTH - 5;
                    boolean hoveredArrow = mouseX >= arrowX1 && mouseX <= arrowX2
                            && mouseY >= y && mouseY < y + ROW_HEIGHT;
                    row.arrowHoverAnim = approach(row.arrowHoverAnim, hoveredArrow ? 1.0f : 0.0f, step * 1.5f);

                    context.fill(arrowX1, y, arrowX2, y + ROW_HEIGHT,
                            withAlpha(row.expanded ? 0x3338BDF8 : 0xFF181E2C, easedOpen));
                    if (row.arrowHoverAnim > 0.01f) {
                        context.fill(arrowX1, y, arrowX2, y + ROW_HEIGHT, withAlpha(0x3838BDF8, row.arrowHoverAnim * easedOpen));
                    }
                    drawFrame(context, arrowX1, y, arrowX2 - arrowX1, ROW_HEIGHT,
                            withAlpha(row.expanded ? 0xFF38BDF8 : 0xFF252F42, easedOpen));

                    String arrow = row.expanded ? "v" : ">";
                    context.centeredText(
                            this.font,
                            Component.literal(arrow),
                            (arrowX1 + arrowX2) / 2,
                            y + 6,
                            withAlpha(row.expanded ? 0xFF38BDF8 : 0xFFCBD5E1, easedOpen)
                    );
                }

                yCursor += ROW_HEIGHT + ROW_GAP;

                float easedExpand = easeOutCubic(row.expandAnim);
                if (hasSub && easedExpand > 0.01f) {
                    int fullSubHeight = row.options.size() * SUB_ROW_HEIGHT;
                    int visibleSubHeight = Math.max(1, Math.round(fullSubHeight * easedExpand));
                    int subTop = Math.round(yCursor);

                    context.fill(colX + 9, subTop, colX + COL_WIDTH - 9, subTop + visibleSubHeight,
                            withAlpha(0xF60B0E17, easedExpand * easedOpen));
                    drawFrame(context, colX + 9, subTop, COL_WIDTH - 18, visibleSubHeight,
                            withAlpha(0xFF232D3F, easedExpand * easedOpen));
                    context.fill(colX + 9, subTop, colX + 11, subTop + visibleSubHeight,
                            withAlpha(accentColor, easedExpand * easedOpen));

                    float subY = subTop;
                    for (int i = 0; i < row.options.size(); i++) {
                        SubOption opt = row.options.get(i);
                        int sy = Math.round(subY);

                        if (sy + 4 > subTop + visibleSubHeight) {
                            break;
                        }

                        boolean subHover = mouseX >= colX + 11 && mouseX <= colX + COL_WIDTH - 11
                                && mouseY >= sy && mouseY < sy + SUB_ROW_HEIGHT
                                && easedExpand > 0.7f;
                        float shAnim = approach(row.subHoverAnim.get(i), subHover ? 1.0f : 0.0f, step * 1.5f);
                        row.subHoverAnim.set(i, shAnim);

                        if (shAnim > 0.01f) {
                            context.fill(colX + 11, sy, colX + COL_WIDTH - 11, sy + SUB_ROW_HEIGHT,
                                    withAlpha(0x28FFFFFF, shAnim * easedExpand * easedOpen));
                        }

                        if (opt instanceof BoolOption bOpt) {
                            boolean bVal = Boolean.TRUE.equals(bOpt.getter().get());
                            context.text(
                                    this.font,
                                    Component.literal(bOpt.label()),
                                    colX + 15,
                                    sy + 4,
                                    withAlpha(0xFFE2E8F0, easedExpand * easedOpen)
                            );

                            String badge = bVal ? "ON" : "OFF";
                            int subBadgeW = this.font.width(badge) + 6;
                            int bx = colX + COL_WIDTH - 13 - subBadgeW;
                            context.fill(bx, sy + 2, bx + subBadgeW, sy + 13,
                                    withAlpha(bVal ? 0x3322C55E : 0x33EF4444, easedExpand * easedOpen));
                            context.centeredText(
                                    this.font,
                                    Component.literal(badge),
                                    bx + subBadgeW / 2,
                                    sy + 4,
                                    withAlpha(bVal ? 0xFF4ADE80 : 0xFFF87171, easedExpand * easedOpen)
                            );
                        } else if (opt instanceof CycleOption cOpt) {
                            context.text(
                                    this.font,
                                    Component.literal(cOpt.label()),
                                    colX + 15,
                                    sy + 4,
                                    withAlpha(0xFFE2E8F0, easedExpand * easedOpen)
                            );

                            String valText = cOpt.valueGetter().get();
                            int swatchColor = cOpt.colorGetter().get();
                            int valW = this.font.width(valText);
                            int textX = colX + COL_WIDTH - 13 - valW;

                            context.fill(textX - 11, sy + 4, textX - 4, sy + 12, withAlpha(swatchColor, easedExpand * easedOpen));
                            drawFrame(context, textX - 11, sy + 4, 7, 8, withAlpha(0xAAFFFFFF, easedExpand * easedOpen));
                            context.text(
                                    this.font,
                                    Component.literal(valText),
                                    textX,
                                    sy + 4,
                                    withAlpha(0xFFFACC15, easedExpand * easedOpen)
                            );
                        }

                        subY += SUB_ROW_HEIGHT;
                    }

                    yCursor += (fullSubHeight + SUB_BOTTOM_PAD) * easedExpand;
                }
            }

            panelIndex++;
        }
    }

    private void drawFrame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
