package com.artem.visuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.artem.visuals.gui.ModernUi.*;

/**
 * Современное меню: открывается по RShift с плавной анимацией (масштаб + fade),
 * боковая панель категорий, анимированные переключатели и слайдеры, шрифт Inter.
 */
public class ModernMenuScreen extends Screen {
    private static final int W = 520, H = 330, SIDE = 132, ROW_H = 32, RADIUS = 14;
    private static final int BG = 0xF2111117, SIDE_BG = 0xF20C0C11, ROW_BG = 0xFF1A1A23, ROW_HOVER = 0xFF22222D;
    private static final int TEXT = 0xFFEDEDF3, TEXT_DIM = 0xFF8B8B9A, OFF = 0xFF3A3A48;

    private enum Kind { TOGGLE, SLIDER }

    private record Row(Kind kind, String label, String key, double min, double max) {}

    private record Category(String name, List<Row> rows) {}

    private final Screen parent;
    private final List<Category> categories = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();
    private int active = 0;
    private float scroll = 0, scrollTarget = 0;
    private String dragging = null;
    private long openedAt = System.nanoTime(), last = System.nanoTime();
    private boolean closing = false;

    public ModernMenuScreen(Screen parent) {
        super(Text.literal("Artem Visuals"));
        this.parent = parent;
        build();
    }

    private void build() {
        categories.add(new Category("HUD", List.of(
            t("FPS", "showFps"), t("Пинг", "showPing"), t("Координаты", "showXyz"), t("CPS", "showCps"),
            t("WASD", "showWasd"), t("Клавиши", "showKeystrokes"), t("Таргет-HUD", "showTargetHud"),
            t("Эффекты зелий", "showPotions"), t("Миникарта", "showMinimap"), t("Бинды", "showHotKeys"),
            t("Кулдауны", "showCooldowns"), t("Список модулей", "showArrayList"), t("Ник", "showNickname"),
            s("Масштаб HUD", "hudScale", 0.5, 2.0))));
        categories.add(new Category("Эффекты", List.of(
            t("Листья", "leafParticles"), t("Блики на воде", "waterSparkles"), t("Ломание блоков", "blockBreakEffects"),
            t("Мягкий свет", "softLighting"), t("Частицы ударов", "hitParticles"), t("Крит-искры", "critSparks"),
            t("Виньетка", "screenVignette"), t("Следы снарядов", "projectileTrails"), t("Свечение руки", "handGlow"),
            t("Низкий огонь", "lowFire"))));
        categories.add(new Category("Прицел и меч", List.of(
            t("Свой прицел", "customCrosshair"), t("Обводка прицела", "crosshairOutline"),
            t("Динамический прицел", "crosshairDynamic"), s("Размер прицела", "crosshairSize", 1, 20),
            t("Маленький меч", "smallSword"), s("Размер меча", "swordScale", 0.3, 1.5))));
        categories.add(new Category("Утилиты", List.of(
            t("Авто-спринт", "autoSprint"), t("Мало брони", "lowArmorAlert"), t("Конец зелья", "potionExpiryAlert"),
            t("Координаты смерти", "deathCoords"), t("Таймер перла", "pearlTimer"),
            t("Траектория перла", "pearlTrajectory"), t("Превью пласта", "trapPlastPreview"),
            t("Таймер ивента", "showEventTimer"))));
    }

    private static Row t(String label, String key) { return new Row(Kind.TOGGLE, label, key, 0, 1); }
    private static Row s(String label, String key, double min, double max) { return new Row(Kind.SLIDER, label, key, min, max); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        openedAt = System.nanoTime();
        ConfigBridge.save();
    }

    private void finishClose() { MinecraftClient.getInstance().setScreen(parent); }

