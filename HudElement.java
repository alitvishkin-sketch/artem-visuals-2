package com.artem.visuals.hud;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Базовый перетаскиваемый элемент HUD в стиле полупрозрачного тёмного стекла.
 */
public abstract class HudElement {
    public final String id;
    protected int width = 54;
    protected int height = 14;
    private boolean dragging;
    private int dragOffsetX, dragOffsetY;

    protected HudElement(String id) {
        this.id = id;
    }

    public abstract boolean isEnabled(ArtemConfig c);
    public abstract String getText(MinecraftClient mc);
    public abstract int getColor(MinecraftClient mc);

    protected void measure(MinecraftClient mc) {
        String text = getText(mc);
        int textW = mc.textRenderer.getWidth(text);
        width = Math.max(42, textW + 12);
        height = 14;
    }

    public void render(DrawContext ctx, MinecraftClient mc, ArtemConfig c) {
        if (!isEnabled(c)) return;

        measure(mc);
        ArtemConfig.HudPos pos = c.getHudPos(id, defaultX(), defaultY());
        int x = pos.x;
        int y = pos.y;

        renderBody(ctx, mc, c, x, y);

        // В режиме редактирования — яркая пунктирно-неоновая рамка
        if (c.hudEditMode) {
            int neon = dragging ? 0xFF4CFFBE : 0xFFC77DFF;
            ctx.fill(x, y, x + width, y + 1, neon);
            ctx.fill(x, y + height - 1, x + width, y + height, neon);
            ctx.fill(x, y, x + 1, y + height, neon);
            ctx.fill(x + width - 1, y, x + width, y + height, neon);
        }
    }

    protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
        String text = getText(mc);
        int accentColor = getColor(mc);

        // Скруглённая полупрозрачная стеклянная подложка
        int bg = dragging ? 0xD8241640 : 0xB40B0917;
        ctx.fill(x + 1, y, x + width - 1, y + height, bg);
        ctx.fill(x, y + 1, x + 1, y + height - 1, bg);
        ctx.fill(x + width - 1, y + 1, x + width, y + height - 1, bg);

        // Тонкий внутренний контур
        ctx.fill(x + 1, y, x + width - 1, y + 1, 0x22FFFFFF);
        ctx.fill(x + 1, y + height - 1, x + width - 1, y + height, 0x22000000);

        // Цветная индикаторная полоска слева
        ctx.fill(x + 1, y + 2, x + 3, y + height - 2, accentColor);

        // Двухцветный рендер: метка (FPS/PING/XYZ/CPS) светло-серая, значение — яркое
        int spaceIdx = text.indexOf(' ');
        if (spaceIdx > 0 && spaceIdx < text.length() - 1) {
            String key = text.substring(0, spaceIdx);
            String val = text.substring(spaceIdx + 1).trim();
            ctx.drawText(mc.textRenderer, key, x + 6, y + 3, 0xFF9E96B8, true);
            int keyW = mc.textRenderer.getWidth(key + " ");
            ctx.drawText(mc.textRenderer, val, x + 6 + keyW, y + 3, accentColor, true);
        } else {
            ctx.drawText(mc.textRenderer, text, x + 6, y + 3, accentColor, true);
        }
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

    protected int defaultX() { return 5; }
    protected int defaultY() { return 5; }
}
