package ppl.beacon.fake.world;

import io.netty.util.concurrent.DefaultThreadFactory;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppl.beacon.utils.LimitedExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class WorldManagerCollection {
    protected static final Logger LOGGER = LogManager.getLogger();

    protected static final int CONCURRENT_REGION_LOADING_JOBS = 10;
    protected static final int CONCURRENT_FINGERPRINT_JOBS = 10;
    protected static final int CONCURRENT_COPY_JOBS = 10;
    protected static final int MATCH_THRESHOLD = 10;
    protected static final int MISMATCH_THRESHOLD = 100;
    protected static final int CURRENT_SAVE_VERSION = SharedConstants.getGameVersion().getSaveVersion().getId();


    // Executor for saving. Single-threaded so we do not have to worry about races between multiple saves.
    protected static final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(new DefaultThreadFactory("beacon-meta-saving", true));
    protected static final LimitedExecutor copyExecutor = new LimitedExecutor(Util.getIoWorkerExecutor(), CONCURRENT_COPY_JOBS);

    protected static final Executor computeFingerprintExecutor = new LimitedExecutor(Util.getIoWorkerExecutor(), CONCURRENT_FINGERPRINT_JOBS);
    protected static final Executor regionLoadingExecutor = new LimitedExecutor(Util.getIoWorkerExecutor(), CONCURRENT_REGION_LOADING_JOBS);


    private static final Map<Path, WorldManager> active = new HashMap<>();


    public static WorldManager getFor(Path directory) {
        synchronized (active) {
            return active.computeIfAbsent(directory, f -> new WorldManager(directory));
        }
    }

    public static void closeAll() {
        synchronized (active) {
            for (WorldManager WorldManager : active.values()) {
                try {
                    WorldManager.close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close storage at " + WorldManager.directory, e);
                }
            }
            active.clear();
        }
    }
}
