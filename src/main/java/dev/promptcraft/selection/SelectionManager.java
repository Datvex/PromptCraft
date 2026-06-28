package dev.promptcraft.selection;

import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.network.PromptCraftNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SelectionManager {
    private static final Map<UUID, PlayerSelection> SELECTIONS = new ConcurrentHashMap<>();

    private SelectionManager() {
    }

    public static PlayerSelection get(ServerPlayerEntity player) {
        return SELECTIONS.computeIfAbsent(player.getUuid(), uuid -> new PlayerSelection());
    }

    public static void clear(ServerPlayerEntity player) {
        SELECTIONS.remove(player.getUuid());
        PromptCraftNetworking.syncSelection(player, new PlayerSelection());
    }

    public static boolean isWithinLimit(PlayerSelection selection) {
        PromptCraftConfig config = PromptCraftConfigManager.get();

        if (!config.selectionLimitEnabled || !selection.isComplete()) {
            return true;
        }

        return selection.getWidth() <= config.maxSelectionWidth
                && selection.getHeight() <= config.maxSelectionHeight
                && selection.getDepth() <= config.maxSelectionDepth;
    }
}
