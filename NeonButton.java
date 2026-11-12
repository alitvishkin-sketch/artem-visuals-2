package com.artem.visuals.gui;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * UI-компоненты в стиле PulseVisual: скруглённые карточки, пилюли-тумблеры, слайдеры, RGB и выбор цвета.
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

    public static int ACCENT = 0xFF6C5CE7;
    public static int ACCENT_BRIGHT = 0xFF9D8DF7;
    public static final int TEXT = 0xFFE6E6F2;
    public static final int TEXT_DIM = 0xFF56566C;
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

    public static void syncTheme() {
        ArtemConfig c = ArtemConfig.get();
        if (c.rgbMode) {
            float hue = ((System.currentTimeMillis() % 4000L) / 4000f * c.rgbSpeed) % 1.0f;
            int rgb = Color.HSBtoRGB(hue, 0.72f, 0.96f);
            setClientAccent(0xFF000000 | (rgb & 0x00FFFFFF));
        } else {
            setClientAccent(c.clientAccent);
        }
    }

    public static int getDynamicColor(int baseColor, int offsetMs) {
        ArtemConfig c = ArtemConfig.get();
        if (!c.rgbMode) return baseColor;
        float hue = (((System.currentTimeMillis() + offsetMs) % 4000L) / 4000f * c.rgbSpeed) % 1.0f;
        int rgb = Color.HSBtoRGB(hue, 0.72f, 0.98f);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    public static void setClientAccent(int primary) {
        ACCENT = primary;
        int r = Math.min(255, ((primary >> 16) & 0xFF) + 46);
        int g = Math.min(255, ((primary >> 8) & 0xFF) + 46);
        int b = Math.min(255, (primary & 0xFF) + 46);
        ACCENT_BRIGHT = 0xFF000000 | (r << 16) | (g << 8) | b;
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

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean hover = isHovered();
        hoverAnim += ((hover ? 1f : 0f) - hoverAnim) * 0.25f;

        String rawText = label.get();
        setMessage(Text.literal(rawText));
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        int x = getX(), y = getY(), w = width, h = height;

        if (style == Style.CLOSE_ICON) {
            int bg = hover ? 0x88FF4D6D : 0x16FFFFFF;
            fillSmoothRounded(ctx, x, y, w, h, 4, bg);
            drawSmallText(ctx, tr, "✕", x + w / 2f - 2.2f, y + (h - 6) / 2f, hover ? 0xFFFFFFFF : TEXT_DIM, 0.8f);
            return;
        }

        if (style == Style.DOCK_ICON) {
            boolean active = state != null && state.getAsBoolean();
            if (active) {
                drawAmbientGlow(ctx, x, y, w, h, 5, ACCENT, 2);
                fillGradientRounded(ctx, x, y, w, h, 5, ACCENT_BRIGHT, ACCENT);
                drawSmoothRoundedOutline(ctx, x, y, w, h, 5, 0x55FFFFFF);
            } else {
                int bg = hover ? 0x26FFFFFF : 0x10FFFFFF;
                fillSmoothRounded(ctx, x, y, w, h, 5, bg);
            }
            drawDockGlyph(ctx, x + w / 2, y + h / 2, iconIndex, active ? 0xFFFFFFFF : (hover ? 0xFFD0D0E4 : 0xFF6E6E88));
            return;
        }

        if (style == Style.SLIDER) {
            double val = sliderGet != null ? sliderGet.getAsDouble() : sliderMin;
            double ratio = (sliderMax > sliderMin) ? Math.max(0.0, Math.min(1.0, (val - sliderMin) / (sliderMax - sliderMin))) : 0.0;
            String valStr = sliderInteger ? String.valueOf((int) Math.round(val)) : String.format("%.2f", val);

            drawSmallText(ctx, tr, rawText, x + 1, y + 1, hover || draggingSlider ? TEXT_ON : TEXT, 0.82f);
            int vw = Math.round(tr.getWidth(valStr) * 0.82f);
            drawSmallText(ctx, tr, valStr, x + w - vw - 2, y + 1, ACCENT_BRIGHT, 0.82f);

            int barX = x + 2;
            int barY = y + h - 4;
            int barW = w - 4;
            int barH = 2;

            fillSmoothRounded(ctx, barX, barY, barW, barH, 1, 0xFF232334);
            int fillW = (int) Math.round(barW * ratio);
            if (fillW > 1) {
                fillGradientRounded(ctx, barX, barY, fillW, barH, 1, ACCENT, ACCENT_BRIGHT);
            }

            int kx = barX + fillW;
            int ky = barY + 1;
            drawCircle(ctx, kx, ky, 4, (ACCENT & 0x00FFFFFF) | 0x44000000);
            drawCircle(ctx, kx, ky, 2, 0xFFFFFFFF);
            return;
        }

        if (style == Style.COLOR_SWATCHES && swatchPalette != null) {
            drawSmallText(ctx, tr, rawText, x + 1, y + 3, TEXT, 0.82f);
            int current = swatchGet != null ? swatchGet.getAsInt() : swatchPalette[0];
            int count = swatchPalette.length;
            int r = 4;
            int spacing = 12;
            int startX = x + w - (count - 1) * spacing - r - 2;
            int cy = y + 6;
            for (int col : swatchPalette) {
                if (col == current) {
                    drawCircle(ctx, startX, cy, r + 2, 0x66FFFFFF);
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
                fillGradientRounded(ctx, x, y, w, h, 5, hover ? 0xF5191828 : 0xEB131320, hover ? 0xF512121E : 0xEB0E0E18);
                drawSmoothRoundedOutline(ctx, x, y, w, h, 5, hover ? ((ACCENT_BRIGHT & 0x00FFFFFF) | 0x66000000) : ((ACCENT & 0x00FFFFFF) | 0x30000000));
            } else {
                fillSmoothRounded(ctx, x, y, w, h, 5, hover ? 0xD813131D : 0xB80D0D15);
                drawSmoothRoundedOutline(ctx, x, y, w, h, 5, hover ? 0x22FFFFFF : 0x10FFFFFF);
            }

            if (toggleAnim < 0f) toggleAnim = on ? 1f : 0f;
            toggleAnim += ((on ? 1f : 0f) - toggleAnim) * 0.30f;

            String cleanName = rawText.replace(": ВКЛ", "").replace(": ВЫКЛ", "");
            int labelCol = on ? TEXT_ON : (hover ? 0xFF8C8CA4 : TEXT_DIM);
            drawSmallText(ctx, tr, cleanName, x + 7, y + (h - 7) / 2f, labelCol, 0.85f);

            int tw = 20;
            int th = 10;
            int tx = x + w - tw - 5;
            int ty = y + (h - th) / 2;

            if (on) {
                drawAmbientGlow(ctx, tx, ty, tw, th, 5, ACCENT, 1);
                fillGradientRounded(ctx, tx, ty, tw, th, 5, ACCENT, ACCENT_BRIGHT);
            } else {
                fillSmoothRounded(ctx, tx, ty, tw, th, 5, 0xFF1C1C28);
                drawSmoothRoundedOutline(ctx, tx, ty, tw, th, 5, 0x18FFFFFF);
            }

            int travel = tw - 10;
            int kcx = tx + 5 + Math.round(travel * toggleAnim);
            int kcy = ty + th / 2;
            drawCircle(ctx, kcx, kcy, 3, on ? 0xFFFFFFFF : 0xFF686880);
        } else {
            fillGradientRounded(ctx, x, y, w, h, 5, hover ? 0xF51B1A2B : 0xEB131320, hover ? 0xF5131320 : 0xEB0E0E18);
            drawSmoothRoundedOutline(ctx, x, y, w, h, 5, hover ? ((ACCENT_BRIGHT & 0x00FFFFFF) | 0x55000000) : 0x1CFFFFFF);

            int colonIdx = rawText.indexOf(':');
            if (colonIdx > 0 && colonIdx < rawText.length() - 1) {
                String k = rawText.substring(0, colonIdx + 1);
                String v = rawText.substring(colonIdx + 1).trim();
                drawSmallText(ctx, tr, k, x + 7, y + (h - 7) / 2f, 0xFF9A9AB2, 0.84f);
                int vw = Math.round(tr.getWidth(v) * 0.84f);
                drawSmallText(ctx, tr, v, x + w - vw - 7, y + (h - 7) / 2f, ACCENT_BRIGHT, 0.84f);
            } else {
                float tw = tr.getWidth(rawText) * 0.84f;
                drawSmallText(ctx, tr, rawText, x + (w - tw) / 2f, y + (h - 7) / 2f, hover ? TEXT_ON : TEXT, 0.84f);
            }
        }
    }

    private void drawDockGlyph(DrawContext ctx, int cx, int cy, int idx, int col) {
        switch (idx) {
            case 0 -> { // Visual
                drawCircle(ctx, cx, cy, 4, col);
                drawCircle(ctx, cx, cy, 2, 0xFF141422);
                ctx.fill(cx, cy, cx + 1, cy + 1, col);
            }
            case 1 -> { // HUD
                fillSmoothRounded(ctx, cx - 4, cy - 4, 3, 3, 1, col);
                fillSmoothRounded(ctx, cx + 1, cy - 4, 3, 3, 1, col);
                fillSmoothRounded(ctx, cx - 4, cy + 1, 3, 3, 1, col);
                fillSmoothRounded(ctx, cx + 1, cy + 1, 3, 3, 1, col);
            }
            case 2 -> { // Crosshair
                ctx.fill(cx, cy - 4, cx + 1, cy - 1, col);
                ctx.fill(cx, cy + 2, cx + 1, cy + 5, col);
                ctx.fill(cx - 4, cy, cx - 1, cy + 1, col);
                ctx.fill(cx + 2, cy, cx + 5, cy + 1, col);
                ctx.fill(cx, cy, cx + 1, cy + 1, col);
            }
            case 3 -> { // FunTime / Perf
                ctx.fill(cx, cy - 4, cx + 2, cy, col);
                ctx.fill(cx - 3, cy - 1, cx + 4, cy + 1, col);
                ctx.fill(cx - 1, cy, cx + 1, cy + 4, col);
            }
            default -> { // Settings & Configs
                fillSmoothRounded(ctx, cx - 4, cy - 3, 4, 2, 1, col);
                fillSmoothRounded(ctx, cx - 4, cy - 2, 8, 6, 1, col);
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
            raw = Math.round(raw * 100.0) / 100.0;
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
            int r = 4;
            int spacing = 12;
            int startX = getX() + width - (count - 1) * spacing - r - 2;
            for (int col : swatchPalette) {
                if (Math.abs(mouseX - startX) <= 6) {
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

    public static void drawSmallText(DrawContext ctx, TextRenderer tr, String text, float x, float y, int color, float scale) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, text, 0, 0, color, false);
        ctx.getMatrices().pop();
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
            int alpha = Math.max(8, 30 - i * 10);
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
