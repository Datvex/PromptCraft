package dev.promptcraft.task;

import dev.promptcraft.structure.PromptCraftStructure;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class BuildTask implements Task {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final BlockPos origin;
    private final PromptCraftStructure structure;
    private int opIndex = 0;

    public BuildTask(ServerPlayerEntity player, BlockPos origin, PromptCraftStructure structure) {
        this.player = player;
        this.world = (ServerWorld) player.getWorld();
        this.origin = origin;
        this.structure = structure;
    }

    @Override
    public boolean tick() {
        if (structure == null || structure.operations == null || opIndex >= structure.operations.size()) {
            player.sendMessage(Text.literal("Building complete!").formatted(Formatting.GREEN), false);
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
        
        Block block = Registries.BLOCK.get(new Identifier(blockId));
        if (block == Blocks.AIR && !blockId.equals("minecraft:air")) block = Blocks.STONE;
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

        if ("place".equals(op.type) && op.pos != null && op.pos.length == 3) {
            world.setBlockState(origin.add(op.pos[0], op.pos[1], op.pos[2]), state, 3);
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
                        world.setBlockState(origin.add(x, y, z), state, 3);
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