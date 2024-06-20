package ppl.beacon.fake.world;

import it.unimi.dsi.fastutil.longs.Long2LongArrayMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import ppl.beacon.utils.RegionPos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Region {
    final Long2LongMap chunkFingerprints = new Long2LongOpenHashMap();
    final Long2LongMap chunks = new Long2LongOpenHashMap(); // value is age in millis since epoch
    boolean dirty;

    private static Region newRegion(RegionPos pos) {
        Region region = new Region();
        pos.getContainedChunks().forEach(chunkPos -> {
            long chunkCoords = chunkPos.toLong();
            region.chunks.put(chunkCoords, 1); // 1 means "unknown age" (0 is reserved for "no chunk")
            region.chunkFingerprints.put(chunkCoords, 0); // 0 means "unknown fingerprint"
        });
        return region;
    }

    public static Region read(Path file, RegionPos pos) throws IOException {
        if (Files.notExists(file)) return newRegion(pos);

        NbtCompound root;
        try (InputStream in = Files.newInputStream(file)) {
            root = NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
        }

        long[] chunkFingerprints = root.getLongArray("chunk_fingerprints");
        long[] chunkCoords = root.getLongArray("chunk_coords");
        long[] chunkAges = root.getLongArray("chunk_ages");

        Region region = new Region();
        region.chunks.putAll(new Long2LongArrayMap(chunkCoords, chunkAges));
        region.chunkFingerprints.putAll(new Long2LongArrayMap(chunkCoords, chunkFingerprints));
        return region;
    }

    public NbtCompound saveToNbt() {
        long[] chunkCoords = new long[chunks.size()];
        long[] chunkAges = new long[chunkCoords.length];
        long[] chunkFingerprints = new long[chunkCoords.length];

        int i = 0;
        for (Long2LongMap.Entry entry : chunks.long2LongEntrySet()) {
            long coords = entry.getLongKey();

            chunkFingerprints[i] = this.chunkFingerprints.get(coords);
            chunkCoords[i] = coords;
            chunkAges[i] = entry.getLongValue();

            i++;
        }

        NbtCompound root = new NbtCompound();
        root.putLongArray("chunk_fingerprints", chunkFingerprints);
        root.putLongArray("chunk_coords", chunkCoords);
        root.putLongArray("chunk_ages", chunkAges);
        return root;
    }
}
