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
 * Настоящий 3D-рендер мира в стиле PulseVisuals / FunTime:
 * - Многослойный светящийся 3D Jump Circle со звёздами прямо на земле
 * - Двойной неоновый 3D Block Overlay по точной форме блока
 * - Частицы окружения
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
                    client.player.getY() + 0.02,
                    client.player.getZ(),
                    System.currentTimeMillis(),
                    jumpCircleRadius,
                    jumpCircleColor
            ));
        }
        wasOnGround = onGround;

        // Удаление старых колец и лёгкие звёздные искры
        long now = System.currentTimeMillis();
        Iterator<JumpRing> it = RINGS.iterator();
        while (it.hasNext()) {
            JumpRing ring = it.next();
            long age = now - ring.createdMs;
            if (age > 850L) {
                it.remove();
                continue;
            }
            if (age % 90 < 45) {
                float p = age / 850f;
                float inv = 1f - p;
                float ease = 1f - inv * inv * inv;
                double r = 0.25 + ease * ring.maxRadius;
                for (int s = 0; s < 4; s++) {
                    double ang = (Math.PI * 2.0 * s) / 4.0 + p * 2.4;
                    double sx = ring.x + Math.cos(ang) * r;
                    double sz = ring.z + Math.sin(ang) * r;
                    client.world.addParticle(ParticleTypes.END_ROD, sx, ring.y + 0.04, sz, 0.0, 0.01, 0.0);
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d cam = context.camera().getPos();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        // 1. Рендер 3D Jump Circle на земле со светящимися звёздами
        if (jumpCircle && !RINGS.isEmpty()) {
            long now = System.currentTimeMillis();
            for (JumpRing ring : RINGS) {
                long age = now - ring.createdMs;
                if (age > 850L) continue;

                float p = age / 850f;
                float inv = 1f - p;
                float ease = 1f - inv * inv * inv;
                float fade = Math.min(1f, inv * 1.4f);
                double baseR = 0.22 + ease * ring.maxRadius;

                float r = ((ring.color >> 16) & 0xFF) / 255f;
                float g = ((ring.color >> 8) & 0xFF) / 255f;
                float b = (ring.color & 0xFF) / 255f;

                double cx = ring.x - cam.x;
                double cy = ring.y - cam.y;
                double cz = ring.z - cam.z;

                // 3 концентрических слоя для мягкого неонового свечения кольца
                draw3DRing(matrices, lines, cx, cy, cz, baseR - 0.03, r, g, b, fade * 0.45f);
                draw3DRing(matrices, lines, cx, cy, cz, baseR, r, g, b, fade * 0.95f);
                draw3DRing(matrices, lines, cx, cy, cz, baseR + 0.035, r, g, b, fade * 0.40f);

                // 8 вращающихся звёзд по окружности кольца
                for (int s = 0; s < 8; s++) {
                    double ang = (Math.PI * 2.0 * s) / 8.0 + p * 2.2;
                    double sx = cx + Math.cos(ang) * baseR;
                    double sz = cz + Math.sin(ang) * baseR;
                    draw3DStar(matrices, lines, sx, cy + 0.015, sz, 0.065, r, g, b, fade);
                }
            }
        }

        // 2. Рендер 3D Block Overlay по точной форме блока
        if (blockOverlay && mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir()) {
                VoxelShape shape = state.getOutlineShape(mc.world, pos);
                float r = ((blockOverlayColor >> 16) & 0xFF) / 255f;
                float g = ((blockOverlayColor >> 8) & 0xFF) / 255f;
                float b = (blockOverlayColor & 0xFF) / 255f;

                if (shape.isEmpty()) {
                    Box box = new Box(pos).expand(0.003).offset(-cam.x, -cam.y, -cam.z);
                    WorldRenderer.drawBox(matrices, lines, box, r, g, b, blockOverlayAlpha);
                    WorldRenderer.drawBox(matrices, lines, box.expand(-0.005), r, g, b, blockOverlayAlpha * 0.45f);
                } else {
                    shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                        Box box = new Box(
                                pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ,
                                pos.getX() + maxX, pos.getY() + maxY, pos.getZ() + maxZ
                        ).expand(0.003).offset(-cam.x, -cam.y, -cam.z);
                        WorldRenderer.drawBox(matrices, lines, box, r, g, b, blockOverlayAlpha);
                        WorldRenderer.drawBox(matrices, lines, box.expand(-0.005), r, g, b, blockOverlayAlpha * 0.45f);
                    });
                }
            }
        }
    }

    private static void draw3DRing(MatrixStack matrices, VertexConsumer lines,
                                   double cx, double cy, double cz, double radius,
                                   float r, float g, float b, float alpha) {
        if (radius <= 0.02 || alpha <= 0.02f) return;
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a1 = (Math.PI * 2.0 * i) / segments;
            double a2 = (Math.PI * 2.0 * (i + 1)) / segments;
            double x1 = cx + Math.cos(a1) * radius;
            double z1 = cz + Math.sin(a1) * radius;
            double x2 = cx + Math.cos(a2) * radius;
            double z2 = cz + Math.sin(a2) * radius;
            Box seg = new Box(
                    Math.min(x1, x2) - 0.004, cy - 0.004, Math.min(z1, z2) - 0.004,
                    Math.max(x1, x2) + 0.004, cy + 0.004, Math.max(z1, z2) + 0.004
            );
            WorldRenderer.drawBox(matrices, lines, seg, r, g, b, alpha);
        }
    }

    private static void draw3DStar(MatrixStack matrices, VertexConsumer lines,
                                   double sx, double sy, double sz, double size,
                                   float r, float g, float b, float alpha) {
        if (alpha <= 0.02f) return;
        Box core = new Box(sx - size * 0.35, sy - size * 0.35, sz - size * 0.35,
                sx + size * 0.35, sy + size * 0.35, sz + size * 0.35);
        Box beamX = new Box(sx - size, sy - 0.005, sz - 0.005, sx + size, sy + 0.005, sz + 0.005);
        Box beamZ = new Box(sx - 0.005, sy - 0.005, sz - size, sx + 0.005, sy + 0.005, sz + size);
        WorldRenderer.drawBox(matrices, lines, core, 1f, 1f, 1f, alpha);
        WorldRenderer.drawBox(matrices, lines, beamX, r, g, b, alpha * 0.9f);
        WorldRenderer.drawBox(matrices, lines, beamZ, r, g, b, alpha * 0.9f);
    }
}
