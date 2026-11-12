package com.artem.visuals.effects;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.gui.NeonButton;
import com.artem.visuals.hud.HudManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class EffectManager {
    private static final Random RNG = new Random();
    private static final List<JumpRing> RINGS = new ArrayList<>();
    private static final LinkedList<Vec3d> TRAIL_POINTS = new LinkedList<>();
    private static final Map<Integer, LinkedList<Vec3d>> PROJECTILE_TRAILS = new HashMap<>();
    private static final List<DamagePopup> DAMAGE_POPUPS = new ArrayList<>();
    private static final Map<Integer, Float> LAST_HEALTH_MAP = new HashMap<>();
    private static boolean wasOnGround = true;
    private static boolean wasDead = false;
    private static boolean wasAhKeyDown = false;
    private static long lastRelistReminderMs = 0L;
    private static String helperAlertText = null;
    private static int helperAlertColor = 0xFFFFB703;

    private record JumpRing(double x, double y, double z, long createdMs, float maxRadius, int color) {}
    private record DamagePopup(double x, double y, double z, float amount, long createdMs) {}

    private EffectManager() {}

    public static void registerEvents() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(EffectManager::onWorldRender);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ArtemConfig c = ArtemConfig.get();
            if (world != null && world.isClient && entity != null) {
                if (c.hitParticles) {
                    int count = Math.max(4, c.hitParticlesCount);
                    for (int i = 0; i < count; i++) {
                        double ox = entity.getX() + (RNG.nextDouble() - 0.5) * 0.9;
                        double oy = entity.getY() + 0.4 + RNG.nextDouble() * entity.getHeight() * 0.7;
                        double oz = entity.getZ() + (RNG.nextDouble() - 0.5) * 0.9;
                        world.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT,
                                ox, oy, oz,
                                (RNG.nextDouble() - 0.5) * 0.22,
                                0.06 + RNG.nextDouble() * 0.14,
                                (RNG.nextDouble() - 0.5) * 0.22);
                    }
                }
                if (c.critSparks) {
                    for (int i = 0; i < 8; i++) {
                        world.addParticle(ParticleTypes.CRIT,
                                entity.getX() + (RNG.nextDouble() - 0.5) * 0.6,
                                entity.getY() + entity.getHeight() * 0.55,
                                entity.getZ() + (RNG.nextDouble() - 0.5) * 0.6,
                                (RNG.nextDouble() - 0.5) * 0.35,
                                0.12 + RNG.nextDouble() * 0.2,
                                (RNG.nextDouble() - 0.5) * 0.35);
                    }
                }
            }
            return ActionResult.PASS;
        });

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

    public static void triggerAhSearchForHand(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) return;
        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty()) {
            client.player.sendMessage(Text.literal("§d[Artem] §7Возьмите предмет в руку для поиска на аукционе."), false);
            return;
        }
        String cleanName = held.getName().getString().trim();
        if (!cleanName.isEmpty()) {
            client.getNetworkHandler().sendChatCommand("ah search " + cleanName);
        }
    }

    public static void tick(MinecraftClient client) {
        HudManager.tick(client);

        if (client == null || client.world == null || client.player == null) {
            RINGS.clear();
            TRAIL_POINTS.clear();
            PROJECTILE_TRAILS.clear();
            DAMAGE_POPUPS.clear();
            LAST_HEALTH_MAP.clear();
            helperAlertText = null;
            return;
        }

        ArtemConfig c = ArtemConfig.get();
        long now = System.currentTimeMillis();

        // --- ХЕЛПЕРЫ ---
        // 1. Авто-спринт
        if (c.autoSprint && client.options != null && client.options.forwardKey.isPressed()
                && !client.player.isSneaking() && !client.player.horizontalCollision
                && client.player.getHungerManager().getFoodLevel() > 6) {
            client.player.setSprinting(true);
        }

        // 2. Координаты смерти и авто-точка смерти
        boolean dead = client.player.isDead() || client.player.getHealth() <= 0.0f;
        if (dead && !wasDead) {
            BlockPos dp = client.player.getBlockPos();
            if (c.deathCoords) {
                client.player.sendMessage(Text.literal(
                        String.format("§d[Artem] §fКоординаты смерти: §b%d %d %d", dp.getX(), dp.getY(), dp.getZ())), false);
            }
            if (c.autoDeathWaypoint) {
                c.waypoints.removeIf(w -> "Смерть".equalsIgnoreCase(w.name));
                c.waypoints.add(new ArtemConfig.Waypoint("Смерть", dp.getX(), dp.getY(), dp.getZ(), 0xFFFF595E));
                c.save();
            }
        }
        wasDead = dead;

        // 3. Поиск на /ah по предмету в руке (клавиша H)
        if (c.ahSearchHandItem && client.currentScreen == null && client.getWindow() != null && c.ahSearchKeyCode > 0) {
            boolean ahDown = GLFW.glfwGetKey(client.getWindow().getHandle(), c.ahSearchKeyCode) == GLFW.GLFW_PRESS;
            if (ahDown && !wasAhKeyDown) {
                triggerAhSearchForHand(client);
            }
            wasAhKeyDown = ahDown;
        } else {
            wasAhKeyDown = false;
        }

        // 4. Напоминание о перевыставлении лотов на аукционе
        if (c.ahAutoRelist && c.ahRelistInterval >= 10) {
            long intervalMs = c.ahRelistInterval * 1000L;
            if (lastRelistReminderMs == 0L) {
                lastRelistReminderMs = now;
            } else if (now - lastRelistReminderMs >= intervalMs) {
                lastRelistReminderMs = now;
                client.player.sendMessage(Text.literal("§d[Аукцион] §fПора обновить лоты на аукционе (§e/ah§f)"), false);
            }
        } else {
            lastRelistReminderMs = 0L;
        }

        // 5. Проверка низкой прочности брони и окончания зелий
        helperAlertText = null;
        if (c.lowArmorAlert) {
            for (int s = 3; s >= 0; s--) {
                ItemStack armor = client.player.getInventory().getArmorStack(s);
                if (!armor.isEmpty() && armor.isDamageable() && armor.getMaxDamage() > 0) {
                    int pct = Math.round(((armor.getMaxDamage() - armor.getDamage()) / (float) armor.getMaxDamage()) * 100f);
                    if (pct <= c.lowArmorThreshold) {
                        helperAlertText = "⚠ Низкая прочность: " + armor.getName().getString() + " (" + pct + "%)";
                        helperAlertColor = 0xFFFF595E;
                        break;
                    }
                }
            }
        }
        if (helperAlertText == null && c.potionExpiryAlert) {
            for (StatusEffectInstance inst : client.player.getStatusEffects()) {
                if (!inst.isInfinite() && inst.getDuration() > 0 && inst.getDuration() <= 100 && inst.getEffectType().value().isBeneficial()) {
                    int sec = Math.max(1, (inst.getDuration() + 19) / 20);
                    helperAlertText = "⏱ Эффект заканчивается: " + inst.getEffectType().value().getName().getString() + " (" + sec + "с)";
                    helperAlertColor = 0xFFFFB703;
                    break;
                }
            }
        }

        // --- ВИЗУАЛЫ ---
        if (c.renderTweaks && client.options != null) {
            client.options.getBobView().setValue(false);
        }

        if (c.worldCustomizer && c.worldTimeMode > 0) {
            long targetTime = switch (c.worldTimeMode) {
                case 1 -> 6000L;
                case 2 -> 12700L;
                default -> 18000L;
            };
            client.world.setTimeOfDay(targetTime);
        }

        if (c.projectileTrails) {
            Set<Integer> aliveIds = new HashSet<>();
            for (Entity e : client.world.getEntities()) {
                if (e instanceof ProjectileEntity && !e.isOnGround() && e.squaredDistanceTo(client.player) < 96 * 96) {
                    aliveIds.add(e.getId());
                    LinkedList<Vec3d> pts = PROJECTILE_TRAILS.computeIfAbsent(e.getId(), k -> new LinkedList<>());
                    pts.addLast(e.getPos());
                    while (pts.size() > 18) pts.removeFirst();
                }
            }
            PROJECTILE_TRAILS.keySet().retainAll(aliveIds);
        } else {
            PROJECTILE_TRAILS.clear();
        }

        if (c.damageIndicator) {
            for (Entity e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || e == client.player) continue;
                if (le.squaredDistanceTo(client.player) > 40 * 40) continue;
                float hp = le.getHealth();
                Float prev = LAST_HEALTH_MAP.put(le.getId(), hp);
                if (prev != null && hp < prev - 0.1f) {
                    DAMAGE_POPUPS.add(new DamagePopup(
                            le.getX() + (RNG.nextDouble() - 0.5) * 0.4,
                            le.getEyeY() + 0.25,
                            le.getZ() + (RNG.nextDouble() - 0.5) * 0.4,
                            prev - hp,
                            now
                    ));
                }
            }
            if (LAST_HEALTH_MAP.size() > 256) LAST_HEALTH_MAP.clear();
        } else {
            DAMAGE_POPUPS.clear();
            LAST_HEALTH_MAP.clear();
        }
        DAMAGE_POPUPS.removeIf(p -> now - p.createdMs > 950L);

        if (c.trails) {
            Vec3d pos = new Vec3d(client.player.getX(), client.player.getY() + 0.15, client.player.getZ());
            if (TRAIL_POINTS.isEmpty() || TRAIL_POINTS.getLast().squaredDistanceTo(pos) > 0.004) {
                TRAIL_POINTS.addLast(pos);
            }
            while (TRAIL_POINTS.size() > Math.max(6, c.trailLength)) TRAIL_POINTS.removeFirst();
        } else {
            TRAIL_POINTS.clear();
        }

        boolean onGround = client.player.isOnGround();
        if (c.jumpCircle && wasOnGround && !onGround && client.player.getVelocity().y > 0.12) {
            RINGS.add(new JumpRing(
                    client.player.getX(),
                    client.player.getY() + 0.02,
                    client.player.getZ(),
                    now,
                    c.jumpCircleRadius,
                    NeonButton.getDynamicColor(c.jumpCircleColor, 0)
            ));
        }
        wasOnGround = onGround;
        RINGS.removeIf(ring -> now - ring.createdMs > 850L);

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

    public static void renderScreenOverlay(DrawContext ctx, MinecraftClient mc) {
        if (mc == null || mc.world == null || mc.player == null || mc.gameRenderer == null) return;
        ArtemConfig c = ArtemConfig.get();

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        if (c.handGlow && mc.options.getPerspective().isFirstPerson()) {
            int rgb = NeonButton.getHudAccent(0) & 0x00FFFFFF;
            if (!mc.player.getOffHandStack().isEmpty()) {
                drawSoftHandAura(ctx, 56, sh - 34, 34, rgb);
            }
            if (!mc.player.getMainHandStack().isEmpty()) {
                drawSoftHandAura(ctx, sw - 46, sh - 32, 36, rgb);
            }
        }

        if (c.screenVignette) {
            int vig = 0x2205050A;
            ctx.fillGradient(0, 0, sw, 16, vig, 0x00000000);
            ctx.fillGradient(0, sh - 16, sw, sh, 0x00000000, vig);
        }

        if (c.worldCustomizer && c.colorCorrection) {
            ctx.fill(0, 0, sw, sh, (NeonButton.ACCENT & 0x00FFFFFF) | 0x16000000);
        }

        // Баннер предупреждения хелпера (броня / зелья)
        if (helperAlertText != null) {
            int aw = Math.round(mc.textRenderer.getWidth(helperAlertText) * 0.85f) + 12;
            int ax = (sw - aw) / 2;
            int ay = sh - 54;
            NeonButton.fillSmoothRounded(ctx, ax, ay, aw, 13, 4, NeonButton.HUD_BG_TOP);
            NeonButton.drawSmoothRoundedOutline(ctx, ax, ay, aw, 13, 4, (helperAlertColor & 0x00FFFFFF) | 0x88000000);
            NeonButton.drawSmallText(ctx, mc.textRenderer, helperAlertText, ax + 6, ay + 3, helperAlertColor, 0.85f);
        }

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        float yaw = cam.getYaw();
        float pitch = cam.getPitch();
        double fov = mc.options.getFov().getValue();

        // Отображение Точек (Waypoints) на экране
        if (c.showWaypoints && !c.waypoints.isEmpty()) {
            for (ArtemConfig.Waypoint wp : c.waypoints) {
                if (wp == null || !wp.enabled) continue;
                double[] scr = projectToScreen(wp.x + 0.5, wp.y + 1.2, wp.z + 0.5, camPos, yaw, pitch, fov, sw, sh);
                if (scr == null) continue;
                double dist = Math.sqrt(mc.player.squaredDistanceTo(wp.x + 0.5, wp.y, wp.z + 0.5));
                String label = wp.name + " [" + Math.round(dist) + "m]";
                int tw = Math.round(mc.textRenderer.getWidth(label) * 0.80f) + 14;
                int px = (int) Math.round(scr[0] - tw / 2.0);
                int py = (int) Math.round(scr[1] - 12);
                int col = 0xFF000000 | (wp.color & 0x00FFFFFF);

                NeonButton.fillSmoothRounded(ctx, px, py, tw, 12, 3, NeonButton.HUD_BG_TOP);
                NeonButton.drawSmoothRoundedOutline(ctx, px, py, tw, 12, 3, (col & 0x00FFFFFF) | 0x77000000);
                NeonButton.drawCircle(ctx, px + 6, py + 6, 2, col);
                NeonButton.drawSmallText(ctx, mc.textRenderer, label, px + 11, py + 3, 0xFFF2F2FA, 0.80f);
            }
        }

        if (!c.nameTags && !c.damageIndicator && !(c.itemEsp && c.itemEspLabels)) return;

        if (c.nameTags || (c.itemEsp && c.itemEspLabels)) {
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                double distSq = e.squaredDistanceTo(mc.player);
                if (distSq > 64 * 64) continue;

                if (c.nameTags && e instanceof PlayerEntity target) {
                    double[] screen = projectToScreen(target.getX(), target.getEyeY() + 0.48, target.getZ(),
                            camPos, yaw, pitch, fov, sw, sh);
                    if (screen == null) continue;

                    String rawName = target.getName().getString();
                    boolean friend = c.highlightFriends && c.isFriend(rawName);
                    String name = (friend ? "★ " : "") + rawName;
                    String hpTxt = c.nameTagsHealth ? String.format(" %.1f❤", target.getHealth()) : "";
                    String distTxt = c.nameTagsDistance ? String.format(" [%dm]", Math.round(Math.sqrt(distSq))) : "";
                    String full = name + hpTxt + distTxt;

                    int tw = mc.textRenderer.getWidth(full) + 8;
                    int th = 12;
                    int px = (int) Math.round(screen[0] - tw / 2.0);
                    int py = (int) Math.round(screen[1] - th);

                    NeonButton.fillSmoothRounded(ctx, px, py, tw, th, 3, NeonButton.HUD_BG_TOP);
                    NeonButton.drawSmoothRoundedOutline(ctx, px, py, tw, th, 3,
                            friend ? ((c.friendColor & 0x00FFFFFF) | 0x88000000) : 0x26FFFFFF);

                    int tx = px + 4, ty = py + 2;
                    ctx.drawText(mc.textRenderer, name, tx, ty, friend ? c.friendColor : 0xFFF4F4FC, false);
                    tx += mc.textRenderer.getWidth(name);
                    if (!hpTxt.isEmpty()) {
                        ctx.drawText(mc.textRenderer, hpTxt, tx, ty, 0xFFFF5E7E, false);
                        tx += mc.textRenderer.getWidth(hpTxt);
                    }
                    if (!distTxt.isEmpty()) {
                        ctx.drawText(mc.textRenderer, distTxt, tx, ty, 0xFF9696B0, false);
                    }
                } else if (c.itemEsp && c.itemEspLabels && e instanceof ItemEntity item && distSq <= 28 * 28) {
                    double[] screen = projectToScreen(item.getX(), item.getY() + 0.45, item.getZ(),
                            camPos, yaw, pitch, fov, sw, sh);
                    if (screen == null) continue;
                    String label = item.getStack().getName().getString();
                    if (item.getStack().getCount() > 1) label += " x" + item.getStack().getCount();
                    int tw = mc.textRenderer.getWidth(label) + 6;
                    int px = (int) Math.round(screen[0] - tw / 2.0);
                    int py = (int) Math.round(screen[1] - 11);
                    NeonButton.fillSmoothRounded(ctx, px, py, tw, 11, 3, NeonButton.HUD_BG_TOP);
                    ctx.drawText(mc.textRenderer, label, px + 3, py + 2, NeonButton.getDynamicColor(c.itemEspColor, 0), false);
                }
            }
        }

        if (c.damageIndicator && !DAMAGE_POPUPS.isEmpty()) {
            long now = System.currentTimeMillis();
            for (DamagePopup pop : DAMAGE_POPUPS) {
                long age = now - pop.createdMs;
                if (age > 950L) continue;
                float p = age / 950f;
                double[] screen = projectToScreen(pop.x, pop.y + p * 0.75, pop.z, camPos, yaw, pitch, fov, sw, sh);
                if (screen == null) continue;
                int alpha = Math.max(25, Math.round((1f - p) * 255f));
                ctx.drawText(mc.textRenderer, String.format("-%.1f", pop.amount),
                        (int) Math.round(screen[0]) - 8, (int) Math.round(screen[1]) - 6, (alpha << 24) | 0xFF4D6D, true);
            }
        }
    }

    private static void drawSoftHandAura(DrawContext ctx, int cx, int cy, int maxR, int rgb) {
        for (int step = 5; step >= 1; step--) {
            int r = (maxR * step) / 5;
            int alpha = Math.max(2, 12 - step * 2);
            NeonButton.drawCircle(ctx, cx, cy, r, (alpha << 24) | rgb);
        }
    }

    private static double[] projectToScreen(double wx, double wy, double wz,
                                            Vec3d camPos, float yaw, float pitch,
                                            double fovDeg, int sw, int sh) {
        double dx = wx - camPos.x, dy = wy - camPos.y, dz = wz - camPos.z;
        double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(pitch);
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad), fy = -Math.sin(pitchRad), fz = Math.cos(yawRad) * Math.cos(pitchRad);
        double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double camX = dx * rx + dz * rz, camY = dx * ux + dy * uy + dz * uz, camZ = dx * fx + dy * fy + dz * fz;
        if (camZ <= 0.08) return null;
        double tanHalfFov = Math.tan(Math.toRadians(fovDeg) * 0.5);
        double aspect = (double) sw / Math.max(1, sh);
        double sx = (camX / (camZ * tanHalfFov * aspect) + 1.0) * 0.5 * sw;
        double sy = (1.0 - camY / (camZ * tanHalfFov)) * 0.5 * sh;
        return (sx < -60 || sx > sw + 60 || sy < -60 || sy > sh + 60) ? null : new double[]{sx, sy};
    }

    private static void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        ArtemConfig c = ArtemConfig.get();
        Vec3d cam = context.camera().getPos();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        long now = System.currentTimeMillis();

        double yawRad = Math.toRadians(context.camera().getYaw());
        double pitchRad = Math.toRadians(context.camera().getPitch());
        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad) * 0.6;
        double lookY = -Math.sin(pitchRad) * 0.6;
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad) * 0.6;

        if (c.esp || c.playerHighlight || c.tracers || c.chams || c.itemEsp || c.mobEsp || c.entityHighlight) {
            float pulse = c.espPulse ? (0.75f + 0.25f * (float) Math.sin(now / 240.0)) : 1.0f;
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player || e.squaredDistanceTo(mc.player) > 72 * 72) continue;
                Box box = e.getBoundingBox().offset(-cam.x, -cam.y, -cam.z);
                if (e instanceof PlayerEntity pe) {
                    boolean isFr = c.highlightFriends && c.isFriend(pe.getName().getString());
                    int pCol = isFr ? c.friendColor : NeonButton.getDynamicColor(c.espColor, e.getId() * 150);
                    if (c.esp) drawStyledEsp(matrices, lines, box, pCol, c.espAlpha * pulse, c.espStyle);
                    if (c.playerHighlight) drawBoxColor(matrices, lines, box.expand(0.04), isFr ? c.friendColor : NeonButton.getDynamicColor(c.playerHighlightColor, 0), c.playerHighlightAlpha * pulse);
                    if (c.chams) {
                        int col = isFr ? c.friendColor : NeonButton.getDynamicColor(c.chamsColor, 200);
                        for (int l = 1; l <= 3; l++) drawBoxColor(matrices, lines, box.expand(-0.04 * l), col, c.chamsAlpha);
                    }
                    if (c.tracers) draw3DLine(matrices, lines, lookX, lookY, lookZ, e.getX() - cam.x, e.getY() + e.getHeight() * 0.5 - cam.y, e.getZ() - cam.z, isFr ? c.friendColor : NeonButton.getDynamicColor(c.tracersColor, 0), c.tracersAlpha);
                } else if (e instanceof ItemEntity && c.itemEsp) {
                    drawBoxColor(matrices, lines, box.expand(0.05), NeonButton.getDynamicColor(c.itemEspColor, 0), 0.90f);
                } else if (e instanceof MobEntity && c.mobEsp) {
                    drawBoxColor(matrices, lines, box, NeonButton.getDynamicColor(c.mobEspColor, 0), 0.80f);
                }
                if (c.entityHighlight && !(e instanceof PlayerEntity)) {
                    drawBoxColor(matrices, lines, box.expand(0.02), NeonButton.getDynamicColor(c.entityHighlightColor, 0), 0.75f * pulse);
                }
            }
        }

        if (c.projectileTrails && !PROJECTILE_TRAILS.isEmpty()) {
            for (LinkedList<Vec3d> pts : PROJECTILE_TRAILS.values()) {
                int total = pts.size(), idx = 0;
                for (Vec3d pt : pts) {
                    int col = NeonButton.getDynamicColor(c.trailColor, idx * 60);
                    float alpha = (idx++ / (float) Math.max(1, total)) * 0.90f;
                    double px = pt.x - cam.x, py = pt.y - cam.y, pz = pt.z - cam.z;
                    drawBoxColor(matrices, lines, new Box(px - 0.02, py - 0.02, pz - 0.02, px + 0.02, py + 0.02, pz + 0.02), col, alpha);
                }
            }
        }

        if (c.nimb && !mc.options.getPerspective().isFirstPerson()) {
            int col = NeonButton.getDynamicColor(c.nimbColor, 0);
            float r = ((col >> 16) & 0xFF) / 255f, g = ((col >> 8) & 0xFF) / 255f, b = (col & 0xFF) / 255f;
            double nx = mc.player.getX() - cam.x, ny = mc.player.getEyeY() + 0.36 + Math.sin(now / 350.0) * 0.03 - cam.y, nz = mc.player.getZ() - cam.z;
            draw3DRing(matrices, lines, nx, ny, nz, c.nimbSize, r, g, b, 0.95f);
            draw3DRing(matrices, lines, nx, ny, nz, c.nimbSize + 0.025, r, g, b, 0.45f);
        }

        if (c.trails && TRAIL_POINTS.size() > 2) {
            int total = TRAIL_POINTS.size(), idx = 0;
            for (Vec3d pt : TRAIL_POINTS) {
                int col = NeonButton.getDynamicColor(c.trailColor, idx * 45);
                float alpha = (idx++ / (float) total) * 0.85f;
                double px = pt.x - cam.x, py = pt.y - cam.y, pz = pt.z - cam.z;
                drawBoxColor(matrices, lines, new Box(px - 0.025, py - 0.025, pz - 0.025, px + 0.025, py + 0.025, pz + 0.025), col, alpha);
            }
        }

        if (c.targetEsp) {
            LivingEntity target = (mc.targetedEntity instanceof LivingEntity le && le.isAlive()) ? le
                    : (mc.player.getAttacking() != null && mc.player.getAttacking().isAlive() && (mc.player.age - mc.player.getLastAttackTime() < 80)) ? mc.player.getAttacking() : null;
            if (target != null) {
                int col = NeonButton.getDynamicColor(c.targetEspColor, 0);
                float r = ((col >> 16) & 0xFF) / 255f, g = ((col >> 8) & 0xFF) / 255f, b = (col & 0xFF) / 255f;
                double wave = (Math.sin(now / (260.0 / Math.max(0.3f, c.targetEspSpeed))) + 1.0) * 0.5;
                double tx = target.getX() - cam.x, ty = target.getY() + wave * target.getHeight() - cam.y, tz = target.getZ() - cam.z;
                double radius = target.getWidth() * 0.85 + 0.16;
                draw3DRing(matrices, lines, tx, ty, tz, radius, r, g, b, 0.95f);
                draw3DRing(matrices, lines, tx, ty + 0.02, tz, radius + 0.03, r, g, b, 0.45f);
            }
        }

        if (c.jumpCircle && !RINGS.isEmpty()) {
            for (JumpRing ring : RINGS) {
                long age = now - ring.createdMs;
                if (age > 850L) continue;
                float p = age / 850f, inv = 1f - p, ease = 1f - inv * inv * inv, fade = Math.min(1f, inv * 1.4f);
                double baseR = 0.22 + ease * ring.maxRadius;
                int col = NeonButton.getDynamicColor(ring.color, 0);
                float r = ((col >> 16) & 0xFF) / 255f, g = ((col >> 8) & 0xFF) / 255f, b = (col & 0xFF) / 255f;
                double cx = ring.x - cam.x, cy = ring.y - cam.y, cz = ring.z - cam.z;
                draw3DRing(matrices, lines, cx, cy, cz, baseR - 0.03, r, g, b, fade * 0.45f);
                draw3DRing(matrices, lines, cx, cy, cz, baseR, r, g, b, fade * 0.95f);
                draw3DRing(matrices, lines, cx, cy, cz, baseR + 0.035, r, g, b, fade * 0.40f);
                for (int s = 0; s < 8; s++) {
                    double ang = (Math.PI * 2.0 * s) / 8.0 + p * 2.2;
                    draw3DStar(matrices, lines, cx + Math.cos(ang) * baseR, cy + 0.015, cz + Math.sin(ang) * baseR, 0.065, r, g, b, fade);
                }
            }
        }

        if (c.blockOverlay && mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir()) {
                VoxelShape shape = state.getOutlineShape(mc.world, pos);
                int col = NeonButton.getDynamicColor(c.blockOverlayColor, 0);
                if (shape.isEmpty()) {
                    Box box = new Box(pos).expand(0.003).offset(-cam.x, -cam.y, -cam.z);
                    drawBoxColor(matrices, lines, box, col, c.blockOverlayAlpha);
                    drawBoxColor(matrices, lines, box.expand(-0.005), col, c.blockOverlayAlpha * 0.45f);
                } else {
                    shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                        Box box = new Box(pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ,
                                pos.getX() + maxX, pos.getY() + maxY, pos.getZ() + maxZ).expand(0.003).offset(-cam.x, -cam.y, -cam.z);
                        drawBoxColor(matrices, lines, box, col, c.blockOverlayAlpha);
                        drawBoxColor(matrices, lines, box.expand(-0.005), col, c.blockOverlayAlpha * 0.45f);
                    });
                }
            }
        }
    }

    private static void drawStyledEsp(MatrixStack matrices, VertexConsumer lines, Box box, int color, float alpha, int style) {
        if (style == 2) { drawBoxColor(matrices, lines, box, color, alpha); drawBoxColor(matrices, lines, box.expand(0.035), color, alpha * 0.45f); }
        else if (style == 1) {
            double h = (box.maxY - box.minY) * 0.25;
            drawBoxColor(matrices, lines, new Box(box.minX, box.minY, box.minZ, box.maxX, box.minY + h, box.maxZ), color, alpha);
            drawBoxColor(matrices, lines, new Box(box.minX, box.maxY - h, box.minZ, box.maxX, box.maxY, box.maxZ), color, alpha);
        } else drawBoxColor(matrices, lines, box, color, alpha);
    }

    private static void drawBoxColor(MatrixStack matrices, VertexConsumer lines, Box box, int color, float alpha) {
        if (alpha <= 0.02f) return;
        WorldRenderer.drawBox(matrices, lines, box, ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, Math.min(1f, alpha));
    }

    private static void draw3DLine(MatrixStack matrices, VertexConsumer lines, double x1, double y1, double z1, double x2, double y2, double z2, int color, float alpha) {
        int steps = 28;
        for (int i = 0; i < steps; i++) {
            double t1 = i / (double) steps, t2 = (i + 1) / (double) steps;
            double ax = x1 + (x2 - x1) * t1, ay = y1 + (y2 - y1) * t1, az = z1 + (z2 - z1) * t1;
            double bx = x1 + (x2 - x1) * t2, by = y1 + (y2 - y1) * t2, bz = z1 + (z2 - z1) * t2;
            drawBoxColor(matrices, lines, new Box(Math.min(ax, bx) - 0.003, Math.min(ay, by) - 0.003, Math.min(az, bz) - 0.003,
                    Math.max(ax, bx) + 0.003, Math.max(ay, by) + 0.003, Math.max(az, bz) + 0.003), color, alpha);
        }
    }

    private static void draw3DRing(MatrixStack matrices, VertexConsumer lines, double cx, double cy, double cz, double radius, float r, float g, float b, float alpha) {
        if (radius <= 0.02 || alpha <= 0.02f) return;
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a1 = (Math.PI * 2.0 * i) / segments, a2 = (Math.PI * 2.0 * (i + 1)) / segments;
            double x1 = cx + Math.cos(a1) * radius, z1 = cz + Math.sin(a1) * radius;
            double x2 = cx + Math.cos(a2) * radius, z2 = cz + Math.sin(a2) * radius;
            WorldRenderer.drawBox(matrices, lines, new Box(Math.min(x1, x2) - 0.004, cy - 0.004, Math.min(z1, z2) - 0.004,
                    Math.max(x1, x2) + 0.004, cy + 0.004, Math.max(z1, z2) + 0.004), r, g, b, alpha);
        }
    }

    private static void draw3DStar(MatrixStack matrices, VertexConsumer lines, double sx, double sy, double sz, double size, float r, float g, float b, float alpha) {
        if (alpha <= 0.02f) return;
        WorldRenderer.drawBox(matrices, lines, new Box(sx - size * 0.35, sy - size * 0.35, sz - size * 0.35, sx + size * 0.35, sy + size * 0.35, sz + size * 0.35), 1f, 1f, 1f, alpha);
        WorldRenderer.drawBox(matrices, lines, new Box(sx - size, sy - 0.005, sz - 0.005, sx + size, sy + 0.005, sz + 0.005), r, g, b, alpha * 0.9f);
        WorldRenderer.drawBox(matrices, lines, new Box(sx - 0.005, sy - 0.005, sz - size, sx + 0.005, sy + 0.005, sz + size), r, g, b, alpha * 0.9f);
    }
}
// === КОНЕЦ ФАЙЛА ===
