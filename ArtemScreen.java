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
 * Меню ArtemVisuals в стиле PulseVisuals: 20+ модулей, живой поиск, выбор цвета клиента и док.
 */
public class ArtemScreen extends Screen {
    private static final int W = 344;
    private static final int H = 196;
    private static final String[] TABS = {"Visuals", "HUD", "Прицел", "Utilities", "Профили"};
    private static final String[] TIME_NAMES = {"Сервер", "День", "Закат", "Ночь"};
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Плюс"};

    private static final int[] CLIENT_PALETTE = {0xFF6C5CE7, 0xFF00B4D8, 0xFFE056FD, 0xFFFFB703, 0xFF2EC4B6};
    private static final int[] OVERLAY_PALETTE = {0xFF6C5CE7, 0xFFFFD166, 0xFF00F5FF, 0xFFFF4D6D, 0xFFFFFFFF};
    private static final int[] CROSSHAIR_PALETTE = {0xFF00F5FF, 0xFF9B59FF, 0xFFFF2D95, 0xFF39FF14, 0xFFFFFFFF};

    private final Screen parent;
    private int tab = 0;
    private int left, top;

    private String searchQuery = "";
    private boolean searchFocused = false;

    private TextFieldWidget profileNameField;
    private String statusMessage = "";
    private long statusUntil = 0;
    private long openedAtMs = 0;

    // Всплывающее окно поднастроек слева (как на 2-м скриншоте PulseVisuals)
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

        int y = top + 28;
        int cw = (W - 24) / 2;
        int x0 = left + 9;
        int x1 = x0 + cw + 6;
        int step = 24;

        if (!searchQuery.isBlank()) {
            buildSearchResults(c, mc, x0, x1, y, cw, step);
        } else {
            switch (tab) {
                case 0 -> buildVisuals(c, mc, x0, x1, y, cw, step);
                case 1 -> buildHud(c, x0, x1, y, cw, step);
                case 2 -> buildCrosshair(c, x0, x1, y, cw, step);
                case 3 -> buildUtilities(c, mc, x0, x1, y, cw, step);
                case 4 -> buildProfiles(x0, y, cw);
            }
        }

        if (popupModule != null) {
            buildPopupControls(c);
        }

        // Плавающий док снизу с 5 иконками
        int dockW = 166;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 8;
        int iconW = 24;
        int gap = 6;
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
        // Модули как на скриншоте PulseVisuals + дополнительные эффекты
        list.add(new ModuleEntry("Motion Blur", "motion blur размытие движение fov", () -> EffectManager.motionBlur, v -> EffectManager.motionBlur = v, null));
        list.add(new ModuleEntry("Nimb", "nimb нимб кольцо голова", () -> EffectManager.nimb, v -> EffectManager.nimb = v, "nimb"));
        list.add(new ModuleEntry("Particles", "particles частицы мир", () -> c.masterEnabled, v -> c.masterEnabled = v, "particles"));
        list.add(new ModuleEntry("Render Tweaks", "render tweaks тряска огонь оптимизация", () -> EffectManager.renderTweaks, v -> EffectManager.renderTweaks = v, null));
        list.add(new ModuleEntry("Jump Circle", "jump circle круг прыжок", () -> EffectManager.jumpCircle, v -> EffectManager.jumpCircle = v, "jump_circle"));
        list.add(new ModuleEntry("Target ESP", "target esp аура кольцо цель", () -> EffectManager.targetEsp, v -> EffectManager.targetEsp = v, "target_esp"));
        list.add(new ModuleEntry("Trails", "trails трейлы шлейф след", () -> EffectManager.trails, v -> EffectManager.trails = v, "trails"));
        list.add(new ModuleEntry("Block Overlay", "block overlay обводка блок", () -> EffectManager.blockOverlay, v -> EffectManager.blockOverlay = v, "block_overlay"));
        list.add(new ModuleEntry("World Customizer", "world customizer мир цвет клиента небо время", () -> EffectManager.worldCustomizer, v -> EffectManager.worldCustomizer = v, "world_customizer"));
        list.add(new ModuleEntry("Hit Particles", "hit particles удары пвп криты", () -> EffectManager.hitParticles, v -> EffectManager.hitParticles = v, null));
        list.add(new ModuleEntry("Fullbright", "fullbright свет гамма темнота", () -> c.softLighting, v -> {
            c.softLighting = v;
            Optimizer.applyLighting(mc);
        }, null));
        list.add(new ModuleEntry("Leaf & Water FX", "leaf water листья вода светлячки", () -> c.leafParticles, v -> {
            c.leafParticles = v;
            c.waterSparkles = v;
            c.fireflies = v;
        }, null));

