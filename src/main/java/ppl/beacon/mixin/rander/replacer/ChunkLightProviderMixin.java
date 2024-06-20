package ppl.beacon.mixin.rander.replacer;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ppl.beacon.fake.ext.ChunkLightProviderExt;

@Mixin(value = ChunkLightProvider.class, targets = {
        "ca.spottedleaf.starlight.common.light.StarLightInterface$1",
        "ca.spottedleaf.starlight.common.light.StarLightInterface$2"
})
public abstract class ChunkLightProviderMixin implements ChunkLightProviderExt {
    @Unique private final Long2ObjectMap<ChunkNibbleArray> sectionData = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    @Unique private final Long2ObjectMap<ChunkNibbleArray> originalSectionData = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    @Override
    public void beacon$addSectionData(long pos, ChunkNibbleArray data) {
        this.sectionData.put(pos, data);
        this.originalSectionData.remove(pos);
    }

    @Override
    public void beacon$removeSectionData(long pos) {
        this.sectionData.remove(pos);
        this.originalSectionData.remove(pos);
    }

    @Override
    public void beacon$setTinted(long pos, int delta) {
        if (delta != 0) {
            ChunkNibbleArray original = this.originalSectionData.get(pos);
            if (original == null) {
                if ((original = this.sectionData.get(pos)) == null) return;
                this.originalSectionData.put(pos, original);
            }

            ChunkNibbleArray updated = new ChunkNibbleArray();

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        updated.set(x, y, z, Math.min(Math.max(original.get(x, y, z) + delta, 0), 15));
                    }
                }
            }

            this.sectionData.put(pos, updated);
        } else {
            ChunkNibbleArray original = this.originalSectionData.remove(pos);
            if (original == null) return;
            sectionData.put(pos, original);
        }
    }

    @Inject(method = "getLightSection(Lnet/minecraft/util/math/ChunkSectionPos;)Lnet/minecraft/world/chunk/ChunkNibbleArray;", at = @At("HEAD"), cancellable = true)
    private void getLightSection(ChunkSectionPos pos, CallbackInfoReturnable<ChunkNibbleArray> ci) {
        ChunkNibbleArray data = this.sectionData.get(pos.asLong());
        if (data == null) return;
        ci.setReturnValue(data);
    }

    @Inject(method = "getLightLevel(Lnet/minecraft/util/math/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private void getLightSection(BlockPos blockPos, CallbackInfoReturnable<Integer> ci) {
        ChunkNibbleArray data = this.sectionData.get(ChunkSectionPos.from(blockPos).asLong());
        if (data == null) return;
        ci.setReturnValue(data.get(
                ChunkSectionPos.getLocalCoord(blockPos.getX()),
                ChunkSectionPos.getLocalCoord(blockPos.getY()),
                ChunkSectionPos.getLocalCoord(blockPos.getZ())
        ));
    }
}
