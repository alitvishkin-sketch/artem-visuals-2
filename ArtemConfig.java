package com.artem.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Единая конфигурация ARTEM VISUALS 3.0 с поддержкой профилей, биндов, HUD-масштабов и импорта/экспорта.
 */
public class ArtemConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("artemvisuals.json");
    private static ArtemConfig INSTANCE;

    public static class HudPos {
        public int x;
        public int y;
        public float scale = 1.0f;

        public HudPos() {}

        public HudPos(int x, int y) {
            this.x = x;
            this.y = y;
            this.scale = 1.0f;
        }
    }

    // Базовые настройки 2.0 (полная совместимость)
    public boolean masterEnabled = true;
    public boolean leafParticles = true;
    public boolean precipitationEffects = true;
    public boolean waterSparkles = true;
    public boolean fireflies = true;
    public boolean blockBreakEffects = true;
    public boolean softLighting = true;
    public boolean adaptive = true;
    public int density = 1;
    public int radius = 1;

    // HUD 2.0 + 3.0
    public boolean hudEditMode = false;
    public boolean showServer = true;
    public boolean showFps = false;
    public boolean showPing = false;
    public boolean showXyz = true;
    public boolean showCps = false;
    public boolean showWasd = false;
    public boolean showKeystrokes = false;
    public boolean showSpeed = false;
    public boolean showDirection = false;
    public boolean showNickname = false;
    public boolean showPlaytime = false;
    public boolean showArmor = true;
    public boolean showHealthHud = false;
    public boolean showHungerHud = false;
    public boolean showPotions = true;
    public boolean showTargetHud = true;
    public boolean showHotKeys = true;
    public boolean showArrayList = true;
    public boolean showRadar = false;
    public boolean showMinimap = false;

    // Прицел
    public boolean customCrosshair = true;
    public int crosshairStyle = 0; // 0=Крест, 1=Точка, 2=Круг, 3=Плюс
    public int crosshairSize = 4;
    public int crosshairGap = 2;
    public int crosshairThickness = 1;
    public int crosshairColor = 0xFF00F5FF;
    public boolean crosshairOutline = true;
    public boolean crosshairDynamic = true;

    // Visual 3.0
    public boolean esp = false;
    public int espStyle = 0; // 0=Полный бокс, 1=Углы, 2=Двойной контур
    public int espColor = 0xFF6C5CE7;
    public float espAlpha = 0.85f;
    public boolean espPulse = true;

    public boolean playerHighlight = true;
    public int playerHighlightColor = 0xFF9D8DF7;
    public float playerHighlightAlpha = 0.85f;

    public boolean tracers = false;
    public int tracersColor = 0xFF6C5CE7;
    public float tracersAlpha = 0.70f;

    public boolean chams = false;
    public int chamsColor = 0xFF6C5CE7;
    public float chamsAlpha = 0.45f;

    public boolean nameTags = true;
    public float nameTagsScale = 0.85f;
    public boolean nameTagsHealth = true;
    public boolean nameTagsDistance = true;

    public boolean itemEsp = true;
    public int itemEspColor = 0xFF00F5FF;
    public boolean itemEspLabels = true;

    public boolean mobEsp = false;
    public int mobEspColor = 0xFFFFB703;

    public boolean entityHighlight = false;
    public int entityHighlightColor = 0xFFE056FD;

    public boolean hitParticles = true;
    public int hitParticlesCount = 14;
    public int hitParticlesColor = 0xFF9D8DF7;

    public boolean damageIndicator = true;
    public float damageIndicatorSize = 0.90f;

    public boolean nimb = true;
    public int nimbColor = 0xFF6C5CE7;
    public float nimbSize = 0.35f;

    public boolean trails = true;
    public int trailLength = 24;
    public int trailColor = 0xFF6C5CE7;

    public boolean targetEsp = true;
    public int targetEspColor = 0xFF6C5CE7;
    public float targetEspSpeed = 1.0f;

    public boolean jumpCircle = true;
    public float jumpCircleRadius = 1.2f;
    public int jumpCircleColor = 0xFF6C5CE7;

    public boolean blockOverlay = true;
    public float blockOverlayAlpha = 0.85f;
    public int blockOverlayColor = 0xFF6C5CE7;

    public boolean renderTweaks = true;
    public boolean motionBlur = false;
    public boolean worldCustomizer = true;
    public int worldTimeMode = 0; // 0=Сервер, 1=День, 2=Закат, 3=Ночь
    public boolean skyTint = true;

    // Тема, RGB, язык и FunTime
    public int clientAccent = 0xFF6C5CE7;
    public boolean rgbMode = false;
    public float rgbSpeed = 1.0f;
    public boolean autoFunTime = true;
    public String language = "RU"; // "RU" или "EN"

    public Map<String, HudPos> hudPositions = new HashMap<>();
    public Map<String, Integer> keybinds = new HashMap<>();

    public static ArtemConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static void replaceInstance(ArtemConfig next) {
        if (next != null) {
            if (next.hudPositions == null) next.hudPositions = new HashMap<>();
            if (next.keybinds == null) next.keybinds = new HashMap<>();
            INSTANCE = next;
            INSTANCE.save();
        }
    }

    private static ArtemConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                ArtemConfig cfg = GSON.fromJson(json, ArtemConfig.class);
                if (cfg != null) {
                    if (cfg.hudPositions == null) cfg.hudPositions = new HashMap<>();
                    if (cfg.keybinds == null) cfg.keybinds = new HashMap<>();
                    return cfg;
                }
            }
        } catch (Exception ignored) {
        }
        ArtemConfig fresh = new ArtemConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public String exportBase64() {
        String json = GSON.toJson(this);
        return "AV3:" + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public boolean importBase64(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            String trimmed = raw.trim();
            String json = trimmed.startsWith("AV3:")
                    ? new String(Base64.getDecoder().decode(trimmed.substring(4)), StandardCharsets.UTF_8)
                    : trimmed;
            ArtemConfig parsed = GSON.fromJson(json, ArtemConfig.class);
            if (parsed == null) return false;
            replaceInstance(parsed);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void resetDefaults() {
        ArtemConfig fresh = new ArtemConfig();
        replaceInstance(fresh);
    }

    public HudPos getHudPos(String id, int defaultX, int defaultY) {
        return hudPositions.computeIfAbsent(id, k -> new HudPos(defaultX, defaultY));
    }

    public int fpsThreshold() {
        return 45;
    }
}
