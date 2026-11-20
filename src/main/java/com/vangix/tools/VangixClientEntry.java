package com.vangix.tools;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

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

        registerWorldRenderSafe();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "status_hud"),
                (context, tickDelta) -> HelperTasks.renderOverlay(
                        context,
                        tickDelta.getGameTimeDeltaPartialTick(true)
                )
        );
    }

    private static void registerWorldRenderSafe() {
        String[][] candidates = {
                {"net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents", "LAST"},
                {"net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents", "END"},
                {"net.fabricmc.fabric.api.client.rendering.v1.LevelRenderEvents", "LAST"},
                {"net.fabricmc.fabric.api.client.rendering.v1.LevelRenderEvents", "END"}
        };

        for (String[] pair : candidates) {
            try {
                Class<?> eventClass = Class.forName(pair[0]);
                Field eventField = eventClass.getField(pair[1]);
                Object eventInstance = eventField.get(null);
                if (eventInstance == null) continue;

                Method registerMethod = findRegisterMethod(eventInstance.getClass());
                if (registerMethod == null) continue;

                Class<?> listenerInterface = registerMethod.getParameterTypes()[0];
                Object proxyListener = Proxy.newProxyInstance(
                        listenerInterface.getClassLoader(),
                        new Class<?>[]{listenerInterface},
                        (proxy, method, args) -> {
                            renderWorldOverlays(args);
                            return null;
                        }
                );
                registerMethod.invoke(eventInstance, proxyListener);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static Method findRegisterMethod(Class<?> eventObjClass) {
        for (Method m : eventObjClass.getMethods()) {
            if (m.getName().equals("register") && m.getParameterCount() == 1) {
                return m;
            }
        }
        return null;
    }

    private static void renderWorldOverlays(Object[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) return;

        PoseStack poseStack = extractPoseStack(args);
        if (poseStack == null) return;

        Vec3 camPos = extractCameraPos(mc, args);
        MultiBufferSource bufferSource = extractBufferSource(args);
        if (bufferSource == null) {
            bufferSource = mc.renderBuffers().bufferSource();
        }

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // 1) Chunk Finder — подсветка площади подозрительных чанков (плоскость + контур, без чата)
        if (FeatureState.areaScanner) {
            for (Map.Entry<Long, AreaScanCache.ScanResult> entry : AreaScanCache.getFlaggedChunks().entrySet()) {
                long key = entry.getKey();
                AreaScanCache.ScanResult res = entry.getValue();
                int cx = ChunkPos.getX(key);
                int cz = ChunkPos.getZ(key);
                double x1 = cx * 16.0;
                double z1 = cz * 16.0;
                double x2 = x1 + 16.0;
                double z2 = z1 + 16.0;
                double y = res.avgY + 0.05;

                drawHorizontalPlane(poseStack, bufferSource, x1, y, z1, x2, z2, 0.15f, 0.90f, 0.25f, 0.35f);
                drawHorizontalOutline(poseStack, bufferSource, x1, y + 0.01, z1, x2, z2, 0.15f, 0.95f, 0.25f, 0.95f);
            }
        }

        // 2) Block ESP — подсветка площади/объёма блоков разными цветами
        if (FeatureState.containerSight) {
            for (WorldOverlayRenderer.TargetBox box : WorldOverlayRenderer.getBlockTargets()) {
                BlockPos p = box.pos();
                double x1 = p.getX();
                double y1 = p.getY();
                double z1 = p.getZ();
                double x2 = x1 + 1.0;
                double y2 = y1 + 1.0;
                double z2 = z1 + 1.0;

                drawBoxFill(poseStack, bufferSource, x1, y1, z1, x2, y2, z2, box.r(), box.g(), box.b(), box.fillAlpha());
                drawBoxOutline(poseStack, bufferSource, x1, y1, z1, x2, y2, z2, box.r(), box.g(), box.b(), box.lineAlpha());
            }
        }

        // 3) Tracers — линии от взгляда до игроков и блоков-целей
        if (FeatureState.lineGuides) {
            Vec3 eyePos = mc.player.getEyePosition(1.0f);

            for (WorldOverlayRenderer.PlayerTarget pt : WorldOverlayRenderer.getPlayerTargets()) {
                if (!pt.tracer() || pt.player() == null) continue;
                Vec3 targetPos = pt.player().position().add(0.0, pt.player().getBbHeight() * 0.5, 0.0);
                drawLine(poseStack, bufferSource, eyePos, targetPos, pt.r(), pt.g(), pt.b(), 0.90f);
            }

            for (WorldOverlayRenderer.TargetBox tb : WorldOverlayRenderer.getTracerBlockTargets()) {
                Vec3 targetPos = Vec3.atCenterOf(tb.pos());
                drawLine(poseStack, bufferSource, eyePos, targetPos, tb.r(), tb.g(), tb.b(), 0.85f);
            }
        }

        poseStack.popPose();
    }

    private static PoseStack extractPoseStack(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof PoseStack ps) return ps;
            if (arg != null) {
                for (String mName : new String[]{"poseStack", "matrixStack", "getPoseStack", "getMatrixStack"}) {
                    try {
                        Method m = arg.getClass().getMethod(mName);
                        Object res = m.invoke(arg);
                        if (res instanceof PoseStack ps) return ps;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static Vec3 extractCameraPos(Minecraft mc, Object[] args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg != null) {
                    for (String mName : new String[]{"camera", "getCamera"}) {
                        try {
                            Method m = arg.getClass().getMethod(mName);
                            Object cam = m.invoke(arg);
                            if (cam != null) {
                                Method posMethod = cam.getClass().getMethod("position");
                                Object pos = posMethod.invoke(cam);
                                if (pos instanceof Vec3 v) return v;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
        return mc.gameRenderer.getMainCamera().position();
    }

    private static MultiBufferSource extractBufferSource(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof MultiBufferSource mbs) return mbs;
            if (arg != null) {
                for (String mName : new String[]{"consumers", "bufferSource", "getConsumers", "getBufferSource"}) {
                    try {
                        Method m = arg.getClass().getMethod(mName);
                        Object res = m.invoke(arg);
                        if (res instanceof MultiBufferSource mbs) return mbs;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static void drawHorizontalPlane(PoseStack poseStack, MultiBufferSource bufferSource,
                                           double x1, double y, double z1, double x2, double z2,
                                           float r, float g, float b, float a) {
        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.debugQuads());
        vc.addVertex(mat, (float) x1, (float) y, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y, (float) z1).setColor(r, g, b, a);
    }

    private static void drawHorizontalOutline(PoseStack poseStack, MultiBufferSource bufferSource,
                                             double x1, double y, double z1, double x2, double z2,
                                             float r, float g, float b, float a) {
        drawLine(poseStack, bufferSource, new Vec3(x1, y, z1), new Vec3(x2, y, z1), r, g, b, a);
        drawLine(poseStack, bufferSource, new Vec3(x2, y, z1), new Vec3(x2, y, z2), r, g, b, a);
        drawLine(poseStack, bufferSource, new Vec3(x2, y, z2), new Vec3(x1, y, z2), r, g, b, a);
        drawLine(poseStack, bufferSource, new Vec3(x1, y, z2), new Vec3(x1, y, z1), r, g, b, a);
    }

    private static void drawBoxFill(PoseStack poseStack, MultiBufferSource bufferSource,
                                    double x1, double y1, double z1, double x2, double y2, double z2,
                                    float r, float g, float b, float a) {
        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.debugQuads());

        // Верх и низ
        vc.addVertex(mat, (float) x1, (float) y2, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y2, (float) z1).setColor(r, g, b, a);

        vc.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y1, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y1, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y1, (float) z2).setColor(r, g, b, a);

        // Боковые грани
        vc.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y2, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y2, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y1, (float) z1).setColor(r, g, b, a);

        vc.addVertex(mat, (float) x2, (float) y1, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y1, (float) z2).setColor(r, g, b, a);

        vc.addVertex(mat, (float) x1, (float) y1, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y2, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);

        vc.addVertex(mat, (float) x2, (float) y1, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y2, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y1, (float) z2).setColor(r, g, b, a);
    }

    private static void drawBoxOutline(PoseStack poseStack, MultiBufferSource bufferSource,
                                       double x1, double y1, double z1, double x2, double y2, double z2,
                                       float r, float g, float b, float a) {
        drawHorizontalOutline(poseStack, bufferSource, x1, y1, z1, x2, z2, r, g, b, a);
        drawHorizontalOutline(poseStack, bufferSource, x1, y2, z1, x2, z2, r, g, b, a);

        drawLine(poseStack, bufferSource, new Vec3(x1, y1, z1), new Vec3(x1, y2, z1), r, g, b, a);
        drawLine(poseStack, bufferSource, new Vec3(x2, y1, z1), new Vec3(x2, y2, z1), r, g, b, a);
        drawLine(poseStack, bufferSource, new Vec3(x2, y1, z2), new Vec3(x2, y2, z2), r, g, b, a);
        drawLine(poseStack, bufferSource, new Vec3(x1, y1, z2), new Vec3(x1, y2, z2), r, g, b, a);
    }

    private static void drawLine(PoseStack poseStack, MultiBufferSource bufferSource,
                                 Vec3 from, Vec3 to, float r, float g, float b, float a) {
        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.lines());
        vc.addVertex(mat, (float) from.x, (float) from.y, (float) from.z).setColor(r, g, b, a);
        vc.addVertex(mat, (float) to.x, (float) to.y, (float) to.z).setColor(r, g, b, a);
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
