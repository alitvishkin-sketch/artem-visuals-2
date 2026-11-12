package com.artem.visuals.hud;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-элементы Artem Visuals 2.0 с графическими клавишами WASD.
 */
public final class HudManager {
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
        ELEMENTS.add(new ServerElement());
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

    // ——— elements ———

    static class FpsElement extends HudElement {
        FpsElement() { super("fps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showFps; }
        @Override public String getText(MinecraftClient mc) { return "FPS " + mc.getCurrentFps(); }
        @Override public int getColor(MinecraftClient mc) {
            int f = mc.getCurrentFps();
            return f >= 60 ? 0xFF4CFFBE : (f >= 30 ? 0xFFC77DFF : 0xFFFF5566);
        }
        @Override protected int defaultX() { return 5; }
        @Override protected int defaultY() { return 5; }
    }

    static class PingElement extends HudElement {
        PingElement() { super("ping"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPing; }
        @Override public String getText(MinecraftClient mc) {
            int ping = getPing(mc);
            return ping >= 0 ? "PING " + ping + "ms" : "PING 0ms";
        }
        @Override public int getColor(MinecraftClient mc) {
            int p = getPing(mc);
            if (p < 0) return 0xFF4CFFBE;
            return p < 60 ? 0xFF4CFFBE : (p < 130 ? 0xFFC77DFF : 0xFFFF5566);
        }
        @Override protected int defaultX() { return 5; }
        @Override protected int defaultY() { return 21; }
        private int getPing(MinecraftClient mc) {
            ClientPlayNetworkHandler h = mc.getNetworkHandler();
            if (h == null || mc.player == null) return -1;
            PlayerListEntry e = h.getPlayerListEntry(mc.player.getUuid());
            return e != null ? e.getLatency() : -1;
        }
    }

    static class XyzElement extends HudElement {
        XyzElement() { super("xyz"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showXyz; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null) return "XYZ 0 0 0";
            BlockPos p = mc.player.getBlockPos();
            return String.format("XYZ %d %d %d", p.getX(), p.getY(), p.getZ());
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFD8C2FF; }
        @Override protected int defaultX() { return 5; }
        @Override protected int defaultY() { return 37; }
    }

    static class CpsElement extends HudElement {
        CpsElement() { super("cps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showCps; }
        @Override public String getText(MinecraftClient mc) { return "CPS " + currentCps(); }
        @Override public int getColor(MinecraftClient mc) { return 0xFFFF79C6; }
        @Override protected int defaultX() { return 5; }
        @Override protected int defaultY() { return 53; }
    }

    static class WasdElement extends HudElement {
        WasdElement() { super("wasd"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showWasd; }
        @Override public String getText(MinecraftClient mc) { return "WASD"; }
        @Override public int getColor(MinecraftClient mc) { return 0xFFC77DFF; }

        @Override
        protected void measure(MinecraftClient mc) {
            // 3 клавиши по 18px + 2 отступа по 2px = 58px; 2 ряда = 38px
            width = 58;
            height = 38;
        }

        @Override
        protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
            boolean w = mc.options != null && mc.options.forwardKey.isPressed();
            boolean a = mc.options != null && mc.options.leftKey.isPressed();
            boolean s = mc.options != null && mc.options.backKey.isPressed();
            boolean d = mc.options != null && mc.options.rightKey.isPressed();

            int keySize = 18;
            int gap = 2;

            drawKey(ctx, mc, x + keySize + gap, y, keySize, "W", w);
            drawKey(ctx, mc, x, y + keySize + gap, keySize, "A", a);
            drawKey(ctx, mc, x + keySize + gap, y + keySize + gap, keySize, "S", s);
            drawKey(ctx, mc, x + (keySize + gap) * 2, y + keySize + gap, keySize, "D", d);
        }

        private void drawKey(DrawContext ctx, MinecraftClient mc, int kx, int ky, int size, String label, boolean pressed) {
            int bg = pressed ? 0xE67B3FE4 : 0xB40B0917;
            int border = pressed ? 0xFF4CFFBE : 0x38FFFFFF;
            int textCol = pressed ? 0xFFFFFFFF : 0xFFB0A8CC;

            // Скруглённая клавиша
            ctx.fill(kx + 1, ky, kx + size - 1, ky + size, bg);
            ctx.fill(kx, ky + 1, kx + 1, ky + size - 1, bg);
            ctx.fill(kx + size - 1, ky + 1, kx + size, ky + size - 1, bg);

            // Контур
            ctx.fill(kx + 1, ky, kx + size - 1, ky + 1, border);
            ctx.fill(kx + 1, ky + size - 1, kx + size - 1, ky + size, border);

            int tw = mc.textRenderer.getWidth(label);
            ctx.drawText(mc.textRenderer, label, kx + (size - tw) / 2, ky + (size - 8) / 2 + 1, textCol, true);
        }

        @Override protected int defaultX() { return 5; }
        @Override protected int defaultY() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.getWindow() != null
                    ? mc.getWindow().getScaledHeight() - 44
                    : 200;
        }
    }

    static class ServerElement extends HudElement {
        ServerElement() { super("server"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showServer; }
        @Override public String getText(MinecraftClient mc) {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null) {
                String name = info.name != null && !info.name.isBlank() ? info.name : info.address;
                if (name.length() > 22) name = name.substring(0, 20) + "…";
                return name;
            }
            if (mc.isInSingleplayer()) return "Singleplayer";
            return "Offline";
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFC7B8EA; }
        @Override protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return 200;
            String text = getText(mc);
            int tw = mc.textRenderer.getWidth(text) + 12;
            return mc.getWindow().getScaledWidth() - tw - 5;
        }
        @Override protected int defaultY() { return 5; }
    }
}
