package ppl.beacon.fake.chunk;

import net.minecraft.util.math.ChunkPos;

import java.util.function.LongConsumer;

public class VisibleChunksTracker {
    private int centerX, centerZ, viewDistance = -1;

    public void updateCenter(int centerX, int centerZ, LongConsumer unload, LongConsumer load) {
        update(centerX, centerZ, viewDistance, unload, load);
    }

    public void updateViewDistance(int viewDistance, LongConsumer unload, LongConsumer load) {
        update(centerX, centerZ, viewDistance, unload, load);
    }

    private static void updateLongConsumer(LongConsumer consumer, int x1, int z1, int dist1, int x2, int z2, int dist2) {
        for (int x = x1 - dist1, mx = x1 + dist1; x <= mx; x++) {
            boolean xOutsideNew = x < x2 - dist2 || x > x2 + dist2;
            for (int z = z1 - dist1, mz = z1 + dist1; z <= mz; z++) {
                boolean zOutsideNew = z < z2 - dist2 || z > z2 + dist2;
                if (xOutsideNew || zOutsideNew) consumer.accept(ChunkPos.toLong(x, z));
            }
        }
    }


    public void update(int newCenterX, int newCenterZ, int newViewDistance, LongConsumer unload, LongConsumer load) {
        if (centerX != newCenterX || centerZ != newCenterZ || viewDistance != newViewDistance) {
            if (unload != null)
                updateLongConsumer(unload,
                        centerX, centerZ, viewDistance,
                        newCenterX, newCenterZ, newViewDistance);

            if (load != null)
                updateLongConsumer(load,
                        newCenterX, newCenterZ, newViewDistance,
                        centerX, centerZ, viewDistance);

            centerX = newCenterX;
            centerZ = newCenterZ;
            viewDistance = newViewDistance;
        }
    }

    public boolean isInViewDistance(int x, int z) {
        boolean xInside = x >= centerX - viewDistance && x <= centerX + viewDistance;
        boolean zInside = z >= centerZ - viewDistance && z <= centerZ + viewDistance;
        return xInside && zInside;
    }

    public void forEach(LongConsumer consumer) {
        for (int x = centerX - viewDistance, mx = centerX + viewDistance; x <= mx; x++) {
            for (int z = centerZ - viewDistance, mz = centerZ + viewDistance; z <= mz; z++) {
                consumer.accept(ChunkPos.toLong(x, z));
            }
        }
    }
}
