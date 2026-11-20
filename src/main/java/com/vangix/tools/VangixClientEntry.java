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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.StringJoiner;

public class VangixClientEntry implements ClientModInitializer {

    public static final String MOD_ID = "vangixtools";
    public static KeyMapping OPEN_MENU_KEY;

    private static long lastOverlayFrameId = -1L;
    private static boolean loggedRenderDebug = false;
    private static boolean loggedGizmoMethods = false;

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

    public static void renderWorldOverlaysFromLevelRenderer(Object levelRenderer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        long frameToken = mc.level.getGameTime() * 1_000_000L + System.nanoTime() / 4_000_000L;
        if (frameToken == lastOverlayFrameId) return;
        lastOverlayFrameId = frameToken;

        Object gizmoCollector = extractGizmoCollector(mc, levelRenderer);
        Vec3 camPos = extractCameraPos(mc, levelRenderer);
        Object renderBuffers = extractRenderBuffers(mc, levelRenderer);
        Object bufferSource = extractBufferSourceFromRenderBuffers(renderBuffers, levelRenderer);
        Object quadLayer = resolveRenderLayer("debugQuads", "DEBUG_QUADS", "lines", "LINES");
        Object lineLayer = resolveRenderLayer("lines", "LINES", "debugLineStrip", "DEBUG_LINE_STRIP");

        if (!loggedRenderDebug) {
            loggedRenderDebug = true;
            System.out.println("[VangixTools] Overlay debug -> gizmoCollector="
                    + (gizmoCollector != null ? gizmoCollector.getClass().getName() : "null")
                    + ", bufferSource=" + (bufferSource != null ? bufferSource.getClass().getName() : "null")
                    + ", blocks=" + WorldOverlayRenderer.getBlockTargets().size()
                    + ", chunks=" + WorldOverlayRenderer.getChunkTargets().size()
                    + ", players=" + WorldOverlayRenderer.getPlayerTargets().size());
        }

        if (gizmoCollector != null && !loggedGizmoMethods) {
            loggedGizmoMethods = true;
            StringJoiner sj = new StringJoiner(", ");
            for (Method m : gizmoCollector.getClass().getMethods()) {
                if (m.getDeclaringClass() != Object.class) {
                    StringJoiner p = new StringJoiner(", ");
                    for (Class<?> c : m.getParameterTypes()) {
                        p.add(c.getSimpleName());
                    }
                    sj.add(m.getReturnType().getSimpleName() + " " + m.getName() + "(" + p + ")");
                }
            }
            System.out.println("[VangixTools] SimpleGizmoCollector methods: " + sj);
        }

        if (gizmoCollector != null) {
            renderWithGizmos(mc, gizmoCollector);
            return;
        }

        if (bufferSource != null && (quadLayer != null || lineLayer != null)) {
            PoseStack poseStack = new PoseStack();
            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            if (FeatureState.areaScanner) {
                for (WorldOverlayRenderer.ChunkTarget ct : WorldOverlayRenderer.getChunkTargets()) {
                    if (quadLayer != null) {
                        drawHorizontalPlane(poseStack, bufferSource, quadLayer,
                                ct.minX(), ct.y(), ct.minZ(), ct.maxX(), ct.maxZ(),
                                ct.r(), ct.g(), ct.b(), ct.fillAlpha());
                    }
                    if (lineLayer != null) {
                        drawHorizontalOutline(poseStack, bufferSource, lineLayer,
                                ct.minX(), ct.y() + 0.01, ct.minZ(), ct.maxX(), ct.maxZ(),
                                ct.r(), ct.g(), ct.b(), ct.lineAlpha());
                    }
                }
            }

            if (FeatureState.containerSight) {
                for (WorldOverlayRenderer.TargetBox box : WorldOverlayRenderer.getBlockTargets()) {
                    BlockPos p = box.pos();
                    double x1 = p.getX();
                    double y1 = p.getY();
                    double z1 = p.getZ();
                    double x2 = x1 + 1.0;
                    double y2 = y1 + 1.0;
                    double z2 = z1 + 1.0;

                    if (quadLayer != null) {
                        drawBoxFill(poseStack, bufferSource, quadLayer, x1, y1, z1, x2, y2, z2,
                                box.r(), box.g(), box.b(), box.fillAlpha());
                    }
                    if (lineLayer != null) {
                        drawBoxOutline(poseStack, bufferSource, lineLayer, x1, y1, z1, x2, y2, z2,
                                box.r(), box.g(), box.b(), box.lineAlpha());
                    }
                }
            }

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
    }

    private static void renderWithGizmos(Minecraft mc, Object gizmoCollector) {
        if (FeatureState.areaScanner) {
            for (WorldOverlayRenderer.ChunkTarget ct : WorldOverlayRenderer.getChunkTargets()) {
                int argb = toArgb(ct.r(), ct.g(), ct.b(), Math.max(0.65f, ct.lineAlpha()));
                double y = ct.y() + 0.02;
                gizmoLine(gizmoCollector, new Vec3(ct.minX(), y, ct.minZ()), new Vec3(ct.maxX(), y, ct.minZ()), argb, 2.0f);
                gizmoLine(gizmoCollector, new Vec3(ct.maxX(), y, ct.minZ()), new Vec3(ct.maxX(), y, ct.maxZ()), argb, 2.0f);
                gizmoLine(gizmoCollector, new Vec3(ct.maxX(), y, ct.maxZ()), new Vec3(ct.minX(), y, ct.maxZ()), argb, 2.0f);
                gizmoLine(gizmoCollector, new Vec3(ct.minX(), y, ct.maxZ()), new Vec3(ct.minX(), y, ct.minZ()), argb, 2.0f);
            }
        }

        if (FeatureState.containerSight) {
            for (WorldOverlayRenderer.TargetBox box : WorldOverlayRenderer.getBlockTargets()) {
                BlockPos p = box.pos();
                AABB aabb = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0, p.getY() + 1.0, p.getZ() + 1.0);
                int lineColor = toArgb(box.r(), box.g(), box.b(), Math.max(0.75f, box.lineAlpha()));
                gizmoBoxOutline(gizmoCollector, aabb, lineColor, 2.0f);
            }
        }

        if (FeatureState.lineGuides && mc.player != null) {
            Vec3 eyePos = mc.player.getEyePosition(1.0f).subtract(0.0, 0.12, 0.0);

            for (WorldOverlayRenderer.PlayerTarget pt : WorldOverlayRenderer.getPlayerTargets()) {
                if (!pt.tracer() || pt.player() == null) continue;
                Vec3 targetPos = pt.player().position().add(0.0, pt.player().getBbHeight() * 0.5, 0.0);
                int color = toArgb(pt.r(), pt.g(), pt.b(), 0.95f);
                gizmoLine(gizmoCollector, eyePos, targetPos, color, 2.0f);
            }

            for (WorldOverlayRenderer.TargetBox tb : WorldOverlayRenderer.getTracerBlockTargets()) {
                Vec3 targetPos = Vec3.atCenterOf(tb.pos());
                int color = toArgb(tb.r(), tb.g(), tb.b(), 0.90f);
                gizmoLine(gizmoCollector, eyePos, targetPos, color, 1.8f);
            }
        }
    }

