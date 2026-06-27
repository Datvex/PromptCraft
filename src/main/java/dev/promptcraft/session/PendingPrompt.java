package dev.promptcraft.session;

import net.minecraft.util.math.BlockPos;

public class PendingPrompt {
    private final String prompt;
    private final BlockPos selectionMin;
    private final BlockPos selectionMax;
    private final int width;
    private final int height;
    private final int depth;

    public PendingPrompt(
            String prompt,
            BlockPos selectionMin,
            BlockPos selectionMax,
            int width,
            int height,
            int depth
    ) {
        this.prompt = prompt;
        this.selectionMin = selectionMin;
        this.selectionMax = selectionMax;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public String getPrompt() {
        return prompt;
    }

    public BlockPos getSelectionMin() {
        return selectionMin;
    }

    public BlockPos getSelectionMax() {
        return selectionMax;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }
}
