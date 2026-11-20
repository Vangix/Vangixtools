package com.vangix.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

public class HelperTasks {

    private static final Random RNG = new Random();
    private static int guardTimer = 0;
    private static boolean appliedFullbright = false;
    private static double previousGamma = 0.5;

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            restoreGammaIfNeeded(client);
            return;
        }

        if (guardTimer > 0) guardTimer--;

        AreaScanCache.tick(client);
        updateFullbright(client);

        if (FeatureState.quickHarvest) runQuickHarvest(client);
        if (FeatureState.offhandGuard) runOffhandGuard(client);
    }

    private static void updateFullbright(Minecraft client) {
        if (client.options == null) return;
        try {
            var gammaOption = client.options.gamma();
            if (FeatureState.fullbrightFlag) {
                if (!appliedFullbright) {
                    previousGamma = gammaOption.get();
                    appliedFullbright = true;
                }
                if (gammaOption.get() < 1.0) {
                    gammaOption.set(1.0);
                }
            } else {
                restoreGammaIfNeeded(client);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void restoreGammaIfNeeded(Minecraft client) {
        if (!appliedFullbright || client.options == null) return;
        try {
            client.options.gamma().set(previousGamma);
        } catch (Throwable ignored) {
        } finally {
            appliedFullbright = false;
        }
    }

    public static void renderOverlay(GuiGraphicsExtractor context, float tickDelta) {
        if (!FeatureState.statusHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<String> enabled = FeatureState.getEnabledTools();
        if (enabled.isEmpty()) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int color = FeatureState.getHudColor();
        int y = 8;

        for (String label : enabled) {
            int textWidth = mc.font.width(label);
            int x = screenWidth - textWidth - 10;

            context.fill(x - 4, y - 2, screenWidth - 4, y + 10, 0xA80B101B);
            context.fill(screenWidth - 5, y - 2, screenWidth - 4, y + 10, color);
            context.text(mc.font, Component.literal(label), x - 1, y, color);
            y += 12;
        }
    }

    private static void runQuickHarvest(Minecraft client) {
        if (!client.options.keyAttack.isDown()) return;
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockState state = client.level.getBlockState(hit.getBlockPos());
        if (state.isAir()) return;

        Inventory inv = client.player.getInventory();
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        if (bestSlot != -1 && inv.getSelectedSlot() != bestSlot) {
            inv.setSelectedSlot(bestSlot);
        }
    }

    private static int nextGuardDelay() {
        int min = FeatureState.minGuardDelay;
        int max = FeatureState.maxGuardDelay;
        if (max <= min) return Math.max(1, min);
        return min + RNG.nextInt(max - min + 1);
    }

    private static void runOffhandGuard(Minecraft client) {
        if (guardTimer > 0) return;
        if (client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;

        Inventory inv = client.player.getInventory();
        int foundSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                foundSlot = i;
                break;
            }
        }
        if (foundSlot == -1) return;

        boolean movedViaPackets = trySwapToOffhandViaGameMode(client, foundSlot);
        if (!movedViaPackets) {
            try {
                ItemStack currentOffhand = inv.getItem(Inventory.SLOT_OFFHAND);
                ItemStack totemStack = inv.getItem(foundSlot);
                inv.setItem(Inventory.SLOT_OFFHAND, totemStack);
                inv.setItem(foundSlot, currentOffhand);
            } catch (Throwable ignored) {
            }
        }

        guardTimer = nextGuardDelay();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean trySwapToOffhandViaGameMode(Minecraft client, int invSlot) {
        if (client.gameMode == null || client.player == null) return false;
        try {
            int containerSlot = invSlot < 9 ? invSlot + 36 : invSlot;
            int syncId = client.player.inventoryMenu.containerId;

            for (Method m : client.gameMode.getClass().getMethods()) {
                if (m.getName().equals("handleInventoryMouseClick") && m.getParameterCount() == 5) {
                    Class<?> clickTypeParam = m.getParameterTypes()[3];
                    Object swapArg = clickTypeParam.isEnum()
                            ? Enum.valueOf((Class<Enum>) clickTypeParam, "SWAP")
                            : null;
                    if (swapArg != null) {
                        m.invoke(client.gameMode, syncId, containerSlot, 40, swapArg, client.player);
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
