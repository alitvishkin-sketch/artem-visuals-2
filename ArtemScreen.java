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
 * Главное меню Artem Visuals 2.0 — 5 категорий, неоновый стиль.
 * Категории: Эффекты | HUD | Прицел | Оптимизация | Профили
 */
public class ArtemScreen extends Screen {
    private static final int W = 380;
    private static final int H = 280;
    private static final String[] TABS = {"Эффекты", "HUD", "Прицел", "Оптимизация", "Профили"};
    private static final String[] LEVELS = {"Низкое", "Среднее", "Высокое"};
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Плюс"};

    private final Screen parent;
    private int tab = 0;
    private int left, top;
    private TextFieldWidget profileNameField;
    private String statusMessage = "";
    private long statusUntil = 0;
    private float openAnim = 0f;

    public ArtemScreen(Screen parent) {
        super(Text.literal("ARTEM VISUALS"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearChildren();
        ArtemConfig c = ArtemConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        // Вкладки
        int tabW = (W - 20 - 4 * 3) / 5;
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            addBtn(left + 10 + i * (tabW + 3), top + 32, tabW, 18,
                    () -> TABS[idx], () -> tab == idx, () -> {
                        tab = idx;
                        clearAndInit();
                    });
        }

        int y = top + 58;
        int cw = (W - 28) / 2;
        int x0 = left + 12, x1 = x0 + cw + 4;
        int step = 24;

        switch (tab) {
            case 0 -> buildEffects(c, mc, x0, x1, y, cw, step);
            case 1 -> buildHud(c, x0, x1, y, cw, step);
            case 2 -> buildCrosshair(c, x0, x1, y, cw, step);
            case 3 -> buildOptimize(c, mc, x0, x1, y, cw, step);
            case 4 -> buildProfiles(x0, y, cw);
        }

        addBtn(left + W / 2 - 50, top + H - 26, 100, 18, () -> "Закрыть", null, this::close);
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
        toggle(x0, y, cw, "FPS", () -> c.showFps, v -> c.showFps = v);
        toggle(x1, y, cw, "Ping", () -> c.showPing, v -> c.showPing = v);
        toggle(x0, y + step, cw, "XYZ", () -> c.showXyz, v -> c.showXyz = v);
        toggle(x1, y + step, cw, "CPS", () -> c.showCps, v -> c.showCps = v);
        toggle(x0, y + step * 2, cw, "WASD", () -> c.showWasd, v -> c.showWasd = v);
        toggle(x1, y + step * 2, cw, "Сервер", () -> c.showServer, v -> c.showServer = v);
        toggle(x0, y + step * 3, cw * 2 + 4, "Режим редактирования HUD (перетаскивание)",
                () -> c.hudEditMode, v -> c.hudEditMode = v);
        // Подсказка рисуется в render
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
        toggle(x1, y + step * 2, cw, "Обводка", () -> c.crosshairOutline, v -> c.crosshairOutline = v);
        toggle(x0, y + step * 3, cw, "Динамический", () -> c.crosshairDynamic, v -> c.crosshairDynamic = v);
        // Цвет: несколько пресетов
        cycle(x1, y + step * 3, cw, () -> "Цвет: " + colorName(c.crosshairColor), () -> {
            c.crosshairColor = nextColor(c.crosshairColor);
        });
    }

    private void buildOptimize(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        addBtn(x0, y, cw * 2 + 4, 22, () -> "Применить пресет производительности", null, () -> {
            Optimizer.applyPerformancePreset(mc);
            setStatus("Пресет применён");
        });
        addBtn(x0, y + step + 4, cw * 2 + 4, 22,
                () -> Optimizer.hasBackup() ? "Вернуть прежние настройки видео" : "Бэкап отсутствует",
                null, () -> {
                    if (Optimizer.hasBackup()) {
                        Optimizer.restoreSettings(mc);
                        setStatus("Настройки восстановлены");
                    } else {
                        setStatus("Нечего восстанавливать");
                    }
                });
        addBtn(x0, y + step * 2 + 8, cw * 2 + 4, 22, () -> "Сбросить все настройки мода", null, () -> {
            c.resetDefaults();
            clearAndInit();
            setStatus("Сброшено к умолчаниям");
        });
    }

    private void buildProfiles(int x0, int y, int cw) {
        profileNameField = new TextFieldWidget(textRenderer, x0, y, cw * 2 + 4, 18, Text.literal("Имя профиля"));
        profileNameField.setMaxLength(32);
        profileNameField.setPlaceholder(Text.literal("Имя нового профиля..."));
        addDrawableChild(profileNameField);

        addBtn(x0, y + 26, cw, 20, () -> "Сохранить", null, () -> {
            String name = profileNameField.getText();
            if (ProfileManager.saveProfile(name)) {
                setStatus("Профиль «" + name + "» сохранён");
                clearAndInit();
            } else {
                setStatus("Ошибка сохранения");
            }
        });
        addBtn(x0 + cw + 4, y + 26, cw, 20, () -> "Обновить список", null, this::clearAndInit);

        List<String> profiles = ProfileManager.listProfiles();
        int py = y + 54;
        int i = 0;
        for (String name : profiles) {
            if (i >= 5) break; // лимит видимых
            final String n = name;
            addBtn(x0, py, cw + 20, 18, () -> "▶ " + n, null, () -> {
                if (ProfileManager.loadProfile(n)) {
                    setStatus("Загружен: " + n);
                    clearAndInit();
                } else {
                    setStatus("Ошибка загрузки");
                }
            });
            addBtn(x0 + cw + 24, py, 50, 18, () -> "Удал.", null, () -> {
                ProfileManager.deleteProfile(n);
                setStatus("Удалён: " + n);
                clearAndInit();
            });
            py += 22;
            i++;
        }
        if (profiles.isEmpty()) {
            // подсказка в render
        }
    }

    // ——— helpers ———

    private void addBtn(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action) {
        addDrawableChild(new NeonButton(x, y, w, h, label, state, action));
    }

    private void toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addBtn(x, y, w, 20, () -> name + ": " + (get.getAsBoolean() ? "ВКЛ" : "ВЫКЛ"), get, () -> {
            set.accept(!get.getAsBoolean());
            ArtemConfig.get().save();
        });
    }

