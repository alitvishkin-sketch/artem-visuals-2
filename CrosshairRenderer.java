package com.artem.visuals.crosshair;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.hud.HudManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Кастомный прицел поверх ванильного (ванильный можно скрыть через настройки игры).
 */
public final class CrosshairRenderer {
    private CrosshairRenderer() {}

    public static void render(DrawContext ctx, float tickDelta) {
        ArtemConfig c = ArtemConfig.get();
        if (!c.customCrosshair) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options.hudHidden || mc.player == null) return;
        // Не рисуем поверх меню/инвентаря
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

        int outline = c.crosshairOutline ? 0xFF000000 : 0;

        switch (c.crosshairStyle) {
            case 1 -> drawDot(ctx, cx, cy, size, color, outline);
            case 2 -> drawCircle(ctx, cx, cy, size, color, outline);
            case 3 -> drawPlus(ctx, cx, cy, size, gap, th, color, outline);
            default -> drawCross(ctx, cx, cy, size, gap, th, color, outline);
        }
    }

    private static void drawCross(DrawContext ctx, int cx, int cy, int size, int gap, int th, int color, int outline) {
        // horizontal
        fillRect(ctx, cx - gap - size, cy - th / 2, size, th, color, outline);
        fillRect(ctx, cx + gap, cy - th / 2, size, th, color, outline);
        // vertical
        fillRect(ctx, cx - th / 2, cy - gap - size, th, size, color, outline);
        fillRect(ctx, cx - th / 2, cy + gap, th, size, color, outline);
    }

    private static void drawPlus(DrawContext ctx, int cx, int cy, int size, int gap, int th, int color, int outline) {
        fillRect(ctx, cx - size, cy - th / 2, size * 2, th, color, outline);
        fillRect(ctx, cx - th / 2, cy - size, th, size * 2, color, outline);
    }

    private static void drawDot(DrawContext ctx, int cx, int cy, int size, int color, int outline) {
        int s = Math.max(2, size / 2);
        fillRect(ctx, cx - s, cy - s, s * 2, s * 2, color, outline);
    }

    private static void drawCircle(DrawContext ctx, int cx, int cy, int radius, int color, int outline) {
        // Простая окружность точками (без лишних зависимостей)
        for (int a = 0; a < 360; a += 8) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.cos(rad) * radius);
            int y = cy + (int) Math.round(Math.sin(rad) * radius);
            ctx.fill(x, y, x + 1, y + 1, color);
            if (outline != 0) {
                ctx.fill(x - 1, y, x, y + 1, outline);
                ctx.fill(x + 1, y, x + 2, y + 1, outline);
            }
        }
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
        int outline = c.crosshairOutline ? 0xFF000000 : 0;
        switch (c.crosshairStyle) {
            case 1 -> drawDot(ctx, cx, cy, size, color, outline);
            case 2 -> drawCircle(ctx, cx, cy, size, color, outline);
            case 3 -> drawPlus(ctx, cx, cy, size, gap, th, color, outline);
            default -> drawCross(ctx, cx, cy, size, gap, th, color, outline);
        }
    }
}
