package ppl.beacon.config;

import org.spongepowered.configurate.objectmapping.meta.Comment;

public class RanderConfig {
    public static final RanderConfig DEFAULT = new RanderConfig();

    private boolean enabled = true;
    private boolean dynamicMultiWorld = false;
    private boolean noBlockEntities = true;
    private boolean tintFakeChunks = false;
    private int unloadDelaySecs = 60;
    private int deleteUnusedRegionsAfterDays = -1;
    private int maxRenderDistance = 32;
    private int viewDistanceOverwrite = 0;

    public RanderConfig() {}

    public RanderConfig(
            boolean enabled,
            boolean dynamicMultiWorld,
            boolean noBlockEntities,
            boolean tintFakeChunks,
            int unloadDelaySecs,
            int deleteUnusedRegionsAfterDays,
            int maxRenderDistance,
            int viewDistanceOverwrite
    ) {
        this.enabled = enabled;
        this.dynamicMultiWorld = dynamicMultiWorld;
        this.noBlockEntities = noBlockEntities;
        this.tintFakeChunks = tintFakeChunks;
        this.unloadDelaySecs = unloadDelaySecs;
        this.deleteUnusedRegionsAfterDays = deleteUnusedRegionsAfterDays;
        this.maxRenderDistance = maxRenderDistance;
        this.viewDistanceOverwrite = viewDistanceOverwrite;
    }

    public boolean isNoBlockEntities() {
        return noBlockEntities;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDynamicMultiWorld() {
        return dynamicMultiWorld;
    }

    public boolean isTintFakeChunks() {
        return tintFakeChunks;
    }

    public int getUnloadDelaySecs() {
        return unloadDelaySecs;
    }

    public int getDeleteUnusedRegionsAfterDays() {
        return deleteUnusedRegionsAfterDays;
    }

    public int getMaxRenderDistance() {
        return maxRenderDistance;
    }

    public int getViewDistanceOverwrite() {
        return viewDistanceOverwrite;
    }
}
