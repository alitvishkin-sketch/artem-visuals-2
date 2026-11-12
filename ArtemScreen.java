package com.artem.visuals.gui;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.config.ProfileManager;
import com.artem.visuals.crosshair.CrosshairRenderer;
import com.artem.visuals.effects.EffectManager;
import com.artem.visuals.hud.HudManager;
import com.artem.visuals.optimize.Optimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Меню ArtemVisuals в стиле PulseVisuals: поиск, тумблеры, слайдеры по ПКМ и док с иконками.
 */
public class ArtemScreen extends Screen {
    private static final int W = 316;
    private static final int H = 174;
    private static final String[] TABS = {"Эффекты", "HUD", "Прицел", "Оптим.", "Профили"};
    private static final String[] LEVELS = {"Низкое", "Среднее", "Высокое"};
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Плюс"};

    private final Screen parent;
    private int tab = 0;
    private int left, top;
    private TextFieldWidget searchField;
    private String searchQuery = "";
    private TextFieldWidget profileNameField;
    private String statusMessage = "";
    private long statusUntil = 0;
    private long openedAtMs = 0;

    // Всплывающее окно поднастроек по ПКМ (как на первом скриншоте PulseVisuals)
    private String popupModule = null;

    public ArtemScreen(Screen parent) {
        super(Text.literal("ArtemVisuals"));
        this.parent = parent;
    }

    private record ModuleEntry(String name, BooleanSupplier get, Consumer<Boolean> set, String popupKey) {}

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

        // Поле поиска в правом верхнем углу окна (как на скриншоте PulseVisuals)
        searchField = new TextFieldWidget(textRenderer, left + W - 98, top + 5, 74, 13, Text.literal("Поиск"));
        searchField.setMaxLength(24);
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Поиск..."));
        searchField.setText(searchQuery);
        searchField.setChangedListener(txt -> {
            searchQuery = txt;
            popupModule = null;
            clearAndInit();
        });
        addDrawableChild(searchField);

        // Кнопка закрытия ✕
        addDrawableChild(new NeonButton(left + W - 19, top + 5, 13, 13,
                () -> "✕", null, this::close, NeonButton.Style.CLOSE_ICON));

        int y = top + 25;
        int cw = (W - 22) / 2;
        int x0 = left + 8;
        int x1 = x0 + cw + 6;
        int step = 20;

        if (!searchQuery.isBlank()) {
            buildSearchResults(c, mc, x0, x1, y, cw, step);
        } else {
            switch (tab) {
                case 0 -> buildEffects(c, mc, x0, x1, y, cw, step);
                case 1 -> buildHud(c, x0, x1, y, cw, step);
                case 2 -> buildCrosshair(c, x0, x1, y, cw, step);
                case 3 -> buildOptimize(c, mc, x0, x1, y, cw, step);
                case 4 -> buildProfiles(x0, y, cw);
            }
        }

        // Окно поднастроек (если открыто по ПКМ)
        if (popupModule != null) {
            buildPopupControls(c, mc);
        }

