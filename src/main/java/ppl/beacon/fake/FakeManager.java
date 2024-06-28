package ppl.beacon.fake;

import io.netty.util.concurrent.DefaultThreadFactory;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.apache.commons.lang3.tuple.Pair;
import ppl.beacon.BeaconMod;
import ppl.beacon.config.RanderConfig;
import ppl.beacon.fake.chunk.FakeChunk;
import ppl.beacon.fake.chunk.FakeChunkSerializer;
import ppl.beacon.fake.chunk.VisibleChunksTracker;
import ppl.beacon.fake.storage.FakeStorage;
import ppl.beacon.fake.storage.FakeStorageManager;
import ppl.beacon.fake.ext.ChunkLightProviderExt;
import ppl.beacon.fake.ext.ClientChunkManagerExt;
import ppl.beacon.fake.ext.LightingProviderExt;
import ppl.beacon.mixin.world.BiomeAccessAccessor;
import ppl.beacon.mixin.world.ClientWorldAccessor;
import ppl.beacon.utils.filesystem.FileSystemUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class FakeManager {
    private static final String FALLBACK_LEVEL_NAME = "beacon-fallback";
    private static final MinecraftClient client = MinecraftClient.getInstance();

    private final ClientWorld world;
    private final FakeStorage storage;

    private final ClientChunkManagerExt clientChunkManagerExt;
    private final ClientChunkManager clientChunkManager;

    private final UnloadingManager unloading = new UnloadingManager();
    private final LoadingManager loading = new LoadingManager();
    private final SavingManager saving = new SavingManager();


    private int ticksSinceLastSave;

    private final Long2ObjectMap<WorldChunk> fakeChunks = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    private final VisibleChunksTracker chunkTracker = new VisibleChunksTracker();

    private static String getCurrentWorldOrServerName(ClientPlayNetworkHandler networkHandler) {
        IntegratedServer integratedServer = client.getServer();
        if (integratedServer != null) {
            return integratedServer.getSaveProperties().getLevelName();
        }

        ServerInfo serverInfo = networkHandler.getServerInfo();
        if (serverInfo != null) {
            if (serverInfo.isRealm()) {
                return "realms";
            }
            return serverInfo.address.replace(':', '_');
        }

        return "unknown";
    }

    private Path getStoragePath() {

        String serverName = getCurrentWorldOrServerName(((ClientWorldAccessor) world).getNetworkHandler());
        Path storagePath = client.runDirectory.toPath().resolve(".beacon");
        long seedHash = ((BiomeAccessAccessor) world.getBiomeAccess()).getSeed();
        Identifier worldId = world.getRegistryKey().getValue();

        if (FileSystemUtils.oldFolderExists(storagePath, serverName)) {
            storagePath = storagePath.resolve(serverName);
        } else {
            storagePath = FileSystemUtils.resolveSafeDirectoryName(storagePath, serverName);
        }
        return storagePath
                .resolve(seedHash + "")
                .resolve(worldId.getNamespace())
                .resolve(worldId.getPath());
    }

    public FakeManager(ClientWorld world, ClientChunkManager clientChunkManager) {
        this.world = world;
        this.clientChunkManager = clientChunkManager;
        this.clientChunkManagerExt = (ClientChunkManagerExt) clientChunkManager;
        this.storage = FakeStorageManager.getFor(getStoragePath(), true);
    }

    public WorldChunk getChunk(int x, int z) {
        return fakeChunks.get(ChunkPos.toLong(x, z));
    }

    public FakeStorage getStorage() {
        return storage;
    }

    public void update(BooleanSupplier shouldKeepTicking) {
        update(shouldKeepTicking, client.options.getViewDistance().getValue());
    }

    private void update(BooleanSupplier shouldKeepTicking, int newViewDistance) {
        // Once a minute, force chunks to disk
        if (++ticksSinceLastSave > 20 * 60) {
            // completeAll is blocking, so we run it on the io pool
            Util.getIoWorkerExecutor().execute(storage::completeAll);

            ticksSinceLastSave = 0;
        }

        ClientPlayerEntity player = client.player;
        if (player == null) return;

        RanderConfig config = BeaconMod.getConfig().getRanderConfig();
        long time = Util.getMeasuringTimeMs();

        List<Pair<Integer, LoadingJob>> newJobs = new ArrayList<>();
        ChunkPos playerChunkPos = player.getChunkPos();
        int newCenterX = playerChunkPos.x;
        int newCenterZ = playerChunkPos.z;
        chunkTracker.update(newCenterX, newCenterZ, newViewDistance, chunkPos -> {
            // Chunk is now outside view distance, can be unloaded / cancelled
            loading.cancel(chunkPos);
            unloading.add(chunkPos, time);
        }, chunkPos -> {
            // Chunk is now inside view distance, load it
            int x = ChunkPos.getPackedX(chunkPos);
            int z = ChunkPos.getPackedZ(chunkPos);

            // We want this chunk, so don't unload it if it's still here
            unloading.cancel(chunkPos);
            // Not removing it from [unloadQueue], we check [toBeUnloaded] when we poll it.

            // If there already is a chunk loaded, there's nothing to do
            if (clientChunkManager.getChunk(x, z, ChunkStatus.FULL, false) != null) return;

            // All good, load it
            int distanceX = Math.abs(x - newCenterX);
            int distanceZ = Math.abs(z - newCenterZ);
            int distanceSquared = distanceX * distanceX + distanceZ * distanceZ;
            newJobs.add(Pair.of(distanceSquared, new LoadingJob(new ChunkPos(x, z))));
        });

        if (!newJobs.isEmpty()) loading.addAll(newJobs);

        // Anything remaining in the set is no longer needed and can now be unloaded
        long unloadTime = time - config.getUnloadDelaySecs() * 1000L;
        unloading.process(shouldKeepTicking, unloadTime);
        loading.process(shouldKeepTicking);
    }

    public void loadMissingChunksFromCache() {
        // We do this by temporarily reducing the client view distance to 0. That will unload all chunks and then try
        // to re-load them (by canceling the unload when they were already loaded, or from the cache when they are
        // missing).
        update(() -> false, 0);
        update(() -> false);
    }

    public boolean shouldBeLoaded(int x, int z) {
        return chunkTracker.isInViewDistance(x, z);
    }

    public void loadFake(ChunkPos pos, WorldChunk chunk) {
        fakeChunks.put(pos.toLong(), chunk);

        world.resetChunkColor(pos);

        for (int i = world.getBottomSectionCoord(), l = world.getTopSectionCoord(); i < l; i++) {
            world.scheduleBlockRenders(pos.x, i, pos.z);
        }

        clientChunkManagerExt.beacon$onFakeChunkAdded(pos.x, pos.z);
    }

    public void unloadFake(int x, int z, boolean willBeReplaced) {
        long chunkPos = ChunkPos.toLong(x, z);
        loading.cancel(chunkPos);
        WorldChunk chunk = fakeChunks.remove(chunkPos);
        if (chunk != null) {
            chunk.clear();

            LightingProvider lightingProvider = clientChunkManager.getLightingProvider();
            LightingProviderExt lightingProviderExt = LightingProviderExt.get(lightingProvider);
            ChunkLightProviderExt blockLightProvider = ChunkLightProviderExt.get(lightingProvider.get(LightType.BLOCK));
            ChunkLightProviderExt skyLightProvider = ChunkLightProviderExt.get(lightingProvider.get(LightType.SKY));

            lightingProviderExt.beacon$disableColumn(chunkPos);

            for (int i = 0, l = chunk.getSectionArray().length; i < l; i++) {
                int y = world.sectionIndexToCoord(i);
                if (blockLightProvider != null) {
                    blockLightProvider.beacon$removeSectionData(ChunkSectionPos.asLong(x, y, z));
                }
                if (skyLightProvider != null) {
                    skyLightProvider.beacon$removeSectionData(ChunkSectionPos.asLong(x, y, z));
                }
            }

            clientChunkManagerExt.beacon$onFakeChunkRemoved(x, z, willBeReplaced);

        }
    }

    public Supplier<WorldChunk> saveReal(WorldChunk chunk) {
        FakeChunk fake = new FakeChunk(chunk);
        saving.add(new SavingJob(fake));
        return FakeChunkSerializer.loadChunk(fake, fake.blockLight, fake.skyLight);
    }


    public String getDebugString() {
        return "F: " + fakeChunks.size() + " L: " + loading.size() + " U: " + unloading.size();
    }

    public Collection<WorldChunk> getFakeChunks() {
        return fakeChunks.values();
    }

    private class UnloadingManager {
        // Contains chunks in order to be unloaded. We keep the chunk and time so we can cross-reference it with
        // [toBeUnloaded] to see if the entry has since been removed / the time reset. This way we do not need
        // to remove entries from the middle of the queue.
        private final Deque<Pair<Long, Long>> unloadQueue = new ArrayDeque<>();
        private final Long2LongMap toBeUnloaded = new Long2LongOpenHashMap();

        private void add(long chunkPos, long time) {
            toBeUnloaded.put(chunkPos, time);
            unloadQueue.add(Pair.of(chunkPos, time));
        }

        private void cancel(long chunkPos) {
            toBeUnloaded.remove(chunkPos);
        }

        private int size() {
            return toBeUnloaded.size();
        }

        private void process(BooleanSupplier shouldKeepTicking, long unloadTime) {
            int countSinceLastThrottleCheck = 0;
            while (true) {
                Pair<Long, Long> next = unloadQueue.pollFirst();
                if (next == null) break;

                long chunkPos = next.getLeft();
                long queuedTime = next.getRight();

                if (queuedTime > unloadTime) {
                    // Unload is still being delayed, put the entry back into the queue
                    // and be done for this update.
                    unloadQueue.addFirst(next);
                    return;
                }

                long actualQueuedTime = toBeUnloaded.remove(chunkPos);
                if (actualQueuedTime != queuedTime) {
                    // The chunk has either been un-queued or re-queued.
                    if (actualQueuedTime != 0) {
                        // If it was re-queued, put it back in the map.
                        toBeUnloaded.put(chunkPos, actualQueuedTime);
                    }
                    // Either way, skip it for now and go to the next entry.
                    continue;
                }

                // This chunk is due for unloading
                unloadFake(ChunkPos.getPackedX(chunkPos), ChunkPos.getPackedZ(chunkPos), false);

                if (countSinceLastThrottleCheck++ > 10) {
                    if (!shouldKeepTicking.getAsBoolean()) return;
                    countSinceLastThrottleCheck = 0;
                }
            }
        }
    }

    private class SavingManager {
        private static final ExecutorService saveExecutor = Executors.newFixedThreadPool(8, new DefaultThreadFactory("beacon-saving", true));

        private void add(SavingJob job) {
            saveExecutor.execute(job);
        }
    }

    private class SavingJob implements Runnable {
        private final FakeChunk chunk;

        public SavingJob(FakeChunk chunk) {
            this.chunk = chunk;
        }

        @Override
        public void run() {
            NbtCompound nbt = FakeChunkSerializer.serialize(chunk, chunk.getWorld().getLightingProvider());
            nbt.putLong("age", System.currentTimeMillis()); // fallback in case meta gets corrupted

            storage.saveChunk(chunk.getPos(), nbt);
        }
    }

    private class LoadingManager {
        public static final Comparator<Pair<Integer, LoadingJob>> BY_DISTANCE = Comparator.comparing(Pair::getLeft);

        // The api for loading chunks unfortunately does not handle cancellation, so we utilize a separate thread pool to
        // ensure only a small number of tasks are active at any one time, and all others can still be cancelled before
        // they are submitted.
        // The size of the pool must be sufficiently large such that there is always at least one query operation
        // running, as otherwise the storage io worker will start writing chunks which slows everything down to a crawl.
        private static final ExecutorService loadExecutor = Executors.newFixedThreadPool(8, new DefaultThreadFactory("beacon-loading", true));
        private final Long2ObjectMap<LoadingJob> loadingJobs = new Long2ObjectLinkedOpenHashMap<>();

        private int size() {
            return loadingJobs.size();
        }

        private void cancel(long chunkPos) {
            LoadingJob loadingJob = loadingJobs.remove(chunkPos);
            if (loadingJob != null) loadingJob.cancelled = true;
        }

        private void add(LoadingJob job) {
            loadingJobs.put(job.pos.toLong(), job);
            loadExecutor.execute(job);
        }

        private void addAll(List<Pair<Integer, LoadingJob>> newJobs) {
            newJobs.sort(BY_DISTANCE);
            newJobs.forEach(e -> add(e.getRight()));
        }

        private void process(BooleanSupplier shouldKeepTicking) {
            ObjectIterator<LoadingJob> loadingJobsIter = this.loadingJobs.values().iterator();
            while (loadingJobsIter.hasNext()) {
                LoadingJob loadingJob = loadingJobsIter.next();

                // Still loading, should we wait for it?
                if (loadingJob.result == null) continue;

                // Done loading
                loadingJobsIter.remove();

                client.getProfiler().push("loadFakeChunk");
                loadingJob.complete();
                client.getProfiler().pop();

                if (!shouldKeepTicking.getAsBoolean()) return;
            }
        }
    }

    private class LoadingJob implements Runnable {

        private final ChunkPos pos;
        private volatile boolean cancelled;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // null while loading, empty() if no chunk was found
        private volatile Optional<Supplier<WorldChunk>> result;

        public LoadingJob(ChunkPos pos) {
            this.pos = pos;
        }

        @Override
        public void run() {
            if (cancelled) return;
            Optional<NbtCompound> value;
            try {
                value = loadNbt(pos).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                value = Optional.empty();
            }
            if (cancelled) return;
            result = value.map(it -> FakeChunkSerializer.deserialize(pos, it, world));
        }

        private CompletableFuture<Optional<NbtCompound>> loadNbt(ChunkPos chunkPos) {
            return storage.loadChunk(chunkPos).thenCompose(maybeTag -> {
                if (maybeTag.isPresent()) {
                    return CompletableFuture.completedFuture(maybeTag);
                }
                return CompletableFuture.completedFuture(Optional.empty());
            });
        }

        private void complete() {
            result.ifPresent(it -> loadFake(pos, it.get()));
        }

    }
}
