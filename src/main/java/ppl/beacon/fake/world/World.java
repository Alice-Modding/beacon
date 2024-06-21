package ppl.beacon.fake.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.ChunkPos;
import ppl.beacon.fake.chunk.storage.FakeStorage;
import ppl.beacon.fake.chunk.storage.FakeStorageManager;
import ppl.beacon.utils.RegionPos;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public class World {
    protected final int id;
    protected int version;

    protected final LongSet knownRegions = new LongOpenHashSet();
    protected final Long2ObjectMap<CompletableFuture<?>> loadingRegions = new Long2ObjectOpenHashMap<>();
    protected final Long2ObjectMap<Region> regions = new Long2ObjectOpenHashMap<>();
    protected final Long2ObjectMap<Region> regionUpdates = new Long2ObjectOpenHashMap<>();

    protected int mergingIntoWorld = -1;
    protected final Int2ObjectMap<Match> matchingWorlds = new Int2ObjectOpenHashMap<>();
    protected final IntSet nonMatchingWorlds = new IntOpenHashSet();

    protected final FakeStorage storage;

    protected MergeState mergeState;

    protected boolean metaDirty;
    protected boolean contentDirty;

    private final WorldManager manager;

    public World(int id, int version, WorldManager manager) {
        this.id = id;
        this.version = version;
        this.storage = FakeStorageManager.getFor(directory(), true);
        this.manager = manager;
    }

    @Override
    public String toString() {
        return String.valueOf(id);
    }

    public Path directory() {
        // 0 is located directly in the main folder for backwards compatibility
        if (id == 0) return manager.directory;
        return manager.directory.resolve(String.valueOf(id));
    }

    protected Path regionFile(RegionPos pos) {
        return directory().resolve("r." + pos.x() + "." + pos.z() + ".meta");
    }

    public void markMetaDirty() {
        manager.dirty = true;
        metaDirty = true;
    }

    public void markContentDirty() {
        manager.dirty = true;
        contentDirty = true;
    }

    public void setFingerprint(ChunkPos chunkPos, long fingerprint) {
        RegionPos regionPos = RegionPos.from(chunkPos);
        long chunkCoord = chunkPos.toLong();
        long regionCoord = regionPos.toLong();

        if (knownRegions.add(regionCoord)) {
            markMetaDirty();
            regions.put(regionCoord, new Region());
        }

        Region region = regions.get(regionCoord);
        if (region == null) {
            region = regionUpdates.get(regionCoord);
            if (region == null) {
                regionUpdates.put(regionCoord, region = new Region());
                loadRegion(regionPos);
            }
        }

        if (region.chunkFingerprints.put(chunkCoord, fingerprint) != fingerprint) {
            region.chunks.put(chunkCoord, System.currentTimeMillis());
            region.dirty = true;
            markContentDirty();
        }
    }

    public Match getMatch(World otherWorld) {
        Match match = matchingWorlds.get(otherWorld.id);
        if (match == null) {
            match = otherWorld.matchingWorlds.get(id);
            if (match == null) {
                match = new Match(new LongOpenHashSet(), new LongOpenHashSet());
                matchingWorlds.put(otherWorld.id, match);
                otherWorld.matchingWorlds.put(id, match);
            }
        }
        return match;
    }

    public CompletableFuture<?> loadRegion(RegionPos regionPos) {
        long regionCoord = regionPos.toLong();

        assert manager.lock.isWriteLockedByCurrentThread();
        assert knownRegions.contains(regionCoord);

        CompletableFuture<?> existingFuture = loadingRegions.get(regionCoord);
        if (existingFuture != null) {
            return existingFuture;
        }

        CompletableFuture<?> future = new CompletableFuture<>();
        loadingRegions.put(regionCoord, future);

        RegionLoadingJob loadingJob = new RegionLoadingJob(this, regionPos);
        manager.regionLoadingJobs.add(loadingJob);
        WorldManagerCollection.regionLoadingExecutor.execute(loadingJob);

        return future;
    }

    public void writeRegionToDisk(RegionPos regionPos, NbtCompound nbt) throws IOException {
        Files.createDirectories(manager.directory);

        Path tmpFile = Files.createTempFile(manager.directory, "region", ".meta");
        try {
            try (OutputStream out = Files.newOutputStream(tmpFile)) {
                NbtIo.writeCompressed(nbt, out);
            }
            Files.move(tmpFile, regionFile(regionPos), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }
}
