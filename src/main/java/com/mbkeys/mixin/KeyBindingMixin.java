package com.mbkeys.mixin;

import com.mbkeys.MbKeysClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the same two places vanilla uses for every key press, so extra keys behave exactly like normal ones. */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Inject(method = "onKeyPressed", at = @At("HEAD"))
    private static void mbkeys$onKeyPressed(InputUtil.Key key, CallbackInfo ci) {
        MbKeysClient.onExtraKeyPressed(key);
    }

    @Inject(method = "setKeyPressed", at = @At("HEAD"))
    private static void mbkeys$setKeyPressed(InputUtil.Key key, boolean pressed, CallbackInfo ci) {
        MbKeysClient.onExtraKeySet(key, pressed);
    }
}
