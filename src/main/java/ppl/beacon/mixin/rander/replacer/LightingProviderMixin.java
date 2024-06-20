package ppl.beacon.mixin.rander.replacer;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ppl.beacon.fake.ext.LightingProviderExt;

@Mixin(value = LightingProvider.class)
public abstract class LightingProviderMixin implements LightingProviderExt {
    @Unique
    private final LongSet bobbyActiveColumns = new LongOpenHashSet();

    @Override
    public void beacon$enabledColumn(long pos) {
        this.bobbyActiveColumns.add(pos);
    }

    @Override
    public void beacon$disableColumn(long pos) {
        this.bobbyActiveColumns.remove(pos);
    }

    @Inject(method = "isLightingEnabled", at = @At("HEAD"), cancellable = true)
    private void getLightSection(ChunkSectionPos pos, CallbackInfoReturnable<Boolean> ci) {
        if (bobbyActiveColumns.contains(pos.toChunkPos().toLong())) {
            ci.setReturnValue(true);
        }
    }
}
