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
 * ARTEM VISUALS 3.0 | PulseVisual Edition — главное меню, поиск, настройки по ПКМ, бинды и HUD Editor.
 */
public class ArtemScreen extends Screen {
    private static final int W = 368;
    private static final int H = 216;
    private static final String[] TABS_RU = {"Visual", "HUD", "Прицел", "FunTime", "Настройки"};
    private static final String[] TABS_EN = {"Visual", "HUD", "Crosshair", "FunTime", "Settings"};
    private static final String[] TIME_NAMES = {"Сервер", "День", "Закат", "Ночь"};
    private static final String[] ESP_STYLES = {"Бокс", "Углы", "Двойной"};
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Плюс"};

    private static final int[] PALETTE = {0xFF6C5CE7, 0xFF00F5FF, 0xFFE056FD, 0xFFFFB703, 0xFF2EC4B6, 0xFFFF4D6D};

    private final Screen parent;
    private int tab = 0;
    private int left, top;

    private String searchQuery = "";
    private boolean searchFocused = false;

    private TextFieldWidget profileNameField;
    private String statusMessage = "";
    private long statusUntil = 0;
    private long openedAtMs = 0;

    // Всплывающее окно тонких настроек слева по ПКМ + режим ожидания бинда клавиши
    private String popupModule = null;
    private boolean listeningBind = false;

    public ArtemScreen(Screen parent) {
        super(Text.literal("ARTEM VISUALS 3.0"));
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
        top = (height - H) / 2 - 4;
        rebuildContent();
    }

