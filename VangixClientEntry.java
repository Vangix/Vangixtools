package com.vangix.tools;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class VangixClientEntry implements ClientModInitializer {

    private static KeyBinding menuBind;

    @Override
    public void onInitializeClient() {
        menuBind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vangixs_tools.panel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.vangixs_tools"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuBind.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ControlPanelScreen());
                } else if (client.currentScreen instanceof ControlPanelScreen) {
                    client.setScreen(null);
                }
            }
            HelperTasks.tick(client);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldOverlayRenderer::render);
        HudRenderCallback.EVENT.register(HelperTasks::renderOverlay);
    }
}
