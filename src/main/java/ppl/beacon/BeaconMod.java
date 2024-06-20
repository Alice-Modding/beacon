package ppl.beacon;

import ca.stellardrift.confabricate.Confabricate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.reference.ConfigurationReference;
import org.spongepowered.configurate.reference.ValueReference;
import org.spongepowered.configurate.reference.WatchServiceListener;
import ppl.beacon.commands.CreateWorldCommand;
import ppl.beacon.commands.MergeWorldsCommand;
import ppl.beacon.commands.UpgradeCommand;
import ppl.beacon.commands.WorldsCommand;
import ppl.beacon.config.Config;
import ppl.beacon.config.ConfigScreenFactory;
import ppl.beacon.config.RanderConfig;
import ppl.beacon.fake.chunk.FakeChunk;
import ppl.beacon.fake.chunk.FakeChunkManager;
import ppl.beacon.fake.ext.ClientChunkManagerExt;
import ppl.beacon.fake.world.WorldManager;
import ppl.beacon.mixin.option.SimpleOptionAccessor;
import ppl.beacon.mixin.option.ValidatingIntSliderCallbacksAccessor;
import ppl.beacon.utils.FlawlessFrames;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class BeaconMod implements ClientModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LogManager.getLogger();
	public static final String MOD_ID = "beacon-mod";

	private static final MinecraftClient client = MinecraftClient.getInstance();

	private ValueReference<RanderConfig, CommentedConfigurationNode> configReference;

	private static BeaconMod instance;
	private Config config;



	@Override
	public void onInitializeClient() {
		try {
			Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".conf");
			@SuppressWarnings("resource") // we'll keep this around for the entire lifetime of our mod
			ConfigurationReference<CommentedConfigurationNode> rootRef = getOrCreateWatchServiceListener()
					.listenToConfiguration(path -> HoconConfigurationLoader.builder().path(path).build(), configPath);
			configReference = rootRef.referenceTo(RanderConfig.class);
			rootRef.saveAsync();
		} catch (IOException e) {
			e.printStackTrace();
		}

		ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) ->
				dispatcher.register(literal("bobby")
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

		configReference.subscribe(new TaintChunksConfigHandler()::update);
		configReference.subscribe(new MaxRenderDistanceConfigHandler()::update);

		Util.getIoWorkerExecutor().submit(this::cleanupOldWorlds);
	}

	public static BeaconMod getInstance() {
		return instance;
	}

	public boolean isRanderEnabled() {
		return Config.renderer.isEnabled()
				// For singleplayer, disable ourselves unless the view-distance overwrite is active.
				&& (client.getServer() == null || Config.renderer.getViewDistanceOverwrite() != 0);
	}

	public Screen createConfigScreen(Screen parent) {
		if (FabricLoader.getInstance().isModLoaded("cloth-config2")) {
			return ConfigScreenFactory.createRanderConfigScreen(parent, Config.renderer, configReference::setAndSaveAsync);
		}
		return null;
	}

	private void cleanupOldWorlds() {
//		int deleteUnusedRegionsAfterDays = Config.renderer.getDeleteUnusedRegionsAfterDays();
//		if (deleteUnusedRegionsAfterDays < 0) return;
//
//		Path basePath = client.runDirectory.toPath().resolve(".bobby");
//
//		List<Path> toBeDeleted;
//		try (Stream<Path> stream = Files.walk(basePath, 4)) {
//			toBeDeleted = stream
//					.filter(it -> basePath.relativize(it).getNameCount() == 4)
//					.flatMap(directory -> {
//						try {
//							if (Files.exists(WorldManager.metaFile(directory))) {
//								List<Path> worlds;
//								try (Stream<Path> fStream = Files.list(directory)) {
//									worlds = fStream.filter(Files::isDirectory).toList();
//								}
//								boolean stillHasWorlds = false;
//								List<Path> toDelete = new ArrayList<>();
//								for (Path world : worlds) {
//									if (LastAccessFile.isEverythingOlderThan(world, deleteUnusedRegionsAfterDays)) {
//										try (Stream<Path> fStream = Files.list(world)) {
//											fStream.forEach(toDelete::add);
//										}
//									} else {
//										stillHasWorlds = true;
//									}
//								}
//								if (!stillHasWorlds && LastAccessFile.isEverythingOlderThan(directory, deleteUnusedRegionsAfterDays)) {
//									try (Stream<Path> fStream = Files.list(directory)) {
//										fStream.forEach(toDelete::add);
//									}
//								}
//								return toDelete.stream();
//							} else {
//								if (LastAccessFile.isEverythingOlderThan(directory, deleteUnusedRegionsAfterDays)) {
//									try (Stream<Path> fStream = Files.list(directory)) {
//										return fStream.toList().stream();
//									}
//								}
//							}
//						} catch (IOException e) {
//							LOGGER.error("Failed to read last used file in " + directory + ":", e);
//						}
//						return Stream.empty();
//					})
//					.toList();
//		} catch (IOException e) {
//			LOGGER.error("Failed to index bobby cache for cleanup:", e);
//			return;
//		}
//
//		for (Path path : toBeDeleted) {
//			if (!Files.exists(path)) continue;
//			try {
//				Files.delete(path);
//				deleteParentsIfEmpty(path);
//			} catch (IOException e) {
//				LOGGER.error("Failed to delete " + path + ":", e);
//			}
//		}
	}

	private static void deleteParentsIfEmpty(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent == null) return;
		try (Stream<Path> stream = Files.list(parent)) {
			if (stream.findAny().isPresent()) {
				return;
			}
		}
		Files.delete(parent);
		deleteParentsIfEmpty(parent);
	}

	private static WatchServiceListener getOrCreateWatchServiceListener() throws IOException {
		try {
			return Confabricate.fileWatcher();
		} catch (NoClassDefFoundError | NoSuchMethodError ignored) {
			return createWatchServiceListener();
		}
	}

	private static WatchServiceListener createWatchServiceListener() throws IOException {
		WatchServiceListener listener = WatchServiceListener.create();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				listener.close();
			} catch (IOException e) {
				LOGGER.catching(e);
			}
		}, "Configurate shutdown thread (Bobby)"));

		return listener;
	}

	private class TaintChunksConfigHandler {
		private boolean wasEnabled = Config.renderer.isTintFakeChunks();

		public void update(RanderConfig config) {
			client.submit(() -> setEnabled(config.isTintFakeChunks()));
		}

		private void setEnabled(boolean enabled) {
			if (wasEnabled == enabled) {
				return;
			}
			wasEnabled = enabled;

			ClientWorld world = client.world;
			if (world == null) {
				return;
			}

			FakeChunkManager bobbyChunkManager = ((ClientChunkManagerExt) world.getChunkManager()).beacon$getFakeChunkManager();
			if (bobbyChunkManager == null) {
				return;
			}

			for (WorldChunk fakeChunk : bobbyChunkManager.getFakeChunks()) {
				((FakeChunk) fakeChunk).setTinted(enabled);
			}
		}
	}

	private class MaxRenderDistanceConfigHandler {
		private int oldMaxRenderDistance = 0;

		{
			update(Config.renderer, true);
		}

		public void update(RanderConfig config) {
			update(config, false);
		}

		public void update(RanderConfig config, boolean increaseOnly) {
			client.submit(() -> setMaxRenderDistance(config.getMaxRenderDistance(), increaseOnly));
		}

		@SuppressWarnings({"ConstantConditions", "unchecked"})
		private void setMaxRenderDistance(int newMaxRenderDistance, boolean increaseOnly) {
			if (oldMaxRenderDistance == newMaxRenderDistance) {
				return;
			}
			oldMaxRenderDistance = newMaxRenderDistance;

			SimpleOption<Integer> viewDistance = client.options.getViewDistance();
			if (viewDistance.getCallbacks() instanceof SimpleOption.ValidatingIntSliderCallbacks callbacks) {
				ValidatingIntSliderCallbacksAccessor callbacksAcc = (ValidatingIntSliderCallbacksAccessor)(Object) callbacks;
				if (increaseOnly) {
					callbacksAcc.setMaxInclusive(Math.max(callbacks.maxInclusive(), newMaxRenderDistance));
				} else {
					callbacksAcc.setMaxInclusive(newMaxRenderDistance);
				}
				SimpleOptionAccessor<Integer> optionAccessor = (SimpleOptionAccessor<Integer>)(Object) viewDistance;
				optionAccessor.setCodec(callbacks.codec());
			}
		}
	}
}