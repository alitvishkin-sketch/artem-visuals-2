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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Премиум-меню ArtemVisuals в стиле PulseVisuals с живым поиском, скруглённым стеклом, слайдерами и доком.
 */
public class ArtemScreen extends Screen {
    private static final int W = 336;
    private static final int H = 192;
    private static final String[] TABS = {"Эффекты", "HUD", "Прицел", "FunTime", "Профили"};
    private static final String[] LEVELS = {"Низкое", "Среднее", "Высокое"};
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Плюс"};
    private static final int[] OVERLAY_PALETTE = {0xFFFFD166, 0xFF7B68EE, 0xFF00F5FF, 0xFFFF4D6D, 0xFFFFFFFF};
    private static final int[] CROSSHAIR_PALETTE = {0xFF00F5FF, 0xFF9B59FF, 0xFFFF2D95, 0xFF39FF14, 0xFFFFFFFF};

    private final Screen parent;
    private int tab = 0;
    private int left, top;

    // Собственный быстрый поиск без потери фокуса
    private String searchQuery = "";
    private boolean searchFocused = false;

    private TextFieldWidget profileNameField;
    private String statusMessage = "";
    private long statusUntil = 0;
    private long openedAtMs = 0;

    // Всплывающее окно тонкой настройки по ПКМ
    private String popupModule = null;

    public ArtemScreen(Screen parent) {
        super(Text.literal("ArtemVisuals"));
        this.parent = parent;
    }

    private record ModuleEntry(String name, String keywords, BooleanSupplier get, Consumer<Boolean> set, String popupKey) {}

    @Override
    protected void init() {
        if (openedAtMs == 0) {
            openedAtMs = System.currentTimeMillis();
        }
        left = (width - W) / 2;
        top = (height - H) / 2 - 4;
        rebuildContent();
    }