    private static void gizmoBoxOutline(Object collector, AABB b, int argb, float width) {
        Vec3 p000 = new Vec3(b.minX, b.minY, b.minZ);
        Vec3 p100 = new Vec3(b.maxX, b.minY, b.minZ);
        Vec3 p101 = new Vec3(b.maxX, b.minY, b.maxZ);
        Vec3 p001 = new Vec3(b.minX, b.minY, b.maxZ);

        Vec3 p010 = new Vec3(b.minX, b.maxY, b.minZ);
        Vec3 p110 = new Vec3(b.maxX, b.maxY, b.minZ);
        Vec3 p111 = new Vec3(b.maxX, b.maxY, b.maxZ);
        Vec3 p011 = new Vec3(b.minX, b.maxY, b.maxZ);

        gizmoLine(collector, p000, p100, argb, width);
        gizmoLine(collector, p100, p101, argb, width);
        gizmoLine(collector, p101, p001, argb, width);
        gizmoLine(collector, p001, p000, argb, width);

        gizmoLine(collector, p010, p110, argb, width);
        gizmoLine(collector, p110, p111, argb, width);
        gizmoLine(collector, p111, p011, argb, width);
        gizmoLine(collector, p011, p010, argb, width);

        gizmoLine(collector, p000, p010, argb, width);
        gizmoLine(collector, p100, p110, argb, width);
        gizmoLine(collector, p101, p111, argb, width);
        gizmoLine(collector, p001, p011, argb, width);
    }

