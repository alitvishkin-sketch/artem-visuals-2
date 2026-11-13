package com.artem.visuals;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.crosshair.CrosshairRenderer;
import com.artem.visuals.effects.EffectManager;
import com.artem.visuals.gui.ArtemScreen;
import com.artem.visuals.gui.ModernMenuScreen;
import com.artem.visuals.hud.HudManager;
import com.artem.visuals.optimize.Optimizer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ArtemVisualsClient implements ClientModInitializer {
    public static final String MOD_ID = "artemvisuals";
    private static KeyBinding openMenuKey;
    private static KeyBinding zoomKey;
    private static boolean wasAttackPressed = false;
    private static boolean wasMouseDown = false;

    private static float currentZoomMult = 1.0f;
    private static float targetZoomLevel = 0.28f;

    @Override
    public void onInitializeClient() {
        ArtemConfig.get();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.artemvisuals.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.categories.artemvisuals"));

        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.artemvisuals.zoom",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "key.categories.artemvisuals"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                client.setScreen(new ModernMenuScreen(client.currentScreen));
            }
            EffectManager.tick(client);
            handleCpsAndHudDrag(client);
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            float delta = 0f;
            try {
                delta = tickCounter.getTickDelta(false);
            } catch (Throwable ignored) {
            }
            HudManager.render(ctx, delta);
            CrosshairRenderer.render(ctx, delta);
        });

        EffectManager.registerEvents();
        ClientLifecycleEvents.CLIENT_STARTED.register(Optimizer::applyLighting);
    }

    public static boolean isZooming() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.currentScreen == null && zoomKey != null && zoomKey.isPressed();
    }

    public static double getZoomFovMultiplier() {
        float goal = isZooming() ? targetZoomLevel : 1.0f;
        currentZoomMult += (goal - currentZoomMult) * 0.22f;
        if (!isZooming() && Math.abs(1.0f - currentZoomMult) < 0.005f) {
            currentZoomMult = 1.0f;
            targetZoomLevel = 0.28f;
        }
        return currentZoomMult;
    }

    public static boolean handleZoomScroll(double vertical) {
        if (!isZooming()) return false;
        targetZoomLevel = Math.max(0.10f, Math.min(0.65f, targetZoomLevel - (float) vertical * 0.04f));
        return true;
    }

    private static void handleCpsAndHudDrag(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        boolean attack = client.options != null && client.options.attackKey.isPressed();
        if (attack && !wasAttackPressed) {
            HudManager.recordClick();
        }
        wasAttackPressed = attack;

        ArtemConfig c = ArtemConfig.get();
        if (!c.hudEditMode || client.currentScreen != null) {
            if (!leftDown) HudManager.mouseReleased();
            wasMouseDown = leftDown;
            return;
        }

        double mx = client.mouse.getX() * client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
        double my = client.mouse.getY() * client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();

        if (leftDown && !wasMouseDown) {
            HudManager.mouseClicked(mx, my);
        } else if (leftDown) {
            HudManager.mouseDragged(mx, my);
        } else if (wasMouseDown) {
            HudManager.mouseReleased();
        }
        wasMouseDown = leftDown;
    }
}
