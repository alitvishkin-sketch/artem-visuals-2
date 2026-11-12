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

    private record ToggleRow(String label, Category category, BooleanSupplier getter, Runnable toggler, Runnable onSettings) implements RowItem {}
    private record SliderRow(String label, Category category, double min, double max, boolean integer, DoubleSupplier getter, DoubleConsumer setter) implements RowItem {}
    private record SwatchRow(String label, Category category, int[] palette, IntSupplier getter, IntConsumer setter) implements RowItem {}
    private record ActionRow(String label, Category category, Supplier<String> badgeText, Runnable action) implements RowItem {}

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

        // Визуалы
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
        all.add(new ToggleRow("Hit Particles (Частицы удара)", Category.VISUALS, () -> c.hitParticles, () -> { c.hitParticles = !c.hitParticles; c.save(); },
                () -> openSubDetails("Hit Particles", List.of(
                        new SliderRow("Количество частиц", Category.VISUALS, 4, 32, true, () -> c.hitParticlesCount, v -> { c.hitParticlesCount = (int) v; c.save(); })
                ))));
        all.add(new ToggleRow("Crit Sparks (Искры критов)", Category.VISUALS, () -> c.critSparks, () -> { c.critSparks = !c.critSparks; c.save(); }, null));
        all.add(new ToggleRow("Damage Indicator", Category.VISUALS, () -> c.damageIndicator, () -> { c.damageIndicator = !c.damageIndicator; c.save(); }, null));
        all.add(new ToggleRow("Projectile Trails (След стрел)", Category.VISUALS, () -> c.projectileTrails, () -> { c.projectileTrails = !c.projectileTrails; c.save(); }, null));
        all.add(new ToggleRow("Block Overlay (Обводка блока)", Category.VISUALS, () -> c.blockOverlay, () -> { c.blockOverlay = !c.blockOverlay; c.save(); },
                () -> openSubDetails("Block Overlay", List.of(
                        new SwatchRow("Цвет обводки", Category.VISUALS, ACCENT_PALETTE, () -> c.blockOverlayColor, v -> { c.blockOverlayColor = v; c.save(); }),
                        new SliderRow("Прозрачность", Category.VISUALS, 0.2, 1.0, false, () -> c.blockOverlayAlpha, v -> { c.blockOverlayAlpha = (float) v; c.save(); })
                ))));
        all.add(new ToggleRow("Player ESP", Category.VISUALS, () -> c.esp, () -> { c.esp = !c.esp; c.save(); },
                () -> openSubDetails("Player ESP", List.of(
                        new SwatchRow("Цвет ESP", Category.VISUALS, ACCENT_PALETTE, () -> c.espColor, v -> { c.espColor = v; c.save(); }),
                        new ToggleRow("Пульсация", Category.VISUALS, () -> c.espPulse, () -> { c.espPulse = !c.espPulse; c.save(); }, null)
                ))));
        all.add(new ToggleRow("Chams", Category.VISUALS, () -> c.chams, () -> { c.chams = !c.chams; c.save(); }, null));
        all.add(new ToggleRow("Tracers", Category.VISUALS, () -> c.tracers, () -> { c.tracers = !c.tracers; c.save(); }, null));
        all.add(new ToggleRow("Item ESP", Category.VISUALS, () -> c.itemEsp, () -> { c.itemEsp = !c.itemEsp; c.save(); }, null));

        // Интерфейс
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

        // Меч и Камера
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

        // Оптимизация
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

        // Мир и Время
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

        // Конфиги
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

        // Темы
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

    private List<RowItem> visibleRows(ArtemConfig c) {
        if (!searchQuery.isBlank()) {
            String q = searchQuery.toLowerCase(Locale.ROOT).trim();
            List<RowItem> found = new ArrayList<>();
            for (RowItem item : buildAllRows(c)) {
                if (rowLabel(item).toLowerCase(Locale.ROOT).contains(q)) found.add(item);
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
        if (r instanceof ToggleRow t) return t.label();
        if (r instanceof SliderRow s) return s.label();
        if (r instanceof SwatchRow w) return w.label();
        if (r instanceof ActionRow a) return a.label();
        return "";
    }

    private Category rowCategory(RowItem r) {
        if (r instanceof ToggleRow t) return t.category();
        if (r instanceof SliderRow s) return s.category();
        if (r instanceof SwatchRow w) return w.category();
        if (r instanceof ActionRow a) return a.category();
        return Category.VISUALS;
    }

    private int rowHeight(RowItem r) {
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
        int ly = winY + 11;
        drawStarLogo(ctx, lx, ly, accB);
        NeonButton.drawSmallText(ctx, textRenderer, "Artem", lx + 12, ly - 2, 0xFFF4F4FC, 0.95f);

        int sx = winX + 7;
        int sy = winY + 24;
        int sw = sideW - 14;
        NeonButton.fillSmoothRounded(ctx, sx, sy, sw, 14, 4, searchFocused ? 0xFF1E1E28 : 0xFF181820);
        NeonButton.drawSmoothRoundedOutline(ctx, sx, sy, sw, 14, 4, searchFocused ? ((accB & 0x00FFFFFF) | 0x88000000) : 0x18FFFFFF);
        drawSearchIcon(ctx, sx + 5, sy + 4, 0xFF7A7A90);

        String shownSearch = searchQuery.isEmpty()
                ? (searchFocused ? "|" : "Поиск...")
                : (searchQuery + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : ""));
        NeonButton.drawSmallText(ctx, textRenderer, shownSearch, sx + 14, sy + 4, searchQuery.isEmpty() ? 0xFF6E6E84 : 0xFFEAEAF6, 0.78f);

        int cy = winY + 44;
        String lastGroup = "";
        for (Category cat : Category.values()) {
            if (!cat.group.equals(lastGroup)) {
                lastGroup = cat.group;
                NeonButton.drawSmallText(ctx, textRenderer, lastGroup, winX + 9, cy, 0xFF646478, 0.72f);
                cy += 9;
            }
            boolean selected = searchQuery.isBlank() && activeCategory == cat;
            boolean hovered = mouseX >= winX + 6 && mouseX <= winX + sideW - 6 && mouseY >= cy && mouseY <= cy + 14;
            if (selected) NeonButton.fillSmoothRounded(ctx, winX + 6, cy, sideW - 12, 14, 4, 0xFF21212B);
            else if (hovered) NeonButton.fillSmoothRounded(ctx, winX + 6, cy, sideW - 12, 14, 4, 0x881C1C24);

            drawCategoryIcon(ctx, cat, winX + 10, cy + 3, selected ? accB : (hovered ? 0xFFB8B8CE : 0xFF7D7D94));
            NeonButton.drawSmallText(ctx, textRenderer, cat.title, winX + 21, cy + 4, selected ? 0xFFF4F4FC : (hovered ? 0xFFD4D4E6 : 0xFF9494AA), 0.80f);

            if (cat.badge && sideW >= 96) {
                int bx = winX + sideW - 29;
                NeonButton.fillSmoothRounded(ctx, bx, cy + 3, 20, 8, 2, 0xFF262632);
                NeonButton.drawSmallText(ctx, textRenderer, "FPS", bx + 3, cy + 4, 0xFF9E9EB6, 0.65f);
            }
            cy += 15;
        }

        int py = winY + winH - 24;
        ctx.fill(winX + 7, py - 3, winX + sideW - 7, py - 2, 0x14FFFFFF);
        MinecraftClient mc = MinecraftClient.getInstance();
        String nick = (mc != null && mc.player != null) ? mc.player.getName().getString() : "Player";
        if (nick.length() > 11) nick = nick.substring(0, 10) + "…";

        int ax = winX + 8;
        int ay = py + 1;
        NeonButton.fillSmoothRounded(ctx, ax, ay, 15, 15, 3, 0xFF23232E);
        if (mc != null && mc.player instanceof AbstractClientPlayerEntity p) {
            PlayerSkinDrawer.draw(ctx, p.getSkinTextures().texture(), ax + 1, ay + 1, 13);
        } else {
            NeonButton.drawCircle(ctx, ax + 7, ay + 7, 4, accB);
        }

        NeonButton.drawSmallText(ctx, textRenderer, nick, ax + 18, ay + 1, 0xFFEAEAF6, 0.78f);
        NeonButton.drawSmallText(ctx, textRenderer, "Fabric 1.21", ax + 18, ay + 9, 0xFF6E6E84, 0.68f);
        drawGearIcon(ctx, winX + sideW - 16, ay + 3, 0xFF7D7D94);
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

        int pillY = winY + 28;
        String pillText = (subDetailTitle != null && searchQuery.isBlank()) ? "Параметры модуля" : "Функции";
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
        NeonButton.fillSmoothRounded(ctx, x, y, w, h, 4, hovered ? 0xFF22222C : 0xFF1C1C24);
        NeonButton.drawSmoothRoundedOutline(ctx, x, y, w, h, 4, hovered ? 0x20FFFFFF : 0x10FFFFFF);

        if (item instanceof ToggleRow tr) {
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
                NeonButton.drawSmoothRoundedOutline(ctx, rightX, cbY, 10, 10, 2, 0x22FFFFFF);
            }
        } else if (item instanceof SliderRow sr) {
            double val = sr.getter().getAsDouble();
            double norm = Math.max(0.0, Math.min(1.0, (val - sr.min()) / Math.max(0.0001, sr.max() - sr.min())));
            String valStr = sr.integer() ? String.valueOf((int) Math.round(val)) : String.format(Locale.US, "%.2f", val);

            drawRowBulletIcon(ctx, x + 6, y + 5, accB);
            NeonButton.drawSmallText(ctx, textRenderer, sr.label(), x + 16, y + 5, 0xFFD8D8EA, 0.80f);
            int vw = Math.round(textRenderer.getWidth(valStr) * 0.80f);
            NeonButton.drawSmallText(ctx, textRenderer, valStr, x + w - vw - 7, y + 5, 0xFFB8B8CE, 0.80f);

            int trackX = x + 7;
            int trackY = y + 17;
            int trackW = w - 14;
            NeonButton.fillSmoothRounded(ctx, trackX, trackY, trackW, 2, 1, 0xFF2B2B38);
            int fillW = Math.max(2, (int) Math.round(trackW * norm));
            NeonButton.fillSmoothRounded(ctx, trackX, trackY, fillW, 2, 1, accB);
            NeonButton.drawCircle(ctx, trackX + fillW, trackY + 1, 2, 0xFFF4F4FC);
        } else if (item instanceof SwatchRow sw) {
            drawRowBulletIcon(ctx, x + 6, y + 5, accB);
            NeonButton.drawSmallText(ctx, textRenderer, sw.label(), x + 16, y + 6, 0xFFD8D8EA, 0.80f);

            int cur = sw.getter().getAsInt() & 0x00FFFFFF;
            int sx = x + w - sw.palette().length * 11 - 5;
            for (int col : sw.palette()) {
                NeonButton.fillSmoothRounded(ctx, sx, y + 4, 9, 9, 2, 0xFF000000 | (col & 0x00FFFFFF));
                if ((col & 0x00FFFFFF) == cur) {
                    NeonButton.drawSmoothRoundedOutline(ctx, sx - 1, y + 3, 11, 11, 3, 0xFFFFFFFF);
                }
                sx += 11;
            }
        } else if (item instanceof ActionRow ar) {
            drawRowBulletIcon(ctx, x + 6, y + 5, accB);
            NeonButton.drawSmallText(ctx, textRenderer, ar.label(), x + 16, y + 6, 0xFFD8D8EA, 0.80f);

            String badge = ar.badgeText().get();
            int bw = Math.round(textRenderer.getWidth(badge) * 0.78f) + 10;
            int bx = x + w - bw - 6;
            NeonButton.fillSmoothRounded(ctx, bx, y + 3, bw, 12, 3, hovered ? acc : 0xFF262632);
            NeonButton.drawSmallText(ctx, textRenderer, badge, bx + 5, y + 6, hovered ? 0xFF121218 : accB, 0.78f);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ArtemConfig c = ArtemConfig.get();

        int closeX = winX + winW - 18;
        int closeY = winY + 7;
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            close();
            return true;
        }

        int sx = winX + 7;
        int sy = winY + 24;
        if (mouseX >= sx && mouseX <= sx + sideW - 14 && mouseY >= sy && mouseY <= sy + 14) {
            searchFocused = true;
            if (button == 1) searchQuery = "";
            return true;
        } else {
            searchFocused = false;
        }

        int py = winY + winH - 24;
        if (mouseX >= winX + sideW - 20 && mouseX <= winX + sideW - 4 && mouseY >= py && mouseY <= winY + winH - 4) {
            activeCategory = Category.THEMES;
            searchQuery = "";
            subDetailTitle = null;
            scrollOffset = 0;
            return true;
        }

        int cy = winY + 44;
        String lastGroup = "";
        for (Category cat : Category.values()) {
            if (!cat.group.equals(lastGroup)) {
                lastGroup = cat.group;
                cy += 9;
            }
            if (mouseX >= winX + 6 && mouseX <= winX + sideW - 6 && mouseY >= cy && mouseY <= cy + 14) {
                activeCategory = cat;
                searchQuery = "";
                subDetailTitle = null;
                subDetailRows.clear();
                scrollOffset = 0;
                return true;
            }
            cy += 15;
        }

        if (subDetailTitle != null && mouseX >= mainX + 8 && mouseX <= winX + winW - 26 && mouseY >= winY + 4 && mouseY <= winY + 23) {
            subDetailTitle = null;
            subDetailRows.clear();
            scrollOffset = 0;
            return true;
        }

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            List<RowItem> rows = visibleRows(c);
            int ry = listY - scrollOffset;
            for (RowItem item : rows) {
                int rh = rowHeight(item);
                if (mouseY >= ry && mouseY <= ry + rh) {
                    if (item instanceof ToggleRow tr) {
                        if (button == 1 && tr.onSettings() != null) tr.onSettings().run();
                        else tr.toggler().run();
                        return true;
                    } else if (item instanceof SliderRow sr) {
                        draggingSlider = sr;
                        draggingSliderX = listX + 7;
                        draggingSliderW = listW - 14;
                        updateSliderFromMouse(sr, mouseX);
                        return true;
                    } else if (item instanceof SwatchRow swRow) {
                        int swX = listX + listW - swRow.palette().length * 11 - 5;
                        for (int col : swRow.palette()) {
                            if (mouseX >= swX - 1 && mouseX <= swX + 10) {
                                swRow.setter().accept(col);
                                return true;
                            }
                            swX += 11;
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

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            updateSliderFromMouse(draggingSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateSliderFromMouse(SliderRow sr, double mouseX) {
        double norm = Math.max(0.0, Math.min(1.0, (mouseX - draggingSliderX) / Math.max(1.0, draggingSliderW)));
        double val = sr.min() + norm * (sr.max() - sr.min());
        sr.setter().accept(sr.integer() ? Math.round(val) : Math.round(val * 100.0) / 100.0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 16)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && chr >= 32 && searchQuery.length() < 24) {
            searchQuery += chr;
            scrollOffset = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                scrollOffset = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && subDetailTitle != null) {
            subDetailTitle = null;
            subDetailRows.clear();
            scrollOffset = 0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawStarLogo(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x + 3, y, x + 4, y + 7, col);
        ctx.fill(x, y + 3, x + 7, y + 4, col);
        ctx.fill(x + 1, y + 1, x + 2, y + 2, col);
        ctx.fill(x + 5, y + 1, x + 6, y + 2, col);
        ctx.fill(x + 1, y + 5, x + 2, y + 6, col);
        ctx.fill(x + 5, y + 5, x + 6, y + 6, col);
    }

    private void drawSearchIcon(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x + 1, y, x + 4, y + 1, col);
        ctx.fill(x + 1, y + 4, x + 4, y + 5, col);
        ctx.fill(x, y + 1, x + 1, y + 4, col);
        ctx.fill(x + 4, y + 1, x + 5, y + 4, col);
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
        ctx.fill(x + 2, y + 1, x + 5, y + 6, col);
        ctx.fill(x + 1, y + 2, x + 6, y + 5, col);
        ctx.fill(x + 3, y + 3, x + 4, y + 4, 0xFF111116);
    }

    private void drawRowBulletIcon(DrawContext ctx, int x, int y, int col) {
        ctx.fill(x + 1, y + 1, x + 6, y + 6, (col & 0x00FFFFFF) | 0x33000000);
        ctx.fill(x + 2, y + 2, x + 5, y + 5, col);
    }

    private void drawCategoryIcon(DrawContext ctx, Category cat, int x, int y, int col) {
        switch (cat) {
            case VISUALS -> {
                ctx.fill(x + 1, y + 2, x + 6, y + 5, col);
                ctx.fill(x + 3, y + 3, x + 4, y + 4, 0xFF111116);
            }
            case HUD -> {
                ctx.fill(x, y + 1, x + 7, y + 2, col);
                ctx.fill(x, y + 5, x + 7, y + 6, col);
                ctx.fill(x, y + 1, x + 1, y + 6, col);
                ctx.fill(x + 6, y + 1, x + 7, y + 6, col);
            }
            case SWORD -> {
                ctx.fill(x + 5, y, x + 7, y + 2, col);
                ctx.fill(x + 3, y + 2, x + 5, y + 4, col);
                ctx.fill(x + 1, y + 4, x + 3, y + 6, col);
            }
            case OPTIMIZE -> drawGearIcon(ctx, x, y, col);
            case WORLD -> NeonButton.drawCircle(ctx, x + 3, y + 3, 3, col);
            case CONFIGS -> {
                ctx.fill(x + 1, y, x + 6, y + 7, col);
                ctx.fill(x + 2, y + 2, x + 5, y + 3, 0xFF111116);
            }
            case THEMES -> {
                ctx.fill(x + 1, y + 1, x + 6, y + 6, col);
                ctx.fill(x + 2, y + 2, x + 3, y + 3, 0xFF111116);
                ctx.fill(x + 4, y + 4, x + 5, y + 5, 0xFF111116);
            }
        }
    }

    @Override
    public void close() {
        ArtemConfig.get().save();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
