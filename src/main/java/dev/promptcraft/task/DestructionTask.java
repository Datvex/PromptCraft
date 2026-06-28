package dev.promptcraft.task;

import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.structure.BlockSnapshot;
import dev.promptcraft.structure.HistoryManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class DestructionTask implements Task {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final BlockPos min, max;
    private final Runnable onComplete;
    private final List<BlockSnapshot> snapshotList = new ArrayList<>();

    private int currentX, currentY, currentZ;

    public DestructionTask(ServerPlayerEntity player, BlockPos min, BlockPos max, Runnable onComplete) {
        this.player = player;
        this.world = (ServerWorld) player.getWorld();
        this.min = min;
        this.max = max;
        this.onComplete = onComplete;
        this.currentX = min.getX();
        this.currentY = min.getY();
        this.currentZ = min.getZ();
    }

    @Override
    public boolean tick() {
        int blocksPerTick = 1000;
        int processed = 0;
        boolean animation = PromptCraftConfigManager.get().enableDestructionAnimation;

        while (processed < blocksPerTick) {
            if (currentY > max.getY()) {
                HistoryManager.pushUndo(player, snapshotList);
                player.sendMessage(Text.literal("Area cleared. Generating...").formatted(Formatting.GREEN), false);
                if (onComplete != null) onComplete.run();
                return true;
            }

            BlockPos pos = new BlockPos(currentX, currentY, currentZ);
            BlockState state = world.getBlockState(pos);
            BlockEntity be = world.getBlockEntity(pos);
            
            snapshotList.add(new BlockSnapshot(pos, state, be != null ? be.createNbt() : null));

            if (!state.isAir()) {
                if (animation) {
                    world.breakBlock(pos, false);
                } else {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                }
            }
            processed++;

            currentX++;
            if (currentX > max.getX()) {
                currentX = min.getX();
                currentZ++;
                if (currentZ > max.getZ()) {
                    currentZ = min.getZ();
                    currentY++;
                }
            }
        }
        return false;
    }
}