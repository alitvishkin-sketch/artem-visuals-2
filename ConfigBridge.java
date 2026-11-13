package com.artem.visuals.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Доступ к полям ArtemConfig по имени через рефлексию: меню не зависит от типов полей
 * (boolean / int / float / double), поэтому компилируется с любой версией ArtemConfig.
 */
public final class ConfigBridge {
    private static final String CLS = "com.artem.visuals.config.ArtemConfig";
    private static Class<?> cls;
    private static Object instance;

    private ConfigBridge() {}

    private static Object cfg() {
        try {
            if (cls == null) cls = Class.forName(CLS);
            if (instance == null) instance = cls.getMethod("get").invoke(null);
            return instance;
        } catch (Exception e) {
            return null;
        }
    }

    private static Field field(String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean getBool(String name) {
        Object c = cfg();
        Field f = c == null ? null : field(name);
        try { return f != null && f.getBoolean(c); } catch (Exception e) { return false; }
    }

    public static void setBool(String name, boolean v) {
        Object c = cfg();
        Field f = c == null ? null : field(name);
        try { if (f != null) f.setBoolean(c, v); } catch (Exception ignored) {}
    }

    public static double getNum(String name, double def) {
        Object c = cfg();
        Field f = c == null ? null : field(name);
        try { return f == null ? def : ((Number) f.get(c)).doubleValue(); } catch (Exception e) { return def; }
    }

    public static void setNum(String name, double v) {
        Object c = cfg();
        Field f = c == null ? null : field(name);
        if (f == null) return;
        try {
            Class<?> t = f.getType();
            if (t == int.class) f.setInt(c, (int) Math.round(v));
            else if (t == float.class) f.setFloat(c, (float) v);
            else if (t == double.class) f.setDouble(c, v);
        } catch (Exception ignored) {}
    }

    public static int accent() {
        int a = (int) getNum("hudAccentColor", 0x7C5CFF);
        return 0xFF000000 | (a & 0xFFFFFF);
    }

    public static void save() {
        Object c = cfg();
        if (c == null) return;
        try {
            Method m = cls.getMethod("save");
            m.invoke(java.lang.reflect.Modifier.isStatic(m.getModifiers()) ? null : c);
        } catch (Exception ignored) {}
    }
}
