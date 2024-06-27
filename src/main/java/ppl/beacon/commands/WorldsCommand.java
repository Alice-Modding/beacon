package ppl.beacon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import ppl.beacon.fake.FakeManager;
import ppl.beacon.fake.world.WorldManager;
import ppl.beacon.fake.ext.ClientChunkManagerExt;

public class WorldsCommand implements Command<FabricClientCommandSource> {

    private final boolean loadAllMetadata;

    public WorldsCommand(boolean loadAllMetadata) {
        this.loadAllMetadata = loadAllMetadata;
    }

    @Override
    public int run(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        ClientWorld world = source.getWorld();

        FakeManager fakeManager = ((ClientChunkManagerExt) world.getChunkManager()).beacon$getFakeChunkManager();
        if (fakeManager == null) {
            source.sendError(Text.translatable("beacon.upgrade.not_enabled"));
            return 0;
        }

        WorldManager worlds = fakeManager.getWorlds();
        if (worlds == null) {
            source.sendError(Text.translatable("beacon.dynamic_multi_world.not_enabled"));
            return 0;
        }

        worlds.sendInfo(source, loadAllMetadata);
        return 0;
    }
}
