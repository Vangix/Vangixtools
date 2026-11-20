package com.vangix.tools;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AreaScanCache {

    public static class ScanResult {
        public final int score;
        public final String summary;

        public ScanResult(int score, String summary) {
            this.score = score;
            this.summary = summary;
        }
    }

    private static final Map<Long, ScanResult> ENTRIES = new HashMap<>();
    private static int scanTimer = 0;
    public static int scoreThreshold = 25;

    public static void tick(MinecraftClient client) {
        if (!FeatureState.areaScanner || client.world == null || client.player == null) {
            return;
        }

        if (scanTimer > 0) {
            scanTimer--;
            return;
        }
        scanTimer = 15;

        int radius = 5;
        ChunkPos center = client.player.getChunkPos();
        Set<Long> seen = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                if (!client.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                if (!(client.world.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof WorldChunk chunk)) continue;

                long key = ChunkPos.toLong(cx, cz);
                seen.add(key);

                if (!chunk.isEmpty()) {
                    ENTRIES.put(key, evaluateChunk(chunk));
                }
            }
        }

        ENTRIES.keySet().removeIf(k -> !seen.contains(k));
    }

    public static Map<Long, ScanResult> getFlaggedChunks() {
        Map<Long, ScanResult> out = new HashMap<>();
        for (Map.Entry<Long, ScanResult> entry : ENTRIES.entrySet()) {
            if (entry.getValue().score >= scoreThreshold) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static ScanResult evaluateChunk(WorldChunk chunk) {
        int blockScore = 0;
        int terrainScore = 0;

        Set<Block> distinctBlocks = new HashSet<>();
        int matchedCount = 0;

        ChunkSection[] sections = chunk.getSectionArray();
        for (ChunkSection section : sections) {
            if (section == null || section.isEmpty()) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        Block block = state.getBlock();

                        if (isNotableBlock(block)) {
                            matchedCount++;
                            distinctBlocks.add(block);
                        }
                    }
                }
            }
        }

        blockScore += matchedCount * 2;
        blockScore += distinctBlocks.size() * 4;

        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        int abruptSteps = 0;

        int[][] heights = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int h = chunk.getHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES).get(x, z);
                heights[x][z] = h;
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;
            }
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int h = heights[x][z];
                if (x + 1 < 16 && Math.abs(h - heights[x + 1][z]) >= 8) {
                    abruptSteps++;
                }
                if (z + 1 < 16 && Math.abs(h - heights[x][z + 1]) >= 8) {
                    abruptSteps++;
                }
            }
        }

        if (maxH - minH >= 24 && abruptSteps >= 12) {
            terrainScore += 15;
        } else if (abruptSteps >= 20) {
            terrainScore += 10;
        }

        int total = blockScore + terrainScore;
        String summary = "Blocks: " + matchedCount
                + " (types: " + distinctBlocks.size() + ")"
                + (terrainScore > 0 ? ", Terrain+" + terrainScore : "");

        return new ScanResult(total, summary);
    }

    private static boolean isNotableBlock(Block block) {
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof EnderChestBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof EnchantingTableBlock
                || block instanceof BeaconBlock
                || block instanceof BrewingStandBlock
                || block instanceof BedBlock
                || block instanceof CarpetBlock
                || block instanceof AbstractGlassBlock
                || block instanceof TorchBlock
                || block instanceof WallTorchBlock
                || block instanceof LanternBlock
                || block instanceof CommandBlock
                || block instanceof StructureBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock
                || block instanceof DropperBlock
                || block instanceof RedstoneWireBlock
                || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock
                || block instanceof ObserverBlock
                || block instanceof PistonBlock;
    }
}
