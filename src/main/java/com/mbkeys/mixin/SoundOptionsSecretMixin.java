package com.mbkeys.mixin;

import com.mbkeys.MbKeysScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsSecretMixin extends Screen {
    @Unique private Button lumaboost$secret;

    protected SoundOptionsSecretMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void lumaboost$init(CallbackInfo ci) {
        lumaboost$secret = Button.builder(Component.literal("·"), b ->
                this.minecraft.setScreen(new MbKeysScreen(this)))
                .bounds(this.width / 2 + 130, this.height - 53, 12, 12).build();
        lumaboost$secret.visible = false;
        this.addRenderableWidget(lumaboost$secret);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void lumaboost$render(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (lumaboost$secret != null) {
            long window = GLFW.glfwGetCurrentContext();
            lumaboost$secret.visible = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
            lumaboost$secret.active = lumaboost$secret.visible;
        }
    }
}
