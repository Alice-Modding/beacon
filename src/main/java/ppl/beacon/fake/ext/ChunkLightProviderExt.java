package ppl.beacon.fake.ext;

import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightingView;

public interface ChunkLightProviderExt {
    void addSectionData(long pos, ChunkNibbleArray data);
    void removeSectionData(long pos);

    void setTinted(long pos, int delta);

    static ChunkLightProviderExt get(ChunkLightingView view) {
        return (view instanceof ChunkLightProviderExt) ? (ChunkLightProviderExt) view : null;
    }
}
