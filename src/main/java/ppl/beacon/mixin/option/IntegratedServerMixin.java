package ppl.beacon.mixin.option;

import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ppl.beacon.BeaconMod;
import ppl.beacon.config.Config;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"), index = 1)
    private int viewDistanceOverwrite(int viewDistance) {
        int overwrite = Config.renderer.getViewDistanceOverwrite();
        if (overwrite != 0) {
            viewDistance = overwrite;
        }
        return viewDistance;
    }
}
