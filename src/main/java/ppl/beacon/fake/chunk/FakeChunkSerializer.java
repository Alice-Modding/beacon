package ppl.beacon.fake.chunk;

import com.google.common.hash.Hashing;
import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.light.LightingProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppl.beacon.BeaconMod;
import ppl.beacon.config.Config;
import ppl.beacon.fake.ext.ChunkLightProviderExt;
import ppl.beacon.fake.ext.LightingProviderExt;

import java.util.*;
import java.util.function.Supplier;

public class FakeChunkSerializer extends ChunkSerializer {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final ChunkNibbleArray COMPLETELY_DARK = new ChunkNibbleArray();
    private static final ChunkNibbleArray COMPLETELY_LIT = new ChunkNibbleArray();
    private static final Codec<PalettedContainer<BlockState>> BLOCK_CODEC = PalettedContainer.createPalettedContainerCodec(
            Block.STATE_IDS,
            BlockState.CODEC,
            PalettedContainer.PaletteProvider.BLOCK_STATE,
            Blocks.AIR.getDefaultState()
    );

    private static void logRecoverableError(ChunkPos chunkPos, int y, String message) {
        LOGGER.error("Recoverable errors when loading section [" + chunkPos.x + ", " + y + ", " + chunkPos.z + "]: " + message);
    }

    private static Codec<ReadableContainer<RegistryEntry<Biome>>> getBiomCodec(DynamicRegistryManager registryManager) {
        Registry<Biome> biomeRegistry = registryManager.get(RegistryKeys.BIOME);
        return getReadableBiomCodec(biomeRegistry);
    }

    private static Codec<ReadableContainer<RegistryEntry<Biome>>> getReadableBiomCodec(Registry<Biome> biomeRegistry) {
        return PalettedContainer.createReadableContainerCodec(
                biomeRegistry.getIndexedEntries(),
                biomeRegistry.getEntryCodec(),
                PalettedContainer.PaletteProvider.BIOME,
                biomeRegistry.entryOf(BiomeKeys.PLAINS)
        );
    }

    private static Codec<PalettedContainer<RegistryEntry<Biome>>> getPalettedBiomCodec(Registry<Biome> biomeRegistry) {
        return PalettedContainer.createPalettedContainerCodec(
                biomeRegistry.getIndexedEntries(),
                biomeRegistry.getEntryCodec(),
                PalettedContainer.PaletteProvider.BIOME,
                biomeRegistry.entryOf(BiomeKeys.PLAINS)
        );
    }


    private static ChunkNibbleArray floodSkylightFromAbove(ChunkNibbleArray above) {
        if (above.isUninitialized()) {
            return new ChunkNibbleArray();
        } else {
            byte[] aboveBytes = above.asByteArray();
            byte[] belowBytes = new byte[2048];

            // Copy the bottom-most slice from above, 16 time over
            for (int i = 0; i < 16; i++) {
                System.arraycopy(aboveBytes, 0, belowBytes, i * 128, 128);
            }

            return new ChunkNibbleArray(belowBytes);
        }
    }

