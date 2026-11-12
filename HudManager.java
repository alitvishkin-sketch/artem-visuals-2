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
 * Полноценный менеджер HUD ARTEM VISUALS 3.0 (18 виджетов, бинды, авто-FunTime, Radar и MiniMap).
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

    // Кэш цветов поверхности для MiniMap (32x32)
    private static final int MAP_SIZE = 32;
    private static final int[][] MINIMAP_COLORS = new int[MAP_SIZE][MAP_SIZE];
    private static int mapScanRow = 0;

    static {
        ELEMENTS.add(new TopBarElement());
        ELEMENTS.add(new HotKeysElement());
        ELEMENTS.add(new TargetHudElement());
        ELEMENTS.add(new PotionsElement());
        ELEMENTS.add(new ArrayListElement());
        ELEMENTS.add(new ArmorElement());
        ELEMENTS.add(new HealthElement());
        ELEMENTS.add(new HungerElement());
        ELEMENTS.add(new FpsElement());
        ELEMENTS.add(new PingElement());
        ELEMENTS.add(new XyzElement());
        ELEMENTS.add(new CpsElement());
        ELEMENTS.add(new SpeedElement());
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

        // 1. Отслеживание ПКМ для CPS (Left / Right)
        boolean rmb = mc.options != null && mc.options.useKey.isPressed();
        if (rmb && !wasRmbPressed) {
            rmbTimestamps[rmbIdx % rmbTimestamps.length] = System.currentTimeMillis();
            rmbIdx++;
        }
        wasRmbPressed = rmb;

        // 2. Авто-определение сервера FunTime
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

        // 3. Обработка горячих клавиш (биндов модулей) когда меню закрыто
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

        // 4. Постепенное обновление миникарты (2 строки за тик для максимального FPS)
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

        // Сначала рисуем 3D->2D проекции в мире (NameTags и Damage Indicator)
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
        c.showArrayList = true;
        c.showArmor = true;
        c.showXyz = true;
        c.showFps = false;
        c.showPing = false;
        c.showCps = false;
        c.showWasd = false;
        c.showKeystrokes = false;
        c.save();
    }

    public static void applyFunTimePreset(MinecraftClient mc) {
        ArtemConfig c = ArtemConfig.get();
        c.showServer = true;
        c.showTargetHud = true;
        c.showHotKeys = true;
        c.showPotions = true;
        c.showArrayList = true;
        c.showArmor = true;
        c.showXyz = true;
        c.showCps = true;

        c.playerHighlight = true;
        c.nameTags = true;
        c.itemEsp = true;
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

    // ——— HUD Виджеты ———

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

        private String buildOnline(MinecraftClient mc) {
            ClientPlayNetworkHandler h = mc.getNetworkHandler();
            int count = (h != null && h.getPlayerList() != null) ? h.getPlayerList().size() : 1;
            return count + " pl";
        }

        private String buildPing(MinecraftClient mc) {
            int p = queryPing(mc);
            return (p >= 0 ? p : 0) + " ms";
        }

        private String buildFps(MinecraftClient mc) {
            return mc.getCurrentFps() + " FPS";
        }

        @Override
        protected void measure(MinecraftClient mc) {
            float s = 0.78f;
            String srv = getText(mc);
            String online = buildOnline(mc);
            String ping = buildPing(mc);
            String fps = buildFps(mc);
            width = Math.round(16
                    + mc.textRenderer.getWidth(srv) * s + 9
                    + mc.textRenderer.getWidth(online) * s + 9
                    + mc.textRenderer.getWidth(ping) * s + 9
                    + mc.textRenderer.getWidth(fps) * s + 7);
            height = 13;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            float s = 0.78f;
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 4, 0xE011111B, 0xE0090910);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 4, 0x22FFFFFF);

            int wx = x + 5;
            int wy = y + 3;
            ctx.fill(wx, wy + 3, wx + 2, wy + 4, NeonButton.ACCENT);
            ctx.fill(wx + 2, wy + 1, wx + 3, wy + 5, NeonButton.ACCENT_BRIGHT);
            ctx.fill(wx + 3, wy + 2, wx + 4, wy + 6, NeonButton.ACCENT);
            ctx.fill(wx + 4, wy + 3, wx + 6, wy + 4, NeonButton.ACCENT);

            float cx = x + 14;
            float ty = y + 3.5f;

            String srv = getText(mc);
            NeonButton.drawSmallText(ctx, mc.textRenderer, srv, cx, ty, 0xFFF2F2FA, s);
            cx += mc.textRenderer.getWidth(srv) * s + 4;
            ctx.fill(Math.round(cx), y + 3, Math.round(cx) + 1, y + height - 3, 0x28FFFFFF);
            cx += 5;

            String online = buildOnline(mc);
            NeonButton.drawSmallText(ctx, mc.textRenderer, online, cx, ty, 0xFF9E9EB6, s);
            cx += mc.textRenderer.getWidth(online) * s + 4;
            ctx.fill(Math.round(cx), y + 3, Math.round(cx) + 1, y + height - 3, 0x28FFFFFF);
            cx += 5;

            String ping = buildPing(mc);
            NeonButton.drawSmallText(ctx, mc.textRenderer, ping, cx, ty, NeonButton.ACCENT_BRIGHT, s);
            cx += mc.textRenderer.getWidth(ping) * s + 4;
            ctx.fill(Math.round(cx), y + 3, Math.round(cx) + 1, y + height - 3, 0x28FFFFFF);
            cx += 5;

            String fps = buildFps(mc);
            NeonButton.drawSmallText(ctx, mc.textRenderer, fps, cx, ty, 0xFFF2F2FA, s);
        }

        @Override
        protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return 120;
            measure(mc);
            return (mc.getWindow().getScaledWidth() - width) / 2;
        }

        @Override protected int defaultY() { return 5; }
    }

    static class HotKeysElement extends HudElement {
        HotKeysElement() { super("hotkeys"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showHotKeys; }
        @Override public String getText(MinecraftClient mc) { return "Hot Keys"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }

        private List<String[]> activeBinds(ArtemConfig c) {
            List<String[]> list = new ArrayList<>();
            list.add(new String[]{"ClickGUI", "RSHIFT"});
            if (c.esp || c.playerHighlight) list.add(new String[]{"ESP / HL", "ON"});
            if (c.jumpCircle) list.add(new String[]{"Jump Circle", "ON"});
            if (c.targetEsp) list.add(new String[]{"Target ESP", "ON"});
            if (c.trails) list.add(new String[]{"Trails", "ON"});
            return list;
        }

        @Override
        protected void measure(MinecraftClient mc) {
            List<String[]> binds = activeBinds(ArtemConfig.get());
            width = 76;
            height = 15 + Math.min(4, binds.size()) * 10;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String[]> binds = activeBinds(c);
            float s = 0.78f;
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 4, 0xD811111B, 0xD80A0A11);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 4, 0x1EFFFFFF);

            NeonButton.fillSmoothRounded(ctx, x + 5, y + 4, 6, 5, 1, NeonButton.ACCENT_BRIGHT);
            NeonButton.drawSmallText(ctx, mc.textRenderer, "Hot Keys", x + 14, y + 3.5f, 0xFFE4E4F2, s);

            int ry = y + 14;
            for (int i = 0; i < Math.min(4, binds.size()); i++) {
                String[] b = binds.get(i);
                NeonButton.drawSmallText(ctx, mc.textRenderer, b[0], x + 6, ry, 0xFFCCCCE0, s);
                float kw = mc.textRenderer.getWidth(b[1]) * s;
                NeonButton.drawSmallText(ctx, mc.textRenderer, b[1], x + width - kw - 6, ry, 0xFF8E8EA8, s);
                ry += 10;
            }
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 5; }
    }

    static class TargetHudElement extends HudElement {
        private float smoothHp = 20f;

        TargetHudElement() { super("target"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showTargetHud; }
        @Override public String getText(MinecraftClient mc) { return "TargetHUD"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }

        private LivingEntity resolveTarget(MinecraftClient mc, ArtemConfig c) {
            if (mc.targetedEntity instanceof LivingEntity le && le.isAlive()) return le;
            if (mc.player != null) {
                LivingEntity last = mc.player.getAttacking();
                if (last != null && last.isAlive() && (mc.player.age - mc.player.getLastAttackTime() < 80)) {
                    return last;
                }
                if (c.hudEditMode) return mc.player;
            }
            return null;
        }

        @Override
        protected void measure(MinecraftClient mc) {
            LivingEntity target = resolveTarget(mc, ArtemConfig.get());
            if (target == null) {
                width = 0;
                height = 0;
                return;
            }
            width = 106;
            height = 24;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            LivingEntity target = resolveTarget(mc, c);
            if (target == null) return;

            float hp = Math.max(0f, target.getHealth());
            float maxHp = Math.max(1f, target.getMaxHealth());
            float abs = Math.max(0f, target.getAbsorptionAmount());

            smoothHp += (hp - smoothHp) * 0.25f;
            float ratio = Math.min(1f, Math.max(0f, smoothHp / maxHp));

            NeonButton.fillGradientRounded(ctx, x, y, width, height, 4, 0xE011111B, 0xE0090910);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 4, 0x22FFFFFF);

            int avSize = 18;
            int ax = x + 3;
            int ay = y + 3;
            NeonButton.fillSmoothRounded(ctx, ax, ay, avSize, avSize, 3, 0xFF1A1A28);

            String name = target.getName().getString();
            if (target instanceof AbstractClientPlayerEntity player) {
                PlayerSkinDrawer.draw(ctx, player.getSkinTextures().texture(), ax + 1, ay + 1, avSize - 2);
            } else {
                String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
                NeonButton.drawSmallText(ctx, mc.textRenderer, initial, ax + 6, ay + 5, NeonButton.ACCENT_BRIGHT, 0.9f);
            }

            if (name.length() > 15) name = name.substring(0, 14) + "…";
            int tx = ax + avSize + 5;
            NeonButton.drawSmallText(ctx, mc.textRenderer, name, tx, y + 3.5f, 0xFFF2F2FA, 0.78f);

            String hpStr = String.format("HP / %.1f", hp);
            NeonButton.drawSmallText(ctx, mc.textRenderer, hpStr, tx, y + 11.5f, 0xFF8E8EA6, 0.72f);
            if (abs > 0.1f) {
                String absStr = String.format(" +%.0f", abs);
                float hw = mc.textRenderer.getWidth(hpStr) * 0.72f;
                NeonButton.drawSmallText(ctx, mc.textRenderer, absStr, tx + hw, y + 11.5f, 0xFFFFD166, 0.72f);
            }

            int barX = tx;
            int barY = y + 19;
            int barW = width - (tx - x) - 4;
            int barH = 2;
            NeonButton.fillSmoothRounded(ctx, barX, barY, barW, barH, 1, 0xFF222232);
            int fillW = Math.round(barW * ratio);
            if (fillW > 1) {
                NeonButton.fillGradientRounded(ctx, barX, barY, fillW, barH, 1, NeonButton.ACCENT, NeonButton.ACCENT_BRIGHT);
            }
        }

        @Override
        protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledWidth() - 112 : 220;
        }

        @Override protected int defaultY() { return 5; }
    }

    static class PotionsElement extends HudElement {
        PotionsElement() { super("potions"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPotions; }
        @Override public String getText(MinecraftClient mc) { return "Potions"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }

        private List<String[]> collectRows(MinecraftClient mc, ArtemConfig c) {
            List<String[]> rows = new ArrayList<>();
            if (mc.player != null) {
                Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
                for (StatusEffectInstance inst : effects) {
                    String name = inst.getEffectType().value().getName().getString();
                    int amp = inst.getAmplifier();
                    if (amp == 1) name += " II";
                    else if (amp == 2) name += " III";
                    else if (amp >= 3) name += " " + (amp + 1);

                    String time;
                    if (inst.isInfinite() || inst.getDuration() < 0) {
                        time = "∞";
                    } else {
                        int sec = inst.getDuration() / 20;
                        time = String.format("%d:%02d", sec / 60, sec % 60);
                    }
                    int col = 0xFF000000 | inst.getEffectType().value().getColor();
                    rows.add(new String[]{name, time, String.valueOf(col)});
                }
            }
            if (rows.isEmpty() && c.hudEditMode) {
                rows.add(new String[]{"Strength II", "2:19", String.valueOf(0xFFFF5E7E)});
                rows.add(new String[]{"Speed II", "2:18", String.valueOf(0xFF70D6FF)});
            }
            return rows;
        }

        @Override
        protected void measure(MinecraftClient mc) {
            List<String[]> rows = collectRows(mc, ArtemConfig.get());
            if (rows.isEmpty()) {
                width = 0;
                height = 0;
                return;
            }
            float s = 0.78f;
            int maxW = 78;
            for (String[] r : rows) {
                int w = Math.round(14 + mc.textRenderer.getWidth(r[0]) * s + 10 + mc.textRenderer.getWidth(r[1]) * s + 6);
                if (w > maxW) maxW = w;
            }
            width = maxW;
            height = 15 + rows.size() * 10;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String[]> rows = collectRows(mc, c);
            if (rows.isEmpty()) return;
            float s = 0.78f;

            NeonButton.fillGradientRounded(ctx, x, y, width, height, 4, 0xD811111B, 0xD80A0A11);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 4, 0x1EFFFFFF);

            NeonButton.fillSmoothRounded(ctx, x + 5, y + 4, 4, 5, 1, NeonButton.ACCENT_BRIGHT);
            NeonButton.drawSmallText(ctx, mc.textRenderer, "Potions", x + 12, y + 3.5f, 0xFFE2E2F0, s);

            int ry = y + 14;
            for (String[] r : rows) {
                int dotCol = Integer.parseInt(r[2]);
                NeonButton.drawCircle(ctx, x + 7, ry + 3, 2, dotCol);
                NeonButton.drawSmallText(ctx, mc.textRenderer, r[0], x + 12, ry, 0xFFE6E6F2, s);
                float tw = mc.textRenderer.getWidth(r[1]) * s;
                NeonButton.drawSmallText(ctx, mc.textRenderer, r[1], x + width - tw - 6, ry, 0xFF8E8EA6, s);
                ry += 10;
            }
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 64; }
    }

    static class ArrayListElement extends HudElement {
        ArrayListElement() { super("arraylist"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showArrayList; }
        @Override public String getText(MinecraftClient mc) { return "ArrayList"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }

        private List<String> getEnabledModules(ArtemConfig c) {
            List<String> list = new ArrayList<>();
            if (c.esp) list.add("ESP");
            if (c.playerHighlight) list.add("PlayerHighlight");
            if (c.tracers) list.add("Tracers");
            if (c.chams) list.add("Chams");
            if (c.nameTags) list.add("NameTags");
            if (c.itemEsp) list.add("ItemESP");
            if (c.mobEsp) list.add("MobESP");
            if (c.entityHighlight) list.add("EntityHighlight");
            if (c.hitParticles) list.add("HitParticles");
            if (c.damageIndicator) list.add("DamageIndicator");
            if (c.targetEsp) list.add("TargetESP");
            if (c.jumpCircle) list.add("JumpCircle");
            if (c.trails) list.add("Trails");
            if (c.nimb) list.add("Nimb");
            if (c.blockOverlay) list.add("BlockOverlay");
            if (c.customCrosshair) list.add("Crosshair");
            list.sort(Comparator.comparingInt(String::length).reversed());
            return list;
        }

        @Override
        protected void measure(MinecraftClient mc) {
            List<String> mods = getEnabledModules(ArtemConfig.get());
            if (mods.isEmpty()) {
                width = 0;
                height = 0;
                return;
            }
            width = 76;
            height = Math.min(10, mods.size()) * 10 + 2;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String> mods = getEnabledModules(c);
            if (mods.isEmpty()) return;
            float s = 0.76f;
            int ry = y;
            for (int i = 0; i < Math.min(10, mods.size()); i++) {
                String m = mods.get(i);
                int tw = Math.round(mc.textRenderer.getWidth(m) * s) + 7;
                int rx = x + width - tw;
                int col = NeonButton.getDynamicColor(NeonButton.ACCENT_BRIGHT, i * 240);
                NeonButton.fillSmoothRounded(ctx, rx, ry, tw, 9, 2, 0xC810101A);
                ctx.fill(rx + tw - 2, ry + 1, rx + tw, ry + 8, col);
                NeonButton.drawSmallText(ctx, mc.textRenderer, m, rx + 3, ry + 1.5f, 0xFFF2F2FA, s);
                ry += 10;
            }
        }

        @Override
        protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledWidth() - 82 : 240;
        }

        @Override protected int defaultY() { return 34; }
    }

    static class ArmorElement extends HudElement {
        ArmorElement() { super("armor"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showArmor; }
        @Override public String getText(MinecraftClient mc) { return "Armor"; }
        @Override public int getColor(MinecraftClient mc) { return 0xFFF2F2FA; }

        @Override
        protected void measure(MinecraftClient mc) {
            width = 76;
            height = 20;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.player == null) return;
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 4, 0xD811111B, 0xD80A0A11);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 4, 0x1EFFFFFF);

            List<ItemStack> items = new ArrayList<>();
            for (ItemStack st : mc.player.getArmorItems()) {
                items.add(st);
            }
            int ix = x + width - 19;
            for (ItemStack st : items) {
                if (!st.isEmpty()) {
                    ctx.drawItem(st, ix, y + 2);
                    if (st.isDamageable() && st.getMaxDamage() > 0) {
                        int pct = Math.max(0, Math.round((1f - (float) st.getDamage() / st.getMaxDamage()) * 100f));
                        int col = pct > 50 ? 0xFF70E000 : (pct > 25 ? 0xFFFFD166 : 0xFFFF4D6D);
                        NeonButton.drawSmallText(ctx, mc.textRenderer, pct + "%", ix + 1, y + 13, col, 0.58f);
                    }
                }
                ix -= 18;
            }
        }

        @Override
        protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledWidth() / 2 + 16 : 200;
        }

        @Override
        protected int defaultY() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledHeight() - 56 : 190;
        }
    }

    static class HealthElement extends HudElement {
        HealthElement() { super("health_hud"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showHealthHud; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "HP 20.0";
            return String.format("HP %.1f/%.0f", mc.player.getHealth(), mc.player.getMaxHealth());
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFFF5E7E; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 102; }
    }

    static class HungerElement extends HudElement {
        HungerElement() { super("hunger_hud"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showHungerHud; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "FOOD 20";
            return String.format("FOOD %d (%.0f)", mc.player.getHungerManager().getFoodLevel(),
                    mc.player.getHungerManager().getSaturationLevel());
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFFFB703; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 117; }
    }

    static class FpsElement extends HudElement {
        FpsElement() { super("fps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showFps; }
        @Override public String getText(MinecraftClient mc) { return "FPS " + mc.getCurrentFps(); }
        @Override public int getColor(MinecraftClient mc) { return 0xFFF2F2FA; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 132; }
    }

    static class PingElement extends HudElement {
        PingElement() { super("ping"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPing; }
        @Override public String getText(MinecraftClient mc) {
            int ping = queryPing(mc);
            return ping >= 0 ? "PING " + ping + "ms" : "PING 0ms";
        }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 147; }
    }

    static class XyzElement extends HudElement {
        XyzElement() { super("xyz"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showXyz; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "XYZ 0 0 0";
            BlockPos p = mc.player.getBlockPos();
            return String.format("XYZ %d %d %d", p.getX(), p.getY(), p.getZ());
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFE4E4F0; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 162; }
    }

    static class CpsElement extends HudElement {
        CpsElement() { super("cps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showCps; }
        @Override public String getText(MinecraftClient mc) {
            return "CPS " + currentLmbCps() + " | " + currentRmbCps();
        }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 177; }
    }

    static class SpeedElement extends HudElement {
        SpeedElement() { super("speed"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showSpeed; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "BPS 0.00";
            double bps = Math.hypot(mc.player.getX() - mc.player.prevX, mc.player.getZ() - mc.player.prevZ) * 20.0;
            return String.format("BPS %.2f", bps);
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFF00F5FF; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 192; }
    }

    static class DirectionElement extends HudElement {
        DirectionElement() { super("facing"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showDirection; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "DIR S (0°)";
            float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
            String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            int idx = Math.round(((yaw + 360f) % 360f) / 45f) % 8;
            return String.format("DIR %s (%.0f°)", dirs[idx], yaw);
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFE4E4F0; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 207; }
    }

    static class NicknameElement extends HudElement {
        NicknameElement() { super("nick"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showNickname; }
        @Override public String getText(MinecraftClient mc) {
            String n = mc.player != null ? mc.player.getName().getString() : "Player";
            return "USER " + n;
        }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }
        @Override protected int defaultX() { return 76; }
        @Override protected int defaultY() { return 162; }
    }

    static class PlaytimeElement extends HudElement {
        PlaytimeElement() { super("playtime"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPlaytime; }
        @Override public String getText(MinecraftClient mc) {
            long sec = (System.currentTimeMillis() - SESSION_START_MS) / 1000L;
            long h = sec / 3600;
            long m = (sec % 3600) / 60;
            long s = sec % 60;
            return h > 0 ? String.format("TIME %d:%02d:%02d", h, m, s) : String.format("TIME %02d:%02d", m, s);
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFE4E4F0; }
        @Override protected int defaultX() { return 76; }
        @Override protected int defaultY() { return 177; }
    }

    static class WasdElement extends HudElement {
        WasdElement() { super("wasd"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showWasd && !c.showKeystrokes; }
        @Override public String getText(MinecraftClient mc) { return "WASD"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }

        @Override protected void measure(MinecraftClient mc) {
            width = 52;
            height = 34;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.options == null) return;
            int ks = 16, g = 2;
            drawKey(ctx, mc, x + ks + g, y, ks, ks, "W", mc.options.forwardKey.isPressed());
            drawKey(ctx, mc, x, y + ks + g, ks, ks, "A", mc.options.leftKey.isPressed());
            drawKey(ctx, mc, x + ks + g, y + ks + g, ks, ks, "S", mc.options.backKey.isPressed());
            drawKey(ctx, mc, x + (ks + g) * 2, y + ks + g, ks, ks, "D", mc.options.rightKey.isPressed());
        }

        private void drawKey(DrawContext ctx, MinecraftClient mc, int kx, int ky, int kw, int kh, String label, boolean pressed) {
            if (pressed) {
                NeonButton.fillGradientRounded(ctx, kx, ky, kw, kh, 4, NeonButton.ACCENT_BRIGHT, NeonButton.ACCENT);
                NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, kw, kh, 4, 0x88FFFFFF);
            } else {
                NeonButton.fillGradientRounded(ctx, kx, ky, kw, kh, 4, 0xD811111B, 0xD80A0A11);
                NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, kw, kh, 4, 0x1EFFFFFF);
            }
            float tw = mc.textRenderer.getWidth(label) * 0.78f;
            NeonButton.drawSmallText(ctx, mc.textRenderer, label, kx + (kw - tw) / 2f, ky + (kh - 6) / 2f,
                    pressed ? 0xFFFFFFFF : 0xFF9696AC, 0.78f);
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledHeight() - 40 : 200;
        }
    }

    static class KeystrokesElement extends HudElement {
        KeystrokesElement() { super("keystrokes"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showKeystrokes; }
        @Override public String getText(MinecraftClient mc) { return "Keystrokes"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }

        @Override protected void measure(MinecraftClient mc) {
            width = 52;
            height = 60;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.options == null) return;
            int ks = 16, g = 2;
            drawKey(ctx, mc, x + ks + g, y, ks, ks, "W", mc.options.forwardKey.isPressed());
            drawKey(ctx, mc, x, y + ks + g, ks, ks, "A", mc.options.leftKey.isPressed());
            drawKey(ctx, mc, x + ks + g, y + ks + g, ks, ks, "S", mc.options.backKey.isPressed());
            drawKey(ctx, mc, x + (ks + g) * 2, y + ks + g, ks, ks, "D", mc.options.rightKey.isPressed());

            int row3 = y + (ks + g) * 2;
            drawKey(ctx, mc, x, row3, 25, 11, "LMB " + currentLmbCps(), mc.options.attackKey.isPressed());
            drawKey(ctx, mc, x + 27, row3, 25, 11, "RMB " + currentRmbCps(), mc.options.useKey.isPressed());

            int row4 = row3 + 13;
            drawKey(ctx, mc, x, row4, 52, 10, "SPACE", mc.options.jumpKey.isPressed());
        }

        private void drawKey(DrawContext ctx, MinecraftClient mc, int kx, int ky, int kw, int kh, String label, boolean pressed) {
            if (pressed) {
                NeonButton.fillGradientRounded(ctx, kx, ky, kw, kh, 3, NeonButton.ACCENT_BRIGHT, NeonButton.ACCENT);
                NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, kw, kh, 3, 0x88FFFFFF);
            } else {
                NeonButton.fillGradientRounded(ctx, kx, ky, kw, kh, 3, 0xD811111B, 0xD80A0A11);
                NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, kw, kh, 3, 0x1EFFFFFF);
            }
            float s = label.length() > 2 ? 0.62f : 0.78f;
            float tw = mc.textRenderer.getWidth(label) * s;
            NeonButton.drawSmallText(ctx, mc.textRenderer, label, kx + (kw - tw) / 2f, ky + (kh - 6) / 2f,
                    pressed ? 0xFFFFFFFF : 0xFF9696AC, s);
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledHeight() - 66 : 180;
        }
    }

    static class RadarElement extends HudElement {
        RadarElement() { super("radar"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showRadar; }
        @Override public String getText(MinecraftClient mc) { return "Radar"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }

        @Override protected void measure(MinecraftClient mc) {
            width = 58;
            height = 58;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 6, 0xDC11111B, 0xDC090910);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 6, 0x26FFFFFF);

            int cx = x + width / 2;
            int cy = y + height / 2;
            ctx.fill(cx, y + 4, cx + 1, y + height - 4, 0x18FFFFFF);
            ctx.fill(x + 4, cy, x + width - 4, cy + 1, 0x18FFFFFF);
            NeonButton.drawCircle(ctx, cx, cy, 2, 0xFFFFFFFF);

            if (mc.world == null || mc.player == null) return;
            double yawRad = Math.toRadians(mc.player.getYaw() + 180.0);
            double cos = Math.cos(yawRad);
            double sin = Math.sin(yawRad);

            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                double dx = e.getX() - mc.player.getX();
                double dz = e.getZ() - mc.player.getZ();
                if (dx * dx + dz * dz > 48 * 48) continue;

                double rx = (dx * cos - dz * sin) * 0.52;
                double rz = (dx * sin + dz * cos) * 0.52;
                int px = MathHelper.clamp((int) Math.round(cx + rx), x + 4, x + width - 5);
                int py = MathHelper.clamp((int) Math.round(cy + rz), y + 4, y + height - 5);

                int col = (e instanceof PlayerEntity) ? NeonButton.ACCENT_BRIGHT
                        : (e instanceof MobEntity) ? 0xFFFFB703
                        : (e instanceof ItemEntity) ? 0xFF00F5FF : 0;
                if (col != 0) {
                    NeonButton.drawCircle(ctx, px, py, 1, col);
                }
            }
        }

        @Override
        protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledWidth() - 64 : 260;
        }

        @Override
        protected int defaultY() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledHeight() - 64 : 180;
        }
    }

    static class MinimapElement extends HudElement {
        MinimapElement() { super("minimap"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showMinimap; }
        @Override public String getText(MinecraftClient mc) { return "MiniMap"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }

        @Override protected void measure(MinecraftClient mc) {
            width = 68;
            height = 68;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 6, 0xE611111B, 0xE6090910);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 6, 0x2EFFFFFF);

            int cell = 2;
            int ox = x + 2;
            int oy = y + 2;
            for (int rz = 0; rz < MAP_SIZE; rz++) {
                for (int rx = 0; rx < MAP_SIZE; rx++) {
                    int col = MINIMAP_COLORS[rz][rx];
                    if (col != 0) {
                        ctx.fill(ox + rx * cell, oy + rz * cell, ox + (rx + 1) * cell, oy + (rz + 1) * cell, col);
                    }
                }
            }

            int cx = x + width / 2;
            int cy = y + height / 2;
            NeonButton.drawCircle(ctx, cx, cy, 2, NeonButton.ACCENT_BRIGHT);
            NeonButton.drawCircle(ctx, cx, cy, 1, 0xFFFFFFFF);
            NeonButton.drawSmallText(ctx, mc.textRenderer, "N", cx - 2, y + 3, 0xFFFFFFFF, 0.65f);
        }

        @Override
        protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledWidth() - 138 : 190;
        }

        @Override
        protected int defaultY() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledHeight() - 74 : 170;
        }
    }
}
