package ppl.beacon.fake.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import ppl.beacon.utils.filesystem.LastAccessFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class FakeStorageManager {
    protected static final Logger LOGGER = LogManager.getLogger();
    public static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");

    private static final Map<Path, FakeStorage> active = new HashMap<>();

    public static FakeStorage getFor(Path directory, boolean writeable) {
        synchronized (active) {
            return active.computeIfAbsent(directory, f -> new FakeStorage(directory, writeable));
        }
    }

    public static void closeAll() {
        synchronized (active) {
            for (FakeStorage storage : active.values()) {
                try {
                    storage.close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close storage", e);
                }
            }
            active.clear();
        }
    }
}
