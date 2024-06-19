package ppl.beacon.mixin.rander;

import ppl.beacon.BeaconMod;
import ppl.beacon.fake.chunk.FakeChunk;
import ppl.beacon.fake.chunk.FakeChunkManager;
import ppl.beacon.VisibleChunksTracker;
import ppl.beacon.ext.ClientChunkManagerExt;

import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerMixin implements ClientChunkManagerExt {
    @Shadow @Final private WorldChunk emptyChunk;

    @Shadow @Nullable public abstract WorldChunk getChunk(int i, int j, ChunkStatus chunkStatus, boolean bl);
    @Shadow private static int getChunkMapRadius(int loadDistance) { throw new AssertionError(); }

    protected FakeChunkManager chunkManager;

    // Tracks which real chunks are visible (whether or not the were actually received), so we can
    // properly unload (i.e. save and replace with fake) them when the server center pos or view distance changes.
    private final VisibleChunksTracker realChunksTracker = new VisibleChunksTracker();

    // List of real chunks saved just before they are unloaded, so we can restore fake ones in their place afterwards
    private final List<Pair<Long, Supplier<WorldChunk>>> chunkReplacements = new ArrayList<>();

    @Override
    public FakeChunkManager getFakeChunkManager() {
        return chunkManager;
    }

    @Override
    public VisibleChunksTracker getRealChunksTracker() {
        return realChunksTracker;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bobbyInit(ClientWorld world, int loadDistance, CallbackInfo ci) {
        if (!BeaconMod.getInstance().isEnabled()) return;
        chunkManager = new FakeChunkManager(world, (ClientChunkManager) (Object) this);
        realChunksTracker.update(0, 0, getChunkMapRadius(loadDistance), null, null);
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/WorldChunk;", at = @At("RETURN"), cancellable = true)
    private void bobbyGetChunk(int x, int z, ChunkStatus chunkStatus, boolean orEmpty, CallbackInfoReturnable<WorldChunk> ci) {
        // Did we find a live chunk?
        if (ci.getReturnValue() != (orEmpty ? emptyChunk : null)) return;

        if (chunkManager == null) return;

        // Otherwise, see if we've got one
        WorldChunk chunk = chunkManager.getChunk(x, z);
        if (chunk != null) ci.setReturnValue(chunk);
    }

    @Inject(method = "loadChunkFromPacket", at = @At("HEAD"))
    private void bobbyUnloadFakeChunk(int x, int z, PacketByteBuf buf, NbtCompound nbt, Consumer<ChunkData.BlockEntityVisitor> consumer, CallbackInfoReturnable<WorldChunk> cir) {
        if (chunkManager == null) return;

        // This needs to be called unconditionally because even if there is no chunk loaded at the moment,
        // we might already have one queued which we need to cancel as otherwise it will overwrite the real one later.
        chunkManager.unload(x, z, true);
    }

    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void bobbyFingerprintRealChunk(CallbackInfoReturnable<WorldChunk> cir) {
        if (chunkManager == null) return;

        chunkManager.fingerprint(cir.getReturnValue());
    }

    @Unique
    private void saveRealChunk(long chunkPos) {
        int chunkX = ChunkPos.getPackedX(chunkPos);
        int chunkZ = ChunkPos.getPackedZ(chunkPos);

        WorldChunk chunk = getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null || chunk instanceof FakeChunk) return;

        Supplier<WorldChunk> copy = chunkManager.save(chunk);

        if (chunkManager.shouldBeLoaded(chunkX, chunkZ)) {
            chunkReplacements.add(Pair.of(chunkPos, copy));
        }
    }

    @Unique
    private void substituteFakeChunksForUnloadedRealOnes() {
        for (Pair<Long, Supplier<WorldChunk>> entry : chunkReplacements) {
            long chunkPos = entry.getKey();
            chunkManager.load(ChunkPos.getPackedX(chunkPos), ChunkPos.getPackedZ(chunkPos), entry.getValue().get());
        }
        chunkReplacements.clear();
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void bobbySaveChunk(ChunkPos pos, CallbackInfo ci) {
        if (chunkManager == null) return;
        saveRealChunk(pos.toLong());
    }

    @Inject(method = "setChunkMapCenter", at = @At("HEAD"))
    private void bobbySaveChunksBeforeMove(int x, int z, CallbackInfo ci) {
        if (chunkManager == null) return;
        realChunksTracker.updateCenter(x, z, this::saveRealChunk, null);
    }

    @Inject(method = "updateLoadDistance", at = @At("HEAD"))
    private void bobbySaveChunksBeforeResize(int loadDistance, CallbackInfo ci) {
        if (chunkManager == null) return;
        realChunksTracker.updateViewDistance(getChunkMapRadius(loadDistance), this::saveRealChunk, null);
    }

    @Inject(method = { "unload", "setChunkMapCenter", "updateLoadDistance" }, at = @At("RETURN"))
    private void bobbySubstituteFakeChunksForUnloadedRealOnes(CallbackInfo ci) {
        if (chunkManager == null) return;
        substituteFakeChunksForUnloadedRealOnes();
    }

    @Inject(method = "getDebugString", at = @At("RETURN"), cancellable = true)
    private void bobbyDebugString(CallbackInfoReturnable<String> cir) {
        if (chunkManager == null) return;
        cir.setReturnValue(cir.getReturnValue() + " " + chunkManager.getDebugString());
    }
}