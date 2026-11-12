package com.artem.visuals.mixin;

import com.artem.visuals.config.ArtemConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
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
    private void artem$transformHeldItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                         float swingProgress, ItemStack item, float equipProgress,
                                         MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                         CallbackInfo ci) {
        if (item == null || item.isEmpty()) return;
        ArtemConfig c = ArtemConfig.get();

        boolean isWeapon = item.getItem() instanceof SwordItem
                || item.getItem() instanceof AxeItem
                || item.getItem() instanceof TridentItem;
        if (c.onlyWeapons && !isWeapon) return;

        boolean mainHand = hand == Hand.MAIN_HAND;
        Arm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        float sign = (arm == Arm.RIGHT) ? 1.0f : -1.0f;

        // Кастомная анимация удара (swingStyle)
        if (mainHand && swingProgress > 0.001f && c.swingStyle > 0) {
            float sin1 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
            float sin2 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);

            switch (c.swingStyle) {
                case 1 -> { // Плавный боковой взмах
                    matrices.translate(sign * -0.10f * sin1, 0.04f * sin1, -0.06f * sin1);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * 34.0f * sin1));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * 52.0f * sin1));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-38.0f * sin2));
                }
                case 2 -> { // Блок-хит 1.7 PvP
                    matrices.translate(sign * -0.12f * sin1, 0.05f * sin1, -0.04f * sin1);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * 42.0f * sin1));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-62.0f * sin1));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * 26.0f * sin1));
                }
                case 3 -> { // Резкий выпад вперёд
                    matrices.translate(0.0f, 0.04f * sin1, -0.34f * sin1);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-26.0f * sin1));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * 14.0f * sin1));
                }
                case 4 -> { // Круговой 360°
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-360.0f * swingProgress));
                }
            }
        }

        // Ракурс: смещение по осям X / Y / Z
        matrices.translate(c.swordOffsetX * sign, c.swordOffsetY, c.swordOffsetZ);

        // Ракурс: углы наклона и поворота (Pitch / Yaw / Roll)
        if (Math.abs(c.swordRotX) > 0.01f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(c.swordRotX));
        }
        if (Math.abs(c.swordRotY) > 0.01f) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(c.swordRotY * sign));
        }
        if (Math.abs(c.swordRotZ) > 0.01f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(c.swordRotZ * sign));
        }

        // Размер предмета в руке
        if (c.smallSword) {
            float sc = Math.max(0.25f, Math.min(1.25f, c.swordScale));
            matrices.scale(sc, sc, sc);
        }
    }
}
