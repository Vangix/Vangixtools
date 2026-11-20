package com.vangix.tools.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vangix.tools.VangixClientEntry;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererOverlayMixin {

    @Inject(method = "*", at = @At("RETURN"), require = 0)
    private void vangixtools$onReturn(CallbackInfo ci) {
        // Без доступа к аргументам метода в @Inject(method="*") безопаснее вызывать
        // через VangixClientEntry.renderWorldOverlaysFromLevelRenderer(this)
        VangixClientEntry.renderWorldOverlaysFromLevelRenderer(this);
    }
}
