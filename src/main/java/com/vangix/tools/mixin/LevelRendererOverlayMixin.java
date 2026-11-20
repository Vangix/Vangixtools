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
    private void vangixtools$afterLevelRendererMethod(CallbackInfo ci) {
        // Временный безопасный хук: вызываем оверлеи только если в аргументах/рендерере всё готово
        // Позже сузим метод на конкретный renderLevel после проверки имени метода в логах
    }
}