    public static NbtCompound serialize(WorldChunk chunk, LightingProvider lightingProvider) {
        ChunkPos chunkPos = chunk.getPos();
        NbtCompound nbtCompound = NbtHelper.putDataVersion(new NbtCompound());

        nbtCompound.putInt("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
        nbtCompound.putInt(X_POS_KEY, chunkPos.x);
        nbtCompound.putInt("yPos", chunk.getBottomSectionCoord());
        nbtCompound.putInt(Z_POS_KEY, chunkPos.z);
        nbtCompound.putBoolean(IS_LIGHT_ON_KEY, true);
        nbtCompound.putString("Status", Registries.CHUNK_STATUS.getId(chunk.getStatus()).toString());


        // NBT SECTION PART
        ChunkSection[] chunkSections = chunk.getSectionArray();
        NbtList section = new NbtList();

        DynamicRegistryManager registryManager = chunk.getWorld().getRegistryManager();
        Codec<ReadableContainer<RegistryEntry<Biome>>> biomeCodec = getBiomCodec(registryManager);

        for (int y = lightingProvider.getBottomY(), my = lightingProvider.getTopY(); y < my; ++y) {
            NbtCompound sectionNbt = new NbtCompound();
            int i = chunk.sectionCoordToIndex(y);

            ChunkSection chunkSection = (i >= 0 && i < chunkSections.length) ? chunkSections[i] : null;
            ChunkNibbleArray blockLight = lightingProvider.get(LightType.BLOCK).getLightSection(ChunkSectionPos.from(chunkPos, y));
            ChunkNibbleArray skyLight = lightingProvider.get(LightType.SKY).getLightSection(ChunkSectionPos.from(chunkPos, y));

            if (chunk instanceof FakeChunk fakeChunk) {
                blockLight = fakeChunk.blockLight[i + 1];
                skyLight = fakeChunk.skyLight[i + 1];
            }

            if (chunkSection != null) {
                sectionNbt.put("block_states", BLOCK_CODEC.encodeStart(NbtOps.INSTANCE, chunkSection.getBlockStateContainer()).getOrThrow());
                sectionNbt.put("biomes", biomeCodec.encodeStart(NbtOps.INSTANCE, chunkSection.getBiomeContainer()).getOrThrow());
            }

            if (blockLight != null && !blockLight.isUninitialized())
                sectionNbt.putByteArray(BLOCK_LIGHT_KEY, blockLight.asByteArray());

            if (skyLight != null && !skyLight.isUninitialized())
                sectionNbt.putByteArray(SKY_LIGHT_KEY, skyLight.asByteArray());

            if (!sectionNbt.isEmpty()) {
                sectionNbt.putByte("Y", (byte) i);
                section.add(sectionNbt);
            }
        }

        nbtCompound.put(SECTIONS_KEY, section);

        // NBT BLOCK ENTITIES PART
        NbtList block_entities;
        if (chunk instanceof FakeChunk fakeChunk) {
            block_entities = fakeChunk.serializedBlockEntities;
        } else {
            block_entities = new NbtList();
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                NbtCompound blockEntityNbt = chunk.getPackedBlockEntityNbt(pos, registryManager);
                if (blockEntityNbt != null) {
                    block_entities.add(blockEntityNbt);
                }
            }
        }
        nbtCompound.put("block_entities", block_entities);

        // NBT HEIGHTMAPS PART
        NbtCompound heightmaps = new NbtCompound();
        for (Map.Entry<Heightmap.Type, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getStatus().getHeightmapTypes().contains(entry.getKey())) {
                heightmaps.put((entry.getKey()).getName(), new NbtLongArray((entry.getValue()).asLongArray()));
            }
        }

        nbtCompound.put(HEIGHTMAPS_KEY, heightmaps);


