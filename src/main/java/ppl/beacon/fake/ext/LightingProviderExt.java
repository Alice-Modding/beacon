package ppl.beacon.fake.ext;

import net.minecraft.world.chunk.light.LightingProvider;

public interface LightingProviderExt {
    void enabledColumn(long pos);
    void disableColumn(long pos);

    static LightingProviderExt get(LightingProvider provider) {
        return (provider instanceof LightingProviderExt) ? (LightingProviderExt) provider : null;
    }
}
