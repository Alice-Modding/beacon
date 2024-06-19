package ppl.beacon.fake.chunk;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.level.storage.LevelStorage;
import ppl.beacon.BeaconMod;
import ppl.beacon.config.Config;
import ppl.beacon.config.RanderConfig;
import ppl.beacon.fake.ext.ClientChunkManagerExt;

import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import ppl.beacon.mixin.world.BiomeAccessAccessor;
import ppl.beacon.mixin.world.ClientWorldAccessor;
import ppl.beacon.utils.filesystem.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class FakeChunkManager {
    private final List<Function<ChunkPos, CompletableFuture<Optional<NbtCompound>>>> storages = new ArrayList<>();
    private final ClientChunkManagerExt clientChunkManagerExt;
    private final ClientChunkManager clientChunkManager;
    private final FakeChunkStorage storage;
    private final ClientWorld world;
    private final Worlds worlds;

    private static final MinecraftClient client = MinecraftClient.getInstance();

    public FakeChunkManager(ClientWorld world, ClientChunkManager clientChunkManager) {
        this.clientChunkManagerExt = (ClientChunkManagerExt) clientChunkManager;
        this.clientChunkManager = clientChunkManager;
        this.world = world;

        String serverName = getCurrentWorldOrServerName(world);
        long seedHash = ((BiomeAccessAccessor) world.getBiomeAccess()).getSeed();
        RegistryKey<World> worldKey = world.getRegistryKey();
        Identifier worldId = worldKey.getValue();

        Path storagePath = getStoragePath(serverName)
                .resolve(seedHash + "")
                .resolve(worldId.getNamespace())
                .resolve(worldId.getPath());


        RanderConfig config = Config.renderer;
        if (config.isDynamicMultiWorld()) {
            worlds = Worlds.getFor(storagePath);
            worlds.startNewWorld();
            storages.add(worlds::loadTag);

            storage = null;
        } else {
            storage = FakeChunkStorage.getFor(storagePath, true);
            storages.add(storage::loadTag);

            worlds = null;
        }

        LevelStorage levelStorage = client.getLevelStorage();
//        if (levelStorage.levelExists(FALLBACK_LEVEL_NAME)) {
//            try (LevelStorage.Session session = levelStorage.createSession(FALLBACK_LEVEL_NAME)) {
//                Path worldDirectory = session.getWorldDirectory(worldKey);
//                Path regionDirectory = worldDirectory.resolve("region");
//                FakeChunkStorage fallbackStorage = FakeChunkStorage.getFor(regionDirectory, false);
//                storages.add(fallbackStorage::loadTag);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
    }

    private static String getCurrentWorldOrServerName(ClientWorld world) {
        IntegratedServer integratedServer = client.getServer();
        if (integratedServer != null) {
            return integratedServer.getSaveProperties().getLevelName();
        }

        ServerInfo serverInfo = ((ClientWorldAccessor) world).getNetworkHandler().getServerInfo();
        if (serverInfo != null) {
            if (serverInfo.isRealm()) return "realms";
            return serverInfo.address.replace(':', '_');
        }
        return "unknown";
    }

    private static Path getStoragePath(String serverName) {
        Path storagePath = client.runDirectory
                .toPath()
                .resolve(".bobby");
        boolean exist = false;
        try {
            exist = Files.exists(storagePath.resolve(serverName));
        } catch (InvalidPathException ignored) {}

        if (exist) storagePath = storagePath.resolve(serverName);
        else storagePath = FileSystemUtils.resolveSafeDirectoryName(storagePath, serverName);

        return storagePath;
    }
}