        return nbtCompound;
    }

    public static Supplier<WorldChunk> deserialize(ChunkPos pos, NbtCompound nbtCompound, World world) {
        NbtList section = nbtCompound.getList(SECTIONS_KEY, NbtElement.COMPOUND_TYPE);
        ChunkSection[] chunkSections = new ChunkSection[world.countVerticalSections()];
        ChunkNibbleArray[] blockLight = new ChunkNibbleArray[chunkSections.length + 2];
        ChunkNibbleArray[] skyLight = new ChunkNibbleArray[chunkSections.length + 2];


        ChunkPos chunkPos = new ChunkPos(nbtCompound.getInt("xPos"), nbtCompound.getInt("zPos"));
        if (!Objects.equals(pos, chunkPos)) {
            LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", pos, pos, chunkPos);
        }

        DynamicRegistryManager registryManager = world.getRegistryManager();
        Registry<Biome> biomeRegistry = registryManager.get(RegistryKeys.BIOME);
        Codec<PalettedContainer<RegistryEntry<Biome>>> biomeCodec = getPalettedBiomCodec(biomeRegistry);
        for (int i = 0, s = section.size(); i < s; i++) {
            NbtCompound sectionNbt = section.getCompound(i);
            int y = sectionNbt.getByte("Y");
            int l = world.sectionCoordToIndex(y);

            if (l < -1 || l > chunkSections.length) continue;


            PalettedContainer<BlockState> blocks;
            if (nbtCompound.contains("block_states", NbtElement.COMPOUND_TYPE)) {
                blocks = BLOCK_CODEC.parse(NbtOps.INSTANCE, nbtCompound.getCompound("block_states"))
                        .promotePartial((errorMessage) -> logRecoverableError(chunkPos, y, errorMessage))
                        .getOrThrow(ChunkLoadingException::new);
            } else {
                blocks = new PalettedContainer<>(Block.STATE_IDS, Blocks.AIR.getDefaultState(), PalettedContainer.PaletteProvider.BLOCK_STATE);
            }

            PalettedContainer<RegistryEntry<Biome>> biomes;
            if (nbtCompound.contains("biomes", NbtElement.COMPOUND_TYPE)) {
                biomes = biomeCodec.parse(NbtOps.INSTANCE, nbtCompound.getCompound("biomes"))
                        .promotePartial((errorMessage) -> logRecoverableError(chunkPos, y, errorMessage))
                        .getOrThrow();
            } else {
                biomes = new PalettedContainer<>(biomeRegistry.getIndexedEntries(), biomeRegistry.entryOf(BiomeKeys.PLAINS), PalettedContainer.PaletteProvider.BIOME);
            }

            if (sectionNbt.contains("BlockLight", NbtElement.BYTE_ARRAY_TYPE)) {
                blockLight[l + 1] = new ChunkNibbleArray(sectionNbt.getByteArray("BlockLight"));
            }

            if (sectionNbt.contains("SkyLight", NbtElement.BYTE_ARRAY_TYPE)) {
                skyLight[l + 1] = new ChunkNibbleArray(sectionNbt.getByteArray("SkyLight"));
            }

            ChunkSection chunkSection = new ChunkSection(blocks, biomes);
            chunkSection.calculateCounts();
            if (!chunkSection.isEmpty()) chunkSections[l] = chunkSection;
        }

        // The nearest section data read from storage
        ChunkNibbleArray fullSectionAbove = null;
        // The nearest section data computed from the one above (based on its bottom-most layer).
        // May be re-used for multiple sections once computed.
        ChunkNibbleArray inferredSection = COMPLETELY_LIT;
        for (int y = skyLight.length - 1; y >= 0; y--) {
            ChunkNibbleArray skyLightSection = skyLight[y];

            // If we found a section, invalidate our inferred section cache and store it for later
            if (skyLightSection != null) {
                inferredSection = null;
                fullSectionAbove = skyLightSection;
                continue;
            }

            // If we are missing a section, infer it from the previous full section (the result of that can be re-used)
            if (inferredSection == null) {
                inferredSection = floodSkylightFromAbove(fullSectionAbove);
            }
            skyLight[y] = inferredSection;
        }

        FakeChunk chunk = new FakeChunk(world, pos, chunkSections);

        NbtCompound heightmaps = nbtCompound.getCompound(HEIGHTMAPS_KEY);
        EnumSet<Heightmap.Type> missingHightmapTypes = EnumSet.noneOf(Heightmap.Type.class);

        for (Heightmap.Type type : chunk.getStatus().getHeightmapTypes()) {
            String key = type.getName();
            if (heightmaps.contains(key, NbtElement.LONG_ARRAY_TYPE)) {
                chunk.setHeightmap(type, heightmaps.getLongArray(key));
            } else {
                missingHightmapTypes.add(type);
            }
        }

        Heightmap.populateHeightmaps(chunk, missingHightmapTypes);

        if (!BeaconMod.getConfig().getRanderConfig().isNoBlockEntities()) {
            NbtList blockEntitiesTag = nbtCompound.getList("block_entities", NbtElement.COMPOUND_TYPE);
            for (int i = 0, l = blockEntitiesTag.size(); i < l; i++) {
                chunk.addPendingBlockEntityNbt(blockEntitiesTag.getCompound(i));
            }
        }

        return loadChunk(chunk, blockLight, skyLight);
    }

    public static Supplier<WorldChunk> loadChunk(
            FakeChunk chunk,
            ChunkNibbleArray[] blockLight,
            ChunkNibbleArray[] skyLight
    ) {
        return () -> {
            ChunkPos pos = chunk.getPos();
            World world = chunk.getWorld();
            ChunkSection[] chunkSections = chunk.getSectionArray();

            boolean hasSkyLight = world.getDimension().hasSkyLight();

            LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();
            LightingProviderExt.get(lightingProvider).beacon$enabledColumn(pos.toLong());

            ChunkLightProviderExt blockLightProvider = ChunkLightProviderExt.get(lightingProvider.get(LightType.BLOCK));
            ChunkLightProviderExt skyLightProvider = ChunkLightProviderExt.get(lightingProvider.get(LightType.SKY));

            for (int i = -1, l = chunkSections.length + 1; i < l; i++) {
                int y = world.sectionIndexToCoord(i);
                if (blockLightProvider != null)
                    blockLightProvider.beacon$addSectionData(ChunkSectionPos.from(pos, y).asLong(), blockLight[i + 1]);
                if (skyLightProvider != null && hasSkyLight)
                    skyLightProvider.beacon$addSectionData(ChunkSectionPos.from(pos, y).asLong(), skyLight[i + 1]);
            }

            chunk.setTinted(BeaconMod.getConfig().getRanderConfig().isTintFakeChunks());

            // MC lazily loads block entities when they are first accessed.
            // It does so in a thread-unsafe way though, so if they are first accessed from e.g. a render thread, this
            // will cause threading issues (afaict thread-unsafe access to a chunk's block entities is still a problem
            // even in vanilla, e.g. if a block entity is removed while it is accessed, but apparently no one at Mojang
            // has run into that so far). To work around this, we force all block entities to be initialized
            // immediately, before any other code gets access to the chunk.
            for (BlockPos blockPos : chunk.getBlockEntityPositions()) {
                chunk.getBlockEntity(blockPos);
            }

            return chunk;
        };
    }


    /**
     * Computes a fingerprint for the blocks in the given chunk.
     *
     * Merely differentiates between opaque and non-opaque block, so should be fairly fast and stable across Minecraft
     * versions.
     *
     * Never returns 0 (so it may be used to indicate an absence of a value).
     * Returns 1 if the chunk does not contain enough entropy to reliably match against other chunks (e.g. flat world
     * chunk without any notable structure).
     */
    public static long fingerprint(WorldChunk chunk) {
        ChunkSection[] sectionArray = chunk.getSectionArray();

        BitSet opaqueBlocks = new BitSet(sectionArray.length * 16 * 16 * 16);

        // We consider a chunk low quality (and return 1) if there are no y layers that have mixed content.
        // I.e. each 16x16x1 layer is either completely filled or completely empty.
        boolean lowQuality = true;

        int i = 0;
        for (ChunkSection chunkSection : sectionArray) {
            if (chunkSection == null) {
                i += 16 * 16 * 16;
                continue;
            }
            PalettedContainer<BlockState> container = chunkSection.getBlockStateContainer();
            for (int y = 0; y < 16; y++) {
                int opaqueCount = 0;

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState blockState = container.get(x, y, z);
                        if (blockState.isOpaque()) {
                            opaqueBlocks.set(i);
                            opaqueCount++;
                        }
                        i++;
                    }
                }

                if (lowQuality && opaqueCount > 0 && opaqueCount < 16 * 16) {
                    lowQuality = false;
                }
            }
        }

        if (lowQuality) return 1;

        long fingerprint = Hashing.farmHashFingerprint64().hashBytes(opaqueBlocks.toByteArray()).asLong();
        // 0 and 1 are reserved
        return (fingerprint == 0 || fingerprint == 1) ? fingerprint + 2 : fingerprint;
    }



}