    private static void gizmoLine(Object collector, Vec3 from, Vec3 to, int argb, float width) {
        if (collector == null) return;
        for (Method m : collector.getClass().getMethods()) {
            String name = m.getName().toLowerCase();
            if (!name.contains("line")) continue;
            Class<?>[] params = m.getParameterTypes();

            try {
                if (params.length == 3 && params[0] == Vec3.class && params[1] == Vec3.class && params[2] == int.class) {
                    m.invoke(collector, from, to, argb);
                    return;
                }
                if (params.length == 4 && params[0] == Vec3.class && params[1] == Vec3.class && params[2] == int.class && params[3] == float.class) {
                    m.invoke(collector, from, to, argb, width);
                    return;
                }
                if (params.length == 4 && params[0] == Vec3.class && params[1] == Vec3.class && params[2] == float.class && params[3] == int.class) {
                    m.invoke(collector, from, to, width, argb);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static Object extractGizmoCollector(Minecraft mc, Object levelRenderer) {
        Object fromLevel = invokeOrReadAny(
                levelRenderer,
                new String[]{"renderThreadGizmos", "getRenderThreadGizmos", "perTickGizmos", "getPerTickGizmos"},
                new String[]{"renderThreadGizmos", "perTickGizmos"}
        );
        if (fromLevel != null) return fromLevel;

        return invokeOrReadAny(
                mc,
                new String[]{"perTickGizmos", "getPerTickGizmos"},
                new String[]{"perTickGizmos"}
        );
    }

    private static int toArgb(float r, float g, float b, float a) {
        int ia = Math.max(0, Math.min(255, Math.round(a * 255.0f)));
        int ir = Math.max(0, Math.min(255, Math.round(r * 255.0f)));
        int ig = Math.max(0, Math.min(255, Math.round(g * 255.0f)));
        int ib = Math.max(0, Math.min(255, Math.round(b * 255.0f)));
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static Object extractRenderBuffers(Minecraft mc, Object levelRenderer) {
        Object rb = invokeOrReadAny(
                levelRenderer,
                new String[]{"renderBuffers", "getRenderBuffers"},
                new String[]{"renderBuffers"}
        );
        if (rb != null) return rb;

        if (mc != null) {
            rb = invokeOrReadAny(
                    mc,
                    new String[]{"renderBuffers", "getRenderBuffers"},
                    new String[]{"renderBuffers"}
            );
        }
        return rb;
    }

    private static Object extractBufferSourceFromRenderBuffers(Object renderBuffers, Object levelRenderer) {
        String[] bufferNames = {
                "bufferSource", "getBufferSource",
                "crumbleBufferSource", "getCrumbleBufferSource",
                "outlineBufferSource", "getOutlineBufferSource",
                "multiBufferSource", "getMultiBufferSource",
                "buffers", "getBuffers",
                "consumers", "getConsumers"
        };

        Object bs = invokeOrReadAny(renderBuffers, bufferNames, bufferNames);
        if (bs != null) return bs;

        return invokeOrReadAny(levelRenderer, bufferNames, bufferNames);
    }

    private static Vec3 extractCameraPos(Minecraft mc, Object levelRenderer) {
        Object gameRenderer = invokeOrReadAny(
                mc,
                new String[]{"gameRenderer", "getGameRenderer"},
                new String[]{"gameRenderer"}
        );
        if (gameRenderer == null) {
            gameRenderer = invokeOrReadAny(
                    levelRenderer,
                    new String[]{"gameRenderer", "getGameRenderer"},
                    new String[]{"gameRenderer"}
            );
        }

        if (gameRenderer != null) {
            Object cam = invokeOrReadAny(
                    gameRenderer,
                    new String[]{"mainCamera", "camera", "getCamera"},
                    new String[]{"mainCamera", "camera"}
            );
            if (cam != null) {
                Object pos = invokeOrReadAny(
                        cam,
                        new String[]{"position", "getPosition"},
                        new String[]{"position"}
                );
                if (pos instanceof Vec3 v) return v;
            }
        }
        return mc != null && mc.player != null ? mc.player.getEyePosition(1.0f) : Vec3.ZERO;
    }

    private static Object invokeOrReadAny(Object target, String[] methodNames, String[] fieldNames) {
        if (target == null) return null;
        Class<?> cls = target.getClass();

        for (String mName : methodNames) {
            try {
                Method m = cls.getMethod(mName);
                if (m.getParameterCount() == 0) {
                    Object val = m.invoke(target);
                    if (val != null) return val;
                }
            } catch (Throwable ignored) {
            }
            try {
                Method m = cls.getDeclaredMethod(mName);
                if (m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object val = m.invoke(target);
                    if (val != null) return val;
                }
            } catch (Throwable ignored) {
            }
        }

        for (String fName : fieldNames) {
            try {
                Field f = cls.getField(fName);
                Object val = f.get(target);
                if (val != null) return val;
            } catch (Throwable ignored) {
            }
            try {
                Field f = cls.getDeclaredField(fName);
                f.setAccessible(true);
                Object val = f.get(target);
                if (val != null) return val;
            } catch (Throwable ignored) {
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
        vc.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(mat, (float) x2, (float) y1, (float) z2).setColor(r, g, b, a);
    }

    private static void drawBoxOutline(PoseStack poseStack, Object bufferSource, Object layer,
                                       double x1, double y1, double z1, double x2, double y2, double z2,
                                       float r, float g, float b, float a) {
        drawHorizontalOutline(poseStack, bufferSource, layer, x1, y1, z1, x2, z2, r, g, b, a);
        drawHorizontalOutline(poseStack, bufferSource, layer, x1, y2, z1, x2, z2, r, g, b, a);

        drawLine(poseStack, bufferSource, layer, new Vec3(x1, y1, z1), new Vec3(x1, y2, z1), r, g, b, a);
        drawLine(poseStack, bufferSource, layer, new Vec3(x2, y1, z1), new Vec3(x2, y2, z1), r, g, b, a);
        drawLine(poseStack, bufferSource, layer, new Vec3(x2, y1, z2), new Vec3(x2, y2, z2), r, g, b, a);
        drawLine(poseStack, bufferSource, layer, new Vec3(x1, y1, z2), new Vec3(x1, y2, z2), r, g, b, a);
    }

    private static void drawLine(PoseStack poseStack, Object bufferSource, Object layer,
                                 Vec3 from, Vec3 to, float r, float g, float b, float a) {
        VertexConsumer vc = obtainVertexConsumer(bufferSource, layer);
        if (vc == null) return;
        Matrix4f mat = poseStack.last().pose();
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
package com.vangixtools;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.StringJoiner;
import net.fabricmc.api.ClientModInitializer;

public class VangixClientEntry implements ClientModInitializer {
    private static boolean dumpedGizmos = false;

    @Override
    public void onInitializeClient() {
        // Регистрация кейбиндов, событий и инициализация клиента
    }

    public static void onWorldOverlayRender() {
        if (!dumpedGizmos) {
            dumpedGizmos = true;
            dumpGizmoPackageClasses();
        }
    }

    private static void dumpGizmoPackageClasses() {
        String[] candidates = {
                "net.minecraft.gizmos.Gizmos",
                "net.minecraft.gizmos.Gizmo",
                "net.minecraft.gizmos.GizmoProperties",
                "net.minecraft.gizmos.SimpleGizmoCollector",
                "net.minecraft.gizmos.LineGizmo",
                "net.minecraft.gizmos.BoxGizmo",
                "net.minecraft.gizmos.CubeGizmo",
                "net.minecraft.gizmos.CuboidGizmo",
                "net.minecraft.gizmos.QuadGizmo",
                "net.minecraft.gizmos.PolygonGizmo",
                "net.minecraft.gizmos.PointGizmo",
                "net.minecraft.gizmos.TextGizmo"
        };

        for (String className : candidates) {
            try {
                Class<?> clazz = Class.forName(className);
                StringJoiner entries = new StringJoiner(", ");

                for (Constructor<?> ctor : clazz.getConstructors()) {
                    entries.add("new " + clazz.getSimpleName() + formatParams(ctor.getParameterTypes()));
                }

                for (Method method : clazz.getMethods()) {
                    if (method.getDeclaringClass() != Object.class) {
                        entries.add(
                                method.getReturnType().getSimpleName()
                                        + " "
                                        + method.getName()
                                        + formatParams(method.getParameterTypes())
                        );
                    }
                }

                System.out.println("[VangixTools][GizmoDump] " + className + " -> " + entries);
            } catch (Throwable ignored) {
                // Класс с таким именем отсутствует в этой версии сборки
            }
        }
    }

    private static String formatParams(Class<?>[] params) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (Class<?> param : params) {
            joiner.add(param.getSimpleName());
        }
        return joiner.toString();
    }
}    }
}
