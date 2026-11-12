package com.artem.visuals.hud;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.effects.EffectManager;
import com.artem.visuals.gui.NeonButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * HUD в стиле PulseVisuals (1-й скриншот):
 * - Верхняя компактная пилюля мелким шрифтом: artemvisuals.pro | 56 ms | 171 FPS
 * - Hot Keys сверху слева
 * - TargetHUD сверху справа с головой скина и "HP / 10.4"
 * - Potions HUD и WASD
 */
public final class HudManager {
    public static boolean showPotions = true;
    public static boolean showTargetHud = true;
    public static boolean showHotKeys = true;

    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static long lastAttackMs = 0;
    private static final long[] clickTimestamps = new long[64];
    private static int ctsIdx = 0;

    static {
        ELEMENTS.add(new TopBarElement());
        ELEMENTS.add(new HotKeysElement());
        ELEMENTS.add(new TargetHudElement());
        ELEMENTS.add(new PotionsElement());
        ELEMENTS.add(new FpsElement());
        ELEMENTS.add(new PingElement());
        ELEMENTS.add(new XyzElement());
        ELEMENTS.add(new CpsElement());
        ELEMENTS.add(new WasdElement());
    }

    private HudManager() {}

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options.hudHidden) return;
        ArtemConfig c = ArtemConfig.get();
        for (HudElement e : ELEMENTS) {
            e.render(ctx, mc, c);
        }
    }

    public static void recordClick() {
        clickTimestamps[ctsIdx % clickTimestamps.length] = System.currentTimeMillis();
        ctsIdx++;
        lastAttackMs = System.currentTimeMillis();
    }

    public static int currentCps() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (long t : clickTimestamps) {
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

    public static void mouseReleased() {
        for (HudElement e : ELEMENTS) e.mouseReleased();
    }

    public static void mouseDragged(double mx, double my) {
        ArtemConfig c = ArtemConfig.get();
        for (HudElement e : ELEMENTS) e.mouseDragged(mx, my, c);
    }

    private static int queryPing(MinecraftClient mc) {
        ClientPlayNetworkHandler h = mc.getNetworkHandler();
        if (h == null || mc.player == null) return -1;
        PlayerListEntry e = h.getPlayerListEntry(mc.player.getUuid());
        return e != null ? e.getLatency() : -1;
    }

    /**
     * Верхняя центральная пилюля как на 1-м скриншоте: [Волна] artemvisuals.pro | 56 ms | 171 FPS
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
            float s = 0.78f;
            String srv = getText(mc);
            String ping = buildPing(mc);
            String fps = buildFps(mc);
            width = Math.round(16
                    + mc.textRenderer.getWidth(srv) * s + 9
                    + mc.textRenderer.getWidth(ping) * s + 9
                    + mc.textRenderer.getWidth(fps) * s + 7);
            height = 13;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            float s = 0.78f;
            NeonButton.fillGradientRounded(ctx, x, y, width, height, 4, 0xE011111B, 0xE0090910);
            NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 4, 0x22FFFFFF);

            // Миниатюрная иконка пульс-волны
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

            String ping = buildPing(mc);
            NeonButton.drawSmallText(ctx, mc.textRenderer, ping, cx, ty, 0xFFD0D0E4, s);
            cx += mc.textRenderer.getWidth(ping) * s + 4;

            ctx.fill(Math.round(cx), y + 3, Math.round(cx) + 1, y + height - 3, 0x28FFFFFF);
            cx += 5;

            String fps = buildFps(mc);
            NeonButton.drawSmallText(ctx, mc.textRenderer, fps, cx, ty, 0xFFF2F2FA, s);
        }

        @Override
        protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return 140;
            measure(mc);
            return (mc.getWindow().getScaledWidth() - width) / 2;
        }

        @Override protected int defaultY() { return 5; }
    }

    /**
     * Виджет Hot Keys сверху слева как на 1-м скриншоте.
     */
    static class HotKeysElement extends HudElement {
        HotKeysElement() { super("hotkeys"); }
        @Override public boolean isEnabled(ArtemConfig c) { return showHotKeys; }
        @Override public String getText(MinecraftClient mc) { return "Hot Keys"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }

        private List<String[]> activeBinds(ArtemConfig c) {
            List<String[]> list = new ArrayList<>();
            list.add(new String[]{"ClickGUI", "RSHIFT"});
            if (EffectManager.jumpCircle) list.add(new String[]{"Jump Circle", "ON"});
            if (EffectManager.targetEsp) list.add(new String[]{"Target ESP", "ON"});
            if (EffectManager.trails) list.add(new String[]{"Trails", "ON"});
            if (EffectManager.nimb) list.add(new String[]{"Nimb", "ON"});
            if (c.softLighting) list.add(new String[]{"Fullbright", "ON"});
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

            // Иконка клавиши + заголовок Hot Keys
            NeonButton.fillSmoothRounded(ctx, x + 5, y + 4, 6, 5, 1, NeonButton.ACCENT_BRIGHT);
            NeonButton.drawSmallText(ctx, mc.textRenderer, "Hot Keys", x + 14, y + 3.5f, 0xFFE4E4F2, s);

            int ry = y + 14;
            int shown = 0;
            for (String[] b : binds) {
                if (shown >= 4) break;
                NeonButton.drawSmallText(ctx, mc.textRenderer, b[0], x + 6, ry, 0xFFCCCCE0, s);
                float kw = mc.textRenderer.getWidth(b[1]) * s;
                NeonButton.drawSmallText(ctx, mc.textRenderer, b[1], x + width - kw - 6, ry, 0xFF8E8EA8, s);
                ry += 10;
                shown++;
            }
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 5; }
    }

    /**
     * TargetHUD сверху справа как на 1-м скриншоте: лицо игрока, ник, "HP / 10.4" мелким шрифтом и фиолетовая полоска.
     */
    static class TargetHudElement extends HudElement {
        private float smoothHp = 20f;

        TargetHudElement() { super("target"); }
        @Override public boolean isEnabled(ArtemConfig c) { return showTargetHud; }
        @Override public String getText(MinecraftClient mc) { return "TargetHUD"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }

        private LivingEntity resolveTarget(MinecraftClient mc, ArtemConfig c) {
            if (mc.targetedEntity instanceof LivingEntity le && le.isAlive()) {
                return le;
            }
            if (mc.player != null) {
                LivingEntity last = mc.player.getAttacking();
                if (last != null && last.isAlive() && (mc.player.age - mc.player.getLastAttackTime() < 80)) {
                    return last;
                }
                if (c.hudEditMode) {
                    return mc.player;
                }
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
            width = 104;
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

            // Полоска здоровья снизу справа как на 1-м скриншоте
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
            if (mc == null || mc.getWindow() == null) return 220;
            return mc.getWindow().getScaledWidth() - 110;
        }

        @Override protected int defaultY() { return 5; }
    }

    static class PotionsElement extends HudElement {
        PotionsElement() { super("potions"); }
        @Override public boolean isEnabled(ArtemConfig c) { return showPotions; }
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

    static class FpsElement extends HudElement {
        FpsElement() { super("fps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showFps; }
        @Override public String getText(MinecraftClient mc) { return "FPS " + mc.getCurrentFps(); }
        @Override public int getColor(MinecraftClient mc) { return 0xFFF2F2FA; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 110; }
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
        @Override protected int defaultY() { return 125; }
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
        @Override protected int defaultY() { return 140; }
    }

    static class CpsElement extends HudElement {
        CpsElement() { super("cps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showCps; }
        @Override public String getText(MinecraftClient mc) { return "CPS " + currentCps(); }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT_BRIGHT; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 155; }
    }

    static class WasdElement extends HudElement {
        WasdElement() { super("wasd"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showWasd; }
        @Override public String getText(MinecraftClient mc) { return "WASD"; }
        @Override public int getColor(MinecraftClient mc) { return NeonButton.ACCENT; }

        @Override
        protected void measure(MinecraftClient mc) {
            width = 52;
            height = 34;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.options == null) return;
            boolean w = mc.options.forwardKey.isPressed();
            boolean a = mc.options.leftKey.isPressed();
            boolean s = mc.options.backKey.isPressed();
            boolean d = mc.options.rightKey.isPressed();

            int keySize = 16;
            int gap = 2;

            drawKey(ctx, mc, x + keySize + gap, y, keySize, "W", w);
            drawKey(ctx, mc, x, y + keySize + gap, keySize, "A", a);
            drawKey(ctx, mc, x + keySize + gap, y + keySize + gap, keySize, "S", s);
            drawKey(ctx, mc, x + (keySize + gap) * 2, y + keySize + gap, keySize, "D", d);
        }

        private void drawKey(DrawContext ctx, MinecraftClient mc, int kx, int ky, int size, String label, boolean pressed) {
            if (pressed) {
                NeonButton.fillGradientRounded(ctx, kx, ky, size, size, 4, NeonButton.ACCENT_BRIGHT, NeonButton.ACCENT);
                NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, size, size, 4, 0x88FFFFFF);
            } else {
                NeonButton.fillGradientRounded(ctx, kx, ky, size, size, 4, 0xD811111B, 0xD80A0A11);
                NeonButton.drawSmoothRoundedOutline(ctx, kx, ky, size, size, 4, 0x1EFFFFFF);
            }
            float tw = mc.textRenderer.getWidth(label) * 0.80f;
            NeonButton.drawSmallText(ctx, mc.textRenderer, label, kx + (size - tw) / 2f, ky + 5f,
                    pressed ? 0xFFFFFFFF : 0xFF9696AC, 0.80f);
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.getWindow() != null
                    ? mc.getWindow().getScaledHeight() - 40
                    : 200;
        }
    }
}
