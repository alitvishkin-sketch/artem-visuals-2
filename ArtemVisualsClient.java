package com.artem.visuals;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.crosshair.CrosshairRenderer;
import com.artem.visuals.effects.EffectManager;
import com.artem.visuals.gui.ArtemScreen;
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
    private static boolean wasAttackPressed = false;
    private static boolean wasMouseDown = false;

    @Override
    public void onInitializeClient() {
        ArtemConfig.get();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.artemvisuals.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.categories.artemvisuals"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                client.setScreen(new ArtemScreen(client.currentScreen));
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
