package ppl.beacon.mixin.option;

import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private static int clampMaxValue(int viewDistance) {
        return Math.min(viewDistance, 32);
    }
}
