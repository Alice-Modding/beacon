package ppl.beacon.fake.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.MapCodec;
import io.netty.util.concurrent.DefaultThreadFactory;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.storage.StorageIoWorker;
import net.minecraft.world.storage.StorageKey;
import net.minecraft.world.storage.VersionedChunkStorage;
import ppl.beacon.utils.RegionPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ppl.beacon.fake.chunk.storage.FakeStorageManager.REGION_FILE_PATTERN;

public class FakeStorage extends VersionedChunkStorage {
    private final AtomicBoolean sentUpgradeNotification = new AtomicBoolean();
    private final boolean writeable;
    private final Path directory;

//    @Nullable
//    private final LastAccessFile lastAccess;

    protected FakeStorage(Path directory, boolean writeable) {
        super(
                new StorageKey("dummy", World.OVERWORLD, "beacon"),
                directory,
                MinecraftClient.getInstance().getDataFixer(),
                false
        );

        this.directory = directory;
        this.writeable = writeable;

//        LastAccessFile lastAccess = null;
        if (writeable) {
            try {
                Files.createDirectories(directory);

//                lastAccess = new LastAccessFile(directory);
            } catch (IOException e) {
                FakeStorageManager.LOGGER.error("Failed to read last_access file:", e);
            }
        }
//        this.lastAccess = lastAccess;
    }

    @Override
    public void close() throws IOException {
        super.close();
//        if (lastAccess == null) return;
//
//        int deleteUnusedRegionsAfterDays = Config.renderer.getDeleteUnusedRegionsAfterDays();
//        if (deleteUnusedRegionsAfterDays >= 0) {
//            for (long entry : lastAccess.pollRegionsOlderThan(deleteUnusedRegionsAfterDays)) {
//                int x = ChunkPos.getPackedX(entry);
//                int z = ChunkPos.getPackedZ(entry);
//                Files.deleteIfExists(directory.resolve("r." + x + "." + z + ".mca"));
//            }
//        }
//
//        lastAccess.close();
    }

    public void save(ChunkPos pos, NbtCompound chunk) {
//        if (lastAccess != null) {
//            lastAccess.touchRegion(pos.getRegionX(), pos.getRegionZ());
//        }
        setNbt(pos, chunk);
    }

    public CompletableFuture<Optional<NbtCompound>> loadTag(ChunkPos pos) {
        return getNbt(pos).thenApply(maybeNbt -> maybeNbt.map(nbt -> loadTag(pos, nbt)));
    }

    private NbtCompound loadTag(ChunkPos pos, NbtCompound nbt) {
//        if (nbt != null && lastAccess != null) {
//            lastAccess.touchRegion(pos.getRegionX(), pos.getRegionZ());
//        }
        if (nbt != null && nbt.getInt("DataVersion") != SharedConstants.getGameVersion().getSaveVersion().getId()) {
            if (sentUpgradeNotification.compareAndSet(false, true)) {
                MinecraftClient client = MinecraftClient.getInstance();
                client.submit(() -> {
                    Text text = Text.translatable(writeable ? "beacon.upgrade.required" : "beacon.upgrade.fallback_world");
                    client.submit(() -> client.inGameHud.getChatHud().addMessage(text));
                });
            }
            return null;
        }
        return nbt;
    }

    public static List<RegionPos> getRegions(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(REGION_FILE_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(it -> new RegionPos(Integer.parseInt(it.group(1)), Integer.parseInt(it.group(2))))
                    .collect(Collectors.toList());
        }
    }

    public void upgrade(RegistryKey<World> worldKey, BiConsumer<Integer, Integer> progress) throws IOException {
        Optional<RegistryKey<MapCodec<? extends ChunkGenerator>>> generatorKey =
                Optional.of(Registries.CHUNK_GENERATOR.getKey(FlatChunkGenerator.CODEC).orElseThrow());

        List<ChunkPos> chunks = getRegions(directory).stream().flatMap(RegionPos::getContainedChunks).toList();

        AtomicInteger done = new AtomicInteger();
        AtomicInteger total = new AtomicInteger(chunks.size());
        progress.accept(done.get(), total.get());

        StorageIoWorker io = (StorageIoWorker) getWorker();

        // We ideally split the actual work of upgrading the chunk NBT across multiple threads, leaving a few for MC
        int workThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService workExecutor = Executors.newFixedThreadPool(workThreads, new DefaultThreadFactory("beacon-upgrade-worker", true));

        try {
            for (ChunkPos chunkPos : chunks) {
                workExecutor.submit(() -> {
                    NbtCompound nbt;
                    try {
                        nbt = io.readChunkData(chunkPos).join().orElse(null);
                    } catch (CompletionException e) {
                        FakeStorageManager.LOGGER.warn("Error reading chunk " + chunkPos.x + "/" + chunkPos.z + ":", e);
                        nbt = null;
                    }

                    if (nbt == null) {
                        progress.accept(done.get(), total.decrementAndGet());
                        return;
                    }

                    // Didn't have this set prior to Beacon 4.0.5 and upgrading from 1.18 to 1.19 wipes light data
                    // from chunks that don't have this set, so we need to set it before we upgrade the chunk.
                    nbt.putBoolean("isLightOn", true);

                    nbt = updateChunkNbt(worldKey, null, nbt, generatorKey);

                    io.setResult(chunkPos, nbt).join();

                    progress.accept(done.incrementAndGet(), total.get());
                });
            }
        } finally {
            workExecutor.shutdown();
        }

        try {
            //noinspection ResultOfMethodCallIgnored
            workExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        progress.accept(done.get(), total.get());
    }
}