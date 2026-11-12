package com.artem.visuals.hud;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.gui.NeonButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Базовый перетаскиваемый и масштабируемый элемент HUD с чётким текстом 1.0f без мыла.
 */
public abstract class HudElement {
    public final String id;
    protected int width = 56;
    protected int height = 16;
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
        width = Math.max(44, textW + 14);
        height = 16;
    }

    public void render(DrawContext ctx, MinecraftClient mc, ArtemConfig c) {
        if (!isEnabled(c)) return;

        measure(mc);
        if (width <= 0 || height <= 0) return;

        ArtemConfig.HudPos pos = c.getHudPos(id, defaultX(), defaultY());
        float s = Math.max(0.75f, Math.min(1.50f, pos.scale));
        int x = pos.x;
        int y = pos.y;

        if (Math.abs(s - 1.0f) < 0.02f) {
            renderBody(ctx, mc, c, x, y);
            if (c.hudEditMode) {
                int border = dragging ? NeonButton.ACCENT_BRIGHT : ((NeonButton.ACCENT & 0x00FFFFFF) | 0x88000000);
                NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, border);
            }
        } else {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 0);
            ctx.getMatrices().scale(s, s, 1f);
            renderBody(ctx, mc, c, 0, 0);
            if (c.hudEditMode) {
                int border = dragging ? NeonButton.ACCENT_BRIGHT : ((NeonButton.ACCENT & 0x00FFFFFF) | 0x88000000);
                NeonButton.drawSmoothRoundedOutline(ctx, 0, 0, width, height, 5, border);
            }
            ctx.getMatrices().pop();
        }
    }

    protected void renderBody(DrawContext ctx, MinecraftClient mc, ArtemConfig c, int x, int y) {
        String text = getText(mc);
        int accentColor = getColor(mc);

        NeonButton.fillGradientRounded(ctx, x, y, width, height, 5, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
        NeonButton.drawSmoothRoundedOutline(ctx, x, y, width, height, 5, 0x1EFFFFFF);

        int spaceIdx = text.indexOf(' ');
        if (spaceIdx > 0 && spaceIdx < text.length() - 1) {
            String key = text.substring(0, spaceIdx);
            String val = text.substring(spaceIdx + 1).trim();
            ctx.drawText(mc.textRenderer, key, x + 7, y + 4, 0xFF8C8CA4, false);
            int keyW = mc.textRenderer.getWidth(key + " ");
            ctx.drawText(mc.textRenderer, val, x + 7 + keyW, y + 4, accentColor, false);
        } else {
            ctx.drawText(mc.textRenderer, text, x + 7, y + 4, accentColor, false);
        }
    }

    public boolean isHoveredScaled(double mx, double my, ArtemConfig c) {
        if (!isEnabled(c) || width <= 0 || height <= 0) return false;
        ArtemConfig.HudPos pos = c.getHudPos(id, defaultX(), defaultY());
        float s = Math.max(0.75f, Math.min(1.50f, pos.scale));
        return mx >= pos.x && mx <= pos.x + width * s && my >= pos.y && my <= pos.y + height * s;
    }

    public boolean mouseClicked(double mx, double my, ArtemConfig c) {
        if (!c.hudEditMode || !isHoveredScaled(mx, my, c)) return false;
        ArtemConfig.HudPos pos = c.getHudPos(id, defaultX(), defaultY());
        dragging = true;
        dragOffsetX = (int) mx - pos.x;
        dragOffsetY = (int) my - pos.y;
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount, ArtemConfig c) {
        if (!c.hudEditMode || !isHoveredScaled(mx, my, c)) return false;
        ArtemConfig.HudPos pos = c.getHudPos(id, defaultX(), defaultY());
        float next = pos.scale + (amount > 0 ? 0.08f : -0.08f);
        pos.scale = Math.max(0.75f, Math.min(1.50f, Math.round(next * 100f) / 100f));
        c.save();
        return true;
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
        float s = Math.max(0.75f, Math.min(1.50f, pos.scale));
        pos.x = (int) mx - dragOffsetX;
        pos.y = (int) my - dragOffsetY;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() != null) {
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            int swScaled = Math.round(width * s);
            int shScaled = Math.round(height * s);
            if (Math.abs((pos.x + swScaled / 2) - sw / 2) <= 4) {
                pos.x = sw / 2 - swScaled / 2;
            }
            pos.x = Math.max(0, Math.min(sw - swScaled, pos.x));
            pos.y = Math.max(0, Math.min(sh - shScaled, pos.y));
        }
    }

    protected int defaultX() { return 6; }
    protected int defaultY() { return 6; }
}
