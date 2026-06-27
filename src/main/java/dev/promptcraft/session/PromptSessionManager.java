package dev.promptcraft.session;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PromptSessionManager {
    private static final Map<UUID, PendingPrompt> PENDING_PROMPTS = new ConcurrentHashMap<>();

    private PromptSessionManager() {
    }

    public static void setPending(ServerPlayerEntity player, PendingPrompt prompt) {
        PENDING_PROMPTS.put(player.getUuid(), prompt);
    }

    public static Optional<PendingPrompt> getPending(ServerPlayerEntity player) {
        return Optional.ofNullable(PENDING_PROMPTS.get(player.getUuid()));
    }

    public static void clearPending(ServerPlayerEntity player) {
        PENDING_PROMPTS.remove(player.getUuid());
    }
}