    private void rebuildContent() {
        clearChildren();
        profileNameField = null;

        ArtemConfig c = ArtemConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        // Кнопка закрытия ✕
        addDrawableChild(new NeonButton(left + W - 21, top + 6, 14, 14,
                () -> "✕", null, this::close, NeonButton.Style.CLOSE_ICON));

        int y = top + 28;
        int cw = (W - 24) / 2;
        int x0 = left + 9;
        int x1 = x0 + cw + 6;
        int step = 23;

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

        if (popupModule != null) {
            buildPopupControls(c, mc);
        }

        // Плавающий стеклянный док снизу
        int dockW = 172;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 8;
        int iconW = 24;
        int gap = 7;
        int startDockX = dockLeft + (dockW - (iconW * 5 + gap * 4)) / 2;

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            addDrawableChild(new NeonButton(
                    startDockX + i * (iconW + gap),
                    dockTop + 4,
                    iconW,
                    18,
                    () -> TABS[idx],
                    () -> tab == idx && searchQuery.isBlank(),
                    () -> {
                        tab = idx;
                        searchQuery = "";
                        searchFocused = false;
                        popupModule = null;
                        rebuildContent();
                    },
                    NeonButton.Style.DOCK_ICON,
                    i
            ));
        }
    }

    private List<ModuleEntry> allModules(ArtemConfig c, MinecraftClient mc) {
        List<ModuleEntry> list = new ArrayList<>();
        list.add(new ModuleEntry("Jump Circle", "jump circle круг прыжок", () -> EffectManager.jumpCircle, v -> EffectManager.jumpCircle = v, "jump_circle"));
        list.add(new ModuleEntry("Block Overlay", "block overlay обводка блок", () -> EffectManager.blockOverlay, v -> EffectManager.blockOverlay = v, "block_overlay"));
        list.add(new ModuleEntry("Все эффекты", "все эффекты particles частицы", () -> c.masterEnabled, v -> c.masterEnabled = v, "particles"));
        list.add(new ModuleEntry("Листопад", "листопад leaves", () -> c.leafParticles, v -> c.leafParticles = v, "particles"));
        list.add(new ModuleEntry("Дождь / снег", "дождь снег weather", () -> c.precipitationEffects, v -> c.precipitationEffects = v, null));
        list.add(new ModuleEntry("Блики на воде", "блики вода water", () -> c.waterSparkles, v -> c.waterSparkles = v, null));
        list.add(new ModuleEntry("Светлячки", "светлячки fireflies", () -> c.fireflies, v -> c.fireflies = v, null));
        list.add(new ModuleEntry("Ломание блоков", "ломание блоков break", () -> c.blockBreakEffects, v -> c.blockBreakEffects = v, null));
        list.add(new ModuleEntry("Мягкий свет", "мягкий свет fullbright гамма", () -> c.softLighting, v -> {
            c.softLighting = v;
            Optimizer.applyLighting(mc);
        }, null));
        list.add(new ModuleEntry("Адаптивно к FPS", "адаптивно fps фпс", () -> c.adaptive, v -> c.adaptive = v, null));
        list.add(new ModuleEntry("Инфо-бар (Верх)", "инфо бар сервер funtime topbar", () -> c.showServer, v -> c.showServer = v, null));
        list.add(new ModuleEntry("TargetHUD", "target hud таргет цель хп", () -> HudManager.showTargetHud, v -> HudManager.showTargetHud = v, null));
        list.add(new ModuleEntry("Potions HUD", "potions зелья эффекты", () -> HudManager.showPotions, v -> HudManager.showPotions = v, null));
        list.add(new ModuleEntry("Клавиши WASD", "wasd keystrokes клавиши", () -> c.showWasd, v -> c.showWasd = v, null));
        list.add(new ModuleEntry("Счётчик FPS", "fps фпс", () -> c.showFps, v -> c.showFps = v, null));
        list.add(new ModuleEntry("Пинг (Ping)", "ping пинг", () -> c.showPing, v -> c.showPing = v, null));
        list.add(new ModuleEntry("Координаты", "xyz координаты", () -> c.showXyz, v -> c.showXyz = v, null));
        list.add(new ModuleEntry("Клики (CPS)", "cps кпс клики", () -> c.showCps, v -> c.showCps = v, null));
        list.add(new ModuleEntry("Свой прицел", "прицел crosshair", () -> c.customCrosshair, v -> c.customCrosshair = v, null));
        return list;
    }

    private void buildSearchResults(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        String q = searchQuery.toLowerCase(Locale.ROOT).trim();
        int idx = 0;
        for (ModuleEntry m : allModules(c, mc)) {
            if (!m.name().toLowerCase(Locale.ROOT).contains(q) && !m.keywords().contains(q)) continue;
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
        toggle(x1, y, cw, "TargetHUD", () -> HudManager.showTargetHud, v -> HudManager.showTargetHud = v, null);
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

        addDrawableChild(NeonButton.slider(x0, y + step, cw, 18, "Размер", 3, 12, true,
                () -> c.crosshairSize, v -> { c.crosshairSize = (int) v; c.save(); }));
        addDrawableChild(NeonButton.slider(x1, y + step, cw, 18, "Зазор", 0, 8, true,
                () -> c.crosshairGap, v -> { c.crosshairGap = (int) v; c.save(); }));
        addDrawableChild(NeonButton.slider(x0, y + step * 2, cw, 18, "Толщина", 1, 3, true,
                () -> c.crosshairThickness, v -> { c.crosshairThickness = (int) v; c.save(); }));

        addDrawableChild(NeonButton.swatches(x1, y + step * 2 + 2, cw, 16, "Цвет", CROSSHAIR_PALETTE,
                () -> c.crosshairColor, col -> { c.crosshairColor = col; c.save(); }));

        toggle(x0, y + step * 3, cw, "Обводка", () -> c.crosshairOutline, v -> c.crosshairOutline = v, null);
        toggle(x1, y + step * 3, cw, "Динамика", () -> c.crosshairDynamic, v -> c.crosshairDynamic = v, null);
    }

    private void buildOptimize(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        addBtn(x0, y, cw * 2 + 6, 21, () -> "⚔ Активировать пресет FunTime PvP", null, () -> {
            applyFunTimePreset(c, mc);
            setStatus("Пресет FunTime PvP активирован");
        });
        addBtn(x0, y + step + 2, cw * 2 + 6, 21, () -> "⚡ Применить пресет высокого FPS", null, () -> {
            Optimizer.applyPerformancePreset(mc);
            setStatus("Пресет производительности применён");
        });
        addBtn(x0, y + step * 2 + 4, cw * 2 + 6, 21,
                () -> Optimizer.hasBackup() ? "↺ Вернуть прежние настройки видео" : "Бэкап настроек отсутствует",
                null, () -> {
                    if (Optimizer.hasBackup()) {
                        Optimizer.restoreSettings(mc);
                        setStatus("Настройки восстановлены");
                    } else {
                        setStatus("Нечего восстанавливать");
                    }
                });
        addBtn(x0, y + step * 3 + 6, cw * 2 + 6, 21, () -> "Сбросить настройки мода", null, () -> {
            c.resetDefaults();
            rebuildContent();
            setStatus("Сброшено к умолчаниям");
        });
    }

    private void buildPopupControls(ArtemConfig c, MinecraftClient mc) {
        int pw = 142;
        int px = Math.max(6, left - pw - 8);
        int py = top + 34;

        addDrawableChild(new NeonButton(px + pw - 17, py + 5, 12, 12,
                () -> "✕", null, () -> { popupModule = null; rebuildContent(); }, NeonButton.Style.CLOSE_ICON));

        int ix = px + 8;
        int iw = pw - 16;
        int iy = py + 24;

        switch (popupModule) {
            case "block_overlay" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 19, "Яркость линий", 0.2, 1.0, false,
                        () -> EffectManager.blockOverlayAlpha, v -> EffectManager.blockOverlayAlpha = (float) v));
                addDrawableChild(NeonButton.swatches(ix, iy + 26, iw, 16, "Цвет", OVERLAY_PALETTE,
                        () -> EffectManager.blockOverlayColor, col -> EffectManager.blockOverlayColor = col));
            }
            case "jump_circle" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 19, "Радиус круга", 0.6, 2.0, false,
                        () -> EffectManager.jumpCircleRadius, v -> EffectManager.jumpCircleRadius = (float) v));
                addDrawableChild(NeonButton.swatches(ix, iy + 26, iw, 16, "Цвет", OVERLAY_PALETTE,
                        () -> EffectManager.jumpCircleColor, col -> EffectManager.jumpCircleColor = col));
            }
            case "particles" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 19, "Плотность", 0, 2, true,
                        () -> c.density, v -> { c.density = (int) v; c.save(); }));
                addDrawableChild(NeonButton.slider(ix, iy + 26, iw, 19, "Радиус", 0, 2, true,
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
        EffectManager.blockOverlayColor = 0xFFFFD166;
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
        profileNameField = new TextFieldWidget(textRenderer, x0, y, cw * 2 + 6, 17, Text.literal("Имя профиля"));
        profileNameField.setMaxLength(28);
        profileNameField.setPlaceholder(Text.literal("Название профиля (напр. FunTime)..."));
        addDrawableChild(profileNameField);

        addBtn(x0, y + 22, cw, 19, () -> "Сохранить", null, () -> {
            String name = profileNameField.getText();
            if (ProfileManager.saveProfile(name)) {
                setStatus("Сохранено: " + name);
                rebuildContent();
            } else {
                setStatus("Введите имя");
            }
        });
        addBtn(x0 + cw + 6, y + 22, cw, 19, () -> "Обновить", null, this::rebuildContent);

        List<String> profiles = ProfileManager.listProfiles();
        int py = y + 46;
        int i = 0;
        for (String name : profiles) {
            if (i >= 4) break;
            final String n = name;
            addBtn(x0, py, cw * 2 - 40, 19, () -> "▶ " + n, null, () -> {
                if (ProfileManager.loadProfile(n)) {
                    setStatus("Загружен: " + n);
                    rebuildContent();
                }
            });
            addBtn(x0 + cw * 2 - 36, py, 42, 19, () -> "Удал.", null, () -> {
                ProfileManager.deleteProfile(n);
                setStatus("Удалён: " + n);
                rebuildContent();
            });
            py += 22;
            i++;
        }
    }

    // ——— helpers ———

    private void addBtn(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action) {
        addDrawableChild(new NeonButton(x, y, w, h, label, state, action));
    }

    private void toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set, String popupKey) {
        NeonButton btn = new NeonButton(x, y, w, 20, () -> name, get, () -> {
            set.accept(!get.getAsBoolean());
            ArtemConfig.get().save();
        });
        if (popupKey != null) {
            btn.withRightClick(() -> {
                popupModule = popupKey.equals(popupModule) ? null : popupKey;
                rebuildContent();
            });
        }
        addDrawableChild(btn);
    }

    private void cycle(int x, int y, int w, Supplier<String> label, Runnable change) {
        addBtn(x, y, w, 20, label, null, () -> {
            change.run();
            ArtemConfig.get().save();
        });
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        statusUntil = System.currentTimeMillis() + 2200;
    }

    private float getOpenProgress() {
        long elapsed = System.currentTimeMillis() - openedAtMs;
        float t = Math.min(1f, Math.max(0f, elapsed / 180f));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    // ——— Интерактивный поиск и ввод ———

    private int searchBoxX() { return left + W - 118; }
    private int searchBoxY() { return top + 6; }
    private int searchBoxW() { return 93; }
    private int searchBoxH() { return 14; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int sx = searchBoxX(), sy = searchBoxY(), sw = searchBoxW(), sh = searchBoxH();
        if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
            if (profileNameField != null) {
                profileNameField.setFocused(false);
            }
            if (!searchQuery.isEmpty() && mouseX >= sx + sw - 13) {
                searchQuery = "";
                searchFocused = true;
                popupModule = null;
                rebuildContent();
                return true;
            }
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (profileNameField != null && profileNameField.isFocused()) {
            return super.charTyped(chr, modifiers);
        }
        if (chr >= 32 && chr != 127) {
            if (searchQuery.length() < 20) {
                searchFocused = true;
                searchQuery += chr;
                popupModule = null;
                rebuildContent();
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (profileNameField != null && profileNameField.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            searchFocused = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchFocused = true;
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            popupModule = null;
            rebuildContent();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!searchQuery.isEmpty()) {
                searchQuery = "";
                searchFocused = false;
                rebuildContent();
                return true;
            }
            if (popupModule != null) {
                popupModule = null;
                rebuildContent();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float p = getOpenProgress();
        int dimAlpha = (int) (0x78 * p);
        ctx.fill(0, 0, width, height, dimAlpha << 24);

        // Мягкое внешнее свечение и тень под главным окном
        NeonButton.drawAmbientGlow(ctx, left, top, W, H, 8, 0xFF6C5CE7, 2);
        NeonButton.fillGradientRounded(ctx, left, top, W, H, 8, 0xF210101A, 0xF20A0A12);
        NeonButton.drawSmoothRoundedOutline(ctx, left, top, W, H, 8, 0x2EFFFFFF);

        // Тонкий стеклянный разделитель шапки
        ctx.fill(left + 9, top + 23, left + W - 9, top + 24, 0x16FFFFFF);

        // Всплывающая карточка поднастроек по ПКМ
        if (popupModule != null) {
            int pw = 142;
            int ph = 82;
            int px = Math.max(6, left - pw - 8);
            int py = top + 34;
            NeonButton.drawAmbientGlow(ctx, px, py, pw, ph, 7, 0xFF6C5CE7, 2);
            NeonButton.fillGradientRounded(ctx, px, py, pw, ph, 7, 0xF212121E, 0xF20B0B14);
            NeonButton.drawSmoothRoundedOutline(ctx, px, py, pw, ph, 7, 0x447B68EE);
            ctx.fill(px + 8, py + 19, px + pw - 8, py + 20, 0x16FFFFFF);
        }

        // Плавающий док снизу
        int dockW = 172;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 8;
        NeonButton.drawAmbientGlow(ctx, dockLeft, dockTop, dockW, 26, 8, 0xFF6C5CE7, 1);
        NeonButton.fillGradientRounded(ctx, dockLeft, dockTop, dockW, 26, 8, 0xF211111C, 0xF20A0A12);
        NeonButton.drawSmoothRoundedOutline(ctx, dockLeft, dockTop, dockW, 26, 8, 0x2AFFFFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float p = getOpenProgress();
        float scale = 0.90f + 0.10f * p;
        float slideY = (1f - p) * 12f;

        int cx = left + W / 2;
        int cy = top + H / 2;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy + slideY, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.getMatrices().translate(-cx, -cy, 0);

        super.render(ctx, mouseX, mouseY, delta);

        drawBrandHeader(ctx);
        drawSearchBar(ctx, mouseX, mouseY);

        // Хлебные крошки: Artem › Эффекты
        int bx = left + 10;
        int by = top + 9;
        ctx.drawText(textRenderer, "Artem", bx, by, 0xFFF4F4FC, false);
        bx += textRenderer.getWidth("Artem");
        ctx.drawText(textRenderer, " › ", bx, by, 0xFF5A5A72, false);
        bx += textRenderer.getWidth(" › ");
        ctx.drawText(textRenderer, searchQuery.isBlank() ? TABS[tab] : "Поиск", bx, by, 0xFF9D8DF7, false);

        // Заголовок карточки поднастроек по ПКМ
        if (popupModule != null) {
            int pw = 142;
            int px = Math.max(6, left - pw - 8);
            int py = top + 34;
            String title = switch (popupModule) {
                case "block_overlay" -> "Block Overlay";
                case "jump_circle" -> "Jump Circle";
                default -> "Частицы мира";
            };
            ctx.drawText(textRenderer, title, px + 8, py + 7, 0xFFEAEAF6, false);
        }

        // Предпросмотр прицела во вкладке Прицел
        if (tab == 2 && searchQuery.isBlank()) {
            int px = left + W / 2;
            int py = top + 144;
            NeonButton.fillSmoothRounded(ctx, px - 24, py - 24, 48, 48, 6, 0xFF141420);
            NeonButton.drawSmoothRoundedOutline(ctx, px - 24, py - 24, 48, 48, 6, 0x28FFFFFF);
            CrosshairRenderer.renderPreview(ctx, px, py, ArtemConfig.get());
        }

        // Если по поиску ничего не нашлось
        if (!searchQuery.isBlank() && children().size() <= 6) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Ничего не найдено по запросу «" + searchQuery + "»",
                    left + W / 2, top + 92, 0xFF6E6E88);
        }

        if (tab == 0 && searchQuery.isBlank() && popupModule == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Нажми ПКМ по карточке с ⚙ для настройки цвета и радиуса",
                    left + W / 2, top + H - 15, 0xFF5C5C74);
        }

        if (tab == 1 && searchQuery.isBlank() && ArtemConfig.get().hudEditMode) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Перетаскивайте элементы HUD мышью прямо на экране",
                    left + W / 2, top + 150, 0xFF9D8DF7);
        }

        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMessage, left + W / 2, top + H - 15, 0xFF9D8DF7);
        }

        if (tab == 4 && searchQuery.isBlank() && ProfileManager.listProfiles().isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Нет сохранённых профилей", left + W / 2, top + 104, 0xFF6E6E85);
        }

        drawDockTooltip(ctx, mouseX, mouseY);

        ctx.getMatrices().pop();
    }

    private void drawSearchBar(DrawContext ctx, int mouseX, int mouseY) {
        int sx = searchBoxX(), sy = searchBoxY(), sw = searchBoxW(), sh = searchBoxH();
        boolean hover = mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh;

        NeonButton.fillSmoothRounded(ctx, sx, sy, sw, sh, 4, (searchFocused || hover) ? 0xFF1A1A2A : 0xFF141420);
        NeonButton.drawSmoothRoundedOutline(ctx, sx, sy, sw, sh, 4,
                searchFocused ? 0xFF7B68EE : (hover ? 0x33FFFFFF : 0x1AFFFFFF));

        // Иконка лупы слева
        int lx = sx + 6;
        int ly = sy + 6;
        NeonButton.drawCircle(ctx, lx, ly, 2, 0xFF8E8EA8);
        NeonButton.drawCircle(ctx, lx, ly, 1, 0xFF161624);
        ctx.fill(lx + 2, ly + 2, lx + 4, ly + 4, 0xFF8E8EA8);

        int textX = sx + 13;
        int textY = sy + 3;
        if (searchQuery.isEmpty() && !searchFocused) {
            ctx.drawText(textRenderer, "Поиск...", textX, textY, 0xFF5E5E76, false);
        } else {
            String visible = textRenderer.trimToWidth(searchQuery, sw - 26);
            ctx.drawText(textRenderer, visible, textX, textY, 0xFFF2F2FA, false);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = textX + textRenderer.getWidth(visible);
                ctx.fill(cx, textY, cx + 1, textY + 8, 0xFF9D8DF7);
            }
        }

        if (!searchQuery.isEmpty()) {
            ctx.drawText(textRenderer, "×", sx + sw - 10, textY, 0xFF8E8EA8, false);
        }
    }

    private void drawDockTooltip(DrawContext ctx, int mouseX, int mouseY) {
        int dockW = 172;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 8;
        int iconW = 24;
        int gap = 7;
        int startDockX = dockLeft + (dockW - (iconW * 5 + gap * 4)) / 2;

        for (int i = 0; i < 5; i++) {
            int ix = startDockX + i * (iconW + gap);
            int iy = dockTop + 4;
            if (mouseX >= ix && mouseX <= ix + iconW && mouseY >= iy && mouseY <= iy + 18) {
                String tip = TABS[i];
                int tw = textRenderer.getWidth(tip) + 10;
                int tx = ix + iconW / 2 - tw / 2;
                int ty = dockTop - 14;
                NeonButton.fillSmoothRounded(ctx, tx, ty, tw, 12, 3, 0xF0161624);
                NeonButton.drawSmoothRoundedOutline(ctx, tx, ty, tw, 12, 3, 0x447B68EE);
                ctx.drawCenteredTextWithShadow(textRenderer, tip, ix + iconW / 2, ty + 2, 0xFFF2F2FA);
                break;
            }
        }
    }

    private void drawBrandHeader(DrawContext ctx) {
        String brand = "ArtemVisuals";
        String tag = "FunTime";
        int textW = textRenderer.getWidth(brand);
        int tagW = textRenderer.getWidth(tag) + 8;
        int iconW = 18;
        int totalW = iconW + 4 + textW + 6 + tagW;
        int startX = left + (W - totalW) / 2;
        int y = top - 17;

        // Мягкое свечение за пульс-иконкой
        NeonButton.drawCircle(ctx, startX + 6, y + 5, 6, 0x337B68EE);

        int waveCol = 0xFF7B68EE;
        ctx.fill(startX, y + 5, startX + 4, y + 6, waveCol);
        ctx.fill(startX + 4, y + 2, startX + 5, y + 6, waveCol);
        ctx.fill(startX + 5, y + 0, startX + 6, y + 9, 0xFFB8A9FF);
        ctx.fill(startX + 6, y + 3, startX + 7, y + 10, waveCol);
        ctx.fill(startX + 7, y + 5, startX + 13, y + 6, waveCol);

        ctx.drawText(textRenderer, brand, startX + iconW, y + 1, 0xFFF6F6FC, true);

        int bx = startX + iconW + textW + 5;
        NeonButton.fillGradientRounded(ctx, bx, y, tagW, 11, 3, 0x886C5CE7, 0x664B3CA8);
        ctx.drawText(textRenderer, tag, bx + 4, y + 2, 0xFFE4DCFF, false);
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
