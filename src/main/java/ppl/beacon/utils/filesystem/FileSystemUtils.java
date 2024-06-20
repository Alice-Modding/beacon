package ppl.beacon.utils.filesystem;

import com.google.common.net.PercentEscaper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class FileSystemUtils {
    private static final PercentEscaper FALLBACK_NAME_ENCODER = new PercentEscaper(".-_ ", false);

    /**
     * Return a unique directory for the given name, even when the name is disallowed by the file system or operating
     * system.
     */
    public static Path resolveSafeDirectoryName(Path parent, String name) {
        // If we can, prefer directly using the name as the folder name
        if (!name.contains("%") && isUsable(parent, name)) {
            return parent.resolve(name);
        }
        // otherwise, fall back to percent encoding
        return parent.resolve(FALLBACK_NAME_ENCODER.escape(name));
    }

    public static boolean oldFolderExists(Path path, String name) {
        try {
            return Files.exists(path.resolve(name));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Checks whether a given directory name is actually usable with the file system / operating system in the given parent folder.
     */
    private static boolean isUsable(Path parent, String name) {
        try {
            if (name.contains(parent.getFileSystem().getSeparator()))
                return false; // name contains the name separator, definitely not usable

            Path path = parent.resolve(name);
            if (Files.exists(path)) return true; // if it already exits, it's definitely usable
            // Otherwise, there's no sure way to know, we just gotta try
            Files.createDirectory(path);
            // Cleanup, we weren't yet asked to create the folder
            Files.delete(path);
        } catch (InvalidPathException e) {
            return false; // name contains invalid characters, definitely not usable
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}
