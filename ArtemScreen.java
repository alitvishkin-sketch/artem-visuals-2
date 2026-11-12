package com.artem.visuals.gui;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.config.ProfileManager;
import com.artem.visuals.crosshair.CrosshairRenderer;
import com.artem.visuals.hud.HudManager;
import com.artem.visuals.optimize.Optimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Компактное всплывающее меню ArtemVisuals с пресетом под FunTime PvP.
 */
public class ArtemScreen extends Screen {
    private static final int W = 304;
    private static final int H = 166;
    private static final String[] TABS = {"Эффекты", "HUD", "Прицел", "Оптим.", "Профили"};
    private static final String[] LEVELS = {"Низкое", "Среднее", "Высокое"};
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Плюс"};

    private final Screen parent;
    private int tab = 0;
    private int left, top;
    private TextFieldWidget profileNameField;
    private String statusMessage = "";
    private long statusUntil = 0;
    private long openedAtMs = 0;

    public ArtemScreen(Screen parent) {
        super(Text.literal("ArtemVisuals"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (openedAtMs == 0) {
            openedAtMs = System.currentTimeMillis();
        }
        left = (width - W) / 2;
        top = (height - H) / 2 - 4;
        clearChildren();

        ArtemConfig c = ArtemConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        // Кнопка закрытия ✕
        addDrawableChild(new NeonButton(left + W - 20, top + 5, 14, 14,
                () -> "✕", null, this::close, NeonButton.Style.CLOSE_ICON));

        int y = top + 24;
        int cw = (W - 22) / 2;
        int x0 = left + 8;
        int x1 = x0 + cw + 6;
        int step = 19;

        switch (tab) {
            case 0 -> buildEffects(c, mc, x0, x1, y, cw, step);
            case 1 -> buildHud(c, x0, x1, y, cw, step);
            case 2 -> buildCrosshair(c, x0, x1, y, cw, step);
            case 3 -> buildOptimize(c, mc, x0, x1, y, cw, step);
            case 4 -> buildProfiles(x0, y, cw);
        }

        // Нижний плавающий док с вкладками
        int dockW = 248;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 7;
        int tabW = (dockW - 8 - 4 * 3) / 5;

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            addDrawableChild(new NeonButton(
                    dockLeft + 4 + i * (tabW + 3),
                    dockTop + 3,
                    tabW,
                    15,
                    () -> TABS[idx],
                    () -> tab == idx,
                    () -> {
                        tab = idx;
                        clearAndInit();
                    },
                    NeonButton.Style.DOCK_TAB
            ));
        }
    }

    private void buildEffects(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Все эффекты", () -> c.masterEnabled, v -> c.masterEnabled = v);
        toggle(x1, y, cw, "Листопад", () -> c.leafParticles, v -> c.leafParticles = v);
        toggle(x0, y + step, cw, "Дождь / снег", () -> c.precipitationEffects, v -> c.precipitationEffects = v);
        toggle(x1, y + step, cw, "Блики на воде", () -> c.waterSparkles, v -> c.waterSparkles = v);
        toggle(x0, y + step * 2, cw, "Светлячки", () -> c.fireflies, v -> c.fireflies = v);
        toggle(x1, y + step * 2, cw, "Ломание блоков", () -> c.blockBreakEffects, v -> c.blockBreakEffects = v);
        toggle(x0, y + step * 3, cw, "Мягкий свет", () -> c.softLighting, v -> {
            c.softLighting = v;
            Optimizer.applyLighting(mc);
        });
        toggle(x1, y + step * 3, cw, "Сглаж. свет", () -> c.smoothLighting, v -> {
            c.smoothLighting = v;
            Optimizer.applyLighting(mc);
        });
        cycle(x0, y + step * 4, cw, () -> "Плотность: " + LEVELS[c.density], () -> {
            c.density = (c.density + 1) % 3;
        });
        cycle(x1, y + step * 4, cw, () -> "Радиус: " + LEVELS[c.radius], () -> {
            c.radius = (c.radius + 1) % 3;
        });
        cycle(x0, y + step * 5, cw, () -> "Свет: " + LEVELS[c.lightingLevel], () -> {
            c.lightingLevel = (c.lightingLevel + 1) % 3;
            if (c.softLighting) Optimizer.applyLighting(mc);
        });
        toggle(x1, y + step * 5, cw, "Адаптивно к FPS", () -> c.adaptive, v -> c.adaptive = v);
        cycle(x0, y + step * 6, cw, () -> "Порог FPS: " + c.fpsThreshold(), () -> {
            c.adaptiveThreshold = (c.adaptiveThreshold + 1) % 3;
        });
    }

    private void buildHud(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Счётчик FPS", () -> c.showFps, v -> c.showFps = v);
        toggle(x1, y, cw, "Пинг (Ping)", () -> c.showPing, v -> c.showPing = v);
        toggle(x0, y + step, cw, "Координаты", () -> c.showXyz, v -> c.showXyz = v);
        toggle(x1, y + step, cw, "Клики (CPS)", () -> c.showCps, v -> c.showCps = v);
        toggle(x0, y + step * 2, cw, "Клавиши WASD", () -> c.showWasd, v -> c.showWasd = v);
        toggle(x1, y + step * 2, cw, "Инфо-бар (Верх)", () -> c.showServer, v -> c.showServer = v);
        toggle(x0, y + step * 3, cw, "Эффекты зелий", () -> HudManager.showPotions, v -> HudManager.showPotions = v);
        toggle(x1, y + step * 3, cw, "TargetHUD (Цель)", () -> HudManager.showTargetHud, v -> HudManager.showTargetHud = v);
        toggle(x0, y + step * 4 + 2, cw * 2 + 6, "Перетаскивание элементов HUD мышью",
                () -> c.hudEditMode, v -> c.hudEditMode = v);
    }

    private void buildCrosshair(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Свой прицел", () -> c.customCrosshair, v -> c.customCrosshair = v);
        cycle(x1, y, cw, () -> "Стиль: " + STYLES[Math.min(c.crosshairStyle, 3)], () -> {
            c.crosshairStyle = (c.crosshairStyle + 1) % 4;
        });
        cycle(x0, y + step, cw, () -> "Размер: " + c.crosshairSize, () -> {
            c.crosshairSize = c.crosshairSize >= 12 ? 3 : c.crosshairSize + 1;
        });
        cycle(x1, y + step, cw, () -> "Зазор: " + c.crosshairGap, () -> {
            c.crosshairGap = c.crosshairGap >= 8 ? 0 : c.crosshairGap + 1;
        });
        cycle(x0, y + step * 2, cw, () -> "Толщина: " + c.crosshairThickness, () -> {
            c.crosshairThickness = c.crosshairThickness >= 3 ? 1 : c.crosshairThickness + 1;
        });
        toggle(x1, y + step * 2, cw, "Обводка", () -> c.crosshairOutline, v -> c.crosshairOutline = v);
        toggle(x0, y + step * 3, cw, "Динамика", () -> c.crosshairDynamic, v -> c.crosshairDynamic = v);
        cycle(x1, y + step * 3, cw, () -> "Цвет: " + colorName(c.crosshairColor), () -> {
            c.crosshairColor = nextColor(c.crosshairColor);
        });
    }

    private void buildOptimize(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        addBtn(x0, y, cw * 2 + 6, 18, () -> "⚔ Пресет под FunTime PvP (HUD + FPS)", null, () -> {
            applyFunTimePreset(c, mc);
            setStatus("Пресет FunTime PvP активирован");
        });
        addBtn(x0, y + step + 3, cw * 2 + 6, 18, () -> "⚡ Применить пресет производительности", null, () -> {
            Optimizer.applyPerformancePreset(mc);
            setStatus("Пресет производительности применён");
        });
        addBtn(x0, y + step * 2 + 6, cw * 2 + 6, 18,
                () -> Optimizer.hasBackup() ? "↺ Вернуть прежние настройки видео" : "Бэкап настроек отсутствует",
                null, () -> {
                    if (Optimizer.hasBackup()) {
                        Optimizer.restoreSettings(mc);
                        setStatus("Настройки восстановлены");
                    } else {
                        setStatus("Нечего восстанавливать");
                    }
                });
        addBtn(x0, y + step * 3 + 9, cw * 2 + 6, 18, () -> "Сбросить настройки мода", null, () -> {
            c.resetDefaults();
            clearAndInit();
            setStatus("Сброшено к умолчаниям");
        });
    }

    private void applyFunTimePreset(ArtemConfig c, MinecraftClient mc) {
        c.showFps = true;
        c.showPing = true;
        c.showXyz = true;
        c.showCps = true;
        c.showWasd = true;
        c.showServer = true;
        HudManager.showPotions = true;
        HudManager.showTargetHud = true;

        c.customCrosshair = true;
        c.crosshairStyle = 0; // Крест с точкой
        c.crosshairSize = 4;
        c.crosshairGap = 2;
        c.crosshairThickness = 1;
        c.crosshairOutline = true;
        c.crosshairDynamic = true;
        c.crosshairColor = 0xFF00F5FF;

        c.adaptive = true;
        c.density = 0; // Низкая плотность частиц для высокого FPS в PvP
        c.save();
        Optimizer.applyPerformancePreset(mc);
    }

    private void buildProfiles(int x0, int y, int cw) {
        profileNameField = new TextFieldWidget(textRenderer, x0, y, cw * 2 + 6, 15, Text.literal("Имя профиля"));
        profileNameField.setMaxLength(28);
        profileNameField.setPlaceholder(Text.literal("Название профиля (напр. FunTime)..."));
        addDrawableChild(profileNameField);

        addBtn(x0, y + 19, cw, 16, () -> "Сохранить", null, () -> {
            String name = profileNameField.getText();
            if (ProfileManager.saveProfile(name)) {
                setStatus("Сохранено: " + name);
                clearAndInit();
            } else {
                setStatus("Введите имя");
            }
        });
        addBtn(x0 + cw + 6, y + 19, cw, 16, () -> "Обновить", null, this::clearAndInit);

        List<String> profiles = ProfileManager.listProfiles();
        int py = y + 39;
        int i = 0;
        for (String name : profiles) {
            if (i >= 5) break;
            final String n = name;
            addBtn(x0, py, cw * 2 - 36, 16, () -> "▶ " + n, null, () -> {
                if (ProfileManager.loadProfile(n)) {
                    setStatus("Загружен: " + n);
                    clearAndInit();
                }
            });
            addBtn(x0 + cw * 2 - 32, py, 38, 16, () -> "Удал.", null, () -> {
                ProfileManager.deleteProfile(n);
                setStatus("Удалён: " + n);
                clearAndInit();
            });
            py += 18;
            i++;
        }
    }

    // ——— helpers ———

    private void addBtn(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action) {
        addDrawableChild(new NeonButton(x, y, w, h, label, state, action));
    }

    private void toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addBtn(x, y, w, 16, () -> name, get, () -> {
            set.accept(!get.getAsBoolean());
            ArtemConfig.get().save();
        });
    }

