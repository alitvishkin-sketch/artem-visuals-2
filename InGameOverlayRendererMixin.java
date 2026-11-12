package com.artem.visuals.mixin;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void artemvisuals$lowFire(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (ArtemConfig.get().lowFire) {
            matrices.translate(0.0f, -0.28f, 0.0f);
        }
    }
}
package com.artem.visuals.mixin;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void artem$cancelVanillaCrosshair(CallbackInfo ci) {
        if (ArtemConfig.get().customCrosshair) {
            ci.cancel();
        }
    }
}
