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
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class HudManager {
    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static final long SESSION_START_MS = System.currentTimeMillis();
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static long lastAttackMs = 0;

    private static final long[] lmbTimestamps = new long[64];
    private static final long[] rmbTimestamps = new long[64];
    private static int lmbIdx = 0, rmbIdx = 0;
    private static boolean wasRmbPressed = false;
    private static String detectedServerAddress = "";
    private static final Map<Integer, Boolean> prevKeyStates = new HashMap<>();

    private static final int MAP_SIZE = 24;
    private static final int[][] MINIMAP_COLORS = new int[MAP_SIZE][MAP_SIZE];
    private static int mapScanRow = 0;

    static {
        ELEMENTS.add(new TopBarElement());
        ELEMENTS.add(new EventTimerElement());
        ELEMENTS.add(new InventoryElement());
        ELEMENTS.add(new PotionsElement());
        ELEMENTS.add(new CooldownsElement());
        ELEMENTS.add(new HotKeysElement());
        ELEMENTS.add(new PaperdollElement());
        ELEMENTS.add(new TargetHudElement());
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
            rmbTimestamps[rmbIdx++ % rmbTimestamps.length] = System.currentTimeMillis();
        }
        wasRmbPressed = rmb;

        if (mc.world != null && c.autoFunTime) {
            ServerInfo info = mc.getCurrentServerEntry();
            String addr = (info != null && info.address != null) ? info.address.toLowerCase() : "";
            if (!addr.equals(detectedServerAddress)) {
                detectedServerAddress = addr;
                if (addr.contains("funtime")) {
                    if (ProfileManager.exists("FunTime")) ProfileManager.loadProfile("FunTime");
                    else applyFunTimePreset(mc);
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
                if (down && !prevKeyStates.getOrDefault(key, false)) toggleById(entry.getKey(), c, mc);
                prevKeyStates.put(key, down);
            }
        }

        if (c.showMinimap && mc.world != null && mc.player != null) {
            BlockPos center = mc.player.getBlockPos();
            int half = MAP_SIZE / 2;
            for (int step = 0; step < 2; step++) {
                int rz = mapScanRow++ % MAP_SIZE;
                for (int rx = 0; rx < MAP_SIZE; rx++) {
                    int wx = center.getX() + (rx - half);
                    int wz = center.getZ() + (rz - half);
                    int topY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz) - 1;
                    BlockPos bp = new BlockPos(wx, Math.max(mc.world.getBottomY(), topY), wz);
                    BlockState st = mc.world.getBlockState(bp);
                    int rgb = st.getMapColor(mc.world, bp).color;
                    MINIMAP_COLORS[rz][rx] = (rgb == 0) ? 0xFF1A1C28 : (0xFF000000 | rgb);
                }
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
            case "fullbright" -> { c.softLighting = !c.softLighting; Optimizer.applyLighting(mc); }
        }
        c.save();
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options.hudHidden) return;
        NeonButton.syncTheme();
        EffectManager.renderScreenOverlay(ctx, mc);
        ArtemConfig c = ArtemConfig.get();
        for (HudElement e : ELEMENTS) e.render(ctx, mc, c);
    }

    public static void recordClick() {
        lmbTimestamps[lmbIdx++ % lmbTimestamps.length] = System.currentTimeMillis();
        lastAttackMs = System.currentTimeMillis();
    }

    public static int currentLmbCps() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (long t : lmbTimestamps) if (t > 0 && now - t < 1000L) n++;
        return n;
    }

    public static int currentRmbCps() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (long t : rmbTimestamps) if (t > 0 && now - t < 1000L) n++;
        return n;
    }

    public static boolean isAttackingRecently() { return System.currentTimeMillis() - lastAttackMs < 200; }

    public static boolean mouseClicked(double mx, double my) {
        ArtemConfig c = ArtemConfig.get();
        if (!c.hudEditMode) return false;
        for (HudElement e : ELEMENTS) if (e.mouseClicked(mx, my, c)) return true;
        return false;
    }

    public static boolean mouseScrolled(double mx, double my, double amount) {
        ArtemConfig c = ArtemConfig.get();
        if (!c.hudEditMode) return false;
        for (HudElement e : ELEMENTS) if (e.mouseScrolled(mx, my, amount, c)) return true;
        return false;
    }

    public static void mouseReleased() { for (HudElement e : ELEMENTS) e.mouseReleased(); }
    public static void mouseDragged(double mx, double my) {
        ArtemConfig c = ArtemConfig.get();
        for (HudElement e : ELEMENTS) e.mouseDragged(mx, my, c);
    }

    public static void applyPulsePreset() {
        ArtemConfig c = ArtemConfig.get();
        c.hudPositions.clear();
        c.hudScale = 0.80f;
        c.hudAccentColor = 0xFF8A7CFF;
        c.hudBgColor = 0x101018;
        c.hudBgAlpha = 0.84f;
        c.hudRainbow = false;
        c.hudUseCustomHue = false;

        c.showServer = true;
        c.showInventory = true;
        c.showPotions = true;
        c.showHotKeys = true;
        c.showPaperdoll = true;
        c.showTargetHud = true;
        c.showArmor = true;
        c.showCooldowns = true;
        c.handGlow = true;

        c.showArrayList = false; c.showXyz = false; c.showFps = false;
        c.showPing = false; c.showCps = false; c.showWasd = false;
        c.showKeystrokes = false; c.showSpeed = false; c.showDirection = false;
        c.showNickname = false; c.showPlaytime = false; c.showHealthHud = false; c.showHungerHud = false;
        c.save();
    }

    public static void applyFunTimePreset(MinecraftClient mc) {
        applyPulsePreset();
        ArtemConfig c = ArtemConfig.get();
        c.nameTags = true; c.hitParticles = true; c.damageIndicator = true;
        c.targetEsp = true; c.jumpCircle = true; c.blockOverlay = true;
        c.renderTweaks = true; c.softLighting = true; c.customCrosshair = true;
        c.smallSword = true; c.noHurtCam = true; c.lowFire = true;
        c.autoSprint = true; c.lowArmorAlert = true; c.potionExpiryAlert = true;
        c.deathCoords = true; c.autoDeathWaypoint = true;
        c.crosshairStyle = 0; c.crosshairSize = 4; c.crosshairGap = 2; c.crosshairThickness = 1;
        c.crosshairOutline = true; c.crosshairDynamic = true; c.crosshairColor = 0xFFE8E8F4;
        c.save();
        if (mc != null) { Optimizer.applyLighting(mc); Optimizer.applyPerformancePreset(mc); }
    }

    private static int queryPing(MinecraftClient mc) {
        ClientPlayNetworkHandler h = mc.getNetworkHandler();
        if (h == null || mc.player == null) return -1;
        PlayerListEntry e = h.getPlayerListEntry(mc.player.getUuid());
        return e != null ? e.getLatency() : -1;
    }

    private static int screenW() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledWidth() : 420;
    }

    private static int screenH() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return (mc != null && mc.getWindow() != null) ? mc.getWindow().getScaledHeight() : 240;
    }

    // 1. Верхняя панель по центру (Ник | FPS | Ping | Время)
    static class TopBarElement extends HudElement {
        TopBarElement() { super("server"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showServer; }
        @Override public String getText(MinecraftClient mc) {
            return mc.player != null ? mc.player.getName().getString() : "Artem";
        }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }
        private String fpsStr(MinecraftClient mc) { return mc.getCurrentFps() + " FPS"; }
        private String pingStr(MinecraftClient mc) { int p = queryPing(mc); return (p >= 0 ? p : 0) + " ms"; }
        private String timeStr() { return LocalTime.now().format(CLOCK_FMT); }

        @Override protected void measure(MinecraftClient mc) {
            width = 16 + mc.textRenderer.getWidth(getText(mc)) + 9
                    + mc.textRenderer.getWidth(fpsStr(mc)) + 9
                    + mc.textRenderer.getWidth(pingStr(mc)) + 9
                    + mc.textRenderer.getWidth(timeStr()) + 6;
            height = 15;
        }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            int accB = NeonButton.getHudAccentBright(0);
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, glassBorder());

            NeonButton.drawCircle(ctx, x + 8, y + 7, 3, accB);
            NeonButton.drawCircle(ctx, x + 8, y + 7, 1, 0xFFFFFFFF);

            int cx = x + 15, ty = y + 4;
            String name = getText(mc), fps = fpsStr(mc), ping = pingStr(mc), time = timeStr();

            ctx.drawText(mc.textRenderer, name, cx, ty, 0xFFF2F2FA, false);
            cx += mc.textRenderer.getWidth(name) + 4;
            ctx.fill(cx, y + 4, cx + 1, y + height - 4, 0x28FFFFFF);
            cx += 5;

            ctx.drawText(mc.textRenderer, fps, cx, ty, 0xFFE2E2F0, false);
            cx += mc.textRenderer.getWidth(fps) + 4;
            ctx.fill(cx, y + 4, cx + 1, y + height - 4, 0x28FFFFFF);
            cx += 5;

            ctx.drawText(mc.textRenderer, ping, cx, ty, accB, false);
            cx += mc.textRenderer.getWidth(ping) + 4;
            ctx.fill(cx, y + 4, cx + 1, y + height - 4, 0x28FFFFFF);
            cx += 5;

            ctx.drawText(mc.textRenderer, time, cx, ty, 0xFFB8B8CE, false);
        }

        @Override protected int defaultX() {
            measure(MinecraftClient.getInstance());
            int scaledW = Math.round(width * ArtemConfig.get().hudScale);
            return Math.max(8, (screenW() - scaledW) / 2);
        }
        @Override protected int defaultY() { return 6; }
    }

    // Таймер ивента (под верхней панелью)
    static class EventTimerElement extends HudElement {
        EventTimerElement() { super("event_timer"); }
        @Override public boolean isEnabled(ArtemConfig c) {
            return c.showEventTimer || c.eventEndTimeMs > System.currentTimeMillis();
        }
        @Override public String getText(MinecraftClient mc) {
            ArtemConfig c = ArtemConfig.get();
            long rem = Math.max(0L, c.eventEndTimeMs - System.currentTimeMillis());
            long sec = rem / 1000L;
            return c.eventName + " " + String.format("%02d:%02d", sec / 60, sec % 60);
        }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }
        @Override protected int defaultX() {
            measure(MinecraftClient.getInstance());
            int scaledW = Math.round(width * ArtemConfig.get().hudScale);
            return Math.max(8, (screenW() - scaledW) / 2);
        }
        @Override protected int defaultY() { return 21; }
    }

    // 2. Мини-инвентарь в верхнем левом углу
    static class InventoryElement extends HudElement {
        InventoryElement() { super("inventory"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showInventory; }
        @Override public String getText(MinecraftClient mc) { return "Inventory"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }
        @Override protected void measure(MinecraftClient mc) { width = 104; height = 49; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            int accB = NeonButton.getHudAccentBright(0);
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, glassBorder());

            NeonButton.fillSmoothRounded(ctx, x + 5, y + 4, 6, 5, 1, accB);
            ctx.drawText(mc.textRenderer, "Inventory", x + 14, y + 3, 0xFFEAEAF6, false);
            ctx.fill(x + 4, y + 13, x + width - 4, y + 14, 0x18FFFFFF);

            if (mc.player == null) return;

            int startX = x + 4;
            int startY = y + 15;
            float itemScale = 0.65f;
            int cell = 11;

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slot = 9 + row * 9 + col;
                    int sx = startX + col * cell;
                    int sy = startY + row * cell;
                    ctx.fill(sx, sy, sx + 10, sy + 10, 0x16FFFFFF);

                    ItemStack st = mc.player.getInventory().getStack(slot);
                    if (!st.isEmpty()) {
                        ctx.getMatrices().push();
                        ctx.getMatrices().translate(sx, sy, 0);
                        ctx.getMatrices().scale(itemScale, itemScale, 1f);
                        ctx.drawItem(st, 0, 0);
                        ctx.drawItemInSlot(mc.textRenderer, st, 0, 0);
                        ctx.getMatrices().pop();
                    }
                }
            }
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 6; }
    }

    // 3. Эффекты зелий (слева под инвентарём)
    static class PotionsElement extends HudElement {
        PotionsElement() { super("potions"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPotions; }
        @Override public String getText(MinecraftClient mc) { return "Potion effects"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }

        private List<String[]> rows(MinecraftClient mc, ArtemConfig c) {
            List<String[]> list = new ArrayList<>();
            if (mc.player != null) {
                for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
                    String name = inst.getEffectType().value().getName().getString();
                    int amp = inst.getAmplifier();
                    if (amp == 1) name += " II";
                    else if (amp >= 2) name += " " + (amp + 1);
                    String time = (inst.isInfinite() || inst.getDuration() < 0) ? "∞"
                            : String.format("%d:%02d", (inst.getDuration() / 20) / 60, (inst.getDuration() / 20) % 60);
                    list.add(new String[]{name, time, String.valueOf(0xFF000000 | inst.getEffectType().value().getColor())});
                }
            }
            if (list.isEmpty() && c.hudEditMode) {
                list.add(new String[]{"Resistance II", "∞", String.valueOf(0xFF9D8DF7)});
                list.add(new String[]{"Speed II", "2:18", String.valueOf(0xFF70D6FF)});
            }
            return list;
        }

        @Override protected void measure(MinecraftClient mc) {
            List<String[]> list = rows(mc, ArtemConfig.get());
            if (list.isEmpty()) { width = 0; height = 0; return; }
            int maxW = 94;
            for (String[] r : list) {
                maxW = Math.max(maxW, 20 + mc.textRenderer.getWidth(r[0]) + mc.textRenderer.getWidth(r[1]));
            }
            width = maxW;
            height = 16 + list.size() * 10;
        }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String[]> list = rows(mc, c);
            if (list.isEmpty()) return;
            int accB = NeonButton.getHudAccentBright(0);
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, glassBorder());
            NeonButton.fillSmoothRounded(ctx, x + 5, y + 4, 4, 5, 1, accB);
            ctx.drawText(mc.textRenderer, "Potion effects", x + 12, y + 3, 0xFFEAEAF6, false);
            int ry = y + 15;
            for (String[] r : list) {
                NeonButton.drawCircle(ctx, x + 6, ry + 3, 2, Integer.parseInt(r[2]));
                ctx.drawText(mc.textRenderer, r[0], x + 11, ry, 0xFFE6E6F2, false);
                ctx.drawText(mc.textRenderer, r[1], x + width - mc.textRenderer.getWidth(r[1]) - 5, ry, 0xFF9A9AB2, false);
                ry += 10;
            }
        }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 52; }
    }

    // Виджет кулдаунов предметов (Хелпер)
    static class CooldownsElement extends HudElement {
        private static final Object[][] TRACKED = {
                {Items.ENDER_PEARL, "Жемчуг"},
                {Items.ENCHANTED_GOLDEN_APPLE, "Чарка"},
                {Items.GOLDEN_APPLE, "Гепл"},
                {Items.CHORUS_FRUIT, "Хорус"},
                {Items.SHIELD, "Щит"},
                {Items.WIND_CHARGE, "Заряд"},
                {Items.FIREWORK_ROCKET, "Фейерверк"}
        };

        CooldownsElement() { super("cooldowns"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showCooldowns; }
        @Override public String getText(MinecraftClient mc) { return "Cooldowns"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }

        private List<Object[]> active(MinecraftClient mc, ArtemConfig c) {
            List<Object[]> list = new ArrayList<>();
            if (mc.player != null) {
                for (Object[] entry : TRACKED) {
                    Item item = (Item) entry[0];
                    float prog = mc.player.getItemCooldownManager().getCooldownProgress(item, 0f);
                    if (prog > 0.01f) {
                        list.add(new Object[]{new ItemStack(item), entry[1], prog});
                    }
                }
            }
            if (list.isEmpty() && c.hudEditMode) {
                list.add(new Object[]{new ItemStack(Items.ENDER_PEARL), "Жемчуг", 0.65f});
                list.add(new Object[]{new ItemStack(Items.GOLDEN_APPLE), "Гепл", 0.35f});
            }
            return list;
        }

        @Override protected void measure(MinecraftClient mc) {
            List<Object[]> list = active(mc, ArtemConfig.get());
            if (list.isEmpty()) { width = 0; height = 0; return; }
            width = 94;
            height = 16 + list.size() * 11;
        }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<Object[]> list = active(mc, c);
            if (list.isEmpty()) return;
            int acc = NeonButton.getHudAccent(0);
            int accB = NeonButton.getHudAccentBright(0);
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, glassBorder());
            NeonButton.drawCircle(ctx, x + 7, y + 6, 2, accB);
            ctx.drawText(mc.textRenderer, "Cooldowns", x + 13, y + 3, 0xFFEAEAF6, false);

            int ry = y + 15;
            for (Object[] row : list) {
                ItemStack st = (ItemStack) row[0];
                String label = (String) row[1];
                float prog = (float) row[2];

                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + 4, ry - 1, 0);
                ctx.getMatrices().scale(0.62f, 0.62f, 1f);
                ctx.drawItem(st, 0, 0);
                ctx.getMatrices().pop();

                ctx.drawText(mc.textRenderer, label, x + 16, ry, 0xFFE6E6F2, false);
                int barX = x + 56;
                int barW = width - 61;
                NeonButton.fillSmoothRounded(ctx, barX, ry + 3, barW, 3, 1, 0xFF222232);
                int fillW = Math.max(2, Math.round(barW * prog));
                NeonButton.fillGradientRounded(ctx, barX, ry + 3, fillW, 3, 1, acc, accB);
                ry += 11;
            }
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 142; }
    }

    // 4. Keybinds (слева по центру)
    static class HotKeysElement extends HudElement {
        HotKeysElement() { super("hotkeys"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showHotKeys; }
        @Override public String getText(MinecraftClient mc) { return "Keybinds"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }

        private List<String[]> binds(ArtemConfig c) {
            List<String[]> list = new ArrayList<>();
            list.add(new String[]{"ClickGUI", "RSHIFT"});
            list.add(new String[]{"Zoom", "C"});
            if (c.ahSearchHandItem) list.add(new String[]{"AH Search", "H"});
            for (Map.Entry<String, Integer> e : c.keybinds.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    String keyName = InputUtil.Type.KEYSYM.createFromCode(e.getValue()).getLocalizedText().getString().toUpperCase();
                    list.add(new String[]{e.getKey(), keyName});
                }
            }
            return list;
        }

        @Override protected void measure(MinecraftClient mc) {
            int rows = Math.min(4, binds(ArtemConfig.get()).size());
            width = 94;
            height = 16 + rows * 10;
        }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String[]> list = binds(c);
            int accB = NeonButton.getHudAccentBright(0);
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, glassBorder());
            NeonButton.fillSmoothRounded(ctx, x + 5, y + 4, 5, 5, 1, accB);
            ctx.drawText(mc.textRenderer, "Keybinds", x + 13, y + 3, 0xFFEAEAF6, false);
            int ry = y + 15;
            for (int i = 0; i < Math.min(4, list.size()); i++) {
                String[] b = list.get(i);
                ctx.drawText(mc.textRenderer, b[0], x + 5, ry, 0xFFD4D4E6, false);
                ctx.drawText(mc.textRenderer, b[1], x + width - mc.textRenderer.getWidth(b[1]) - 5, ry, 0xFF9A9AB2, false);
                ry += 10;
            }
        }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 96; }
    }

    // 5. Мини-моделька игрока в левом нижнем углу
    static class PaperdollElement extends HudElement {
        PaperdollElement() { super("paperdoll"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPaperdoll; }
        @Override public String getText(MinecraftClient mc) { return "Player"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }
        @Override protected void measure(MinecraftClient mc) { width = 38; height = 52; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.player == null) return;
            if (c.hudEditMode) {
                NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            }
            try {
                InventoryScreen.drawEntity(
                        ctx,
                        x, y,
                        x + width, y + height,
                        22,
                        0.0625f,
                        (float) (x + width / 2.0f - 14.0f),
                        (float) (y + 12.0f),
                        mc.player
                );
            } catch (Throwable ignored) {
            }
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return Math.max(120, screenH() - 56); }
    }

    // 6. TargetHUD (в верхнем правом углу + подсветка друзей)
    static class TargetHudElement extends HudElement {
        private float smoothHp = 20f;
        TargetHudElement() { super("target"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showTargetHud; }
        @Override public String getText(MinecraftClient mc) { return "TargetHUD"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccent(0); }

        private LivingEntity target(MinecraftClient mc, ArtemConfig c) {
            if (mc.targetedEntity instanceof LivingEntity le && le.isAlive()) return le;
            if (mc.player != null) {
                LivingEntity last = mc.player.getAttacking();
                if (last != null && last.isAlive() && (mc.player.age - mc.player.getLastAttackTime() < 80)) return last;
                if (c.hudEditMode) return mc.player;
            }
            return null;
        }

        @Override protected void measure(MinecraftClient mc) {
            if (target(mc, ArtemConfig.get()) == null) { width = 0; height = 0; return; }
            width = 104; height = 25;
        }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            LivingEntity t = target(mc, c);
            if (t == null) return;
            float hp = Math.max(0f, t.getHealth()), maxHp = Math.max(1f, t.getMaxHealth());
            smoothHp += (hp - smoothHp) * 0.25f;
            float ratio = Math.min(1f, Math.max(0f, smoothHp / maxHp));

            String rawName = t.getName().getString();
            boolean isFriend = (t instanceof PlayerEntity) && c.highlightFriends && c.isFriend(rawName);
            int acc = isFriend ? c.friendColor : NeonButton.getHudAccent(0);
            int accB = isFriend ? c.friendColor : NeonButton.getHudAccentBright(0);

            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5,
                    isFriend ? ((c.friendColor & 0x00FFFFFF) | 0x88000000) : glassBorder());
            int ax = x + 3, ay = y + 3, av = 19;
            NeonButton.fillSmoothRounded(ctx, ax, ay, av, av, 3, 0xFF1A1A28);

            if (t instanceof AbstractClientPlayerEntity p) {
                PlayerSkinDrawer.draw(ctx, p.getSkinTextures().texture(), ax + 1, ay + 1, av - 2);
            } else {
                String ini = rawName.isEmpty() ? "?" : rawName.substring(0, 1).toUpperCase();
                ctx.drawText(mc.textRenderer, ini, ax + 6, ay + 5, accB, false);
            }
            String name = (isFriend ? "★ " : "") + rawName;
            if (name.length() > 12) name = name.substring(0, 11) + "…";
            int tx = ax + av + 5;
            ctx.drawText(mc.textRenderer, name, tx, y + 3, isFriend ? c.friendColor : 0xFFF2F2FA, false);
            ctx.drawText(mc.textRenderer, String.format("%.1f HP", hp), tx, y + 12, accB, false);

            int barW = width - (tx - x) - 4;
            NeonButton.fillSmoothRounded(ctx, tx, y + 21, barW, 2, 1, 0xFF222232);
            int fillW = Math.round(barW * ratio);
            if (fillW > 1) NeonButton.fillGradientRounded(ctx, tx, y + 21, fillW, 2, 1, acc, accB);
        }
        @Override protected int defaultX() {
            int scaledW = Math.round(104 * ArtemConfig.get().hudScale);
            return Math.max(6, screenW() - scaledW - 6);
        }
        @Override protected int defaultY() { return 6; }
    }

    // 7. Броня с реальной прочностью
    static class ArmorElement extends HudElement {
        ArmorElement() { super("armor"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showArmor; }
        @Override public String getText(MinecraftClient mc) { return "Armor"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }

        @Override protected void measure(MinecraftClient mc) {
            if (mc.player == null) { width = 0; height = 0; return; }
            int count = 0;
            for (int s = 3; s >= 0; s--) {
                if (!mc.player.getInventory().getArmorStack(s).isEmpty()) count++;
            }
            if (count == 0 && !ArtemConfig.get().hudEditMode) {
                width = 0;
                height = 0;
                return;
            }
            int slots = Math.max(1, count);
            width = 6 + slots * 18;
            height = ArtemConfig.get().armorDurabilityPercent ? 26 : 21;
        }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.player == null) return;
            List<ItemStack> pieces = new ArrayList<>(4);
            for (int s = 3; s >= 0; s--) {
                ItemStack st = mc.player.getInventory().getArmorStack(s);
                if (!st.isEmpty()) pieces.add(st);
            }
            if (pieces.isEmpty()) {
                if (!c.hudEditMode) return;
                NeonButton.fillGradientRounded(ctx, x, y, width, height, 4, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
                NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 4, glassBorder());
                ctx.drawText(mc.textRenderer, "ARM", x + 4, y + 6, 0xFF9A9AB2, false);
                return;
            }

            NeonButton.fillGradientRounded(ctx, x, y, width, height, 4, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 4, glassBorder());

            int ix = x + 4;
            for (ItemStack st : pieces) {
                ctx.drawItem(st, ix, y + 1);
                ctx.drawItemInSlot(mc.textRenderer, st, ix, y + 1);

                if (st.isDamageable() && st.getMaxDamage() > 0) {
                    float ratio = Math.max(0f, Math.min(1f, (st.getMaxDamage() - st.getDamage()) / (float) st.getMaxDamage()));
                    int durCol = 0xFF000000 | (Color.HSBtoRGB(ratio / 3.0f, 0.85f, 0.96f) & 0x00FFFFFF);
                    int bw = Math.max(1, Math.round(14 * ratio));
                    ctx.fill(ix + 1, y + 17, ix + 15, y + 19, 0xCC0A0A12);
                    ctx.fill(ix + 1, y + 17, ix + 1 + bw, y + 19, durCol);

                    if (c.armorDurabilityPercent) {
                        String pct = Math.round(ratio * 100f) + "%";
                        NeonButton.drawSmallText(ctx, mc.textRenderer, pct, ix + 1, y + 20, durCol, 0.65f);
                    }
                }
                ix += 18;
            }
        }

        @Override protected int defaultX() { return screenW() / 2 + 95; }
        @Override protected int defaultY() { return screenH() - 22; }
    }

    static class ArrayListElement extends HudElement {
        ArrayListElement() { super("arraylist"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showArrayList; }
        @Override public String getText(MinecraftClient mc) { return "ArrayList"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }

        private List<String> mods(ArtemConfig c) {
            List<String> l = new ArrayList<>();
            if (c.autoSprint) l.add("AutoSprint");
            if (c.smallSword) l.add("SmallSword");
            if (c.esp) l.add("ESP"); if (c.playerHighlight) l.add("PlayerHighlight");
            if (c.tracers) l.add("Tracers"); if (c.chams) l.add("Chams");
            if (c.nameTags) l.add("NameTags"); if (c.itemEsp) l.add("ItemESP");
            if (c.mobEsp) l.add("MobESP"); if (c.hitParticles) l.add("HitParticles");
            if (c.damageIndicator) l.add("DamageIndicator"); if (c.targetEsp) l.add("TargetESP");
            if (c.jumpCircle) l.add("JumpCircle"); if (c.trails) l.add("Trails"); if (c.nimb) l.add("Nimb");
            l.sort(Comparator.comparingInt(String::length).reversed());
            return l;
        }

        @Override protected void measure(MinecraftClient mc) { width = 84; height = Math.max(12, Math.min(8, mods(ArtemConfig.get()).size()) * 10); }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String> list = mods(c);
            int ry = y;
            for (int i = 0; i < Math.min(8, list.size()); i++) {
                String m = list.get(i);
                int tw = mc.textRenderer.getWidth(m) + 8, rx = x + width - tw;
                int col = NeonButton.getHudAccentBright(i * 220);
                NeonButton.fillSmoothRounded(ctx, rx, ry, tw, 10, 3, NeonButton.HUD_BG_TOP);
                ctx.fill(rx + tw - 2, ry + 1, rx + tw, ry + 9, col);
                ctx.drawText(mc.textRenderer, m, rx + 3, ry + 1, 0xFFF2F2FA, false);
                ry += 10;
            }
        }
        @Override protected int defaultX() { return screenW() - 75; }
        @Override protected int defaultY() { return 36; }
    }

    static class FpsElement extends HudElement {
        FpsElement() { super("fps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showFps; }
        @Override public String getText(MinecraftClient mc) { return "FPS " + mc.getCurrentFps(); }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(0); }
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return 144; }
    }

    static class PingElement extends HudElement {
        PingElement() { super("ping"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPing; }
        @Override public String getText(MinecraftClient mc) { int p = queryPing(mc); return "PING " + (p >= 0 ? p : 0) + "ms"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(100); }
        @Override protected int defaultX() { return 54; } @Override protected int defaultY() { return 144; }
    }

    static class XyzElement extends HudElement {
        XyzElement() { super("xyz"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showXyz; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "XYZ 0 0 0";
            BlockPos p = mc.player.getBlockPos();
            return String.format("XYZ %d %d %d", p.getX(), p.getY(), p.getZ());
        }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(200); }
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return 158; }
    }

    static class SpeedElement extends HudElement {
        SpeedElement() { super("speed"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showSpeed; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "BPS 0.00";
            return String.format("BPS %.2f", Math.hypot(mc.player.getX() - mc.player.prevX, mc.player.getZ() - mc.player.prevZ) * 20.0);
        }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(300); }
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return 172; }
    }

    static class CpsElement extends HudElement {
        CpsElement() { super("cps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showCps; }
        @Override public String getText(MinecraftClient mc) { return "CPS " + currentLmbCps() + "|" + currentRmbCps(); }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(400); }
        @Override protected int defaultX() { return 64; } @Override protected int defaultY() { return 172; }
    }

    static class HealthElement extends HudElement {
        HealthElement() { super("health_hud"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showHealthHud; }
        @Override public String getText(MinecraftClient mc) { return mc.player == null ? "HP 20.0" : String.format("HP %.1f", mc.player.getHealth()); }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(500); }
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return 186; }
    }

    static class HungerElement extends HudElement {
        HungerElement() { super("hunger_hud"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showHungerHud; }
        @Override public String getText(MinecraftClient mc) { return mc.player == null ? "FOOD 20" : "FOOD " + mc.player.getHungerManager().getFoodLevel(); }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(600); }
        @Override protected int defaultX() { return 56; } @Override protected int defaultY() { return 186; }
    }

    static class DirectionElement extends HudElement {
        DirectionElement() { super("facing"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showDirection; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "DIR S";
            String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            return "DIR " + dirs[Math.round(((MathHelper.wrapDegrees(mc.player.getYaw()) + 360f) % 360f) / 45f) % 8];
        }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(700); }
        @Override protected int defaultX() { return 106; } @Override protected int defaultY() { return 186; }
    }

    static class NicknameElement extends HudElement {
        NicknameElement() { super("nick"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showNickname; }
        @Override public String getText(MinecraftClient mc) { return "USER " + (mc.player != null ? mc.player.getName().getString() : "Player"); }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(800); }
        @Override protected int defaultX() { return 64; } @Override protected int defaultY() { return 200; }
    }

    static class PlaytimeElement extends HudElement {
        PlaytimeElement() { super("playtime"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPlaytime; }
        @Override public String getText(MinecraftClient mc) {
            long sec = (System.currentTimeMillis() - SESSION_START_MS) / 1000L;
            return String.format("TIME %02d:%02d", sec / 60, sec % 60);
        }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccentBright(900); }
        @Override protected int defaultX() { return 64; } @Override protected int defaultY() { return 214; }
    }

    private static void drawKeyBox(DrawContext ctx, MinecraftClient mc, int kx, int ky, int kw, int kh, String label, boolean on) {
        int acc = NeonButton.getHudAccent(0);
        int accB = NeonButton.getHudAccentBright(0);
        if (on) {
            NeonButton.fillGradientRounded(ctx, kx, ky, kw, kh, 3, accB, acc);
            NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, kw, kh, 3, 0x88FFFFFF);
        } else {
            NeonButton.fillGradientRounded(ctx, kx, ky, kw, kh, 3, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, kw, kh, 3, 0x22FFFFFF);
        }
        NeonButton.drawSmallText(ctx, mc.textRenderer, label,
                kx + (kw - mc.textRenderer.getWidth(label) * 0.85f) / 2f,
                ky + (kh - 7) / 2f,
                on ? 0xFFFFFFFF : accB, 0.85f);
    }

    static class WasdElement extends HudElement {
        WasdElement() { super("wasd"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showWasd && !c.showKeystrokes; }
        @Override public String getText(MinecraftClient mc) { return "WASD"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccent(0); }
        @Override protected void measure(MinecraftClient mc) { width = 46; height = 30; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.options == null) return;
            int ks = 14, g = 2;
            drawKeyBox(ctx, mc, x + ks + g, y, ks, ks, "W", mc.options.forwardKey.isPressed());
            drawKeyBox(ctx, mc, x, y + ks + g, ks, ks, "A", mc.options.leftKey.isPressed());
            drawKeyBox(ctx, mc, x + ks + g, y + ks + g, ks, ks, "S", mc.options.backKey.isPressed());
            drawKeyBox(ctx, mc, x + (ks + g) * 2, y + ks + g, ks, ks, "D", mc.options.rightKey.isPressed());
        }
        @Override protected int defaultX() { return 52; } @Override protected int defaultY() { return screenH() - 34; }
    }

    static class KeystrokesElement extends HudElement {
        KeystrokesElement() { super("keystrokes"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showKeystrokes; }
        @Override public String getText(MinecraftClient mc) { return "Keystrokes"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccent(0); }
        @Override protected void measure(MinecraftClient mc) { width = 50; height = 54; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.options == null) return;
            int ks = 15, g = 2;
            drawKeyBox(ctx, mc, x + ks + g, y, ks, ks, "W", mc.options.forwardKey.isPressed());
            drawKeyBox(ctx, mc, x, y + ks + g, ks, ks, "A", mc.options.leftKey.isPressed());
            drawKeyBox(ctx, mc, x + ks + g, y + ks + g, ks, ks, "S", mc.options.backKey.isPressed());
            drawKeyBox(ctx, mc, x + (ks + g) * 2, y + ks + g, ks, ks, "D", mc.options.rightKey.isPressed());
            int r3 = y + (ks + g) * 2;
            drawKeyBox(ctx, mc, x, r3, 24, 10, "LMB", mc.options.attackKey.isPressed());
            drawKeyBox(ctx, mc, x + 26, r3, 24, 10, "RMB", mc.options.useKey.isPressed());
            drawKeyBox(ctx, mc, x, r3 + 12, 50, 9, "SPACE", mc.options.jumpKey.isPressed());
        }
        @Override protected int defaultX() { return 52; } @Override protected int defaultY() { return screenH() - 54; }
    }

    static class RadarElement extends HudElement {
        RadarElement() { super("radar"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showRadar; }
        @Override public String getText(MinecraftClient mc) { return "Radar"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccent(0); }
        @Override protected void measure(MinecraftClient mc) { width = 48; height = 48; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            int accB = NeonButton.getHudAccentBright(0);
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, glassBorder());
            int cx = x + width / 2, cy = y + height / 2;
            ctx.fill(cx, y + 3, cx + 1, y + height - 3, 0x18FFFFFF);
            ctx.fill(x + 3, cy, x + width - 3, cy + 1, 0x18FFFFFF);
            NeonButton.drawCircle(ctx, cx, cy, 1, 0xFFFFFFFF);
            if (mc.world == null || mc.player == null) return;
            double rad = Math.toRadians(mc.player.getYaw() + 180.0), cos = Math.cos(rad), sin = Math.sin(rad);
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
                if (dx * dx + dz * dz > 48 * 48) continue;
                int px = MathHelper.clamp((int) Math.round(cx + (dx * cos - dz * sin) * 0.40), x + 3, x + width - 4);
                int py = MathHelper.clamp((int) Math.round(cy + (dx * sin + dz * cos) * 0.40), y + 3, y + height - 4);
                int col = (e instanceof PlayerEntity pe)
                        ? (c.highlightFriends && c.isFriend(pe.getName().getString()) ? c.friendColor : accB)
                        : (e instanceof MobEntity) ? 0xFFFFB703 : (e instanceof ItemEntity) ? 0xFF00F5FF : 0;
                if (col != 0) NeonButton.drawCircle(ctx, px, py, 1, col);
            }
        }
        @Override protected int defaultX() { return screenW() - 46; } @Override protected int defaultY() { return screenH() - 68; }
    }

    static class MinimapElement extends HudElement {
        MinimapElement() { super("minimap"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showMinimap; }
        @Override public String getText(MinecraftClient mc) { return "MiniMap"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.getHudAccent(0); }
        @Override protected void measure(MinecraftClient mc) { width = 52; height = 52; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            int accB = NeonButton.getHudAccentBright(0);
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.HUD_BG_TOP, NeonButton.HUD_BG_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, glassBorder());
            int ox = x + 2, oy = y + 2;
            for (int rz = 0; rz < MAP_SIZE; rz++) {
                for (int rx = 0; rx < MAP_SIZE; rx++) {
                    int col = MINIMAP_COLORS[rz][rx];
                    if (col != 0) ctx.fill(ox + rx * 2, oy + rz * 2, ox + (rx + 1) * 2, oy + (rz + 1) * 2, col);
                }
            }
            int cx = x + width / 2, cy = y + height / 2;
            NeonButton.drawCircle(ctx, cx, cy, 2, accB);
            NeonButton.drawCircle(ctx, cx, cy, 1, 0xFFFFFFFF);
            ctx.drawText(mc.textRenderer, "N", cx - 2, y + 3, accB, false);
        }
        @Override protected int defaultX() { return screenW() - 96; } @Override protected int defaultY() { return screenH() - 70; }
    }
}
