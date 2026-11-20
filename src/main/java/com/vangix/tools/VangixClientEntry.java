package com.vangix.tools;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class VangixClientEntry implements ClientModInitializer {

    public static final String MOD_ID = "vangixtools";
    public static KeyMapping OPEN_MENU_KEY;

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
        if (OPEN_MENU_KEY == null) return;

        boolean clicked = false;
        while (OPEN_MENU_KEY.consumeClick()) {
            clicked = true;
        }

        if (!clicked) return;

        Screen current = getCurrentScreenSafe(client);
        if (current instanceof ControlPanelScreen) {
            setScreenSafe(client, null);
        } else if (current == null) {
            setScreenSafe(client, new ControlPanelScreen());
        }
    }

    static Screen getCurrentScreenSafe(Minecraft client) {
        if (client == null) return null;
        for (String methodName : new String[]{"getScreen", "screen"}) {
            try {
                Method m = client.getClass().getMethod(methodName);
                Object res = m.invoke(client);
                if (res instanceof Screen s) return s;
            } catch (Throwable ignored) {
            }
        }
        for (String fieldName : new String[]{"screen", "currentScreen"}) {
            try {
                Field f = client.getClass().getField(fieldName);
                Object res = f.get(client);
                if (res instanceof Screen s) return s;
            } catch (Throwable ignored) {
            }
            try {
                Field f = client.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object res = f.get(client);
                if (res instanceof Screen s) return s;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static void setScreenSafe(Minecraft client, Screen target) {
        if (client == null) return;
        for (String methodName : new String[]{"setScreen", "openScreen"}) {
            try {
                Method m = client.getClass().getMethod(methodName, Screen.class);
                m.invoke(client, target);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            if (client.gui != null) {
                Method m = client.gui.getClass().getMethod("setScreen", Screen.class);
                m.invoke(client.gui, target);
            }
        } catch (Throwable ignored) {
        }
    }

    private static KeyMapping registerKeyMappingSafe(KeyMapping mapping) {
        String[] helperClasses = {
                "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper",
                "net.fabricmc.fabric.api.client.key.mapping.v1.KeyMappingHelper"
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
