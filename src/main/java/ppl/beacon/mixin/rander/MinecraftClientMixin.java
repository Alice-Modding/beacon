package ppl.beacon.mixin.rander;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ppl.beacon.fake.FakeManager;
import ppl.beacon.fake.storage.FakeStorageManager;
import ppl.beacon.fake.ext.ClientChunkManagerExt;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow private Profiler profiler;

    @Shadow @Final public GameOptions options;

    @Shadow @Nullable public ClientWorld world;

    @Inject(method = "render", at = @At(value = "CONSTANT", args = "stringValue=tick"))
    private void beacon$render(CallbackInfo ci) {
        if (world == null) return;
        FakeManager fakeManager = ((ClientChunkManagerExt) world.getChunkManager()).beacon$getFakeChunkManager();
        if (fakeManager == null) return;

        profiler.push("bobbyUpdate");

        long frameTime = 1_000_000_000 / options.getMaxFps().getValue();
        // Arbitrarily choosing 1/4 of frame time as our max budget, that way we're hopefully not noticeable.
        long frameBudget = frameTime / 4;
        long timeLimit = Util.getMeasuringTimeNano() + frameBudget;
        fakeManager.update(() -> Util.getMeasuringTimeNano() < timeLimit);

        profiler.pop();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("RETURN"))
    private void beacon$disconnect(CallbackInfo ci) {
        FakeStorageManager.closeAll();
    }
}
