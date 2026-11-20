package com.vangix.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChunkBaseFinder {

    public record SuspiciousChunk(ChunkPos chunkPos, int baseScore, int sampleY) {}

    private static final List<SuspiciousChunk> FOUND_CHUNKS = new ArrayList<>();
    private static long lastScanTick = -20L;

    private static final int BASE_SCORE_THRESHOLD = 6;
    private static final int CHUNK_RADIUS = 4;

    private ChunkBaseFinder() {}

    public static List<SuspiciousChunk> getFoundChunks() {
        return FeatureState.areaScanner ? Collections.unmodifiableList(FOUND_CHUNKS) : Collections.emptyList();
    }

    public static void tick(Minecraft mc) {
        if (!FeatureState.areaScanner || mc == null || mc.level == null || mc.player == null) {
            FOUND_CHUNKS.clear();
            return;
        }

        long gameTime = mc.level.getGameTime();
        if (gameTime - lastScanTick < 15L) {
            return;
        }
        lastScanTick = gameTime;

        scanAroundPlayer(mc.level, mc.player.blockPosition());
    }

    private static void scanAroundPlayer(Level level, BlockPos playerPos) {
        FOUND_CHUNKS.clear();

        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;
        int minY = Math.max(level.getMinY(), playerPos.getY() - 32);
        int maxY = Math.min(level.getMaxY() - 1, playerPos.getY() + 24);

        for (int cx = centerChunkX - CHUNK_RADIUS; cx <= centerChunkX + CHUNK_RADIUS; cx++) {
            for (int cz = centerChunkZ - CHUNK_RADIUS; cz <= centerChunkZ + CHUNK_RADIUS; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                if (chunk == null) continue;

                ScoreResult res = scoreChunk(chunk, minY, maxY);
                if (res.score >= BASE_SCORE_THRESHOLD) {
                    FOUND_CHUNKS.add(new SuspiciousChunk(chunk.getPos(), res.score, res.sampleY));
                }
            }
        }
    }

    private record ScoreResult(int score, int sampleY) {}

    private static ScoreResult scoreChunk(LevelChunk chunk, int minY, int maxY) {
        ChunkPos pos = chunk.getPos();
        int startX = pos.getMinBlockX();
        int startZ = pos.getMinBlockZ();

        int score = 0;
        int ySum = 0;
        int hits = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y += 2) {
            for (int x = startX; x <= startX + 15; x += 2) {
                for (int z = startZ; z <= startZ + 15; z += 2) {
                    cursor.set(x, y, z);
                    BlockState state = chunk.getBlockState(cursor);
                    int pts = blockBaseWeight(state.getBlock());
                    if (pts > 0) {
                        score += pts;
                        ySum += y;
                        hits++;
                    }
                }
            }
        }

        int avgY = hits > 0 ? Math.round((float) ySum / hits) : 64;
        return new ScoreResult(score, avgY);
    }

    private static int blockBaseWeight(Block block) {
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
                || block == Blocks.ENDER_CHEST || block == Blocks.SHULKER_BOX) {
            return 4;
        }
        if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER
                || block == Blocks.CRAFTING_TABLE || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL || block == Blocks.ENCHANTING_TABLE || block == Blocks.BREWING_STAND) {
            return 2;
        }
        if (block == Blocks.BEACON || block == Blocks.RESPAWN_ANCHOR || block == Blocks.LODESTONE
                || block == Blocks.REDSTONE_LAMP || block == Blocks.OBSERVER || block == Blocks.PISTON
                || block == Blocks.STICKY_PISTON || block == Blocks.DISPENSER || block == Blocks.DROPPER) {
            return 2;
        }
        return 0;
    }
}
