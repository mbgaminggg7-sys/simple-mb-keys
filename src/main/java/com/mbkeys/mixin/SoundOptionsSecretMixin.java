package com.mbkeys.mixin;

import com.mbkeys.MbKeysScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the button once; visibility is updated by the normal Fabric client tick. */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsSecretMixin extends Screen implements SoundOptionsSecretAccess {
    @Unique private Button lumaboost$secret;

    protected SoundOptionsSecretMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void lumaboost$init(CallbackInfo ci) {
        lumaboost$secret = Button.builder(Component.literal("·"), button ->
                this.minecraft.setScreen(new MbKeysScreen(this)))
                .bounds(this.width / 2 + 130, this.height - 53, 12, 12).build();
        lumaboost$secret.visible = false;
        lumaboost$secret.active = false;
        this.addRenderableWidget(lumaboost$secret);
    }

    public Button lumaboost$getSecretButton() { return lumaboost$secret; }
}
