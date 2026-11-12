package com.artem.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Главный конфиг мода + позиции HUD + настройки прицела.
 * Профили хранятся отдельно через ProfileManager.
 */
public class ArtemConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("artemvisuals.json");
    private static ArtemConfig instance;

    // ——— Общие ———
    public boolean masterEnabled = true;

    // ——— Эффекты ———
    public boolean leafParticles = true;
    public boolean precipitationEffects = true;
    public boolean waterSparkles = true;
    public boolean fireflies = true;
    public boolean blockBreakEffects = true;
    public boolean softLighting = false;
    public boolean smoothLighting = true;
    public int density = 1;          // 0 low / 1 med / 2 high
    public int radius = 1;
    public int lightingLevel = 1;
    public boolean adaptive = true;
    public int adaptiveThreshold = 1;

    // ——— HUD ———
    public boolean showFps = true;
    public boolean showPing = true;
    public boolean showXyz = true;
    public boolean showCps = true;
    public boolean showWasd = true;
    public boolean showServer = true;
    public boolean hudEditMode = false;
    public Map<String, HudPos> hudPositions = new HashMap<>();

    // ——— Прицел ———
    public boolean customCrosshair = true;
    public int crosshairStyle = 0;   // 0=cross, 1=dot, 2=circle, 3=plus
    public int crosshairColor = 0xFF9B59FF; // ARGB neon purple
    public int crosshairSize = 6;
    public int crosshairGap = 2;
    public int crosshairThickness = 1;
    public boolean crosshairOutline = true;
    public boolean crosshairDynamic = false; // expand on attack

    // ——— Оптимизация (бэкап) ———
    public double originalGamma = -1;
    public Backup backup;

    public static class Backup {
        public int particles;
        public double entityDistance;
        public int biomeBlend;
        public int mipmaps;
        public int clouds;
        public boolean shadows;
    }

    public static class HudPos {
        public int x = 4;
        public int y = 4;
        public HudPos() {}
        public HudPos(int x, int y) { this.x = x; this.y = y; }
    }

    public double densityMul() { return new double[]{0.5, 1.0, 1.75}[clamp(density)]; }
    public int radiusBlocks() { return new int[]{8, 12, 16}[clamp(radius)]; }
    public double gammaLevel() { return new double[]{0.4, 0.6, 0.8}[clamp(lightingLevel)]; }
    public int fpsThreshold() { return new int[]{20, 30, 45}[clamp(adaptiveThreshold)]; }

    private static int clamp(int v) { return Math.max(0, Math.min(2, v)); }

    public HudPos getHudPos(String id, int defX, int defY) {
        return hudPositions.computeIfAbsent(id, k -> new HudPos(defX, defY));
    }

    public static ArtemConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static ArtemConfig load() {
        try {
            if (Files.exists(PATH)) {
                ArtemConfig c = GSON.fromJson(Files.readString(PATH), ArtemConfig.class);
                if (c != null) {
                    if (c.hudPositions == null) c.hudPositions = new HashMap<>();
                    return c;
                }
            }
        } catch (Exception ignored) {
        }
        ArtemConfig c = new ArtemConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[ArtemVisuals] Не удалось сохранить конфиг: " + e);
        }
    }

    /** Сброс к значениям по умолчанию (без удаления бэкапа освещения). */
    public void resetDefaults() {
        masterEnabled = true;
        leafParticles = precipitationEffects = waterSparkles = fireflies = blockBreakEffects = true;
        softLighting = false;
        smoothLighting = true;
        density = radius = lightingLevel = adaptiveThreshold = 1;
        adaptive = true;
        showFps = showPing = showXyz = showCps = showWasd = showServer = true;
        hudEditMode = false;
        customCrosshair = true;
        crosshairStyle = 0;
        crosshairColor = 0xFF9B59FF;
        crosshairSize = 6;
        crosshairGap = 2;
        crosshairThickness = 1;
        crosshairOutline = true;
        crosshairDynamic = false;
        save();
    }
}
