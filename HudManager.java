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
 * HUD-элементы Artem Visuals.
 *
 * По умолчанию:
 *   Левый верх  — FPS → Ping → XYZ → CPS
 *   Левый низ   — WASD
 *   Правый верх — Сервер
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
        @Override public String getText(MinecraftClient mc) { return "FPS  " + mc.getCurrentFps(); }
        @Override public int getColor(MinecraftClient mc) {
            int f = mc.getCurrentFps();
            return f >= 60 ? 0xFF55FFAA : (f >= 30 ? 0xFFC0A0FF : 0xFFFF5566);
        }
        @Override protected int defaultX() { return 4; }
        @Override protected int defaultY() { return 4; }
    }

    static class PingElement extends HudElement {
        PingElement() { super("ping"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showPing; }
        @Override public String getText(MinecraftClient mc) {
            int ping = getPing(mc);
            return ping >= 0 ? "PING " + ping + "ms" : "PING —";
        }
        @Override public int getColor(MinecraftClient mc) {
            int p = getPing(mc);
            if (p < 0) return 0xFF888888;
            return p < 50 ? 0xFF55FFAA : (p < 120 ? 0xFFC0A0FF : 0xFFFF5566);
        }
        @Override protected int defaultX() { return 4; }
        @Override protected int defaultY() { return 20; }
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
            if (mc.player == null) return "XYZ  —";
            BlockPos p = mc.player.getBlockPos();
            return String.format("XYZ  %d %d %d", p.getX(), p.getY(), p.getZ());
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFD0B8FF; }
        @Override protected int defaultX() { return 4; }
        @Override protected int defaultY() { return 36; }
    }

    static class CpsElement extends HudElement {
        CpsElement() { super("cps"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showCps; }
        @Override public String getText(MinecraftClient mc) { return "CPS  " + currentCps(); }
        @Override public int getColor(MinecraftClient mc) { return 0xFFFF88CC; }
        @Override protected int defaultX() { return 4; }
        @Override protected int defaultY() { return 52; }
    }

    static class WasdElement extends HudElement {
        WasdElement() { super("wasd"); }
        @Override public boolean isEnabled(ArtemConfig c) { return c.showWasd; }
        @Override public String getText(MinecraftClient mc) {
            if (mc.player == null || mc.options == null) return "[W][A][S][D]";
            boolean w = mc.options.forwardKey.isPressed();
            boolean a = mc.options.leftKey.isPressed();
            boolean s = mc.options.backKey.isPressed();
            boolean d = mc.options.rightKey.isPressed();
            return (w ? "[W]" : "[·]") + (a ? "[A]" : "[·]")
                    + (s ? "[S]" : "[·]") + (d ? "[D]" : "[·]");
        }
        @Override public int getColor(MinecraftClient mc) {
            if (mc.options == null) return 0xFF666677;
            boolean any = mc.options.forwardKey.isPressed() || mc.options.leftKey.isPressed()
                    || mc.options.backKey.isPressed() || mc.options.rightKey.isPressed();
            return any ? 0xFFC77DFF : 0xFF666677;
        }
        @Override protected int defaultX() { return 4; }
        @Override protected int defaultY() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.getWindow() != null
                    ? mc.getWindow().getScaledHeight() - 20
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
            return "—";
        }
        @Override public int getColor(MinecraftClient mc) { return 0xFFAAA0C0; }
        @Override protected int defaultX() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return 200;
            String text = getText(mc);
            int tw = mc.textRenderer.getWidth(text) + 10;
            return mc.getWindow().getScaledWidth() - tw - 4;
        }
        @Override protected int defaultY() { return 4; }
    }
}
