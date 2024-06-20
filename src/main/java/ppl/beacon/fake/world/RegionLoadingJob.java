package ppl.beacon.fake.world;

import ppl.beacon.utils.RegionPos;

import java.io.IOException;
import java.nio.file.Path;

public class RegionLoadingJob implements Runnable {
    protected final RegionPos regionPos;
    protected volatile Region result;
    protected final World world;

    protected RegionLoadingJob(World world, RegionPos regionPos) {
        this.regionPos = regionPos;
        this.world = world;
    }

    @Override
    public void run() {
        Path file = world.regionFile(regionPos);
        try {
            result = Region.read(file, regionPos);
        } catch (IOException e) {
            WorldManagerCollection.LOGGER.error("Failed to load {}", file, e);
        } finally {
            if (result == null) result = new Region();
        }
    }
}
