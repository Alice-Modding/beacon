package ppl.beacon.fake.chunk;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.gen.chunk.BlendingData;
import net.minecraft.world.tick.ChunkTickScheduler;
import org.jetbrains.annotations.Nullable;
import ppl.beacon.fake.ext.ChunkLightProviderExt;

public class FakeChunk extends WorldChunk {
    private boolean is_tinted;

    public ChunkNibbleArray[] blockLight;
    public ChunkNibbleArray[] skyLight;
    public NbtList serializedBlockEntities;

    public FakeChunk(World world, ChunkPos pos, ChunkSection[] sections) {
        super(world, pos, UpgradeData.NO_UPGRADE_DATA, new ChunkTickScheduler<Block>(), new ChunkTickScheduler<Fluid>(), 0L, sections, null, null);
    }

    public void setTinted(boolean tinted) {
        if (this.is_tinted == tinted) return;
        this.is_tinted = tinted;

        MinecraftClient client = MinecraftClient.getInstance();
        WorldRenderer worldRenderer = client.worldRenderer;

        LightingProvider lightingProvider = getWorld().getLightingProvider();
        ChunkLightProviderExt blockLightProvider = ChunkLightProviderExt.get(lightingProvider.get(LightType.BLOCK));
        ChunkLightProviderExt skyLightProvider = ChunkLightProviderExt.get(lightingProvider.get(LightType.SKY));

        int blockDelta = tinted ? 5 : 0;
        double gamma = client.options.getGamma().getValue();
        int skyDelta = tinted ? -3 + (int) (-7 * gamma) : 0;

        for (int y = getBottomSectionCoord(); y < getTopSectionCoord(); y++) {
            updateTintedState(blockLightProvider, y, blockDelta);
            updateTintedState(skyLightProvider, y, skyDelta);

            worldRenderer.scheduleBlockRender(this.pos.x, y, this.pos.z);
        }
    }

    private void updateTintedState(ChunkLightProviderExt lightProvider, int y, int delta) {
        if (lightProvider == null) return;
        lightProvider.setTinted(ChunkSectionPos.asLong(this.pos.x, y, this.pos.z), delta);
    }
}
