package com.artem.visuals.hud;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.config.ProfileManager;
import com.artem.visuals.effects.EffectManager;
import com.artem.visuals.gui.NeonButton;
import com.artem.visuals.optimize.Optimizer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Аккуратный HUD в стиле PulseVisual без наложений друг на друга.
 */
public final class HudManager {
    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static final long SESSION_START_MS = System.currentTimeMillis();
    private static long lastAttackMs = 0;

    private static final long[] lmbTimestamps = new long[64];
    private static final long[] rmbTimestamps = new long[64];
    private static int lmbIdx = 0;
    private static int rmbIdx = 0;
    private static boolean wasRmbPressed = false;
    private static String detectedServerAddress = "";
    private static final Map<Integer, Boolean> prevKeyStates = new HashMap<>();

    private static final int MAP_SIZE = 32;
    private static final int[][] MINIMAP_COLORS = new int[MAP_SIZE][MAP_SIZE];
    private static int mapScanRow = 0;

    static {
        ELEMENTS.add(new TopBarElement());
        ELEMENTS.add(new HotKeysElement());
        ELEMENTS.add(new TargetHudElement());
        ELEMENTS.add(new PotionsElement());
        ELEMENTS.add(new ArmorElement());
        ELEMENTS.add(new ArrayListElement());
        ELEMENTS.add(new FpsElement());
        ELEMENTS.add(new PingElement());
        ELEMENTS.add(new XyzElement());
        ELEMENTS.add(new SpeedElement());
        ELEMENTS.add(new CpsElement());
        ELEMENTS.add(new HealthElement());
        ELEMENTS.add(new HungerElement());
        ELEMENTS.add(new DirectionElement());
        ELEMENTS.add(new NicknameElement());
        ELEMENTS.add(new PlaytimeElement());
        ELEMENTS.add(new WasdElement());
        ELEMENTS.add(new KeystrokesElement());
        ELEMENTS.add(new RadarElement());
        ELEMENTS.add(new MinimapElement());
    }

    private HudManager() {}

