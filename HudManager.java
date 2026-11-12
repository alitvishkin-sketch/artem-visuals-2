package com.artem.visuals.hud;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.gui.NeonButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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
 * HUD для FunTime: верхний инфо-бар, Potions HUD, TargetHUD, WASD, FPS/Ping/XYZ/CPS.
 */
public final class HudManager {
    public static boolean showPotions = true;
    public static boolean showTargetHud = true;

    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static long lastAttackMs = 0;
    private static final long[] clickTimestamps = new long[64];
    private static int ctsIdx = 0;

    static {
        ELEMENTS.add(new FpsElement());
        ELEMENTS.add(new PingElement());
        ELEMENTS.add(new XyzElement());
        ELEMENTS.add(new CpsElement());
        ELEMENTS.add(new WasdElement());
        ELEMENTS.add(new TopBarElement());
        ELEMENTS.add(new PotionsElement());
        ELEMENTS.add(new TargetHudElement());
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

    // ——— elements ———

    static class FpsElement extends HudElement {
        FpsElement() { super("fps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showFps; }
        @Override public String getText(MinecraftClient mc) { return "FPS " + mc.getCurrentFps(); }
        @Override public int getColor(MinecraftClient mc) {
            int f = mc.getCurrentFps();
            return f >= 60 ? 0xFFF2F2FA : (f >= 30 ? 0xFF9D8DF7 : 0xFFFF5E7E);
        }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 6; }
    }

    static class PingElement extends HudElement {
        PingElement() { super("ping"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPing; }
        @Override public String getText(MinecraftClient mc) {
            int ping = queryPing(mc);
            return ping >= 0 ? "PING " + ping + "ms" : "PING 0ms";
        }
        @Override public int getColor(MinecraftClient mc) {
            int p = queryPing(mc);
            if (p < 0) return 0xFFF2F2FA;
            return p < 60 ? 0xFFF2F2FA : (p < 130 ? 0xFF9D8DF7 : 0xFFFF5E7E);
        }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 22; }
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
        @Override protected int defaultY() { return 38; }
    }

    static class CpsElement extends HudElement {
        CpsElement() { super("cps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showCps; }
        @Override public String getText(MinecraftClient mc) { return "CPS " + currentCps(); }
        @Override public int getColor(MinecraftClient mc) { return 0xFF9D8DF7; }
        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 54; }
    }

    static class WasdElement extends HudElement {
        WasdElement() { super("wasd"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showWasd; }
        @Override public String getText(MinecraftClient mc) { return "WASD"; }
        @Override public int getColor(MinecraftClient mc) { return 0xFF7B68EE; }

        @Override
        protected void measure(MinecraftClient mc) {
            width = 58;
            height = 38;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            if (mc.options == null) return;
            boolean w = mc.options.forwardKey.isPressed();
            boolean a = mc.options.leftKey.isPressed();
            boolean s = mc.options.backKey.isPressed();
            boolean d = mc.options.rightKey.isPressed();

            int keySize = 18;
            int gap = 2;

            drawKey(ctx, mc, x + keySize + gap, y, keySize, "W", w);
            drawKey(ctx, mc, x, y + keySize + gap, keySize, "A", a);
            drawKey(ctx, mc, x + keySize + gap, y + keySize + gap, keySize, "S", s);
            drawKey(ctx, mc, x + (keySize + gap) * 2, y + keySize + gap, keySize, "D", d);
        }

        private void drawKey(DrawContext ctx, MinecraftClient mc, int kx, int ky, int size, String label, boolean pressed) {
            int bg = pressed ? 0xE66C5CE7 : 0xCC101018;
            int border = pressed ? 0x66B8A9FF : 0x22FFFFFF;
            int textCol = pressed ? 0xFFFFFFFF : 0xFF9696AC;

            NeonButton.fillRounded(ctx, kx, ky, size, size, bg);
            NeonButton.drawRoundedBorder(ctx, kx, ky, size, size, border);

            int tw = mc.textRenderer.getWidth(label);
            ctx.drawText(mc.textRenderer, label, kx + (size - tw) / 2, ky + (size - 8) / 2 + 1, textCol, pressed);
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.getWindow() != null
                    ? mc.getWindow().getScaledHeight() - 44
                    : 200;
        }
    }

    /**
     * Верхняя центральная плашка в стиле PulseVisuals: лого + сервер (FunTime) + пинг + FPS.
     */
    static class TopBarElement extends HudElement {
        TopBarElement() { super("server"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showServer; }

        @Override
        public String getText(MinecraftClient mc) {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null) {
                String addr = info.address != null ? info.address.toLowerCase() : "";
                if (addr.contains("funtime")) return "mc.funtime.su";
                String name = info.name != null && !info.name.isBlank() ? info.name : info.address;
                if (name.length() > 18) name = name.substring(0, 16) + "…";
                return name;
            }
            return mc.isInSingleplayer() ? "FunTime Local" : "Offline";
        }

        @Override public int getColor(MinecraftClient mc) { return 0xFFE8E8F4; }

        private String buildPing(MinecraftClient mc) {
            int p = queryPing(mc);
            return (p >= 0 ? p : 0) + "ms";
        }

        private String buildFps(MinecraftClient mc) {
            return mc.getCurrentFps() + " FPS";
        }

        @Override
        protected void measure(MinecraftClient mc) {
            String srv = getText(mc);
            String ping = buildPing(mc);
            String fps = buildFps(mc);
            int w = 18 + mc.textRenderer.getWidth(srv) + 10
                    + mc.textRenderer.getWidth(ping) + 10
                    + mc.textRenderer.getWidth(fps) + 8;
            width = w;
            height = 15;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            NeonButton.fillRounded(ctx, x, y, width, height, 0xD8101018);
            NeonButton.drawRoundedBorder(ctx, x, y, width, height, 0x28FFFFFF);

            // Мини-волна пульса слева
            int wx = x + 5;
            int wy = y + 3;
            ctx.fill(wx, wy + 4, wx + 3, wy + 5, 0xFF7B68EE);
            ctx.fill(wx + 3, wy + 1, wx + 4, wy + 6, 0xFF9D8DF7);
            ctx.fill(wx + 4, wy + 3, wx + 5, wy + 7, 0xFF7B68EE);
            ctx.fill(wx + 5, wy + 4, wx + 8, wy + 5, 0xFF7B68EE);

            int cx = x + 16;
            int ty = y + 4;

            String srv = getText(mc);
            ctx.drawText(mc.textRenderer, srv, cx, ty, 0xFFF2F2FA, false);
            cx += mc.textRenderer.getWidth(srv) + 4;

            // Разделитель
            ctx.fill(cx, y + 3, cx + 1, y + height - 3, 0x33FFFFFF);
            cx += 5;

            String ping = buildPing(mc);
            ctx.drawText(mc.textRenderer, ping, cx, ty, 0xFFB8A9FF, false);
            cx += mc.textRenderer.getWidth(ping) + 4;

            ctx.fill(cx, y + 3, cx + 1, y + height - 3, 0x33FFFFFF);
            cx += 5;

            String fps = buildFps(mc);
            ctx.drawText(mc.textRenderer, fps, cx, ty, 0xFFE8E8F4, false);
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
     * Карточка активных зелий слева (Сила II, Скорость II, Огнестойкость и др. для FunTime PvP).
     */
    static class PotionsElement extends HudElement {
        PotionsElement() { super("potions"); }
        @Override public boolean isEnabled(ArtemConfig c) { return showPotions; }
        @Override public String getText(MinecraftClient mc) { return "Potions"; }
        @Override public int getColor(MinecraftClient mc) { return 0xFF9D8DF7; }

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
            int maxW = 86;
            for (String[] r : rows) {
                int w = 14 + mc.textRenderer.getWidth(r[0]) + 10 + mc.textRenderer.getWidth(r[1]) + 6;
                if (w > maxW) maxW = w;
            }
            width = maxW;
            height = 16 + rows.size() * 12;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            List<String[]> rows = collectRows(mc, c);
            if (rows.isEmpty()) return;

            NeonButton.fillRounded(ctx, x, y, width, height, 0xCC101018);
            NeonButton.drawRoundedBorder(ctx, x, y, width, height, 0x22FFFFFF);

            // Иконка зелья + заголовок Potions
            ctx.fill(x + 6, y + 4, x + 9, y + 5, 0xFF9D8DF7);
            ctx.fill(x + 5, y + 5, x + 10, y + 10, 0xFF7B68EE);
            ctx.drawText(mc.textRenderer, "Potions", x + 13, y + 4, 0xFFD8D8E6, false);

            int ry = y + 16;
            for (String[] r : rows) {
                int dotCol = Integer.parseInt(r[2]);
                ctx.fill(x + 6, ry + 2, x + 9, ry + 6, dotCol);
                ctx.drawText(mc.textRenderer, r[0], x + 13, ry, 0xFFE6E6F2, false);
                int tw = mc.textRenderer.getWidth(r[1]);
                ctx.drawText(mc.textRenderer, r[1], x + width - tw - 6, ry, 0xFF9696AC, false);
                ry += 12;
            }
        }

        @Override protected int defaultX() { return 6; }
        @Override protected int defaultY() { return 74; }
    }

    /**
     * TargetHUD в стиле PulseVisuals под верхним инфо-баром (имя цели, HP и фиолетовая полоса здоровья).
     */
    static class TargetHudElement extends HudElement {
        private float smoothHp = 20f;

        TargetHudElement() { super("target"); }
        @Override public boolean isEnabled(ArtemConfig c) { return showTargetHud; }
        @Override public String getText(MinecraftClient mc) { return "TargetHUD"; }
        @Override public int getColor(MinecraftClient mc) { return 0xFF7B68EE; }

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
            width = 114;
            height = 28;
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

            NeonButton.fillRounded(ctx, x, y, width, height, 0xD8101018);
            NeonButton.drawRoundedBorder(ctx, x, y, width, height, 0x28FFFFFF);

            // Аватар-иконка слева
            int avSize = 20;
            int ax = x + 4;
            int ay = y + 4;
            NeonButton.fillRounded(ctx, ax, ay, avSize, avSize, 0xFF1C1B2E);
            NeonButton.drawRoundedBorder(ctx, ax, ay, avSize, avSize, 0x557B68EE);

            String name = target.getName().getString();
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            ctx.drawCenteredTextWithShadow(mc.textRenderer, initial, ax + avSize / 2, ay + 6, 0xFFB8A9FF);

            // Имя цели
            if (name.length() > 13) name = name.substring(0, 12) + "…";
            int tx = ax + avSize + 5;
            ctx.drawText(mc.textRenderer, name, tx, y + 4, 0xFFF2F2FA, false);

            // HP + Absorption
            String hpStr = String.format("HP %.1f", hp);
            ctx.drawText(mc.textRenderer, hpStr, tx, y + 13, 0xFF9696AC, false);
            if (abs > 0.1f) {
                String absStr = String.format("+%.0f", abs);
                ctx.drawText(mc.textRenderer, absStr, tx + mc.textRenderer.getWidth(hpStr) + 3, y + 13, 0xFFFFD166, false);
            }

            // Полоска здоровья снизу (фиолетовый градиент как в PulseVisuals)
            int barX = tx;
            int barY = y + 22;
            int barW = width - (tx - x) - 5;
            int barH = 3;
            ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF232234);
            int fillW = Math.round(barW * ratio);
            if (fillW > 0) {
                ctx.fillGradient(barX, barY, barX + fillW, barY + barH, 0xFF6C5CE7, 0xFF9D8DF7);
            }
        }

        @Override
        protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return 150;
            return (mc.getWindow().getScaledWidth() - 114) / 2;
        }

        @Override protected int defaultY() { return 24; }
    }
}
