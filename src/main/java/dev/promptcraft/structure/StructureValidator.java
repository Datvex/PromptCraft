package dev.promptcraft.structure;

import java.util.ArrayList;

public final class StructureValidator {

    private StructureValidator() {}

    /** true, если КАЖДАЯ координата структуры внутри [0,w-1]x[0,h-1]x[0,d-1]. */
    public static boolean isWithinBounds(PromptCraftStructure s, int w, int h, int d) {
        if (s == null || s.operations == null) return true;
        for (PromptCraftStructure.Operation op : s.operations) {
            if (op.pos != null && op.pos.length == 3 && out(op.pos, w, h, d)) return false;
            if (op.from != null && op.from.length == 3 && out(op.from, w, h, d)) return false;
            if (op.to != null && op.to.length == 3 && out(op.to, w, h, d)) return false;
        }
        return true;
    }

    private static boolean out(int[] c, int w, int h, int d) {
        return c[0] < 0 || c[0] > w - 1
            || c[1] < 0 || c[1] > h - 1
            || c[2] < 0 || c[2] > d - 1;
    }

    /**
     * Уточняющий промпт для ИИ: не обрезать, а переделать структуру под коробку.
     */
    public static String buildCorrectionNote(PromptCraftStructure s, int w, int h, int d) {
        PromptCraftStructure.Bounds b = s.computeBounds();
        return "\n\nCRITICAL CORRECTION - YOUR PREVIOUS OUTPUT DID NOT FIT THE BUILD AREA. "
            + "The allowed bounding box is width=" + w + ", height=" + h + ", depth=" + d
            + ", so EVERY coordinate MUST satisfy 0<=x<=" + (w - 1)
            + ", 0<=y<=" + (h - 1) + ", 0<=z<=" + (d - 1) + ". "
            + "Your previous structure occupied x[" + b.minX() + ".." + b.maxX() + "], "
            + "y[" + b.minY() + ".." + b.maxY() + "], z[" + b.minZ() + ".." + b.maxZ() + "], "
            + "which sticks out of the box. Redesign the ENTIRE structure so it fits COMPLETELY inside. "
            + "Do NOT crop, truncate or cut it off - instead scale down and adjust the proportions so the "
            + "whole design fits with no coordinate out of range, and it stays structurally coherent. "
            + "Re-check every single operation before answering. Output ONLY the corrected JSON.";
    }

    /**
     * Крайняя мера, если ИИ так и не вписался: одиночные блоки вне зоны отбрасываем,
     * объёмные операции обрезаем по границам (полностью вылетевшие по оси - выкидываем).
     */
    public static PromptCraftStructure clamp(PromptCraftStructure s, int w, int h, int d) {
        if (s == null || s.operations == null) return s;
        int[] dims = {w, h, d};
        PromptCraftStructure result = new PromptCraftStructure();
        result.operations = new ArrayList<>();

        for (PromptCraftStructure.Operation op : s.operations) {
            PromptCraftStructure.Operation n = new PromptCraftStructure.Operation();
            n.type = op.type;
            n.block = op.block;

            if ("place".equals(op.type) && op.pos != null && op.pos.length == 3) {
                if (out(op.pos, w, h, d)) continue; // блок не туда -> выбрасываем
                n.pos = op.pos;
                result.operations.add(n);
            } else if (("fill".equals(op.type) || "hollow_box".equals(op.type))
                    && op.from != null && op.to != null && op.from.length == 3 && op.to.length == 3) {
                int[] from = new int[3];
                int[] to = new int[3];
                boolean drop = false;
                for (int a = 0; a < 3; a++) {
                    int lo = Math.min(op.from[a], op.to[a]);
                    int hi = Math.max(op.from[a], op.to[a]);
                    int max = dims[a] - 1;
                    if (hi < 0 || lo > max) { drop = true; break; } // целиком вне зоны по этой оси
                    from[a] = Math.max(0, lo);
                    to[a] = Math.min(max, hi);
                }
                if (drop) continue;
                n.from = from;
                n.to = to;
                result.operations.add(n);
            } else {
                result.operations.add(n); // прочее оставляем как есть
            }
        }
        return result;
    }
}