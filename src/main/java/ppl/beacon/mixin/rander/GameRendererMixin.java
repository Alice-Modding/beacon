package ppl.beacon.mixin.rander;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ppl.beacon.fake.chunk.FakeChunkManager;
import ppl.beacon.fake.ext.ClientChunkManagerExt;
import ppl.beacon.utils.FlawlessFrames;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void blockingBobbyUpdate(CallbackInfo ci) {
        if (!FlawlessFrames.isActive()) return;

        ClientWorld world = this.client.world;
        if (world == null) return;

        FakeChunkManager chunkManager = ((ClientChunkManagerExt) world.getChunkManager()).beacon$getFakeChunkManager();
        if (chunkManager == null) return;

        this.client.getProfiler().push("bobbyUpdate");

        chunkManager.update(() -> true);

        this.client.getProfiler().pop();
    }
}
