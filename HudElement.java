package com.artem.visuals.hud;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.gui.NeonButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Базовый перетаскиваемый элемент HUD в стиле тёмного стекла PulseVisuals.
 */
public abstract class HudElement {
    public final String id;
    protected int width = 52;
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
        width = Math.max(40, textW + 12);
        height = 14;
    }

    public void render(DrawContext ctx, MinecraftClient mc, ArtemConfig c) {
        if (!isEnabled(c)) return;

        measure(mc);
        if (width <= 0 || height <= 0) return;

        ArtemConfig.HudPos pos = c.getHudPos(id, defaultX(), defaultY());
        int x = pos.x;
        int y = pos.y;

        renderBody(ctx, mc, c, x, y);

        if (c.hudEditMode) {
            int border = dragging ? 0xFF9D8DF7 : 0x887B68EE;
            NeonButton.drawRoundedBorder(ctx, x, y, width, height, border);
        }
    }

    protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
        String text = getText(mc);
        int accentColor = getColor(mc);

        int bg = dragging ? 0xE01C1C2B : 0xCC101018;
        NeonButton.fillRounded(ctx, x, y, width, height, bg);
        NeonButton.drawRoundedBorder(ctx, x, y, width, height, 0x22FFFFFF);

        int spaceIdx = text.indexOf(' ');
        if (spaceIdx > 0 && spaceIdx < text.length() - 1) {
            String key = text.substring(0, spaceIdx);
            String val = text.substring(spaceIdx + 1).trim();
            ctx.drawText(mc.textRenderer, key, x + 6, y + 3, 0xFF88889E, false);
            int keyW = mc.textRenderer.getWidth(key + " ");
            ctx.drawText(mc.textRenderer, val, x + 6 + keyW, y + 3, accentColor, true);
        } else {
            ctx.drawText(mc.textRenderer, text, x + 6, y + 3, accentColor, true);
        }
    }

    public boolean mouseClicked(double mx, double my, ArtemConfig c) {
        if (!c.hudEditMode || !isEnabled(c) || width <= 0) return false;
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

    protected int defaultX() { return 6; }
    protected int defaultY() { return 6; }
}
