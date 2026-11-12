package com.artem.visuals.gui;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.hud.HudManager;
import com.artem.visuals.optimize.Optimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * ClickGUI в минималистичном стиле (2 колонки карточек + pill-переключатели + выдвижная панель настроек и HSB-палитра)
 * в фирменной неоновой тёмной палитре ARTEM VISUALS 3.0.
 */
public class ArtemScreen extends Screen {
    private enum Tab {
        VISUALS("Visuals"),
        HUD("HUD"),
        UTILITIES("Utilities");

        final String title;
        Tab(String title) { this.title = title; }
    }

    private static final class SubToggle {
        final String label;
        final BooleanSupplier getter;
        final Runnable toggler;

        SubToggle(String label, BooleanSupplier getter, Runnable toggler) {
            this.label = label;
            this.getter = getter;
            this.toggler = toggler;
        }
    }

    private static final class SubSlider {
        final String label;
        final float min, max, step;
        final java.util.function.Supplier<Float> getter;
        final Consumer<Float> setter;
        final boolean isInt;

        SubSlider(String label, float min, float max, float step, boolean isInt,
                  java.util.function.Supplier<Float> getter, Consumer<Float> setter) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.isInt = isInt;
            this.getter = getter;
            this.setter = setter;
        }
    }

    private static final class ModuleCard {
        final String id;
        final String name;
        final BooleanSupplier enabled;
        final Runnable onToggle;
        final List<SubToggle> subToggles = new ArrayList<>();
        final List<SubSlider> subSliders = new ArrayList<>();
        final IntSupplier colorGetter;
        final IntConsumer colorSetter;

        ModuleCard(String id, String name, BooleanSupplier enabled, Runnable onToggle,
                   IntSupplier colorGetter, IntConsumer colorSetter) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.onToggle = onToggle;
            this.colorGetter = colorGetter;
            this.colorSetter = colorSetter;
        }

        boolean hasSettings() {
            return !subToggles.isEmpty() || !subSliders.isEmpty() || colorGetter != null;
        }
    }

    private final Screen parent;
    private Tab currentTab = Tab.VISUALS;
    private int scrollOffset = 0;

    // Выдвижная панель настроек справа
    private ModuleCard activeSettingsCard = null;
    private boolean themeDrawerOpen = false;
    private int activeDrawerY = 0;

    // Состояние HSB-палитры
    private boolean colorPickerExpanded = true;
    private float pickerHue = 0.55f;
    private float pickerSat = 0.85f;
    private float pickerBri = 1.0f;
    private int syncedPickerColor = 0;
    private boolean draggingSatBri = false;
    private boolean draggingHue = false;
    private SubSlider draggingSlider = null;

    // Кэш 2D-градиента HSB
    private float cachedHue = -1f;
    private final int[][] cachedGradient = new int[36][64];

    // Геометрия окна
    private int winX, winY, winW, winH;
    private int drawerX, drawerY, drawerW, drawerH;

    public ArtemScreen() {
        this(null);
    }

    public ArtemScreen(Screen parent) {
        super(Text.literal("ARTEM VISUALS"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        } else {
            super.close();
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x6208080E);
    }

    private List<ModuleCard> buildCardsForTab(Tab tab) {
        ArtemConfig c = ArtemConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        List<ModuleCard> list = new ArrayList<>();

        if (tab == Tab.VISUALS) {
            // 1. Full Bright
            ModuleCard fullBright = new ModuleCard("fullbright", "Full Bright",
                    () -> c.softLighting,
                    () -> { c.softLighting = !c.softLighting; Optimizer.applyLighting(mc); c.save(); },
                    null, null);
            list.add(fullBright);

            // 2. Jump Circles
            ModuleCard jumpCircles = new ModuleCard("jump_circle", "Jump Circles",
                    () -> c.jumpCircle,
                    () -> { c.jumpCircle = !c.jumpCircle; c.save(); },
                    () -> c.jumpCircleColor,
                    col -> { c.jumpCircleColor = col; c.save(); });
            jumpCircles.subSliders.add(new SubSlider("Радиус круга", 0.6f, 2.4f, 0.1f, false,
                    () -> c.jumpCircleRadius, v -> { c.jumpCircleRadius = v; c.save(); }));
            list.add(jumpCircles);

            // 3. Render Tweaks
            ModuleCard renderTweaks = new ModuleCard("render_tweaks", "Render Tweaks",
                    () -> c.renderTweaks,
                    () -> { c.renderTweaks = !c.renderTweaks; c.save(); },
                    null, null);
            renderTweaks.subToggles.add(new SubToggle("Эффекты мира", () -> c.masterEnabled,
                    () -> { c.masterEnabled = !c.masterEnabled; c.save(); }));
            renderTweaks.subToggles.add(new SubToggle("Частицы листвы", () -> c.leafParticles,
                    () -> { c.leafParticles = !c.leafParticles; c.save(); }));
            renderTweaks.subToggles.add(new SubToggle("Блики воды", () -> c.waterSparkles,
                    () -> { c.waterSparkles = !c.waterSparkles; c.save(); }));
            renderTweaks.subToggles.add(new SubToggle("Светлячки", () -> c.fireflies,
                    () -> { c.fireflies = !c.fireflies; c.save(); }));
            list.add(renderTweaks);

            // 4. Trails
            ModuleCard trails = new ModuleCard("trails", "Trails",
                    () -> c.trails,
                    () -> { c.trails = !c.trails; c.save(); },
                    () -> c.trailColor,
                    col -> { c.trailColor = col; c.save(); });
            trails.subSliders.add(new SubSlider("Длина шлейфа", 10f, 80f, 2f, true,
                    () -> (float) c.trailLength, v -> { c.trailLength = Math.round(v); c.save(); }));
            list.add(trails);

            // 5. Target ESP
            ModuleCard targetEsp = new ModuleCard("target_esp", "Target ESP",
                    () -> c.targetEsp,
                    () -> { c.targetEsp = !c.targetEsp; c.save(); },
                    () -> c.targetEspColor,
                    col -> { c.targetEspColor = col; c.save(); });
            targetEsp.subSliders.add(new SubSlider("Скорость кольца", 0.4f, 2.5f, 0.1f, false,
                    () -> c.targetEspSpeed, v -> { c.targetEspSpeed = v; c.save(); }));
            list.add(targetEsp);

            // 6. World Customizer
            ModuleCard worldCust = new ModuleCard("world_cust", "World Customizer",
                    () -> c.worldCustomizer,
                    () -> { c.worldCustomizer = !c.worldCustomizer; c.save(); },
                    () -> NeonButton.ACCENT,
                    ArtemScreen::applyClientAccent);
            worldCust.subToggles.add(new SubToggle("День (6:00)", () -> c.worldTimeMode == 1,
                    () -> { c.worldTimeMode = (c.worldTimeMode == 1 ? 0 : 1); c.save(); }));
            worldCust.subToggles.add(new SubToggle("Закат (12:70)", () -> c.worldTimeMode == 2,
                    () -> { c.worldTimeMode = (c.worldTimeMode == 2 ? 0 : 2); c.save(); }));
            worldCust.subToggles.add(new SubToggle("Ночь (18:00)", () -> c.worldTimeMode == 3,
                    () -> { c.worldTimeMode = (c.worldTimeMode == 3 ? 0 : 3); c.save(); }));
            worldCust.subToggles.add(new SubToggle("Цветокоррекция", () -> c.colorCorrection,
                    () -> { c.colorCorrection = !c.colorCorrection; c.save(); }));
            list.add(worldCust);

            // 7. NameTags
            ModuleCard nameTags = new ModuleCard("nametags", "NameTags",
                    () -> c.nameTags,
                    () -> { c.nameTags = !c.nameTags; c.save(); },
                    null, null);
            nameTags.subToggles.add(new SubToggle("Показывать HP", () -> c.nameTagsHealth,
                    () -> { c.nameTagsHealth = !c.nameTagsHealth; c.save(); }));
            nameTags.subToggles.add(new SubToggle("Дистанция", () -> c.nameTagsDistance,
                    () -> { c.nameTagsDistance = !c.nameTagsDistance; c.save(); }));
            list.add(nameTags);

            // 8. Hit Particles
            ModuleCard hitParticles = new ModuleCard("hit_particles", "Hit Particles",
                    () -> c.hitParticles,
                    () -> { c.hitParticles = !c.hitParticles; c.save(); },
                    null, null);
            hitParticles.subToggles.add(new SubToggle("Индикатор урона", () -> c.damageIndicator,
                    () -> { c.damageIndicator = !c.damageIndicator; c.save(); }));
            hitParticles.subToggles.add(new SubToggle("Ломание блоков", () -> c.blockBreakEffects,
                    () -> { c.blockBreakEffects = !c.blockBreakEffects; c.save(); }));
            hitParticles.subSliders.add(new SubSlider("Кол-во частиц", 4f, 24f, 1f, true,
                    () -> (float) c.hitParticlesCount, v -> { c.hitParticlesCount = Math.round(v); c.save(); }));
            list.add(hitParticles);

            // 9. Player ESP
            ModuleCard esp = new ModuleCard("esp", "Player ESP",
                    () -> c.esp,
                    () -> { c.esp = !c.esp; c.save(); },
                    () -> c.espColor,
                    col -> { c.espColor = col; c.save(); });
            esp.subToggles.add(new SubToggle("Пульсация", () -> c.espPulse,
                    () -> { c.espPulse = !c.espPulse; c.save(); }));
            esp.subToggles.add(new SubToggle("Угловой стиль", () -> c.espStyle == 1,
                    () -> { c.espStyle = (c.espStyle == 1 ? 0 : 1); c.save(); }));
            esp.subToggles.add(new SubToggle("Двойной неон", () -> c.espStyle == 2,
                    () -> { c.espStyle = (c.espStyle == 2 ? 0 : 2); c.save(); }));
            list.add(esp);

            // 10. Block Overlay
            ModuleCard blockOverlay = new ModuleCard("block_overlay", "Block Overlay",
                    () -> c.blockOverlay,
                    () -> { c.blockOverlay = !c.blockOverlay; c.save(); },
                    () -> c.blockOverlayColor,
                    col -> { c.blockOverlayColor = col; c.save(); });
            list.add(blockOverlay);

            // 11. Custom Crosshair
            ModuleCard crosshair = new ModuleCard("crosshair", "Custom Crosshair",
                    () -> c.customCrosshair,
                    () -> { c.customCrosshair = !c.customCrosshair; c.save(); },
                    () -> c.crosshairColor,
                    col -> { c.crosshairColor = col; c.save(); });
            crosshair.subToggles.add(new SubToggle("Обводка", () -> c.crosshairOutline,
                    () -> { c.crosshairOutline = !c.crosshairOutline; c.save(); }));
            crosshair.subToggles.add(new SubToggle("Динамический", () -> c.crosshairDynamic,
                    () -> { c.crosshairDynamic = !c.crosshairDynamic; c.save(); }));
            crosshair.subSliders.add(new SubSlider("Размер", 2f, 12f, 1f, true,
                    () -> (float) c.crosshairSize, v -> { c.crosshairSize = Math.round(v); c.save(); }));
            crosshair.subSliders.add(new SubSlider("Зазор", 0f, 8f, 1f, true,
                    () -> (float) c.crosshairGap, v -> { c.crosshairGap = Math.round(v); c.save(); }));
            list.add(crosshair);

            // 12. Nimb (Halo)
            ModuleCard nimb = new ModuleCard("nimb", "Nimb Halo",
                    () -> c.nimb,
                    () -> { c.nimb = !c.nimb; c.save(); },
                    () -> c.nimbColor,
                    col -> { c.nimbColor = col; c.save(); });
            nimb.subSliders.add(new SubSlider("Размер нимба", 0.25f, 0.85f, 0.05f, false,
                    () -> c.nimbSize, v -> { c.nimbSize = v; c.save(); }));
            list.add(nimb);

            // 13. Tracers
            ModuleCard tracers = new ModuleCard("tracers", "Tracers",
                    () -> c.tracers,
                    () -> { c.tracers = !c.tracers; c.save(); },
                    () -> c.tracersColor,
                    col -> { c.tracersColor = col; c.save(); });
            list.add(tracers);

            // 14. Chams
            ModuleCard chams = new ModuleCard("chams", "Chams",
                    () -> c.chams,
                    () -> { c.chams = !c.chams; c.save(); },
                    () -> c.chamsColor,
                    col -> { c.chamsColor = col; c.save(); });
            list.add(chams);

            // 15. Item ESP
            ModuleCard itemEsp = new ModuleCard("item_esp", "Item ESP",
                    () -> c.itemEsp,
                    () -> { c.itemEsp = !c.itemEsp; c.save(); },
                    () -> c.itemEspColor,
                    col -> { c.itemEspColor = col; c.save(); });
            itemEsp.subToggles.add(new SubToggle("Названия предметов", () -> c.itemEspLabels,
                    () -> { c.itemEspLabels = !c.itemEspLabels; c.save(); }));
            list.add(itemEsp);

            // 16. Mob ESP
            ModuleCard mobEsp = new ModuleCard("mob_esp", "Mob ESP",
                    () -> c.mobEsp,
                    () -> { c.mobEsp = !c.mobEsp; c.save(); },
                    () -> c.mobEspColor,
                    col -> { c.mobEspColor = col; c.save(); });
            list.add(mobEsp);

        } else if (tab == Tab.HUD) {
            list.add(new ModuleCard("hud_edit", "HUD Edit Mode", () -> c.hudEditMode, () -> { c.hudEditMode = !c.hudEditMode; c.save(); }, null, null));
            list.add(new ModuleCard("hud_server", "Watermark Bar", () -> c.showServer, () -> { c.showServer = !c.showServer; c.save(); }, null, null));
            list.add(new ModuleCard("hud_target", "Target HUD", () -> c.showTargetHud, () -> { c.showTargetHud = !c.showTargetHud; c.save(); }, null, null));
            list.add(new ModuleCard("hud_hotkeys", "Hot Keys", () -> c.showHotKeys, () -> { c.showHotKeys = !c.showHotKeys; c.save(); }, null, null));
            list.add(new ModuleCard("hud_potions", "Potions", () -> c.showPotions, () -> { c.showPotions = !c.showPotions; c.save(); }, null, null));
            list.add(new ModuleCard("hud_armor", "Armor", () -> c.showArmor, () -> { c.showArmor = !c.showArmor; c.save(); }, null, null));
            list.add(new ModuleCard("hud_array", "ArrayList", () -> c.showArrayList, () -> { c.showArrayList = !c.showArrayList; c.save(); }, null, null));
            list.add(new ModuleCard("hud_keys", "Keystrokes", () -> c.showKeystrokes, () -> { c.showKeystrokes = !c.showKeystrokes; c.save(); }, null, null));
            list.add(new ModuleCard("hud_radar", "Radar", () -> c.showRadar, () -> { c.showRadar = !c.showRadar; c.save(); }, null, null));
            list.add(new ModuleCard("hud_minimap", "MiniMap", () -> c.showMinimap, () -> { c.showMinimap = !c.showMinimap; c.save(); }, null, null));
            list.add(new ModuleCard("hud_fps", "FPS Counter", () -> c.showFps, () -> { c.showFps = !c.showFps; c.save(); }, null, null));
            list.add(new ModuleCard("hud_ping", "Ping Counter", () -> c.showPing, () -> { c.showPing = !c.showPing; c.save(); }, null, null));
            list.add(new ModuleCard("hud_xyz", "Coordinates XYZ", () -> c.showXyz, () -> { c.showXyz = !c.showXyz; c.save(); }, null, null));
            list.add(new ModuleCard("hud_speed", "Speed BPS", () -> c.showSpeed, () -> { c.showSpeed = !c.showSpeed; c.save(); }, null, null));
            list.add(new ModuleCard("hud_cps", "CPS Counter", () -> c.showCps, () -> { c.showCps = !c.showCps; c.save(); }, null, null));
            list.add(new ModuleCard("hud_health", "Health Text", () -> c.showHealthHud, () -> { c.showHealthHud = !c.showHealthHud; c.save(); }, null, null));
            list.add(new ModuleCard("hud_hunger", "Hunger Text", () -> c.showHungerHud, () -> { c.showHungerHud = !c.showHungerHud; c.save(); }, null, null));
            list.add(new ModuleCard("hud_dir", "Direction", () -> c.showDirection, () -> { c.showDirection = !c.showDirection; c.save(); }, null, null));
            list.add(new ModuleCard("hud_nick", "Nickname", () -> c.showNickname, () -> { c.showNickname = !c.showNickname; c.save(); }, null, null));
            list.add(new ModuleCard("hud_time", "Session Time", () -> c.showPlaytime, () -> { c.showPlaytime = !c.showPlaytime; c.save(); }, null, null));

        } else if (tab == Tab.UTILITIES) {
            list.add(new ModuleCard("auto_ft", "Auto FunTime", () -> c.autoFunTime,
                    () -> { c.autoFunTime = !c.autoFunTime; c.save(); }, null, null));
            list.add(new ModuleCard("adaptive", "Adaptive FPS", () -> c.adaptive,
                    () -> { c.adaptive = !c.adaptive; c.save(); }, null, null));
            list.add(new ModuleCard("preset_ft", "Apply FunTime Preset", () -> false,
                    () -> HudManager.applyFunTimePreset(mc), null, null));
            list.add(new ModuleCard("preset_pulse", "Reset HUD Layout", () -> false,
                    HudManager::applyPulsePreset, null, null));
            list.add(new ModuleCard("preset_fps", "FPS Boost Optimization", () -> false,
                    () -> { if (mc != null) Optimizer.applyPerformancePreset(mc); }, null, null));
        }

        return list;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        // Размеры главного окна пропорционально референсу (5 рядов x 2 колонки)
        winW = Math.min(444, this.width - 36);
        winH = 266;
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;

        // 1. Внешняя мягкая тень и корпус главного окна в стиле ARTEM VISUALS
        NeonButton.fillSmoothRounded(ctx, winX - 2, winY - 2, winW + 4, winH + 4, 14, 0x4504040A);
        NeonButton.fillGradientRounded(ctx, winX, winY, winW, winH, 12,
                withAlpha(NeonButton.WINDOW_TOP, 0xEE), withAlpha(NeonButton.WINDOW_BOTTOM, 0xF2));
        NeonButton.drawSmoothRoundedOutline(ctx, winX, winY, winW, winH, 12, 0x26FFFFFF);

        // 2. Верхняя панель: красная точка закрытия (слева), табы (по центру), луна/тема (справа)
        int closeX = winX + 14;
        int closeY = winY + 14;
        boolean closeHover = isHovered(mouseX, mouseY, closeX - 5, closeY - 5, 11, 11);
        NeonButton.fillSmoothRounded(ctx, closeX - 4, closeY - 4, 9, 9, 4, closeHover ? 0xFFFF7B6B : 0xFFF26D5B);

        // Иконка полумесяца со звёздами справа сверху (открывает настройки темы клиента)
        int moonBoxX = winX + winW - 26;
        int moonBoxY = winY + 9;
        boolean moonHover = isHovered(mouseX, mouseY, moonBoxX, moonBoxY, 18, 18);
        if (moonHover || themeDrawerOpen) {
            NeonButton.fillSmoothRounded(ctx, moonBoxX, moonBoxY, 18, 18, 5, 0x24FFFFFF);
        }
        drawMoonIcon(ctx, moonBoxX + 9, moonBoxY + 9,
                (moonHover || themeDrawerOpen) ? NeonButton.ACCENT_BRIGHT : 0xFFE8E8F4);

        // Центрированные вкладки: Visuals | HUD | Utilities
        int tabTotalW = 204;
        int tabBarX = winX + (winW - tabTotalW) / 2;
        int tabBarY = winY + 12;
        int tabW = tabTotalW / Tab.values().length;
        int tabH = 19;

        for (int i = 0; i < Tab.values().length; i++) {
            Tab t = Tab.values()[i];
            int tx = tabBarX + i * tabW;
            boolean active = (t == currentTab);
            boolean hov = isHovered(mouseX, mouseY, tx, tabBarY, tabW, tabH);
            if (active) {
                NeonButton.fillSmoothRounded(ctx, tx, tabBarY, tabW, tabH, 5, 0xFF32323B);
                NeonButton.fillSmoothRounded(ctx, tx + 16, tabBarY + tabH - 2, tabW - 32, 2, 1, NeonButton.ACCENT_BRIGHT);
            } else if (hov) {
                NeonButton.fillSmoothRounded(ctx, tx, tabBarY, tabW, tabH, 5, 0x16FFFFFF);
            }
            int labelW = this.textRenderer.getWidth(t.title);
            ctx.drawText(this.textRenderer, t.title, tx + (tabW - labelW) / 2, tabBarY + 6,
                    active ? 0xFFF6F6FC : (hov ? 0xFFD0D0DE : 0xFF9494A6), false);
        }

        // 3. Сетка модулей в 2 колонки (5 рядов видно сразу, скролл колесом мыши)
        List<ModuleCard> cards = buildCardsForTab(currentTab);
        int totalRows = (cards.size() + 1) / 2;
        int visibleRows = 5;
        int maxScroll = Math.max(0, totalRows - visibleRows);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        int gridX = winX + 16;
        int gridY = winY + 42;
        int colGap = 12;
        int rowGap = 9;
        int cardW = (winW - 32 - colGap) / 2;
        int cardH = 34;

        for (int r = 0; r < visibleRows; r++) {
            int dataRow = r + scrollOffset;
            if (dataRow >= totalRows) break;
            for (int col = 0; col < 2; col++) {
                int idx = dataRow * 2 + col;
                if (idx >= cards.size()) break;

                ModuleCard card = cards.get(idx);
                int cx = gridX + col * (cardW + colGap);
                int cy = gridY + r * (cardH + rowGap);

                boolean on = card.enabled.getAsBoolean();
                boolean hov = isHovered(mouseX, mouseY, cx, cy, cardW, cardH);
                boolean selected = (activeSettingsCard != null && activeSettingsCard.id.equals(card.id));

                int cardBg = selected ? 0xFF30303B : (hov ? 0xFF2B2B34 : 0xFF25252D);
                NeonButton.fillSmoothRounded(ctx, cx, cy, cardW, cardH, 7, cardBg);
                if (selected) {
                    NeonButton.drawSmoothRoundedOutline(ctx, cx, cy, cardW, cardH, 7,
                            withAlpha(NeonButton.ACCENT_BRIGHT, 0x99));
                } else if (hov) {
                    NeonButton.drawSmoothRoundedOutline(ctx, cx, cy, cardW, cardH, 7, 0x1EFFFFFF);
                }

                // Название модуля слева
                ctx.drawText(this.textRenderer, card.name, cx + 12, cy + 13,
                        on ? 0xFFF4F4FC : 0xFFDCDCE8, false);

                // Если у модуля есть доп. настройки (ПКМ) — аккуратный индикатор из 3 точек
                if (card.hasSettings()) {
                    int dotX = cx + cardW - 48;
                    int dotY = cy + 16;
                    int dotCol = selected ? NeonButton.ACCENT_BRIGHT : 0x55AAAAC0;
                    ctx.fill(dotX, dotY, dotX + 2, dotY + 2, dotCol);
                    ctx.fill(dotX + 4, dotY, dotX + 6, dotY + 2, dotCol);
                    ctx.fill(dotX + 8, dotY, dotX + 10, dotY + 2, dotCol);
                }

                // Pill-переключатель справа
                int swW = 26;
                int swH = 14;
                int swX = cx + cardW - swW - 11;
                int swY = cy + (cardH - swH) / 2;
                drawPillSwitch(ctx, swX, swY, swW, swH, on);
            }
        }

        // Тонкий скроллбар справа от сетки
        if (maxScroll > 0) {
            int trackX = winX + winW - 7;
            int trackY = gridY + 2;
            int trackH = visibleRows * cardH + (visibleRows - 1) * rowGap - 4;
            NeonButton.fillSmoothRounded(ctx, trackX, trackY, 2, trackH, 1, 0x18FFFFFF);
            int thumbH = Math.max(20, (trackH * visibleRows) / totalRows);
            int thumbY = trackY + ((trackH - thumbH) * scrollOffset) / maxScroll;
            NeonButton.fillSmoothRounded(ctx, trackX, thumbY, 2, thumbH, 1, NeonButton.ACCENT_BRIGHT);
        }

        // 4. Выдвижная панель настроек и HSB-палитры справа
        if (themeDrawerOpen) {
            renderThemeDrawer(ctx, mouseX, mouseY);
        } else if (activeSettingsCard != null) {
            for (ModuleCard fresh : cards) {
                if (fresh.id.equals(activeSettingsCard.id)) {
                    activeSettingsCard = fresh;
                    break;
                }
            }
            renderModuleDrawer(ctx, activeSettingsCard, mouseX, mouseY);
        }
    }

    private void renderModuleDrawer(DrawContext ctx, ModuleCard card, int mouseX, int mouseY) {
        drawerW = 162;
        int contentH = 26;
        contentH += card.subToggles.size() * 22;
        contentH += card.subSliders.size() * 24;
        boolean hasColor = (card.colorGetter != null && card.colorSetter != null);
        if (hasColor) {
            contentH += 24;
            if (colorPickerExpanded) contentH += 98;
        }
        drawerH = Math.max(54, contentH + 8);
        drawerX = Math.min(this.width - drawerW - 6, winX + winW + 10);
        drawerY = Math.max(8, Math.min(this.height - drawerH - 8, activeDrawerY));

        NeonButton.fillSmoothRounded(ctx, drawerX - 2, drawerY - 2, drawerW + 4, drawerH + 4, 11, 0x4504040A);
        NeonButton.fillGradientRounded(ctx, drawerX, drawerY, drawerW, drawerH, 9,
                withAlpha(NeonButton.WINDOW_TOP, 0xF2), withAlpha(NeonButton.WINDOW_BOTTOM, 0xF5));
        NeonButton.drawSmoothRoundedOutline(ctx, drawerX, drawerY, drawerW, drawerH, 9, 0x26FFFFFF);

        ctx.drawText(this.textRenderer, card.name, drawerX + 10, drawerY + 8, NeonButton.ACCENT_BRIGHT, false);
        int xBtnX = drawerX + drawerW - 15;
        int xBtnY = drawerY + 7;
        ctx.drawText(this.textRenderer, "×", xBtnX, xBtnY,
                isHovered(mouseX, mouseY, xBtnX - 3, xBtnY - 2, 12, 12) ? 0xFFFFFFFF : 0xFF8E8EA6, false);

        int ry = drawerY + 25;

        for (SubToggle st : card.subToggles) {
            ctx.drawText(this.textRenderer, st.label, drawerX + 10, ry + 3, 0xFFE6E6F2, false);
            drawPillSwitch(ctx, drawerX + drawerW - 35, ry, 25, 13, st.getter.getAsBoolean());
            ry += 22;
        }

        for (SubSlider sl : card.subSliders) {
            float val = sl.getter.get();
            String valTxt = sl.isInt ? String.valueOf(Math.round(val)) : String.format("%.1f", val);
            ctx.drawText(this.textRenderer, sl.label, drawerX + 10, ry, 0xFFD4D4E4, false);
            int vw = this.textRenderer.getWidth(valTxt);
            ctx.drawText(this.textRenderer, valTxt, drawerX + drawerW - vw - 10, ry, NeonButton.ACCENT_BRIGHT, false);

            int barX = drawerX + 10;
            int barY = ry + 12;
            int barW = drawerW - 20;
            NeonButton.fillSmoothRounded(ctx, barX, barY, barW, 3, 1, 0xFF343440);
            float ratio = Math.max(0f, Math.min(1f, (val - sl.min) / (sl.max - sl.min)));
            int fillW = Math.round(barW * ratio);
            if (fillW > 1) {
                NeonButton.fillGradientRounded(ctx, barX, barY, fillW, 3, 1, NeonButton.ACCENT, NeonButton.ACCENT_BRIGHT);
            }
            NeonButton.fillSmoothRounded(ctx, barX + fillW - 3, barY - 2, 7, 7, 3, 0xFFFFFFFF);
            ry += 24;
        }

        if (hasColor) {
            int curCol = card.colorGetter.getAsInt();
            syncPickerFromColor(curCol);

            ctx.drawText(this.textRenderer, "Кастомный цвет", drawerX + 10, ry + 3, 0xFFE6E6F2, false);
            int boxX = drawerX + drawerW - 24;
            int boxY = ry + 1;
            NeonButton.fillSmoothRounded(ctx, boxX, boxY, 13, 13, 3, 0xFF000000 | (curCol & 0x00FFFFFF));
            NeonButton.drawSmoothRoundedOutline(ctx, boxX, boxY, 13, 13, 3, 0xCCFFFFFF);
            ry += 21;

            if (colorPickerExpanded) {
                renderHsbPicker(ctx, drawerX + 10, ry, drawerW - 20);
            }
        }
    }

    private void renderThemeDrawer(DrawContext ctx, int mouseX, int mouseY) {
        drawerW = 162;
        drawerH = 196;
        drawerX = Math.min(this.width - drawerW - 6, winX + winW + 10);
        drawerY = Math.max(8, winY + 8);

        NeonButton.fillSmoothRounded(ctx, drawerX - 2, drawerY - 2, drawerW + 4, drawerH + 4, 11, 0x4504040A);
        NeonButton.fillGradientRounded(ctx, drawerX, drawerY, drawerW, drawerH, 9,
                withAlpha(NeonButton.WINDOW_TOP, 0xF2), withAlpha(NeonButton.WINDOW_BOTTOM, 0xF5));
        NeonButton.drawSmoothRoundedOutline(ctx, drawerX, drawerY, drawerW, drawerH, 9, 0x26FFFFFF);

        ctx.drawText(this.textRenderer, "Стиль клиента", drawerX + 10, drawerY + 8, NeonButton.ACCENT_BRIGHT, false);
        int xBtnX = drawerX + drawerW - 15;
        int xBtnY = drawerY + 7;
        ctx.drawText(this.textRenderer, "×", xBtnX, xBtnY,
                isHovered(mouseX, mouseY, xBtnX - 3, xBtnY - 2, 12, 12) ? 0xFFFFFFFF : 0xFF8E8EA6, false);

        int[] presets = {0xFF00D4FF, 0xFF9D4EDD, 0xFF00F59B, 0xFFFF4D8D, 0xFFFFB703};
        int px = drawerX + 10;
        int py = drawerY + 26;
        for (int pCol : presets) {
            NeonButton.fillSmoothRounded(ctx, px, py, 22, 14, 4, pCol);
            if ((NeonButton.ACCENT & 0x00FFFFFF) == (pCol & 0x00FFFFFF)) {
                NeonButton.drawSmoothRoundedOutline(ctx, px, py, 22, 14, 4, 0xFFFFFFFF);
            }
            px += 29;
        }

        int ry = drawerY + 50;
        int curCol = NeonButton.ACCENT;
        syncPickerFromColor(curCol);

        ctx.drawText(this.textRenderer, "Кастомный цвет", drawerX + 10, ry + 3, 0xFFE6E6F2, false);
        int boxX = drawerX + drawerW - 24;
        int boxY = ry + 1;
        NeonButton.fillSmoothRounded(ctx, boxX, boxY, 13, 13, 3, 0xFF000000 | (curCol & 0x00FFFFFF));
        NeonButton.drawSmoothRoundedOutline(ctx, boxX, boxY, 13, 13, 3, 0xCCFFFFFF);

        renderHsbPicker(ctx, drawerX + 10, ry + 21, drawerW - 20);
    }

    private void renderHsbPicker(DrawContext ctx, int px, int py, int pw) {
        int boxH = 72;
        NeonButton.fillSmoothRounded(ctx, px - 2, py - 2, pw + 4, boxH + 22, 6, 0xFF282832);
        NeonButton.drawSmoothRoundedOutline(ctx, px - 2, py - 2, pw + 4, boxH + 22, 6, 0x24FFFFFF);

        if (Math.abs(cachedHue - pickerHue) > 0.002f) {
            cachedHue = pickerHue;
            for (int row = 0; row < 36; row++) {
                float bri = 1.0f - (row / 35.0f);
                for (int col = 0; col < 64; col++) {
                    float sat = col / 63.0f;
                    cachedGradient[row][col] = 0xFF000000 | (Color.HSBtoRGB(pickerHue, sat, bri) & 0x00FFFFFF);
                }
            }
        }

        for (int row = 0; row < 36; row++) {
            int y1 = py + (row * boxH) / 36;
            int y2 = py + ((row + 1) * boxH) / 36;
            for (int col = 0; col < 64; col++) {
                int x1 = px + (col * pw) / 64;
                int x2 = px + ((col + 1) * pw) / 64;
                ctx.fill(x1, y1, x2, y2, cachedGradient[row][col]);
            }
        }

        int selX = px + Math.round(pickerSat * (pw - 1));
        int selY = py + Math.round((1.0f - pickerBri) * (boxH - 1));
        NeonButton.drawSmoothRoundedOutline(ctx, selX - 3, selY - 3, 7, 7, 3, 0xFFFFFFFF);

        int hueY = py + boxH + 5;
        int hueH = 10;
        for (int i = 0; i < pw; i++) {
            float h = i / (float) Math.max(1, pw - 1);
            int hCol = 0xFF000000 | (Color.HSBtoRGB(h, 1.0f, 1.0f) & 0x00FFFFFF);
            ctx.fill(px + i, hueY, px + i + 1, hueY + hueH, hCol);
        }

        int hueKnobX = px + Math.round(pickerHue * (pw - 5));
        NeonButton.fillSmoothRounded(ctx, hueKnobX, hueY - 1, 5, hueH + 2, 2, 0xFFFFFFFF);
    }

    private void drawPillSwitch(DrawContext ctx, int x, int y, int w, int h, boolean on) {
        if (on) {
            NeonButton.fillGradientRounded(ctx, x, y, w, h, h / 2, NeonButton.ACCENT, NeonButton.ACCENT_BRIGHT);
        } else {
            NeonButton.fillSmoothRounded(ctx, x, y, w, h, h / 2, 0xFF4B4B55);
        }
        int knobSize = h - 4;
        int knobX = on ? (x + w - knobSize - 2) : (x + 2);
        int knobY = y + 2;
        NeonButton.fillSmoothRounded(ctx, knobX, knobY, knobSize, knobSize, knobSize / 2, 0xFFFFFFFF);
    }

    private void drawMoonIcon(DrawContext ctx, int cx, int cy, int col) {
        ctx.fill(cx - 2, cy - 5, cx + 1, cy - 4, col);
        ctx.fill(cx - 4, cy - 4, cx - 2, cy - 2, col);
        ctx.fill(cx - 5, cy - 2, cx - 3, cy + 2, col);
        ctx.fill(cx - 4, cy + 2, cx - 2, cy + 4, col);
        ctx.fill(cx - 2, cy + 4, cx + 2, cy + 5, col);
        ctx.fill(cx + 2, cy + 3, cx + 4, cy + 4, col);
        ctx.fill(cx + 3, cy - 4, cx + 4, cy - 1, col);
        ctx.fill(cx + 2, cy - 3, cx + 5, cy - 2, col);
        ctx.fill(cx + 1, cy - 6, cx + 2, cy - 5, col);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (themeDrawerOpen) {
            if (handleThemeDrawerClick(mx, my)) return true;
        } else if (activeSettingsCard != null) {
            if (handleModuleDrawerClick(activeSettingsCard, mx, my)) return true;
        }

        if (isHovered(mx, my, winX + 8, winY + 8, 14, 14)) {
            this.close();
            return true;
        }

        if (isHovered(mx, my, winX + winW - 26, winY + 9, 18, 18)) {
            themeDrawerOpen = !themeDrawerOpen;
            if (themeDrawerOpen) {
                activeSettingsCard = null;
                syncedPickerColor = 0;
            }
            return true;
        }

        int tabTotalW = 204;
        int tabBarX = winX + (winW - tabTotalW) / 2;
        int tabBarY = winY + 12;
        int tabW = tabTotalW / Tab.values().length;
        int tabH = 19;
        for (int i = 0; i < Tab.values().length; i++) {
            int tx = tabBarX + i * tabW;
            if (isHovered(mx, my, tx, tabBarY, tabW, tabH)) {
                currentTab = Tab.values()[i];
                scrollOffset = 0;
                activeSettingsCard = null;
                return true;
            }
        }

        List<ModuleCard> cards = buildCardsForTab(currentTab);
        int totalRows = (cards.size() + 1) / 2;
        int visibleRows = 5;
        int gridX = winX + 16;
        int gridY = winY + 42;
        int colGap = 12;
        int rowGap = 9;
        int cardW = (winW - 32 - colGap) / 2;
        int cardH = 34;

        for (int r = 0; r < visibleRows; r++) {
            int dataRow = r + scrollOffset;
            if (dataRow >= totalRows) break;
            for (int col = 0; col < 2; col++) {
                int idx = dataRow * 2 + col;
                if (idx >= cards.size()) break;
                int cx = gridX + col * (cardW + colGap);
                int cy = gridY + r * (cardH + rowGap);
                if (isHovered(mx, my, cx, cy, cardW, cardH)) {
                    ModuleCard card = cards.get(idx);
                    if (button == 1 && card.hasSettings()) {
                        themeDrawerOpen = false;
                        if (activeSettingsCard != null && activeSettingsCard.id.equals(card.id)) {
                            activeSettingsCard = null;
                        } else {
                            activeSettingsCard = card;
                            activeDrawerY = cy - 12;
                            syncedPickerColor = 0;
                        }
                    } else if (button == 0) {
                        card.onToggle.run();
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleModuleDrawerClick(ModuleCard card, int mx, int my) {
        if (!isHovered(mx, my, drawerX - 4, drawerY - 4, drawerW + 8, drawerH + 8)) {
            return false;
        }
        if (isHovered(mx, my, drawerX + drawerW - 18, drawerY + 4, 15, 15)) {
            activeSettingsCard = null;
            return true;
        }

        int ry = drawerY + 25;
        for (SubToggle st : card.subToggles) {
            if (isHovered(mx, my, drawerX + 6, ry - 2, drawerW - 12, 18)) {
                st.toggler.run();
                return true;
            }
            ry += 22;
        }

        for (SubSlider sl : card.subSliders) {
            if (isHovered(mx, my, drawerX + 8, ry + 7, drawerW - 16, 14)) {
                draggingSlider = sl;
                updateSliderFromMouse(sl, mx, drawerX + 10, drawerW - 20);
                return true;
            }
            ry += 24;
        }

        if (card.colorGetter != null && card.colorSetter != null) {
            if (isHovered(mx, my, drawerX + 6, ry - 2, drawerW - 12, 18)) {
                colorPickerExpanded = !colorPickerExpanded;
                return true;
            }
            ry += 21;
            if (colorPickerExpanded) {
                return handlePickerInteraction(mx, my, drawerX + 10, ry, drawerW - 20, card.colorSetter, true);
            }
        }
        return true;
    }

    private boolean handleThemeDrawerClick(int mx, int my) {
        if (!isHovered(mx, my, drawerX - 4, drawerY - 4, drawerW + 8, drawerH + 8)) {
            return false;
        }
        if (isHovered(mx, my, drawerX + drawerW - 18, drawerY + 4, 15, 15)) {
            themeDrawerOpen = false;
            return true;
        }

        int[] presets = {0xFF00D4FF, 0xFF9D4EDD, 0xFF00F59B, 0xFFFF4D8D, 0xFFFFB703};
        int px = drawerX + 10;
        int py = drawerY + 26;
        for (int pCol : presets) {
            if (isHovered(mx, my, px, py, 22, 14)) {
                applyClientAccent(pCol);
                syncedPickerColor = 0;
                return true;
            }
            px += 29;
        }

        return handlePickerInteraction(mx, my, drawerX + 10, drawerY + 71, drawerW - 20,
                ArtemScreen::applyClientAccent, true);
    }

    private boolean handlePickerInteraction(int mx, int my, int px, int py, int pw,
                                            IntConsumer onColorChanged, boolean startDrag) {
        int boxH = 72;
        int hueY = py + boxH + 5;
        int hueH = 10;

        if ((startDrag && isHovered(mx, my, px - 2, py - 2, pw + 4, boxH + 4)) || draggingSatBri) {
            if (startDrag) draggingSatBri = true;
            pickerSat = Math.max(0f, Math.min(1f, (mx - px) / (float) Math.max(1, pw - 1)));
            pickerBri = 1.0f - Math.max(0f, Math.min(1f, (my - py) / (float) Math.max(1, boxH - 1)));
            int rgb = 0xFF000000 | (Color.HSBtoRGB(pickerHue, pickerSat, pickerBri) & 0x00FFFFFF);
            syncedPickerColor = rgb;
            onColorChanged.accept(rgb);
            return true;
        }

        if ((startDrag && isHovered(mx, my, px - 2, hueY - 3, pw + 4, hueH + 6)) || draggingHue) {
            if (startDrag) draggingHue = true;
            pickerHue = Math.max(0f, Math.min(1f, (mx - px) / (float) Math.max(1, pw - 1)));
            int rgb = 0xFF000000 | (Color.HSBtoRGB(pickerHue, pickerSat, pickerBri) & 0x00FFFFFF);
            syncedPickerColor = rgb;
            onColorChanged.accept(rgb);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (draggingSlider != null) {
            updateSliderFromMouse(draggingSlider, mx, drawerX + 10, drawerW - 20);
            return true;
        }

        if (draggingSatBri || draggingHue) {
            if (themeDrawerOpen) {
                handlePickerInteraction(mx, my, drawerX + 10, drawerY + 71, drawerW - 20,
                        ArtemScreen::applyClientAccent, false);
                return true;
            } else if (activeSettingsCard != null && activeSettingsCard.colorSetter != null) {
                int pickerY = drawerY + 25 + activeSettingsCard.subToggles.size() * 22
                        + activeSettingsCard.subSliders.size() * 24 + 21;
                handlePickerInteraction(mx, my, drawerX + 10, pickerY, drawerW - 20,
                        activeSettingsCard.colorSetter, false);
                return true;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSatBri = false;
        draggingHue = false;
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isHovered((int) mouseX, (int) mouseY, winX, winY, winW, winH)) {
            if (verticalAmount < 0) scrollOffset++;
            else if (verticalAmount > 0) scrollOffset--;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateSliderFromMouse(SubSlider sl, int mx, int barX, int barW) {
        float ratio = Math.max(0f, Math.min(1f, (mx - barX) / (float) Math.max(1, barW)));
        float raw = sl.min + ratio * (sl.max - sl.min);
        float stepped = Math.round(raw / sl.step) * sl.step;
        stepped = Math.max(sl.min, Math.min(sl.max, stepped));
        sl.setter.accept(stepped);
    }

    private void syncPickerFromColor(int argb) {
        if (syncedPickerColor == argb) return;
        syncedPickerColor = argb;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        pickerHue = hsb[0];
        pickerSat = hsb[1];
        pickerBri = hsb[2];
    }

    private static void applyClientAccent(int argb) {
        NeonButton.ACCENT = argb;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * 1.25f + 24));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * 1.25f + 24));
        int b = Math.min(255, Math.round((argb & 0xFF) * 1.25f + 24));
        NeonButton.ACCENT_BRIGHT = 0xFF000000 | (r << 16) | (g << 8) | b;

        ArtemConfig c = ArtemConfig.get();
        try {
            for (String fieldName : new String[]{"accentColor", "customAccentColor", "themeColor", "uiColor"}) {
                try {
                    var f = c.getClass().getField(fieldName);
                    if (f.getType() == int.class) f.setInt(c, argb);
                } catch (NoSuchFieldException ignored) {}
            }
            for (String boolName : new String[]{"customAccent", "useCustomColor", "customTheme"}) {
                try {
                    var f = c.getClass().getField(boolName);
                    if (f.getType() == boolean.class) f.setBoolean(c, true);
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception ignored) {}
        c.save();
    }

    private static int withAlpha(int col, int alpha) {
        return ((alpha & 0xFF) << 24) | (col & 0x00FFFFFF);
    }

    private static boolean isHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
