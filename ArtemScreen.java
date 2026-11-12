package com.artem.visuals.gui;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.hud.HudManager;
import com.artem.visuals.optimize.Optimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class ArtemScreen extends Screen {
    private enum Category {
        VISUALS("Визуалы", "Основные", false),
        HUD("Интерфейс", "Основные", false),
        SWORD("Меч и Камера", "Основные", false),
        OPTIMIZE("Оптимизация", "Основные", true),
        WORLD("Мир и Время", "Остальные", false),
        CONFIGS("Конфиги", "Остальные", false),
        THEMES("Темы", "Остальные", false);

        final String title;
        final String group;
        final boolean badge;

        Category(String title, String group, boolean badge) {
            this.title = title;
            this.group = group;
            this.badge = badge;
        }
    }

    private sealed interface RowItem permits ToggleRow, SliderRow, SwatchRow, ActionRow {}

    private record ToggleRow(
            String label,
            Category category,
            BooleanSupplier getter,
            Runnable toggler,
            Runnable onSettings
    ) implements RowItem {}

    private record SliderRow(
            String label,
            Category category,
            double min,
            double max,
            boolean integer,
            DoubleSupplier getter,
            DoubleConsumer setter
    ) implements RowItem {}

    private record SwatchRow(
            String label,
            Category category,
            int[] palette,
            IntSupplier getter,
            IntConsumer setter
    ) implements RowItem {}

    private record ActionRow(
            String label,
            Category category,
            Supplier<String> badgeText,
            Runnable action
    ) implements RowItem {}

    private static final int[] ACCENT_PALETTE = {
            0xFFA594F9, 0xFF8A7CFF, 0xFF00F5FF, 0xFFFF6B9D,
            0xFF2EC4B6, 0xFFFFB703, 0xFF6BCB77, 0xFFF2F2FA
    };

    private static final int[] BG_PALETTE = {
            0x101018, 0x0C1220, 0x180E22, 0x0B1A17,
            0x1D0E14, 0x08080C
    };

    private final Screen parent;
    private Category activeCategory = Category.VISUALS;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private String statusToast = "";
    private long statusToastUntil = 0L;

    private String subDetailTitle = null;
    private final List<RowItem> subDetailRows = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private SliderRow draggingSlider = null;
    private int draggingSliderX = 0;
    private int draggingSliderW = 1;

    private int winX, winY, winW, winH;
    private int sideW, mainX, mainW, listX, listY, listW, listH;

    public ArtemScreen(Screen parent) {
        super(Text.literal("Artem Visuals"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        NeonButton.syncTheme();
        ArtemConfig c = ArtemConfig.get();
        float s = Math.max(0.75f, Math.min(1.15f, c.guiScale));

        winW = Math.min(width - 20, Math.round(396 * s));
        winH = Math.min(height - 20, Math.round(248 * s));
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;

        sideW = Math.round(108 * s);
        mainX = winX + sideW;
        mainW = winW - sideW;

        listX = mainX + 10;
        listY = winY + 42;
        listW = mainW - 20;
        listH = winH - 50;
    }

    private void toast(String msg) {
        this.statusToast = msg;
        this.statusToastUntil = System.currentTimeMillis() + 2200L;
    }

    private void openSubDetails(String title, List<RowItem> rows) {
        this.subDetailTitle = title;
        this.subDetailRows.clear();
        this.subDetailRows.addAll(rows);
        this.scrollOffset = 0;
    }

    private List<RowItem> buildAllRows(ArtemConfig c) {
        List<RowItem> all = new ArrayList<>();

        // --- ВИЗУАЛЫ ---
        all.add(new ToggleRow("Target ESP", Category.VISUALS, () -> c.targetEsp,
                () -> { c.targetEsp = !c.targetEsp; c.save(); },
                () -> openSubDetails("Target ESP", List.of(
                        new SwatchRow("Цвет кольца", Category.VISUALS, ACCENT_PALETTE, () -> c.targetEspColor, v -> { c.targetEspColor = v; c.save(); }),
                        new SliderRow("Скорость вращения", Category.VISUALS, 0.3, 2.5, false, () -> c.targetEspSpeed, v -> { c.targetEspSpeed = (float) v; c.save(); })
                ))));
        all.add(new ToggleRow("Jump Circle", Category.VISUALS, () -> c.jumpCircle,
                () -> { c.jumpCircle = !c.jumpCircle; c.save(); },
                () -> openSubDetails("Jump Circle", List.of(
                        new SwatchRow("Цвет круга", Category.VISUALS, ACCENT_PALETTE, () -> c.jumpCircleColor, v -> { c.jumpCircleColor = v; c.save(); }),
                        new SliderRow("Радиус волны", Category.VISUALS, 0.5, 2.5, false, () -> c.jumpCircleRadius, v -> { c.jumpCircleRadius = (float) v; c.save(); })
                ))));
        all.add(new ToggleRow("Trails (Шлейф)", Category.VISUALS, () -> c.trails,
                () -> { c.trails = !c.trails; c.save(); },
                () -> openSubDetails("Trails", List.of(
                        new SwatchRow("Цвет шлейфа", Category.VISUALS, ACCENT_PALETTE, () -> c.trailColor, v -> { c.trailColor = v; c.save(); }),
                        new SliderRow("Длина шлейфа", Category.VISUALS, 8, 60, true, () -> c.trailLength, v -> { c.trailLength = (int) v; c.save(); })
                ))));
        all.add(new ToggleRow("Nimb (Нимб)", Category.VISUALS, () -> c.nimb,
                () -> { c.nimb = !c.nimb; c.save(); },
                () -> openSubDetails("Nimb", List.of(
                        new SwatchRow("Цвет нимба", Category.VISUALS, ACCENT_PALETTE, () -> c.nimbColor, v -> { c.nimbColor = v; c.save(); }),
                        new SliderRow("Размер нимба", Category.VISUALS, 0.2, 0.7, false, () -> c.nimbSize, v -> { c.nimbSize = (float) v; c.save(); })
                ))));
        all.add(new ToggleRow("NameTags", Category.VISUALS, () -> c.nameTags,
                () -> { c.nameTags = !c.nameTags; c.save(); },
                () -> openSubDetails("NameTags", List.of(
                        new ToggleRow("Показывать здоровье", Category.VISUALS, () -> c.nameTagsHealth, () -> { c.nameTagsHealth = !c.nameTagsHealth; c.save(); }, null),
                        new ToggleRow("Показывать дистанцию", Category.VISUALS, () -> c.nameTagsDistance, () -> { c.nameTagsDistance = !c.nameTagsDistance; c.save(); }, null)
                ))));
        all.add(new ToggleRow("Hit Particles (Частицы удара)", Category.VISUALS, () -> c.hitParticles,
                () -> { c.hitParticles = !c.hitParticles; c.save(); },
                () -> openSubDetails("Hit Particles", List.of(
                        new SliderRow("Количество частиц", Category.VISUALS, 4, 32, true, () -> c.hitParticlesCount, v -> { c.hitParticlesCount = (int) v; c.save(); })
                ))));
        all.add(new ToggleRow("Crit Sparks (Искры критов)", Category.VISUALS, () -> c.critSparks, () -> { c.critSparks = !c.critSparks; c.save(); }, null));
        all.add(new ToggleRow("Damage Indicator", Category.VISUALS, () -> c.damageIndicator, () -> { c.damageIndicator = !c.damageIndicator; c.save(); }, null));
        all.add(new ToggleRow("Projectile Trails (След стрел)", Category.VISUALS, () -> c.projectileTrails, () -> { c.projectileTrails = !c.projectileTrails; c.save(); }, null));
        all.add(new ToggleRow("Block Overlay (Обводка блока)", Category.VISUALS, () -> c.blockOverlay,
                () -> { c.blockOverlay = !c.blockOverlay; c.save(); },
                () -> openSubDetails("Block Overlay", List.of(
                        new SwatchRow("Цвет обводки", Category.VISUALS, ACCENT_PALETTE, () -> c.blockOverlayColor, v -> { c.blockOverlayColor = v; c.save(); }),
                        new SliderRow("Прозрачность", Category.VISUALS, 0.2, 1.0, false, () -> c.blockOverlayAlpha, v -> { c.blockOverlayAlpha = (float) v; c.save(); })
                ))));
        all.add(new ToggleRow("Player ESP", Category.VISUALS, () -> c.esp,
                () -> { c.esp = !c.esp; c.save(); },
                () -> openSubDetails("Player ESP", List.of(
                        new SwatchRow("Цвет ESP", Category.VISUALS, ACCENT_PALETTE, () -> c.espColor, v -> { c.espColor = v; c.save(); }),
                        new ToggleRow("Пульсация", Category.VISUALS, () -> c.espPulse, () -> { c.espPulse = !c.espPulse; c.save(); }, null)
                ))));
        all.add(new ToggleRow("Chams", Category.VISUALS, () -> c.chams, () -> { c.chams = !c.chams; c.save(); }, null));
        all.add(new ToggleRow("Tracers", Category.VISUALS, () -> c.tracers, () -> { c.tracers = !c.tracers; c.save(); }, null));
        all.add(new ToggleRow("Item ESP", Category.VISUALS, () -> c.itemEsp, () -> { c.itemEsp = !c.itemEsp; c.save(); }, null));

        // --- ИНТЕРФЕЙС (HUD) ---
        all.add(new ToggleRow("Режим перемещения HUD", Category.HUD, () -> c.hudEditMode, () -> { c.hudEditMode = !c.hudEditMode; c.save(); }, null));
        all.add(new SliderRow("Масштаб элементов HUD", Category.HUD, 0.50, 1.20, false, () -> c.hudScale, v -> { c.hudScale = (float) v; c.save(); }));
        all.add(new ToggleRow("Верхняя панель (Ник/FPS/Время)", Category.HUD, () -> c.showServer, () -> { c.showServer = !c.showServer; c.save(); }, null));
        all.add(new ToggleRow("Мини-инвентарь (Inventory)", Category.HUD, () -> c.showInventory, () -> { c.showInventory = !c.showInventory; c.save(); }, null));
        all.add(new ToggleRow("Эффекты зелий (Potions)", Category.HUD, () -> c.showPotions, () -> { c.showPotions = !c.showPotions; c.save(); }, null));
        all.add(new ToggleRow("Горячие клавиши (Keybinds)", Category.HUD, () -> c.showHotKeys, () -> { c.showHotKeys = !c.showHotKeys; c.save(); }, null));
        all.add(new ToggleRow("Моделька игрока (Paperdoll)", Category.HUD, () -> c.showPaperdoll, () -> { c.showPaperdoll = !c.showPaperdoll; c.save(); }, null));
        all.add(new ToggleRow("TargetHUD", Category.HUD, () -> c.showTargetHud, () -> { c.showTargetHud = !c.showTargetHud; c.save(); }, null));
        all.add(new ToggleRow("Броня с прочностью (Armor)", Category.HUD, () -> c.showArmor, () -> { c.showArmor = !c.showArmor; c.save(); }, null));
        all.add(new ToggleRow("Проценты прочности брони", Category.HUD, () -> c.armorDurabilityPercent, () -> { c.armorDurabilityPercent = !c.armorDurabilityPercent; c.save(); }, null));
        all.add(new ToggleRow("Список модулей (ArrayList)", Category.HUD, () -> c.showArrayList, () -> { c.showArrayList = !c.showArrayList; c.save(); }, null));
        all.add(new ToggleRow("Нажатия клавиш (Keystrokes)", Category.HUD, () -> c.showKeystrokes, () -> { c.showKeystrokes = !c.showKeystrokes; c.save(); }, null));
        all.add(new ToggleRow("Радар сущностей (Radar)", Category.HUD, () -> c.showRadar, () -> { c.showRadar = !c.showRadar; c.save(); }, null));
        all.add(new ToggleRow("Мини-карта (MiniMap)", Category.HUD, () -> c.showMinimap, () -> { c.showMinimap = !c.showMinimap; c.save(); }, null));
        all.add(new ToggleRow("Координаты XYZ", Category.HUD, () -> c.showXyz, () -> { c.showXyz = !c.showXyz; c.save(); }, null));
        all.add(new ToggleRow("Скорость (BPS)", Category.HUD, () -> c.showSpeed, () -> { c.showSpeed = !c.showSpeed; c.save(); }, null));
        all.add(new ToggleRow("Счётчик кликов (CPS)", Category.HUD, () -> c.showCps, () -> { c.showCps = !c.showCps; c.save(); }, null));
        all.add(new ActionRow("Сбросить позиции HUD", Category.HUD, () -> "Сброс", () -> {
            c.hudPositions.clear();
            c.save();
            toast("Позиции HUD сброшены");
        }));

        // --- МЕЧ И КАМЕРА ---
        all.add(new ToggleRow("Маленький меч (SmallSword)", Category.SWORD, () -> c.smallSword, () -> { c.smallSword = !c.smallSword; c.save(); }, null));
        all.add(new ToggleRow("Уменьшать только оружие", Category.SWORD, () -> c.onlyWeapons, () -> { c.onlyWeapons = !c.onlyWeapons; c.save(); }, null));
        all.add(new SliderRow("Размер предмета в руке", Category.SWORD, 0.25, 1.25, false, () -> c.swordScale, v -> { c.swordScale = (float) v; c.save(); }));
        all.add(new SliderRow("Смещение по X", Category.SWORD, -0.40, 0.40, false, () -> c.swordOffsetX, v -> { c.swordOffsetX = (float) v; c.save(); }));
        all.add(new SliderRow("Смещение по Y", Category.SWORD, -0.40, 0.40, false, () -> c.swordOffsetY, v -> { c.swordOffsetY = (float) v; c.save(); }));
        all.add(new SliderRow("Смещение по Z", Category.SWORD, -0.40, 0.40, false, () -> c.swordOffsetZ, v -> { c.swordOffsetZ = (float) v; c.save(); }));
        all.add(new ToggleRow("Свечение предметов в руках", Category.SWORD, () -> c.handGlow, () -> { c.handGlow = !c.handGlow; c.save(); }, null));
        all.add(new ToggleRow("No HurtCam (Без тряски урона)", Category.SWORD, () -> c.noHurtCam, () -> { c.noHurtCam = !c.noHurtCam; c.save(); }, null));
        all.add(new ToggleRow("Low Fire (Низкий огонь)", Category.SWORD, () -> c.lowFire, () -> { c.lowFire = !c.lowFire; c.save(); }, null));
        all.add(new ToggleRow("Мягкая виньетка экрана", Category.SWORD, () -> c.screenVignette, () -> { c.screenVignette = !c.screenVignette; c.save(); }, null));
        all.add(new ToggleRow("Убрать покачивание камеры", Category.SWORD, () -> c.renderTweaks, () -> { c.renderTweaks = !c.renderTweaks; c.save(); }, null));
        all.add(new ToggleRow("Кастомный прицел", Category.SWORD, () -> c.customCrosshair, () -> { c.customCrosshair = !c.customCrosshair; c.save(); }, null));
        all.add(new SwatchRow("Цвет прицела", Category.SWORD, ACCENT_PALETTE, () -> c.crosshairColor, v -> { c.crosshairColor = v; c.save(); }));

        // --- ОПТИМИЗАЦИЯ ---
        all.add(new ToggleRow("Fullbright (Яркость 100%)", Category.OPTIMIZE, () -> c.softLighting, () -> {
            c.softLighting = !c.softLighting;
            c.save();
            Optimizer.applyLighting(MinecraftClient.getInstance());
        }, null));
        all.add(new ToggleRow("Адаптивный FPS лимитер частиц", Category.OPTIMIZE, () -> c.adaptive, () -> { c.adaptive = !c.adaptive; c.save(); }, null));
        all.add(new ToggleRow("Авто-профиль для FunTime", Category.OPTIMIZE, () -> c.autoFunTime, () -> { c.autoFunTime = !c.autoFunTime; c.save(); }, null));
        all.add(new ActionRow("Оптимизировать графику (FPS Boost)", Category.OPTIMIZE, () -> "Применить", () -> {
            Optimizer.applyPerformancePreset(MinecraftClient.getInstance());
            toast("Настройки FPS применены");
        }));

        // --- МИР И ВРЕМЯ ---
        all.add(new ActionRow("Время суток в мире", Category.WORLD, () -> switch (c.worldTimeMode) {
            case 1 -> "День";
            case 2 -> "Закат";
            case 3 -> "Ночь";
            default -> "Сервер";
        }, () -> {
            c.worldCustomizer = true;
            c.worldTimeMode = (c.worldTimeMode + 1) % 4;
            c.save();
        }));
        all.add(new ToggleRow("Падающая листва", Category.WORLD, () -> c.leafParticles, () -> { c.leafParticles = !c.leafParticles; c.save(); }, null));
        all.add(new ToggleRow("Ночные светлячки", Category.WORLD, () -> c.fireflies, () -> { c.fireflies = !c.fireflies; c.save(); }, null));
        all.add(new ToggleRow("Искры на поверхности воды", Category.WORLD, () -> c.waterSparkles, () -> { c.waterSparkles = !c.waterSparkles; c.save(); }, null));
        all.add(new ToggleRow("Частицы разрушения блоков", Category.WORLD, () -> c.blockBreakEffects, () -> { c.blockBreakEffects = !c.blockBreakEffects; c.save(); }, null));
        all.add(new ToggleRow("Цветокоррекция экрана", Category.WORLD, () -> c.colorCorrection, () -> { c.colorCorrection = !c.colorCorrection; c.save(); }, null));
        all.add(new SliderRow("Плотность частиц мира", Category.WORLD, 0, 3, true, () -> c.density, v -> { c.density = (int) v; c.save(); }));

        // --- КОНФИГИ ---
        all.add(new ActionRow("Пресет Andromeda Visuals", Category.CONFIGS, () -> "Загрузить", () -> {
            HudManager.applyPulsePreset();
            toast("Пресет Andromeda загружен");
        }));
        all.add(new ActionRow("Пресет FunTime PvP", Category.CONFIGS, () -> "Загрузить", () -> {
            HudManager.applyFunTimePreset(MinecraftClient.getInstance());
            toast("Пресет FunTime загружен");
        }));
        all.add(new ActionRow("Экспорт конфига в буфер обмена", Category.CONFIGS, () -> "Копировать", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.keyboard != null) {
                mc.keyboard.setClipboard(c.exportBase64());
                toast("Конфиг скопирован");
            }
        }));
        all.add(new ActionRow("Импорт конфига из буфера обмена", Category.CONFIGS, () -> "Вставить", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.keyboard != null && c.importBase64(mc.keyboard.getClipboard())) {
                NeonButton.syncTheme();
                init();
                toast("Конфиг успешно импортирован");
            } else {
                toast("Буфер не содержит конфиг");
            }
        }));
        all.add(new ActionRow("Сбросить все настройки по умолчанию", Category.CONFIGS, () -> "Сбросить", () -> {
            c.resetDefaults();
            NeonButton.syncTheme();
            init();
            toast("Настройки сброшены");
        }));

        // --- ТЕМЫ ---
        all.add(new SwatchRow("Акцент меню", Category.THEMES, ACCENT_PALETTE, () -> c.clientAccent, col -> {
            c.clientAccent = col;
            c.useCustomHue = false;
            c.rgbMode = false;
            c.save();
            NeonButton.syncTheme();
        }));
        all.add(new SwatchRow("Акцент HUD", Category.THEMES, ACCENT_PALETTE, () -> c.hudAccentColor, col -> {
            c.hudAccentColor = col;
            c.hudUseCustomHue = false;
            c.hudRainbow = false;
            c.save();
            NeonButton.syncTheme();
        }));
        all.add(new SwatchRow("Фон плашек HUD", Category.THEMES, BG_PALETTE, () -> c.hudBgColor, col -> {
            c.hudBgColor = col;
            c.save();
            NeonButton.syncTheme();
        }));
        all.add(new SliderRow("Масштаб окна меню", Category.THEMES, 0.75, 1.15, false, () -> c.guiScale, v -> {
            c.guiScale = (float) v;
            c.save();
            init();
        }));
        all.add(new ToggleRow("RGB перелив меню", Category.THEMES, () -> c.rgbMode, () -> { c.rgbMode = !c.rgbMode; c.save(); NeonButton.syncTheme(); }, null));
        all.add(new ToggleRow("RGB перелив HUD", Category.THEMES, () -> c.hudRainbow, () -> { c.hudRainbow = !c.hudRainbow; c.save(); NeonButton.syncTheme(); }, null
