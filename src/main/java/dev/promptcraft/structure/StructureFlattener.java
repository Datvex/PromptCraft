package dev.promptcraft.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StructureFlattener {
    private StructureFlattener() {
    }

    public static Map<BlockPos, BlockState> flatten(PromptCraftStructure structure) {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        if (structure == null || structure.operations == null) return result;

        for (PromptCraftStructure.Operation op : structure.operations) {
            var stateOpt = StructureBlockCodec.parse(op.block);
            if (stateOpt.isEmpty()) continue;
            BlockState state = stateOpt.get();

            if ("place".equals(op.type) && op.pos != null && op.pos.length == 3) {
                result.put(new BlockPos(op.pos[0], op.pos[1], op.pos[2]), state);
            } else if (("fill".equals(op.type) || "hollow_box".equals(op.type))
                    && op.from != null && op.to != null && op.from.length == 3 && op.to.length == 3) {
                int minX = Math.min(op.from[0], op.to[0]);
                int minY = Math.min(op.from[1], op.to[1]);
                int minZ = Math.min(op.from[2], op.to[2]);
                int maxX = Math.max(op.from[0], op.to[0]);
                int maxY = Math.max(op.from[1], op.to[1]);
                int maxZ = Math.max(op.from[2], op.to[2]);

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            if ("hollow_box".equals(op.type) && x > minX && x < maxX && y > minY && y < maxY && z > minZ && z < maxZ)
                                continue;
                            result.put(new BlockPos(x, y, z), state);
                        }
                    }
                }
            }
        }

        return result;
    }
}