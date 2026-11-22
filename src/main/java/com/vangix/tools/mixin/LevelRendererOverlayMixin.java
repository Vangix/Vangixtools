package com.vangix.tools.mixin;

import com.vangix.tools.VangixClientEntry;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererOverlayMixin {

    @Inject(method = "*", at = @At("RETURN"), require = 0)
    private void vangixtools$onReturn(CallbackInfo ci) {
        VangixClientEntry.renderWorldOverlaysFromLevelRenderer(this);
    }
}
