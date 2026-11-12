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
 * Современная кнопка в стиле тёмного неона с плавным hover и индикатором ВКЛ/ВЫКЛ.
 */
public class NeonButton extends ClickableWidget {
    private final Supplier<String> label;
    private final BooleanSupplier state; // null = обычная кнопка или переключатель значений
    private final Runnable action;
    private float hoverAnim = 0f;

    // Цвета темы
    public static final int BG = 0xE6100D1D;
    public static final int BG_HOVER = 0xF21B1533;
    public static final int NEON = 0xFF9B59FF;
    public static final int NEON_DIM = 0xFF473275;
    public static final int NEON_ON = 0xFFC77DFF;
    public static final int TEXT = 0xFFE6E0F8;
    public static final int TEXT_ON = 0xFFFFFFFF;

    public NeonButton(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action) {
        super(x, y, w, h, Text.empty());
        this.label = label;
        this.state = state;
        this.action = action;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean isToggle = state != null;
        boolean on = isToggle && state.getAsBoolean();
        boolean hover = isHovered();

        hoverAnim += ((hover ? 1f : 0f) - hoverAnim) * Math.min(1f, delta * 0.45f);

        String rawText = label.get();
        setMessage(Text.literal(rawText));

        int x = getX(), y = getY(), w = width, h = height;

        // Фон со скруглёнными углами (срез 1px по углам)
        int bgColor = on ? 0xEE181230 : (hover ? BG_HOVER : BG);
        fillRounded(ctx, x, y, w, h, bgColor);

        // Мягкий внутренний блик сверху
        ctx.fill(x + 2, y + 1, x + w - 2, y + 2, on ? 0x33C77DFF : 0x18FFFFFF);

        // Рамка
        int border = on ? NEON_ON : (hover ? NEON : NEON_DIM);
        drawRoundedBorder(ctx, x, y, w, h, border);

        // Полоска подсветки при наведении
        if (hoverAnim > 0.02f) {
            int alpha = (int) (hoverAnim * 0x45);
            int glow = (alpha << 24) | (NEON & 0x00FFFFFF);
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, glow);
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        if (isToggle) {
            // Убираем дублирующий хвост ": ВКЛ" / ": ВЫКЛ", если он передан в строке
            String cleanName = rawText
                    .replace(": ВКЛ", "")
                    .replace(": ВЫКЛ", "");

            // Левая цветная риска
            int accent = on ? 0xFF38F2AF : 0xFF5A4E7A;
            ctx.fill(x + 1, y + 3, x + 3, y + h - 3, accent);

            // Название слева
            ctx.drawText(tr, cleanName, x + 7, y + (h - 8) / 2, on ? TEXT_ON : TEXT, true);

            // Плашка-индикатор справа
            int pillW = 28;
            int pillH = h - 6;
            int px = x + w - pillW - 4;
            int py = y + 3;
            int pillBg = on ? 0x3338F2AF : 0x28FFFFFF;
            int pillBorder = on ? 0xAA38F2AF : 0x448880A8;

            ctx.fill(px, py, px + pillW, py + pillH, pillBg);
            ctx.fill(px, py, px + pillW, py + 1, pillBorder);
            ctx.fill(px, py + pillH - 1, px + pillW, py + pillH, pillBorder);

            String badge = on ? "ВКЛ" : "ВЫКЛ";
            int badgeColor = on ? 0xFF4CFFBE : 0xFF968EB0;
            int bw = tr.getWidth(badge);
            ctx.drawText(tr, badge, px + (pillW - bw) / 2, py + (pillH - 8) / 2 + 1, badgeColor, false);
        } else {
            // Обычная кнопка / вкладка / переключатель значений
            if (hover) {
                ctx.fill(x + 6, y + h - 2, x + w - 6, y + h - 1, NEON_ON);
            }
            ctx.drawCenteredTextWithShadow(tr, rawText, x + w / 2, y + (h - 8) / 2, hover ? TEXT_ON : TEXT);
        }
    }

    private static void fillRounded(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void drawRoundedBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
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
