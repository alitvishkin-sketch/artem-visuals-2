package com.artem.visuals.optimize;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.ParticlesMode;

public final class Optimizer {
    private Optimizer() {}

    public static void applyPerformancePreset(MinecraftClient mc) {
        GameOptions o = mc.options;
        ArtemConfig c = ArtemConfig.get();
        if (c.backup == null) {
            ArtemConfig.Backup b = new ArtemConfig.Backup();
            b.particles = o.getParticles().getValue().ordinal();
            b.entityDistance = o.getEntityDistanceScaling().getValue();
            b.biomeBlend = o.getBiomeBlendRadius().getValue();
            b.mipmaps = o.getMipmapLevels().getValue();
            b.clouds = o.getCloudRenderMode().getValue().ordinal();
            b.shadows = o.getEntityShadows().getValue();
            c.backup = b;
        }
        o.getParticles().setValue(ParticlesMode.DECREASED);
        o.getEntityDistanceScaling().setValue(0.75);
        o.getBiomeBlendRadius().setValue(1);
        o.getMipmapLevels().setValue(2);
        o.getCloudRenderMode().setValue(CloudRenderMode.FAST);
        o.getEntityShadows().setValue(false);
        o.write();
        c.save();
    }

    public static boolean hasBackup() {
        return ArtemConfig.get().backup != null;
    }

    public static void restoreSettings(MinecraftClient mc) {
        ArtemConfig c = ArtemConfig.get();
        ArtemConfig.Backup b = c.backup;
        if (b == null) return;
        GameOptions o = mc.options;
        o.getParticles().setValue(ParticlesMode.values()[Math.min(b.particles, ParticlesMode.values().length - 1)]);
        o.getEntityDistanceScaling().setValue(b.entityDistance);
        o.getBiomeBlendRadius().setValue(b.biomeBlend);
        o.getMipmapLevels().setValue(b.mipmaps);
        o.getCloudRenderMode().setValue(CloudRenderMode.values()[Math.min(b.clouds, CloudRenderMode.values().length - 1)]);
        o.getEntityShadows().setValue(b.shadows);
        o.write();
        c.backup = null;
        c.save();
    }

    public static void applyLighting(MinecraftClient mc) {
        if (mc == null || mc.options == null) return;
        ArtemConfig c = ArtemConfig.get();
        GameOptions o = mc.options;
        if (c.softLighting) {
            if (c.originalGamma < 0) c.originalGamma = o.getGamma().getValue();
            o.getGamma().setValue(c.gammaLevel());
        } else if (c.originalGamma >= 0) {
            o.getGamma().setValue(c.originalGamma);
            c.originalGamma = -1;
        }
        if (o.getAo().getValue() != c.smoothLighting) {
            o.getAo().setValue(c.smoothLighting);
        }
        o.write();
        c.save();
    }
}
