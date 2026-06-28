package dev.promptcraft.structure;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

public class HistoryManager {
    private static final Map<UUID, Deque<List<BlockSnapshot>>> UNDO_STACKS = new HashMap<>();

    public static void pushUndo(ServerPlayerEntity player, List<BlockSnapshot> snapshots) {
        UNDO_STACKS.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>()).push(snapshots);
    }

    public static boolean undo(ServerPlayerEntity player) {
        Deque<List<BlockSnapshot>> stack = UNDO_STACKS.get(player.getUuid());
        if (stack == null || stack.isEmpty()) return false;

        List<BlockSnapshot> snapshots = stack.pop();
        ServerWorld world = (ServerWorld) player.getWorld();

        for (BlockSnapshot snap : snapshots) {
            world.setBlockState(snap.pos(), snap.state(), 3);
            if (snap.nbt() != null) {
                BlockEntity be = world.getBlockEntity(snap.pos());
                if (be != null) {
                    be.readNbt(snap.nbt());
                }
            }
        }
        return true;
    }
}