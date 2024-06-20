package ppl.beacon.fake.ext;

import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightingView;

public interface ChunkLightProviderExt {
    void beacon$addSectionData(long pos, ChunkNibbleArray data);
    void beacon$removeSectionData(long pos);

    void beacon$setTinted(long pos, int delta);

    static ChunkLightProviderExt get(ChunkLightingView view) {
        return (view instanceof ChunkLightProviderExt) ? (ChunkLightProviderExt) view : null;
    }
}
