package dev.promptcraft.task;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

public final class BlockPlacementUtil {

    public static final int SAFE_FORCE_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    public static final int NORMAL_FLAGS = Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS;

    private BlockPlacementUtil() {
    }

    public static boolean isFragileMultipart(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            String name = prop.getName();
            if (name.equals("half") || name.equals("part")) {
                return true;
            }
        }
        return false;
    }

    public static int flagsFor(BlockState state) {
        return isFragileMultipart(state) ? SAFE_FORCE_FLAGS : NORMAL_FLAGS;
    }
}
