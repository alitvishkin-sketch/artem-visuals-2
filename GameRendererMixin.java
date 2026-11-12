package com.artem.visuals.mixin;

import com.artem.visuals.ArtemVisualsClient;
import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void artemvisuals$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (ArtemConfig.get().noHurtCam) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void artemvisuals$smoothZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double mult = ArtemVisualsClient.getZoomFovMultiplier();
        if (mult < 0.999) {
            cir.setReturnValue(cir.getReturnValue() * mult);
        }
    }
}
