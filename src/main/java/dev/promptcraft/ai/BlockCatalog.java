package dev.promptcraft.ai;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.TreeSet;

public final class BlockCatalog {

    private static final Set<String> BLACKLIST = Set.of(
            "barrier",
            "structure_void",
            "structure_block",
            "jigsaw",
            "command_block",
            "chain_command_block",
            "repeating_command_block",
            "moving_piston",
            "end_portal",
            "end_gateway",
            "nether_portal",
            "light"
    );

    private static volatile String cachedList;

    private BlockCatalog() {
    }

    public static String getBlockListForPrompt() {
        String local = cachedList;
        if (local != null) return local;

        synchronized (BlockCatalog.class) {
            if (cachedList != null) return cachedList;

            Set<String> names = new TreeSet<>();
            for (Identifier id : Registries.BLOCK.getIds()) {
                if (!"minecraft".equals(id.getNamespace())) continue;
                String path = id.getPath();
                if (BLACKLIST.contains(path)) continue;
                names.add(path);
            }

            cachedList = String.join(",", names);
            return cachedList;
        }
    }
}