        // HUD модули
        list.add(new ModuleEntry("Top Watermark", "top watermark верхняя панель сервер фпс пинг", () -> c.showServer, v -> c.showServer = v, null));
        list.add(new ModuleEntry("TargetHUD", "target hud цель хп скин", () -> HudManager.showTargetHud, v -> HudManager.showTargetHud = v, null));
        list.add(new ModuleEntry("Hot Keys", "hot keys бинды клавиши", () -> HudManager.showHotKeys, v -> HudManager.showHotKeys = v, null));
        list.add(new ModuleEntry("Potions HUD", "potions зелья эффекты", () -> HudManager.showPotions, v -> HudManager.showPotions = v, null));
        list.add(new ModuleEntry("Keystrokes (WASD)", "wasd клавиши keystrokes", () -> c.showWasd, v -> c.showWasd = v, null));
        list.add(new ModuleEntry("Coordinates (XYZ)", "xyz координаты", () -> c.showXyz, v -> c.showXyz = v, null));
        list.add(new ModuleEntry("CPS Counter", "cps клики кпс", () -> c.showCps, v -> c.showCps = v, null));
        list.add(new ModuleEntry("FPS & Ping Mini", "fps ping фпс пинг", () -> c.showFps, v -> {
            c.showFps = v;
            c.showPing = v;
        }, null));
        list.add(new ModuleEntry("Custom Crosshair", "crosshair прицел", () -> c.customCrosshair, v -> c.customCrosshair = v, null));
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

    private void buildVisuals(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Motion Blur", () -> EffectManager.motionBlur, v -> EffectManager.motionBlur = v, null);
        toggle(x1, y, cw, "Nimb", () -> EffectManager.nimb, v -> EffectManager.nimb = v, "nimb");

        toggle(x0, y + step, cw, "Particles", () -> c.masterEnabled, v -> c.masterEnabled = v, "particles");
        toggle(x1, y + step, cw, "Render Tweaks", () -> EffectManager.renderTweaks, v -> EffectManager.renderTweaks = v, null);

        toggle(x0, y + step * 2, cw, "Jump Circle", () -> EffectManager.jumpCircle, v -> EffectManager.jumpCircle = v, "jump_circle");
        toggle(x1, y + step * 2, cw, "Target ESP", () -> EffectManager.targetEsp, v -> EffectManager.targetEsp = v, "target_esp");

        toggle(x0, y + step * 3, cw, "Trails", () -> EffectManager.trails, v -> EffectManager.trails = v, "trails");
        toggle(x1, y + step * 3, cw, "Block Overlay", () -> EffectManager.blockOverlay, v -> EffectManager.blockOverlay = v, "block_overlay");

        toggle(x0, y + step * 4, cw, "World Customizer", () -> EffectManager.worldCustomizer, v -> EffectManager.worldCustomizer = v, "world_customizer");
        toggle(x1, y + step * 4, cw, "Hit Particles", () -> EffectManager.hitParticles, v -> EffectManager.hitParticles = v, null);

        toggle(x0, y + step * 5, cw, "Fullbright", () -> c.softLighting, v -> {
            c.softLighting = v;
            Optimizer.applyLighting(mc);
        }, null);
        toggle(x1, y + step * 5, cw, "Leaf & Water FX", () -> c.leafParticles, v -> {
            c.leafParticles = v;
            c.waterSparkles = v;
            c.fireflies = v;
        }, null);
    }

    private void buildHud(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Top Watermark", () -> c.showServer, v -> c.showServer = v, null);
        toggle(x1, y, cw, "TargetHUD", () -> HudManager.showTargetHud, v -> HudManager.showTargetHud = v, null);

        toggle(x0, y + step, cw, "Hot Keys", () -> HudManager.showHotKeys, v -> HudManager.showHotKeys = v, null);
        toggle(x1, y + step, cw, "Potions HUD", () -> HudManager.showPotions, v -> HudManager.showPotions = v, null);

        toggle(x0, y + step * 2, cw, "Keystrokes (WASD)", () -> c.showWasd, v -> c.showWasd = v, null);
        toggle(x1, y + step * 2, cw, "Coordinates (XYZ)", () -> c.showXyz, v -> c.showXyz = v, null);

        toggle(x0, y + step * 3, cw, "CPS Counter", () -> c.showCps, v -> c.showCps = v, null);
        toggle(x1, y + step * 3, cw, "FPS & Ping Mini", () -> c.showFps, v -> {
            c.showFps = v;
            c.showPing = v;
        }, null);

        toggle(x0, y + step * 4 + 2, cw * 2 + 6, "Режим перетаскивания элементов HUD мышью",
                () -> c.hudEditMode, v -> c.hudEditMode = v, null);
    }

