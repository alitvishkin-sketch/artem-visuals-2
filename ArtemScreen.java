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
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Меню ArtemVisuals 3.0 в точном стиле PulseVisual с живой настройкой цвета окна.
 */
public class ArtemScreen extends Screen {
    private static final int W = 382;
    private static final int H = 222;
    private static final String[] TABS_RU = {"Visuals", "HUD", "Прицел", "Utilities", "Настройки"};
    private static final String[] TABS_EN = {"Visuals", "HUD", "Crosshair", "Utilities", "Settings"};
    private static final String[] TIME_NAMES = {"Сервер", "День", "Закат", "Ночь"};
    private static final String[] ESP_STYLES = {"Бокс", "Углы", "Двойной"};
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Плюс"};

    private static final int[] ACCENT_PALETTE = {0xFF7B68EE, 0xFF00D4FF, 0xFFE056FD, 0xFFFFB703, 0xFF2EC4B6, 0xFFFF4D6D};
    private static final int[] WINDOW_BG_PALETTE = {0x0C0C12, 0x130D21, 0x0B1120, 0x1C0D16, 0x0B1714, 0x15151A};

    private final Screen parent;
    private int tab = 0;
    private int left, top;

    private String searchQuery = "";
    private boolean searchFocused = false;

    private TextFieldWidget profileNameField;
    private String statusMessage = "";
    private long statusUntil = 0;
    private long openedAtMs = 0;

    // Всплывающее окно настроек слева (как World Customizer в PulseVisual)
    private String popupModule = null;
    private boolean listeningBind = false;

    public ArtemScreen(Screen parent) {
        super(Text.literal("ArtemVisuals"));
        this.parent = parent;
    }

    private record ModuleEntry(String id, String name, String keywords, BooleanSupplier get, Consumer<Boolean> set, String popupKey) {}

    private boolean isEn() {
        return "EN".equalsIgnoreCase(ArtemConfig.get().language);
    }

    private String tr(String ru, String en) {
        return isEn() ? en : ru;
    }

    @Override
    protected void init() {
        if (openedAtMs == 0) {
            openedAtMs = System.currentTimeMillis();
        }
        left = (width - W) / 2;
        top = (height - H) / 2 - 2;
        rebuildContent();
    }

