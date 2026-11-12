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

public class NeonButton extends ClickableWidget {
    public enum Style {
        TOGGLE,
        SLIDER,
        HUE_SLIDER,
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

    public static int ACCENT = 0xFF7B68EE;
    public static int ACCENT_BRIGHT = 0xFFA495FF;
    public static int WINDOW_TOP = 0xF00E0E15;
    public static int WINDOW_BOTTOM = 0xF00A0A0F;
    public static int CARD_OFF = 0xCC13131C;
    public static int CARD_ON = 0xE6181824;

    // Отдельные цвета для HUD
    public static int HUD_ACCENT = 0xFF7B68EE;
    public static int HUD_ACCENT_BRIGHT = 0xFFA495FF;
    public static int HUD_BG_TOP = 0xE0101018;
    public static int HUD_BG_BOTTOM = 0xE00A0A10;

    public static final int TEXT = 0xFFE8E8F4;
    public static final int TEXT_DIM = 0xFF6A6A82;
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

        // 1. Тема меню
        if (c.rgbMode) {
            float hue = ((System.currentTimeMillis() % 4500L) / 4500f * c.rgbSpeed) % 1.0f;
            int rgb = Color.HSBtoRGB(hue, 0.70f, 0.96f);
            setClientAccent(0xFF000000 | (rgb & 0x00FFFFFF));
        } else if (c.useCustomHue) {
            int rgb = Color.HSBtoRGB(c.customHue, 0.70f, 0.96f);
            setClientAccent(0xFF000000 | (rgb & 0x00FFFFFF));
        } else {
            setClientAccent(c.clientAccent);
        }

        // 2. Цвет HUD (независимый и всегда актуальный)
        int hudBase;
        if (c.hudRainbow) {
            float hue = ((System.currentTimeMillis() % 4200L) / 4200f * Math.max(0.3f, c.rgbSpeed)) % 1.0f;
            hudBase = 0xFF000000 | (Color.HSBtoRGB(hue, 0.74f, 0.98f) & 0x00FFFFFF);
        } else if (c.hudUseCustomHue) {
            hudBase = 0xFF000000 | (Color.HSBtoRGB(c.hudHue, 0.74f, 0.98f) & 0x00FFFFFF);
        } else {
            hudBase = 0xFF000000 | (c.hudAccentColor & 0x00FFFFFF);
        }
        HUD_ACCENT = hudBase;
        HUD_ACCENT_BRIGHT = brighten(hudBase, 44);

        int hBg = c.hudBgColor & 0x00FFFFFF;
        int hAlpha = Math.max(110, Math.min(248, Math.round(c.hudBgAlpha * 255f)));
        int hr1 = Math.min(255, ((hBg >> 16) & 0xFF) + 5);
        int hg1 = Math.min(255, ((hBg >> 8) & 0xFF) + 5);
        int hb1 = Math.min(255, (hBg & 0xFF) + 8);
        HUD_BG_TOP = (hAlpha << 24) | (hr1 << 16) | (hg1 << 8) | hb1;
        HUD_BG_BOTTOM = (hAlpha << 24) | hBg;

        // 3. Фон окна меню
        int base = c.windowBgColor & 0x00FFFFFF;
        int alpha = Math.max(140, Math.min(252, Math.round(c.windowAlpha * 255f)));
        int r1 = Math.min(255, ((base >> 16) & 0xFF) + 4);
        int g1 = Math.min(255, ((base >> 8) & 0xFF) + 4);
        int b1 = Math.min(255, (base & 0xFF) + 6);
        WINDOW_TOP = (alpha << 24) | (r1 << 16) | (g1 << 8) | b1;
        WINDOW_BOTTOM = (alpha << 24) | base;

        int cr = Math.min(255, ((base >> 16) & 0xFF) + 9);
        int cg = Math.min(255, ((base >> 8) & 0xFF) + 9);
        int cb = Math.min(255, (base & 0xFF) + 13);
        CARD_OFF = (Math.min(245, alpha) << 24) | (cr << 16) | (cg << 8) | cb;

        int onR = Math.min(255, cr + 7);
        int onG = Math.min(255, cg + 7);
        int onB = Math.min(255, cb + 10);
        CARD_ON = (Math.min(250, alpha) << 24) | (onR << 16) | (onG << 8) | onB;
    }

