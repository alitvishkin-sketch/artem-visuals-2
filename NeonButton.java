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
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Современные UI-компоненты в стиле PulseVisuals: плавные скругления, градиенты, свечение, слайдеры и палитры.
 */
public class NeonButton extends ClickableWidget {
    public enum Style {
        TOGGLE,
        SLIDER,
        COLOR_SWATCHES,
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

    private DoubleSupplier sliderGet;
    private DoubleConsumer sliderSet;
    private double sliderMin;
    private double sliderMax;
    private boolean sliderInteger;
    private boolean draggingSlider;

    private int[] swatchPalette;
    private IntSupplier swatchGet;
    private IntConsumer swatchSet;

    private float hoverAnim = 0f;
    private float toggleAnim = -1f;

    public static final int ACCENT = 0xFF6C5CE7;
    public static final int ACCENT_BRIGHT = 0xFF9D8DF7;
    public static final int TEXT = 0xFFE6E6F2;
    public static final int TEXT_DIM = 0xFF686880;
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

    public static NeonButton swatches(int x, int y, int w, int h, String title,
                                      int[] palette, IntSupplier get, IntConsumer set) {
        NeonButton b = new NeonButton(x, y, w, h, () -> title, null, () -> {}, Style.COLOR_SWATCHES, 0);
        b.swatchPalette = palette;
        b.swatchGet = get;
        b.swatchSet = set;
        return b;
    }

    public NeonButton withRightClick(Runnable onRightClick) {
        this.rightClickAction = onRightClick;
        return this;
    }

    public boolean hasRightClick() {
        return rightClickAction != null;
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
            int bg = hover ? 0x88FF4D6D : 0x1CFFFFFF;
            fillSmoothRounded(ctx, x, y, w, h, 4, bg);
            ctx.drawCenteredTextWithShadow(tr, "✕", x + w / 2, y + (h - 8) / 2, hover ? 0xFFFFFFFF : TEXT_DIM);
            return;
        }

        if (style == Style.DOCK_ICON) {
            boolean active = state != null && state.getAsBoolean();
            if (active) {
                drawAmbientGlow(ctx, x, y, w, h, 5, ACCENT, 2);
                fillGradientRounded(ctx, x, y, w, h, 5, ACCENT_BRIGHT, ACCENT);
                drawSmoothRoundedOutline(ctx, x, y, w, h, 5, 0x66FFFFFF);
            } else {
                int bg = hover ? 0x2EFFFFFF : 0x12FFFFFF;
                fillSmoothRounded(ctx, x, y, w, h, 5, bg);
            }
            drawDockGlyph(ctx, x + w / 2, y + h / 2, iconIndex, active ? 0xFFFFFFFF : (hover ? 0xFFD8D8EC : 0xFF7D7D96));
            return;
        }

        if (style == Style.SLIDER) {
            double val = sliderGet != null ? sliderGet.getAsDouble() : sliderMin;
            double ratio = (sliderMax > sliderMin) ? Math.max(0.0, Math.min(1.0, (val - sliderMin) / (sliderMax - sliderMin))) : 0.0;
            String valStr = sliderInteger ? String.valueOf((int) Math.round(val)) : String.format("%.1f", val);

            ctx.drawText(tr, rawText, x + 1, y + 1, hover || draggingSlider ? TEXT_ON : TEXT, false);
            int vw = tr.getWidth(valStr);
            fillSmoothRounded(ctx, x + w - vw - 6, y, vw + 6, 10, 3, 0x286C5CE7);
            ctx.drawText(tr, valStr, x + w - vw - 3, y + 1, 0xFFC4B5FD, false);

            int barX = x + 2;
            int barY = y + h - 5;
            int barW = w - 4;
            int barH = 3;

            fillSmoothRounded(ctx, barX, barY, barW, barH, 1, 0xFF252536);
            int fillW = (int) Math.round(barW * ratio);
            if (fillW > 1) {
                fillGradientRounded(ctx, barX, barY, fillW, barH, 1, ACCENT, ACCENT_BRIGHT);
            }

            int kx = barX + fillW;
            int ky = barY + 1;
            drawCircle(ctx, kx, ky, 4, 0x448B78FF);
            drawCircle(ctx, kx, ky, 3, 0xFFFFFFFF);
            return;
        }

