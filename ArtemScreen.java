package com.artem.visuals.gui;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.hud.HudManager;
import com.artem.visuals.optimize.Optimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ArtemScreen extends Screen {
    private enum Tab {
        VISUALS("Visuals"),
        HUD("HUD"),
        SWORD("Sword & View"),
        WORLD("World & Theme");

        final String title;
        Tab(String title) { this.title = title; }
    }

    private sealed interface SubItem permits SubToggle, SubSlider, SubSwatches {}

    private record SubToggle(String label, BooleanSupplier getter, Runnable toggler) implements SubItem {}
    private record SubSlider(String label, double min, double max, boolean integer, DoubleSupplier getter, DoubleConsumer setter) implements SubItem {}
    private record SubSwatches(String label, int[] palette, IntSupplier getter, IntConsumer setter) implements SubItem {}

    private static final int[] ACCENT_PALETTE = {
            0xFF8A7CFF, 0xFF00F5FF, 0xFFFF4D8D, 0xFF2EC4B6,
            0xFFFFB703, 0xFFFF595E, 0xFF6BCB77, 0xFFFFFFFF
    };

    private static final int[] BG_PALETTE = {
            0x101018, 0x0A101E, 0x160C1E, 0x0A1916,
            0x1C0C12, 0x050507
    };

    private final Screen parent;
    private Tab currentTab = Tab.VISUALS;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private String subTitle = null;
    private final List<SubItem> subItems = new ArrayList<>();

    private int winX, winY, winW, winH;

    public ArtemScreen(Screen parent) {
        super(Text.literal("Artem Visuals"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        NeonButton.syncTheme();
        ArtemConfig c = ArtemConfig.get();

        float s = Math.max(0.70f, Math.min(1.15f, c.guiScale));
        winW = Math.min(width - 16, Math.round(336 * s));
        winH = Math.min(height - 16, Math.round(212 * s));
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;

        int dockX = winX + 6;
        int dockY = winY + 26;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            final Tab tab = tabs[i];
            addDrawableChild(new NeonButton(
                    dockX, dockY + i * 24, 20, 20,
                    () -> tab.title,
                    () -> currentTab == tab,
                    () -> {
                        currentTab = tab;
                        scrollOffset = 0;
                        subTitle = null;
                        subItems.clear();
                        init();
                    },
                    NeonButton.Style.DOCK_ICON,
                    i
            ));
        }

        addDrawableChild(new NeonButton(
                winX + winW - 20, winY + 5, 14, 14,
                () -> "X", null,
                () -> close(),
                NeonButton.Style.CLOSE_ICON
        ));

        buildTabContent(c);
    }

    private void openSubPanel(String title, List<SubItem> items) {
        this.subTitle = title;
        this.subItems.clear();
        this.subItems.addAll(items);
        init();
    }

    private void buildTabContent(ArtemConfig c) {
        int contentX = winX + 32;
        int contentY = winY + 25;
        int subW = (subTitle != null && !subItems.isEmpty()) ? 112 : 0;
        int contentW = winW - 38 - (subW > 0 ? subW + 6 : 0);
        int contentH = winH - 31;

        List<NeonButton> items = new ArrayList<>();
        int colW = (contentW - 6) / 2;

        switch (currentTab) {
            case VISUALS -> {
                items.add(toggleCard("Target ESP (ПКМ)", () -> c.targetEsp, () -> { c.targetEsp = !c.targetEsp; c.save(); },
                        () -> openSubPanel("Target ESP", List.of(
                                new SubSwatches("Цвет", ACCENT_PALETTE, () -> c.targetEspColor, col -> { c.targetEspColor = col; c.save(); }),
                                new SubSlider("Скорость", 0.3, 2.5, false, () -> c.targetEspSpeed, v -> { c.targetEspSpeed = (float) v; c.save(); })
                        ))));
                items.add(toggleCard("Jump Circle (ПКМ)", () -> c.jumpCircle, () -> { c.jumpCircle = !c.jumpCircle; c.save(); },
                        () -> openSubPanel("Jump Circle", List.of(
                                new SubSwatches("Цвет", ACCENT_PALETTE, () -> c.jumpCircleColor, col -> { c.jumpCircleColor = col; c.save(); }),
                                new SubSlider("Радиус", 0.5, 2.5, false, () -> c.jumpCircleRadius, v -> { c.jumpCircleRadius = (float) v; c.save(); })
                        ))));
                items.add(toggleCard("Trails (ПКМ)", () -> c.trails, () -> { c.trails = !c.trails; c.save(); },
                        () -> openSubPanel("Trails", List.of(
                                new SubSwatches("Цвет", ACCENT_PALETTE, () -> c.trailColor, col -> { c.trailColor = col; c.save(); }),
                                new SubSlider("Длина", 8, 60, true, () -> c.trailLength, v -> { c.trailLength = (int) v; c.save(); })
                        ))));
                items.add(toggleCard("Nimb (ПКМ)", () -> c.nimb, () -> { c.nimb = !c.nimb; c.save(); },
                        () -> openSubPanel("Nimb", List.of(
                                new SubSwatches("Цвет", ACCENT_PALETTE, () -> c.nimbColor, col -> { c.nimbColor = col; c.save(); }),
                                new SubSlider("Размер", 0.2, 0.7, false, () -> c.nimbSize, v -> { c.nimbSize = (float) v; c.save(); })
                        ))));
                items.add(toggleCard("NameTags (ПКМ)", () -> c.nameTags, () -> { c.nameTags = !c.nameTags; c.save(); },
                        () -> openSubPanel("NameTags", List.of(
                                new SubToggle("Здоровье", () -> c.nameTagsHealth, () -> { c.nameTagsHealth = !c.nameTagsHealth; c.save(); }),
                                new SubToggle("Дистанция", () -> c.nameTagsDistance, () -> { c.nameTagsDistance = !c.nameTagsDistance; c.save(); })
                        ))));
                items.add(toggleCard("Hit Particles", () -> c.hitParticles, () -> { c.hitParticles = !c.hitParticles; c.save(); }, null));
                items.add(toggleCard("Crit Sparks", () -> c.critSparks, () -> { c.critSparks = !c.critSparks; c.save(); }, null));
                items.add(toggleCard("Damage Indicator", () -> c.damageIndicator, () -> { c.damageIndicator = !c.damageIndicator; c.save(); }, null));
                items.add(toggleCard("Projectile Trails", () -> c.projectileTrails, () -> { c.projectileTrails = !c.projectileTrails; c.save(); }, null));
                items.add(toggleCard("Block Overlay", () -> c.blockOverlay, () -> { c.blockOverlay = !c.blockOverlay; c.save(); }, null));
                items.add(toggleCard("Player ESP (ПКМ)", () -> c.esp, () -> { c.esp = !c.esp; c.save(); },
                        () -> openSubPanel("Player ESP", List.of(
                                new SubSwatches("Цвет", ACCENT_PALETTE, () -> c.espColor, col -> { c.espColor = col; c.save(); }),
                                new SubToggle("Пульсация", () -> c.espPulse, () -> { c.espPulse = !c.espPulse; c.save(); })
                        ))));
                items.add(toggleCard("Chams", () -> c.chams, () -> { c.chams = !c.chams; c.save(); }, null));
                items.add(toggleCard("Tracers", () -> c.tracers, () -> { c.tracers = !c.tracers; c.save(); }, null));
                items.add(toggleCard("Item ESP", () -> c.itemEsp, () -> { c.itemEsp = !c.itemEsp; c.save(); }, null));
            }
            case HUD -> {
                items.add(NeonButton.swatches(0, 0, colW, 18, "Цвет HUD", ACCENT_PALETTE,
                        () -> c.hudAccentColor,
                        col -> {
                            c.hudAccentColor = col;
                            c.hudUseCustomHue = false;
                            c.hudRainbow = false;
                            c.save();
                            NeonButton.syncTheme();
                        }));
                items.add(NeonButton.hueSlider(0, 0, colW, 18, "Оттенок HUD",
                        () -> c.hudHue,
                        val -> {
                            c.hudHue = (float) val;
                            c.hudUseCustomHue = true;
                            c.hudRainbow = false;
                            c.save();
                            NeonButton.syncTheme();
                        }));
                items.add(NeonButton.swatches(0, 0, colW, 18, "Фон HUD", BG_PALETTE,
                        () -> c.hudBgColor,
                        col -> {
                            c.hudBgColor = col;
                            c.save();
                            NeonButton.syncTheme();
                        }));
                items.add(NeonButton.slider(0, 0, colW, 18, "Масштаб HUD", 0.50, 1.20, false,
                        () -> c.hudScale,
                        val -> {
                            c.hudScale = (float) val;
                            c.save();
                        }));
                items.add(toggleCard("RGB Перелив HUD", () -> c.hudRainbow, () -> {
                    c.hudRainbow = !c.hudRainbow;
                    c.save();
                    NeonButton.syncTheme();
                }, null));
                items.add(toggleCard("Двигать HUD", () -> c.hudEditMode, () -> {
                    c.hudEditMode = !c.hudEditMode;
                    c.save();
                }, null));

                items.add(new NeonButton(0, 0, colW, 18, () -> "Сброс позиций", null, () -> {
                    c.hudPositions.clear();
                    c.save();
                }));
                items.add(new NeonButton(0, 0, colW, 18, () -> "Пресет Andromeda", null, HudManager::applyPulsePreset));

                items.add(toggleCard("Top Bar (Время)", () -> c.showServer, () -> { c.showServer = !c.showServer; c.save(); }, null));
                items.add(toggleCard("Inventory HUD", () -> c.showInventory, () -> { c.showInventory = !c.showInventory; c.save(); }, null));
                items.add(toggleCard("Potion Effects", () -> c.showPotions, () -> { c.showPotions = !c.showPotions; c.save(); }, null));
                items.add(toggleCard("Keybinds", () -> c.showHotKeys, () -> { c.showHotKeys = !c.showHotKeys; c.save(); }, null));
                items.add(toggleCard("Paperdoll (Модель)", () -> c.showPaperdoll, () -> { c.showPaperdoll = !c.showPaperdoll; c.save(); }, null));
                items.add(toggleCard("TargetHUD", () -> c.showTargetHud, () -> { c.showTargetHud = !c.showTargetHud; c.save(); }, null));
                items.add(toggleCard("Armor (Броня)", () -> c.showArmor, () -> { c.showArmor = !c.showArmor; c.save(); }, null));
                items.add(toggleCard("Armor % (Цифры)", () -> c.armorDurabilityPercent, () -> { c.armorDurabilityPercent = !c.armorDurabilityPercent; c.save(); }, null));
                items.add(toggleCard("ArrayList", () -> c.showArrayList, () -> { c.showArrayList = !c.showArrayList; c.save(); }, null));
                items.add(toggleCard("Keystrokes", () -> c.showKeystrokes, () -> { c.showKeystrokes = !c.showKeystrokes; c.save(); }, null));
                items.add(toggleCard("Radar", () -> c.showRadar, () -> { c.showRadar = !c.showRadar; c.save(); }, null));
                items.add(toggleCard("MiniMap", () -> c.showMinimap, () -> { c.showMinimap = !c.showMinimap; c.save(); }, null));
                items.add(toggleCard("XYZ", () -> c.showXyz, () -> { c.showXyz = !c.showXyz; c.save(); }, null));
                items.add(toggleCard("Speed (BPS)", () -> c.showSpeed, () -> { c.showSpeed = !c.showSpeed; c.save(); }, null));
                items.add(toggleCard("CPS", () -> c.showCps, () -> { c.showCps = !c.showCps; c.save(); }, null));
            }
            case SWORD -> {
                items.add(toggleCard("Маленький меч", () -> c.smallSword, () -> { c.smallSword = !c.smallSword; c.save(); }, null));
                items.add(toggleCard("Только оружие", () -> c.onlyWeapons, () -> { c.onlyWeapons = !c.onlyWeapons; c.save(); }, null));
                items.add(NeonButton.slider(0, 0, colW, 18, "Размер меча", 0.25, 1.25, false,
                        () -> c.swordScale, v -> { c.swordScale = (float) v; c.save(); }));
                items.add(NeonButton.slider(0, 0, colW, 18, "Сдвиг X", -0.40, 0.40, false,
                        () -> c.swordOffsetX, v -> { c.swordOffsetX = (float) v; c.save(); }));
                items.add(NeonButton.slider(0, 0, colW, 18, "Сдвиг Y", -0.40, 0.40, false,
                        () -> c.swordOffsetY, v -> { c.swordOffsetY = (float) v; c.save(); }));
                items.add(NeonButton.slider(0, 0, colW, 18, "Сдвиг Z", -0.40, 0.40, false,
                        () -> c.swordOffsetZ, v -> { c.swordOffsetZ = (float) v; c.save(); }));

                items.add(toggleCard("Hand Aura (Свечение)", () -> c.handGlow, () -> { c.handGlow = !c.handGlow; c.save(); }, null));
                items.add(toggleCard("No HurtCam", () -> c.noHurtCam, () -> { c.noHurtCam = !c.noHurtCam; c.save(); }, null));
                items.add(toggleCard("Low Fire", () -> c.lowFire, () -> { c.lowFire = !c.lowFire; c.save(); }, null));
                items.add(toggleCard("Виньетка экрана", () -> c.screenVignette, () -> { c.screenVignette = !c.screenVignette; c.save(); }, null));
                items.add(toggleCard("Убрать тряску рук", () -> c.renderTweaks, () -> { c.renderTweaks = !c.renderTweaks; c.save(); }, null));
                items.add(toggleCard("Кастом прицел", () -> c.customCrosshair, () -> { c.customCrosshair = !c.customCrosshair; c.save(); }, null));
                items.add(NeonButton.swatches(0, 0, colW, 18, "Цвет прицела", ACCENT_PALETTE,
                        () -> c.crosshairColor, col -> { c.crosshairColor = col; c.save(); }));
            }
            case WORLD -> {
                items.add(NeonButton.swatches(0, 0, colW, 18, "Цвет темы", ACCENT_PALETTE,
                        () -> c.clientAccent,
                        col -> {
                            c.clientAccent = col;
                            c.useCustomHue = false;
                            c.rgbMode = false;
                            c.save();
                            NeonButton.syncTheme();
                        }));
                items.add(NeonButton.slider(0, 0, colW, 18, "Масштаб меню", 0.70, 1.15, false,
                        () -> c.guiScale,
                        v -> {
                            c.guiScale = (float) v;
                            c.save();
                            init();
                        }));
                items.add(toggleCard("RGB Тема", () -> c.rgbMode, () -> { c.rgbMode = !c.rgbMode; c.save(); NeonButton.syncTheme(); }, null));
                items.add(toggleCard("Fullbright", () -> c.softLighting, () -> {
                    c.softLighting = !c.softLighting;
                    c.save();
                    Optimizer.applyLighting(MinecraftClient.getInstance());
                }, null));
                items.add(new NeonButton(0, 0, colW, 18, () -> {
                    String mode = switch (c.worldTimeMode) {
                        case 1 -> "День";
                        case 2 -> "Закат";
                        case 3 -> "Ночь";
                        default -> "Сервер";
                    };
                    return "Время: " + mode;
                }, null, () -> {
                    c.worldCustomizer = true;
                    c.worldTimeMode = (c.worldTimeMode + 1) % 4;
                    c.save();
                }));
                items.add(toggleCard("Листва и частицы", () -> c.leafParticles, () -> { c.leafParticles = !c.leafParticles; c.save(); }, null));
                items.add(toggleCard("Светлячки", () -> c.fireflies, () -> { c.fireflies = !c.fireflies; c.save(); }, null));
                items.add(toggleCard("Искры воды", () -> c.waterSparkles, () -> { c.waterSparkles = !c.waterSparkles; c.save(); }, null));
                items.add(new NeonButton(0, 0, colW, 18, () -> "Пресет FunTime", null, () -> HudManager.applyFunTimePreset(MinecraftClient.getInstance())));
                items.add(new NeonButton(0, 0, colW, 18, () -> "Сбросить всё", null, () -> {
                    c.resetDefaults();
                    NeonButton.syncTheme();
                    init();
                }));
            }
        }

        int rowH = 21;
        int totalRows = (items.size() + 1) / 2;
        int totalH = totalRows * rowH;
        maxScroll = Math.max(0, totalH - contentH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        for (int i = 0; i < items.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = contentX + col * (colW + 6);
            int by = contentY + row * rowH - scrollOffset;
            NeonButton btn = items.get(i);
            btn.setX(bx);
            btn.setY(by);
            btn.setWidth(colW);
            btn.visible = (by >= contentY - 4) && (by + btn.getHeight() <= contentY + contentH + 4);
            addDrawableChild(btn);
        }

        if (subW > 0) {
            int sx = winX + winW - subW - 6;
            int sy = contentY + 16;
            for (SubItem item : subItems) {
                if (item instanceof SubToggle st) {
                    addDrawableChild(new NeonButton(sx + 4, sy, subW - 8, 18, st::label, st.getter(), st.toggler()));
                    sy += 21;
                } else if (item instanceof SubSlider sl) {
                    addDrawableChild(NeonButton.slider(sx + 4, sy, subW - 8, 18, sl.label(), sl.min(), sl.max(), sl.integer(), sl.getter(), sl.setter()));
                    sy += 21;
                } else if (item instanceof SubSwatches sw) {
                    addDrawableChild(NeonButton.swatches(sx + 4, sy, subW - 8, 18, sw.label(), sw.palette(), sw.getter(), sw.setter()));
                    sy += 21;
                }
            }
        }
    }

    private NeonButton toggleCard(String name, BooleanSupplier state, Runnable click, Runnable rightClick) {
        NeonButton b = new NeonButton(0, 0, 120, 18, () -> name, state, click);
        if (rightClick != null) b.withRightClick(rightClick);
        return b;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x6606060A);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        NeonButton.syncTheme();
        renderBackground(ctx, mouseX, mouseY, delta);

        NeonButton.drawAmbientGlow(ctx, winX, winY, winW, winH, 7, NeonButton.ACCENT, 2);
        NeonButton.fillGradientRounded(ctx, winX, winY, winW, winH, 7, NeonButton.WINDOW_TOP, NeonButton.WINDOW_BOTTOM);
        NeonButton.drawSmoothRoundedOutline(ctx, winX, winY, winW, winH, 7, 0x26FFFFFF);

        ctx.fill(winX + 29, winY + 22, winX + 30, winY + winH - 6, 0x18FFFFFF);
        ctx.fill(winX + 6, winY + 21, winX + winW - 6, winY + 22, 0x18FFFFFF);

        NeonButton.drawCircle(ctx, winX + 12, winY + 12, 3, NeonButton.ACCENT_BRIGHT);
        NeonButton.drawSmallText(ctx, textRenderer, "ARTEM VISUALS", winX + 19, winY + 8, 0xFFF4F4FC, 0.90f);
        String sub = " | " + currentTab.title + "  [Zoom: C]";
        NeonButton.drawSmallText(ctx, textRenderer, sub, winX + 86, winY + 8, NeonButton.ACCENT_BRIGHT, 0.85f);

        if (subTitle != null && !subItems.isEmpty()) {
            int subW = 112;
            int sx = winX + winW - subW - 6;
            int sy = winY + 25;
            int sh = winH - 31;
            NeonButton.fillSmoothRounded(ctx, sx, sy, subW, sh, 5, NeonButton.CARD_OFF);
            NeonButton.drawSmoothRoundedOutline(ctx, sx, sy, subW, sh, 5, (NeonButton.ACCENT & 0x00FFFFFF) | 0x44000000);
            NeonButton.drawSmallText(ctx, textRenderer, subTitle, sx + 6, sy + 5, NeonButton.ACCENT_BRIGHT, 0.85f);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 14)));
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
