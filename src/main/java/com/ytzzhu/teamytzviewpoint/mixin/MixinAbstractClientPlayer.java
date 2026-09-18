package com.ytzzhu.teamytzviewpoint.mixin;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraft.client.entity.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {

    @Inject(method = "func_175149_v", at = @At("HEAD"), cancellable = true)
    public void onIsSpectator(CallbackInfoReturnable<Boolean> cir) {
        if (ViewpointCameraManager.isOverlooking()) {
            cir.setReturnValue(false);
        }
    }
}