    public static int getHudAccent(int offsetMs) {
        ArtemConfig c = ArtemConfig.get();
        if (c.hudRainbow) {
            float hue = (((System.currentTimeMillis() + offsetMs) % 4200L) / 4200f * Math.max(0.3f, c.rgbSpeed)) % 1.0f;
            return 0xFF000000 | (Color.HSBtoRGB(hue, 0.74f, 0.98f) & 0x00FFFFFF);
        }
        return HUD_ACCENT;
    }

    public static int getHudAccentBright(int offsetMs) {
        ArtemConfig c = ArtemConfig.get();
        if (c.hudRainbow) {
            return brighten(getHudAccent(offsetMs), 40);
        }
        return HUD_ACCENT_BRIGHT;
    }

    public static int getDynamicColor(int baseColor, int offsetMs) {
        ArtemConfig c = ArtemConfig.get();
        if (c.rgbMode) {
            float hue = (((System.currentTimeMillis() + offsetMs) % 4500L) / 4500f * c.rgbSpeed) % 1.0f;
            int rgb = Color.HSBtoRGB(hue, 0.68f, 0.98f);
            return 0xFF000000 | (rgb & 0x00FFFFFF);
        }
        if (c.useCustomHue) {
            return ACCENT;
        }
        return baseColor;
    }

    public static void setClientAccent(int primary) {
        ACCENT = 0xFF000000 | (primary & 0x00FFFFFF);
        ACCENT_BRIGHT = brighten(ACCENT, 42);
    }

