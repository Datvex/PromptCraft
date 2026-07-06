package dev.promptcraft.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;

import java.util.ArrayList;

public final class StructureRotationUtil {
    private StructureRotationUtil() {
    }

    /**
     * @param steps 0..3, каждый шаг = поворот на 90° по часовой стрелке (вид сверху).
     */
    public static PromptCraftStructure rotate(PromptCraftStructure original, int steps) {
        int normalizedSteps = ((steps % 4) + 4) % 4;

        if (normalizedSteps == 0 || original.operations == null) {
            return original;
        }

        PromptCraftStructure.Bounds bounds = original.computeBounds();
        int width = bounds.width();
        int depth = bounds.depth();

        BlockRotation rotation = switch (normalizedSteps) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            default -> BlockRotation.COUNTERCLOCKWISE_90;
        };

        PromptCraftStructure result = new PromptCraftStructure();
        result.operations = new ArrayList<>();

        for (PromptCraftStructure.Operation op : original.operations) {
            PromptCraftStructure.Operation newOp = new PromptCraftStructure.Operation();
            newOp.type = op.type;
            newOp.block = rotateBlockString(op.block, rotation);

            if (op.pos != null && op.pos.length == 3) {
                newOp.pos = rotateCoord(op.pos, normalizedSteps, width, depth);
            }

            if (op.from != null && op.to != null && op.from.length == 3 && op.to.length == 3) {
                int[] a = rotateCoord(op.from, normalizedSteps, width, depth);
                int[] b = rotateCoord(op.to, normalizedSteps, width, depth);
                newOp.from = new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
                newOp.to = new int[]{Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
            }

            result.operations.add(newOp);
        }

        return result;
    }

    private static int[] rotateCoord(int[] xyz, int steps, int width, int depth) {
        int x = xyz[0];
        int y = xyz[1];
        int z = xyz[2];

        return switch (steps) {
            case 1 -> new int[]{(depth - 1) - z, y, x};
            case 2 -> new int[]{(width - 1) - x, y, (depth - 1) - z};
            case 3 -> new int[]{z, y, (width - 1) - x};
            default -> new int[]{x, y, z};
        };
    }

    private static String rotateBlockString(String encoded, BlockRotation rotation) {
        var stateOpt = StructureBlockCodec.parse(encoded);
        if (stateOpt.isEmpty()) return encoded;

        BlockState rotated = stateOpt.get().rotate(rotation);
        return StructureBlockCodec.encode(rotated);
    }
}