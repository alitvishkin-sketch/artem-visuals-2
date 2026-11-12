package com.artem.visuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Кнопки, тумблеры, плавные слайдеры и иконки дока в стиле PulseVisuals.
 */
public class NeonButton extends ClickableWidget {
    public enum Style {
        TOGGLE,
        SLIDER,
        ACTION,
        DOCK_ICON,
        CLOSE_ICON
    }

    private final Supplier<String> label;
    private final BooleanSupplier state;
    private final Runnable action;
    private Runnable rightClickAction;
    private final Style style;
    private final int iconIndex;

    // Для слайдера
    private DoubleSupplier sliderGet;
    private DoubleConsumer sliderSet;
    private double sliderMin;
    private double sliderMax;
    private boolean sliderInteger;
    private boolean draggingSlider;

    private float hoverAnim = 0f;
    private float toggleAnim = -1f;

    public static final int BG = 0xE614141F;
    public static final int BG_HOVER = 0xF21C1C2B;
    public static final int NEON = 0xFF6C5CE7;
    public static final int NEON_BRIGHT = 0xFF8B78FF;
    public static final int TEXT = 0xFFE4E4F0;
    public static final int TEXT_DIM = 0xFF66667C;
    public static final int TEXT_ON = 0xFFFFFFFF;

    public NeonButton(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action) {
        this(x, y, w, h, label, state, action, state != null ? Style.TOGGLE : Style.ACTION, 0);
    }

    public NeonButton(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action, Style style) {
        this(x, y, w, h, label, state, action, style, 0);
    }

    public NeonButton(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action, Style style, int iconIndex) {
        super(x, y, w, h, Text.empty());
        this.label = label;
        this.state = state;
        this.action = action;
        this.style = style;
        this.iconIndex = iconIndex;
    }

    public static NeonButton slider(int x, int y, int w, int h, String title,
                                    double min, double max, boolean integer,
                                    DoubleSupplier get, DoubleConsumer set) {
        NeonButton b = new NeonButton(x, y, w, h, () -> title, null, () -> {}, Style.SLIDER, 0);
        b.sliderMin = min;
        b.sliderMax = max;
        b.sliderInteger = integer;
        b.sliderGet = get;
        b.sliderSet = set;
        return b;
    }

    public NeonButton withRightClick(Runnable onRightClick) {
        this.rightClickAction = onRightClick;
        return this;
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
            int bg = hover ? 0x66FF4D6D : 0x1AFFFFFF;
            fillRounded(ctx, x, y, w, h, bg);
            ctx.drawCenteredTextWithShadow(tr, "✕", x + w / 2, y + (h - 8) / 2, hover ? 0xFFFFFFFF : TEXT_DIM);
            return;
        }

        if (style == Style.DOCK_ICON) {
            boolean active = state != null && state.getAsBoolean();
            int bg = active ? NEON : (hover ? 0x2EFFFFFF : 0x14FFFFFF);
            fillRounded(ctx, x, y, w, h, bg);
            if (active) {
                ctx.fill(x + 2, y, x + w - 2, y + 1, 0x99C4B5FD);
                ctx.fill(x + 2, y + h, x + w - 2, y + h + 1, 0x446C5CE7);
            }
            drawDockGlyph(ctx, x + w / 2, y + h / 2, iconIndex, active ? 0xFFFFFFFF : (hover ? 0xFFD0D0E4 : 0xFF7A7A92));
            return;
        }

        if (style == Style.SLIDER) {
            double val = sliderGet != null ? sliderGet.getAsDouble() : sliderMin;
            double ratio = (sliderMax > sliderMin) ? Math.max(0.0, Math.min(1.0, (val - sliderMin) / (sliderMax - sliderMin))) : 0.0;
            String valStr = sliderInteger ? String.valueOf((int) Math.round(val)) : String.format("%.1f", val);

            ctx.drawText(tr, rawText, x + 2, y + 1, hover || draggingSlider ? TEXT_ON : TEXT, false);
            int vw = tr.getWidth(valStr);
            ctx.drawText(tr, valStr, x + w - vw - 2, y + 1, 0xFFB8A9FF, false);

            int barX = x + 2;
            int barY = y + h - 5;
            int barW = w - 4;
            int barH = 2;

            ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF28283A);
            int fillW = (int) Math.round(barW * ratio);
            if (fillW > 0) {
                ctx.fillGradient(barX, barY, barX + fillW, barY + barH, NEON, NEON_BRIGHT);
            }

