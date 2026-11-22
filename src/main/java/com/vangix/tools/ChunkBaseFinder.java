package com.vangix.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChunkBaseFinder {

    public record SuspiciousChunk(ChunkPos chunkPos, int baseScore, int sampleY) {}

    private static final List<SuspiciousChunk> FOUND_CHUNKS = Collections.synchronizedList(new ArrayList<>());
    private static long lastScanTick = -20L;

    private static final int BASE_SCORE_THRESHOLD = 4;
    private static final int CHUNK_RADIUS = 3;

    private ChunkBaseFinder() {}

    public static List<SuspiciousChunk> getFoundChunks() {
        if (!FeatureState.areaScanner) {
            return Collections.emptyList();
        }
        synchronized (FOUND_CHUNKS) {
            return new ArrayList<>(FOUND_CHUNKS);
        }
    }

    public static void tick(Minecraft mc) {
        if (!FeatureState.areaScanner || mc == null || mc.level == null || mc.player == null) {
            synchronized (FOUND_CHUNKS) {
                FOUND_CHUNKS.clear();
            }
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
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;

        int minY = level.getMinY();
        int maxY = level.getMaxY() - 1;

        List<SuspiciousChunk> nextChunks = new ArrayList<>();

        for (int cx = centerChunkX - CHUNK_RADIUS; cx <= centerChunkX + CHUNK_RADIUS; cx++) {
            for (int cz = centerChunkZ - CHUNK_RADIUS; cz <= centerChunkZ + CHUNK_RADIUS; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                if (chunk == null) continue;

                ScoreResult res = scoreChunk(chunk, minY, maxY, playerPos.getY());
                if (res.score >= BASE_SCORE_THRESHOLD) {
                    nextChunks.add(new SuspiciousChunk(chunk.getPos(), res.score, res.sampleY));
                }
            }
        }

        synchronized (FOUND_CHUNKS) {
            FOUND_CHUNKS.clear();
            FOUND_CHUNKS.addAll(nextChunks);
        }
    }

    private record ScoreResult(int score, int sampleY) {}

    private static ScoreResult scoreChunk(LevelChunk chunk, int minY, int maxY, int fallbackY) {
        ChunkPos pos = chunk.getPos();
        int startX = pos.getMinBlockX();
        int startZ = pos.getMinBlockZ();

        int score = 0;
        int ySum = 0;
        int hits = 0;

        // 1. Быстрая проверка всех BlockEntity на любой высоте (сундуки, бочки, шалкеры, печи, спавнеры)
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            BlockPos bePos = be.getBlockPos();
            int pts = blockEntityWeight(be);
            if (pts > 0) {
                score += pts;
                ySum += bePos.getY();
                hits++;
            }
        }

        // 2. Сэмпл по непустым секциям от бедрока до максимума высоты без фризов
        LevelChunkSection[] sections = chunk.getSections();
        int chunkMinY = chunk.getMinY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int sIdx = 0; sIdx < sections.length; sIdx++) {
            LevelChunkSection section = sections[sIdx];
            if (section == null || section.hasOnlyAir()) continue;

            int secBottomY = chunkMinY + sIdx * 16;
            int secTopY = secBottomY + 15;

            int fromY = Math.max(minY, secBottomY);
            int toY = Math.min(maxY, secTopY);

            for (int y = fromY; y <= toY; y += 2) {
                for (int x = startX; x <= startX + 15; x += 2) {
                    for (int z = startZ; z <= startZ + 15; z += 2) {
                        cursor.set(x, y, z);
                        BlockState state = chunk.getBlockState(cursor);
                        if (state.isAir()) continue;
                        int pts = blockBaseWeight(state.getBlock());
                        if (pts > 0) {
                            score += pts;
                            ySum += y;
                            hits++;
                        }
                    }
                }
            }
        }

        int avgY = hits > 0 ? Math.round((float) ySum / hits) : fallbackY;
        return new ScoreResult(score, avgY);
    }

    private static int blockEntityWeight(BlockEntity be) {
        String name = be.getClass().getSimpleName();
        if (name.contains("Chest") || name.contains("Barrel") || name.contains("ShulkerBox")) {
            return 4;
        }
        if (name.contains("Furnace") || name.contains("BrewingStand") || name.contains("Spawner") || name.contains("Beacon")) {
            return 3;
        }
        return 0;
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
