package com.artem.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Профили настроек в config/artemvisuals/profiles/*.json
 */
public final class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("artemvisuals").resolve("profiles");

    private ProfileManager() {}

    public static List<String> listProfiles() {
        try {
            Files.createDirectories(DIR);
            try (Stream<Path> s = Files.list(DIR)) {
                return s.filter(p -> p.toString().endsWith(".json"))
                        .map(p -> p.getFileName().toString().replace(".json", ""))
                        .sorted()
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static boolean saveProfile(String name) {
        if (name == null || name.isBlank()) return false;
        String safe = name.replaceAll("[^a-zA-Z0-9_\\-а-яА-ЯёЁ ]", "").trim();
        if (safe.isEmpty()) return false;
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(safe + ".json");
            Files.writeString(file, GSON.toJson(ArtemConfig.get()));
            return true;
        } catch (IOException e) {
            System.err.println("[ArtemVisuals] Ошибка сохранения профиля: " + e);
            return false;
        }
    }

    public static boolean loadProfile(String name) {
        Path file = DIR.resolve(name + ".json");
        if (!Files.exists(file)) return false;
        try {
            ArtemConfig loaded = GSON.fromJson(Files.readString(file), ArtemConfig.class);
            if (loaded == null) return false;
            // Копируем поля в текущий инстанс
            ArtemConfig cur = ArtemConfig.get();
            copy(loaded, cur);
            cur.save();
            return true;
        } catch (Exception e) {
            System.err.println("[ArtemVisuals] Ошибка загрузки профиля: " + e);
            return false;
        }
    }

    public static boolean deleteProfile(String name) {
        try {
            return Files.deleteIfExists(DIR.resolve(name + ".json"));
        } catch (IOException e) {
            return false;
        }
    }

    private static void copy(ArtemConfig from, ArtemConfig to) {
        to.masterEnabled = from.masterEnabled;
        to.leafParticles = from.leafParticles;
        to.precipitationEffects = from.precipitationEffects;
        to.waterSparkles = from.waterSparkles;
        to.fireflies = from.fireflies;
        to.blockBreakEffects = from.blockBreakEffects;
        to.softLighting = from.softLighting;
        to.smoothLighting = from.smoothLighting;
        to.density = from.density;
        to.radius = from.radius;
        to.lightingLevel = from.lightingLevel;
        to.adaptive = from.adaptive;
        to.adaptiveThreshold = from.adaptiveThreshold;
        to.showFps = from.showFps;
        to.showPing = from.showPing;
        to.showXyz = from.showXyz;
        to.showCps = from.showCps;
        to.showWasd = from.showWasd;
        to.showServer = from.showServer;
        to.hudPositions = from.hudPositions != null ? new java.util.HashMap<>(from.hudPositions) : new java.util.HashMap<>();
        to.customCrosshair = from.customCrosshair;
        to.crosshairStyle = from.crosshairStyle;
        to.crosshairColor = from.crosshairColor;
        to.crosshairSize = from.crosshairSize;
        to.crosshairGap = from.crosshairGap;
        to.crosshairThickness = from.crosshairThickness;
        to.crosshairOutline = from.crosshairOutline;
        to.crosshairDynamic = from.crosshairDynamic;
        // backup / gamma не трогаем при загрузке профиля
    }
}