    public static void tick(MinecraftClient mc) {
        if (mc == null) return;
        ArtemConfig c = ArtemConfig.get();

        boolean rmb = mc.options != null && mc.options.useKey.isPressed();
        if (rmb && !wasRmbPressed) {
            rmbTimestamps[rmbIdx % rmbTimestamps.length] = System.currentTimeMillis();
            rmbIdx++;
        }
        wasRmbPressed = rmb;

        if (mc.world != null && c.autoFunTime) {
            ServerInfo info = mc.getCurrentServerEntry();
            String addr = (info != null && info.address != null) ? info.address.toLowerCase() : "";
            if (!addr.equals(detectedServerAddress)) {
                detectedServerAddress = addr;
                if (addr.contains("funtime")) {
                    if (ProfileManager.exists("FunTime")) {
                        ProfileManager.loadProfile("FunTime");
                    } else {
                        applyFunTimePreset(mc);
                    }
                }
            }
        } else if (mc.world == null) {
            detectedServerAddress = "";
        }

        if (mc.currentScreen == null && mc.getWindow() != null && !c.keybinds.isEmpty()) {
            long win = mc.getWindow().getHandle();
            for (Map.Entry<String, Integer> entry : c.keybinds.entrySet()) {
                int key = entry.getValue();
                if (key <= 0) continue;
                boolean down = GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
                boolean was = prevKeyStates.getOrDefault(key, false);
                if (down && !was) {
                    toggleById(entry.getKey(), c, mc);
                }
                prevKeyStates.put(key, down);
            }
        }

        if (c.showMinimap && mc.world != null && mc.player != null) {
            BlockPos center = mc.player.getBlockPos();
            int half = MAP_SIZE / 2;
            for (int step = 0; step < 2; step++) {
                int rz = mapScanRow % MAP_SIZE;
                for (int rx = 0; rx < MAP_SIZE; rx++) {
                    int wx = center.getX() + (rx - half);
                    int wz = center.getZ() + (rz - half);
                    int topY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz) - 1;
                    BlockPos bp = new BlockPos(wx, Math.max(mc.world.getBottomY(), topY), wz);
                    BlockState st = mc.world.getBlockState(bp);
                    int mapRgb = st.getMapColor(mc.world, bp).color;
                    MINIMAP_COLORS[rz][rx] = (mapRgb == 0) ? 0xFF1A1C28 : (0xFF000000 | mapRgb);
                }
                mapScanRow++;
            }
        }
    }

    private static void toggleById(String id, ArtemConfig c, MinecraftClient mc) {
        switch (id) {
            case "esp" -> c.esp = !c.esp;
            case "player_hl" -> c.playerHighlight = !c.playerHighlight;
            case "tracers" -> c.tracers = !c.tracers;
            case "chams" -> c.chams = !c.chams;
            case "nametags" -> c.nameTags = !c.nameTags;
            case "item_esp" -> c.itemEsp = !c.itemEsp;
            case "mob_esp" -> c.mobEsp = !c.mobEsp;
            case "entity_hl" -> c.entityHighlight = !c.entityHighlight;
            case "hit_particles" -> c.hitParticles = !c.hitParticles;
            case "dmg_ind" -> c.damageIndicator = !c.damageIndicator;
            case "nimb" -> c.nimb = !c.nimb;
            case "trails" -> c.trails = !c.trails;
            case "target_esp" -> c.targetEsp = !c.targetEsp;
            case "jump_circle" -> c.jumpCircle = !c.jumpCircle;
            case "block_overlay" -> c.blockOverlay = !c.blockOverlay;
            case "world_cust" -> c.worldCustomizer = !c.worldCustomizer;
            case "fullbright" -> {
                c.softLighting = !c.softLighting;
                Optimizer.applyLighting(mc);
            }
        }
        c.save();
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options.hudHidden) return;
        NeonButton.syncTheme();

        EffectManager.renderScreenOverlay(ctx, mc);

        ArtemConfig c = ArtemConfig.get();
        for (HudElement e : ELEMENTS) {
            e.render(ctx, mc, c);
        }
    }

    public static void recordClick() {
        lmbTimestamps[lmbIdx % lmbTimestamps.length] = System.currentTimeMillis();
        lmbIdx++;
        lastAttackMs = System.currentTimeMillis();
    }

    public static int currentLmbCps() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (long t : lmbTimestamps) {
            if (t > 0 && now - t < 1000L) n++;
        }
        return n;
    }

    public static int currentRmbCps() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (long t : rmbTimestamps) {
            if (t > 0 && now - t < 1000L) n++;
        }
        return n;
    }

    public static boolean isAttackingRecently() {
        return System.currentTimeMillis() - lastAttackMs < 200;
    }

    public static boolean mouseClicked(double mx, double my) {
        ArtemConfig c = ArtemConfig.get();
        if (!c.hudEditMode) return false;
        for (HudElement e : ELEMENTS) {
            if (e.mouseClicked(mx, my, c)) return true;
        }
        return false;
    }

    public static boolean mouseScrolled(double mx, double my, double amount) {
        ArtemConfig c = ArtemConfig.get();
        if (!c.hudEditMode) return false;
        for (HudElement e : ELEMENTS) {
            if (e.mouseScrolled(mx, my, amount, c)) return true;
        }
        return false;
    }

    public static void mouseReleased() {
        for (HudElement e : ELEMENTS) e.mouseReleased();
    }

    public static void mouseDragged(double mx, double my) {
        ArtemConfig c = ArtemConfig.get();
        for (HudElement e : ELEMENTS) e.mouseDragged(mx, my, c);
    }

    public static void applyPulsePreset() {
        ArtemConfig c = ArtemConfig.get();
        c.hudPositions.clear();
        c.showServer = true;
        c.showTargetHud = true;
        c.showHotKeys = true;
        c.showPotions = true;
        c.showArmor = true;
        c.showArrayList = false;
        c.showXyz = false;
        c.showFps = false;
        c.showPing = false;
        c.showCps = false;
        c.showWasd = false;
        c.showKeystrokes = false;
        c.showSpeed = false;
        c.showDirection = false;
        c.showNickname = false;
        c.showPlaytime = false;
        c.showHealthHud = false;
        c.showHungerHud = false;
        c.save();
    }

    public static void applyFunTimePreset(MinecraftClient mc) {
        applyPulsePreset();
        ArtemConfig c = ArtemConfig.get();
        c.nameTags = true;
        c.hitParticles = true;
        c.damageIndicator = true;
        c.targetEsp = true;
        c.jumpCircle = true;
        c.blockOverlay = true;
        c.renderTweaks = true;
        c.softLighting = true;

        c.customCrosshair = true;
        c.crosshairStyle = 0;
        c.crosshairSize = 4;
        c.crosshairGap = 2;
        c.crosshairThickness = 1;
        c.crosshairOutline = true;
        c.crosshairDynamic = true;
        c.crosshairColor = 0xFF00F5FF;
        c.save();

        if (mc != null) {
            Optimizer.applyLighting(mc);
            Optimizer.applyPerformancePreset(mc);
        }
    }

    private static int queryPing(MinecraftClient mc) {
        ClientPlayNetworkHandler h = mc.getNetworkHandler();
        if (h == null || mc.player == null) return -1;
        PlayerListEntry e = h.getPlayerListEntry(mc.player.getUuid());
        return e != null ? e.getLatency() : -1;
    }

    // ——— HUD Виджеты (чёткие координаты без наложений) ———

    /**
     * 1. Верхняя центральная пилюля как в PulseVisual: [Волна] artemvisuals.pro | 56 ms | 171 FPS
     */
    static class TopBarElement extends HudElement {
        TopBarElement() { super("server"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showServer; }

        @Override
        public String getText(MinecraftClient mc) {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null && info.address != null) {
                String addr = info.address.toLowerCase();
                if (addr.contains("funtime")) return "mc.funtime.su";
                return info.address;
            }
            return "artemvisuals.pro";
        }

        @Override public int getColor(MinecraftClient mc) { return 0xFFE8E8F4; }

        private String buildPing(MinecraftClient mc) {
            int p = queryPing(mc);
            return (p >= 0 ? p : 0) + " ms";
        }

        private String buildFps(MinecraftClient mc) {
            return mc.getCurrentFps() + " FPS";
        }

        @Override
        protected void measure(MinecraftClient mc) {
            String srv = getText(mc);
            String ping = buildPing(mc);
            String fps = buildFps(mc);
            width = 18
                    + mc.textRenderer.getWidth(srv) + 11
                    + mc.textRenderer.getWidth(ping) + 11
                    + mc.textRenderer.getWidth(fps) + 8;
            height = 16;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, 0x22FFFFFF);

            int wx = x + 6;
            int wy = y + 4;
            ctx.fill(wx, wy + 4, wx + 2, wy + 5, NeonButton.ACCENT);
            ctx.fill(wx + 2, wy + 1, wx + 3, wy + 6, NeonButton.ACCENT_BRIGHT);
            ctx.fill(wx + 3, wy + 3, wx + 4, wy + 8, NeonButton.ACCENT);
            ctx.fill(wx + 4, wy + 4, wx + 6, wy + 5, NeonButton.ACCENT);

            int cx = x + 16;
            int ty = y + 4;

            String srv = getText(mc);
            ctx.drawText(mc.textRenderer, srv, cx, ty, 0xFFF2F2FA, false);
            cx += mc.textRenderer.getWidth(srv) + 5;
            ctx.fill(cx, y + 4, cx + 1, y + height - 4, 0x28FFFFFF);
            cx += 6;

            String ping = buildPing(mc);
            ctx.drawText(mc.textRenderer, ping, cx, ty, NeonButton.ACCENT_BRIGHT, false);
            cx += mc.textRenderer.getWidth(ping) + 5;
            ctx.fill(cx, y + 4, cx + 1, y + height - 4, 0x28FFFFFF);
            cx += 6;

            String fps = buildFps(mc);
            ctx.drawText(mc.textRenderer, fps, cx, ty, 0xFFF2F2FA, false);
        }

        @Override
        protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return 120;
            measure(
