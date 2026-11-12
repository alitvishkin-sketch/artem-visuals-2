package com.artem.visuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Кнопка в неоновом стиле: чёрный фон, фиолетовая обводка, glow при hover.
 */
public class NeonButton extends ClickableWidget {
    private final Supplier<String> label;
    private final BooleanSupplier state; // null = action button
    private final Runnable action;

    // Цвета темы
    public static final int BG = 0xFF0C0A12;
    public static final int BG_HOVER = 0xFF16122A;
    public static final int NEON = 0xFF9B59FF;
    public static final int NEON_DIM = 0xFF5A3A99;
    public static final int NEON_ON = 0xFFC77DFF;
    public static final int TEXT = 0xFFE8E0FF;
    public static final int TEXT_ON = 0xFFFFFFFF;

    public NeonButton(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action) {
        super(x, y, w, h, Text.empty());
        this.label = label;
        this.state = state;
        this.action = action;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean on = state != null && state.getAsBoolean();
        boolean hover = isHovered();
        setMessage(Text.literal(label.get()));

        int x = getX(), y = getY(), w = width, h = height;

        // Фон
        ctx.fill(x, y, x + w, y + h, hover ? BG_HOVER : BG);

        // Неоновая рамка (имитация rounded)
        int border = on ? NEON_ON : (hover ? NEON : NEON_DIM);
        // top
        ctx.fill(x + 1, y, x + w - 1, y + 1, border);
        // bottom
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        // left
        ctx.fill(x, y + 1, x + 1, y + h - 1, border);
        // right
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        // soft glow line inside on hover/on
        if (hover || on) {
            ctx.fill(x + 1, y + 1, x + w - 1, y + 2, border & 0x55FFFFFF | 0x22000000);
        }

        // Акцент слева
        ctx.fill(x, y + 2, x + 2, y + h - 2, on ? NEON_ON : border);

        int textColor = on ? TEXT_ON : TEXT;
        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                x + w / 2, y + (h - 8) / 2, textColor);
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