    private void rebuildContent() {
        clearChildren();
        profileNameField = null;

        ArtemConfig c = ArtemConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        int y = top + 34;
        int cw = (W - 30) / 2;
        int x0 = left + 11;
        int x1 = x0 + cw + 8;
        int step = 30;

        if (!searchQuery.isBlank()) {
            buildSearchResults(c, mc, x0, x1, y, cw, step);
        } else {
            switch (tab) {
                case 0 -> buildVisuals(c, mc, x0, x1, y, cw, step);
                case 1 -> buildHud(c, x0, x1, y, cw, 22);
                case 2 -> buildCrosshair(c, x0, x1, y, cw, 26);
                case 3 -> buildUtilities(c, mc, x0, x1, y, cw, 28);
                case 4 -> buildSettingsAndTheme(c, x0, x1, y, cw, 24);
            }
        }

        if (popupModule != null) {
            buildPopupControls(c);
        }

        // Кнопка смены цвета окна прямо в шапке справа от Поиска
        addDrawableChild(new NeonButton(
                left + W - 46,
                top + 8,
                35,
                16,
                () -> "Цвет",
                () -> "window_theme".equals(popupModule),
                () -> {
                    listeningBind = false;
                    popupModule = "window_theme".equals(popupModule) ? null : "window_theme";
                    rebuildContent();
                },
                NeonButton.Style.ACTION
        ));

        // Нижний плавающий док
        int dockW = 166;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 8;
        int iconW = 24;
        int gap = 6;
        int startDockX = dockLeft + (dockW - (iconW * 5 + gap * 4)) / 2;
        String[] tabNames = isEn() ? TABS_EN : TABS_RU;

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            addDrawableChild(new NeonButton(
                    startDockX + i * (iconW + gap),
                    dockTop + 4,
                    iconW,
                    18,
                    () -> tabNames[idx],
                    () -> tab == idx && searchQuery.isBlank(),
                    () -> {
                        tab = idx;
                        searchQuery = "";
                        searchFocused = false;
                        popupModule = null;
                        listeningBind = false;
                        rebuildContent();
                    },
                    NeonButton.Style.DOCK_ICON,
                    i
            ));
        }
    }

    private List<ModuleEntry> allModules(ArtemConfig c, MinecraftClient mc) {
        List<ModuleEntry> list = new ArrayList<>();
        // Точно как на скриншоте PulseVisual + дополнительные функции
        list.add(new ModuleEntry("motion_blur", "Motion Blur", "motion blur размытие", () -> c.motionBlur, v -> c.motionBlur = v, null));
        list.add(new ModuleEntry("nimb", "Nimb", "nimb нимб кольцо", () -> c.nimb, v -> c.nimb = v, "nimb"));
        list.add(new ModuleEntry("particles", "Particles", "particles частицы", () -> c.masterEnabled, v -> c.masterEnabled = v, "particles"));
        list.add(new ModuleEntry("render_tweaks", "Render Tweaks", "render tweaks тряска", () -> c.renderTweaks, v -> c.renderTweaks = v, null));
        list.add(new ModuleEntry("nametags", "NameTags", "nametags ники теги", () -> c.nameTags, v -> c.nameTags = v, "nametags"));
        list.add(new ModuleEntry("target_hud", "Target HUD", "target hud скин хп", () -> c.showTargetHud, v -> c.showTargetHud = v, null));
        list.add(new ModuleEntry("target_esp", "Target ESP", "target esp кольцо цель", () -> c.targetEsp, v -> c.targetEsp = v, "target_esp"));
        list.add(new ModuleEntry("trails", "Trails", "trails трейлы шлейф", () -> c.trails, v -> c.trails = v, "trails"));
        list.add(new ModuleEntry("world_cust", "World Customizer", "world customizer цвет окна время небо", () -> c.worldCustomizer, v -> c.worldCustomizer = v, "window_theme"));
        list.add(new ModuleEntry("jump_circle", "Jump Circle", "jump circle круг прыжок", () -> c.jumpCircle, v -> c.jumpCircle = v, "jump_circle"));

        list.add(new ModuleEntry("esp", "ESP", "esp боксы игроки", () -> c.esp, v -> c.esp = v, "esp"));
        list.add(new ModuleEntry("player_hl", "Player Highlight", "player highlight подсветка", () -> c.playerHighlight, v -> c.playerHighlight = v, "player_hl"));
        list.add(new ModuleEntry("tracers", "Tracers", "tracers линии", () -> c.tracers, v -> c.tracers = v, "tracers"));
        list.add(new ModuleEntry("chams", "Chams", "chams чамсы", () -> c.chams, v -> c.chams = v, "chams"));
        list.add(new ModuleEntry("item_esp", "Item ESP", "item esp предметы лут", () -> c.itemEsp, v -> c.itemEsp = v, "item_esp"));
        list.add(new ModuleEntry("mob_esp", "Mob ESP", "mob esp мобы", () -> c.mobEsp, v -> c.mobEsp = v, "mob_esp"));
        list.add(new ModuleEntry("hit_particles", "HitParticles", "hit particles удары", () -> c.hitParticles, v -> c.hitParticles = v, "hit_particles"));
        list.add(new ModuleEntry("dmg_ind", "Damage Indicator", "damage indicator урон", () -> c.damageIndicator, v -> c.damageIndicator = v, "dmg_ind"));
        list.add(new ModuleEntry("block_overlay", "Block Overlay", "block overlay обводка", () -> c.blockOverlay, v -> c.blockOverlay = v, "block_overlay"));
        list.add(new ModuleEntry("fullbright", "Fullbright", "fullbright свет гамма", () -> c.softLighting, v -> {
            c.softLighting = v;
            Optimizer.applyLighting(mc);
        }, null));
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
            toggle(colX, rowY, cw, 25, m.name(), m.get(), m.set(), m.popupKey());
            idx++;
        }
    }

    /**
     * Главная сетка Visuals 1-в-1 как на скриншоте PulseVisual (10 просторных карточек, 2 колонки x 5 рядов).
     */
    private void buildVisuals(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, 25, "Motion Blur", () -> c.motionBlur, v -> c.motionBlur = v, null);
        toggle(x1, y, cw, 25, "Nimb", () -> c.nimb, v -> c.nimb = v, "nimb");

        toggle(x0, y + step, cw, 25, "Particles", () -> c.masterEnabled, v -> c.masterEnabled = v, "particles");
        toggle(x1, y + step, cw, 25, "Render Tweaks", () -> c.renderTweaks, v -> c.renderTweaks = v, null);

        toggle(x0, y + step * 2, cw, 25, "NameTags", () -> c.nameTags, v -> c.nameTags = v, "nametags");
        toggle(x1, y + step * 2, cw, 25, "Jump Circle", () -> c.jumpCircle, v -> c.jumpCircle = v, "jump_circle");

        toggle(x0, y + step * 3, cw, 25, "Target ESP", () -> c.targetEsp, v -> c.targetEsp = v, "target_esp");
        toggle(x1, y + step * 3, cw, 25, "Trails", () -> c.trails, v -> c.trails = v, "trails");

        toggle(x0, y + step * 4, cw, 25, "World Customizer", () -> c.worldCustomizer, v -> c.worldCustomizer = v, "window_theme");
        toggle(x1, y + step * 4, cw, 25, "Hit Particles", () -> c.hitParticles, v -> c.hitParticles = v, "hit_particles");

        // Аккуратная нижняя строка с дополнительными ESP-модулями (ESP, Tracers, Chams, Item ESP, Block Overlay)
        int miniW = (W - 38) / 5;
        int my = y + step * 5 + 2;
        miniToggle(left + 11, my, miniW, "ESP", () -> c.esp, v -> c.esp = v, "esp");
        miniToggle(left + 11 + (miniW + 4), my, miniW, "Chams", () -> c.chams, v -> c.chams = v, "chams");
        miniToggle(left + 11 + (miniW + 4) * 2, my, miniW, "Tracers", () -> c.tracers, v -> c.tracers = v, "tracers");
        miniToggle(left + 11 + (miniW + 4) * 3, my, miniW, "Items", () -> c.itemEsp, v -> c.itemEsp = v, "item_esp");
        miniToggle(left + 11 + (miniW + 4) * 4, my, miniW, "Overlay", () -> c.blockOverlay, v -> c.blockOverlay = v, "block_overlay");
    }

    private void buildHud(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, 19, "Top Watermark", () -> c.showServer, v -> c.showServer = v, null);
        toggle(x1, y, cw, 19, "Target HUD", () -> c.showTargetHud, v -> c.showTargetHud = v, null);

        toggle(x0, y + step, cw, 19, "Hot Keys", () -> c.showHotKeys, v -> c.showHotKeys = v, null);
        toggle(x1, y + step, cw, 19, "Potions HUD", () -> c.showPotions, v -> c.showPotions = v, null);

        toggle(x0, y + step * 2, cw, 19, "Armor HUD", () -> c.showArmor, v -> c.showArmor = v, null);
        toggle(x1, y + step * 2, cw, 19, "ArrayList", () -> c.showArrayList, v -> c.showArrayList = v, null);

        toggle(x0, y + step * 3, cw, 19, "Radar", () -> c.showRadar, v -> c.showRadar = v, null);
        toggle(x1, y + step * 3, cw, 19, "MiniMap", () -> c.showMinimap, v -> c.showMinimap = v, null);

        toggle(x0, y + step * 4, cw, 19, "WASD / Keystrokes", () -> c.showKeystrokes, v -> {
            c.showKeystrokes = v;
            c.showWasd = false;
        }, null);
        toggle(x1, y + step * 4, cw, 19, "XYZ & Speed", () -> c.showXyz, v -> {
            c.showXyz = v;
            c.showSpeed = v;
        }, null);

        toggle(x0, y + step * 5, cw, 19, "CPS (LMB / RMB)", () -> c.showCps, v -> c.showCps = v, null);
        toggle(x1, y + step * 5, cw, 19, "Health & Hunger", () -> c.showHealthHud, v -> {
            c.showHealthHud = v;
            c.showHungerHud = v;
        }, null);

        addBtn(x0, y + step * 6 + 3, cw * 2 + 8, 21,
                () -> tr("Открыть HUD Editor (Перетаскивание + Размер)", "Open HUD Editor (Drag + Resize)"),
                null, () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen(this)));
    }

    private void buildCrosshair(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, 22, "Custom Crosshair", () -> c.customCrosshair, v -> c.customCrosshair = v, null);
        cycle(x1, y, cw, 22, () -> tr("Стиль: ", "Style: ") + STYLES[Math.min(c.crosshairStyle, 3)], () -> c.crosshairStyle = (c.crosshairStyle + 1) % 4);

        addDrawableChild(NeonButton.slider(x0, y + step, cw, 20, tr("Размер", "Size"), 3, 12, true,
                () -> c.crosshairSize, v -> { c.crosshairSize = (int) v; c.save(); }));
        addDrawableChild(NeonButton.slider(x1, y + step, cw, 20, tr("Зазор", "Gap"), 0, 8, true,
                () -> c.crosshairGap, v -> { c.crosshairGap = (int) v; c.save(); }));
        addDrawableChild(NeonButton.slider(x0, y + step * 2, cw, 20, tr("Толщина", "Thickness"), 1, 3, true,
                () -> c.crosshairThickness, v -> { c.crosshairThickness = (int) v; c.save(); }));

        addDrawableChild(NeonButton.swatches(x1, y + step * 2 + 2, cw, 18, tr("Цвет", "Color"), ACCENT_PALETTE,
                () -> c.crosshairColor, col -> { c.crosshairColor = col; c.save(); }));

        toggle(x0, y + step * 3, cw, 22, tr("Обводка", "Outline"), () -> c.crosshairOutline, v -> c.crosshairOutline = v, null);
        toggle(x1, y + step * 3, cw, 22, tr("Динамика", "Dynamic"), () -> c.crosshairDynamic, v -> c.crosshairDynamic = v, null);
    }

    private void buildUtilities(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw * 2 + 8, 23, tr("Авто-определение сервера FunTime", "Auto-detect FunTime Server"),
                () -> c.autoFunTime, v -> c.autoFunTime = v, null);

        addBtn(x0, y + step, cw * 2 + 8, 23,
                () -> tr("Применить профиль FunTime PvP (Чистый HUD + FPS)", "Apply FunTime PvP Profile (Clean HUD + FPS)"),
                null, () -> {
                    HudManager.applyFunTimePreset(mc);
                    setStatus(tr("Профиль FunTime применён", "FunTime profile applied"));
                });

        toggle(x0, y + step * 2, cw, 23, "Fullbright", () -> c.softLighting, v -> {
            c.softLighting = v;
            Optimizer.applyLighting(mc);
        }, null);
        toggle(x1, y + step * 2, cw, 23, "Damage Indicator", () -> c.damageIndicator, v -> c.damageIndicator = v, null);

        addBtn(x0, y + step * 3, cw, 23,
                () -> tr("Оптимизация FPS", "FPS Boost"),
                null, () -> {
                    Optimizer.applyPerformancePreset(mc);
                    setStatus(tr("Оптимизация FPS применена", "FPS boost applied"));
                });
        addBtn(x1, y + step * 3, cw, 23,
                () -> Optimizer.hasBackup() ? tr("Вернуть настройки", "Restore Video") : tr("Бэкап пуст", "No Backup"),
                null, () -> {
                    if (Optimizer.hasBackup()) {
                        Optimizer.restoreSettings(mc);
                        setStatus(tr("Настройки видео восстановлены", "Video settings restored"));
                    }
                });

        addBtn(x0, y + step * 4, cw * 2 + 8, 23,
                () -> tr("Сохранить текущие настройки в профиль FunTime", "Save Current Settings to FunTime Profile"),
                null, () -> {
                    ProfileManager.saveProfile("FunTime");
                    setStatus(tr("Сохранено в профиль FunTime", "Saved to FunTime profile"));
                });
    }

    private void buildSettingsAndTheme(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        // Прямая настройка цвета окна и акцента
        addDrawableChild(NeonButton.swatches(x0, y + 2, cw, 18, tr("Акцент", "Accent"), ACCENT_PALETTE,
                () -> c.clientAccent, col -> {
                    c.clientAccent = col;
                    c.useCustomHue = false;
                    c.rgbMode = false;
                    c.save();
                }));
        addDrawableChild(NeonButton.swatches(x1, y + 2, cw, 18, tr("Фон окна", "Window"), WINDOW_BG_PALETTE,
                () -> c.windowBgColor, col -> {
                    c.windowBgColor = col & 0x00FFFFFF;
                    c.save();
                }));

        addDrawableChild(NeonButton.hueSlider(x0, y + step, cw, 20, tr("Оттенок окна", "Window Hue"),
                () -> c.customHue, v -> {
                    c.customHue = (float) v;
                    c.useCustomHue = true;
                    c.rgbMode = false;
                    c.save();
                }));
        addDrawableChild(NeonButton.slider(x1, y + step, cw, 20, tr("Прозрачность", "Opacity"), 0.60, 0.98, false,
                () -> c.windowAlpha, v -> {
                    c.windowAlpha = (float) v;
                    c.save();
                }));

        toggle(x0, y + step * 2 + 2, cw, 20, tr("RGB Перелив", "RGB Mode"), () -> c.rgbMode, v -> c.rgbMode = v, null);
        cycle(x1, y + step * 2 + 2, cw, 20, () -> tr("Язык: ", "Language: ") + c.language, () -> {
            c.language = isEn() ? "RU" : "EN";
            rebuildContent();
        });

        addBtn(x0, y + step * 3 + 2, cw, 20, () -> tr("Экспорт в буфер", "Export Config"), null, () -> {
            MinecraftClient.getInstance().keyboard.setClipboard(c.exportBase64());
            setStatus(tr("Конфиг скопирован в буфер обмена", "Config copied to clipboard"));
        });
        addBtn(x1, y + step * 3 + 2, cw, 20, () -> tr("Импорт из буфера", "Import Config"), null, () -> {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            if (ArtemConfig.get().importBase64(clip)) {
                rebuildContent();
                setStatus(tr("Конфиг импортирован", "Config imported"));
            } else {
                setStatus(tr("Неверный конфиг в буфере", "Invalid config"));
            }
        });

        profileNameField = new TextFieldWidget(textRenderer, x0, y + step * 4 + 2, cw + 18, 18, Text.literal("Profile"));
        profileNameField.setMaxLength(24);
        profileNameField.setPlaceholder(Text.literal(tr("Имя профиля...", "Profile name...")));
        addDrawableChild(profileNameField);

        addBtn(x0 + cw + 22, y + step * 4 + 2, cw - 14, 18, () -> tr("Сохранить", "Save"), null, () -> {
            String name = profileNameField.getText();
            if (ProfileManager.saveProfile(name)) {
                setStatus(tr("Сохранено: ", "Saved: ") + name);
                rebuildContent();
            }
        });

        addBtn(x0, top + H - 27, cw * 2 + 8, 19, () -> tr("Сбросить настройки по умолчанию", "Reset All Settings"), null, () -> {
            c.resetDefaults();
            rebuildContent();
            setStatus(tr("Настройки сброшены", "Settings reset"));
        });
    }

    /**
     * Левая плавающая панель настроек как на 2-м скриншоте PulseVisual (включая полную настройку цвета окошка!).
     */
    private void buildPopupControls(ArtemConfig c) {
        int pw = 158;
        int px = Math.max(6, left - pw - 8);
        int py = top + 34;
        int ix = px + 9;
        int iw = pw - 18;
        int iy = py + 24;

        switch (popupModule) {
            case "window_theme" -> {
                addDrawableChild(NeonButton.swatches(ix, iy, iw, 16, tr("Цвет клиента", "Accent"), ACCENT_PALETTE,
                        () -> c.clientAccent, col -> {
                            c.clientAccent = col;
                            c.useCustomHue = false;
                            c.rgbMode = false;
                            c.save();
                        }));
                addDrawableChild(NeonButton.hueSlider(ix, iy + 21, iw, 20, tr("Оттенок", "Hue"),
                        () -> c.customHue, v -> {
                            c.customHue = (float) v;
                            c.useCustomHue = true;
                            c.rgbMode = false;
                            c.save();
                        }));
                addDrawableChild(NeonButton.swatches(ix, iy + 45, iw, 16, tr("Фон окна", "Window BG"), WINDOW_BG_PALETTE,
                        () -> c.windowBgColor, col -> {
                            c.windowBgColor = col & 0x00FFFFFF;
                            c.save();
                        }));
                addDrawableChild(NeonButton.slider(ix, iy + 65, iw, 20, tr("Прозрачность", "Opacity"), 0.60, 0.98, false,
                        () -> c.windowAlpha, v -> {
                            c.windowAlpha = (float) v;
                            c.save();
                        }));
                toggle(ix, iy + 89, iw, 20, tr("Включить шейдер", "Sky Shader"), () -> c.skyTint, v -> c.skyTint = v, null);
                toggle(ix, iy + 112, iw, 20, tr("Цветокоррекция", "Color Tint"), () -> c.colorCorrection, v -> c.colorCorrection = v, null);
            }
            case "esp" -> {
                cycle(ix, iy, iw, 20, () -> tr("Стиль: ", "Style: ") + ESP_STYLES[c.espStyle % 3],
                        () -> c.espStyle = (c.espStyle + 1) % 3);
                addDrawableChild(NeonButton.slider(ix, iy + 23, iw, 20, tr("Прозрачность", "Alpha"), 0.2, 1.0, false,
                        () -> c.espAlpha, v -> { c.espAlpha = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 46, iw, 16, tr("Цвет", "Color"), ACCENT_PALETTE,
                        () -> c.espColor, col -> { c.espColor = col; c.save(); }));
            }
            case "nametags" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 20, tr("Размер", "Scale"), 0.75, 1.25, false,
                        () -> c.nameTagsScale, v -> { c.nameTagsScale = (float) v; c.save(); }));
                toggle(ix, iy + 23, iw, 20, tr("Показывать HP", "Show HP"), () -> c.nameTagsHealth, v -> c.nameTagsHealth = v, null);
                toggle(ix, iy + 46, iw, 20, tr("Дистанция", "Distance"), () -> c.nameTagsDistance, v -> c.nameTagsDistance = v, null);
            }
            case "nimb" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 20, tr("Радиус", "Radius"), 0.25, 0.65, false,
                        () -> c.nimbSize, v -> { c.nimbSize = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 24, iw, 16, tr("Цвет", "Color"), ACCENT_PALETTE,
                        () -> c.nimbColor, col -> { c.nimbColor = col; c.save(); }));
            }
            case "trails" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 20, tr("Длина", "Length"), 10, 48, true,
                        () -> c.trailLength, v -> { c.trailLength = (int) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 24, iw, 16, tr("Цвет", "Color"), ACCENT_PALETTE,
                        () -> c.trailColor, col -> { c.trailColor = col; c.save(); }));
            }
            case "target_esp" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 20, tr("Скорость", "Speed"), 0.5, 2.5, false,
                        () -> c.targetEspSpeed, v -> { c.targetEspSpeed = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 24, iw, 16, tr("Цвет", "Color"), ACCENT_PALETTE,
                        () -> c.targetEspColor, col -> { c.targetEspColor = col; c.save(); }));
            }
            case "jump_circle" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 20, tr("Радиус", "Radius"), 0.6, 2.2, false,
                        () -> c.jumpCircleRadius, v -> { c.jumpCircleRadius = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 24, iw, 16, tr("Цвет", "Color"), ACCENT_PALETTE,
                        () -> c.jumpCircleColor, col -> { c.jumpCircleColor = col; c.save(); }));
            }
            case "block_overlay" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 20, tr("Яркость", "Alpha"), 0.2, 1.0, false,
                        () -> c.blockOverlayAlpha, v -> { c.blockOverlayAlpha = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 24, iw, 16, tr("Цвет", "Color"), ACCENT_PALETTE,
                        () -> c.blockOverlayColor, col -> { c.blockOverlayColor = col; c.save(); }));
            }
            case "hit_particles" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 20, tr("Количество", "Count"), 4, 30, true,
                        () -> c.hitParticlesCount, v -> { c.hitParticlesCount = (int) v; c.save(); }));
            }
            case "particles" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 20, tr("Плотность", "Density"), 0, 2, true,
                        () -> c.density, v -> { c.density = (int) v; c.save(); }));
            }
            default -> {
                addDrawableChild(NeonButton.swatches(ix, iy, iw, 16, tr("Цвет", "Color"), ACCENT_PALETTE,
                        () -> c.clientAccent, col -> { c.clientAccent = col; c.save(); }));
            }
        }

        if (!"window_theme".equals(popupModule)) {
            int bindY = py + getPopupHeight() - 24;
            addBtn(ix, bindY, iw, 18, () -> {
                if (listeningBind) return tr("Нажмите клавишу...", "Press key...");
                int key = c.keybinds.getOrDefault(popupModule, -1);
                String keyName = key > 0 ? InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString() : "NONE";
                return tr("Бинд: ", "Bind: ") + keyName;
            }, null, () -> {
                listeningBind = !listeningBind;
                rebuildContent();
            });
        }
    }

    private int getPopupHeight() {
        if (popupModule == null) return 0;
        return switch (popupModule) {
            case "window_theme" -> 162;
            case "esp", "nametags" -> 114;
            case "hit_particles", "particles" -> 76;
            default -> 94;
        };
    }

    // ——— helpers ———

    private void addBtn(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier state, Runnable action) {
        addDrawableChild(new NeonButton(x, y, w, h, label, state, action));
    }

    private void toggle(int x, int y, int w, int h, String name, BooleanSupplier get, Consumer<Boolean> set, String popupKey) {
        NeonButton btn = new NeonButton(x, y, w, h, () -> name, get, () -> {
            set.accept(!get.getAsBoolean());
            ArtemConfig.get().save();
        });
        if (popupKey != null) {
            btn.withRightClick(() -> {
                listeningBind = false;
                popupModule = popupKey.equals(popupModule) ? null : popupKey;
                rebuildContent();
            });
        }
        addDrawableChild(btn);
    }

    private void miniToggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set, String popupKey) {
        NeonButton btn = new NeonButton(x, y, w, 17, () -> name, get, () -> {
            set.accept(!get.getAsBoolean());
            ArtemConfig.get().save();
        }, NeonButton.Style.ACTION);
        if (popupKey != null) {
            btn.withRightClick(() -> {
                listeningBind = false;
                popupModule = popupKey.equals(popupModule) ? null : popupKey;
                rebuildContent();
            });
        }
        addDrawableChild(btn);
    }

    private void cycle(int x, int y, int w, int h, Supplier<String> label, Runnable change) {
        addBtn(x, y, w, h, label, null, () -> {
            change.run();
            ArtemConfig.get().save();
        });
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        statusUntil = System.currentTimeMillis() + 2400;
    }

    private float getOpenProgress() {
        long elapsed = System.currentTimeMillis() - openedAtMs;
        float t = Math.min(1f, Math.max(0f, elapsed / 180f));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private int searchBoxX() { return left + W - 146; }
    private int searchBoxY() { return top + 8; }
    private int searchBoxW() { return 95; }
    private int searchBoxH() { return 16; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tx = left + 12;
        int ty = top + 11;
        String[] topTabs = {"Visuals", "HUD", "Utilities"};
        int[] tabIndices = {0, 1, 3};
        for (int i = 0; i < topTabs.length; i++) {
            int tw = textRenderer.getWidth(topTabs[i]);
            if (mouseX >= tx - 2 && mouseX <= tx + tw + 2 && mouseY >= ty - 3 && mouseY <= ty + 11) {
                tab = tabIndices[i];
                searchQuery = "";
                searchFocused = false;
                popupModule = null;
                listeningBind = false;
                rebuildContent();
                return true;
            }
            tx += tw + textRenderer.getWidth("  /  ");
        }

        int sx = searchBoxX(), sy = searchBoxY(), sw = searchBoxW(), sh = searchBoxH();
        if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
            if (profileNameField != null) {
                profileNameField.setFocused(false);
            }
            if (!searchQuery.isEmpty() && mouseX >= sx + sw - 14) {
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
        if (listeningBind) return true;
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
        if (listeningBind && popupModule != null) {
            ArtemConfig c = ArtemConfig.get();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                c.keybinds.remove(popupModule);
            } else {
                c.keybinds.put(popupModule, keyCode);
            }
            c.save();
            listeningBind = false;
            rebuildContent();
            return true;
        }
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
        NeonButton.syncTheme();
        float p = getOpenProgress();
        int dimAlpha = (int) (0x78 * p);
        ctx.fill(0, 0, width, height, dimAlpha << 24);

        // Главное окно в выбранном цвете пользователя
        NeonButton.drawAmbientGlow(ctx, left, top, W, H, 10, NeonButton.ACCENT, 3);
        NeonButton.fillGradientRounded(ctx, left, top, W, H, 10, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
        NeonButton.drawSmoothRoundedOutline(ctx, left, top, W, H, 10,
                (NeonButton.ACCENT & 0x00FFFFFF) | 0x38000000);
        ctx.fill(left + 11, top + 28, left + W - 11, top + 29, 0x12FFFFFF);

        // Левая карточка настроек / смены цвета окна
        if (popupModule != null) {
            int pw = 158;
            int ph = getPopupHeight();
            int px = Math.max(6, left - pw - 8);
            int py = top + 34;
            NeonButton.drawAmbientGlow(ctx, px, py, pw, ph, 9, NeonButton.ACCENT, 2);
            NeonButton.fillGradientRounded(ctx, px, py, pw, ph, 9, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
            NeonButton.drawSmoothRoundedOutline(ctx, px, py, pw, ph, 9,
                    (NeonButton.ACCENT & 0x00FFFFFF) | 0x44000000);
            ctx.fill(px + 9, py + 19, px + pw - 9, py + 20, 0x12FFFFFF);
        }

        // Нижний док
        int dockW = 166;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 8;
        NeonButton.fillGradientRounded(ctx, dockLeft, dockTop, dockW, 26, 9, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
        NeonButton.drawSmoothRoundedOutline(ctx, dockLeft, dockTop, dockW, 26, 9, 0x20FFFFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float p = getOpenProgress();
        float scale = 0.92f + 0.08f * p;
        float slideY = (1f - p) * 10f;

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
            int pw = 158;
            int px = Math.max(6, left - pw - 8);
            int py = top + 34;
            String title = "window_theme".equals(popupModule)
                    ? tr("Цвет и стиль окна", "Window Theme")
                    : popupModule.toUpperCase(Locale.ROOT).replace('_', ' ');
            ctx.drawText(textRenderer, title, px + 9, py + 7, 0xFFF2F2FA, false);
        }

        if (tab == 2 && searchQuery.isBlank()) {
            int px = left + W / 2;
            int py = top + 162;
            NeonButton.fillSmoothRounded(ctx, px - 22, py - 22, 44, 44, 6, 0xFF141420);
            NeonButton.drawSmoothRoundedOutline(ctx, px - 22, py - 22, 44, 44, 6, 0x28FFFFFF);
            CrosshairRenderer.renderPreview(ctx, px, py, ArtemConfig.get());
        }

        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMessage, left + W / 2, top + H - 15, NeonButton.ACCENT_BRIGHT);
        }

        ctx.getMatrices().pop();
    }

    private void drawTopCategories(DrawContext ctx) {
        int tx = left + 12;
        int ty = top + 12;
        String[] topTabs = {"Visuals", "HUD", "Utilities"};
        int[] tabIndices = {0, 1, 3};

        for (int i = 0; i < topTabs.length; i++) {
            boolean active = searchQuery.isBlank() && tab == tabIndices[i];
            int col = active ? 0xFFF4F4FC : 0xFF56566C;
            ctx.drawText(textRenderer, topTabs[i], tx, ty, col, false);
            tx += textRenderer.getWidth(topTabs[i]);
            if (i < topTabs.length - 1) {
                ctx.drawText(textRenderer, "  /  ", tx, ty, 0xFF3A3A4E, false);
                tx += textRenderer.getWidth("  /  ");
            }
        }
    }

    private void drawSearchBar(DrawContext ctx, int mouseX, int mouseY) {
        int sx = searchBoxX(), sy = searchBoxY(), sw = searchBoxW(), sh = searchBoxH();
        boolean hover = mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh;

        NeonButton.fillSmoothRounded(ctx, sx, sy, sw, sh, 5, (searchFocused || hover) ? 0xFF181826 : 0xFF12121C);
        NeonButton.drawSmoothRoundedOutline(ctx, sx, sy, sw, sh, 5,
                searchFocused ? NeonButton.ACCENT_BRIGHT : (hover ? 0x28FFFFFF : 0x12FFFFFF));

        int lx = sx + 7;
        int ly = sy + 7;
        NeonButton.drawCircle(ctx, lx, ly, 2, 0xFF72728A);
        NeonButton.drawCircle(ctx, lx, ly, 1, 0xFF141420);
        ctx.fill(lx + 2, ly + 2, lx + 4, ly + 4, 0xFF72728A);

        int textX = sx + 14;
        int textY = sy + 4;
        if (searchQuery.isEmpty() && !searchFocused) {
            ctx.drawText(textRenderer, tr("Поиск", "Search"), textX, textY, 0xFF56566C, false);
        } else {
            String visible = textRenderer.trimToWidth(searchQuery, sw - 26);
            ctx.drawText(textRenderer, visible, textX, textY, 0xFFF2F2FA, false);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = textX + textRenderer.getWidth(visible);
                ctx.fill(cx, textY - 1, cx + 1, textY + 8, NeonButton.ACCENT_BRIGHT);
            }
        }

        if (!searchQuery.isEmpty()) {
            ctx.drawText(textRenderer, "×", sx + sw - 10, textY, 0xFF8E8EA8, false);
        }
    }

    private void drawBrandHeader(DrawContext ctx) {
        String brand = "ArtemVisuals";
        int textW = textRenderer.getWidth(brand);
        int iconW = 20;
        int totalW = iconW + 4 + textW;
        int startX = left + (W - totalW) / 2;
        int y = top - 22;

        NeonButton.drawCircle(ctx, startX + 7, y + 6, 8, (NeonButton.ACCENT & 0x00FFFFFF) | 0x38000000);

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

    /**
     * Интерактивный HUD Editor.
     */
    public static class HudEditorScreen extends Screen {
        private final Screen returnTo;

        public HudEditorScreen(Screen returnTo) {
            super(Text.literal("HUD Editor"));
            this.returnTo = returnTo;
        }

        @Override
        protected void init() {
            ArtemConfig.get().hudEditMode = true;
            int bw = 76;
            int gap = 6;
            int total = bw * 4 + gap * 3;
            int sx = (width - total) / 2;
            int y = height - 28;

            addDrawableChild(new NeonButton(sx, y, bw, 20, () -> "PulseVisual", null,
                    HudManager::applyPulsePreset));
            addDrawableChild(new NeonButton(sx + (bw + gap), y, bw, 20, () -> "FunTime PvP", null,
                    () -> HudManager.applyFunTimePreset(MinecraftClient.getInstance())));
            addDrawableChild(new NeonButton(sx + (bw + gap) * 2, y, bw, 20, () -> "Сброс мест", null,
                    () -> {
                        ArtemConfig.get().hudPositions.clear();
                        ArtemConfig.get().save();
                    }));
            addDrawableChild(new NeonButton(sx + (bw + gap) * 3, y, bw, 20, () -> "Готово ✓", null,
                    this::close));
        }

        @Override
        public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(0, 0, width, height, 0x4408080E);
            ctx.fill(width / 2, 0, width / 2 + 1, height, 0x227B68EE);
            ctx.fill(0, height / 2, width, height / 2 + 1, 0x227B68EE);

            int pw = 290;
            int px = (width - pw) / 2;
            NeonButton.fillSmoothRounded(ctx, px, 24, pw, 18, 6, 0xE610101A);
            NeonButton.drawSmoothRoundedOutline(ctx, px, 24, pw, 18, 6, 0x447B68EE);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "ЛКМ — двигать элемент  |  Колёсико мыши — изменить размер",
                    width / 2, 29, 0xFFE6E6F4);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            if (button == 0) {
                return HudManager.mouseClicked(mouseX, mouseY);
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (button == 0) {
                HudManager.mouseDragged(mouseX, mouseY);
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            HudManager.mouseReleased();
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (HudManager.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        public void close() {
            ArtemConfig.get().hudEditMode = false;
            ArtemConfig.get().save();
            MinecraftClient.getInstance().setScreen(returnTo);
        }
    }
}