        // Нижний плавающий док с графическими иконками (как на скриншоте)
        int dockW = 148;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 7;
        int iconW = 22;
        int gap = 6;
        int startDockX = dockLeft + (dockW - (iconW * 5 + gap * 4)) / 2;

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            addDrawableChild(new NeonButton(
                    startDockX + i * (iconW + gap),
                    dockTop + 3,
                    iconW,
                    16,
                    () -> TABS[idx],
                    () -> tab == idx && searchQuery.isBlank(),
                    () -> {
                        tab = idx;
                        searchQuery = "";
                        popupModule = null;
                        clearAndInit();
                    },
                    NeonButton.Style.DOCK_ICON,
                    i
            ));
        }
    }

    private List<ModuleEntry> allModules(ArtemConfig c, MinecraftClient mc) {
        List<ModuleEntry> list = new ArrayList<>();
        list.add(new ModuleEntry("Jump Circle", () -> EffectManager.jumpCircle, v -> EffectManager.jumpCircle = v, "jump_circle"));
        list.add(new ModuleEntry("Block Overlay", () -> EffectManager.blockOverlay, v -> EffectManager.blockOverlay = v, "block_overlay"));
        list.add(new ModuleEntry("Все эффекты", () -> c.masterEnabled, v -> c.masterEnabled = v, "particles"));
        list.add(new ModuleEntry("Листопад", () -> c.leafParticles, v -> c.leafParticles = v, "particles"));
        list.add(new ModuleEntry("Дождь / снег", () -> c.precipitationEffects, v -> c.precipitationEffects = v, null));
        list.add(new ModuleEntry("Блики на воде", () -> c.waterSparkles, v -> c.waterSparkles = v, null));
        list.add(new ModuleEntry("Светлячки", () -> c.fireflies, v -> c.fireflies = v, null));
        list.add(new ModuleEntry("Мягкий свет", () -> c.softLighting, v -> {
            c.softLighting = v;
            Optimizer.applyLighting(mc);
        }, null));
        list.add(new ModuleEntry("Счётчик FPS", () -> c.showFps, v -> c.showFps = v, null));
        list.add(new ModuleEntry("Пинг (Ping)", () -> c.showPing, v -> c.showPing = v, null));
        list.add(new ModuleEntry("Координаты", () -> c.showXyz, v -> c.showXyz = v, null));
        list.add(new ModuleEntry("Клики (CPS)", () -> c.showCps, v -> c.showCps = v, null));
        list.add(new ModuleEntry("Клавиши WASD", () -> c.showWasd, v -> c.showWasd = v, null));
        list.add(new ModuleEntry("Инфо-бар (Верх)", () -> c.showServer, v -> c.showServer = v, null));
        list.add(new ModuleEntry("Potions HUD", () -> HudManager.showPotions, v -> HudManager.showPotions = v, null));
        list.add(new ModuleEntry("TargetHUD", () -> HudManager.showTargetHud, v -> HudManager.showTargetHud = v, null));
        list.add(new ModuleEntry("Свой прицел", () -> c.customCrosshair, v -> c.customCrosshair = v, "crosshair"));
        return list;
    }

    private void buildSearchResults(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        String q = searchQuery.toLowerCase(Locale.ROOT).trim();
        int idx = 0;
        for (ModuleEntry m : allModules(c, mc)) {
            if (!m.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            if (idx >= 12) break;
            int colX = (idx % 2 == 0) ? x0 : x1;
            int rowY = y + (idx / 2) * step;
            toggle(colX, rowY, cw, m.name(), m.get(), m.set(), m.popupKey());
            idx++;
        }
    }

    private void buildEffects(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Jump Circle", () -> EffectManager.jumpCircle, v -> EffectManager.jumpCircle = v, "jump_circle");
        toggle(x1, y, cw, "Block Overlay", () -> EffectManager.blockOverlay, v -> EffectManager.blockOverlay = v, "block_overlay");
        toggle(x0, y + step, cw, "Все эффекты", () -> c.masterEnabled, v -> c.masterEnabled = v, "particles");
        toggle(x1, y + step, cw, "Листопад", () -> c.leafParticles, v -> c.leafParticles = v, "particles");
        toggle(x0, y + step * 2, cw, "Дождь / снег", () -> c.precipitationEffects, v -> c.precipitationEffects = v, null);
        toggle(x1, y + step * 2, cw, "Блики на воде", () -> c.waterSparkles, v -> c.waterSparkles = v, null);
        toggle(x0, y + step * 3, cw, "Светлячки", () -> c.fireflies, v -> c.fireflies = v, null);
        toggle(x1, y + step * 3, cw, "Ломание блоков", () -> c.blockBreakEffects, v -> c.blockBreakEffects = v, null);
        toggle(x0, y + step * 4, cw, "Мягкий свет", () -> c.softLighting, v -> {
            c.softLighting = v;
            Optimizer.applyLighting(mc);
        }, null);
        toggle(x1, y + step * 4, cw, "Адаптивно к FPS", () -> c.adaptive, v -> c.adaptive = v, null);
        cycle(x0, y + step * 5, cw, () -> "Плотность: " + LEVELS[c.density], () -> c.density = (c.density + 1) % 3);
        cycle(x1, y + step * 5, cw, () -> "Радиус: " + LEVELS[c.radius], () -> c.radius = (c.radius + 1) % 3);
    }

    private void buildHud(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Инфо-бар (Верх)", () -> c.showServer, v -> c.showServer = v, null);
        toggle(x1, y, cw, "TargetHUD (Цель)", () -> HudManager.showTargetHud, v -> HudManager.showTargetHud = v, null);
        toggle(x0, y + step, cw, "Potions HUD", () -> HudManager.showPotions, v -> HudManager.showPotions = v, null);
        toggle(x1, y + step, cw, "Клавиши WASD", () -> c.showWasd, v -> c.showWasd = v, null);
        toggle(x0, y + step * 2, cw, "Счётчик FPS", () -> c.showFps, v -> c.showFps = v, null);
        toggle(x1, y + step * 2, cw, "Пинг (Ping)", () -> c.showPing, v -> c.showPing = v, null);
        toggle(x0, y + step * 3, cw, "Координаты", () -> c.showXyz, v -> c.showXyz = v, null);
        toggle(x1, y + step * 3, cw, "Клики (CPS)", () -> c.showCps, v -> c.showCps = v, null);
        toggle(x0, y + step * 4 + 2, cw * 2 + 6, "Перетаскивание элементов HUD мышью",
                () -> c.hudEditMode, v -> c.hudEditMode = v, null);
    }

    private void buildCrosshair(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Свой прицел", () -> c.customCrosshair, v -> c.customCrosshair = v, null);
        cycle(x1, y, cw, () -> "Стиль: " + STYLES[Math.min(c.crosshairStyle, 3)], () -> c.crosshairStyle = (c.crosshairStyle + 1) % 4);

        // Настоящие плавные слайдеры
        addDrawableChild(NeonButton.slider(x0, y + step, cw, 17, "Размер", 3, 12, true,
                () -> c.crosshairSize, v -> { c.crosshairSize = (int) v; c.save(); }));
        addDrawableChild(NeonButton.slider(x1, y + step, cw, 17, "Зазор", 0, 8, true,
                () -> c.crosshairGap, v -> { c.crosshairGap = (int) v; c.save(); }));
        addDrawableChild(NeonButton.slider(x0, y + step * 2, cw, 17, "Толщина", 1, 3, true,
                () -> c.crosshairThickness, v -> { c.crosshairThickness = (int) v; c.save(); }));

        cycle(x1, y + step * 2, cw, () -> "Цвет: " + colorName(c.crosshairColor), () -> c.crosshairColor = nextColor(c.crosshairColor));
        toggle(x0, y + step * 3, cw, "Обводка", () -> c.crosshairOutline, v -> c.crosshairOutline = v, null);
        toggle(x1, y + step * 3, cw, "Динамика", () -> c.crosshairDynamic, v -> c.crosshairDynamic = v, null);
    }

    private void buildOptimize(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        addBtn(x0, y, cw * 2 + 6, 18, () -> "⚔ Пресет под FunTime PvP (HUD + 3D + FPS)", null, () -> {
            applyFunTimePreset(c, mc);
            setStatus("Пресет FunTime PvP активирован");
        });
        addBtn(x0, y + step + 2, cw * 2 + 6, 18, () -> "⚡ Применить пресет производительности", null, () -> {
            Optimizer.applyPerformancePreset(mc);
            setStatus("Пресет производительности применён");
        });
        addBtn(x0, y + step * 2 + 4, cw * 2 + 6, 18,
                () -> Optimizer.hasBackup() ? "↺ Вернуть прежние настройки видео" : "Бэкап настроек отсутствует",
                null, () -> {
                    if (Optimizer.hasBackup()) {
                        Optimizer.restoreSettings(mc);
                        setStatus("Настройки восстановлены");
                    } else {
                        setStatus("Нечего восстанавливать");
                    }
                });
        addBtn(x0, y + step * 3 + 6, cw * 2 + 6, 18, () -> "Сбросить настройки мода", null, () -> {
            c.resetDefaults();
            clearAndInit();
            setStatus("Сброшено к умолчаниям");
        });
    }

    private void buildPopupControls(ArtemConfig c, MinecraftClient mc) {
        int pw = 128;
        int ph = 84;
        int px = Math.max(6, left - pw - 8);
        int py = top + 36;

        addDrawableChild(new NeonButton(px + pw - 16, py + 4, 12, 12,
                () -> "✕", null, () -> { popupModule = null; clearAndInit(); }, NeonButton.Style.CLOSE_ICON));

        int ix = px + 7;
        int iw = pw - 14;
        int iy = py + 22;

        switch (popupModule) {
            case "block_overlay" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 17, "Яркость линий", 0.2, 1.0, false,
                        () -> EffectManager.blockOverlayAlpha, v -> EffectManager.blockOverlayAlpha = (float) v));
                cycle(ix, iy + 22, iw, () -> "Цвет: " + overlayColorName(EffectManager.blockOverlayColor),
                        () -> EffectManager.blockOverlayColor = nextOverlayColor(EffectManager.blockOverlayColor));
            }
            case "jump_circle" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 17, "Радиус круга", 0.6, 2.0, false,
                        () -> EffectManager.jumpCircleRadius, v -> EffectManager.jumpCircleRadius = (float) v));
                cycle(ix, iy + 22, iw, () -> "Цвет: " + overlayColorName(EffectManager.jumpCircleColor),
                        () -> EffectManager.jumpCircleColor = nextOverlayColor(EffectManager.jumpCircleColor));
            }
            case "particles" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 17, "Плотность", 0, 2, true,
                        () -> c.density, v -> { c.density = (int) v; c.save(); }));
                addDrawableChild(NeonButton.slider(ix, iy + 22, iw, 17, "Радиус", 0, 2, true,
                        () -> c.radius, v -> { c.radius = (int) v; c.save(); }));
            }
        }
    }

    private void applyFunTimePreset(ArtemConfig c, MinecraftClient mc) {
        c.showFps = false;
        c.showPing = false;
        c.showXyz = true;
        c.showCps = false;
        c.showWasd = true;
        c.showServer = true;
        HudManager.showPotions = true;
        HudManager.showTargetHud = true;

        EffectManager.jumpCircle = true;
        EffectManager.blockOverlay = true;
        EffectManager.blockOverlayColor = 0xFFFFD166; // Золотой как на скриншоте FunTime
        EffectManager.jumpCircleColor = 0xFFFFD166;

        c.customCrosshair = true;
        c.crosshairStyle = 0;
        c.crosshairSize = 4;
        c.crosshairGap = 2;
        c.crosshairThickness = 1;
        c.crosshairOutline = true;
        c.crosshairDynamic = true;
        c.crosshairColor = 0xFF00F5FF;

        c.adaptive = true;
        c.density = 0;
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

    private void toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set, String popupKey) {
        NeonButton btn = new NeonButton(x, y, w, 17, () -> name, get, () -> {
            set.accept(!get.getAsBoolean());
            ArtemConfig.get().save();
        });
        if (popupKey != null) {
            btn.withRightClick(() -> {
                popupModule = popupKey.equals(popupModule) ? null : popupKey;
                clearAndInit();
            });
        }
        addDrawableChild(btn);
    }

    private void cycle(int x, int y, int w, Supplier<String> label, Runnable change) {
        addBtn(x, y, w, 17, label, null, () -> {
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

    private static String overlayColorName(int argb) {
        return switch (argb) {
            case 0xFFFFD166 -> "Золотой";
            case 0xFF7B68EE -> "Фиолетовый";
            case 0xFF00F5FF -> "Циан";
            case 0xFFFF4D6D -> "Розовый";
            default -> "Белый";
        };
    }

    private static int nextOverlayColor(int current) {
        int[] palette = {0xFFFFD166, 0xFF7B68EE, 0xFF00F5FF, 0xFFFF4D6D, 0xFFFFFFFF};
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

        // Тень и главное окно
        NeonButton.fillRounded(ctx, left - 3, top - 2, W + 6, H + 5, 0x55000000);
        NeonButton.fillRounded(ctx, left, top, W, H, 0xEE0D0D15);
        NeonButton.drawRoundedBorder(ctx, left, top, W, H, 0x24FFFFFF);

        // Фон поля поиска в правом верхнем углу
        NeonButton.fillRounded(ctx, left + W - 102, top + 4, 80, 14, 0xCC161622);
        NeonButton.drawRoundedBorder(ctx, left + W - 102, top + 4, 80, 14, 0x1AFFFFFF);

        ctx.fill(left + 8, top + 20, left + W - 8, top + 21, 0x14FFFFFF);

        // Всплывающая карточка поднастроек (если открыта по ПКМ)
        if (popupModule != null) {
            int pw = 128;
            int ph = 84;
            int px = Math.max(6, left - pw - 8);
            int py = top + 36;
            NeonButton.fillRounded(ctx, px - 2, py - 2, pw + 4, ph + 4, 0x44000000);
            NeonButton.fillRounded(ctx, px, py, pw, ph, 0xEE0D0D15);
            NeonButton.drawRoundedBorder(ctx, px, py, pw, ph, 0x337B68EE);
            ctx.fill(px + 6, py + 17, px + pw - 6, py + 18, 0x14FFFFFF);
        }

        // Плавающий док снизу
        int dockW = 148;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 7;
        NeonButton.fillRounded(ctx, dockLeft, dockTop, dockW, 22, 0xEE0D0D15);
        NeonButton.drawRoundedBorder(ctx, dockLeft, dockTop, dockW, 22, 0x24FFFFFF);
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

        // Хлебные крошки: Visuals / Эффекты
        int bx = left + 9;
        int by = top + 7;
        ctx.drawText(textRenderer, "Visuals", bx, by, 0xFFF2F2FA, false);
        bx += textRenderer.getWidth("Visuals");
        ctx.drawText(textRenderer, " / ", bx, by, 0xFF55556A, false);
        bx += textRenderer.getWidth(" / ");
        ctx.drawText(textRenderer, searchQuery.isBlank() ? TABS[tab] : "Поиск", bx, by, 0xFF8E8EA4, false);

        // Заголовок окна поднастроек по ПКМ
        if (popupModule != null) {
            int pw = 128;
            int px = Math.max(6, left - pw - 8);
            int py = top + 36;
            String title = switch (popupModule) {
                case "block_overlay" -> "Block Overlay";
                case "jump_circle" -> "Jump Circle";
                default -> "Частицы";
            };
            ctx.drawText(textRenderer, title, px + 7, py + 6, 0xFFE6E6F2, false);
        }

        // Предпросмотр прицела внизу вкладки Прицел
        if (tab == 2 && searchQuery.isBlank()) {
            int px = left + W / 2;
            int py = top + 132;
            NeonButton.fillRounded(ctx, px - 22, py - 22, 44, 44, 0xFF151520);
            NeonButton.drawRoundedBorder(ctx, px - 22, py - 22, 44, 44, 0x26FFFFFF);
            CrosshairRenderer.renderPreview(ctx, px, py, ArtemConfig.get());
        }

        if (tab == 0 && searchQuery.isBlank() && popupModule == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "ПКМ по модулю — открыть его настройки",
                    left + W / 2, top + H - 15, 0xFF585870);
        }

        if (tab == 1 && searchQuery.isBlank() && ArtemConfig.get().hudEditMode) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Перетаскивайте элементы HUD мышью прямо на экране",
                    left + W / 2, top + 134, 0xFF9D8DF7);
        }

        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMessage, left + W / 2, top + H - 15, 0xFF8B78FF);
        }

        if (tab == 4 && searchQuery.isBlank() && ProfileManager.listProfiles().isEmpty()) {
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
