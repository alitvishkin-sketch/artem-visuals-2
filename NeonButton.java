package com.artem.visuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Компактные кнопки, тумблеры и вкладки в стиле PulseVisuals / FunTime.
 */
public class NeonButton extends ClickableWidget {
    public enum Style {
        TOGGLE,
        ACTION,
        DOCK_TAB,
        CLOSE_ICON
    }

    private final Supplier<String> label;
    private final BooleanSupplier state;
    private final Runnable action;
    private final Style style;
    private float hoverAnim = 0f;
    private float toggleAnim = -1f;

    public static final int BG = 0xE6151520;
    public static final int BG_HOVER = 0xF21E1E2E;
    public static final int NEON = 0xFF7B68EE;
    public static final int NEON_DIM = 0xFF34324A;
    public static final int TEXT = 0xFFD8D8E6;
    public static final int TEXT_DIM = 0xFF7D7D92;
    public static final int TEXT_ON = 0xFFFFFFFF;

    public NeonButton(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action) {
        this(x, y, w, h, label, state, action, state != null ? Style.TOGGLE : Style.ACTION);
    }

    public NeonButton(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action, Style style) {
        super(x, y, w, h, Text.empty());
        this.label = label;
        this.state = state;
        this.action = action;
        this.style = style;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean hover = isHovered();
        hoverAnim += ((hover ? 1f : 0f) - hoverAnim) * 0.25f;

        String rawText = label.get();
        setMessage(Text.literal(rawText));
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        int x = getX(), y = getY(), w = width, h = height;

        if (style == Style.CLOSE_ICON) {
            int bg = hover ? 0x66FF4D6D : 0x22FFFFFF;
            fillRounded(ctx, x, y, w, h, bg);
            ctx.drawCenteredTextWithShadow(tr, "✕", x + w / 2, y + (h - 8) / 2, hover ? 0xFFFFFFFF : TEXT_DIM);
            return;
        }

        if (style == Style.DOCK_TAB) {
            boolean active = state != null && state.getAsBoolean();
            int bg = active ? 0xFF6C5CE7 : (hover ? 0x33FFFFFF : 0x18FFFFFF);
            fillRounded(ctx, x, y, w, h, bg);
            if (active) {
                ctx.fill(x + 2, y, x + w - 2, y + 1, 0x88B8A9FF);
            }
            ctx.drawCenteredTextWithShadow(tr, rawText, x + w / 2, y + (h - 8) / 2, active ? TEXT_ON : (hover ? TEXT : TEXT_DIM));
            return;
        }

        int bgColor = hover ? BG_HOVER : BG;
        fillRounded(ctx, x, y, w, h, bgColor);

        int borderColor = hover ? 0x448B78FF : 0x1AFFFFFF;
        drawRoundedBorder(ctx, x, y, w, h, borderColor);

        if (style == Style.TOGGLE && state != null) {
            boolean on = state.getAsBoolean();
            if (toggleAnim < 0f) toggleAnim = on ? 1f : 0f;
            toggleAnim += ((on ? 1f : 0f) - toggleAnim) * 0.3f;

            String cleanName = rawText.replace(": ВКЛ", "").replace(": ВЫКЛ", "");
            int labelCol = on ? TEXT_ON : (hover ? TEXT : 0xFFA2A2B8);
            ctx.drawText(tr, cleanName, x + 6, y + (h - 8) / 2, labelCol, false);

            int tw = 22;
            int th = 10;
            int tx = x + w - tw - 5;
            int ty = y + (h - th) / 2;

            int trackBg = on ? 0xFF6C5CE7 : 0xFF282838;
            fillRounded(ctx, tx, ty, tw, th, trackBg);
            if (on) {
                ctx.fill(tx + 1, ty - 1, tx + tw - 1, ty, 0x337B68EE);
                ctx.fill(tx + 1, ty + th, tx + tw - 1, ty + th + 1, 0x337B68EE);
            }

            int knobSize = 6;
            int travel = tw - knobSize - 4;
            int kx = tx + 2 + Math.round(travel * toggleAnim);
            int ky = ty + 2;
            ctx.fill(kx, ky, kx + knobSize, ky + knobSize, on ? 0xFFFFFFFF : 0xFF8A8AA0);
        } else {
            int colonIdx = rawText.indexOf(':');
            if (colonIdx > 0 && colonIdx < rawText.length() - 1) {
                String k = rawText.substring(0, colonIdx + 1);
                String v = rawText.substring(colonIdx + 1).trim();
                ctx.drawText(tr, k, x + 6, y + (h - 8) / 2, 0xFFA2A2B8, false);
                int vw = tr.getWidth(v);
                ctx.drawText(tr, v, x + w - vw - 6, y + (h - 8) / 2, 0xFF9D8DF7, false);
            } else {
                ctx.drawCenteredTextWithShadow(tr, rawText, x + w / 2, y + (h - 8) / 2, hover ? TEXT_ON : TEXT);
            }
        }
    }

    public static void fillRounded(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void drawRoundedBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + 1, color);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        action.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
