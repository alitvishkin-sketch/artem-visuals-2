package com.artem.visuals.crosshair;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.hud.HudManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Чёткий кастомный прицел с ровным кольцом и центральной точкой.
 */
public final class CrosshairRenderer {
    private CrosshairRenderer() {}

    public static void render(DrawContext ctx, float tickDelta) {
        ArtemConfig c = ArtemConfig.get();
        if (!c.customCrosshair) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options.hudHidden || mc.player == null) return;
        if (mc.currentScreen != null) return;

        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;

        int size = c.crosshairSize;
        int gap = c.crosshairGap;
        int th = Math.max(1, c.crosshairThickness);
        int color = c.crosshairColor;

        if (c.crosshairDynamic && HudManager.isAttackingRecently()) {
            gap += 3;
            size += 1;
        }

        int outline = c.crosshairOutline ? 0xBB000000 : 0;

        switch (c.crosshairStyle) {
            case 1 -> drawDot(ctx, cx, cy, size, color, outline);
            case 2 -> drawCircle(ctx, cx, cy, size, color, outline);
            case 3 -> drawPlus(ctx, cx, cy, size, gap, th, color, outline);
            default -> drawCross(ctx, cx, cy, size, gap, th, color, outline);
        }
    }

    private static void drawCross(DrawContext ctx, int cx, int cy, int size, int gap, int th, int color, int outline) {
        int half = th / 2;
        fillRect(ctx, cx - gap - size, cy - half, size, th, color, outline);
        fillRect(ctx, cx + gap + (th % 2), cy - half, size, th, color, outline);
        fillRect(ctx, cx - half, cy - gap - size, th, size, color, outline);
        fillRect(ctx, cx - half, cy + gap + (th % 2), th, size, color, outline);
        if (gap >= 2) {
            fillRect(ctx, cx, cy, 1, 1, color, outline);
        }
    }

    private static void drawPlus(DrawContext ctx, int cx, int cy, int size, int gap, int th, int color, int outline) {
        int half = th / 2;
        fillRect(ctx, cx - size, cy - half, size * 2 + (th % 2), th, color, outline);
        fillRect(ctx, cx - half, cy - size, th, size * 2 + (th % 2), color, outline);
    }

    private static void drawDot(DrawContext ctx, int cx, int cy, int size, int color, int outline) {
        int s = Math.max(1, size / 3);
        fillRect(ctx, cx - s, cy - s, s * 2 + 1, s * 2 + 1, color, outline);
    }

    private static void drawCircle(DrawContext ctx, int cx, int cy, int radius, int color, int outline) {
        int r = Math.max(3, radius);
        for (int a = 0; a < 360; a++) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.cos(rad) * r);
            int y = cy + (int) Math.round(Math.sin(rad) * r);
            if (outline != 0) {
                ctx.fill(x - 1, y, x, y + 1, outline);
                ctx.fill(x + 1, y, x + 2, y + 1, outline);
                ctx.fill(x, y - 1, x + 1, y, outline);
                ctx.fill(x, y + 1, x + 1, y + 2, outline);
            }
            ctx.fill(x, y, x + 1, y + 1, color);
        }
        fillRect(ctx, cx, cy, 1, 1, color, outline);
    }

    private static void fillRect(DrawContext ctx, int x, int y, int w, int h, int color, int outline) {
        if (outline != 0) {
            ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, outline);
        }
        ctx.fill(x, y, x + w, y + h, color);
    }

    /** Предпросмотр прицела в меню. */
    public static void renderPreview(DrawContext ctx, int cx, int cy, ArtemConfig c) {
        int size = c.crosshairSize;
        int gap = c.crosshairGap;
        int th = Math.max(1, c.crosshairThickness);
        int color = c.crosshairColor;
        int outline = c.crosshairOutline ? 0xBB000000 : 0;
        switch (c.crosshairStyle) {
            case 1 -> drawDot(ctx, cx, cy, size, color, outline);
            case 2 -> drawCircle(ctx, cx, cy, size, color, outline);
            case 3 -> drawPlus(ctx, cx, cy, size, gap, th, color, outline);
            default -> drawCross(ctx, cx, cy, size, gap, th, color, outline);
        }
    }
}
