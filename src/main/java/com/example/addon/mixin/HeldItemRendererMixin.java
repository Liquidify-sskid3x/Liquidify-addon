package com.example.addon.mixin;

import com.example.addon.modules.Animations;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Inject(method = "applyEquipOffset", at = @At("TAIL"))
    private void applyHandView(MatrixStack matrices, Arm arm, float equipProgress, CallbackInfo ci) {
        Animations mod = Animations.INSTANCE;
        if (mod == null || !mod.isActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.player.getMainHandStack().isEmpty()) return;

        // 1.7 sword/block animation
        if (mod.oldBlock.get() && mc.options.useKey.isPressed()) {
            matrices.translate(
                mod.handDistance.get().floatValue(),
                mod.handHeight.get().floatValue(),
                0f
            );

            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(13f));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(78f));
        }


        // Swing speed scaling
        float swingScale = mod.swingSpeed.get().floatValue();
        matrices.scale(swingScale, swingScale, swingScale);
    }
}
