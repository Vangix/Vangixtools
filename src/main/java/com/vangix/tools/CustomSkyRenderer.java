package com.vangix.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.LineGizmo;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Random;

public class CustomSkyRenderer {

    private static final int STAR_COUNT = 140;
    private static final double SKY_RADIUS = 110.0;

    public static void renderCustomSky(Minecraft mc, Object gizmoCollector, Vec3 camPos, float partialTick) {
        if (!FeatureState.customSky || mc == null || mc.level == null || gizmoCollector == null) {
            return;
        }

        FeatureState.SkyPreset preset = FeatureState.getSkyPreset();

        int horizonArgb = withAlpha(preset.horizonArgb, 0.35f);
        int accentArgb = withAlpha(preset.accentArgb, 0.55f);

        int segments = 36;
        double yHorizon = camPos.y + 18.0;
        for (int i = 0; i < segments; i++) {
            double a0 = (2.0 * Math.PI * i) / segments;
            double a1 = (2.0 * Math.PI * (i + 1)) / segments;

            Vec3 p0 = new Vec3(
                    camPos.x + Math.cos(a0) * SKY_RADIUS,
                    yHorizon,
                    camPos.z + Math.sin(a0) * SKY_RADIUS
            );
            Vec3 p1 = new Vec3(
                    camPos.x + Math.cos(a1) * SKY_RADIUS,
                    yHorizon,
                    camPos.z + Math.sin(a1) * SKY_RADIUS
            );
            addGizmoSafe(gizmoCollector, new LineGizmo(p0, p1, horizonArgb, 1.5f));
        }

        if (preset.stars) {
            long seed = mc.level.dimension().hashCode() ^ preset.ordinal() * 0x9E3779B97F4A7C15L;
            Random rng = new Random(seed);
            for (int s = 0; s < STAR_COUNT; s++) {
                double theta = rng.nextDouble() * Math.PI * 2.0;
                double phi = rng.nextDouble() * (Math.PI * 0.44);
                double r = SKY_RADIUS * (0.75 + rng.nextDouble() * 0.25);

                double sx = camPos.x + Math.sin(phi) * Math.cos(theta) * r;
                double sy = camPos.y + Math.cos(phi) * r;
                double sz = camPos.z + Math.sin(phi) * Math.sin(theta) * r;

                Vec3 starA = new Vec3(sx - 0.35, sy, sz);
                Vec3 starB = new Vec3(sx + 0.35, sy, sz);
                addGizmoSafe(gizmoCollector, new LineGizmo(starA, starB, accentArgb, 1.6f));
            }
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int ia = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        return (ia << 24) | (argb & 0x00FFFFFF);
    }

    private static void addGizmoSafe(Object collector, Gizmo gizmo) {
        if (collector == null || gizmo == null) return;
        try {
            Method addMethod = collector.getClass().getMethod("add", Gizmo.class);
            addMethod.invoke(collector, gizmo);
        } catch (Throwable ignored) {
        }
    }
}
