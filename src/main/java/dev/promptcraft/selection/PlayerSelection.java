package dev.promptcraft.selection;

import net.minecraft.util.math.BlockPos;

public class PlayerSelection {
    private BlockPos first;
    private BlockPos second;

    public BlockPos getFirst() {
        return first;
    }

    public BlockPos getSecond() {
        return second;
    }

    public boolean hasFirst() {
        return first != null;
    }

    public boolean hasSecond() {
        return second != null;
    }

    public boolean isComplete() {
        return first != null && second != null;
    }

    public void setFirst(BlockPos first) {
        this.first = first;
    }

    public void setSecond(BlockPos second) {
        this.second = second;
    }

    public void clear() {
        this.first = null;
        this.second = null;
    }

    public BlockPos getMin() {
        if (!isComplete()) {
            return null;
        }

        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ())
        );
    }

    public BlockPos getMax() {
        if (!isComplete()) {
            return null;
        }

        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ())
        );
    }

    public int getWidth() {
        if (!isComplete()) {
            return 0;
        }

        return getMax().getX() - getMin().getX() + 1;
    }

    public int getHeight() {
        if (!isComplete()) {
            return 0;
        }

        return getMax().getY() - getMin().getY() + 1;
    }

    public int getDepth() {
        if (!isComplete()) {
            return 0;
        }

        return getMax().getZ() - getMin().getZ() + 1;
    }
}