            // Белый круглый бегунок как в PulseVisuals
            int kx = barX + fillW - 2;
            int ky = barY - 2;
            ctx.fill(kx, ky + 1, kx + 5, ky + 4, 0xFFFFFFFF);
            ctx.fill(kx + 1, ky, kx + 4, ky + 5, 0xFFFFFFFF);
            return;
        }

        // Карточка модуля (TOGGLE или ACTION)
        boolean isToggle = (style == Style.TOGGLE && state != null);
        boolean on = isToggle && state.getAsBoolean();

        int bgColor = isToggle
                ? (on ? (hover ? 0xF21B1A29 : 0xEB151522) : (hover ? 0xD814141E : 0xB80F0F17))
                : (hover ? BG_HOVER : BG);
        fillRounded(ctx, x, y, w, h, bgColor);

        int borderColor = on
                ? (hover ? 0x558B78FF : 0x26FFFFFF)
                : (hover ? 0x2EFFFFFF : 0x12FFFFFF);
        drawRoundedBorder(ctx, x, y, w, h, borderColor);

        if (isToggle) {
            if (toggleAnim < 0f) toggleAnim = on ? 1f : 0f;
            toggleAnim += ((on ? 1f : 0f) - toggleAnim) * 0.32f;

            String cleanName = rawText.replace(": ВКЛ", "").replace(": ВЫКЛ", "");
            int labelCol = on ? TEXT_ON : (hover ? 0xFF9A9AB2 : TEXT_DIM);
            ctx.drawText(tr, cleanName, x + 7, y + (h - 8) / 2, labelCol, false);

            // Маленькая точка-индикатор наличия ПКМ настроек
            if (rightClickAction != null) {
                int dotX = x + w - 33;
                int dotY = y + h / 2;
                ctx.fill(dotX, dotY - 1, dotX + 1, dotY + 1, on ? 0x999D8DF7 : 0x4466667C);
            }

            // Тумблер справа
            int tw = 22;
            int th = 10;
            int tx = x + w - tw - 6;
            int ty = y + (h - th) / 2;

            int trackBg = on ? NEON : 0xFF222230;
            fillRounded(ctx, tx, ty, tw, th, trackBg);
            if (on) {
                ctx.fill(tx + 1, ty - 1, tx + tw - 1, ty, 0x388B78FF);
                ctx.fill(tx + 1, ty + th, tx + tw - 1, ty + th + 1, 0x388B78FF);
            }

            int knobSize = 6;
            int travel = tw - knobSize - 4;
            int kx = tx + 2 + Math.round(travel * toggleAnim);
            int ky = ty + 2;
            ctx.fill(kx, ky, kx + knobSize, ky + knobSize, on ? 0xFFFFFFFF : 0xFF6E6E86);
        } else {
            int colonIdx = rawText.indexOf(':');
            if (colonIdx > 0 && colonIdx < rawText.length() - 1) {
                String k = rawText.substring(0, colonIdx + 1);
                String v = rawText.substring(colonIdx + 1).trim();
                ctx.drawText(tr, k, x + 7, y + (h - 8) / 2, 0xFFA2A2B8, false);
                int vw = tr.getWidth(v);
                ctx.drawText(tr, v, x + w - vw - 7, y + (h - 8) / 2, 0xFF9D8DF7, false);
            } else {
                ctx.drawCenteredTextWithShadow(tr, rawText, x + w / 2, y + (h - 8) / 2, hover ? TEXT_ON : TEXT);
            }
        }
    }

    private void drawDockGlyph(DrawContext ctx, int cx, int cy, int idx, int col) {
        switch (idx) {
            case 0 -> { // Эффекты (звезда / вспышка)
                ctx.fill(cx, cy - 4, cx + 1, cy + 5, col);
                ctx.fill(cx - 4, cy, cx + 5, cy + 1, col);
                ctx.fill(cx - 2, cy - 2, cx + 3, cy + 3, col);
            }
            case 1 -> { // HUD (сетка виджетов)
                ctx.fill(cx - 4, cy - 4, cx - 1, cy - 1, col);
                ctx.fill(cx + 1, cy - 4, cx + 4, cy - 1, col);
                ctx.fill(cx - 4, cy + 1, cx - 1, cy + 4, col);
                ctx.fill(cx + 1, cy + 1, cx + 4, cy + 4, col);
            }
            case 2 -> { // Прицел (перекрестие)
                ctx.fill(cx, cy - 4, cx + 1, cy - 1, col);
                ctx.fill(cx, cy + 2, cx + 1, cy + 5, col);
                ctx.fill(cx - 4, cy, cx - 1, cy + 1, col);
                ctx.fill(cx + 2, cy, cx + 5, cy + 1, col);
                ctx.fill(cx, cy, cx + 1, cy + 1, col);
            }
            case 3 -> { // Оптимизация / FunTime (молния)
                ctx.fill(cx, cy - 4, cx + 2, cy, col);
                ctx.fill(cx - 2, cy - 1, cx + 3, cy + 1, col);
                ctx.fill(cx - 1, cy, cx + 1, cy + 4, col);
            }
            default -> { // Профили (папка)
                ctx.fill(cx - 4, cy - 3, cx - 1, cy - 2, col);
                ctx.fill(cx - 4, cy - 2, cx + 4, cy + 3, col);
            }
        }
    }

    private void updateSliderFromMouse(double mouseX) {
        if (sliderSet == null) return;
        double barX = getX() + 2;
        double barW = Math.max(1, width - 4);
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - barX) / barW));
        double raw = sliderMin + ratio * (sliderMax - sliderMin);
        if (sliderInteger) {
            raw = Math.round(raw);
        } else {
            raw = Math.round(raw * 10.0) / 10.0;
        }
        sliderSet.accept(raw);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        if (style == Style.SLIDER && button == 0) {
            draggingSlider = true;
            updateSliderFromMouse(mouseX);
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }
        if (button == 1 && rightClickAction != null) {
            rightClickAction.run();
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }
        if (button == 0) {
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            onClick(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (style == Style.SLIDER && draggingSlider && button == 0) {
            updateSliderFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (style == Style.SLIDER && draggingSlider && button == 0) {
            draggingSlider = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