    private void rebuildContent() {
        clearChildren();
        profileNameField = null;

        ArtemConfig c = ArtemConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        int y = top + 27;
        int cw = (W - 24) / 2;
        int x0 = left + 9;
        int x1 = x0 + cw + 6;
        int step = 20;

        if (!searchQuery.isBlank()) {
            buildSearchResults(c, mc, x0, x1, y, cw, step);
        } else {
            switch (tab) {
                case 0 -> buildVisuals(c, mc, x0, x1, y, cw, step);
                case 1 -> buildHud(c, x0, x1, y, cw, step);
                case 2 -> buildCrosshair(c, x0, x1, y, cw, step);
                case 3 -> buildFunTimeAndPerf(c, mc, x0, x1, y, cw, step);
                case 4 -> buildSettingsAndConfigs(c, x0, x1, y, cw, step);
            }
        }

        if (popupModule != null) {
            buildPopupControls(c);
        }

        // Нижний стеклянный док
        int dockW = 166;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 7;
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
        // Visual (16 модулей)
        list.add(new ModuleEntry("esp", "ESP", "esp боксы игроки", () -> c.esp, v -> c.esp = v, "esp"));
        list.add(new ModuleEntry("player_hl", "Player Highlight", "player highlight подсветка игроков", () -> c.playerHighlight, v -> c.playerHighlight = v, "player_hl"));
        list.add(new ModuleEntry("tracers", "Tracers", "tracers линии трейсеры", () -> c.tracers, v -> c.tracers = v, "tracers"));
        list.add(new ModuleEntry("chams", "Chams", "chams чамсы заливка", () -> c.chams, v -> c.chams = v, "chams"));
        list.add(new ModuleEntry("nametags", "NameTags", "nametags ники теги хп", () -> c.nameTags, v -> c.nameTags = v, "nametags"));
        list.add(new ModuleEntry("item_esp", "Item ESP", "item esp предметы лут", () -> c.itemEsp, v -> c.itemEsp = v, "item_esp"));
        list.add(new ModuleEntry("mob_esp", "Mob ESP", "mob esp мобы животные", () -> c.mobEsp, v -> c.mobEsp = v, "mob_esp"));
        list.add(new ModuleEntry("entity_hl", "Entity Highlight", "entity highlight сущности", () -> c.entityHighlight, v -> c.entityHighlight = v, "entity_hl"));
        list.add(new ModuleEntry("hit_particles", "HitParticles", "hit particles удары искры", () -> c.hitParticles, v -> c.hitParticles = v, "hit_particles"));
        list.add(new ModuleEntry("dmg_ind", "Damage Indicator", "damage indicator урон цифры", () -> c.damageIndicator, v -> c.damageIndicator = v, "dmg_ind"));
        list.add(new ModuleEntry("nimb", "Nimb", "nimb нимб кольцо голова", () -> c.nimb, v -> c.nimb = v, "nimb"));
        list.add(new ModuleEntry("trails", "Trails", "trails трейлы шлейф", () -> c.trails, v -> c.trails = v, "trails"));
        list.add(new ModuleEntry("target_esp", "Target ESP", "target esp цель кольцо", () -> c.targetEsp, v -> c.targetEsp = v, "target_esp"));
        list.add(new ModuleEntry("jump_circle", "Jump Circle", "jump circle круг прыжок", () -> c.jumpCircle, v -> c.jumpCircle = v, "jump_circle"));
        list.add(new ModuleEntry("block_overlay", "Block Overlay", "block overlay обводка блока", () -> c.blockOverlay, v -> c.blockOverlay = v, "block_overlay"));
        list.add(new ModuleEntry("world_cust", "World Customizer", "world customizer мир цвет rgb время", () -> c.worldCustomizer, v -> c.worldCustomizer = v, "world_cust"));

        // HUD
        list.add(new ModuleEntry("topbar", "Server Watermark", "server info topbar фантайм", () -> c.showServer, v -> c.showServer = v, null));
        list.add(new ModuleEntry("target_hud", "Target HUD", "target hud таргет скин", () -> c.showTargetHud, v -> c.showTargetHud = v, null));
        list.add(new ModuleEntry("arraylist", "ArrayList", "arraylist активные модули", () -> c.showArrayList, v -> c.showArrayList = v, null));
        list.add(new ModuleEntry("radar", "Radar", "radar радар карта", () -> c.showRadar, v -> c.showRadar = v, null));
        list.add(new ModuleEntry("minimap", "MiniMap", "minimap миникарта", () -> c.showMinimap, v -> c.showMinimap = v, null));
        list.add(new ModuleEntry("potions", "Potions HUD", "potions зелья эффекты", () -> c.showPotions, v -> c.showPotions = v, null));
        list.add(new ModuleEntry("armor", "Armor HUD", "armor броня прочность", () -> c.showArmor, v -> c.showArmor = v, null));
        list.add(new ModuleEntry("wasd", "WASD Display", "wasd клавиши", () -> c.showWasd, v -> c.showWasd = v, null));
        list.add(new ModuleEntry("keystrokes", "Keystrokes Full", "keystrokes space lmb rmb", () -> c.showKeystrokes, v -> c.showKeystrokes = v, null));
        list.add(new ModuleEntry("fps", "FPS Counter", "fps фпс", () -> c.showFps, v -> c.showFps = v, null));
        list.add(new ModuleEntry("ping", "Ping Counter", "ping пинг", () -> c.showPing, v -> c.showPing = v, null));
        list.add(new ModuleEntry("xyz", "XYZ Coordinates", "xyz координаты", () -> c.showXyz, v -> c.showXyz = v, null));
        list.add(new ModuleEntry("cps", "CPS Counter (L/R)", "cps кпс клики", () -> c.showCps, v -> c.showCps = v, null));
        list.add(new ModuleEntry("speed", "Player Speed (BPS)", "speed скорость", () -> c.showSpeed, v -> c.showSpeed = v, null));
        list.add(new ModuleEntry("facing", "Look Direction", "facing направление взгляда", () -> c.showDirection, v -> c.showDirection = v, null));
        list.add(new ModuleEntry("nick", "Player Nickname", "nickname никнейм", () -> c.showNickname, v -> c.showNickname = v, null));
        list.add(new ModuleEntry("playtime", "Playtime Clock", "playtime время игры", () -> c.showPlaytime, v -> c.showPlaytime = v, null));
        list.add(new ModuleEntry("health_hud", "Health Indicator", "health здоровье хп", () -> c.showHealthHud, v -> c.showHealthHud = v, null));
        list.add(new ModuleEntry("hunger_hud", "Hunger Indicator", "hunger еда голод", () -> c.showHungerHud, v -> c.showHungerHud = v, null));
        list.add(new ModuleEntry("crosshair", "Custom Crosshair", "crosshair прицел", () -> c.customCrosshair, v -> c.customCrosshair = v, null));
        list.add(new ModuleEntry("fullbright", "Fullbright", "fullbright яркость свет", () -> c.softLighting, v -> {
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
            if (idx >= 16) break;
            int colX = (idx % 2 == 0) ? x0 : x1;
            int rowY = y + (idx / 2) * step;
            toggle(colX, rowY, cw, m.name(), m.get(), m.set(), m.popupKey());
            idx++;
        }
    }

    private void buildVisuals(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "ESP", () -> c.esp, v -> c.esp = v, "esp");
        toggle(x1, y, cw, "Player Highlight", () -> c.playerHighlight, v -> c.playerHighlight = v, "player_hl");

        toggle(x0, y + step, cw, "Tracers", () -> c.tracers, v -> c.tracers = v, "tracers");
        toggle(x1, y + step, cw, "Chams", () -> c.chams, v -> c.chams = v, "chams");

        toggle(x0, y + step * 2, cw, "NameTags", () -> c.nameTags, v -> c.nameTags = v, "nametags");
        toggle(x1, y + step * 2, cw, "Item ESP", () -> c.itemEsp, v -> c.itemEsp = v, "item_esp");

        toggle(x0, y + step * 3, cw, "Mob ESP", () -> c.mobEsp, v -> c.mobEsp = v, "mob_esp");
        toggle(x1, y + step * 3, cw, "Entity Highlight", () -> c.entityHighlight, v -> c.entityHighlight = v, "entity_hl");

        toggle(x0, y + step * 4, cw, "HitParticles", () -> c.hitParticles, v -> c.hitParticles = v, "hit_particles");
        toggle(x1, y + step * 4, cw, "Damage Indicator", () -> c.damageIndicator, v -> c.damageIndicator = v, "dmg_ind");

        toggle(x0, y + step * 5, cw, "Jump Circle", () -> c.jumpCircle, v -> c.jumpCircle = v, "jump_circle");
        toggle(x1, y + step * 5, cw, "Target ESP", () -> c.targetEsp, v -> c.targetEsp = v, "target_esp");

        toggle(x0, y + step * 6, cw, "Trails", () -> c.trails, v -> c.trails = v, "trails");
        toggle(x1, y + step * 6, cw, "Nimb", () -> c.nimb, v -> c.nimb = v, "nimb");

        toggle(x0, y + step * 7, cw, "Block Overlay", () -> c.blockOverlay, v -> c.blockOverlay = v, "block_overlay");
        toggle(x1, y + step * 7, cw, "World Customizer", () -> c.worldCustomizer, v -> c.worldCustomizer = v, "world_cust");
    }

    private void buildHud(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Server Info", () -> c.showServer, v -> c.showServer = v, null);
        toggle(x1, y, cw, "Target HUD", () -> c.showTargetHud, v -> c.showTargetHud = v, null);

        toggle(x0, y + step, cw, "ArrayList", () -> c.showArrayList, v -> c.showArrayList = v, null);
        toggle(x1, y + step, cw, "Potions HUD", () -> c.showPotions, v -> c.showPotions = v, null);

        toggle(x0, y + step * 2, cw, "Radar", () -> c.showRadar, v -> c.showRadar = v, null);
        toggle(x1, y + step * 2, cw, "MiniMap", () -> c.showMinimap, v -> c.showMinimap = v, null);

        toggle(x0, y + step * 3, cw, "Armor HUD", () -> c.showArmor, v -> c.showArmor = v, null);
        toggle(x1, y + step * 3, cw, "Health & Hunger", () -> c.showHealthHud, v -> {
            c.showHealthHud = v;
            c.showHungerHud = v;
        }, null);

        toggle(x0, y + step * 4, cw, "WASD Display", () -> c.showWasd, v -> c.showWasd = v, null);
        toggle(x1, y + step * 4, cw, "Keystrokes Full", () -> c.showKeystrokes, v -> c.showKeystrokes = v, null);

        toggle(x0, y + step * 5, cw, "FPS & Ping", () -> c.showFps, v -> {
            c.showFps = v;
            c.showPing = v;
        }, null);
        toggle(x1, y + step * 5, cw, "XYZ & CPS (L/R)", () -> c.showXyz, v -> {
            c.showXyz = v;
            c.showCps = v;
        }, null);

        toggle(x0, y + step * 6, cw, "Speed & Facing", () -> c.showSpeed, v -> {
            c.showSpeed = v;
            c.showDirection = v;
        }, null);
        toggle(x1, y + step * 6, cw, "Nick & Playtime", () -> c.showNickname, v -> {
            c.showNickname = v;
            c.showPlaytime = v;
        }, null);

        addBtn(x0, y + step * 7 + 1, cw * 2 + 6, 18,
                () -> tr("✦ Открыть HUD Editor (Перетаскивание + Размер + Пресеты)", "✦ Open HUD Editor (Drag + Resize + Presets)"),
                null, () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen(this)));
    }

