package ppl.beacon.fake.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.StorageIoWorker;

//
public class CopyJob implements Runnable {
    private final World source;
    private final World target;

    private final long sourceAge;
    private final long targetAge;

    protected final ChunkPos chunkPos;
    protected volatile boolean done;
    protected long age;

    protected CopyJob(World source, World target, ChunkPos chunkPos, long sourceAge, long targetAge) {
        this.source = source;
        this.target = target;

        this.sourceAge = sourceAge;
        this.targetAge = targetAge;

        this.chunkPos = chunkPos;
    }


    @Override
    public void run() {
        NbtCompound nbt = source.storage.loadChunk(chunkPos).join().orElse(null);
        if (nbt == null) {
            done = true;
            return;
        }

        // If source age is unknown, check the nbt for it
        if (sourceAge == 1) {
            age = nbt.getLong("age");
            if (age < targetAge) {
                done = true;
                return;
            }
        } else {
            age = sourceAge;
        }

        // Save doesn't return a future, so we instead wait for all writes to be done (syncing to disk only once
        // after every job for the region is done)
        target.storage.saveChunk(chunkPos, nbt);
        ((StorageIoWorker) target.storage.getWorker()).completeAll(false).join();
        done = true;
    }
}
