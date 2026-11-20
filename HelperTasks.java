package com.vangix.tools;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.List;
import java.util.Random;

public class HelperTasks {

    private static final Random RNG = new Random();
    private static int guardTimer = 0;

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        if (guardTimer > 0) guardTimer--;

        AreaScanCache.tick(client);

        if (FeatureState.quickHarvest) runQuickHarvest(client);
        if (FeatureState.offhandGuard) runOffhandGuard(client);
    }

    public static void renderOverlay(DrawContext context, float tickDelta) {
        if (!FeatureState.statusHud) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        List<String> enabled = FeatureState.getEnabledTools();
        if (enabled.isEmpty()) return;

        int screenWidth = mc.getWindow().getScaledWidth();
        int color = FeatureState.getHudColor();
        int y = 8;

        for (String label : enabled) {
            int textWidth = mc.textRenderer.getWidth(label);
            int x = screenWidth - textWidth - 8;

            context.fill(x - 3, y - 1, screenWidth - 5, y + 9, 0x700F172A);
            context.drawTextWithShadow(mc.textRenderer, label, x, y, color);
            y += 11;
        }
    }

    private static void runQuickHarvest(MinecraftClient client) {
        if (!client.options.attackKey.isPressed()) return;
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockState state = client.world.getBlockState(hit.getBlockPos());
        if (state.isAir()) return;

        PlayerInventory inv = client.player.getInventory();
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getStack(slot);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        if (bestSlot != -1 && inv.selectedSlot != bestSlot) {
            inv.selectedSlot = bestSlot;
        }
    }

    private static int nextGuardDelay() {
        int min = FeatureState.minGuardDelay;
        int max = FeatureState.maxGuardDelay;
        if (max <= min) return Math.max(1, min);
        return min + RNG.nextInt(max - min + 1);
    }

    private static void runOffhandGuard(MinecraftClient client) {
        if (guardTimer > 0 || client.currentScreen != null) return;
        if (client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        PlayerInventory inv = client.player.getInventory();
        int targetSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                targetSlot = i;
                break;
            }
        }
        if (targetSlot == -1 || client.interactionManager == null) return;

        int syncId = client.player.playerScreenHandler.syncId;
        int containerSlot = targetSlot < 9 ? targetSlot + 36 : targetSlot;
        int offhandSlot = 45;

        client.interactionManager.clickSlot(syncId, containerSlot, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(syncId, offhandSlot, 0, SlotActionType.PICKUP, client.player);
        if (!client.player.playerScreenHandler.getCursorStack().isEmpty()) {
            client.interactionManager.clickSlot(syncId, containerSlot, 0, SlotActionType.PICKUP, client.player);
        }

        guardTimer = nextGuardDelay();
    }
}
