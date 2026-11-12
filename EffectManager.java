package com.artem.visuals.effects;

import com.artem.visuals.config.ArtemConfig;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

/**
 * Клиентские частицы. Не затрагивает серверную логику.
 */
public final class EffectManager {
    private static int counter = 0;

    private EffectManager() {}

    public static void registerEvents() {
        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            ArtemConfig c = ArtemConfig.get();
            if (!c.masterEnabled || !c.blockBreakEffects || state.isAir()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (c.adaptive && mc.getCurrentFps() < c.fpsThreshold()) return;
            Random r = world.random;
            int n = (int) Math.round(10 * c.densityMul());
            for (int i = 0; i < n; i++) {
                world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        pos.getX() + r.nextDouble(), pos.getY() + r.nextDouble(), pos.getZ() + r.nextDouble(),
                        (r.nextDouble() - 0.5) * 0.25, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.25);
            }
            for (int i = 0; i < Math.max(1, n / 4); i++) {
                world.addParticle(ParticleTypes.POOF,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        (r.nextDouble() - 0.5) * 0.1, 0.02, (r.nextDouble() - 0.5) * 0.1);
            }
        });
    }

    public static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null || mc.player == null || mc.isPaused()) return;
        ArtemConfig c = ArtemConfig.get();
        if (!c.masterEnabled) return;
        if (c.adaptive && mc.getCurrentFps() < c.fpsThreshold()) return;
        if (++counter % 4 != 0) return;

        Random rnd = w.random;
        BlockPos origin = mc.player.getBlockPos();
        int r = c.radiusBlocks();
        int attempts = Math.max(2, (int) Math.round(6 * c.densityMul()));
        BlockPos.Mutable m = new BlockPos.Mutable();
        boolean night = !w.isDay();

        for (int i = 0; i < attempts; i++) {
            m.set(origin.getX() + rnd.nextBetween(-r, r),
                  origin.getY() + rnd.nextBetween(-r / 2, r / 2),
                  origin.getZ() + rnd.nextBetween(-r, r));
            BlockState s = w.getBlockState(m);

            if (c.leafParticles && s.getBlock() instanceof LeavesBlock
                    && w.getBlockState(m.down()).isAir() && rnd.nextInt(10) == 0) {
                w.addParticle(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, s),
                        m.getX() + rnd.nextDouble(), m.getY() - 0.05, m.getZ() + rnd.nextDouble(),
                        (rnd.nextDouble() - 0.5) * 0.04, -0.02, (rnd.nextDouble() - 0.5) * 0.04);
            } else if (c.waterSparkles && !night) {
                FluidState f = s.getFluidState();
                if (f.isIn(FluidTags.WATER) && f.isStill() && w.getBlockState(m.up()).isAir()
                        && w.isSkyVisible(m.up()) && rnd.nextInt(6) == 0) {
                    w.addParticle(ParticleTypes.BUBBLE_POP,
                            m.getX() + rnd.nextDouble(), m.getY() + f.getHeight(), m.getZ() + rnd.nextDouble(),
                            0, 0.01, 0);
                }
            } else if (c.fireflies && night && s.isOf(Blocks.GRASS_BLOCK)
                    && w.getBlockState(m.up()).isAir() && rnd.nextInt(14) == 0) {
                w.addParticle(ParticleTypes.GLOW,
                        m.getX() + rnd.nextDouble(), m.getY() + 1.0 + rnd.nextDouble() * 1.5, m.getZ() + rnd.nextDouble(),
                        (rnd.nextDouble() - 0.5) * 0.02, 0.005, (rnd.nextDouble() - 0.5) * 0.02);
            }
        }

        if (c.precipitationEffects && w.isRaining()) {
            for (int i = 0; i < attempts; i++) {
                int x = origin.getX() + rnd.nextBetween(-r, r);
                int z = origin.getZ() + rnd.nextBetween(-r, r);
                if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;
                int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                BlockPos pos = new BlockPos(x, top, z);
                Biome.Precipitation p = w.getBiome(pos).value().getPrecipitation(pos);
                if (p == Biome.Precipitation.SNOW) {
                    w.addParticle(ParticleTypes.SNOWFLAKE,
                            x + rnd.nextDouble(), top + 1 + rnd.nextDouble() * 5, z + rnd.nextDouble(),
                            (rnd.nextDouble() - 0.5) * 0.05, -0.03, (rnd.nextDouble() - 0.5) * 0.05);
                } else if (p == Biome.Precipitation.RAIN) {
                    w.addParticle(ParticleTypes.RAIN, x + rnd.nextDouble(), top + 0.1, z + rnd.nextDouble(), 0, 0, 0);
                }
            }
        }
    }
}
