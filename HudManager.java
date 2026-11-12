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

import java.util.*;

/**
 * HUD в стиле PulseVisual: чёткая сетка без наложений и кастомный цвет окна.
 */
public final class HudManager {
    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static final long SESSION_START_MS = System.currentTimeMillis();
    private static long lastAttackMs = 0;

    private static final long[] lmbTimestamps = new long[64];
    private static final long[] rmbTimestamps = new long[64];
    private static int lmbIdx = 0, rmbIdx = 0;
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
        c.showServer = true; c.showTargetHud = true; c.showHotKeys = true;
        c.showPotions = true; c.showArmor = true;
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
        c.crosshairStyle = 0; c.crosshairSize = 4; c.crosshairGap = 2; c.crosshairThickness = 1;
        c.crosshairOutline = true; c.crosshairDynamic = true; c.crosshairColor = 0xFF00F5FF;
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

    static class TopBarElement extends HudElement {
        TopBarElement() { super("server"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showServer; }
        @Override public String getText(MinecraftClient mc) {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null && info.address != null) {
                return info.address.toLowerCase().contains("funtime") ? "mc.funtime.su" : info.address;
            }
            return "artemvisuals.pro";
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFE8E8F4; }
        private String pingStr(MinecraftClient mc) { int p = queryPing(mc); return (p >= 0 ? p : 0) + " ms"; }
        private String fpsStr(MinecraftClient mc) { return mc.getCurrentFps() + " FPS"; }

        @Override protected void measure(MinecraftClient mc) {
            width = 18 + mc.textRenderer.getWidth(getText(mc)) + 11
                    + mc.textRenderer.getWidth(pingStr(mc)) + 11
                    + mc.textRenderer.getWidth(fpsStr(mc)) + 8;
            height = 16;
        }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, 0x22FFFFFF);
            int wx = x + 6, wy = y + 4;
            ctx.fill(wx, wy + 4, wx + 2, wy + 5, NeonButton.ACCENT);
            ctx.fill(wx + 2, wy + 1, wx + 3, wy + 6, NeonButton.ACCENT_BRIGHT);
            ctx.fill(wx + 3, wy + 3, wx + 4, wy + 8, NeonButton.ACCENT);
            ctx.fill(wx + 4, wy + 4, wx + 6, wy + 5, NeonButton.ACCENT);

