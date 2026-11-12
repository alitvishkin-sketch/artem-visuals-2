package com.artem.visuals.hud;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Базовый перетаскиваемый элемент HUD.
 * Фон + неоновый акцент всегда; в режиме редактирования — яркая рамка.
 */
public abstract class HudElement {
    public final String id;
    protected int width = 48;
    protected int height = 14;
    private boolean dragging;
    private int dragOffsetX, dragOffsetY;

    protected HudElement(String id) {
        this.id = id;
    }

    public abstract boolean isEnabled(ArtemConfig c);
    public abstract String getText(MinecraftClient mc);
    public abstract int getColor(MinecraftClient mc);

    public void render(DrawContext ctx, MinecraftClient mc, ArtemConfig c) {
        if (!isEnabled(c)) return;

        ArtemConfig.HudPos pos = c.getHudPos(id, defaultX(), defaultY());
        String text = getText(mc);
        int color = getColor(mc);

        int textW = mc.textRenderer.getWidth(text);
        width = Math.max(36, textW + 10);
        height = 14;

        int x = pos.x;
        int y = pos.y;

        // Полупрозрачный тёмный фон
        int bg = dragging ? 0xCC2A1848 : 0xAA0A0818;
        ctx.fill(x, y, x + width, y + height, bg);

        // Неоновая полоска слева
        int accent = c.hudEditMode ? 0xFFC77DFF : 0xFF9B59FF;
        ctx.fill(x, y, x + 2, y + height, accent);

        // В режиме редактирования — полная неоновая рамка
        if (c.hudEditMode) {
            int neon = dragging ? 0xFFFF88FF : 0xFF9B59FF;
            ctx.fill(x, y, x + width, y + 1, neon);
            ctx.fill(x, y + height - 1, x + width, y + height, neon);
            ctx.fill(x, y, x + 1, y + height, neon);
            ctx.fill(x + width - 1, y, x + width, y + height, neon);
        }

        // Текст с небольшим отступом
        ctx.drawText(mc.textRenderer, text, x + 5, y + 3, color, true);
    }

    public boolean mouseClicked(double mx, double my, ArtemConfig c) {
        if (!c.hudEditMode || !isEnabled(c)) return false;
        ArtemConfig.HudPos pos = c.getHudPos(id, defaultX(), defaultY());
        if (mx >= pos.x && mx <= pos.x + width && my >= pos.y && my <= pos.y + height) {
            dragging = true;
            dragOffsetX = (int) mx - pos.x;
            dragOffsetY = (int) my - pos.y;
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        if (dragging) {
            dragging = false;
            ArtemConfig.get().save();
        }
    }

    public void mouseDragged(double mx, double my, ArtemConfig c) {
        if (!dragging) return;
        ArtemConfig.HudPos pos = c.getHudPos(id, defaultX(), defaultY());
        pos.x = (int) mx - dragOffsetX;
        pos.y = (int) my - dragOffsetY;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() != null) {
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            pos.x = Math.max(0, Math.min(sw - width, pos.x));
            pos.y = Math.max(0, Math.min(sh - height, pos.y));
        }
    }

    protected int defaultX() { return 4; }
    protected int defaultY() { return 4; }
}
