package dev.promptcraft.task;

import dev.promptcraft.structure.PromptCraftStructure;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
            return true; // Task finished
        }

        PromptCraftStructure.Operation op = structure.operations.get(opIndex++);
        executeOp(op);
        return false; // Continue next tick
    }

    private void executeOp(PromptCraftStructure.Operation op) {
        if (op.block == null) return;
        
        // Strip block states for MVP to avoid parse errors (e.g. minecraft:oak_door[facing=north] -> minecraft:oak_door)
        String blockId = op.block.split("\\[")[0];
        
        Block block = Registries.BLOCK.get(new Identifier(blockId));
        if (block == Blocks.AIR && !blockId.equals("minecraft:air")) block = Blocks.STONE; // Fallback for invalid blocks
        BlockState state = block.getDefaultState();

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
                        if ("hollow_box".equals(op.type)) {
                            if (x > minX && x < maxX && y > minY && y < maxY && z > minZ && z < maxZ) continue;
                        }
                        world.setBlockState(origin.add(x, y, z), state, 3);
                    }
                }
            }
        }
    }
}