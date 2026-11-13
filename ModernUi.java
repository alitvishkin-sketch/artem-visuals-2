package com.artem.visuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Рисование в современном стиле: скруглённые прямоугольники, тени, шрифт Inter. */
public final class ModernUi {
    public static final Identifier FONT = Identifier.of("artemvisuals", "inter");
    public static final Identifier FONT_BOLD = Identifier.of("artemvisuals", "inter_bold");

    /** Общая прозрачность (для анимации появления окна). */
    public static float globalAlpha = 1f;

    private ModernUi() {}

    public static Text t(String s, boolean bold) {
        return Text.literal(s).setStyle(Style.EMPTY.withFont(bold ? FONT_BOLD : FONT));
    }

    public static int width(String s, boolean bold) {
        return MinecraftClient.getInstance().textRenderer.getWidth(t(s, bold));
    }

    public static void text(DrawContext c, String s, int x, int y, int argb, boolean bold) {
        c.drawText(MinecraftClient.getInstance().textRenderer, t(s, bold), x, y, mulAlpha(argb), false);
    }

    public static void textCentered(DrawContext c, String s, int cx, int y, int argb, boolean bold) {
        text(c, s, cx - width(s, bold) / 2, y, argb, bold);
    }

    /** Скруглённый прямоугольник без обводки, строка за строкой (без наложения альфы). */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int argb) {
        argb = mulAlpha(argb);
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * (double) r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, argb);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, argb);
        }
        c.fill(x, y + r, x + w, y + h - r, argb);
    }

    /** Мягкая тень вокруг блока. */
    public static void shadow(DrawContext c, int x, int y, int w, int h, int r, int size) {
        for (int i = size; i >= 1; i--) {
            int a = (int) (26f * (1f - i / (float) (size + 1)));
            rrect(c, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, argb(a, 0, 0, 0));
        }
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    public static int withAlpha(int color, int a) {
        return (a & 255) << 24 | (color & 0xFFFFFF);
    }

    public static int mulAlpha(int color) {
        int a = (int) (((color >>> 24) & 255) * globalAlpha);
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return argb(a, r, g, b);
    }

    public static float easeOutCubic(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float f = 1f - t;
        return 1f - f * f * f;
    }
}
