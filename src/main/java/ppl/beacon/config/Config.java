package ppl.beacon.config;

import ca.stellardrift.confabricate.Confabricate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.reference.ConfigurationReference;
import org.spongepowered.configurate.reference.ValueReference;
import org.spongepowered.configurate.reference.WatchServiceListener;
import ppl.beacon.fake.chunk.FakeChunk;
import ppl.beacon.fake.chunk.FakeChunkManager;
import ppl.beacon.fake.ext.ClientChunkManagerExt;
import ppl.beacon.mixin.option.SimpleOptionAccessor;
import ppl.beacon.mixin.option.ValidatingIntSliderCallbacksAccessor;

import java.io.IOException;
import java.nio.file.Path;

public class Config {
    public static final Logger LOGGER = LogManager.getLogger();
    private static final MinecraftClient client = MinecraftClient.getInstance();

    private ValueReference<RanderConfig, CommentedConfigurationNode> randerConfig;

    public void init() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve( "beacon_rander.conf");
            @SuppressWarnings("resource") // we'll keep this around for the entire lifetime of our mod
            ConfigurationReference<CommentedConfigurationNode> rootRef = getOrCreateWatchServiceListener().listenToConfiguration(path -> HoconConfigurationLoader.builder().path(path).build(), configPath);
            randerConfig = rootRef.referenceTo(RanderConfig.class);
            rootRef.saveAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }


        randerConfig.subscribe(new TaintChunksConfigHandler()::update);
        randerConfig.subscribe(new MaxRenderDistanceConfigHandler()::update);
    }


    public RanderConfig getRanderConfig() {
        return randerConfig != null ? randerConfig.get() : RanderConfig.DEFAULT;
    }

    public Screen createConfigScreen(Screen parent) {
        if (FabricLoader.getInstance().isModLoaded("cloth-config2")) {
            return ConfigScreenFactory.createRanderConfigScreen(parent, getRanderConfig(), randerConfig::setAndSaveAsync);
        }
        return null;
    }

    private static WatchServiceListener getOrCreateWatchServiceListener() throws IOException {
        try {
            return Confabricate.fileWatcher();
        } catch (NoClassDefFoundError | NoSuchMethodError ignored) {
            WatchServiceListener listener = WatchServiceListener.create();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    listener.close();
                } catch (IOException e) {
                    LOGGER.catching(e);
                }
            }, "Configurate shutdown thread (Beacon)"));

            return listener;
        }
    }

    private class TaintChunksConfigHandler {
        private boolean wasEnabled = getRanderConfig().isTintFakeChunks();

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
            update(getRanderConfig(), true);
        }

        public void update(RanderConfig config) {
            update(config, false);
        }

        public void update(RanderConfig config, boolean increaseOnly) {
            client.submit(() -> setMaxRenderDistance(config.getMaxRenderDistance(), increaseOnly));
        }

        @SuppressWarnings({"ConstantConditions", "unchecked"})
        private void setMaxRenderDistance(int newMaxRenderDistance, boolean increaseOnly) {
            if (oldMaxRenderDistance == newMaxRenderDistance) return;
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
