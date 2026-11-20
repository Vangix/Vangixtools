package com.vangix.tools;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

public class VangixClientEntry implements ClientModInitializer {

    public static final String MOD_ID = "vangixtools";
    public static KeyMapping OPEN_MENU_KEY;
    private static boolean prevRShiftDown = false;

    @Override
    public void onInitializeClient() {
        KeyMapping mapping = new KeyMapping(
                "key.vangixtools.open_menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                KeyMapping.Category.MISC
        );
        OPEN_MENU_KEY = registerKeyMappingSafe(mapping);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                prevRShiftDown = false;
                return;
            }

            handleMenuToggle(client);

            HelperTasks.tick(client);
            WorldOverlayRenderer.collectTargets(client);
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "status_hud"),
                (context, tickDelta) -> HelperTasks.renderOverlay(
                        context,
                        tickDelta.getGameTimeDeltaPartialTick(true)
                )
        );
    }

    private static void handleMenuToggle(Minecraft client) {
        long windowHandle = client.getWindow().handle();
        boolean rShiftDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean justPressed = rShiftDown && !prevRShiftDown;
        prevRShiftDown = rShiftDown;

        boolean consumed = false;
        if (OPEN_MENU_KEY != null) {
            while (OPEN_MENU_KEY.consumeClick()) {
                consumed = true;
            }
        }

        if (justPressed || (consumed && !(client.screen instanceof ControlPanelScreen))) {
            if (client.screen instanceof ControlPanelScreen) {
                client.setScreen(null);
            } else if (client.screen == null) {
                client.setScreen(new ControlPanelScreen());
            }
        }
    }

    private static KeyMapping registerKeyMappingSafe(KeyMapping mapping) {
        String[] helperClasses = {
                "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper",
                "net.fabricmc.fabric.api.client.key.mapping.v1.KeyMappingHelper",
                "net.fabricmc.fabric.api.client.keybinding.v1.KeyMappingHelper"
        };
        String[] methodNames = {"registerKeyBinding", "registerKeyMapping"};

        for (String className : helperClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                for (String methodName : methodNames) {
                    try {
                        Method m = clazz.getMethod(methodName, KeyMapping.class);
                        Object res = m.invoke(null, mapping);
                        if (res instanceof KeyMapping km) {
                            return km;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return mapping;
    }
}
