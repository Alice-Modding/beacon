package ppl.beacon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import ppl.beacon.fake.chunk.FakeChunkManager;
import ppl.beacon.fake.world.WorldManager;
import ppl.beacon.fake.ext.ClientChunkManagerExt;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;

public class MergeWorldsCommand implements Command<FabricClientCommandSource> {
    @Override
    public int run(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        ClientWorld world = source.getWorld();

        FakeChunkManager chunkManager = ((ClientChunkManagerExt) world.getChunkManager()).beacon$getFakeChunkManager();
        if (chunkManager == null) {
            source.sendError(Text.translatable("beacon.upgrade.not_enabled"));
            return 0;
        }

        WorldManager worlds = chunkManager.getWorlds();
        if (worlds == null) {
            source.sendError(Text.translatable("beacon.dynamic_multi_world.not_enabled"));
            return 0;
        }

        int sourceId = getInteger(context, "source");
        int targetId = getInteger(context, "target");

        worlds.userRequestedMerge(source, sourceId, targetId);

        return 0;
    }
}
