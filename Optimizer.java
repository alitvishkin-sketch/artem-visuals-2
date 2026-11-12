package com.artem.visuals.optimize;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.ParticlesMode;

/**
 * Безопасный клиентский оптимизатор FPS и яркости (без вмешательства в пакеты).
 */
public final class Optimizer {
    private static Double savedGamma = null;
    private static Integer savedViewDistance = null;
    private static Integer savedSimulationDistance = null;
    private static CloudRenderMode savedClouds = null;
    private static ParticlesMode savedParticles = null;
    private static Boolean savedEntityShadows = null;

    private Optimizer() {}

    public static void applyLighting(MinecraftClient mc) {
        if (mc == null || mc.options == null) return;
        ArtemConfig c = ArtemConfig.get();
        try {
            if (c.softLighting) {
                if (savedGamma == null) {
                    savedGamma = mc.options.getGamma().getValue();
                }
                mc.options.getGamma().setValue(1.0);
            } else if (savedGamma != null) {
                mc.options.getGamma().setValue(savedGamma);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void applyPerformancePreset(MinecraftClient mc) {
        if (mc == null || mc.options == null) return;
        try {
            if (savedViewDistance == null) {
                savedViewDistance = mc.options.getViewDistance().getValue();
                savedSimulationDistance = mc.options.getSimulationDistance().getValue();
                savedClouds = mc.options.getCloudRenderMode().getValue();
                savedParticles = mc.options.getParticles().getValue();
                savedEntityShadows = mc.options.getEntityShadows().getValue();
            }
            mc.options.getViewDistance().setValue(Math.min(8, savedViewDistance));
            mc.options.getSimulationDistance().setValue(Math.min(6, savedSimulationDistance));
            mc.options.getCloudRenderMode().setValue(CloudRenderMode.FAST);
            mc.options.getParticles().setValue(ParticlesMode.DECREASED);
            mc.options.getEntityShadows().setValue(false);
        } catch (Throwable ignored) {
        }
    }

    public static boolean hasBackup() {
        return savedViewDistance != null;
    }

    public static void restoreSettings(MinecraftClient mc) {
        if (mc == null || mc.options == null || !hasBackup()) return;
        try {
            mc.options.getViewDistance().setValue(savedViewDistance);
            mc.options.getSimulationDistance().setValue(savedSimulationDistance);
            mc.options.getCloudRenderMode().setValue(savedClouds);
            mc.options.getParticles().setValue(savedParticles);
            mc.options.getEntityShadows().setValue(savedEntityShadows);
            savedViewDistance = null;
        } catch (Throwable ignored) {
        }
    }
}
