package ppl.beacon.fake.chunk;

import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.chunk.BlendingData;
import net.minecraft.world.tick.ChunkTickScheduler;
import org.jetbrains.annotations.Nullable;

public class FakeChunk extends WorldChunk {
    public FakeChunk(World world, ChunkPos pos) {
        super(world, pos);
    }

    public FakeChunk(World world, ChunkPos pos, UpgradeData upgradeData, ChunkTickScheduler<Block> blockTickScheduler, ChunkTickScheduler<Fluid> fluidTickScheduler, long inhabitedTime, @Nullable ChunkSection[] sectionArrayInitializer, @Nullable WorldChunk.EntityLoader entityLoader, @Nullable BlendingData blendingData) {
        super(world, pos, upgradeData, blockTickScheduler, fluidTickScheduler, inhabitedTime, sectionArrayInitializer, entityLoader, blendingData);
    }

    public FakeChunk(ServerWorld world, ProtoChunk protoChunk, @Nullable WorldChunk.EntityLoader entityLoader) {
        super(world, protoChunk, entityLoader);
    }
}
