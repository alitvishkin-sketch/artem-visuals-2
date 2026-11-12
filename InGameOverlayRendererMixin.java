package com.artem.visuals.mixin;

import com.artem.visuals.config.ArtemConfig;
import com.artem.visuals.hud.HudManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void artem$onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ArtemConfig.get().customCrosshair) {
            HudManager.renderCustomCrosshair(context);
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void artem$onRenderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HudManager.render(context, tickCounter.getTickDelta(true));
    }
}
