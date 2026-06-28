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
}