package ppl.beacon.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import ppl.beacon.BeaconMod;

import java.util.function.Consumer;

public class ConfigScreenFactory {
    public static Screen createRanderConfigScreen(Screen parent, RanderConfig config, Consumer<RanderConfig> update) {
        RanderConfig defaultConfig = RanderConfig.DEFAULT;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("title.beacon.config"));


        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        BooleanListEntry enabled = entryBuilder
                .startBooleanToggle(Text.translatable("option.beacon.enabled"), config.isEnabled())
                .setDefaultValue(defaultConfig.isEnabled())
                .build();

        BooleanListEntry dynamicMultiWorld = entryBuilder
                .startBooleanToggle(Text.translatable("option.beacon.dynamic_multi_world"), config.isDynamicMultiWorld())
                .setTooltip(Text.translatable("tooltip.option.beacon.dynamic_multi_world"))
                .setDefaultValue(defaultConfig.isDynamicMultiWorld())
                .build();

        BooleanListEntry noBlockEntities = entryBuilder
                .startBooleanToggle(Text.translatable("option.beacon.no_block_entities"), config.isNoBlockEntities())
                .setTooltip(Text.translatable("tooltip.option.beacon.no_block_entities"))
                .setDefaultValue(defaultConfig.isNoBlockEntities())
                .build();

        BooleanListEntry taintFakeChunks = entryBuilder
                .startBooleanToggle(Text.translatable("option.beacon.taint_fake_chunks"), config.isTintFakeChunks())
                .setTooltip(Text.translatable("tooltip.option.beacon.taint_fake_chunks"))
                .setDefaultValue(defaultConfig.isTintFakeChunks())
                .build();

        IntegerListEntry unloadDelaySecs = entryBuilder
                .startIntField(Text.translatable("option.beacon.unload_delay"), config.getUnloadDelaySecs())
                .setTooltip(Text.translatable("tooltip.option.beacon.unload_delay"))
                .setDefaultValue(defaultConfig.getUnloadDelaySecs())
                .build();

        IntegerListEntry deleteUnusedRegionsAfterDays = entryBuilder
                .startIntField(Text.translatable("option.beacon.delete_unused_regions_after_days"), config.getDeleteUnusedRegionsAfterDays())
                .setTooltip(Text.translatable("tooltip.option.beacon.delete_unused_regions_after_days"))
                .setDefaultValue(defaultConfig.getDeleteUnusedRegionsAfterDays())
                .build();

        IntegerListEntry maxRenderDistance = entryBuilder
                .startIntField(Text.translatable("option.beacon.max_render_distance"), config.getMaxRenderDistance())
                .setTooltip(Text.translatable("tooltip.option.beacon.max_render_distance"))
                .setDefaultValue(defaultConfig.getMaxRenderDistance())
                .build();

        IntegerSliderEntry viewDistanceOverwrite = entryBuilder
                .startIntSlider(Text.translatable("option.beacon.view_distance_overwrite"), config.getViewDistanceOverwrite(), 0, 32)
                .setTooltip(Text.translatable("tooltip.option.beacon.view_distance_overwrite"))
                .setDefaultValue(defaultConfig.getViewDistanceOverwrite())
                .build();

        ConfigCategory general = builder.getOrCreateCategory(Text.translatable("category.beacon.general"));
        general.addEntry(enabled);
        general.addEntry(dynamicMultiWorld);
        general.addEntry(noBlockEntities);
        general.addEntry(taintFakeChunks);
        general.addEntry(unloadDelaySecs);
        general.addEntry(deleteUnusedRegionsAfterDays);
        general.addEntry(maxRenderDistance);
        general.addEntry(viewDistanceOverwrite);

        builder.setSavingRunnable(() -> update.accept(new RanderConfig(
                enabled.getValue(),
                dynamicMultiWorld.getValue(),
                noBlockEntities.getValue(),
                taintFakeChunks.getValue(),
                unloadDelaySecs.getValue(),
                deleteUnusedRegionsAfterDays.getValue(),
                maxRenderDistance.getValue(),
                viewDistanceOverwrite.getValue()
        )));

        return builder.build();
    }
}
