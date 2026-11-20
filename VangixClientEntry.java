package com.vangix.tools;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class VangixClientEntry implements ClientModInitializer {

    public static final String MOD_ID = "vangixtools";
    public static KeyMapping OPEN_MENU_KEY;

    @Override
    public void onInitializeClient() {
        OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vangixtools.open_menu",
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (OPEN_MENU_KEY.consumeClick()) {
                if (client.screen instanceof ControlPanelScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new ControlPanelScreen());
                }
            }

            HelperTasks.tick(client);
            WorldOverlayRenderer.collectTargets(client);
        });

        HudRenderCallback.EVENT.register((context, tickDelta) ->
                HelperTasks.renderOverlay(context, tickDelta.getGameTimeDeltaPartialTick(true))
        );
    }
}