        if (style == Style.COLOR_SWATCHES && swatchPalette != null) {
            ctx.drawText(tr, rawText, x + 1, y + 2, TEXT, false);
            int current = swatchGet != null ? swatchGet.getAsInt() : swatchPalette[0];
            int count = swatchPalette.length;
            int r = 5;
            int spacing = 15;
            int startX = x + w - (count - 1) * spacing - r - 2;
            int cy = y + 6;
            for (int col : swatchPalette) {
                if (col == current) {
                    drawCircle(ctx, startX, cy, r + 2, 0x55FFFFFF);
                    drawCircle(ctx, startX, cy, r + 1, 0xFFFFFFFF);
                }
                drawCircle(ctx, startX, cy, r, col);
                startX += spacing;
            }
            return;
        }

        boolean isToggle = (style == Style.TOGGLE && state != null);
        boolean on = isToggle && state.getAsBoolean();

        if (isToggle) {
            if (on) {
                fillGradientRounded(ctx, x, y, w, h, 5, hover ? 0xF51E1D2F : 0xEB171726, hover ? 0xF5161524 : 0xEB11111C);
                drawSmoothRoundedOutline(ctx, x, y, w, h, 5, hover ? 0x668B78FF : 0x2E7B68EE);
            } else {
                fillSmoothRounded(ctx, x, y, w, h, 5, hover ? 0xDC151520 : 0xBA0F0F17);
                drawSmoothRoundedOutline(ctx, x, y, w, h, 5, hover ? 0x2AFFFFFF : 0x12FFFFFF);
            }

            if (toggleAnim < 0f) toggleAnim = on ? 1f : 0f;
            toggleAnim += ((on ? 1f : 0f) - toggleAnim) * 0.30f;

            int dotCol = on ? ACCENT_BRIGHT : (hover ? 0xFF5A5A72 : 0xFF343446);
            drawCircle(ctx, x + 8, y + h / 2, 2, dotCol);

            String cleanName = rawText.replace(": ВКЛ", "").replace(": ВЫКЛ", "");
            int labelCol = on ? TEXT_ON : (hover ? 0xFFA4A4BC : TEXT_DIM);
            ctx.drawText(tr, cleanName, x + 14, y + (h - 8) / 2, labelCol, false);

            if (rightClickAction != null) {
                int gx = x + w - 36;
                int gy = y + (h - 8) / 2;
                ctx.drawText(tr, "⚙", gx, gy, on ? 0xAA9D8DF7 : 0x44686880, false);
            }

            int tw = 24;
            int th = 12;
            int tx = x + w - tw - 6;
            int ty = y + (h - th) / 2;

            if (on) {
                drawAmbientGlow(ctx, tx, ty, tw, th, 6, ACCENT, 1);
                fillGradientRounded(ctx, tx, ty, tw, th, 6, ACCENT, ACCENT_BRIGHT);
            } else {
                fillSmoothRounded(ctx, tx, ty, tw, th, 6, 0xFF20202E);
                drawSmoothRoundedOutline(ctx, tx, ty, tw, th, 6, 0x1AFFFFFF);
            }

            int travel = tw - 12;
            int kcx = tx + 6 + Math.round(travel * toggleAnim);
            int kcy = ty + th / 2;
            drawCircle(ctx, kcx, kcy, 4, on ? 0xFFFFFFFF : 0xFF72728A);
        } else {
            fillGradientRounded(ctx, x, y, w, h, 5, hover ? 0xF51F1E32 : 0xEB161624, hover ? 0xF5171626 : 0xEB11111C);
            drawSmoothRoundedOutline(ctx, x, y, w, h, 5, hover ? 0x558B78FF : 0x20FFFFFF);

            int colonIdx = rawText.indexOf(':');
            if (colonIdx > 0 && colonIdx < rawText.length() - 1) {
                String k = rawText.substring(0, colonIdx + 1);
                String v = rawText.substring(colonIdx + 1).trim();
                ctx.drawText(tr, k, x + 8, y + (h - 8) / 2, 0xFFA4A4BC, false);
                int vw = tr.getWidth(v);
                fillSmoothRounded(ctx, x + w - vw - 11, y + 3, vw + 6, h - 6, 3, 0x266C5CE7);
                ctx.drawText(tr, v, x + w - vw - 8, y + (h - 8) / 2, 0xFFB8A9FF, false);
            } else {
                ctx.drawCenteredTextWithShadow(tr, rawText, x + w / 2, y + (h - 8) / 2, hover ? TEXT_ON : TEXT);
            }
        }
    }

