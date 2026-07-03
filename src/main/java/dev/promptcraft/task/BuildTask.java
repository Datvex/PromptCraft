package dev.promptcraft.task;

import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.session.GenerationSession;
import dev.promptcraft.session.PromptSessionManager;
import dev.promptcraft.structure.PromptCraftStructure;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class BuildTask implements Task {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final BlockPos origin;
    private final PromptCraftStructure structure;
    private final GenerationSession session;
    private int opIndex = 0;

    private final Set<String> unknownBlocks = new LinkedHashSet<>();

    public BuildTask(ServerPlayerEntity player, BlockPos origin, PromptCraftStructure structure, GenerationSession session) {
        this.player = player;
        this.world = (ServerWorld) player.getWorld();
        this.origin = origin;
        this.structure = structure;
        this.session = session;
        if (session != null) session.markBuildStarted();
    }

    @Override
    public boolean tick() {
        if (session != null && session.isCancelled()) {
            session.markBuildFinished();
            return true;
        }

        if (structure == null || structure.operations == null || opIndex >= structure.operations.size()) {
            if (!unknownBlocks.isEmpty()) {
                player.sendMessage(Text.literal("Warning: AI referenced unknown block(s), skipped: " + String.join(", ", unknownBlocks))
                        .formatted(Formatting.YELLOW), false);
            }
            player.sendMessage(Text.literal("Building complete!").formatted(Formatting.GREEN), false);

            if (session != null) session.markBuildFinished();
            PromptSessionManager.clearGeneration(player);
            PromptCraftNetworking.sendAiStreamEvent(player, "done", "");
            return true;
        }

        PromptCraftStructure.Operation op = structure.operations.get(opIndex++);
        executeOp(op);
        return false;
    }

    private void executeOp(PromptCraftStructure.Operation op) {
        if (op.block == null) return;

        String[] parts = op.block.split("\\[");
        String blockId = parts[0];

        Identifier id;
        try {
            id = new Identifier(blockId);
        } catch (Exception e) {
            unknownBlocks.add(blockId);
            return;
        }

        Optional<Block> blockOpt = Registries.BLOCK.getOrEmpty(id);
        if (blockOpt.isEmpty()) {
            unknownBlocks.add(blockId);
            return;
        }

        Block block = blockOpt.get();
        BlockState state = block.getDefaultState();

        if (parts.length > 1) {
            String props = parts[1].replace("]", "");
            for (String prop : props.split(",")) {
                String[] kv = prop.split("=");
                if (kv.length == 2) {
                    state = applyProperty(state, kv[0], kv[1]);
                }
            }
        }

        int flags = BlockPlacementUtil.flagsFor(state);

        if ("place".equals(op.type) && op.pos != null && op.pos.length == 3) {
            world.setBlockState(origin.add(op.pos[0], op.pos[1], op.pos[2]), state, flags);
        } else if (("fill".equals(op.type) || "hollow_box".equals(op.type)) && op.from != null && op.to != null && op.from.length == 3 && op.to.length == 3) {
            int minX = Math.min(op.from[0], op.to[0]);
            int minY = Math.min(op.from[1], op.to[1]);
            int minZ = Math.min(op.from[2], op.to[2]);
            int maxX = Math.max(op.from[0], op.to[0]);
            int maxY = Math.max(op.from[1], op.to[1]);
            int maxZ = Math.max(op.from[2], op.to[2]);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if ("hollow_box".equals(op.type) && x > minX && x < maxX && y > minY && y < maxY && z > minZ && z < maxZ) continue;
                        world.setBlockState(origin.add(x, y, z), state, flags);
                    }
                }
            }
        }
    }

    private BlockState applyProperty(BlockState state, String key, String value) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals(key)) {
                return parseAndSet(state, prop, value);
            }
        }
        return state;
    }

    private <T extends Comparable<T>> BlockState parseAndSet(BlockState state, Property<T> prop, String value) {
        return prop.parse(value).map(t -> state.with(prop, t)).orElse(state);
    }
}
