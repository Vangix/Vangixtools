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
    private static boolean loggedBufferIntrospect = false;

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

        Vec3 camPos = extractCameraPos(mc, null);
        Object bufferSource = extractBufferSource(mc, levelRenderer);
        Object quadLayer = resolveRenderLayer("debugQuads", "DEBUG_QUADS", "lines", "LINES");
        Object lineLayer = resolveRenderLayer("lines", "LINES", "debugLineStrip", "DEBUG_LINE_STRIP");

        if (!loggedRenderDebug) {
            loggedRenderDebug = true;
            System.out.println("[VangixTools] Overlay debug -> camPos=" + camPos
                    + ", bufferSource=" + (bufferSource != null ? bufferSource.getClass().getName() : "null")
                    + ", quadLayer=" + (quadLayer != null ? quadLayer : "null")
                    + ", lineLayer=" + (lineLayer != null ? lineLayer : "null")
                    + ", blocks=" + WorldOverlayRenderer.getBlockTargets().size()
                    + ", chunks=" + WorldOverlayRenderer.getChunkTargets().size()
                    + ", players=" + WorldOverlayRenderer.getPlayerTargets().size());
        }

        if (bufferSource == null) {
            if (!loggedBufferIntrospect) {
                loggedBufferIntrospect = true;
                logBufferIntrospection(mc, levelRenderer);
            }
            return;
        }

        if (quadLayer == null && lineLayer == null) {
            return;
        }

        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // 1) Chunk Finder
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

        // 2) Block ESP
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

        // 3) Tracers
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

    private static void logBufferIntrospection(Minecraft mc, Object levelRenderer) {
        System.out.println("[VangixTools] Buffer introspection start:");
        if (mc != null) {
            System.out.println("  mc methods: " + describeZeroArgMethods(mc.getClass()));
            System.out.println("  mc fields: " + describeFields(mc.getClass()));
            Object rb = invokeOrReadAny(
                    mc,
                    new String[]{"renderBuffers", "getRenderBuffers"},
                    new String[]{"renderBuffers"}
            );
            if (rb != null) {
                System.out.println("  renderBuffers (" + rb.getClass().getName() + ") methods: "
                        + describeZeroArgMethods(rb.getClass()));
                System.out.println("  renderBuffers fields: " + describeFields(rb.getClass()));
            }
        }
        if (levelRenderer != null) {
            System.out.println("  levelRenderer (" + levelRenderer.getClass().getName() + ") methods: "
                    + describeZeroArgMethods(levelRenderer.getClass()));
            System.out.println("  levelRenderer fields: " + describeFields(levelRenderer.getClass()));
        }
    }

    private static String describeZeroArgMethods(Class<?> cls) {
        StringJoiner sj = new StringJoiner(", ");
        for (Method m : cls.getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class) {
                sj.add(m.getReturnType().getSimpleName() + " " + m.getName() + "()");
            }
        }
        return sj.toString();
    }

    private static String describeFields(Class<?> cls) {
        StringJoiner sj = new StringJoiner(", ");
        for (Field f : cls.getDeclaredFields()) {
            sj.add(f.getType().getSimpleName() + " " + f.getName());
        }
        return sj.toString();
    }

    private static Vec3 extractCameraPos(Minecraft mc, Object[] args) {
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

    private static Object extractBufferSource(Minecraft mc, Object levelRenderer) {
        String[] bufferMethodNames = {
                "bufferSource", "getBufferSource",
                "outlineBufferSource", "getOutlineBufferSource",
                "multiBufferSource", "getMultiBufferSource",
                "buffers", "getBuffers",
                "consumers", "getConsumers"
        };
        String[] bufferFieldNames = {
                "bufferSource", "outlineBufferSource", "multiBufferSource", "buffers", "consumers"
        };

        Object fromLevel = invokeOrReadAny(levelRenderer, bufferMethodNames, bufferFieldNames);
        if (fromLevel != null) {
            Object nested = invokeOrReadAny(fromLevel, bufferMethodNames, bufferFieldNames);
            return nested != null ? nested : fromLevel;
        }

        if (mc != null) {
            Object renderBuffers = invokeOrReadAny(
                    mc,
                    new String[]{"renderBuffers", "getRenderBuffers"},
                    new String[]{"renderBuffers"}
            );
            if (renderBuffers != null) {
                Object bs = invokeOrReadAny(renderBuffers, bufferMethodNames, bufferFieldNames);
                if (bs != null) return bs;
            }

            Object directMc = invokeOrReadAny(mc, bufferMethodNames, bufferFieldNames);
            if (directMc != null) return directMc;
        }

        return null;
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
    }
}