    private void buildCrosshair(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Custom Crosshair", () -> c.customCrosshair, v -> c.customCrosshair = v, null);
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

    private void buildUtilities(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        addBtn(x0, y, cw * 2 + 6, 21, () -> "⚔ Активировать пресет FunTime PvP", null, () -> {
            applyFunTimePreset(c, mc);
            setStatus("Пресет FunTime PvP активирован");
        });
        addBtn(x0, y + step, cw * 2 + 6, 21, () -> "⚡ Применить пресет высокого FPS", null, () -> {
            Optimizer.applyPerformancePreset(mc);
            setStatus("Пресет производительности применён");
        });
        addBtn(x0, y + step * 2, cw * 2 + 6, 21,
                () -> Optimizer.hasBackup() ? "↺ Вернуть прежние настройки видео" : "Бэкап настроек отсутствует",
                null, () -> {
                    if (Optimizer.hasBackup()) {
                        Optimizer.restoreSettings(mc);
                        setStatus("Настройки восстановлены");
                    } else {
                        setStatus("Нечего восстанавливать");
                    }
                });
        addBtn(x0, y + step * 3, cw * 2 + 6, 21, () -> "Сбросить настройки мода", null, () -> {
            c.resetDefaults();
            rebuildContent();
            setStatus("Сброшено к умолчаниям");
        });
    }

    private void buildPopupControls(ArtemConfig c) {
        int pw = 144;
        int px = Math.max(6, left - pw - 8);
        int py = top + 48;

        int ix = px + 8;
        int iw = pw - 16;
        int iy = py + 22;

        switch (popupModule) {
            case "world_customizer" -> {
                addDrawableChild(NeonButton.swatches(ix, iy, iw, 16, "Цвет клиента", CLIENT_PALETTE,
                        () -> NeonButton.ACCENT, col -> {
                            NeonButton.setClientAccent(col);
                            EffectManager.nimbColor = col;
                            EffectManager.trailColor = col;
                            EffectManager.targetEspColor = col;
                        }));
                cycle(ix, iy + 23, iw, () -> "Время: " + TIME_NAMES[EffectManager.worldTimeMode],
                        () -> EffectManager.worldTimeMode = (EffectManager.worldTimeMode + 1) % 4);
                toggle(ix, iy + 47, iw, "Шейдер неба", () -> EffectManager.skyTint, v -> EffectManager.skyTint = v, null);
            }
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
            case "nimb" -> {
                addDrawableChild(NeonButton.swatches(ix, iy, iw, 16, "Цвет нимба", OVERLAY_PALETTE,
                        () -> EffectManager.nimbColor, col -> EffectManager.nimbColor = col));
            }
            case "trails" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 19, "Длина шлейфа", 12, 45, true,
                        () -> EffectManager.trailLength, v -> EffectManager.trailLength = (int) v));
                addDrawableChild(NeonButton.swatches(ix, iy + 26, iw, 16, "Цвет", OVERLAY_PALETTE,
                        () -> EffectManager.trailColor, col -> EffectManager.trailColor = col));
            }
            case "target_esp" -> {
                addDrawableChild(NeonButton.swatches(ix, iy, iw, 16, "Цвет кольца", OVERLAY_PALETTE,
                        () -> EffectManager.targetEspColor, col -> EffectManager.targetEspColor = col));
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
        c.showXyz = false;
        c.showCps = false;
        c.showWasd = false;
        c.showServer = true;
        HudManager.showHotKeys = true;
        HudManager.showPotions = true;
        HudManager.showTargetHud = true;

        EffectManager.nimb = true;
        EffectManager.trails = true;
        EffectManager.targetEsp = true;
        EffectManager.jumpCircle = true;
        EffectManager.blockOverlay = true;
        EffectManager.hitParticles = true;
        EffectManager.renderTweaks = true;

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

    // ——— Поиск и клики по верхним вкладкам ———

    private int searchBoxX() { return left + W - 104; }
    private int searchBoxY() { return top + 6; }
    private int searchBoxW() { return 94; }
    private int searchBoxH() { return 14; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Клик по верхним вкладкам: Visuals / HUD / Utilities
        int tx = left + 10;
        int ty = top + 8;
        String[] topTabs = {"Visuals", "HUD", "Utilities"};
        int[] tabIndices = {0, 1, 3};
        for (int i = 0; i < topTabs.length; i++) {
            int tw = Math.round(textRenderer.getWidth(topTabs[i]) * 0.88f);
            if (mouseX >= tx - 2 && mouseX <= tx + tw + 2 && mouseY >= ty - 2 && mouseY <= ty + 10) {
                tab = tabIndices[i];
                searchQuery = "";
                searchFocused = false;
                popupModule = null;
                rebuildContent();
                return true;
            }
            tx += tw + Math.round(textRenderer.getWidth("  /  ") * 0.88f);
        }

        // Клик по строке поиска
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
        int dimAlpha = (int) (0x82 * p);
        ctx.fill(0, 0, width, height, dimAlpha << 24);

        // Главное окно в стиле PulseVisuals
        NeonButton.drawAmbientGlow(ctx, left, top, W, H, 8, NeonButton.ACCENT, 2);
        NeonButton.fillGradientRounded(ctx, left, top, W, H, 8, 0xF50E0E16, 0xF50A0A10);
        NeonButton.drawSmoothRoundedOutline(ctx, left, top, W, H, 8, 0x22FFFFFF);

        // Тонкий разделитель под шапкой
        ctx.fill(left + 9, top + 23, left + W - 9, top + 24, 0x12FFFFFF);

        // Всплывающее окно поднастроек слева (как World Customizer на 2-м скриншоте)
        if (popupModule != null) {
            int pw = 144;
            int ph = "world_customizer".equals(popupModule) ? 98 : ("nimb".equals(popupModule) || "target_esp".equals(popupModule) ? 46 : 74);
            int px = Math.max(6, left - pw - 8);
            int py = top + 48;
            NeonButton.drawAmbientGlow(ctx, px, py, pw, ph, 7, NeonButton.ACCENT, 2);
            NeonButton.fillGradientRounded(ctx, px, py, pw, ph, 7, 0xF510101A, 0xF50B0B12);
            NeonButton.drawSmoothRoundedOutline(ctx, px, py, pw, ph, 7, 0x33FFFFFF);
            ctx.fill(px + 8, py + 18, px + pw - 8, py + 19, 0x12FFFFFF);
        }

        // Нижний плавающий док
        int dockW = 166;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 8;
        NeonButton.fillGradientRounded(ctx, dockLeft, dockTop, dockW, 26, 8, 0xF50E0E16, 0xF50A0A10);
        NeonButton.drawSmoothRoundedOutline(ctx, dockLeft, dockTop, dockW, 26, 8, 0x22FFFFFF);
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
        drawTopCategories(ctx);
        drawSearchBar(ctx, mouseX, mouseY);

        if (popupModule != null) {
            int pw = 144;
            int px = Math.max(6, left - pw - 8);
            int py = top + 48;
            String title = switch (popupModule) {
                case "world_customizer" -> "World Customizer";
                case "block_overlay" -> "Block Overlay";
                case "jump_circle" -> "Jump Circle";
                case "nimb" -> "Nimb";
                case "trails" -> "Trails";
                case "target_esp" -> "Target ESP";
                default -> "Particles";
            };
            NeonButton.drawSmallText(ctx, textRenderer, title, px + 8, py + 6, 0xFFE6E6F4, 0.85f);
        }

        if (tab == 2 && searchQuery.isBlank()) {
            int px = left + W / 2;
            int py = top + 146;
            NeonButton.fillSmoothRounded(ctx, px - 24, py - 24, 48, 48, 6, 0xFF141420);
            NeonButton.drawSmoothRoundedOutline(ctx, px - 24, py - 24, 48, 48, 6, 0x28FFFFFF);
            CrosshairRenderer.renderPreview(ctx, px, py, ArtemConfig.get());
        }

        if (!searchQuery.isBlank() && children().size() <= 5) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Ничего не найдено по запросу «" + searchQuery + "»",
                    left + W / 2, top + 96, 0xFF6E6E88);
        }

