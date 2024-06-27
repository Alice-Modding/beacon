package ppl.beacon;

import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import ppl.beacon.commands.CreateWorldCommand;
import ppl.beacon.commands.MergeWorldsCommand;
import ppl.beacon.commands.UpgradeCommand;
import ppl.beacon.commands.WorldsCommand;
import ppl.beacon.config.Config;
import ppl.beacon.config.RanderConfig;
import ppl.beacon.utils.FlawlessFrames;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class BeaconMod implements ClientModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    private static final MinecraftClient client = MinecraftClient.getInstance();
    public static final String MOD_ID = "beacon-mod";
    private final Config config = new Config();

    private static BeaconMod instance;

    {
        instance = this;
    }


    @Override
    public void onInitializeClient() {
        config.init();

        ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) ->
                dispatcher.register(literal("beacon")
                        .then(literal("worlds").executes(new WorldsCommand(false))
                                .then(literal("full").executes(new WorldsCommand(true)))
                                .then(literal("merge")
                                        .then(argument("source", integer())
                                                .then(argument("target", integer())
                                                        .executes(new MergeWorldsCommand()))))
                                .then(literal("create").executes(new CreateWorldCommand()))
                        )
                        .then(literal("upgrade").executes(new UpgradeCommand())))));

        FlawlessFrames.onClientInitialization();

//        Util.getIoWorkerExecutor().submit(this::cleanupOldWorlds);
    }

    public static BeaconMod getInstance() {
        return instance;
    }
    public static Config getConfig() {
        return instance.config;
    }

    public boolean isRanderEnabled() {
        RanderConfig renderConfig = config.getRanderConfig();
        return renderConfig.isEnabled()
                // For singleplayer, disable ourselves unless the view-distance overwrite is active.
                && (client.getServer() == null || renderConfig.getViewDistanceOverwrite() != 0);
    }

//    private void cleanupOldWorlds() {
////		int deleteUnusedRegionsAfterDays = Config.renderer.getDeleteUnusedRegionsAfterDays();
////		if (deleteUnusedRegionsAfterDays < 0) return;
////
////		Path basePath = client.runDirectory.toPath().resolve(".beacon");
////
////		List<Path> toBeDeleted;
////		try (Stream<Path> stream = Files.walk(basePath, 4)) {
////			toBeDeleted = stream
////					.filter(it -> basePath.relativize(it).getNameCount() == 4)
////					.flatMap(directory -> {
////						try {
////							if (Files.exists(WorldManager.metaFile(directory))) {
////								List<Path> worlds;
////								try (Stream<Path> fStream = Files.list(directory)) {
////									worlds = fStream.filter(Files::isDirectory).toList();
////								}
////								boolean stillHasWorlds = false;
////								List<Path> toDelete = new ArrayList<>();
////								for (Path world : worlds) {
////									if (LastAccessFile.isEverythingOlderThan(world, deleteUnusedRegionsAfterDays)) {
////										try (Stream<Path> fStream = Files.list(world)) {
////											fStream.forEach(toDelete::add);
////										}
////									} else {
////										stillHasWorlds = true;
////									}
////								}
////								if (!stillHasWorlds && LastAccessFile.isEverythingOlderThan(directory, deleteUnusedRegionsAfterDays)) {
////									try (Stream<Path> fStream = Files.list(directory)) {
////										fStream.forEach(toDelete::add);
////									}
////								}
////								return toDelete.stream();
////							} else {
////								if (LastAccessFile.isEverythingOlderThan(directory, deleteUnusedRegionsAfterDays)) {
////									try (Stream<Path> fStream = Files.list(directory)) {
////										return fStream.toList().stream();
////									}
////								}
////							}
////						} catch (IOException e) {
////							LOGGER.error("Failed to read last used file in " + directory + ":", e);
////						}
////						return Stream.empty();
////					})
////					.toList();
////		} catch (IOException e) {
////			LOGGER.error("Failed to index beacon cache for cleanup:", e);
////			return;
////		}
////
////		for (Path path : toBeDeleted) {
////			if (!Files.exists(path)) continue;
////			try {
////				Files.delete(path);
////				deleteParentsIfEmpty(path);
////			} catch (IOException e) {
////				LOGGER.error("Failed to delete " + path + ":", e);
////			}
////		}
//    }
//
//    private static void deleteParentsIfEmpty(Path path) throws IOException {
//        Path parent = path.getParent();
//        if (parent == null) return;
//        try (Stream<Path> stream = Files.list(parent)) {
//            if (stream.findAny().isPresent()) {
//                return;
//            }
//        }
//        Files.delete(parent);
//        deleteParentsIfEmpty(parent);
//    }


}