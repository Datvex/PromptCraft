package dev.promptcraft.structure;

import java.util.List;

public class PromptCraftStructure {
    public List<Operation> operations;

    public static class Operation {
        public String type;
        public int[] from;
        public int[] to;
        public int[] pos;
        public String block;
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int width() { return maxX - minX + 1; }
        public int height() { return maxY - minY + 1; }
        public int depth() { return maxZ - minZ + 1; }
    }

    public Bounds computeBounds() {
        if (operations == null || operations.isEmpty()) {
            return new Bounds(0, 0, 0, 0, 0, 0);
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Operation op : operations) {
            if (op.pos != null && op.pos.length == 3) {
                minX = Math.min(minX, op.pos[0]); maxX = Math.max(maxX, op.pos[0]);
                minY = Math.min(minY, op.pos[1]); maxY = Math.max(maxY, op.pos[1]);
                minZ = Math.min(minZ, op.pos[2]); maxZ = Math.max(maxZ, op.pos[2]);
            }
            if (op.from != null && op.to != null && op.from.length == 3 && op.to.length == 3) {
                minX = Math.min(minX, Math.min(op.from[0], op.to[0])); maxX = Math.max(maxX, Math.max(op.from[0], op.to[0]));
                minY = Math.min(minY, Math.min(op.from[1], op.to[1])); maxY = Math.max(maxY, Math.max(op.from[1], op.to[1]));
                minZ = Math.min(minZ, Math.min(op.from[2], op.to[2])); maxZ = Math.max(maxZ, Math.max(op.from[2], op.to[2]));
            }
        }

        if (minX == Integer.MAX_VALUE) {
            return new Bounds(0, 0, 0, 0, 0, 0);
        }

        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}