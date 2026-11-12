package com.artem.visuals.gui;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.config.ProfileManager;
import com.artem.visuals.crosshair.CrosshairRenderer;
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
 * Компактное неоновое меню Artem Visuals 2.0 — полностью помещается на любом экране.
 */
public class ArtemScreen extends Screen {
    private static final int W = 390;
    private static final int H = 218;
    private static final String[] TABS = {"Эффекты", "HUD", "Прицел", "Оптимизация", "Профили"};
    private static final String[] LEVELS = {"Низкое", "Среднее", "Высокое"};
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Плюс"};

    private final Screen parent;
    private int tab = 0;
    private int left, top;
    private TextFieldWidget profileNameField;
    private String statusMessage = "";
    private long statusUntil = 0;

    public ArtemScreen(Screen parent) {
        super(Text.literal("ARTEM VISUALS"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        clearChildren();
        ArtemConfig c = ArtemConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        // Вкладки
        int tabW = (W - 20 - 4 * 3) / 5;
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            addBtn(left + 10 + i * (tabW + 3), top + 26, tabW, 16,
                    () -> TABS[idx], null, () -> {
                        tab = idx;
                        clearAndInit();
                    });
        }

        int y = top + 48;
        int cw = (W - 26) / 2;
        int x0 = left + 10, x1 = x0 + cw + 6;
        int step = 19;

        switch (tab) {
            case 0 -> buildEffects(c, mc, x0, x1, y, cw, step);
            case 1 -> buildHud(c, x0, x1, y, cw, step);
            case 2 -> buildCrosshair(c, x0, x1, y, cw, step);
            case 3 -> buildOptimize(c, mc, x0, x1, y, cw, step);
            case 4 -> buildProfiles(x0, y, cw);
        }

        addBtn(left + W / 2 - 48, top + H - 22, 96, 16, () -> "Закрыть", null, this::close);
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
        toggle(x1, y + step * 3, cw, "Сглаж. освещение", () -> c.smoothLighting, v -> {
            c.smoothLighting = v;
            Optimizer.applyLighting(mc);
        });
        cycle(x0, y + step * 4, cw, () -> "Плотность: " + LEVELS[c.density], () -> {
            c.density = (c.density + 1) % 3;
        });
        cycle(x1, y + step * 4, cw, () -> "Радиус: " + LEVELS[c.radius], () -> {
            c.radius = (c.radius + 1) % 3;
        });
        cycle(x0, y + step * 5, cw, () -> "Уровень света: " + LEVELS[c.lightingLevel], () -> {
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
        toggle(x0, y + step, cw, "Координаты XYZ", () -> c.showXyz, v -> c.showXyz = v);
        toggle(x1, y + step, cw, "Клики в сек (CPS)", () -> c.showCps, v -> c.showCps = v);
        toggle(x0, y + step * 2, cw, "Клавиши WASD", () -> c.showWasd, v -> c.showWasd = v);
        toggle(x1, y + step * 2, cw, "Текущий сервер", () -> c.showServer, v -> c.showServer = v);
        toggle(x0, y + step * 3 + 4, cw * 2 + 6, "Режим перетаскивания HUD на экране",
                () -> c.hudEditMode, v -> c.hudEditMode = v);
    }

    private void buildCrosshair(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Кастомный прицел", () -> c.customCrosshair, v -> c.customCrosshair = v);
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
        toggle(x1, y + step * 2, cw, "Чёрная обводка", () -> c.crosshairOutline, v -> c.crosshairOutline = v);
        toggle(x0, y + step * 3, cw, "Динамический", () -> c.crosshairDynamic, v -> c.crosshairDynamic = v);
        cycle(x1, y + step * 3, cw, () -> "Цвет: " + colorName(c.crosshairColor), () -> {
            c.crosshairColor = nextColor(c.crosshairColor);
        });
    }

    private void buildOptimize(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        addBtn(x0, y, cw * 2 + 6, 20, () -> "⚡ Применить пресет производительности", null, () -> {
            Optimizer.applyPerformancePreset(mc);
            setStatus("Пресет производительности применён");
        });
        addBtn(x0, y + step + 5, cw * 2 + 6, 20,
                () -> Optimizer.hasBackup() ? "↺ Вернуть прежние настройки видео" : "Бэкап настроек отсутствует",
                null, () -> {
                    if (Optimizer.hasBackup()) {
                        Optimizer.restoreSettings(mc);
                        setStatus("Настройки видео восстановлены");
                    } else {
                        setStatus("Нечего восстанавливать");
                    }
                });
        addBtn(x0, y + step * 2 + 10, cw * 2 + 6, 20, () -> "Сбросить все настройки мода", null, () -> {
            c.resetDefaults();
            clearAndInit();
            setStatus("Настройки сброшены");
        });
    }

    private void buildProfiles(int x0, int y, int cw) {
        profileNameField = new TextFieldWidget(textRenderer, x0, y, cw * 2 + 6, 16, Text.literal("Имя профиля"));
        profileNameField.setMaxLength(32);
        profileNameField.setPlaceholder(Text.literal("Введите имя профиля..."));
        addDrawableChild(profileNameField);

        addBtn(x0, y + 20, cw, 17, () -> "Сохранить профиль", null, () -> {
            String name = profileNameField.getText();
            if (ProfileManager.saveProfile(name)) {
                setStatus("Профиль «" + name + "» сохранён");
                clearAndInit();
            } else {
                setStatus("Введите корректное имя профиля");
            }
        });
        addBtn(x0 + cw + 6, y + 20, cw, 17, () -> "Обновить список", null, this::clearAndInit);

        List<String> profiles = ProfileManager.listProfiles();
        int py = y + 42;
        int i = 0;
        for (String name : profiles) {
            if (i >= 4) break;
            final String n = name;
            addBtn(x0, py, cw * 2 - 48, 17, () -> "▶ Загрузить: " + n, null, () -> {
                if (ProfileManager.loadProfile(n)) {
                    setStatus("Загружен профиль: " + n);
                    clearAndInit();
                } else {
                    setStatus("Ошибка загрузки");
                }
            });
            addBtn(x0 + cw * 2 - 44, py, 50, 17, () -> "Удал.", null, () -> {
                ProfileManager.deleteProfile(n);
                setStatus("Удалён: " + n);
                clearAndInit();
            });
            py += 19;
            i++;
        }
    }

    // ——— helpers ———

    private void addBtn(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action) {
        addDrawableChild(new NeonButton(x, y, w, h, label, state, action));
    }

    private void toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addBtn(x, y, w, 17, () -> name, get, () -> {
            set.accept(!get.getAsBoolean());
            ArtemConfig.get().save();
        });
    }

    private void cycle(int x, int y, int w, Supplier<String> label, Runnable change) {
        addBtn(x, y, w, 17, label, null, () -> {
            change.run();
            ArtemConfig.get().save();
        });
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    private static String colorName(int argb) {
        return switch (argb) {
            case 0xFF9B59FF -> "Фиолетовый";
            case 0xFF00F5FF -> "Циан";
            case 0xFFFF2D95 -> "Розовый";
            case 0xFF39FF14 -> "Неон-зелёный";
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

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Затемнение фона
        ctx.fill(0, 0, width, height, 0xA805040A);

        // Тень под панелью
        ctx.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0x55000000);

        // Основная панель (тёмное стекло)
        ctx.fill(left, top, left + W, top + H, 0xF20B0917);

        // Градиентная шапка
        ctx.fillGradient(left + 1, top + 1, left + W - 1, top + 23, 0xFF1F143D, 0xFF0E0B1E);

        // Неоновая рамка
        int neon = NeonButton.NEON;
        ctx.fill(left + 1, top, left + W - 1, top + 1, neon);
        ctx.fill(left + 1, top + H - 1, left + W - 1, top + H, neon);
        ctx.fill(left, top + 1, left + 1, top + H - 1, neon);
        ctx.fill(left + W - 1, top + 1, left + W, top + H - 1, neon);

        // Разделитель под шапкой
        ctx.fill(left + 8, top + 22, left + W - 8, top + 23, 0x669B59FF);

        // Индикатор активной вкладки
        int tabW = (W - 20 - 4 * 3) / 5;
        int activeTabX = left + 10 + tab * (tabW + 3);
        ctx.fill(activeTabX + 2, top + 41, activeTabX + tabW - 2, top + 43, 0xFF38F2AF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        // Заголовок + стильный бейдж версии
        String title = "♛ ARTEM VISUALS";
        ctx.drawText(textRenderer, title, left + 10, top + 7, 0xFFF2EAFF, true);

        int badgeX = left + 12 + textRenderer.getWidth(title);
        ctx.fill(badgeX, top + 6, badgeX + 22, top + 16, 0x4438F2AF);
        ctx.drawText(textRenderer, "2.0", badgeX + 3, top + 7, 0xFF4CFFBE, false);

        // Живой счётчик FPS в правом углу шапки
        int fps = MinecraftClient.getInstance().getCurrentFps();
        String f = fps + " FPS";
        int col = fps >= 60 ? 0xFF4CFFBE : (fps >= 30 ? 0xFFC77DFF : 0xFFFF5566);
        ctx.drawText(textRenderer, f, left + W - 10 - textRenderer.getWidth(f), top + 7, col, true);

        // Компактный предпросмотр прицела
        if (tab == 2) {
            int px = left + W / 2;
            int py = top + 152;
            ctx.fill(px - 24, py - 24, px + 24, py + 24, 0xFF141124);
            ctx.fill(px - 24, py - 24, px + 24, py - 23, NeonButton.NEON_DIM);
            ctx.fill(px - 24, py + 23, px + 24, py + 24, NeonButton.NEON_DIM);
            ctx.fill(px - 24, py - 24, px - 23, py + 24, NeonButton.NEON_DIM);
            ctx.fill(px + 23, py - 24, px + 24, py + 24, NeonButton.NEON_DIM);
            CrosshairRenderer.renderPreview(ctx, px, py, ArtemConfig.get());
            ctx.drawCenteredTextWithShadow(textRenderer, "Предпросмотр", px, py + 27, 0xFF9A92B8);
        }

        // Подсказка HUD
        if (tab == 1 && ArtemConfig.get().hudEditMode) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Перетаскивайте элементы HUD прямо на игровом экране",
                    left + W / 2, top + 136, 0xFF4CFFBE);
        }

        // Статус-сообщение
        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMessage, left + W / 2, top + H - 34, 0xFF4CFFBE);
        }

        if (tab == 4 && ProfileManager.listProfiles().isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Нет сохранённых профилей", left + W / 2, top + 115, 0xFF8880A8);
        }
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