        if (tab == 0 && searchQuery.isBlank() && popupModule == null) {
            NeonButton.drawSmallText(ctx, textRenderer,
                    "Нажми ПКМ по модулю, чтобы открыть его настройки (цвет, радиус, время суток)",
                    left + 32, top + H - 12, 0xFF4E4E64, 0.80f);
        }

        if (tab == 1 && searchQuery.isBlank() && ArtemConfig.get().hudEditMode) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Перетаскивайте элементы HUD мышью прямо на экране",
                    left + W / 2, top + 152, NeonButton.ACCENT_BRIGHT);
        }

        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMessage, left + W / 2, top + H - 14, NeonButton.ACCENT_BRIGHT);
        }

        if (tab == 4 && searchQuery.isBlank() && ProfileManager.listProfiles().isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Нет сохранённых профилей", left + W / 2, top + 104, 0xFF6E6E85);
        }

        ctx.getMatrices().pop();
    }

    private void drawTopCategories(DrawContext ctx) {
        int tx = left + 10;
        int ty = top + 9;
        String[] topTabs = {"Visuals", "HUD", "Utilities"};
        int[] tabIndices = {0, 1, 3};

        for (int i = 0; i < topTabs.length; i++) {
            boolean active = searchQuery.isBlank() && tab == tabIndices[i];
            int col = active ? 0xFFF4F4FC : 0xFF56566C;
            NeonButton.drawSmallText(ctx, textRenderer, topTabs[i], tx, ty, col, 0.88f);
            tx += Math.round(textRenderer.getWidth(topTabs[i]) * 0.88f);
            if (i < topTabs.length - 1) {
                NeonButton.drawSmallText(ctx, textRenderer, "  /  ", tx, ty, 0xFF3A3A4E, 0.88f);
                tx += Math.round(textRenderer.getWidth("  /  ") * 0.88f);
            }
        }
    }

    private void drawSearchBar(DrawContext ctx, int mouseX, int mouseY) {
        int sx = searchBoxX(), sy = searchBoxY(), sw = searchBoxW(), sh = searchBoxH();
        boolean hover = mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh;

        NeonButton.fillSmoothRounded(ctx, sx, sy, sw, sh, 4, (searchFocused || hover) ? 0xFF181826 : 0xFF12121D);
        NeonButton.drawSmoothRoundedOutline(ctx, sx, sy, sw, sh, 4,
                searchFocused ? NeonButton.ACCENT_BRIGHT : (hover ? 0x2EFFFFFF : 0x14FFFFFF));

        int lx = sx + 6;
        int ly = sy + 6;
        NeonButton.drawCircle(ctx, lx, ly, 2, 0xFF72728A);
        NeonButton.drawCircle(ctx, lx, ly, 1, 0xFF141420);
        ctx.fill(lx + 2, ly + 2, lx + 4, ly + 4, 0xFF72728A);

        int textX = sx + 13;
        int textY = sy + 4;
        if (searchQuery.isEmpty() && !searchFocused) {
            NeonButton.drawSmallText(ctx, textRenderer, "Поиск", textX, textY, 0xFF56566C, 0.85f);
        } else {
            String visible = textRenderer.trimToWidth(searchQuery, sw - 24);
            NeonButton.drawSmallText(ctx, textRenderer, visible, textX, textY, 0xFFF2F2FA, 0.85f);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = textX + Math.round(textRenderer.getWidth(visible) * 0.85f);
                ctx.fill(cx, textY - 1, cx + 1, textY + 7, NeonButton.ACCENT_BRIGHT);
            }
        }

        if (!searchQuery.isEmpty()) {
            NeonButton.drawSmallText(ctx, textRenderer, "×", sx + sw - 9, textY, 0xFF8E8EA8, 0.85f);
        }
    }

    private void drawBrandHeader(DrawContext ctx) {
        String brand = "ArtemVisuals";
        int textW = textRenderer.getWidth(brand);
        int iconW = 20;
        int totalW = iconW + 4 + textW;
        int startX = left + (W - totalW) / 2;
        int y = top - 20;

        NeonButton.drawCircle(ctx, startX + 7, y + 6, 7, (NeonButton.ACCENT & 0x00FFFFFF) | 0x33000000);

        int waveCol = NeonButton.ACCENT;
        ctx.fill(startX, y + 6, startX + 4, y + 7, waveCol);
        ctx.fill(startX + 4, y + 2, startX + 5, y + 7, waveCol);
        ctx.fill(startX + 5, y + 0, startX + 6, y + 10, NeonButton.ACCENT_BRIGHT);
        ctx.fill(startX + 6, y + 4, startX + 7, y + 11, waveCol);
        ctx.fill(startX + 7, y + 6, startX + 14, y + 7, waveCol);

        ctx.drawText(textRenderer, brand, startX + iconW, y + 2, 0xFFF6F6FC, true);
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
