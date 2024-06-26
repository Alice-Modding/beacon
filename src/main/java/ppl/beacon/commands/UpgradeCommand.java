package ppl.beacon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import ppl.beacon.fake.FakeManager;
import ppl.beacon.fake.storage.FakeStorage;
import ppl.beacon.fake.ext.ClientChunkManagerExt;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiConsumer;

public class UpgradeCommand implements Command<FabricClientCommandSource> {
    @Override
    public int run(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        MinecraftClient client = source.getClient();
        ClientWorld world = source.getWorld();

        ClientChunkManagerExt chunkManager = (ClientChunkManagerExt) world.getChunkManager();
        FakeManager fakeManager = chunkManager.beacon$getFakeChunkManager();
        if (fakeManager == null) {
            source.sendError(Text.translatable("beacon.upgrade.not_enabled"));
            return 0;
        }

        List<FakeStorage> storages = List.of(fakeManager.getStorage());

        source.sendFeedback(Text.translatable("beacon.upgrade.begin"));
        new Thread(() -> {
            for (int i = 0, l = storages.size(); i < l; i++) {
                FakeStorage storage = storages.get(i);
                try {
                    storage.upgrade(world.getRegistryKey(), new ProgressReported(client, i, storages.size()));
                } catch (IOException e) {
                    e.printStackTrace();
                    source.sendError(Text.of(e.getMessage()));
                }
            }
            client.submit(() -> {
                source.sendFeedback(Text.translatable("beacon.upgrade.done"));
                fakeManager.loadMissingChunksFromCache();
            });
        }, "beacon-upgrade").start();

        return 0;
    }

    private static class ProgressReported implements BiConsumer<Integer, Integer> {
        private final MinecraftClient client;
        private final int worldIndex;
        private final int totalWorlds;
        private Instant nextReport = Instant.MIN;
        private int done;
        private int total = Integer.MAX_VALUE;

        public ProgressReported(MinecraftClient client, int worldIndex, int totalWorlds) {
            this.client = client;
            this.worldIndex = worldIndex;
            this.totalWorlds = totalWorlds;
        }

        @Override
        public synchronized void accept(Integer done, Integer total) {
            this.done = Math.max(this.done, done);
            this.total = Math.min(this.total, total);

            Instant now = Instant.now();
            if (now.isAfter(nextReport) || this.done == this.total) {
                nextReport = now.plus(3, ChronoUnit.SECONDS);

                Text text = Text.translatable("beacon.upgrade.progress", this.done, this.total, this.worldIndex + 1, this.totalWorlds);
                client.submit(() -> client.inGameHud.getChatHud().addMessage(text));
            }
        }
    }
}
