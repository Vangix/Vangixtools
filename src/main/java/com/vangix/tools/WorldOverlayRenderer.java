package com.vangix.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorldOverlayRenderer {

    public record TargetBox(BlockPos pos, float r, float g, float b, float fillAlpha, float lineAlpha) {}

    public record PlayerTarget(Player player, String label, float r, float g, float b, boolean tracer) {}

    // Плоскость подозрительного чанка (16x16 на высоте sampleY), как на скрине
    public record ChunkTarget(int minX, int minZ, int maxX, int maxZ, double y, float r, float g, float b, float fillAlpha, float lineAlpha) {}

    private static final List<TargetBox> BLOCK_TARGETS = Collections.synchronizedList(new ArrayList<>());
    private static final List<TargetBox> TRACER_BLOCK_TARGETS = Collections.synchronizedList(new ArrayList<>());
    private static final List<PlayerTarget> PLAYER_TARGETS = Collections.synchronizedList(new ArrayList<>());
    private static final List<ChunkTarget> CHUNK_TARGETS = Collections.synchronizedList(new ArrayList<>());

    private static int scanCooldown = 0;

    public static List<TargetBox> getBlockTargets() {
        return BLOCK_TARGETS;
    }

    public static List<TargetBox> getTracerBlockTargets() {
        return TRACER_BLOCK_TARGETS;
    }

    public static List<PlayerTarget> getPlayerTargets() {
        return PLAYER_TARGETS;
    }

    public static List<ChunkTarget> getChunkTargets() {
        return CHUNK_TARGETS;
    }

    public static float[] argbToRgbFloats(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255.0f,
                ((argb >> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f
        };
    }

    public static float[] getGuideColorRgba() {
        float[] rgb = argbToRgbFloats(FeatureState.getGuideColor());
        return new float[]{rgb[0], rgb[1], rgb[2], 0.90f};
    }

    public static void collectTargets(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            clearAllTargets();
            return;
        }

        boolean needEsp = FeatureState.containerSight;
        boolean needBlockTracers = FeatureState.lineGuides && FeatureState.tracerBlocks;

        if (!needEsp && !needBlockTracers) {
            synchronized (BLOCK_TARGETS) { BLOCK_TARGETS.clear(); }
            synchronized (TRACER_BLOCK_TARGETS) { TRACER_BLOCK_TARGETS.clear(); }
            scanCooldown = 0;
        } else if (scanCooldown <= 0) {
            scanCooldown = 4;
            scanWorldBlocks(mc, needEsp, needBlockTracers);
        } else {
            scanCooldown--;
        }

        collectPlayerTargets(mc);
        collectChunkTargets(mc);
    }

    private static void clearAllTargets() {
        synchronized (BLOCK_TARGETS) { BLOCK_TARGETS.clear(); }
        synchronized (TRACER_BLOCK_TARGETS) { TRACER_BLOCK_TARGETS.clear(); }
        synchronized (PLAYER_TARGETS) { PLAYER_TARGETS.clear(); }
        synchronized (CHUNK_TARGETS) { CHUNK_TARGETS.clear(); }
    }

    private static void collectChunkTargets(Minecraft mc) {
        List<ChunkTarget> nextChunks = new ArrayList<>();
        if (FeatureState.areaScanner) {
            ChunkBaseFinder.tick(mc);
            for (ChunkBaseFinder.SuspiciousChunk sc : ChunkBaseFinder.getFoundChunks()) {
                ChunkPos cp = sc.chunkPos();
                int minX = cp.getMinBlockX();
                int minZ = cp.getMinBlockZ();
                int maxX = cp.getMaxBlockX() + 1;
                int maxZ = cp.getMaxBlockZ() + 1;
                double y = sc.sampleY() + 0.05;

                nextChunks.add(new ChunkTarget(
                        minX, minZ, maxX, maxZ, y,
                        0.15f, 0.90f, 0.25f,
                        0.35f, 0.90f
                ));
            }
        }
        synchronized (CHUNK_TARGETS) {
            CHUNK_TARGETS.clear();
            CHUNK_TARGETS.addAll(nextChunks);
        }
    }

    private static void scanWorldBlocks(Minecraft mc, boolean needEsp, boolean needBlockTracers) {
        List<TargetBox> nextBlocks = new ArrayList<>();
        List<TargetBox> nextTracerBlocks = new ArrayList<>();

        int radius = 4;
        int centerX = mc.player.chunkPosition().x();
        int centerZ = mc.player.chunkPosition().z();
        float[] guideRgb = argbToRgbFloats(FeatureState.getGuideColor());

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = centerX + dx;
                int cz = centerZ + dz;
                if (!mc.level.hasChunk(cx, cz)) continue;
                if (!(mc.level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    float[] rgba = matchBlockEntityColor(be);
                    if (rgba != null) {
                        addTarget(nextBlocks, nextTracerBlocks, be.getBlockPos().immutable(), rgba, guideRgb, needEsp, needBlockTracers);
                    }
                }

                if (FeatureState.filterDiamondBlocks
                        || FeatureState.filterNetheriteBlocks
                        || FeatureState.filterEmeraldBlocks
                        || FeatureState.filterRedstoneBlocks
                        || FeatureState.filterGildedBlackstone) {
                    int minWorldY = chunk.getMinY();
                    LevelChunkSection[] sections = chunk.getSections();
                    int baseX = cx * 16;
                    int baseZ = cz * 16;

                    for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                        LevelChunkSection section = sections[sIdx];
                        if (section == null || section.hasOnlyAir()) continue;
                        int sectionBottomY = minWorldY + sIdx * 16;

                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                int worldY = sectionBottomY + y;
                                for (int z = 0; z < 16; z++) {
                                    BlockState state = section.getBlockState(x, y, z);
                                    if (state.isAir()) continue;
                                    float[] rgba = matchBlockStateColor(state.getBlock());
                                    if (rgba != null) {
                                        addTarget(nextBlocks, nextTracerBlocks, new BlockPos(baseX + x, worldY, baseZ + z), rgba, guideRgb, needEsp, needBlockTracers);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        synchronized (BLOCK_TARGETS) {
            BLOCK_TARGETS.clear();
            BLOCK_TARGETS.addAll(nextBlocks);
        }
        synchronized (TRACER_BLOCK_TARGETS) {
            TRACER_BLOCK_TARGETS.clear();
            TRACER_BLOCK_TARGETS.addAll(nextTracerBlocks);
        }
    }

    private static void collectPlayerTargets(Minecraft mc) {
        List<PlayerTarget> nextPlayers = new ArrayList<>();
        if (FeatureState.entitySight || FeatureState.lineGuides) {
            float[] guideRgb = argbToRgbFloats(FeatureState.getGuideColor());
            double distSqLimit = 128.0 * 128.0;

            for (Player player : mc.level.players()) {
                if (player == mc.player || !player.isAlive()) continue;
                if (mc.player.distanceToSqr(player) > distSqLimit) continue;

                String label = buildPlayerLabel(player);
                nextPlayers.add(new PlayerTarget(
                        player,
                        label,
                        guideRgb[0],
                        guideRgb[1],
                        guideRgb[2],
                        FeatureState.lineGuides
                ));
            }
        }

        synchronized (PLAYER_TARGETS) {
            PLAYER_TARGETS.clear();
            PLAYER_TARGETS.addAll(nextPlayers);
        }
    }

    private static String buildPlayerLabel(Player player) {
        List<String> parts = new ArrayList<>();
        if (FeatureState.showEntityTag) {
            parts.add(player.getName().getString());
        }
        if (FeatureState.showEntityGear) {
            String gear = summarizeGear(player);
            if (!gear.isEmpty()) {
                parts.add(gear);
            }
        }
        return String.join(" | ", parts);
    }

    private static void addTarget(List<TargetBox> blocks, List<TargetBox> tracerBlocks, BlockPos pos,
                                  float[] blockRgb, float[] guideRgb, boolean needEsp, boolean needBlockTracers) {
        if (needEsp) {
            blocks.add(new TargetBox(pos, blockRgb[0], blockRgb[1], blockRgb[2], 0.24f, 0.95f));
        }
        if (needBlockTracers) {
            tracerBlocks.add(new TargetBox(pos, guideRgb[0], guideRgb[1], guideRgb[2], 0.14f, 0.90f));
        }
    }

    private static float[] matchBlockEntityColor(BlockEntity be) {
        if (FeatureState.filterStorage
                && (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity)) {
            return new float[]{1.0f, 0.65f, 0.05f};
        }
        if (FeatureState.filterCustomSpawners && be instanceof SpawnerBlockEntity) {
            return new float[]{1.0f, 0.2f, 0.35f};
        }
        return null;
    }

    private static float[] matchBlockStateColor(Block block) {
        if (FeatureState.filterDiamondBlocks && block == Blocks.DIAMOND_BLOCK) {
            return new float[]{0.2f, 0.9f, 1.0f};
        }
        if (FeatureState.filterNetheriteBlocks && block == Blocks.NETHERITE_BLOCK) {
            return new float[]{0.35f, 0.22f, 0.45f};
        }
        if (FeatureState.filterEmeraldBlocks && block == Blocks.EMERALD_BLOCK) {
            return new float[]{0.15f, 0.95f, 0.45f};
        }
        if (FeatureState.filterRedstoneBlocks && block == Blocks.REDSTONE_BLOCK) {
            return new float[]{0.95f, 0.15f, 0.15f};
        }
        if (FeatureState.filterGildedBlackstone && block == Blocks.GILDED_BLACKSTONE) {
            return new float[]{0.85f, 0.65f, 0.15f};
        }
        return null;
    }

    private static String summarizeGear(Player player) {
        List<String> parts = new ArrayList<>();
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty()) {
            parts.add("H:" + main.getHoverName().getString());
        }

        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        int pieces = 0;
        for (EquipmentSlot slot : armorSlots) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                pieces++;
            }
        }
        if (pieces > 0) {
            parts.add("Armor:" + pieces + "/4");
        }
        return String.join(" | ", parts);
    }
}
