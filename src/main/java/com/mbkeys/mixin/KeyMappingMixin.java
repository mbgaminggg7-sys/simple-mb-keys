package com.mbkeys.mixin;

import com.mbkeys.MbKeysClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the same two places vanilla uses for every key press, so extra keys behave exactly like normal ones. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "click", at = @At("HEAD"))
    private static void mbkeys$click(InputConstants.Key key, CallbackInfo ci) {
        MbKeysClient.onExtraKeyPressed(key);
    }

    @Inject(method = "set", at = @At("HEAD"))
    private static void mbkeys$set(InputConstants.Key key, boolean pressed, CallbackInfo ci) {
        MbKeysClient.onExtraKeySet(key, pressed);
    }
}
