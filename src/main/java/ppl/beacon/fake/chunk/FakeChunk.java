package ppl.beacon.fake.chunk;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.light.ChunkLightingView;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.tick.ChunkTickScheduler;
import ppl.beacon.BeaconMod;
import ppl.beacon.config.Config;
import ppl.beacon.fake.ext.ChunkLightProviderExt;

import java.util.Map;

public class FakeChunk extends WorldChunk {
    private boolean is_tinted;

    public ChunkNibbleArray[] blockLight;
    public ChunkNibbleArray[] skyLight;
    public NbtList serializedBlockEntities;

    public FakeChunk(World world, ChunkPos chunkPos, ChunkSection[] sections) {
        super(world, chunkPos, UpgradeData.NO_UPGRADE_DATA, new ChunkTickScheduler<>(), new ChunkTickScheduler<>(), 0L, sections, null, null);
    }

    public FakeChunk(WorldChunk real) {
        super(real.getWorld(), real.getPos(), UpgradeData.NO_UPGRADE_DATA, new ChunkTickScheduler<>(), new ChunkTickScheduler<>(), 0L, real.getSectionArray(), null, null);
        blockLight = new ChunkNibbleArray[getSectionArray().length + 2];
        skyLight = new ChunkNibbleArray[getSectionArray().length + 2];

        LightingProvider lightingProvider = getWorld().getChunkManager().getLightingProvider();
        ChunkLightingView blockLightView= lightingProvider.get(LightType.BLOCK);
        ChunkLightingView skyLightView = lightingProvider.get(LightType.SKY);


        for (int y = lightingProvider.getBottomY(), my = lightingProvider.getTopY(), i = 0; y < my; y++, i++) {
            ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(pos, y);
            blockLight[i] = blockLightView.getLightSection(chunkSectionPos);
            skyLight[i] = skyLightView.getLightSection(chunkSectionPos);
        }

        for (Map.Entry<Heightmap.Type, Heightmap> entry : real.getHeightmaps()) {
            heightmaps.put(entry.getKey(), entry.getValue());
        }

        NbtList blockEntitiesTag = new NbtList();
        DynamicRegistryManager register = getWorld().getRegistryManager();
        for (BlockPos pos : real.getBlockEntityPositions()) {
            NbtCompound blockEntityTag = real.getPackedBlockEntityNbt(pos, register);
            if (blockEntityTag == null) continue;

            blockEntitiesTag.add(blockEntityTag);
            if (!BeaconMod.getConfig().getRanderConfig().isNoBlockEntities()) addPendingBlockEntityNbt(blockEntityTag);
        }
        serializedBlockEntities = blockEntitiesTag;
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
        int skyDelta = tinted ? -3 + (int) (-7 * client.options.getGamma().getValue()) : 0;

        if (blockLightProvider == null)
            for (int y = getBottomSectionCoord(), my = getTopSectionCoord(); y < my; y++)
                blockLightProvider.beacon$setTinted(ChunkSectionPos.asLong(this.pos.x, y, this.pos.z), blockDelta);

        if (skyLightProvider == null)
            for (int y = getBottomSectionCoord(), my = getTopSectionCoord(); y < my; y++)
                skyLightProvider.beacon$setTinted(ChunkSectionPos.asLong(this.pos.x, y, this.pos.z), skyDelta);


        for (int y = getBottomSectionCoord(), my = getTopSectionCoord(); y < my; y++)
            worldRenderer.scheduleBlockRender(this.pos.x, y, this.pos.z);
    }
}