    private void cycle(int x, int y, int w, Supplier<String> label, Runnable change) {
        addBtn(x, y, w, 20, label, null, () -> {
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
        // Затемнение
        ctx.fill(0, 0, width, height, 0xB0000000);

        // Основная панель
        int bg = 0xF00A0814;
        ctx.fill(left, top, left + W, top + H, bg);

        // Неоновая рамка
        int neon = NeonButton.NEON;
        // outer glow
        ctx.fill(left - 1, top - 1, left + W + 1, top, neon & 0x66FFFFFF);
        ctx.fill(left - 1, top + H, left + W + 1, top + H + 1, neon & 0x66FFFFFF);
        ctx.fill(left - 1, top, left, top + H, neon & 0x66FFFFFF);
        ctx.fill(left + W, top, left + W + 1, top + H, neon & 0x66FFFFFF);
        // solid border
        ctx.fill(left, top, left + W, top + 2, neon);
        ctx.fill(left, top + H - 2, left + W, top + H, neon);
        ctx.fill(left, top, left + 2, top + H, neon);
        ctx.fill(left + W - 2, top, left + W, top + H, neon);

        // Accent line under header
        ctx.fill(left + 8, top + 28, left + W - 8, top + 29, NeonButton.NEON_DIM);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        openAnim = Math.min(1f, openAnim + delta * 0.15f);
        super.render(ctx, mouseX, mouseY, delta);

        // Логотип с короной
        String logo = "♛  ARTEM VISUALS  2.0";
        int logoColor = 0xFFE0C0FF;
        ctx.drawText(textRenderer, logo, left + 12, top + 10, logoColor, true);

        // FPS в углу меню
        int fps = MinecraftClient.getInstance().getCurrentFps();
        String f = "FPS " + fps;
        int col = fps >= 60 ? 0xFF55FFAA : (fps >= 30 ? 0xFFAA88FF : 0xFFFF5566);
        ctx.drawText(textRenderer, f, left + W - 12 - textRenderer.getWidth(f), top + 10, col, true);

        // Предпросмотр прицела
        if (tab == 2) {
            int px = left + W / 2;
            int py = top + H - 70;
            ctx.fill(px - 28, py - 28, px + 28, py + 28, 0xFF12101C);
            ctx.fill(px - 27, py - 27, px + 27, py - 26, NeonButton.NEON_DIM);
            ctx.fill(px - 27, py + 26, px + 27, py + 27, NeonButton.NEON_DIM);
            CrosshairRenderer.renderPreview(ctx, px, py, ArtemConfig.get());
            ctx.drawCenteredTextWithShadow(textRenderer, "Предпросмотр", px, py + 32, 0xFF8888AA);
        }

        // Подсказка HUD
        if (tab == 1 && ArtemConfig.get().hudEditMode) {
            ctx.drawText(textRenderer, "§dПеретаскивайте элементы на экране мышью", left + 14, top + H - 48, 0xFFC0A0FF, false);
        }

        // Статус
        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMessage, left + W / 2, top + H - 48, 0xFFAAFFCC);
        }

        if (tab == 4 && ProfileManager.listProfiles().isEmpty()) {
            ctx.drawText(textRenderer, "§7Нет сохранённых профилей", left + 14, top + 120, 0xFF888899, false);
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