    private void drawDockGlyph(DrawContext ctx, int cx, int cy, int idx, int col) {
        switch (idx) {
            case 0 -> {
                ctx.fill(cx, cy - 5, cx + 1, cy + 6, col);
                ctx.fill(cx - 5, cy, cx + 6, cy + 1, col);
                ctx.fill(cx - 2, cy - 2, cx + 3, cy + 3, col);
                ctx.fill(cx + 3, cy - 4, cx + 5, cy - 2, col);
            }
            case 1 -> {
                fillSmoothRounded(ctx, cx - 5, cy - 5, 4, 4, 1, col);
                fillSmoothRounded(ctx, cx + 1, cy - 5, 4, 4, 1, col);
                fillSmoothRounded(ctx, cx - 5, cy + 1, 4, 4, 1, col);
                fillSmoothRounded(ctx, cx + 1, cy + 1, 4, 4, 1, col);
            }
            case 2 -> {
                ctx.fill(cx, cy - 5, cx + 1, cy - 1, col);
                ctx.fill(cx, cy + 2, cx + 1, cy + 6, col);
                ctx.fill(cx - 5, cy, cx - 1, cy + 1, col);
                ctx.fill(cx + 2, cy, cx + 6, cy + 1, col);
                ctx.fill(cx, cy, cx + 1, cy + 1, col);
            }
            case 3 -> {
                ctx.fill(cx, cy - 5, cx + 2, cy, col);
                ctx.fill(cx - 3, cy - 1, cx + 4, cy + 1, col);
                ctx.fill(cx - 1, cy, cx + 1, cy + 5, col);
            }
            default -> {
                fillSmoothRounded(ctx, cx - 5, cy - 4, 5, 2, 1, col);
                fillSmoothRounded(ctx, cx - 5, cy - 3, 10, 7, 1, col);
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
        if (style == Style.COLOR_SWATCHES && button == 0 && swatchPalette != null && swatchSet != null) {
            int count = swatchPalette.length;
            int r = 5;
            int spacing = 15;
            int startX = getX() + width - (count - 1) * spacing - r - 2;
            for (int col : swatchPalette) {
                if (Math.abs(mouseX - startX) <= 7) {
                    swatchSet.accept(col);
                    playDownSound(MinecraftClient.getInstance().getSoundManager());
                    return true;
                }
                startX += spacing;
            }
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

    public static void fillSmoothRounded(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w / 2, h / 2)));
        if (r == 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    public static void fillGradientRounded(DrawContext ctx, int x, int y, int w, int h, int r, int topColor, int bottomColor) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w / 2, h / 2)));
        if (r == 0) {
            ctx.fillGradient(x, y, x + w, y + h, topColor, bottomColor);
            return;
        }
        ctx.fillGradient(x, y + r, x + w, y + h - r, topColor, bottomColor);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, topColor);
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, bottomColor);
        }
    }

    public static void drawSmoothRoundedOutline(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 1 || h <= 1) return;
        r = Math.max(0, Math.min(r, Math.min(w / 2, h / 2)));
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
        if (r == 0) {
            ctx.fill(x, y, x + w, y + 1, color);
            ctx.fill(x, y + h - 1, x + w, y + h, color);
            return;
        }
        int prevInset = r;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            int span = Math.max(1, prevInset - inset);
            if (i == 0) {
                ctx.fill(x + inset, y, x + w - inset, y + 1, color);
                ctx.fill(x + inset, y + h - 1, x + w - inset, y + h, color);
            } else {
                ctx.fill(x + inset, y + i, x + inset + span, y + i + 1, color);
                ctx.fill(x + w - inset - span, y + i, x + w - inset, y + i + 1, color);
                ctx.fill(x + inset, y + h - 1 - i, x + inset + span, y + h - i, color);
                ctx.fill(x + w - inset - span, y + h - 1 - i, x + w - inset, y + h - i, color);
            }
            prevInset = inset;
        }
    }

    public static void drawAmbientGlow(DrawContext ctx, int x, int y, int w, int h, int r, int baseColor, int layers) {
        int rgb = baseColor & 0x00FFFFFF;
        for (int i = 1; i <= layers; i++) {
            int alpha = Math.max(8, 34 - i * 10);
            int glowCol = (alpha << 24) | rgb;
            drawSmoothRoundedOutline(ctx, x - i, y - i, w + i * 2, h + i * 2, r + i, glowCol);
        }
    }

    public static void drawCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        if (r <= 0) {
            ctx.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
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
