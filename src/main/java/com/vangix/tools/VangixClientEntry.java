package com.vangix.tools;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
        if (mc.level == null || mc.player == null) return;

        PoseStack poseStack = extractPoseStack(args);
        if (poseStack == null) return;

        Vec3 camPos = extractCameraPos(mc, args);
        Object bufferSource = extractBufferSource(mc, args);
        if (bufferSource == null) return;

        Object quadLayer = resolveRenderLayer("debugQuads", "DEBUG_QUADS", "lines", "LINES");
        Object lineLayer = resolveRenderLayer("lines", "LINES", "debugLineStrip", "DEBUG_LINE_STRIP");

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // 1) Chunk Finder — подсветка площади подозрительных чанков (плоскость + контур, без чата)
        if (FeatureState.areaScanner && quadLayer != null && lineLayer != null) {
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

                drawHorizontalPlane(poseStack, bufferSource, quadLayer, x1, y, z1, x2, z2, 0.15f, 0.90f, 0.25f, 0.35f);
                drawHorizontalOutline(poseStack, bufferSource, lineLayer, x1, y + 0.01, z1, x2, z2, 0.15f, 0.95f, 0.25f, 0.95f);
            }
        }

        // 2) Block ESP — подсветка площади/объёма блоков разными цветами
        if (FeatureState.containerSight && quadLayer != null && lineLayer != null) {
            for (WorldOverlayRenderer.TargetBox box : WorldOverlayRenderer.getBlockTargets()) {
                BlockPos p = box.pos();
                double x1 = p.getX();
                double y1 = p.getY();
                double z1 = p.getZ();
                double x2 = x1 + 1.0;
                double y2 = y1 + 1.0;
                double z2 = z1 + 1.0;

                drawBoxFill(poseStack, bufferSource, quadLayer, x1, y1, z1, x2, y2, z2, box.r(), box.g(), box.b(), box.fillAlpha());
                drawBoxOutline(poseStack, bufferSource, lineLayer, x1, y1, z1, x2, y2, z2, box.r(), box.g(), box.b(), box.lineAlpha());
            }
        }

        // 3) Tracers — линии от взгляда до игроков и блоков-целей
        if (FeatureState.lineGuides && lineLayer != null) {
            Vec3 eyePos = mc.player.getEyePosition(1.0f);

            for (WorldOverlayRenderer.PlayerTarget pt : WorldOverlayRenderer.getPlayerTargets()) {
                if (!pt.tracer() || pt.player() == null) continue;
                Vec3 targetPos = pt.player().position().add(0.0, pt.player().getBbHeight() * 0.5, 0.0);
                drawLine(poseStack, bufferSource, lineLayer, eyePos, targetPos, pt.r(), pt.g(), pt.b(), 0.90f);
            }

            for (WorldOverlayRenderer.TargetBox tb : WorldOverlayRenderer.getTracerBlockTargets()) {
                Vec3 targetPos = Vec3.atCenterOf(tb.pos());
                drawLine(poseStack, bufferSource, lineLayer, eyePos, targetPos, tb.r(), tb.g(), tb.b(), 0.85f);
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
                                for (String posName : new String[]{"position", "getPosition"}) {
                                    try {
                                        Method posMethod = cam.getClass().getMethod(posName);
                                        Object pos = posMethod.invoke(cam);
                                        if (pos instanceof Vec3 v) return v;
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
        if (mc.gameRenderer != null) {
            for (String camMethod : new String[]{"mainCamera", "camera", "getCamera"}) {
                try {
                    Method m = mc.gameRenderer.getClass().getMethod(camMethod);
                    Object cam = m.invoke(mc.gameRenderer);
                    if (cam != null) {
                        for (String posName : new String[]{"position", "getPosition"}) {
                            try {
                                Method posMethod = cam.getClass().getMethod(posName);
                                Object pos = posMethod.invoke(cam);
                                if (pos instanceof Vec3 v) return v;
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return mc.player != null ? mc.player.getEyePosition(1.0f) : Vec3.ZERO;
    }

    private static Object extractBufferSource(Minecraft mc, Object[] args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg != null) {
                    for (String mName : new String[]{"consumers", "bufferSource", "getConsumers", "getBufferSource"}) {
                        try {
                            Method m = arg.getClass().getMethod(mName);
                            Object res = m.invoke(arg);
                            if (res != null) return res;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
        if (mc != null) {
            for (String rbMethod : new String[]{"renderBuffers", "getRenderBuffers"}) {
                try {
                    Method m = mc.getClass().getMethod(rbMethod);
                    Object rb = m.invoke(mc);
                    if (rb != null) {
                        for (String bsMethod : new String[]{"bufferSource", "getBufferSource"}) {
                            try {
                                Method bs = rb.getClass().getMethod(bsMethod);
                                Object res = bs.invoke(rb);
                                if (res != null) return res;
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Object resolveRenderLayer(String... names) {
        String[] typeClasses = {
                "net.minecraft.client.renderer.RenderType",
                "net.minecraft.client.renderer.rendertype.RenderType",
                "net.minecraft.client.renderer.RenderTypes",
                "net.minecraft.client.renderer.rendertype.RenderTypes"
        };

        for (String clsName : typeClasses) {
            try {
                Class<?> cls = Class.forName(clsName);
                for (String name : names) {
                    try {
                        Method m = cls.getMethod(name);
                        if (m.getParameterCount() == 0) {
                            Object val = m.invoke(null);
                            if (val != null) return val;
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        Field f = cls.getField(name);
                        Object val = f.get(null);
                        if (val != null) return val;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static VertexConsumer obtainVertexConsumer(Object bufferSource, Object layer) {
        if (bufferSource == null || layer == null) return null;
        for (String methodName : new String[]{"getBuffer", "obtain", "buffer"}) {
            for (Method m : bufferSource.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                    try {
                        Object vc = m.invoke(bufferSource, layer);
                        if (vc instanceof VertexConsumer consumer) {
                            return consumer;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static void drawHorizontalPlane(PoseStack poseStack, Object bufferSource, Object layer,
                                           double x1, double y, double z1, double x2, double z2,
                                           float r, float g, float b, float a) {
        VertexConsumer vc = obtainVertexConsumer(bufferSource, layer);
        if (vc == null) return;
        Matrix4f mat = poseStack.last().pose();
        vc.addVertex(mat, (float) x1, (float) y, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y, (float) z1).setColor(r, g, b, a);
    }

    private static void drawHorizontalOutline(PoseStack poseStack, Object bufferSource, Object layer,
                                             double x1, double y, double z1, double x2, double z2,
                                             float r, float g, float b, float a) {
        drawLine(poseStack, bufferSource, layer, new Vec3(x1, y, z1), new Vec3(x2, y, z1), r, g, b, a);
        drawLine(poseStack, bufferSource, layer, new Vec3(x2, y, z1), new Vec3(x2, y, z2), r, g, b, a);
        drawLine(poseStack, bufferSource, layer, new Vec3(x2, y, z2), new Vec3(x1, y, z2), r, g, b, a);
        drawLine(poseStack, bufferSource, layer, new Vec3(x1, y, z2), new Vec3(x1, y, z1), r, g, b, a);
    }

    private static void drawBoxFill(PoseStack poseStack, Object bufferSource, Object layer,
                                    double x1, double y1, double z1, double x2, double y2, double z2,
                                    float r, float g, float b, float a) {
        VertexConsumer vc = obtainVertexConsumer(bufferSource, layer);
        if (vc == null) return;
        Matrix4f mat = poseStack.last().pose();

        vc.addVertex(mat, (float) x1, (float) y2, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y2, (float) z1).setColor(r, g, b, a);

        vc.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y1, (float) z1).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y1, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x1, (float) y1, (float) z2).setColor(r, g, b, a);

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
        vc.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b,