    private void buildCrosshair(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, "Custom Crosshair", () -> c.customCrosshair, v -> c.customCrosshair = v, null);
        cycle(x1, y, cw, () -> tr("Стиль: ", "Style: ") + STYLES[Math.min(c.crosshairStyle, 3)], () -> c.crosshairStyle = (c.crosshairStyle + 1) % 4);

        addDrawableChild(NeonButton.slider(x0, y + step, cw, 18, tr("Размер", "Size"), 3, 12, true,
                () -> c.crosshairSize, v -> { c.crosshairSize = (int) v; c.save(); }));
        addDrawableChild(NeonButton.slider(x1, y + step, cw, 18, tr("Зазор", "Gap"), 0, 8, true,
                () -> c.crosshairGap, v -> { c.crosshairGap = (int) v; c.save(); }));
        addDrawableChild(NeonButton.slider(x0, y + step * 2, cw, 18, tr("Толщина", "Thickness"), 1, 3, true,
                () -> c.crosshairThickness, v -> { c.crosshairThickness = (int) v; c.save(); }));

        addDrawableChild(NeonButton.swatches(x1, y + step * 2 + 2, cw, 16, tr("Цвет", "Color"), PALETTE,
                () -> c.crosshairColor, col -> { c.crosshairColor = col; c.save(); }));

