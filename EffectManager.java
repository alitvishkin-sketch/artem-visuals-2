package com.artem.visuals.effects;

import com.artem.visuals.config.ArtemConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 3D-эффекты мира в стиле PulseVisuals / FunTime: Jump Circle, Block Overlay и частицы окружения.
 */
public final class EffectManager {
    public static boolean jumpCircle = true;
    public static float jumpCircleRadius = 1.2f;
    public static int jumpCircleColor = 0xFFFFD166;

    public static boolean blockOverlay = true;
    public static float blockOverlayAlpha = 0.85f;
    public static int blockOverlayColor = 0xFFFFD166;

    private static final Random RNG = new Random();
    private static final List<JumpRing> RINGS = new ArrayList<>();
    private static boolean wasOnGround = true;

    private record JumpRing(double x, double y, double z, long createdMs, float maxRadius, int color) {}

    private EffectManager() {}

    public static void registerEvents() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(EffectManager::onWorldRender);

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            ArtemConfig c = ArtemConfig.get();
            if (!c.masterEnabled || !c.blockBreakEffects || world == null || !world.isClient) return;
            for (int i = 0; i < 8; i++) {
                double ox = pos.getX() + 0.2 + RNG.nextDouble() * 0.6;
                double oy = pos.getY() + 0.2 + RNG.nextDouble() * 0.6;
                double oz = pos.getZ() + 0.2 + RNG.nextDouble() * 0.6;
                world.addParticle(ParticleTypes.ENCHANT, ox, oy, oz,
                        (RNG.nextDouble() - 0.5) * 0.15, 0.08 + RNG.nextDouble() * 0.1, (RNG.nextDouble() - 0.5) * 0.15);
            }
        });
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) return;
        ArtemConfig c = ArtemConfig.get();

        // Отслеживание прыжка для Jump Circle
        boolean onGround = client.player.isOnGround();
        if (jumpCircle && wasOnGround && !onGround && client.player.getVelocity().y > 0.12) {
            RINGS.add(new JumpRing(
                    client.player.getX(),
                    client.player.getY() + 0.04,
                    client.player.getZ(),
                    System.currentTimeMillis(),
                    jumpCircleRadius,
                    jumpCircleColor
            ));
        }
        wasOnGround = onGround;

        // Анимация колец и звёздочек при прыжке
        long now = System.currentTimeMillis();
        Iterator<JumpRing> it = RINGS.iterator();
        while (it.hasNext()) {
            JumpRing ring = it.next();
            long age = now - ring.createdMs;
            if (age > 750L) {
                it.remove();
                continue;
            }
            float p = age / 750f;
            float inv = 1f - p;
            float ease = 1f - inv * inv * inv;
            double r = 0.2 + ease * ring.maxRadius;

            int points = 24;
            for (int i = 0; i < points; i++) {
                double ang = (Math.PI * 2.0 * i) / points + p * 1.5;
                double px = ring.x + Math.cos(ang) * r;
                double pz = ring.z + Math.sin(ang) * r;
                client.world.addParticle(ParticleTypes.ELECTRIC_SPARK, px, ring.y, pz, 0.0, 0.005, 0.0);
            }

            if (age % 60 < 50) {
                for (int s = 0; s < 8; s++) {
                    double ang = (Math.PI * 2.0 * s) / 8.0 + p * 2.2;
                    double sx = ring.x + Math.cos(ang) * r;
                    double sz = ring.z + Math.sin(ang) * r;
                    client.world.addParticle(ParticleTypes.END_ROD, sx, ring.y + 0.03, sz, 0.0, 0.012, 0.0);
                }
            }
        }

        if (!c.masterEnabled) return;
        if (c.adaptive && client.getCurrentFps() < c.fpsThreshold()) return;

        int count = (c.density + 1) * 2;
        int rad = (c.radius + 1) * 6;
        BlockPos center = client.player.getBlockPos();

        for (int i = 0; i < count; i++) {
            int dx = RNG.nextInt(rad * 2 + 1) - rad;
            int dy = RNG.nextInt(10) - 3;
            int dz = RNG.nextInt(rad * 2 + 1) - rad;
            BlockPos pos = center.add(dx, dy, dz);
            BlockState state = client.world.getBlockState(pos);

            if (c.leafParticles && state.getBlock() instanceof LeavesBlock && client.world.isAir(pos.down())) {
                client.world.addParticle(ParticleTypes.CHERRY_LEAVES,
                        pos.getX() + RNG.nextDouble(), pos.getY() - 0.05, pos.getZ() + RNG.nextDouble(),
                        0.0, -0.03, 0.0);
            }

            if (c.waterSparkles && state.getFluidState().isIn(FluidTags.WATER) && client.world.isAir(pos.up())) {
                if (RNG.nextFloat() < 0.35f) {
                    client.world.addParticle(ParticleTypes.GLOW,
                            pos.getX() + RNG.nextDouble(), pos.getY() + 0.95, pos.getZ() + RNG.nextDouble(),
                            0.0, 0.01, 0.0);
                }
            }

            if (c.fireflies && state.isAir() && client.world.getLightLevel(pos) <= 7) {
                if (RNG.nextFloat() < 0.25f) {
                    client.world.addParticle(ParticleTypes.END_ROD,
                            pos.getX() + RNG.nextDouble(), pos.getY() + RNG.nextDouble(), pos.getZ() + RNG.nextDouble(),
                            (RNG.nextDouble() - 0.5) * 0.02, 0.01, (RNG.nextDouble() - 0.5) * 0.02);
                }
            }
        }
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (!blockOverlay) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.crosshairTarget == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        Box box = shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos);
        Vec3d cam = context.camera().getPos();
        box = box.expand(0.003).offset(-cam.x, -cam.y, -cam.z);

        float r = ((blockOverlayColor >> 16) & 0xFF) / 255f;
        float g = ((blockOverlayColor >> 8) & 0xFF) / 255f;
        float b = (blockOverlayColor & 0xFF) / 255f;

        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        WorldRenderer.drawBox(matrices, lines, box, r, g, b, blockOverlayAlpha);
    }
}