    private static int brighten(int argb, int add) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + add);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + add);
        int b = Math.min(255, (argb & 0xFF) + add);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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

    public static NeonButton hueSlider(int x, int y, int w, int h, String title,
                                       DoubleSupplier get, DoubleConsumer set) {
        NeonButton b = new NeonButton(x, y, w, h, () -> title, null, () -> {}, Style.HUE_SLIDER, 0);
        b.sliderMin = 0.0;
        b.sliderMax = 1.0;
        b.sliderInteger = false;
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
            ctx.drawCenteredTextWithShadow(tr, "✕", x + w / 2, y + (h - 8) / 2, hover ? 0xFFFFFFFF : TEXT_DIM);
            return;
        }

        if (style == Style.DOCK_ICON) {
            boolean active = state != null && state.getAsBoolean();
            if (active) {
                drawAmbientGlow(ctx, x, y, w, h, 5, ACCENT, 2);
                fillGradientRounded(ctx, x, y, w, h, 5, ACCENT_BRIGHT, ACCENT);
                drawSmoothRoundedOutline(ctx, x, y, w, h, 5, 0x55FFFFFF);
            } else {
                int bg = hover ? 0x24FFFFFF : 0x10FFFFFF;
                fillSmoothRounded(ctx, x, y, w, h, 5, bg);
            }
            drawDockGlyph(ctx, x + w / 2, y + h / 2, iconIndex, active ? 0xFFFFFFFF : (hover ? 0xFFD4D4E8 : 0xFF6C6C84));
            return;
        }

        if (style == Style.SLIDER || style == Style.HUE_SLIDER) {
            double val = sliderGet != null ? sliderGet.getAsDouble() : sliderMin;
            double ratio = (sliderMax > sliderMin) ? Math.max(0.0, Math.min(1.0, (val - sliderMin) / (sliderMax - sliderMin))) : 0.0;
            String valStr = style == Style.HUE_SLIDER
                    ? Math.round(ratio * 360) + "°"
                    : (sliderInteger ? String.valueOf((int) Math.round(val)) : String.format("%.2f", val));

            drawSmallText(ctx, tr, rawText, x + 1, y + 1, hover || draggingSlider ? TEXT_ON : TEXT, 0.85f);
            int vw = Math.round(tr.getWidth(valStr) * 0.85f);
            drawSmallText(ctx, tr, valStr, x + w - vw - 1, y + 1, ACCENT_BRIGHT, 0.85f);

            int barX = x + 1;
            int barY = y + h - 4;
            int barW = w - 2;
            int barH = 3;

            if (style == Style.HUE_SLIDER) {
                int segs = 24;
                for (int i = 0; i < segs; i++) {
                    int sx1 = barX + (barW * i) / segs;
                    int sx2 = barX + (barW * (i + 1)) / segs;
                    int col = 0xFF000000 | (Color.HSBtoRGB(i / (float) segs, 0.78f, 0.98f) & 0x00FFFFFF);
                    ctx.fill(sx1, barY, sx2, barY + barH, col);
                }
            } else {
                fillSmoothRounded(ctx, barX, barY, barW, barH, 1, 0xFF232334);
                int fillW = (int) Math.round(barW * ratio);
                if (fillW > 1) {
                    fillGradientRounded(ctx, barX, barY, fillW, barH, 1, ACCENT, ACCENT_BRIGHT);
                }
            }

            int kx = barX + (int) Math.round(barW * ratio);
            int ky = barY + 1;
            drawCircle(ctx, kx, ky, 3, (ACCENT & 0x00FFFFFF) | 0x66000000);
            drawCircle(ctx, kx, ky, 2, 0xFFFFFFFF);
            return;
        }

        if (style == Style.COLOR_SWATCHES && swatchPalette != null) {
            drawSmallText(ctx, tr, rawText, x + 1, y + 3, TEXT, 0.85f);
            int current = swatchGet != null ? swatchGet.getAsInt() : swatchPalette[0];
            int count = swatchPalette.length;
            int r = 4;
            int spacing = 11;
            int startX = x + w - (count - 1) * spacing - r - 2;
            int cy = y + h / 2;
            for (int col : swatchPalette) {
                int fullCol = 0xFF000000 | (col & 0x00FFFFFF);
                if ((col & 0x00FFFFFF) == (current & 0x00FFFFFF)) {
                    drawCircle(ctx, startX, cy, r + 2, 0x66FFFFFF);
                    drawCircle(ctx, startX, cy, r + 1, 0xFFFFFFFF);
                }
                drawCircle(ctx, startX, cy, r, fullCol);
                startX += spacing;
            }
            return;
        }

        boolean isToggle = (style == Style.TOGGLE && state != null);
        boolean on = isToggle && state.getAsBoolean();

        if (isToggle) {
            if (on) {
                fillSmoothRounded(ctx, x, y, w, h, 5, hover ? lighten(CARD_ON, 6) : CARD_ON);
                drawSmoothRoundedOutline(ctx, x, y, w, h, 5,
                        hover ? ((ACCENT_BRIGHT & 0x00FFFFFF) | 0x55000000) : ((ACCENT & 0x00FFFFFF) | 0x28000000));
            } else {
                fillSmoothRounded(ctx, x, y, w, h, 5, hover ? lighten(CARD_OFF, 5) : CARD_OFF);
                drawSmoothRoundedOutline(ctx, x, y, w, h, 5, hover ? 0x1EFFFFFF : 0x0EFFFFFF);
            }

            if (toggleAnim < 0f) toggleAnim = on ? 1f : 0f;
            toggleAnim += ((on ? 1f : 0f) - toggleAnim) * 0.30f;

            int labelCol = on ? TEXT_ON : (hover ? 0xFF9C9CB4 : TEXT_DIM);
            drawSmallText(ctx, tr, rawText, x + 7, y + (h - 7) / 2f, labelCol, 0.88f);

            int tw = 20;
            int th = 10;
            int tx = x + w - tw - 6;
            int ty = y + (h - th) / 2;

            if (on) {
                fillGradientRounded(ctx, tx, ty, tw, th, 5, ACCENT, ACCENT_BRIGHT);
            } else {
                fillSmoothRounded(ctx, tx, ty, tw, th, 5, 0xFF1E1E2B);
                drawSmoothRoundedOutline(ctx, tx, ty, tw, th, 5, 0x16FFFFFF);
            }

            int travel = tw - 10;
            int kcx = tx + 5 + Math.round(travel * toggleAnim);
            int kcy = ty + th / 2;
            drawCircle(ctx, kcx, kcy, 3, on ? 0xFFFFFFFF : 0xFF6B6B82);
        } else {
            fillSmoothRounded(ctx, x, y, w, h, 5, hover ? lighten(CARD_ON, 6) : CARD_OFF);
            drawSmoothRoundedOutline(ctx, x, y, w, h, 5,
                    hover ? ((ACCENT_BRIGHT & 0x00FFFFFF) | 0x55000000) : 0x18FFFFFF);

            int colonIdx = rawText.indexOf(':');
            if (colonIdx > 0 && colonIdx < rawText.length() - 1) {
                String k = rawText.substring(0, colonIdx + 1);
                String v = rawText.substring(colonIdx + 1).trim();
                drawSmallText(ctx, tr, k, x + 7, y + (h - 7) / 2f, 0xFF9A9AB2, 0.88f);
                int vw = Math.round(tr.getWidth(v) * 0.88f);
                drawSmallText(ctx, tr, v, x + w - vw - 7, y + (h - 7) / 2f, ACCENT_BRIGHT, 0.88f);
            } else {
                int tw = Math.round(tr.getWidth(rawText) * 0.88f);
                drawSmallText(ctx, tr, rawText, x + (w - tw) / 2f, y + (h - 7) / 2f, hover ? TEXT_ON : TEXT, 0.88f);
            }
        }
    }

    private static int lighten(int argb, int add) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + add);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + add);
        int b = Math.min(255, (argb & 0xFF) + add);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawDockGlyph(DrawContext ctx, int cx, int cy, int idx, int col) {
        switch (idx) {
            case 0 -> {
                drawCircle(ctx, cx, cy, 4, col);
                drawCircle(ctx, cx, cy, 2, 0xFF141422);
                ctx.fill(cx, cy, cx + 1, cy + 1, col);
            }
            case 1 -> {
                fillSmoothRounded(ctx, cx - 4, cy - 4, 3, 3, 1, col);
                fillSmoothRounded(ctx, cx + 1, cy - 4, 3, 3, 1, col);
                fillSmoothRounded(ctx, cx - 4, cy + 1, 3, 3, 1, col);
                fillSmoothRounded(ctx, cx + 1, cy + 1, 3, 3, 1, col);
            }
            case 2 -> {
                // Иконка меча
                ctx.fill(cx + 2, cy - 4, cx + 4, cy - 2, col);
                ctx.fill(cx + 1, cy - 3, cx + 3, cy - 1, col);
                ctx.fill(cx, cy - 2, cx + 2, cy, col);
                ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, col);
                ctx.fill(cx - 3, cy, cx + 1, cy + 1, col);
                ctx.fill(cx - 1, cy, cx, cy + 3, col);
                ctx.fill(cx - 3, cy + 2, cx - 1, cy + 4, col);
            }
            default -> {
                fillSmoothRounded(ctx, cx - 4, cy - 3, 4, 2, 1, col);
                fillSmoothRounded(ctx, cx - 4, cy - 2, 8, 6, 1, col);
            }
        }
    }

    private void updateSliderFromMouse(double mouseX) {
        if (sliderSet == null) return;
        double barX = getX() + 1;
        double barW = Math.max(1, width - 2);
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
        if ((style == Style.SLIDER || style == Style.HUE_SLIDER) && button == 0) {
            draggingSlider = true;
            updateSliderFromMouse(mouseX);
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }
        if (style == Style.COLOR_SWATCHES && button == 0 && swatchPalette != null && swatchSet != null) {
            int count = swatchPalette.length;
            int r = 4;
            int spacing = 11;
            int startX = getX() + width - (count - 1) * spacing - r - 2;
            for (int col : swatchPalette) {
                if (Math.abs(mouseX - startX) <= 5) {
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
        if ((style == Style.SLIDER || style == Style.HUE_SLIDER) && draggingSlider && button == 0) {
            updateSliderFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if ((style == Style.SLIDER || style == Style.HUE_SLIDER) && draggingSlider && button == 0) {
            draggingSlider = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public static void drawSmallText(DrawContext ctx, TextRenderer tr, String text, float x, float y, int color, float scale) {
        if (Math.abs(scale - 1.0f) < 0.02f) {
            ctx.drawText(tr, text, Math.round(x), Math.round(y), color, false);
            return;
        }
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
            int alpha = Math.max(8, 28 - i * 9);
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