    private void cycle(int x, int y, int w, Supplier<String> label, Runnable change) {
        addBtn(x, y, w, 16, label, null, () -> {
            change.run();
            ArtemConfig.get().save();
        });
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        statusUntil = System.currentTimeMillis() + 2200;
    }

    private static String colorName(int argb) {
        return switch (argb) {
            case 0xFF9B59FF -> "Фиолетовый";
            case 0xFF00F5FF -> "Циан";
            case 0xFFFF2D95 -> "Розовый";
            case 0xFF39FF14 -> "Зелёный";
            case 0xFFFFFFFF -> "Белый";
            default -> "Кастом";
        };
    }

    private static int nextColor(int current) {
        int[] palette = {0xFF9B59FF, 0xFF00F5FF, 0xFFFF2D95, 0xFF39FF14, 0xFFFFFFFF};
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == current) return palette[(i + 1) % palette.length];
        }
        return palette[0];
    }

    private float getOpenProgress() {
        long elapsed = System.currentTimeMillis() - openedAtMs;
        float t = Math.min(1f, Math.max(0f, elapsed / 180f));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float p = getOpenProgress();
        int dimAlpha = (int) (0x78 * p);
        ctx.fill(0, 0, width, height, dimAlpha << 24);

        NeonButton.fillRounded(ctx, left - 3, top - 2, W + 6, H + 5, 0x55000000);
        NeonButton.fillRounded(ctx, left, top, W, H, 0xEC0E0E16);
        NeonButton.drawRoundedBorder(ctx, left, top, W, H, 0x26FFFFFF);

        ctx.fill(left + 8, top + 20, left + W - 8, top + 21, 0x16FFFFFF);

        int dockW = 248;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 7;
        NeonButton.fillRounded(ctx, dockLeft, dockTop, dockW, 21, 0xEC0E0E16);
        NeonButton.drawRoundedBorder(ctx, dockLeft, dockTop, dockW, 21, 0x26FFFFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float p = getOpenProgress();
        float scale = 0.88f + 0.12f * p;
        float slideY = (1f - p) * 14f;

        int cx = left + W / 2;
        int cy = top + H / 2;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy + slideY, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.getMatrices().translate(-cx, -cy, 0);

        super.render(ctx, mouseX, mouseY, delta);

        drawBrandHeader(ctx);

        int bx = left + 9;
        int by = top + 7;
        ctx.drawText(textRenderer, "Visuals", bx, by, 0xFFF2F2FA, false);
        bx += textRenderer.getWidth("Visuals");
        ctx.drawText(textRenderer, " / ", bx, by, 0xFF55556A, false);
        bx += textRenderer.getWidth(" / ");
        ctx.drawText(textRenderer, TABS[tab], bx, by, 0xFF8E8EA4, false);

        int fps = MinecraftClient.getInstance().getCurrentFps();
        String fpsStr = fps + " FPS";
        ctx.drawText(textRenderer, fpsStr, left + W - 26 - textRenderer.getWidth(fpsStr), by, 0xFF6E6E85, false);

        if (tab == 2) {
            int px = left + W / 2;
            int py = top + 126;
            NeonButton.fillRounded(ctx, px - 22, py - 22, 44, 44, 0xFF151520);
            NeonButton.drawRoundedBorder(ctx, px - 22, py - 22, 44, 44, 0x26FFFFFF);
            CrosshairRenderer.renderPreview(ctx, px, py, ArtemConfig.get());
        }

        if (tab == 1 && ArtemConfig.get().hudEditMode) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Перетаскивайте элементы HUD мышью прямо на экране",
                    left + W / 2, top + 122, 0xFF9D8DF7);
        }

        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMessage, left + W / 2, top + H - 15, 0xFF8B78FF);
        }

        if (tab == 4 && ProfileManager.listProfiles().isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Нет сохранённых профилей", left + W / 2, top + 95, 0xFF6E6E85);
        }

        ctx.getMatrices().pop();
    }

    private void drawBrandHeader(DrawContext ctx) {
        String brand = "ArtemVisuals";
        String tag = "FunTime";
        int textW = textRenderer.getWidth(brand);
        int tagW = textRenderer.getWidth(tag) + 6;
        int iconW = 16;
        int totalW = iconW + 4 + textW + 6 + tagW;
        int startX = left + (W - totalW) / 2;
        int y = top - 15;

        int waveCol = 0xFF7B68EE;
        ctx.fill(startX, y + 5, startX + 4, y + 6, waveCol);
        ctx.fill(startX + 4, y + 2, startX + 5, y + 6, waveCol);
        ctx.fill(startX + 5, y + 1, startX + 6, y + 8, 0xFF9D8DF7);
        ctx.fill(startX + 6, y + 3, startX + 7, y + 9, waveCol);
        ctx.fill(startX + 7, y + 5, startX + 12, y + 6, waveCol);

        ctx.drawText(textRenderer, brand, startX + iconW, y + 1, 0xFFF4F4FA, true);

        int bx = startX + iconW + textW + 5;
        NeonButton.fillRounded(ctx, bx, y, tagW, 10, 0x446C5CE7);
        ctx.drawText(textRenderer, tag, bx + 3, y + 1, 0xFFB8A9FF, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        ArtemConfig.get().save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
