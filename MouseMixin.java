package com.artem.visuals.mixin;

import com.artem.visuals.ArtemVisualsClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void artemvisuals$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ArtemVisualsClient.handleZoomScroll(vertical)) {
            ci.cancel();
        }
    }
}