        toggle(x0, y + step * 3, cw, tr("Обводка", "Outline"), () -> c.crosshairOutline, v -> c.crosshairOutline = v, null);
        toggle(x1, y + step * 3, cw, tr("Динамика", "Dynamic"), () -> c.crosshairDynamic, v -> c.crosshairDynamic = v, null);
    }

    private void buildFunTimeAndPerf(ArtemConfig c, MinecraftClient mc, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw * 2 + 6, tr("Авто-определение сервера FunTime", "Auto-detect FunTime Server"),
                () -> c.autoFunTime, v -> c.autoFunTime = v, null);

        addBtn(x0, y + step + 2, cw * 2 + 6, 20,
                () -> tr("⚔ Применить профиль FunTime PvP (HUD + Visual + FPS)", "⚔ Apply FunTime PvP Profile (HUD + Visual + FPS)"),
                null, () -> {
                    HudManager.applyFunTimePreset(mc);
                    setStatus(tr("Профиль FunTime активирован", "FunTime profile activated"));
                });

        addBtn(x0, y + step * 2 + 4, cw * 2 + 6, 20,
                () -> tr("💾 Сохранить текущие настройки в профиль FunTime", "💾 Save Current Settings to FunTime Profile"),
                null, () -> {
                    ProfileManager.saveProfile("FunTime");
                    setStatus(tr("Сохранено в профиль FunTime", "Saved to FunTime profile"));
                });

        toggle(x0, y + step * 3 + 6, cw, "Fullbright", () -> c.softLighting, v -> {
            c.softLighting = v;
            Optimizer.applyLighting(mc);
        }, null);
        toggle(x1, y + step * 3 + 6, cw, "Render Tweaks", () -> c.renderTweaks, v -> c.renderTweaks = v, null);

        addBtn(x0, y + step * 4 + 8, cw, 19,
                () -> tr("⚡ Оптимизация FPS", "⚡ FPS Boost"),
                null, () -> {
                    Optimizer.applyPerformancePreset(mc);
                    setStatus(tr("Оптимизация FPS применена", "FPS boost applied"));
                });
        addBtn(x1, y + step * 4 + 8, cw, 19,
                () -> Optimizer.hasBackup() ? tr("↺ Вернуть настройки", "↺ Restore Video") : tr("Бэкап пуст", "No Backup"),
                null, () -> {
                    if (Optimizer.hasBackup()) {
                        Optimizer.restoreSettings(mc);
                        setStatus(tr("Настройки видео восстановлены", "Video settings restored"));
                    }
                });
    }

    private void buildSettingsAndConfigs(ArtemConfig c, int x0, int x1, int y, int cw, int step) {
        toggle(x0, y, cw, tr("RGB Перелив", "RGB Mode"), () -> c.rgbMode, v -> c.rgbMode = v, null);
        addDrawableChild(NeonButton.swatches(x1, y + 2, cw, 16, tr("Акцент", "Accent"), PALETTE,
                () -> c.clientAccent, col -> {
                    c.clientAccent = col;
                    c.rgbMode = false;
                    c.save();
                }));

        addDrawableChild(NeonButton.slider(x0, y + step, cw, 18, tr("Скорость RGB", "RGB Speed"), 0.3, 3.0, false,
                () -> c.rgbSpeed, v -> { c.rgbSpeed = (float) v; c.save(); }));
        cycle(x1, y + step, cw, () -> tr("Язык: ", "Language: ") + c.language, () -> {
            c.language = isEn() ? "RU" : "EN";
            rebuildContent();
        });

        // Импорт / Экспорт через буфер обмена + Сброс
        addBtn(x0, y + step * 2 + 2, cw, 18, () -> tr("📋 Экспорт в буфер", "📋 Export Clipboard"), null, () -> {
            MinecraftClient.getInstance().keyboard.setClipboard(c.exportBase64());
            setStatus(tr("Конфиг скопирован в буфер обмена", "Config copied to clipboard"));
        });
        addBtn(x1, y + step * 2 + 2, cw, 18, () -> tr("📥 Импорт из буфера", "📥 Import Clipboard"), null, () -> {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            if (ArtemConfig.get().importBase64(clip)) {
                rebuildContent();
                setStatus(tr("Конфиг успешно импортирован!", "Config imported!"));
            } else {
                setStatus(tr("Неверный формат конфига в буфере", "Invalid config in clipboard"));
            }
        });

        // Создание и загрузка конфигураций
        profileNameField = new TextFieldWidget(textRenderer, x0, y + step * 3 + 4, cw + 20, 16, Text.literal("Config"));
        profileNameField.setMaxLength(24);
        profileNameField.setPlaceholder(Text.literal(tr("Имя конфига...", "Config name...")));
        addDrawableChild(profileNameField);

        addBtn(x0 + cw + 24, y + step * 3 + 4, cw - 18, 16, () -> tr("Сохранить", "Save"), null, () -> {
            String name = profileNameField.getText();
            if (ProfileManager.saveProfile(name)) {
                setStatus(tr("Сохранено: ", "Saved: ") + name);
                rebuildContent();
            }
        });

        List<String> profiles = ProfileManager.listProfiles();
        int py = y + step * 4 + 4;
        for (int i = 0; i < Math.min(2, profiles.size()); i++) {
            final String n = profiles.get(i);
            addBtn(x0, py, cw * 2 - 38, 17, () -> "▶ " + n, null, () -> {
                if (ProfileManager.loadProfile(n)) {
                    setStatus(tr("Загружен: ", "Loaded: ") + n);
                    rebuildContent();
                }
            });
            addBtn(x0 + cw * 2 - 34, py, 40, 17, () -> tr("Удал.", "Del"), null, () -> {
                ProfileManager.deleteProfile(n);
                rebuildContent();
            });
            py += 19;
        }

        addBtn(x0, top + H - 23, cw * 2 + 6, 17, () -> tr("↺ Reset Settings (Сбросить всё)", "↺ Reset Settings"), null, () -> {
            c.resetDefaults();
            rebuildContent();
            setStatus(tr("Настройки сброшены", "Settings reset"));
        });
    }

    private void buildPopupControls(ArtemConfig c) {
        int pw = 148;
        int px = Math.max(6, left - pw - 8);
        int py = top + 34;
        int ix = px + 8;
        int iw = pw - 16;
        int iy = py + 21;

        switch (popupModule) {
            case "esp" -> {
                cycle(ix, iy, iw, () -> tr("Стиль: ", "Style: ") + ESP_STYLES[c.espStyle % 3],
                        () -> c.espStyle = (c.espStyle + 1) % 3);
                addDrawableChild(NeonButton.slider(ix, iy + 21, iw, 18, tr("Прозрачность", "Alpha"), 0.2, 1.0, false,
                        () -> c.espAlpha, v -> { c.espAlpha = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 43, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.espColor, col -> { c.espColor = col; c.save(); }));
                toggle(ix, iy + 61, iw, tr("Анимация пульса", "Pulse Anim"), () -> c.espPulse, v -> c.espPulse = v, null);
            }
            case "player_hl" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Яркость", "Alpha"), 0.2, 1.0, false,
                        () -> c.playerHighlightAlpha, v -> { c.playerHighlightAlpha = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 22, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.playerHighlightColor, col -> { c.playerHighlightColor = col; c.save(); }));
            }
            case "tracers" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Прозрачность", "Alpha"), 0.2, 1.0, false,
                        () -> c.tracersAlpha, v -> { c.tracersAlpha = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 22, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.tracersColor, col -> { c.tracersColor = col; c.save(); }));
            }
            case "chams" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Плотность", "Alpha"), 0.15, 0.9, false,
                        () -> c.chamsAlpha, v -> { c.chamsAlpha = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 22, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.chamsColor, col -> { c.chamsColor = col; c.save(); }));
            }
            case "nametags" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Размер", "Scale"), 0.65, 1.25, false,
                        () -> c.nameTagsScale, v -> { c.nameTagsScale = (float) v; c.save(); }));
                toggle(ix, iy + 21, iw, tr("Показывать HP", "Show HP"), () -> c.nameTagsHealth, v -> c.nameTagsHealth = v, null);
                toggle(ix, iy + 41, iw, tr("Дистанция", "Distance"), () -> c.nameTagsDistance, v -> c.nameTagsDistance = v, null);
            }
            case "item_esp" -> {
                addDrawableChild(NeonButton.swatches(ix, iy, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.itemEspColor, col -> { c.itemEspColor = col; c.save(); }));
                toggle(ix, iy + 20, iw, tr("Названия лута", "Item Labels"), () -> c.itemEspLabels, v -> c.itemEspLabels = v, null);
            }
            case "mob_esp" -> {
                addDrawableChild(NeonButton.swatches(ix, iy, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.mobEspColor, col -> { c.mobEspColor = col; c.save(); }));
            }
            case "entity_hl" -> {
                addDrawableChild(NeonButton.swatches(ix, iy, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.entityHighlightColor, col -> { c.entityHighlightColor = col; c.save(); }));
            }
            case "hit_particles" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Количество", "Count"), 4, 30, true,
                        () -> c.hitParticlesCount, v -> { c.hitParticlesCount = (int) v; c.save(); }));
            }
            case "dmg_ind" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Размер цифр", "Scale"), 0.7, 1.4, false,
                        () -> c.damageIndicatorSize, v -> { c.damageIndicatorSize = (float) v; c.save(); }));
            }
            case "nimb" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Радиус", "Radius"), 0.25, 0.65, false,
                        () -> c.nimbSize, v -> { c.nimbSize = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 22, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.nimbColor, col -> { c.nimbColor = col; c.save(); }));
            }
            case "trails" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Длина", "Length"), 10, 48, true,
                        () -> c.trailLength, v -> { c.trailLength = (int) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 22, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.trailColor, col -> { c.trailColor = col; c.save(); }));
            }
            case "target_esp" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Скорость", "Speed"), 0.5, 2.5, false,
                        () -> c.targetEspSpeed, v -> { c.targetEspSpeed = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 22, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.targetEspColor, col -> { c.targetEspColor = col; c.save(); }));
            }
            case "jump_circle" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Радиус", "Radius"), 0.6, 2.2, false,
                        () -> c.jumpCircleRadius, v -> { c.jumpCircleRadius = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 22, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.jumpCircleColor, col -> { c.jumpCircleColor = col; c.save(); }));
            }
            case "block_overlay" -> {
                addDrawableChild(NeonButton.slider(ix, iy, iw, 18, tr("Яркость", "Alpha"), 0.2, 1.0, false,
                        () -> c.blockOverlayAlpha, v -> { c.blockOverlayAlpha = (float) v; c.save(); }));
                addDrawableChild(NeonButton.swatches(ix, iy + 22, iw, 15, tr("Цвет", "Color"), PALETTE,
                        () -> c.blockOverlayColor, col -> { c.blockOverlayColor = col; c.save(); }));
            }
            case "world_cust" -> {
                addDrawableChild(NeonButton.swatches(ix, iy, iw, 15, tr("Цвет клиента", "Accent"), PALETTE,
                        () -> c.clientAccent, col -> {
                            c.clientAccent = col;
                            c.rgbMode = false;
                            c.save();
                        }));
                cycle(ix, iy + 20, iw, () -> tr("Время: ", "Time: ") + TIME_NAMES[c.worldTimeMode % 4],
                        () -> c.worldTimeMode = (c.worldTimeMode + 1) % 4);
                toggle(ix, iy + 41, iw, "RGB Mode", () -> c.rgbMode, v -> c.rgbMode = v, null);
            }
        }

        // Кнопка бинда клавиши внизу каждой карточки поднастроек!
        int bindY = py + getPopupHeight() - 22;
        addBtn(ix, bindY, iw, 16, () -> {
            if (listeningBind) return tr("Нажмите клавишу...", "Press any key...");
            int key = c.keybinds.getOrDefault(popupModule, -1);
            String keyName = key > 0 ? InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString() : "NONE";
            return tr("Бинд: ", "Bind: ") + keyName;
        }, null, () -> {
            listeningBind = !listeningBind;
            rebuildContent();
        });
    }

    private int getPopupHeight() {
        if (popupModule == null) return 0;
        return switch (popupModule) {
            case "esp" -> 124;
            case "nametags", "world_cust" -> 104;
            case "hit_particles", "dmg_ind", "mob_esp", "entity_hl" -> 66;
            default -> 86;
        };
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
                listeningBind = false;
                popupModule = popupKey.equals(popupModule) ? null : popupKey;
                rebuildContent();
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
        statusUntil = System.currentTimeMillis() + 2400;
    }

    private float getOpenProgress() {
        long elapsed = System.currentTimeMillis() - openedAtMs;
        float t = Math.min(1f, Math.max(0f, elapsed / 180f));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private int searchBoxX() { return left + W - 104; }
    private int searchBoxY() { return top + 6; }
    private int searchBoxW() { return 94; }
    private int searchBoxH() { return 14; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tx = left + 10;
        int ty = top + 8;
        String[] topTabs = {"Visual", "HUD", "FunTime"};
        int[] tabIndices = {0, 1, 3};
        for (int i = 0; i < topTabs.length; i++) {
            int tw = Math.round(textRenderer.getWidth(topTabs[i]) * 0.86f);
            if (mouseX >= tx - 2 && mouseX <= tx + tw + 2 && mouseY >= ty - 2 && mouseY <= ty + 10) {
                tab = tabIndices[i];
                searchQuery = "";
                searchFocused = false;
                popupModule = null;
                listeningBind = false;
                rebuildContent();
                return true;
            }
            tx += tw + Math.round(textRenderer.getWidth("  /  ") * 0.86f);
        }

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
        int dimAlpha = (int) (0x84 * p);
        ctx.fill(0, 0, width, height, dimAlpha << 24);

        NeonButton.drawAmbientGlow(ctx, left, top, W, H, 8, NeonButton.ACCENT, 2);
        NeonButton.fillGradientRounded(ctx, left, top, W, H, 8, 0xF50E0E16, 0xF50A0A10);
        NeonButton.drawSmoothRoundedOutline(ctx, left, top, W, H, 8, 0x22FFFFFF);
        ctx.fill(left + 9, top + 22, left + W - 9, top + 23, 0x12FFFFFF);

        if (popupModule != null) {
            int pw = 148;
            int ph = getPopupHeight();
            int px = Math.max(6, left - pw - 8);
            int py = top + 34;
            NeonButton.drawAmbientGlow(ctx, px, py, pw, ph, 7, NeonButton.ACCENT, 2);
            NeonButton.fillGradientRounded(ctx, px, py, pw, ph, 7, 0xF510101A, 0xF50B0B12);
            NeonButton.drawSmoothRoundedOutline(ctx, px, py, pw, ph, 7, 0x33FFFFFF);
            ctx.fill(px + 8, py + 17, px + pw - 8, py + 18, 0x12FFFFFF);
        }

        int dockW = 166;
        int dockLeft = (width - dockW) / 2;
        int dockTop = top + H + 7;
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
            int pw = 148;
            int px = Math.max(6, left - pw - 8);
            int py = top + 34;
            NeonButton.drawSmallText(ctx, textRenderer, popupModule.toUpperCase(Locale.ROOT).replace('_', ' '),
                    px + 8, py + 6, 0xFFE6E6F4, 0.82f);
        }

        if (tab == 2 && searchQuery.isBlank()) {
            int px = left + W / 2;
            int py = top + 148;
            NeonButton.fillSmoothRounded(ctx, px - 24, py - 24, 48, 48, 6, 0xFF141420);
            NeonButton.drawSmoothRoundedOutline(ctx, px - 24, py - 24, 48, 48, 6, 0x28FFFFFF);
            CrosshairRenderer.renderPreview(ctx, px, py, ArtemConfig.get());
        }

        if (tab == 0 && searchQuery.isBlank() && popupModule == null) {
            NeonButton.drawSmallText(ctx, textRenderer,
                    tr("ПКМ по любому модулю — цвет, размер, анимация и бинд клавиши", "Right-click any module — color, size, animation & keybind"),
                    left + 44, top + H - 11, 0xFF4E4E64, 0.78f);
        }

        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMessage, left + W / 2, top + H - 14, NeonButton.ACCENT_BRIGHT);
        }

        ctx.getMatrices().pop();
    }

    private void drawTopCategories(DrawContext ctx) {
        int tx = left + 10;
        int ty = top + 8;
        String[] topTabs = {"Visual", "HUD", "FunTime"};
        int[] tabIndices = {0, 1, 3};

        for (int i = 0; i < topTabs.length; i++) {
            boolean active = searchQuery.isBlank() && tab == tabIndices[i];
            int col = active ? 0xFFF4F4FC : 0xFF56566C;
            NeonButton.drawSmallText(ctx, textRenderer, topTabs[i], tx, ty, col, 0.86f);
            tx += Math.round(textRenderer.getWidth(topTabs[i]) * 0.86f);
            if (i < topTabs.length - 1) {
                NeonButton.drawSmallText(ctx, textRenderer, "  /  ", tx, ty, 0xFF3A3A4E, 0.86f);
                tx += Math.round(textRenderer.getWidth("  /  ") * 0.86f);
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
            NeonButton.drawSmallText(ctx, textRenderer, tr("Поиск", "Search"), textX, textY, 0xFF56566C, 0.84f);
        } else {
            String visible = textRenderer.trimToWidth(searchQuery, sw - 24);
            NeonButton.drawSmallText(ctx, textRenderer, visible, textX, textY, 0xFFF2F2FA, 0.84f);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = textX + Math.round(textRenderer.getWidth(visible) * 0.84f);
                ctx.fill(cx, textY - 1, cx + 1, textY + 7, NeonButton.ACCENT_BRIGHT);
            }
        }

        if (!searchQuery.isEmpty()) {
            NeonButton.drawSmallText(ctx, textRenderer, "×", sx + sw - 9, textY, 0xFF8E8EA8, 0.84f);
        }
    }

    private void drawBrandHeader(DrawContext ctx) {
        String brand = "ARTEM VISUALS";
        String ver = "3.0";
        int textW = textRenderer.getWidth(brand);
        int verW = textRenderer.getWidth(ver) + 6;
        int iconW = 18;
        int totalW = iconW + 4 + textW + 5 + verW;
        int startX = left + (W - totalW) / 2;
        int y = top - 19;

        NeonButton.drawCircle(ctx, startX + 7, y + 6, 7, (NeonButton.ACCENT & 0x00FFFFFF) | 0x33000000);

        int waveCol = NeonButton.ACCENT;
        ctx.fill(startX, y + 6, startX + 4, y + 7, waveCol);
        ctx.fill(startX + 4, y + 2, startX + 5, y + 7, waveCol);
        ctx.fill(startX + 5, y + 0, startX + 6, y + 10, NeonButton.ACCENT_BRIGHT);
        ctx.fill(startX + 6, y + 4, startX + 7, y + 11, waveCol);
        ctx.fill(startX + 7, y + 6, startX + 14, y + 7, waveCol);

        ctx.drawText(textRenderer, brand, startX + iconW, y + 2, 0xFFF6F6FC, true);

        int bx = startX + iconW + textW + 4;
        NeonButton.fillGradientRounded(ctx, bx, y + 1, verW, 10, 3, NeonButton.ACCENT, NeonButton.ACCENT_BRIGHT);
        NeonButton.drawSmallText(ctx, textRenderer, ver, bx + 3, y + 2.5f, 0xFFFFFFFF, 0.82f);
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
     * Полноценный интерактивный HUD Editor: перетаскивание, масштабирование колёсиком мыши и пресеты.
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
            int bw = 72;
            int gap = 5;
            int total = bw * 4 + gap * 3;
            int sx = (width - total) / 2;
            int y = height - 26;

            addDrawableChild(new NeonButton(sx, y, bw, 18, () -> "PulseVisual", null,
                    () -> HudManager.applyPulsePreset()));
            addDrawableChild(new NeonButton(sx + (bw + gap), y, bw, 18, () -> "FunTime PvP", null,
                    () -> HudManager.applyFunTimePreset(MinecraftClient.getInstance())));
            addDrawableChild(new NeonButton(sx + (bw + gap) * 2, y, bw, 18, () -> "Сброс позиций", null,
                    () -> {
                        ArtemConfig.get().hudPositions.clear();
                        ArtemConfig.get().save();
                    }));
            addDrawableChild(new NeonButton(sx + (bw + gap) * 3, y, bw, 18, () -> "Готово ✓", null,
                    this::close));
        }

        @Override
        public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(0, 0, width, height, 0x4408080E);
            // Направляющие оси по центру экрана
            ctx.fill(width / 2, 0, width / 2 + 1, height, 0x227B68EE);
            ctx.fill(0, height / 2, width, height / 2 + 1, 0x227B68EE);

            int pw = 250;
            int px = (width - pw) / 2;
            NeonButton.fillSmoothRounded(ctx, px, 24, pw, 16, 5, 0xE610101A);
            NeonButton.drawSmoothRoundedOutline(ctx, px, 24, pw, 16, 5, 0x447B68EE);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "ЛКМ — двигать элемент  |  Колёсико мыши — изменить размер",
                    width / 2, 28, 0xFFE6E6F4);
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
