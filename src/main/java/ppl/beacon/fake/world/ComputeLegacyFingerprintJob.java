package ppl.beacon.fake.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import ppl.beacon.fake.chunk.FakeChunkSerializer;
import ppl.beacon.utils.RegionPos;

import java.util.concurrent.CompletableFuture;

public class ComputeLegacyFingerprintJob implements Runnable {
    private final net.minecraft.world.World mcWorld;

    protected final CompletableFuture<Long> future = new CompletableFuture<>();
    protected final ChunkPos chunkPos;
    protected final World world;

    protected volatile Long result;

    protected ComputeLegacyFingerprintJob(World world, ChunkPos chunkPos, net.minecraft.world.World mcWorld) {
        this.world = world;
        this.chunkPos = chunkPos;
        this.mcWorld = mcWorld;
    }

    @Override
    public void run() {
        NbtCompound nbt = world.storage.loadChunk(chunkPos).join().orElse(null);
        if (nbt == null) {
            result = 0L;
            return;
        }

        WorldChunk chunk = FakeChunkSerializer.deserialize(chunkPos, nbt, mcWorld);
        result = FakeChunkSerializer.fingerprint(chunk);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComputeLegacyFingerprintJob that = (ComputeLegacyFingerprintJob) o;
        return world.id == that.world.id && chunkPos.equals(that.chunkPos);
    }

    @Override
    public int hashCode() {
        // Using 127 instead of the traditional 31, so worlds are separated by more than a regular render distance
        return world.id * 127 + RegionPos.hashCode(chunkPos.toLong());
    }
}
