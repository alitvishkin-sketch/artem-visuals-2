package com.artem.visuals.gui;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.effects.EffectManager;
import com.artem.visuals.hud.HudManager;
import com.artem.visuals.optimize.Optimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
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
        MAIN("Основное", "Основные", null, "Меч и прицел"),
        HELPERS("Хелперы", "Основные", "Updated", "Помощник"),
        AUCTION("Аукцион", "Основные", "Updated", "Фантайм"),
        OPTIMIZE("Оптимизация", "Основные", null, "Производительность"),
        VISUALS("Визуалы", "Основные", null, "Эффекты"),
        HUD("Интерфейс", "Основные", null, "Элементы HUD"),
        CONFIGS("Конфиги", "Остальные", null, "Профили"),
        FRIENDS("Друзья", "Остальные", null, "Список друзей"),
        EVENTS("Ивенты", "Остальные", null, "Таймер событий"),
        WAYPOINTS("Точки", "Остальные", null, "Метки координат"),
        THEMES("Темы", "Остальные", null, "Оформление");

        final String title;
        final String group;
        final String badge;
        final String subPill;

        Category(String title, String group, String badge, String subPill) {
            this.title = title;
            this.group = group;
            this.badge = badge;
            this.subPill = subPill;
        }
    }

    private sealed interface RowItem permits ToggleRow, SliderRow, SwatchRow, ActionRow, HeaderLabelRow, KeybindRow, CrosshairStyleRow {}

    private record HeaderLabelRow(String title, Category category) implements RowItem {}
    private record ToggleRow(String label, Category category, BooleanSupplier getter, Runnable toggler, Runnable onSettings) implements RowItem {}
    private record SliderRow(String label, Category category, double min, double max, boolean integer, DoubleSupplier getter, DoubleConsumer setter) implements RowItem {}
    private record SwatchRow(String label, Category category, int[] palette, IntSupplier getter, IntConsumer setter) implements RowItem {}
    private record ActionRow(String label, Category category, Supplier<String> badgeText, Runnable action) implements RowItem {}
    private record KeybindRow(String label, Category category, IntSupplier getter, IntConsumer setter) implements RowItem {}
    private record CrosshairStyleRow(String label, Category category) implements RowItem {}

    private static final int[] ACCENT_PALETTE = {
            0xFFA594F9, 0xFF8A7CFF, 0xFF00F5FF, 0xFFFF6B9D,
            0xFF2EC4B6, 0xFFFFB703, 0xFF6BCB77, 0xFFF2F2FA
    };

    private static final int[] BG_PALETTE = {
            0x101018, 0x0C1220, 0x180E22, 0x0B1A17,
            0x1D0E14, 0x08080C
    };

    private final Screen parent;
    private Category activeCategory = Category.MAIN;
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
    private KeybindRow listeningKeybind = null;

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

        winW = Math.min(width - 20, Math.round(404 * s));
        winH = Math.min(height - 16, Math.round(256 * s));
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;

        sideW = Math.round(112 * s);
        mainX = winX + sideW;
        mainW = winW - sideW;

        listX = mainX + 10;
        listY = winY + 41;
        listW = mainW - 20;
        listH = winH - 49;
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

    private String formatKeyName(int keyCode) {
        if (keyCode <= 0) return "Не назначен";
        return InputUtil.Type.KEYSYM.createFromCode(keyCode).getLocalizedText().getString().toUpperCase(Locale.ROOT);
    }

    private List<RowItem> buildAllRows(ArtemConfig c) {
        List<RowItem> all = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();

        // === ОСНОВНОЕ (Меч, ракурс, стиль удара и гладкий прицел) ===
        all.add(new HeaderLabelRow("Ракурс меча и анимация удара", Category.MAIN));
        all.add(new ActionRow("Стиль удара (Swing)", Category.MAIN, () -> switch (c.swingStyle) {
            case 1 -> "Плавный боковой";
            case 2 -> "Блок-хит 1.7";
            case 3 -> "Резкий выпад";
            case 4 -> "Круговой 360°";
            default -> "Обычный";
        }, () -> { c.swingStyle = (c.swingStyle + 1) % 5; c.save(); }));
        all.add(new ToggleRow("Маленький меч (SmallSword)", Category.MAIN, () -> c.smallSword, () -> { c.smallSword = !c.smallSword; c.save(); }, null));
        all.add(new ToggleRow("Уменьшать только оружие", Category.MAIN, () -> c.onlyWeapons, () -> { c.onlyWeapons = !c.onlyWeapons; c.save(); }, null));
        all.add(new SliderRow("Размер меча в руке", Category.MAIN, 0.25, 1.25, false, () -> c.swordScale, v -> { c.swordScale = (float) v; c.save(); }));
        all.add(new SliderRow("Ракурс: наклон вперёд/назад (X°)", Category.MAIN, -90, 90, true, () -> c.swordRotX, v -> { c.swordRotX = (float) v; c.save(); }));
        all.add(new SliderRow("Ракурс: поворот влево/вправо (Y°)", Category.MAIN, -90, 90, true, () -> c.swordRotY, v -> { c.swordRotY = (float) v; c.save(); }));
        all.add(new SliderRow("Ракурс: наклон вбок (Z°)", Category.MAIN, -90, 90, true, () -> c.swordRotZ, v -> { c.swordRotZ = (float) v; c.save(); }));
        all.add(new SliderRow("Позиция по X", Category.MAIN, -0.45, 0.45, false, () -> c.swordOffsetX, v -> { c.swordOffsetX = (float) v; c.save(); }));
        all.add(new SliderRow("Позиция по Y", Category.MAIN, -0.45, 0.45, false, () -> c.swordOffsetY, v -> { c.swordOffsetY = (float) v; c.save(); }));
        all.add(new SliderRow("Позиция по Z", Category.MAIN, -0.45, 0.45, false, () -> c.swordOffsetZ, v -> { c.swordOffsetZ = (float) v; c.save(); }));
        all.add(new ActionRow("Сбросить ракурс меча", Category.MAIN, () -> "Сброс", () -> {
            c.swordRotX = 0f; c.swordRotY = 0f; c.swordRotZ = 0f;
            c.swordOffsetX = 0.03f; c.swordOffsetY = -0.04f; c.swordOffsetZ = -0.05f;
            c.save();
            toast("Ракурс меча сброшен");
        }));

        all.add(new HeaderLabelRow("Кастомный гладкий прицел (отключает ванильный)", Category.MAIN));
        all.add(new ToggleRow("Кастомный прицел (убрать обычный)", Category.MAIN, () -> c.customCrosshair, () -> { c.customCrosshair = !c.customCrosshair; c.save(); }, null));
        all.add(new CrosshairStyleRow("Дизайн прицела (8 стилей)", Category.MAIN));
        all.add(new SliderRow("Размер прицела", Category.MAIN, 2, 14, true, () -> c.crosshairSize, v -> { c.crosshairSize = (int) v; c.save(); }));
        all.add(new SliderRow("Зазор от центра", Category.MAIN, 0, 12, true, () -> c.crosshairGap, v -> { c.crosshairGap = (int) v; c.save(); }));
        all.add(new SliderRow("Толщина линий", Category.MAIN, 1, 4, true, () -> c.crosshairThickness, v -> { c.crosshairThickness = (int) v; c.save(); }));
        all.add(new ToggleRow("Точка по центру прицела", Category.MAIN, () -> c.crosshairDot, () -> { c.crosshairDot = !c.crosshairDot; c.save(); }, null));
        all.add(new ToggleRow("Мягкая тень / обводка прицела", Category.MAIN, () -> c.crosshairOutline, () -> { c.crosshairOutline = !c.crosshairOutline; c.save(); }, null));
        all.add(new ToggleRow("Динамика прицела при ударе/ходьбе", Category.MAIN, () -> c.crosshairDynamic, () -> { c.crosshairDynamic = !c.crosshairDynamic; c.save(); }, null));
        all.add(new SwatchRow("Цвет прицела", Category.MAIN, ACCENT_PALETTE, () -> c.crosshairColor, v -> { c.crosshairColor = v; c.save(); }));

        all.add(new HeaderLabelRow("Камера и экран", Category.MAIN));
        all.add(new ToggleRow("Свечение предметов в руках", Category.MAIN, () -> c.handGlow, () -> { c.handGlow = !c.handGlow; c.save(); }, null));
        all.add(new ToggleRow("No HurtCam (Без тряски урона)", Category.MAIN, () -> c.noHurtCam, () -> { c.noHurtCam = !c.noHurtCam; c.save(); }, null));
        all.add(new ToggleRow("Low Fire (Низкий огонь)", Category.MAIN, () -> c.lowFire, () -> { c.lowFire = !c.lowFire; c.save(); }, null));
        all.add(new ToggleRow("Мягкая виньетка экрана", Category.MAIN, () -> c.screenVignette, () -> { c.screenVignette = !c.screenVignette; c.save(); }, null));
        all.add(new ToggleRow("Убрать покачивание камеры", Category.MAIN, () -> c.renderTweaks, () -> { c.renderTweaks = !c.renderTweaks; c.save(); }, null));

        // === ХЕЛПЕРЫ ===
        all.add(new HeaderLabelRow("Таймер эндер-жемчуга и границы PvP", Category.HELPERS));
        all.add(new ToggleRow("Окно секунд эндер-жемчуга слева", Category.HELPERS, () -> c.showPearlHud, () -> { c.showPearlHud = !c.showPearlHud; c.save(); }, null));
        all.add(new ToggleRow("Таймер и луч приземления жемчуга", Category.HELPERS, () -> c.pearlTimer, () -> { c.pearlTimer = !c.pearlTimer; c.save(); },
                () -> openSubDetails("Таймер эндер-жемчуга", List.of(
                        new ToggleRow("Линия траектории полёта", Category.HELPERS, () -> c.pearlTrajectory, () -> { c.pearlTrajectory = !c.pearlTrajectory; c.save(); }, null),
                        new SwatchRow("Цвет индикатора", Category.HELPERS, ACCENT_PALETTE, () -> c.pearlColor, col -> { c.pearlColor = col; c.save(); })
                ))));
        all.add(new ToggleRow("Линия траектории эндер-жемчуга", Category.HELPERS, () -> c.pearlTrajectory, () -> { c.pearlTrajectory = !c.pearlTrajectory; c.save(); }, null));
        all.add(new SwatchRow("Цвет таймера жемчуга", Category.HELPERS, ACCENT_PALETTE, () -> c.pearlColor, col -> { c.pearlColor = col; c.save(); }));

        all.add(new ToggleRow("Границы Трапки / Пласта по блокам", Category.HELPERS, () -> c.trapPlastPreview, () -> { c.trapPlastPreview = !c.trapPlastPreview; c.save(); }, null));
        all.add(new ActionRow("Режим границ (Трапка / Пласт)", Category.HELPERS, () -> switch (c.trapPlastMode) {
            case 1 -> "Трапка";
            case 2 -> "Пласт";
            default -> "Авто (предмет)";
        }, () -> { c.trapPlastMode = (c.trapPlastMode + 1) % 3; c.save(); }));
        all.add(new SliderRow("Размер границы (ширина в блоках)", Category.HELPERS, 3, 7, true, () -> c.trapPlastWidth, v -> { c.trapPlastWidth = (int) v; c.save(); }));
        all.add(new SliderRow("Высота трапки (в блоках)", Category.HELPERS, 2, 6, true, () -> c.trapPlastHeight, v -> { c.trapPlastHeight = (int) v; c.save(); }));

        all.add(new HeaderLabelRow("Авто-помощники", Category.HELPERS));
        all.add(new ToggleRow("Авто-спринт (AutoSprint)", Category.HELPERS, () -> c.autoSprint, () -> { c.autoSprint = !c.autoSprint; c.save(); }, null));
        all.add(new ToggleRow("Виджет кулдаунов (Жемчуг/Чарка)", Category.HELPERS, () -> c.showCooldowns, () -> { c.showCooldowns = !c.showCooldowns; c.save(); }, null));
        all.add(new ToggleRow("Предупреждение о поломке брони", Category.HELPERS, () -> c.lowArmorAlert, () -> { c.lowArmorAlert = !c.lowArmorAlert; c.save(); }, null));
        all.add(new SliderRow("Порог прочности брони (%)", Category.HELPERS, 5, 50, true, () -> c.lowArmorThreshold, v -> { c.lowArmorThreshold = (int) v; c.save(); }));
        all.add(new ToggleRow("Уведомление об окончании зелий", Category.HELPERS, () -> c.potionExpiryAlert, () -> { c.potionExpiryAlert = !c.potionExpiryAlert; c.save(); }, null));
        all.add(new ToggleRow("Координаты смерти в чат", Category.HELPERS, () -> c.deathCoords, () -> { c.deathCoords = !c.deathCoords; c.save(); }, null));
        all.add(new ToggleRow("Авто-точка на месте смерти", Category.HELPERS, () -> c.autoDeathWaypoint, () -> { c.autoDeathWaypoint = !c.autoDeathWaypoint; c.save(); }, null));
        all.add(new HeaderLabelRow("Быстрые команды", Category.HELPERS));
        all.add(new ActionRow("Открыть аукцион (/ah)", Category.HELPERS, () -> "/ah", () -> sendCmd(mc, "ah")));
        all.add(new ActionRow("Открыть эндер-сундук (/ec)", Category.HELPERS, () -> "/ec", () -> sendCmd(mc, "ec")));
        all.add(new ActionRow("Открыть верстак (/craft)", Category.HELPERS, () -> "/craft", () -> sendCmd(mc, "craft")));
        all.add(new ActionRow("Починить предмет (/fix)", Category.HELPERS, () -> "/fix", () -> sendCmd(mc, "fix")));

        // === АУКЦИОН ===
        all.add(new HeaderLabelRow("Бинды клавиш аукциона", Category.AUCTION));
        all.add(new KeybindRow("Бинд: открыть аукцион (/ah)", Category.AUCTION, () -> c.ahOpenKeyCode, k -> { c.ahOpenKeyCode = k; c.save(); }));
        all.add(new KeybindRow("Бинд: /ah search по предмету в руке", Category.AUCTION, () -> c.ahSearchKeyCode, k -> { c.ahSearchKeyCode = k; c.save(); }));
        all.add(new HeaderLabelRow("Помощник", Category.AUCTION));
        all.add(new ToggleRow("Показать цену за один", Category.AUCTION, () -> c.ahUnitPrice, () -> { c.ahUnitPrice = !c.ahUnitPrice; c.save(); }, null));
        all.add(new ToggleRow("Автоперевыставление", Category.AUCTION, () -> c.ahAutoRelist, () -> { c.ahAutoRelist = !c.ahAutoRelist; c.save(); }, null));
        all.add(new SliderRow("Интервал перевыставления (сек)", Category.AUCTION, 10, 300, true, () -> c.ahRelistInterval, v -> { c.ahRelistInterval = (int) v; c.save(); }));
        all.add(new ToggleRow("Включить поиск /ah search по бинду", Category.AUCTION, () -> c.ahSearchHandItem, () -> { c.ahSearchHandItem = !c.ahSearchHandItem; c.save(); }, null));
        all.add(new ActionRow("Найти предмет в руке на аукционе сейчас", Category.AUCTION, () -> "Поиск", () -> {
            EffectManager.triggerAhSearchForHand(mc);
            close();
        }));

        // === ОПТИМИЗАЦИЯ ===
        all.add(new ToggleRow("Fullbright (Яркость 100%)", Category.OPTIMIZE, () -> c.softLighting, () -> {
            c.softLighting = !c.softLighting;
            c.save();
            Optimizer.applyLighting(mc);
        }, null));
        all.add(new ToggleRow("Адаптивный FPS лимитер частиц", Category.OPTIMIZE, () -> c.adaptive, () -> { c.adaptive = !c.adaptive; c.save(); }, null));
        all.add(new ToggleRow("Авто-профиль для FunTime", Category.OPTIMIZE, () -> c.autoFunTime, () -> { c.autoFunTime = !c.autoFunTime; c.save(); }, null));
        all.add(new ActionRow("Время суток в мире", Category.OPTIMIZE, () -> switch (c.worldTimeMode) {
            case 1 -> "День";
            case 2 -> "Закат";
            case 3 -> "Ночь";
            default -> "Сервер";
        }, () -> {
            c.worldCustomizer = true;
            c.worldTimeMode = (c.worldTimeMode + 1) % 4;
            c.save();
        }));
        all.add(new ActionRow("Оптимизировать графику (FPS Boost)", Category.OPTIMIZE, () -> "Применить", () -> {
            Optimizer.applyPerformancePreset(mc);
            toast("Настройки FPS применены");
        }));

        // === ВИЗУАЛЫ ===
        all.add(new ToggleRow("Target ESP", Category.VISUALS, () -> c.targetEsp, () -> { c.targetEsp = !c.targetEsp; c.save(); },
                () -> openSubDetails("Target ESP", List.of(
                        new SwatchRow("Цвет кольца", Category.VISUALS, ACCENT_PALETTE, () -> c.targetEspColor, v -> { c.targetEspColor = v; c.save(); }),
                        new SliderRow("Скорость вращения", Category.VISUALS, 0.3, 2.5, false, () -> c.targetEspSpeed, v -> { c.targetEspSpeed = (float) v; c.save(); })
                ))));
        all.add(new ToggleRow("Jump Circle", Category.VISUALS, () -> c.jumpCircle, () -> { c.jumpCircle = !c.jumpCircle; c.save(); },
                () -> openSubDetails("Jump Circle", List.of(
                        new SwatchRow("Цвет круга", Category.VISUALS, ACCENT_PALETTE, () -> c.jumpCircleColor, v -> { c.jumpCircleColor = v; c.save(); }),
                        new SliderRow("Радиус волны", Category.VISUALS, 0.5, 2.5, false, () -> c.jumpCircleRadius, v -> { c.jumpCircleRadius = (float) v; c.save(); })
                ))));
        all.add(new ToggleRow("Trails (Шлейф)", Category.VISUALS, () -> c.trails, () -> { c.trails = !c.trails; c.save(); },
                () -> openSubDetails("Trails", List.of(
                        new SwatchRow("Цвет шлейфа", Category.VISUALS, ACCENT_PALETTE, () -> c.trailColor, v -> { c.trailColor = v; c.save(); }),
                        new SliderRow("Длина шлейфа", Category.VISUALS, 8, 60, true, () -> c.trailLength, v -> { c.trailLength = (int) v; c.save(); })
                ))));
        all.add(new ToggleRow("Nimb (Нимб)", Category.VISUALS, () -> c.nimb, () -> { c.nimb = !c.nimb; c.save(); },
                () -> openSubDetails("Nimb", List.of(
                        new SwatchRow("Цвет нимба", Category.VISUALS, ACCENT_PALETTE, () -> c.nimbColor, v -> { c.nimbColor = v; c.save(); }),
                        new SliderRow("Размер нимба", Category.VISUALS, 0.2, 0.7, false, () -> c.nimbSize, v -> { c.nimbSize = (float) v; c.save(); })
                ))));
        all.add(new ToggleRow("NameTags", Category.VISUALS, () -> c.nameTags, () -> { c.nameTags = !c.nameTags; c.save(); },
                () -> openSubDetails("NameTags", List.of(
                        new ToggleRow("Показывать здоровье", Category.VISUALS, () -> c.nameTagsHealth, () -> { c.nameTagsHealth = !c.nameTagsHealth; c.save(); }, null),
                        new ToggleRow("Показывать дистанцию", Category.VISUALS, () -> c.nameTagsDistance, () -> { c.nameTagsDistance = !c.nameTagsDistance; c.save(); }, null)
                ))));
        all.add(new ToggleRow("Hit Particles (Частицы удара)", Category.VISUALS, () -> c.hitParticles, () -> { c.hitParticles = !c.hitParticles; c.save(); }, null));
        all.add(new ToggleRow("Crit Sparks (Искры критов)", Category.VISUALS, () -> c.critSparks, () -> { c.critSparks = !c.critSparks; c.save(); }, null));
        all.add(new ToggleRow("Damage Indicator", Category.VISUALS, () -> c.damageIndicator, () -> { c.damageIndicator = !c.damageIndicator; c.save(); }, null));
        all.add(new ToggleRow("Projectile Trails (След стрел)", Category.VISUALS, () -> c.projectileTrails, () -> { c.projectileTrails = !c.projectileTrails; c.save(); }, null));
        all.add(new ToggleRow("Block Overlay (Обводка блока)", Category.VISUALS, () -> c.blockOverlay, () -> { c.blockOverlay = !c.blockOverlay; c.save(); }, null));
        all.add(new ToggleRow("Player ESP", Category.VISUALS, () -> c.esp, () -> { c.esp = !c.esp; c.save(); }, null));
        all.add(new ToggleRow("Chams", Category.VISUALS, () -> c.chams, () -> { c.chams = !c.chams; c.save(); }, null));
        all.add(new ToggleRow("Tracers", Category.VISUALS, () -> c.tracers, () -> { c.tracers = !c.tracers; c.save(); }, null));
        all.add(new ToggleRow("Item ESP", Category.VISUALS, () -> c.itemEsp, () -> { c.itemEsp = !c.itemEsp; c.save(); }, null));
        all.add(new ToggleRow("Падающая листва", Category.VISUALS, () -> c.leafParticles, () -> { c.leafParticles = !c.leafParticles; c.save(); }, null));
        all.add(new ToggleRow("Ночные светлячки", Category.VISUALS, () -> c.fireflies, () -> { c.fireflies = !c.fireflies; c.save(); }, null));

        // === ИНТЕРФЕЙС ===
        all.add(new ToggleRow("Режим перемещения HUD", Category.HUD, () -> c.hudEditMode, () -> { c.hudEditMode = !c.hudEditMode; c.save(); }, null));
        all.add(new SliderRow("Масштаб элементов HUD", Category.HUD, 0.50, 1.20, false, () -> c.hudScale, v -> { c.hudScale = (float) v; c.save(); }));
        all.add(new ToggleRow("Верхняя панель (Ник/FPS/Время)", Category.HUD, () -> c.showServer, () -> { c.showServer = !c.showServer; c.save(); }, null));
        all.add(new ToggleRow("Мини-инвентарь (Inventory)", Category.HUD, () -> c.showInventory, () -> { c.showInventory = !c.showInventory; c.save(); }, null));
        all.add(new ToggleRow("Эффекты зелий (Potions)", Category.HUD, () -> c.showPotions, () -> { c.showPotions = !c.showPotions; c.save(); }, null));
        all.add(new ToggleRow("Окно таймера жемчуга (Pearl Timer)", Category.HUD, () -> c.showPearlHud, () -> { c.showPearlHud = !c.showPearlHud; c.save(); }, null));
        all.add(new ToggleRow("Кулдауны предметов (Cooldowns)", Category.HUD, () -> c.showCooldowns, () -> { c.showCooldowns = !c.showCooldowns; c.save(); }, null));
        all.add(new ToggleRow("Горячие клавиши (Keybinds)", Category.HUD, () -> c.showHotKeys, () -> { c.showHotKeys = !c.showHotKeys; c.save(); }, null));
        all.add(new ToggleRow("Моделька игрока (Paperdoll)", Category.HUD, () -> c.showPaperdoll, () -> { c.showPaperdoll = !c.showPaperdoll; c.save(); }, null));
        all.add(new ToggleRow("TargetHUD", Category.HUD, () -> c.showTargetHud, () -> { c.showTargetHud = !c.showTargetHud; c.save(); }, null));
        all.add(new ToggleRow("Броня с прочностью (Armor)", Category.HUD, () -> c.showArmor, () -> { c.showArmor = !c.showArmor; c.save(); }, null));
        all.add(new ToggleRow("Проценты прочности брони", Category.HUD, () -> c.armorDurabilityPercent, () -> { c.armorDurabilityPercent = !c.armorDurabilityPercent; c.save(); }, null));
        all.add(new ToggleRow("Нажатия клавиш (Keystrokes)", Category.HUD, () -> c.showKeystrokes, () -> { c.showKeystrokes = !c.showKeystrokes; c.save(); }, null));
        all.add(new ToggleRow("Радар сущностей (Radar)", Category.HUD, () -> c.showRadar, () -> { c.showRadar = !c.showRadar; c.save(); }, null));
        all.add(new ToggleRow("Мини-карта (MiniMap)", Category.HUD, () -> c.showMinimap, () -> { c.showMinimap = !c.showMinimap; c.save(); }, null));
        all.add(new ActionRow("Сбросить позиции HUD", Category.HUD, () -> "Сброс", () -> {
            c.hudPositions.clear();
            c.save();
            toast("Позиции HUD сброшены");
        }));

        // === КОНФИГИ ===
        all.add(new ActionRow("Пресет Andromeda Visuals", Category.CONFIGS, () -> "Загрузить", () -> {
            HudManager.applyPulsePreset();
            toast("Пресет Andromeda загружен");
        }));
        all.add(new ActionRow("Пресет FunTime PvP + Helpers", Category.CONFIGS, () -> "Загрузить", () -> {
            HudManager.applyFunTimePreset(mc);
            toast("Пресет FunTime загружен");
        }));
        all.add(new ActionRow("Экспорт конфига в буфер обмена", Category.CONFIGS, () -> "Копировать", () -> {
            if (mc != null && mc.keyboard != null) {
                mc.keyboard.setClipboard(c.exportBase64());
                toast("Конфиг скопирован");
            }
        }));
        all.add(new ActionRow("Импорт конфига из буфера обмена", Category.CONFIGS, () -> "Вставить", () -> {
            if (mc != null && mc.keyboard != null && c.importBase64(mc.keyboard.getClipboard())) {
                NeonButton.syncTheme();
                init();
                toast("Конфиг импортирован");
            } else {
                toast("Буфер не содержит конфиг");
            }
        }));
        all.add(new ActionRow("Сбросить все настройки", Category.CONFIGS, () -> "Сбросить", () -> {
            c.resetDefaults();
            NeonButton.syncTheme();
            init();
            toast("Настройки сброшены");
        }));

        // === ДРУЗЬЯ ===
        all.add(new ToggleRow("Подсвечивать друзей (NameTags/Target)", Category.FRIENDS, () -> c.highlightFriends, () -> { c.highlightFriends = !c.highlightFriends; c.save(); }, null));
        all.add(new SwatchRow("Цвет подсветки друзей", Category.FRIENDS, new int[]{0xFF5CE67E, 0xFF00F5FF, 0xFFA594F9, 0xFFFFB703, 0xFFFF6B9D, 0xFFFFFFFF}, () -> c.friendColor, col -> { c.friendColor = col; c.save(); }));
        if (!c.friends.isEmpty()) {
            all.add(new HeaderLabelRow("Добавленные друзья (" + c.friends.size() + ")", Category.FRIENDS));
            for (String f : new ArrayList<>(c.friends)) {
                all.add(new ActionRow("★ " + f, Category.FRIENDS, () -> "Удалить", () -> {
                    c.friends.removeIf(x -> x.equalsIgnoreCase(f));
                    c.save();
                    toast("Удалён: " + f);
                }));
            }
        }
        if (mc != null && mc.getNetworkHandler() != null && mc.player != null) {
            String selfName = mc.player.getName().getString();
            List<String> onlineOthers = new ArrayList<>();
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile() != null && entry.getProfile().getName() != null) {
                    String n = entry.getProfile().getName();
                    if (!n.equalsIgnoreCase(selfName) && !c.isFriend(n)) onlineOthers.add(n);
                }
            }
            if (!onlineOthers.isEmpty()) {
                all.add(new HeaderLabelRow("Игроки на сервере", Category.FRIENDS));
                for (int i = 0; i < Math.min(10, onlineOthers.size()); i++) {
                    final String candidate = onlineOthers.get(i);
                    all.add(new ActionRow(candidate, Category.FRIENDS, () -> "+ Друг", () -> {
                        if (!c.isFriend(candidate)) {
                            c.friends.add(candidate);
                            c.save();
                            toast("Друг добавлен: " + candidate);
                        }
                    }));
                }
            }
        }

        // === ИВЕНТЫ ===
        all.add(new ToggleRow("Показывать таймер ивента в HUD", Category.EVENTS, () -> c.showEventTimer, () -> { c.showEventTimer = !c.showEventTimer; c.save(); }, null));
        all.add(new ActionRow("Запустить таймер на 5 минут", Category.EVENTS, () -> "Старт 5м", () -> startEventTimer(c, "Ивент", 5)));
        all.add(new ActionRow("Запустить таймер на 15 минут", Category.EVENTS, () -> "Старт 15м", () -> startEventTimer(c, "Ивент", 15)));
        all.add(new ActionRow("Запустить таймер на 30 минут", Category.EVENTS, () -> "Старт 30м", () -> startEventTimer(c, "Ивент", 30)));
        all.add(new ActionRow("Остановить и скрыть таймер", Category.EVENTS, () -> "Стоп", () -> {
            c.eventEndTimeMs = 0L;
            c.showEventTimer = false;
            c.save();
            toast("Таймер остановлен");
        }));

        // === ТОЧКИ ===
        all.add(new ToggleRow("Отображать точки на экране", Category.WAYPOINTS, () -> c.showWaypoints, () -> { c.showWaypoints = !c.showWaypoints; c.save(); }, null));
        all.add(new ActionRow("Сохранить текущие координаты (База)", Category.WAYPOINTS, () -> "+ Точка", () -> {
            if (mc != null && mc.player != null) {
                BlockPos p = mc.player.getBlockPos();
                String name = "Точка #" + (c.waypoints.size() + 1);
                c.waypoints.add(new ArtemConfig.Waypoint(name, p.getX(), p.getY(), p.getZ(), c.hudAccentColor));
                c.showWaypoints = true;
                c.save();
                toast("Сохранено: " + p.getX() + ", " + p.getY() + ", " + p.getZ());
            }
        }));
        if (!c.waypoints.isEmpty()) {
            all.add(new HeaderLabelRow("Сохранённые точки (" + c.waypoints.size() + ")", Category.WAYPOINTS));
            for (ArtemConfig.Waypoint wp : new ArrayList<>(c.waypoints)) {
                String coords = wp.name + " (" + wp.x + ", " + wp.y + ", " + wp.z + ")";
                all.add(new ActionRow(coords, Category.WAYPOINTS, () -> "Удалить", () -> {
                    c.waypoints.remove(wp);
                    c.save();
                    toast("Точка удалена");
                }));
            }
        }

        // === ТЕМЫ ===
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
        all.add(new ToggleRow("RGB перелив HUD", Category.THEMES, () -> c.hudRainbow, () -> { c.hudRainbow = !c.hudRainbow; c.save(); NeonButton.syncTheme(); }, null));

        return all;
    }

    private void startEventTimer(ArtemConfig c, String name, int minutes) {
        c.eventName = name;
        c.eventEndTimeMs = System.currentTimeMillis() + minutes * 60_000L;
        c.showEventTimer = true;
        c.save();
        toast("Таймер на " + minutes + " мин запущен");
    }

    private void sendCmd(MinecraftClient mc, String cmd) {
        if (mc != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand(cmd);
            close();
        }
    }

    private List<RowItem> visibleRows(ArtemConfig c) {
        if (!searchQuery.isBlank()) {
            String q = searchQuery.toLowerCase(Locale.ROOT).trim();
            List<RowItem> found = new ArrayList<>();
            for (RowItem item : buildAllRows(c)) {
                if (!(item instanceof HeaderLabelRow) && rowLabel(item).toLowerCase(Locale.ROOT).contains(q)) {
                    found.add(item);
                }
            }
            return found;
        }
        if (subDetailTitle != null && !subDetailRows.isEmpty()) return subDetailRows;
        List<RowItem> filtered = new ArrayList<>();
        for (RowItem item : buildAllRows(c)) {
            if (rowCategory(item) == activeCategory) filtered.add(item);
        }
        return filtered;
    }

    private String rowLabel(RowItem r) {
        if (r instanceof HeaderLabelRow h) return h.title();
        if (r instanceof ToggleRow t) return t.label();
        if (r instanceof SliderRow s) return s.label();
        if (r instanceof SwatchRow w) return w.label();
        if (r instanceof ActionRow a) return a.label();
        if (r instanceof KeybindRow k) return k.label();
        if (r instanceof CrosshairStyleRow cs) return cs.label();
        return "";
    }

    private Category rowCategory(RowItem r) {
        if (r instanceof HeaderLabelRow h) return h.category();
        if (r instanceof ToggleRow t) return t.category();
        if (r instanceof SliderRow s) return s.category();
        if (r instanceof SwatchRow w) return w.category();
        if (r instanceof ActionRow a) return a.category();
        if (r instanceof KeybindRow k) return k.category();
        if (r instanceof CrosshairStyleRow cs) return cs.category();
        return Category.MAIN;
    }

    private int rowHeight(RowItem r) {
        if (r instanceof HeaderLabelRow) return 12;
        if (r instanceof CrosshairStyleRow) return 22;
        return (r instanceof SliderRow) ? 24 : 18;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x6A06060A);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        NeonButton.syncTheme();
        renderBackground(ctx, mouseX, mouseY, delta);

        ArtemConfig c = ArtemConfig.get();
        int acc = NeonButton.ACCENT;
        int accB = NeonButton.ACCENT_BRIGHT;

        NeonButton.drawAmbientGlow(ctx, winX, winY, winW, winH, 7, acc, 2);
        NeonButton.fillSmoothRounded(ctx, winX, winY, winW, winH, 7, 0xF416161C);
        NeonButton.fillSmoothRounded(ctx, winX, winY, sideW, winH, 7, 0xF8111116);
        ctx.fill(mainX - 4, winY, mainX, winY + winH, 0xF8111116);
        NeonButton.drawSmoothRoundedOutline(ctx, winX, winY, winW, winH, 7, 0x22FFFFFF);
        ctx.fill(mainX, winY + 1, mainX + 1, winY + winH - 1, 0x16FFFFFF);

        renderSidebar(ctx, mouseX, mouseY, accB);
        renderMainHeader(ctx, mouseX, mouseY, acc, accB);
        renderRowsList(ctx, mouseX, mouseY, c, acc, accB);

        if (!statusToast.isEmpty() && System.currentTimeMillis() < statusToastUntil) {
            int tw = textRenderer.getWidth(statusToast) + 12;
            int tx = winX + winW - tw - 10;
            int ty = winY + winH - 19;
            NeonButton.fillSmoothRounded(ctx, tx, ty, tw, 13, 4, 0xEE1E1E28);
            NeonButton.drawSmoothRoundedOutline(ctx, tx, ty, tw, 13, 4, (accB & 0x00FFFFFF) | 0x88000000);
            NeonButton.drawSmallText(ctx, textRenderer, statusToast, tx + 6, ty + 3, accB, 0.80f);
        }
    }

    private void renderSidebar(DrawContext ctx, int mouseX, int mouseY, int accB) {
        int lx = winX + 11;
        int ly = winY + 10;
        drawStarLogo(ctx, lx, ly, accB);
        NeonButton.drawSmallText(ctx, textRenderer, "Artem", lx + 12, ly - 2, 0xFFF4F4FC, 0.95f);

        int sx = winX + 7;
        int sy = winY + 23;
        int sw = sideW - 14;
        NeonButton.fillSmoothRounded(ctx, sx, sy, sw, 13, 4, searchFocused ? 0xFF1E1E28 : 0xFF181820);
        NeonButton.drawSmoothRoundedOutline(ctx, sx, sy, sw, 13, 4, searchFocused ? ((accB & 0x00FFFFFF) | 0x88000000) : 0x18FFFFFF);
        drawSearchIcon(ctx, sx + 5, sy + 3, 0xFF7A7A90);

        String shownSearch = searchQuery.isEmpty()
                ? (searchFocused ? "|" : "Поиск...")
                : (searchQuery + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : ""));
        NeonButton.drawSmallText(ctx, textRenderer, shownSearch, sx + 14, sy + 3, searchQuery.isEmpty() ? 0xFF6E6E84 : 0xFFEAEAF6, 0.76f);

        int cy = winY + 41;
        String lastGroup = "";
        for (Category cat : Category.values()) {
            if (!cat.group.equals(lastGroup)) {
                lastGroup = cat.group;
                NeonButton.drawSmallText(ctx, textRenderer, lastGroup, winX + 9, cy, 0xFF646478, 0.70f);
                cy += 8;
            }
            boolean selected = searchQuery.isBlank() && activeCategory == cat;
            boolean hovered = mouseX >= winX + 6 && mouseX <= winX + sideW - 6 && mouseY >= cy && mouseY <= cy + 12;
            if (selected) NeonButton.fillSmoothRounded(ctx, winX + 6, cy, sideW - 12, 12, 3, 0xFF21212B);
            else if (hovered) NeonButton.fillSmoothRounded(ctx, winX + 6, cy, sideW - 12, 12, 3, 0x881C1C24);

            drawCategoryIcon(ctx, cat, winX + 10, cy + 2, selected ? accB : (hovered ? 0xFFB8B8CE : 0xFF7D7D94));
            NeonButton.drawSmallText(ctx, textRenderer, cat.title, winX + 20, cy + 3, selected ? 0xFFF4F4FC : (hovered ? 0xFFD4D4E6 : 0xFF9494AA), 0.76f);

            if (cat.badge != null && sideW >= 100) {
                int bw = Math.round(textRenderer.getWidth(cat.badge) * 0.62f) + 6;
                int bx = winX + sideW - bw - 8;
                NeonButton.fillSmoothRounded(ctx, bx, cy + 2, bw, 8, 2, 0xFF262632);
                NeonButton.drawSmallText(ctx, textRenderer, cat.badge, bx + 3, cy + 3, 0xFF9E9EB6, 0.62f);
            }
            cy += 13;
        }

        int py = winY + winH - 22;
        ctx.fill(winX + 7, py - 3, winX + sideW - 7, py - 2, 0x14FFFFFF);
        MinecraftClient mc = MinecraftClient.getInstance();
        String nick = (mc != null && mc.player != null) ? mc.player.getName().getString() : "Player";
        if (nick.length() > 11) nick = nick.substring(0, 10) + "…";

        int ax = winX + 8;
        int ay = py + 1;
        NeonButton.fillSmoothRounded(ctx, ax, ay, 14, 14, 3, 0xFF23232E);
        if (mc != null && mc.player instanceof AbstractClientPlayerEntity p) {
            PlayerSkinDrawer.draw(ctx, p.getSkinTextures().texture(), ax + 1, ay + 1, 12);
        } else {
            NeonButton.drawCircle(ctx, ax + 7, ay + 7, 4, accB);
        }

        NeonButton.drawSmallText(ctx, textRenderer, nick, ax + 17, ay + 1, 0xFFEAEAF6, 0.76f);
        NeonButton.drawSmallText(ctx, textRenderer, "Fabric 1.21", ax + 17, ay + 8, 0xFF6E6E84, 0.66f);
        drawGearIcon(ctx, winX + sideW - 15, ay + 3, 0xFF7D7D94);
    }

    private void renderMainHeader(DrawContext ctx, int mouseX, int mouseY, int acc, int accB) {
        int hx = mainX + 10;
        int hy = winY + 9;
        drawHomeIcon(ctx, hx, hy + 1, 0xFF8A8AA0);
        NeonButton.drawSmallText(ctx, textRenderer, ">", hx + 11, hy + 1, 0xFF646478, 0.80f);

        String title = !searchQuery.isBlank() ? ("Поиск: " + searchQuery) : activeCategory.title;
        NeonButton.drawSmallText(ctx, textRenderer, title, hx + 19, hy + 1, 0xFFEAEAF6, 0.85f);

        if (searchQuery.isBlank() && subDetailTitle != null) {
            int offset = 23 + Math.round(textRenderer.getWidth(title) * 0.85f);
            NeonButton.drawSmallText(ctx, textRenderer, ">  " + subDetailTitle + " (назад)", hx + offset, hy + 1, accB, 0.82f);
        }

        int closeX = winX + winW - 18;
        int closeY = winY + 7;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        NeonButton.fillSmoothRounded(ctx, closeX, closeY, 12, 12, 3, closeHover ? 0x66FF4D6D : 0xFF1F1F28);
        NeonButton.drawSmallText(ctx, textRenderer, "x", closeX + 4, closeY + 2, closeHover ? 0xFFFFFFFF : 0xFF9A9AB0, 0.80f);

        int dotX = closeX - 10;
        NeonButton.drawCircle(ctx, dotX, winY + 13, 4, (acc & 0x00FFFFFF) | 0x88000000);
        NeonButton.drawCircle(ctx, dotX - 4, winY + 13, 4, (accB & 0x00FFFFFF) | 0xBB000000);
        NeonButton.drawCircle(ctx, dotX - 8, winY + 13, 4, 0xFFF2D7EE);

        ctx.fill(mainX + 1, winY + 23, winX + winW - 1, winY + 24, 0x16FFFFFF);

        int pillY = winY + 27;
        String pillText = (subDetailTitle != null && searchQuery.isBlank()) ? "Параметры модуля" : activeCategory.subPill;
        int pw = Math.round(textRenderer.getWidth(pillText) * 0.78f) + 10;
        NeonButton.fillSmoothRounded(ctx, hx, pillY, pw, 11, 3, 0xFF21212B);
        NeonButton.drawSmallText(ctx, textRenderer, pillText, hx + 5, pillY + 2, 0xFFD4D4E6, 0.78f);
    }

    private void renderRowsList(DrawContext ctx, int mouseX, int mouseY, ArtemConfig c, int acc, int accB) {
        List<RowItem> rows = visibleRows(c);
        int totalH = 0;
        for (RowItem r : rows) totalH += rowHeight(r) + 3;
        maxScroll = Math.max(0, totalH - listH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        ctx.enableScissor(listX - 2, listY - 1, listX + listW + 2, listY + listH + 1);
        int ry = listY - scrollOffset;
        for (RowItem item : rows) {
            int rh = rowHeight(item);
            if (ry + rh >= listY && ry <= listY + listH) {
                boolean hovered = mouseX >= listX && mouseX <= listX + listW
                        && mouseY >= Math.max(listY, ry) && mouseY <= Math.min(listY + listH, ry + rh);
                renderSingleRow(ctx, item, listX, ry, listW, rh, hovered, acc, accB);
            }
            ry += rh + 3;
        }
        if (rows.isEmpty()) {
            NeonButton.drawSmallText(ctx, textRenderer, "Ничего не найдено", listX + 6, listY + 8, 0xFF7D7D94, 0.85f);
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int sbX = winX + winW - 5;
            int barH = Math.max(18, (int) ((listH / (float) totalH) * listH));
            int barY = listY + (int) ((scrollOffset / (float) maxScroll) * (listH - barH));
            NeonButton.fillSmoothRounded(ctx, sbX, barY, 2, barH, 1, (accB & 0x00FFFFFF) | 0x99000000);
        }
    }

    private void renderSingleRow(DrawContext ctx, RowItem item, int x, int y, int w, int h, boolean hovered, int acc, int accB) {
        if (item instanceof HeaderLabelRow hl) {
            NeonButton.drawSmallText(ctx, textRenderer, hl.title(), x + 2, y + 3, 0xFF7E7E96, 0.78f);
            return;
        }

        NeonButton.fillSmoothRounded(ctx, x, y, w, h, 4, hovered ? 0xFF22222C : 0xFF1C1C24);
        NeonButton.drawSmoothRoundedOutline(ctx, x, y, w, h, 4, hovered ? 0x20FFFFFF : 0x10FFFFFF);

        if (item instanceof CrosshairStyleRow cs) {
            ArtemConfig c = ArtemConfig.get();
            int st = Math.floorMod(c.crosshairStyle, HudManager.CROSSHAIR_STYLE_NAMES.length);
            String styleName = (st + 1) + "/8 • " + HudManager.CROSSHAIR_STYLE_NAMES[st];

            drawRowBulletIcon(ctx, x + 6, y + 7, accB);
            NeonButton.drawSmallText(ctx, textRenderer, cs.label(), x + 16, y + 8, 0xFFD8D8EA, 0.80f);

            int bw = Math.round(textRenderer.getWidth(styleName) * 0.76f) + 10;
            int bx = x + w - bw - 6;
            NeonButton.fillSmoothRounded(ctx, bx, y + 5, bw, 12, 3, hovered ? acc : 0xFF262632);
            NeonButton.drawSmallText(ctx, textRenderer, styleName, bx + 5, y + 8, hovered ? 0xFF121218 : accB, 0.76f);

            int prevBoxX = bx - 22;
            NeonButton.fillSmoothRounded(ctx, prevBoxX, y + 3, 16, 16, 3, 0xFF121218);
            NeonButton.drawSmoothRoundedOutline(ctx, prevBoxX, y + 3, 16, 16, 3, 0x22FFFFFF);
            HudManager.renderCrosshairPreview(ctx, prevBoxX + 8.0f, y + 11.0f, st, c.crosshairColor);
        } else if (item instanceof ToggleRow tr) {
            boolean on = tr.getter().getAsBoolean();
            drawRowBulletIcon(ctx, x + 6, y + 5, on ? accB : 0xFF7D7D94);
            NeonButton.drawSmallText(ctx, textRenderer, tr.label(), x + 16, y + 6, on ? 0xFFF2F2FA : 0xFFB8B8CC, 0.82f);

            int rightX = x + w - 15;
            if (tr.onSettings() != null) {
                NeonButton.drawSmallText(ctx, textRenderer, "ПКМ", rightX - 20, y + 6, 0xFF6E6E84, 0.70f);
            }
            int cbY = y + 4;
            if (on) {
                NeonButton.fillSmoothRounded(ctx, rightX, cbY, 10, 10, 2, accB);
                ctx.fill(rightX + 2, cbY + 5, rightX + 3, cbY + 6, 0xFF121218);
                ctx.fill(rightX + 3, cbY + 6, rightX + 4, cbY + 7, 0xFF121218);
                ctx.fill(rightX + 4, cbY + 7, rightX + 5, cbY + 8, 0xFF121218);
                ctx.fill(rightX + 5, cbY + 6, rightX + 6, cbY + 7, 0xFF121218);
                ctx.fill(rightX + 6, cbY + 5, rightX + 7, cbY + 6, 0xFF121218);
                ctx.fill(rightX + 7, cbY + 4, rightX + 8, cbY + 5, 0xFF121218);
            } else {
                NeonButton.fillSmoothRounded(ctx, rightX, cbY, 10, 10, 2, 0xFF282834);
                NeonButton.drawSmoothRoundedOutline(ctx, rightX, cbY, 10, 10, 2, 0x24FFFFFF);
            }
        } else if (item instanceof SliderRow sr) {
            double val = sr.getter().getAsDouble();
            double norm = Math.max(0.0, Math.min(1.0, (val - sr.min()) / Math.max(0.0001, sr.max() - sr.min())));
            String valStr = sr.integer() ? String.valueOf(Math.round(val)) : String.format(Locale.US, "%.2f", val);

            NeonButton.drawSmallText(ctx, textRenderer, sr.label(), x + 7, y + 4, 0xFFD4D4E6, 0.80f);
            int vw = Math.round(textRenderer.getWidth(valStr) * 0.80f);
            NeonButton.drawSmallText(ctx, textRenderer, valStr, x + w - vw - 7, y + 4, accB, 0.80f);

            int barX = x + 7;
            int barY = y + 16;
            int barW = w - 14;
            NeonButton.fillSmoothRounded(ctx, barX, barY, barW, 3, 1, 0xFF2B2B38);
            int fillW = Math.max(3, (int) Math.round(barW * norm));
            NeonButton.fillGradientRounded(ctx, barX, barY, fillW, 3, 1, acc, accB);
            NeonButton.drawCircle(ctx, barX + fillW, barY + 1, 3, 0xFFFFFFFF);
        } else if (item instanceof SwatchRow swr) {
            int cur = swr.getter().getAsInt() & 0x00FFFFFF;
            NeonButton.drawSmallText(ctx, textRenderer, swr.label(), x + 7, y + 6, 0xFFD4D4E6, 0.80f);
            int[] pal = swr.palette();
            int startX = x + w - pal.length * 12 - 5;
            for (int i = 0; i < pal.length; i++) {
                int sx = startX + i * 12;
                int sy = y + 4;
                int col = 0xFF000000 | (pal[i] & 0x00FFFFFF);
                NeonButton.fillSmoothRounded(ctx, sx, sy, 9, 9, 2, col);
                if ((pal[i] & 0x00FFFFFF) == cur) {
                    NeonButton.drawSmoothRoundedOutline(ctx, sx - 1, sy - 1, 11, 11, 3, 0xFFFFFFFF);
                }
            }
        } else if (item instanceof KeybindRow kr) {
            boolean isListening = (listeningKeybind == kr);
            drawRowBulletIcon(ctx, x + 6, y + 5, isListening ? accB : 0xFF7D7D94);
            NeonButton.drawSmallText(ctx, textRenderer, kr.label(), x + 16, y + 6, 0xFFD4D4E6, 0.80f);
            String badge = isListening ? "Нажмите клавишу..." : formatKeyName(kr.getter().getAsInt());
            int bw = Math.round(textRenderer.getWidth(badge) * 0.78f) + 10;
            int bx = x + w - bw - 6;
            NeonButton.fillSmoothRounded(ctx, bx, y + 3, bw, 12, 3, isListening ? accB : (hovered ? acc : 0xFF262632));
            NeonButton.drawSmallText(ctx, textRenderer, badge, bx + 5, y + 6, isListening ? 0xFF121218 : (hovered ? 0xFF121218 : accB), 0.78f);
        } else if (item instanceof ActionRow ar) {
            drawRowBulletIcon(ctx, x + 6, y + 5, accB);
            NeonButton.drawSmallText(ctx, textRenderer, ar.label(), x + 16, y + 6, 0xFFD4D4E6, 0.80f);
            String badge = ar.badgeText().get();
            int bw = Math.round(textRenderer.getWidth(badge) * 0.78f) + 10;
            int bx = x + w - bw - 6;
            NeonButton.fillSmoothRounded(ctx, bx, y + 3, bw, 12, 3, hovered ? acc : 0xFF262632);
            NeonButton.drawSmallText(ctx, textRenderer, badge, bx + 5, y + 6, hovered ? 0xFF121218 : accB, 0.78f);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listeningKeybind != null) {
            if (button == 1 || button == 2) {
                listeningKeybind.setter().accept(0);
                toast("Бинд очищен");
            }
            listeningKeybind = null;
            return true;
        }

        int closeX = winX + winW - 18;
        int closeY = winY + 7;
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            close();
            return true;
        }

        if (mouseX >= winX + 6 && mouseX <= winX + sideW - 6 && mouseY >= winY + winH - 22 && mouseY <= winY + winH - 4) {
            activeCategory = Category.THEMES;
            searchQuery = "";
            subDetailTitle = null;
            subDetailRows.clear();
            scrollOffset = 0;
            return true;
        }

        if (subDetailTitle != null && searchQuery.isBlank() && mouseX >= mainX + 10 && mouseX <= mainX + mainW - 45 && mouseY >= winY + 5 && mouseY <= winY + 21) {
            subDetailTitle = null;
            subDetailRows.clear();
            scrollOffset = 0;
            return true;
        }

        int sx = winX + 7, sy = winY + 23, sw = sideW - 14;
        if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + 13) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        int cy = winY + 41;
        String lastGroup = "";
        for (Category cat : Category.values()) {
            if (!cat.group.equals(lastGroup)) { lastGroup = cat.group; cy += 8; }
            if (mouseX >= winX + 6 && mouseX <= winX + sideW - 6 && mouseY >= cy && mouseY <= cy + 12) {
                activeCategory = cat;
                searchQuery = "";
                subDetailTitle = null;
                subDetailRows.clear();
                scrollOffset = 0;
                return true;
            }
            cy += 13;
        }

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            ArtemConfig c = ArtemConfig.get();
            int ry = listY - scrollOffset;
            for (RowItem item : visibleRows(c)) {
                int rh = rowHeight(item);
                if (mouseY >= ry && mouseY <= ry + rh) {
                    if (item instanceof CrosshairStyleRow) {
                        int delta = (button == 1) ? -1 : 1;
                        c.crosshairStyle = Math.floorMod(c.crosshairStyle + delta, HudManager.CROSSHAIR_STYLE_NAMES.length);
                        c.save();
                        return true;
                    } else if (item instanceof ToggleRow tr) {
                        if (button == 1 && tr.onSettings() != null) tr.onSettings().run();
                        else tr.toggler().run();
                        return true;
                    } else if (item instanceof SliderRow sr) {
                        draggingSlider = sr;
                        draggingSliderX = listX + 7;
                        draggingSliderW = listW - 14;
                        updateSlider(sr, mouseX);
                        return true;
                    } else if (item instanceof SwatchRow swr) {
                        int[] pal = swr.palette();
                        int startX = listX + listW - pal.length * 12 - 5;
                        for (int i = 0; i < pal.length; i++) {
                            int px = startX + i * 12;
                            if (mouseX >= px - 1 && mouseX <= px + 10) {
                                swr.setter().accept(pal[i]);
                                return true;
                            }
                        }
                        int cur = swr.getter().getAsInt() & 0x00FFFFFF;
                        int nextIdx = 0;
                        for (int i = 0; i < pal.length; i++) {
                            if ((pal[i] & 0x00FFFFFF) == cur) { nextIdx = (i + 1) % pal.length; break; }
                        }
                        swr.setter().accept(pal[nextIdx]);
                        return true;
                    } else if (item instanceof KeybindRow kr) {
                        if (button == 1) {
                            kr.setter().accept(0);
                            toast("Бинд очищен");
                        } else {
                            listeningKeybind = kr;
                        }
                        return true;
                    } else if (item instanceof ActionRow ar) {
                        ar.action().run();
                        return true;
                    }
                }
                ry += rh + 3;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateSlider(SliderRow sr, double mouseX) {
        double norm = Math.max(0.0, Math.min(1.0, (mouseX - draggingSliderX) / Math.max(1.0, draggingSliderW)));
        double val = sr.min() + norm * (sr.max() - sr.min());
        if (sr.integer()) val = Math.round(val);
        sr.setter().accept(val);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            updateSlider(draggingSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(verticalAmount * 14)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (listeningKeybind != null) return true;
        if (searchFocused && chr >= 32) {
            searchQuery += chr;
            scrollOffset = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningKeybind != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                listeningKeybind.setter().accept(0);
                toast("Бинд очищен");
            } else {
                listeningKeybind.setter().accept(keyCode);
                toast("Назначено: " + formatKeyName(keyCode));
            }
            listeningKeybind = null;
            return true;
        }

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                scrollOffset = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) { searchQuery = ""; return true; }
                searchFocused = false;
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && subDetailTitle != null) {
            subDetailTitle = null;
            subDetailRows.clear();
            scrollOffset = 0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private void drawStarLogo(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x + 3, y, x + 5, y + 8, col);
        ctx.fill(x, y + 3, x + 8, y + 5, col);
        ctx.fill(x + 1, y + 1, x + 7, y + 7, (col & 0x00FFFFFF) | 0x66000000);
        ctx.fill(x + 3, y + 3, x + 5, y + 5, 0xFFFFFFFF);
    }

    private void drawSearchIcon(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x + 1, y, x + 4, y + 1, col);
        ctx.fill(x, y + 1, x + 1, y + 4, col);
        ctx.fill(x + 4, y + 1, x + 5, y + 4, col);
        ctx.fill(x + 1, y + 4, x + 4, y + 5, col);
        ctx.fill(x + 4, y + 4, x + 6, y + 6, col);
    }

    private void drawHomeIcon(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x + 3, y, x + 4, y + 1, col);
        ctx.fill(x + 2, y + 1, x + 5, y + 2, col);
        ctx.fill(x + 1, y + 2, x + 6, y + 3, col);
        ctx.fill(x + 1, y + 3, x + 6, y + 6, col);
        ctx.fill(x + 3, y + 4, x + 4, y + 6, 0xFF16161C);
    }

    private void drawGearIcon(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x + 2, y, x + 5, y + 7, col);
        ctx.fill(x, y + 2, x + 7, y + 5, col);
        ctx.fill(x + 2, y + 2, x + 5, y + 5, 0xFF111116);
    }

    private void drawRowBulletIcon(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x + 1, y, x + 6, y + 1, col);
        ctx.fill(x, y + 1, x + 1, y + 6, col);
        ctx.fill(x + 6, y + 1, x + 7, y + 6, col);
        ctx.fill(x + 1, y + 6, x + 6, y + 7, col);
        ctx.fill(x + 2, y + 2, x + 5, y + 5, (col & 0x00FFFFFF) | 0x55000000);
    }

    private void drawCategoryIcon(DrawContext ctx, Category cat, int x, int y, int col) {
        switch (cat) {
            case MAIN -> {
                ctx.fill(x + 1, y, x + 6, y + 1, col);
                ctx.fill(x, y + 1, x + 1, y + 6, col);
                ctx.fill(x + 6, y + 1, x + 7, y + 6, col);
                ctx.fill(x + 1, y + 6, x + 6, y + 7, col);
                ctx.fill(x + 3, y + 2, x + 4, y + 5, col);
                ctx.fill(x + 2, y + 3, x + 5, y + 4, col);
            }
            case HELPERS -> {
                ctx.fill(x + 2, y, x + 5, y + 1, col);
                ctx.fill(x + 1, y + 1, x + 6, y + 5, col);
                ctx.fill(x + 2, y + 5, x + 5, y + 7, col);
            }
            case AUCTION -> {
                ctx.fill(x, y + 2, x + 7, y + 7, col);
                ctx.fill(x + 2, y, x + 5, y + 2, col);
                ctx.fill(x + 3, y + 4, x + 4, y + 5, 0xFF111116);
            }
            case OPTIMIZE -> {
                ctx.fill(x + 3, y, x + 6, y + 2, col);
                ctx.fill(x + 1, y + 2, x + 5, y + 4, col);
                ctx.fill(x + 2, y + 4, x + 4, y + 7, col);
            }
            case VISUALS -> {
                ctx.fill(x + 1, y + 1, x + 6, y + 2, col);
                ctx.fill(x, y + 2, x + 7, y + 5, col);
                ctx.fill(x + 1, y + 5, x + 6, y + 6, col);
                ctx.fill(x + 3, y + 3, x + 4, y + 4, 0xFF111116);
            }
            case HUD -> {
                ctx.fill(x, y + 1, x + 7, y + 6, col);
                ctx.fill(x + 1, y + 2, x + 6, y + 5, 0xFF111116);
                ctx.fill(x + 2, y + 3, x + 5, y + 4, col);
            }
            case CONFIGS -> {
                ctx.fill(x, y + 1, x + 3, y + 2, col);
                ctx.fill(x, y + 2, x + 7, y + 6, col);
            }
            case FRIENDS -> {
                ctx.fill(x + 2, y + 1, x + 5, y + 3, col);
                ctx.fill(x + 1, y + 4, x + 6, y + 7, col);
            }
            case EVENTS -> {
                ctx.fill(x + 1, y + 1, x + 6, y + 2, col);
                ctx.fill(x + 1, y + 2, x + 6, y + 6, col);
                ctx.fill(x + 2, y + 3, x + 5, y + 5, 0xFF111116);
            }
            case WAYPOINTS -> {
                ctx.fill(x + 2, y, x + 5, y + 4, col);
                ctx.fill(x + 3, y + 4, x + 4, y + 7, col);
            }
            case THEMES -> {
                ctx.fill(x + 1, y + 1, x + 6, y + 6, col);
                ctx.fill(x + 2, y + 2, x + 3, y + 3, 0xFF111116);
                ctx.fill(x + 4, y + 4, x + 5, y + 5, 0xFF111116);
            }
        }
    }
}
