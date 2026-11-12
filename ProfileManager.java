package com.artem.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Менеджер профилей ARTEM VISUALS 3.0 (включая профиль FunTime).
 */
public final class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("artemvisuals_profiles");

    private ProfileManager() {}

    public static boolean saveProfile(String rawName) {
        String name = sanitize(rawName);
        if (name.isEmpty()) return false;
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(name + ".json");
            Files.writeString(file, GSON.toJson(ArtemConfig.get()), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean loadProfile(String rawName) {
        String name = sanitize(rawName);
        if (name.isEmpty()) return false;
        try {
            Path file = DIR.resolve(name + ".json");
            if (!Files.exists(file)) return false;
            String json = Files.readString(file, StandardCharsets.UTF_8);
            ArtemConfig loaded = GSON.fromJson(json, ArtemConfig.class);
            if (loaded == null) return false;
            ArtemConfig.replaceInstance(loaded);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean deleteProfile(String rawName) {
        String name = sanitize(rawName);
        if (name.isEmpty()) return false;
        try {
            return Files.deleteIfExists(DIR.resolve(name + ".json"));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean exists(String rawName) {
        String name = sanitize(rawName);
        if (name.isEmpty()) return false;
        return Files.exists(DIR.resolve(name + ".json"));
    }

    public static List<String> listProfiles() {
        List<String> out = new ArrayList<>();
        try {
            if (!Files.exists(DIR)) return out;
            try (Stream<Path> s = Files.list(DIR)) {
                s.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    String fn = p.getFileName().toString();
                    out.add(fn.substring(0, fn.length() - 5));
                });
            }
            Collections.sort(out);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("[^a-zA-Zа-яА-Я0-9_\\- ]", "");
    }
}