            int cx = x + 16, ty = y + 4;
            String srv = getText(mc), ping = pingStr(mc), fps = fpsStr(mc);
            ctx.drawText(mc.textRenderer, srv, cx, ty, 0xFFF2F2FA, false);
            cx += mc.textRenderer.getWidth(srv) + 5;
            ctx.fill(cx, y + 4, cx + 1, y + height - 4, 0x28FFFFFF);
            cx += 6;
            ctx.drawText(mc.textRenderer, ping, cx, ty, NeonButton.ACCENT_BRIGHT, false);
            cx += mc.textRenderer.getWidth(ping) + 5;
            ctx.fill(cx, y + 4, cx + 1, y + height - 4, 0x28FFFFFF);
            cx += 6;
            ctx.drawText(mc.textRenderer, fps, cx, ty, 0xFFF2F2FA, false);
        }

        @Override protected int defaultX() { measure(MinecraftClient.getInstance()); return (screenW() - width) / 2; }
        @Override protected int defaultY() { return 6; }
    }

    static class HotKeysElement extends HudElement {
        HotKeysElement() { super("hotkeys"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showHotKeys; }
        @Override public String getText(MinecraftClient mc) { return "Hot Keys"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }

        private List<String[]> binds(ArtemConfig c) {
            List<String[]> list = new ArrayList<>();
            list.add(new String[]{"ClickGUI", "RSHIFT"});
            if (c.jumpCircle) list.add(new String[]{"Jump Circle", "ON"});
            if (c.targetEsp) list.add(new String[]{"Target ESP", "ON"});
            if (c.trails) list.add(new String[]{"Trails", "ON"});
            return list;
        }

        @Override protected void measure(MinecraftClient mc) { width = 104; height = 18 + Math.min(3, binds(ArtemConfig.get()).size()) * 11; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String[]> list = binds(c);
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, 0x1EFFFFFF);
            NeonButton.fillSmoothRounded(ctx, x + 6, y + 5, 7, 6, 1, NeonButton.ACCENT_BRIGHT);
            ctx.drawText(mc.textRenderer, "Hot Keys", x + 17, y + 4, 0xFFE8E8F4, false);
            int ry = y + 17;
            for (int i = 0; i < Math.min(3, list.size()); i++) {
                String[] b = list.get(i);
                ctx.drawText(mc.textRenderer, b[0], x + 7, ry, 0xFFCCCCE0, false);
                ctx.drawText(mc.textRenderer, b[1], x + width - mc.textRenderer.getWidth(b[1]) - 7, ry, 0xFF8E8EA8, false);
                ry += 11;
            }
        }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 6; }
    }

    static class TargetHudElement extends HudElement {
        private float smoothHp = 20f;
        TargetHudElement() { super("target"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showTargetHud; }
        @Override public String getText(MinecraftClient mc) { return "TargetHUD"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }

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
            width = 118; height = 28;
        }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            LivingEntity t = target(mc, c);
            if (t == null) return;
            float hp = Math.max(0f, t.getHealth()), maxHp = Math.max(1f, t.getMaxHealth());
            smoothHp += (hp - smoothHp) * 0.25f;
            float ratio = Math.min(1f, Math.max(0f, smoothHp / maxHp));

            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, 0x22FFFFFF);
            int ax = x + 4, ay = y + 4, av = 20;
            NeonButton.fillSmoothRounded(ctx, ax, ay, av, av, 3, 0xFF1A1A28);

            String name = t.getName().getString();
            if (t instanceof AbstractClientPlayerEntity p) {
                PlayerSkinDrawer.draw(ctx, p.getSkinTextures().texture(), ax + 1, ay + 1, av - 2);
            } else {
                String ini = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
                ctx.drawText(mc.textRenderer, ini, ax + 7, ay + 6, NeonButton.ACCENT_BRIGHT, false);
            }
            if (name.length() > 14) name = name.substring(0, 13) + "…";
            int tx = ax + av + 6;
            ctx.drawText(mc.textRenderer, name, tx, y + 4, 0xFFF2F2FA, false);
            ctx.drawText(mc.textRenderer, String.format("HP / %.1f", hp), tx, y + 14, 0xFF8E8EA6, false);

            int barW = width - (tx - x) - 5;
            NeonButton.fillSmoothRounded(ctx, tx, y + 23, barW, 2, 1, 0xFF222232);
            int fillW = Math.round(barW * ratio);
            if (fillW > 1) NeonButton.fillGradientRounded(ctx, tx, y + 23, fillW, 2, 1, NeonButton.ACCENT, NeonButton.ACCENT_BRIGHT);
        }
        @Override protected int defaultX() { return screenW() - 124; }
        @Override protected int defaultY() { return 6; }
    }

    static class PotionsElement extends HudElement {
        PotionsElement() { super("potions"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPotions; }
        @Override public String getText(MinecraftClient mc) { return "Potions"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }

        private List<String[]> rows(MinecraftClient mc, ArtemConfig c) {
            List<String[]> list = new ArrayList<>();
            if (mc.player != null) {
                for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
                    String name = inst.getEffectType().value().getName().getString();
                    int amp = inst.getAmplifier();
                    if (amp == 1) name += " II"; else if (amp >= 2) name += " " + (amp + 1);
                    String time = (inst.isInfinite() || inst.getDuration() < 0) ? "∞"
                            : String.format("%d:%02d", (inst.getDuration() / 20) / 60, (inst.getDuration() / 20) % 60);
                    list.add(new String[]{name, time, String.valueOf(0xFF000000 | inst.getEffectType().value().getColor())});
                }
            }
            if (list.isEmpty() && c.hudEditMode) {
                list.add(new String[]{"Strength II", "2:19", String.valueOf(0xFFFF5E7E)});
                list.add(new String[]{"Speed II", "2:18", String.valueOf(0xFF70D6FF)});
            }
            return list;
        }

        @Override protected void measure(MinecraftClient mc) {
            List<String[]> list = rows(mc, ArtemConfig.get());
            if (list.isEmpty()) { width = 0; height = 0; return; }
            int maxW = 104;
            for (String[] r : list) maxW = Math.max(maxW, 26 + mc.textRenderer.getWidth(r[0]) + mc.textRenderer.getWidth(r[1]));
            width = maxW; height = 18 + list.size() * 11;
        }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String[]> list = rows(mc, c);
            if (list.isEmpty()) return;
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, 0x1EFFFFFF);
            NeonButton.fillSmoothRounded(ctx, x + 6, y + 5, 5, 6, 1, NeonButton.ACCENT_BRIGHT);
            ctx.drawText(mc.textRenderer, "Potions", x + 15, y + 4, 0xFFE8E8F4, false);
            int ry = y + 17;
            for (String[] r : list) {
                NeonButton.drawCircle(ctx, x + 8, ry + 4, 2, Integer.parseInt(r[2]));
                ctx.drawText(mc.textRenderer, r[0], x + 14, ry, 0xFFE6E6F2, false);
                ctx.drawText(mc.textRenderer, r[1], x + width - mc.textRenderer.getWidth(r[1]) - 7, ry, 0xFF8E8EA6, false);
                ry += 11;
            }
        }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 64; }
    }

    static class ArmorElement extends HudElement {
        ArmorElement() { super("armor"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showArmor; }
        @Override public String getText(MinecraftClient mc) { return "Armor"; }
        @Override public int getColor(MinecraftClient mc) { return 0xFFF2F2FA; }
        @Override protected void measure(MinecraftClient mc) { width = 76; height = 20; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.player == null) return;
            boolean any = false;
            for (ItemStack st : mc.player.getArmorItems()) if (!st.isEmpty()) { any = true; break; }
            if (!any && !c.hudEditMode) return;
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, 0x1EFFFFFF);
            int ix = x + width - 19;
            for (ItemStack st : mc.player.getArmorItems()) { if (!st.isEmpty()) ctx.drawItem(st, ix, y + 2); ix -= 18; }
        }
        @Override protected int defaultX() { return screenW() / 2 + 96; }
        @Override protected int defaultY() { return screenH() - 24; }
    }

    static class ArrayListElement extends HudElement {
        ArrayListElement() { super("arraylist"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showArrayList; }
        @Override public String getText(MinecraftClient mc) { return "ArrayList"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }

        private List<String> mods(ArtemConfig c) {
            List<String> l = new ArrayList<>();
            if (c.esp) l.add("ESP"); if (c.playerHighlight) l.add("PlayerHighlight");
            if (c.tracers) l.add("Tracers"); if (c.chams) l.add("Chams");
            if (c.nameTags) l.add("NameTags"); if (c.itemEsp) l.add("ItemESP");
            if (c.mobEsp) l.add("MobESP"); if (c.hitParticles) l.add("HitParticles");
            if (c.damageIndicator) l.add("DamageIndicator"); if (c.targetEsp) l.add("TargetESP");
            if (c.jumpCircle) l.add("JumpCircle"); if (c.trails) l.add("Trails"); if (c.nimb) l.add("Nimb");
            l.sort(Comparator.comparingInt(String::length).reversed());
            return l;
        }

        @Override protected void measure(MinecraftClient mc) { width = 96; height = Math.max(14, Math.min(8, mods(ArtemConfig.get()).size()) * 12); }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String> list = mods(c);
            int ry = y;
            for (int i = 0; i < Math.min(8, list.size()); i++) {
                String m = list.get(i);
                int tw = mc.textRenderer.getWidth(m) + 10, rx = x + width - tw;
                int col = NeonButton.getDynamicColor(NeonButton.ACCENT_BRIGHT, i * 240);
                NeonButton.fillSmoothRounded(ctx, rx, ry, tw, 11, 3, NeonButton.WINDOW_TOP);
                ctx.fill(rx + tw - 2, ry + 1, rx + tw, ry + 10, col);
                ctx.drawText(mc.textRenderer, m, rx + 4, ry + 2, 0xFFF2F2FA, false);
                ry += 12;
            }
        }
        @Override protected int defaultX() { return screenW() - 102; }
        @Override protected int defaultY() { return 42; }
    }

    static class FpsElement extends HudElement {
        FpsElement() { super("fps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showFps; }
        @Override public String getText(MinecraftClient mc) { return "FPS " + mc.getCurrentFps(); }
        @Override public int getColor(MinecraftClient mc) { return 0xFFF2F2FA; }
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return 112; }
    }

    static class PingElement extends HudElement {
        PingElement() { super("ping"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPing; }
        @Override public String getText(MinecraftClient mc) { int p = queryPing(mc); return "PING " + (p >= 0 ? p : 0) + "ms"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }
        @Override protected int defaultX() { return 62; } @Override protected int defaultY() { return 112; }
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
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return 131; }
    }

    static class SpeedElement extends HudElement {
        SpeedElement() { super("speed"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showSpeed; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "BPS 0.00";
            return String.format("BPS %.2f", Math.hypot(mc.player.getX() - mc.player.prevX, mc.player.getZ() - mc.player.prevZ) * 20.0);
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFF00F5FF; }
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return 150; }
    }

    static class CpsElement extends HudElement {
        CpsElement() { super("cps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showCps; }
        @Override public String getText(MinecraftClient mc) { return "CPS " + currentLmbCps() + " | " + currentRmbCps(); }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }
        @Override protected int defaultX() { return 76; } @Override protected int defaultY() { return 150; }
    }

    static class HealthElement extends HudElement {
        HealthElement() { super("health_hud"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showHealthHud; }
        @Override public String getText(MinecraftClient mc) { return mc.player == null ? "HP 20.0" : String.format("HP %.1f", mc.player.getHealth()); }
        @Override public int getColor(MinecraftClient mc) { return 0xFFFF5E7E; }
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return 169; }
    }

    static class HungerElement extends HudElement {
        HungerElement() { super("hunger_hud"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showHungerHud; }
        @Override public String getText(MinecraftClient mc) { return mc.player == null ? "FOOD 20" : "FOOD " + mc.player.getHungerManager().getFoodLevel(); }
        @Override public int getColor(MinecraftClient mc) { return 0xFFFFB703; }
        @Override protected int defaultX() { return 64; } @Override protected int defaultY() { return 169; }
    }

    static class DirectionElement extends HudElement {
        DirectionElement() { super("facing"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showDirection; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "DIR S";
            String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            return "DIR " + dirs[Math.round(((MathHelper.wrapDegrees(mc.player.getYaw()) + 360f) % 360f) / 45f) % 8];
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFE4E4F0; }
        @Override protected int defaultX() { return 124; } @Override protected int defaultY() { return 169; }
    }

    static class NicknameElement extends HudElement {
        NicknameElement() { super("nick"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showNickname; }
        @Override public String getText(MinecraftClient mc) { return "USER " + (mc.player != null ? mc.player.getName().getString() : "Player"); }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }
        @Override protected int defaultX() { return 76; } @Override protected int defaultY() { return 188; }
    }

    static class PlaytimeElement extends HudElement {
        PlaytimeElement() { super("playtime"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPlaytime; }
        @Override public String getText(MinecraftClient mc) {
            long sec = (System.currentTimeMillis() - SESSION_START_MS) / 1000L;
            return String.format("TIME %02d:%02d", sec / 60, sec % 60);
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFE4E4F0; }
        @Override protected int defaultX() { return 76; } @Override protected int defaultY() { return 207; }
    }

    private static void drawKeyBox(DrawContext ctx, MinecraftClient mc, int kx, int ky, int kw, int kh, String label, boolean on) {
        if (on) {
            NeonButton.fillGradientRounded(ctx, kx, ky, kw, kh, 4, NeonButton.ACCENT_BRIGHT, NeonButton.ACCENT);
            NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, kw, kh, 4, 0x88FFFFFF);
        } else {
            NeonButton.fillGradientRounded(ctx, kx, ky, kw, kh, 4, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, kw, kh, 4, 0x1EFFFFFF);
        }
        ctx.drawText(mc.textRenderer, label, kx + (kw - mc.textRenderer.getWidth(label)) / 2, ky + (kh - 8) / 2, on ? 0xFFFFFFFF : 0xFF9696AC, false);
    }

    static class WasdElement extends HudElement {
        WasdElement() { super("wasd"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showWasd && !c.showKeystrokes; }
        @Override public String getText(MinecraftClient mc) { return "WASD"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }
        @Override protected void measure(MinecraftClient mc) { width = 58; height = 38; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.options == null) return;
            int ks = 18, g = 2;
            drawKeyBox(ctx, mc, x + ks + g, y, ks, ks, "W", mc.options.forwardKey.isPressed());
            drawKeyBox(ctx, mc, x, y + ks + g, ks, ks, "A", mc.options.leftKey.isPressed());
            drawKeyBox(ctx, mc, x + ks + g, y + ks + g, ks, ks, "S", mc.options.backKey.isPressed());
            drawKeyBox(ctx, mc, x + (ks + g) * 2, y + ks + g, ks, ks, "D", mc.options.rightKey.isPressed());
        }
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return screenH() - 44; }
    }

    static class KeystrokesElement extends HudElement {
        KeystrokesElement() { super("keystrokes"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showKeystrokes; }
        @Override public String getText(MinecraftClient mc) { return "Keystrokes"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }
        @Override protected void measure(MinecraftClient mc) { width = 64; height = 70; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.options == null) return;
            int ks = 20, g = 2;
            drawKeyBox(ctx, mc, x + ks + g, y, ks, ks, "W", mc.options.forwardKey.isPressed());
            drawKeyBox(ctx, mc, x, y + ks + g, ks, ks, "A", mc.options.leftKey.isPressed());
            drawKeyBox(ctx, mc, x + ks + g, y + ks + g, ks, ks, "S", mc.options.backKey.isPressed());
            drawKeyBox(ctx, mc, x + (ks + g) * 2, y + ks + g, ks, ks, "D", mc.options.rightKey.isPressed());
            int r3 = y + (ks + g) * 2;
            drawKeyBox(ctx, mc, x, r3, 31, 13, "LMB", mc.options.attackKey.isPressed());
            drawKeyBox(ctx, mc, x + 33, r3, 31, 13, "RMB", mc.options.useKey.isPressed());
            drawKeyBox(ctx, mc, x, r3 + 15, 64, 12, "SPACE", mc.options.jumpKey.isPressed());
        }
        @Override protected int defaultX() { return 6; } @Override protected int defaultY() { return screenH() - 76; }
    }

    static class RadarElement extends HudElement {
        RadarElement() { super("radar"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showRadar; }
        @Override public String getText(MinecraftClient mc) { return "Radar"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }
        @Override protected void measure(MinecraftClient mc) { width = 62; height = 62; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 6, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 6, 0x26FFFFFF);
            int cx = x + width / 2, cy = y + height / 2;
            ctx.fill(cx, y + 4, cx + 1, y + height - 4, 0x18FFFFFF);
            ctx.fill(x + 4, cy, x + width - 4, cy + 1, 0x18FFFFFF);
            NeonButton.drawCircle(ctx, cx, cy, 2, 0xFFFFFFFF);
            if (mc.world == null || mc.player == null) return;
            double rad = Math.toRadians(mc.player.getYaw() + 180.0), cos = Math.cos(rad), sin = Math.sin(rad);
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
                if (dx * dx + dz * dz > 48 * 48) continue;
                int px = MathHelper.clamp((int) Math.round(cx + (dx * cos - dz * sin) * 0.52), x + 4, x + width - 5);
                int py = MathHelper.clamp((int) Math.round(cy + (dx * sin + dz * cos) * 0.52), y + 4, y + height - 5);
                int col = (e instanceof PlayerEntity) ? NeonButton.ACCENT_BRIGHT : (e instanceof MobEntity) ? 0xFFFFB703 : (e instanceof ItemEntity) ? 0xFF00F5FF : 0;
                if (col != 0) NeonButton.drawCircle(ctx, px, py, 1, col);
            }
        }
        @Override protected int defaultX() { return screenW() - 68; } @Override protected int defaultY() { return screenH() - 68; }
    }

    static class MinimapElement extends HudElement {
        MinimapElement() { super("minimap"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showMinimap; }
        @Override public String getText(MinecraftClient mc) { return "MiniMap"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }
        @Override protected void measure(MinecraftClient mc) { width = 68; height = 68; }

        @Override protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 6, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 6, 0x2EFFFFFF);
            int ox = x + 2, oy = y + 2;
            for (int rz = 0; rz < MAP_SIZE; rz++) {
                for (int rx = 0; rx < MAP_SIZE; rx++) {
                    int col = MINIMAP_COLORS[rz][rx];
                    if (col != 0) ctx.fill(ox + rx * 2, oy + rz * 2, ox + (rx + 1) * 2, oy + (rz + 1) * 2, col);
                }
            }
            int cx = x + width / 2, cy = y + height / 2;
            NeonButton.drawCircle(ctx, cx, cy, 2, NeonButton.ACCENT_BRIGHT);
            NeonButton.drawCircle(ctx, cx, cy, 1, 0xFFFFFFFF);
            ctx.drawText(mc.textRenderer, "N", cx - 2, y + 3, 0xFFFFFFFF, false);
        }
        @Override protected int defaultX() { return screenW() - 142; } @Override protected int defaultY() { return screenH() - 74; }
    }
}
