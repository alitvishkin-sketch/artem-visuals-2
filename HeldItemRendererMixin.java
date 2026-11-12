package com.artem.visuals.mixin;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
            )
    )
    private void artemvisuals$scaleFirstPersonItem(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        ArtemConfig c = ArtemConfig.get();
        if (!c.smallSword || item.isEmpty()) return;

        if (c.onlyWeapons) {
            boolean isWeapon = item.getItem() instanceof SwordItem
                    || item.getItem() instanceof MiningToolItem
                    || item.getItem() instanceof TridentItem
                    || item.getItem() instanceof BowItem
                    || item.getItem() instanceof CrossbowItem;
            if (!isWeapon) return;
        }

        float side = (hand == Hand.MAIN_HAND) ? 1.0f : -1.0f;
        matrices.translate(c.swordOffsetX * side, c.swordOffsetY, c.swordOffsetZ);
        float s = Math.max(0.25f, Math.min(1.30f, c.swordScale));
        matrices.scale(s, s, s);
    }
}
