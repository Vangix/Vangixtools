package com.vangix.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

public class AreaScanCache {

    public static final class ScanResult {
        public final int score;
        public final int suspiciousBlocks;
        public final int undergroundCount;
        public final int distinctTypes;
        public final int avgY;
        public final String summary;

        public ScanResult(int score, int suspiciousBlocks, int undergroundCount, int distinctTypes, int avgY, String summary) {
            this.score = score;
            this.suspiciousBlocks = suspiciousBlocks;
            this.undergroundCount = undergroundCount;
            this.distinctTypes = distinctTypes;
            this.avgY = avgY;
            this.summary = summary;
        }
    }

    private static final Map<Long, ScanResult> CACHE = new HashMap<>();

    public static int scanRadiusChunks = 5;
    public static int scoreThreshold = 25;
    private static int scanTimer = 0;

    public static void clear() {
        CACHE.clear();
    }

    public static Map<Long, ScanResult> getFlaggedChunks() {
        Map<Long, ScanResult> out = new LinkedHashMap<>();
        for (Map.Entry<Long, ScanResult> e : CACHE.entrySet()) {
            if (e.getValue().score >= scoreThreshold) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    public static List<ChunkPos> getFlaggedChunkPositions() {
        if (!FeatureState.areaScanner) return Collections.emptyList();
        List<ChunkPos> out = new ArrayList<>();
        for (Long key : getFlaggedChunks().keySet()) {
            out.add(new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key)));
        }
        return out;
    }

    public static void tick(Minecraft mc) {
        if (!FeatureState.areaScanner || mc.level == null || mc.player == null) {
            return;
        }

        if (--scanTimer > 0) {
            return;
        }
        scanTimer = 15;

        int centerX = mc.player.chunkPosition().x();
        int centerZ = mc.player.chunkPosition().z();
        Set<Long> keepKeys = new HashSet<>();

        for (int dx = -scanRadiusChunks; dx <= scanRadiusChunks; dx++) {
            for (int dz = -scanRadiusChunks; dz <= scanRadiusChunks; dz++) {
                int cx = centerX + dx;
                int cz = centerZ + dz;
                long key = ChunkPos.pack(cx, cz);
                keepKeys.add(key);

                if (!mc.level.hasChunk(cx, cz)) continue;
                if (!(mc.level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;

                ScanResult res = scanChunk(chunk);
                CACHE.put(key, res);
            }
        }

        CACHE.keySet().retainAll(keepKeys);
    }

    private static ScanResult scanChunk(LevelChunk chunk) {
        int score = 0;
        int suspicious = 0;
        int underground = 0;
        int ySum = 0;
        Set<String> distinctBlocks = new HashSet<>();

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            int wy = be.getBlockPos().getY();
            int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE,
                    Math.floorMod(be.getBlockPos().getX(), 16),
                    Math.floorMod(be.getBlockPos().getZ(), 16));
            boolean isUnderground = wy < surfaceY - 3 || wy < 56;

            int weight = blockEntityWeight(be);
            if (weight > 0) {
                suspicious++;
                ySum += wy;
                if (isUnderground) underground++;
                score += weight + (isUnderground ? 3 : 0);
                distinctBlocks.add(be.getType().toString());
            }
        }

        int minWorldY = chunk.getMinY();
        LevelChunkSection[] sections = chunk.getSections();

        for (int sIdx = 0; sIdx < sections.length; sIdx++) {
            LevelChunkSection section = sections[sIdx];
            if (section == null || section.hasOnlyAir()) continue;
            int secBottomY = minWorldY + sIdx * 16;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int wy = secBottomY + y;
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        int w = blockStateWeight(state.getBlock());
                        if (w > 0) {
                            int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                            boolean isUnderground = wy < surfaceY - 3 || wy < 56;
                            suspicious++;
                            ySum += wy;
                            if (isUnderground) underground++;
                            score += w + (isUnderground ? 3 : 0);
                            distinctBlocks.add(state.getBlock().getDescriptionId());
                        }
                    }
                }
            }
        }

        score += distinctBlocks.size() * 4;
        int avgY = suspicious > 0 ? Math.round((float) ySum / suspicious) : 64;
        String summary = "UG:" + underground + "/" + suspicious + " types:" + distinctBlocks.size();
        return new ScanResult(score, suspicious, underground, distinctBlocks.size(), avgY, summary);
    }

    private static int blockEntityWeight(BlockEntity be) {
        String name = be.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("shulker")) return 12;
        if (name.contains("chest") || name.contains("barrel")) return 6;
        if (name.contains("furnace") || name.contains("hopper") || name.contains("brewing") || name.contains("beacon")) return 5;
        if (name.contains("spawner") || name.contains("trial")) return 8;
        return 0;
    }

    private static int blockStateWeight(Block block) {
        if (block == Blocks.CRAFTING_TABLE || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL) {
            return 4;
        }
        if (block == Blocks.OBSERVER || block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.REDSTONE_BLOCK) {
            return 4;
        }
        if (block == Blocks.DIAMOND_BLOCK || block == Blocks.NETHERITE_BLOCK || block == Blocks.EMERALD_BLOCK) {
            return 7;
        }
        return 0;
    }
}
