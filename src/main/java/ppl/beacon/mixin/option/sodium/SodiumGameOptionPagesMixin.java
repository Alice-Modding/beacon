package ppl.beacon.mixin.option.sodium;

import me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import ppl.beacon.BeaconMod;
import ppl.beacon.config.Config;

@Mixin(value = SodiumGameOptionPages.class, remap = false)
public abstract class SodiumGameOptionPagesMixin {
    @ModifyConstant(method = "lambda$general$0", constant = @Constant(intValue = 32))
    private static int bobbyMaxRenderDistance(int oldValue) {
        int overwrite = BeaconMod.getConfig().getRanderConfig().getMaxRenderDistance();
        return Math.max(3, overwrite);
    }
}
