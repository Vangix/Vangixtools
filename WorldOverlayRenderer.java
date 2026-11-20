package com.vangix.tools;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public class WorldOverlayRenderer {

    public static void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        if (FeatureState.entitySight || FeatureState.lineGuides) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || !player.isAlive()) continue;
                if (FeatureState.entitySight && (FeatureState.showEntityTag || FeatureState.showEntityGear)) {
                    summarizeGear(player);
                }
            }
        }

        if (FeatureState.containerSight) {
            int radius = 4;
            int centerX = mc.player.getChunkPos().x;
            int centerZ = mc.player.getChunkPos().z;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = centerX + dx;
                    int cz = centerZ + dz;
                    if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                    if (!(mc.world.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof WorldChunk chunk)) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        matchBlockColor(be);
                    }
                }
            }
        }
    }

    private static float[] matchBlockColor(BlockEntity be) {
        if (FeatureState.filterContainers && (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity)) {
            return new float[]{1.0f, 0.65f, 0.0f};
        }
        if (FeatureState.filterShulkers && be instanceof ShulkerBoxBlockEntity) {
            return new float[]{0.85f, 0.25f, 1.0f};
        }
        if (FeatureState.filterLogicBlocks && be instanceof CommandBlockBlockEntity) {
            return new float[]{0.15f, 0.85f, 1.0f};
        }
        if (FeatureState.filterSpawners && be instanceof MobSpawnerBlockEntity) {
            return new float[]{1.0f, 0.2f, 0.2f};
        }
        if (FeatureState.filterVaultBlocks && be instanceof EnderChestBlockEntity) {
            return new float[]{0.2f, 1.0f, 0.7f};
        }
        return null;
    }

    private static String summarizeGear(PlayerEntity player) {
        List<String> parts = new ArrayList<>();
        ItemStack main = player.getMainHandStack();
        if (!main.isEmpty()) {
            parts.add("H:" + main.getName().getString());
        }

        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        int pieces = 0;
        for (EquipmentSlot slot : armorSlots) {
            if (!player.getEquippedStack(slot).isEmpty()) {
                pieces++;
            }
        }
        if (pieces > 0) {
            parts.add("Armor:" + pieces + "/4");
        }
        return String.join(" | ", parts);
    }
}