    // ---------- геометрия ----------
    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }
    private int listX() { return x0() + SIDE + 16; }
    private int listY() { return y0() + 58; }
    private int listW() { return W - SIDE - 32; }
    private int listH() { return H - 58 - 14; }

    private float step(String key, boolean on, float dt, float speed) {
        float v = anim.getOrDefault(key, on ? 1f : 0f);
        float target = on ? 1f : 0f;
        v += (target - v) * Math.min(1f, dt * speed);
        anim.put(key, v);
        return v;
    }

    // ---------- отрисовка ----------
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = (now - last) / 1_000_000_000f;
        last = now;

        float p = Math.min(1f, (now - openedAt) / 220_000_000f);
        float e = easeOutCubic(p);
        float vis = closing ? 1f - e : e;
        if (closing && p >= 1f) { finishClose(); return; }
        globalAlpha = vis;

        c.fill(0, 0, width, height, argb((int) (150 * vis), 0, 0, 0));

        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
        int accent = ConfigBridge.accent();
        int x0 = x0(), y0 = y0();

        float sc = 0.93f + 0.07f * vis;
        c.getMatrices().push();
        c.getMatrices().translate(width / 2f, height / 2f, 0);
        c.getMatrices().scale(sc, sc, 1f);
        c.getMatrices().translate(-width / 2f, -height / 2f, 0);

        shadow(c, x0, y0, W, H, RADIUS, 14);
        rrect(c, x0, y0, W, H, RADIUS, BG);
        rrect(c, x0, y0, SIDE, H, RADIUS, SIDE_BG);
        c.fill(x0 + SIDE - RADIUS, y0, x0 + SIDE, y0 + H, mulAlpha(SIDE_BG));

        // логотип
        rrect(c, x0 + 16, y0 + 18, 22, 22, 7, accent);
        textCentered(c, "A", x0 + 27, y0 + 25, 0xFFFFFFFF, true);
        text(c, "Artem", x0 + 46, y0 + 20, TEXT, true);
        text(c, "Visuals", x0 + 46, y0 + 31, TEXT_DIM, false);

        // категории
        for (int i = 0; i < categories.size(); i++) {
            int by = y0 + 62 + i * 34;
            boolean hov = mx >= x0 + 10 && mx < x0 + SIDE - 10 && my >= by && my < by + 28;
            float a = step("cat" + i, i == active, dt, 14f);
            float h = step("cath" + i, hov, dt, 16f);
            int bg = lerp(0x00000000, withAlpha(accent, 60), a);
            bg = lerp(bg, 0x30FFFFFF, h * (1 - a));
            rrect(c, x0 + 10, by, SIDE - 20, 28, 9, bg);
            if (a > 0.02f) rrect(c, x0 + 10, by + 7, 3, 14, 2, withAlpha(accent, (int) (255 * a)));
            text(c, categories.get(i).name(), x0 + 24, by + 10, lerp(TEXT_DIM, TEXT, Math.max(a, h)), i == active);
        }
        text(c, "RShift — закрыть", x0 + 16, y0 + H - 22, 0xFF5E5E6C, false);

        // заголовок
        Category cat = categories.get(active);
        text(c, cat.name(), listX(), y0 + 22, TEXT, true);
        text(c, cat.rows().size() + " параметров", listX(), y0 + 36, TEXT_DIM, false);

        // список
        int lx = listX(), ly = listY(), lw = listW(), lh = listH();
        c.enableScissor(lx - 2, ly, lx + lw + 2, ly + lh);
        for (int i = 0; i < cat.rows().size(); i++) {
            Row r = cat.rows().get(i);
            int ry = (int) (ly + i * ROW_H - scroll);
            if (ry + ROW_H < ly || ry > ly + lh) continue;
            boolean hov = mx >= lx && mx < lx + lw && my >= ry && my < ry + ROW_H - 4 && my >= ly && my < ly + lh;
            float hv = step("h" + cat.name() + i, hov, dt, 16f);
            rrect(c, lx, ry, lw, ROW_H - 4, 9, lerp(ROW_BG, ROW_HOVER, hv));
            text(c, r.label(), lx + 14, ry + 10, TEXT, false);

            if (r.kind() == Kind.TOGGLE) {
                boolean on = ConfigBridge.getBool(r.key());
                float a = step("t" + r.key(), on, dt, 16f);
                int sx = lx + lw - 14 - 34, sy = ry + 6;
                rrect(c, sx, sy, 34, 18, 9, lerp(OFF, accent, a));
                int kx = sx + 2 + (int) (16 * a);
                rrect(c, kx, sy + 2, 14, 14, 7, 0xFFFFFFFF);
            } else {
                double v = ConfigBridge.getNum(r.key(), r.min());
                float frac = (float) ((v - r.min()) / (r.max() - r.min()));
                frac = Math.max(0f, Math.min(1f, frac));
                float a = step("s" + r.key(), frac > 0, dt, 20f);
                String val = String.format(java.util.Locale.ROOT, r.max() <= 5 ? "%.2f" : "%.0f", v);
                int tw = width(val, false);
                text(c, val, lx + lw - 14 - tw, ry + 10, TEXT_DIM, false);
                int trackX = lx + lw - 14 - 120 - tw - 12, trackY = ry + 12;
                rrect(c, trackX, trackY, 120, 4, 2, OFF);
                rrect(c, trackX, trackY, Math.max(4, (int) (120 * frac)), 4, 2, accent);
                rrect(c, trackX + (int) (120 * frac) - 5, trackY - 3, 10, 10, 5, 0xFFFFFFFF);
            }
        }
        c.disableScissor();

        c.getMatrices().pop();
        globalAlpha = 1f;
    }

    // ---------- ввод ----------
    private double sliderTrackX(Row r) {
        double v = ConfigBridge.getNum(r.key(), r.min());
        String val = String.format(java.util.Locale.ROOT, r.max() <= 5 ? "%.2f" : "%.0f", v);
        return listX() + listW() - 14 - 120 - width(val, false) - 12;
    }

    private void applySlider(Row r, double mx) {
        double frac = Math.max(0, Math.min(1, (mx - sliderTrackX(r)) / 120.0));
        ConfigBridge.setNum(r.key(), r.min() + (r.max() - r.min()) * frac);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing || button != 0) return false;
        int x0 = x0(), y0 = y0();
        for (int i = 0; i < categories.size(); i++) {
            int by = y0 + 62 + i * 34;
            if (mx >= x0 + 10 && mx < x0 + SIDE - 10 && my >= by && my < by + 28) {
                active = i; scroll = scrollTarget = 0; return true;
            }
        }
        Category cat = categories.get(active);
        int lx = listX(), ly = listY();
        if (mx >= lx && mx < lx + listW() && my >= ly && my < ly + listH()) {
            int i = (int) ((my - ly + scroll) / ROW_H);
            if (i >= 0 && i < cat.rows().size()) {
                Row r = cat.rows().get(i);
                if (r.kind() == Kind.TOGGLE) ConfigBridge.setBool(r.key(), !ConfigBridge.getBool(r.key()));
                else { dragging = r.key(); applySlider(r, mx); }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            for (Row r : categories.get(active).rows()) if (r.key().equals(dragging)) applySlider(r, mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) { dragging = null; ConfigBridge.save(); }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int max = Math.max(0, categories.get(active).rows().size() * ROW_H - listH());
        scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - v * 28));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
