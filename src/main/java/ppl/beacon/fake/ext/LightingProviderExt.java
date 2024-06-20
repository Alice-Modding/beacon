package ppl.beacon.fake.ext;

import net.minecraft.world.chunk.light.LightingProvider;

public interface LightingProviderExt {
    void beacon$enabledColumn(long pos);
    void beacon$disableColumn(long pos);

    static LightingProviderExt get(LightingProvider provider) {
        return (provider instanceof LightingProviderExt) ? (LightingProviderExt) provider : null;
    }
}
