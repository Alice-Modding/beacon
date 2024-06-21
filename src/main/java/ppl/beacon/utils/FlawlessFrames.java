package ppl.beacon.utils;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implements the "Flawless Frames" FREX feature using which third-party mods can instruct Beacon to sacrifice
 * performance (even beyond the point where it can no longer achieve interactive frame rates) in exchange for
 * a noticeable boost to quality.
 * In Beacon's case, this means blocking loading all chunks are loaded from the cache.
 * See <a href="https://github.com/grondag/frex/pull/9">the link</a>
 */
public class FlawlessFrames {
    private static final Set<Object> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @SuppressWarnings("unchecked")
    public static void onClientInitialization() {
        Function<String, Consumer<Boolean>> provider = name -> active -> {
            if (active) ACTIVE.add(new Object());
            else ACTIVE.remove(new Object());
        };
        FabricLoader.getInstance()
                .getEntrypoints("frex_flawless_frames", Consumer.class)
                .forEach(api -> api.accept(provider));
    }

    public static boolean isActive() {
        return !ACTIVE.isEmpty();
    }
}
