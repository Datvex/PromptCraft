package dev.promptcraft.session;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PromptSessionManager {
    private static final Map<UUID, PendingPrompt> LAST_PROMPTS = new ConcurrentHashMap<>();
    private static final Map<UUID, GenerationSession> ACTIVE_GENERATIONS = new ConcurrentHashMap<>();

    private PromptSessionManager() {}

    public static void setLast(ServerPlayerEntity player, PendingPrompt prompt) {
        LAST_PROMPTS.put(player.getUuid(), prompt);
    }

    public static Optional<PendingPrompt> getLast(ServerPlayerEntity player) {
        return Optional.ofNullable(LAST_PROMPTS.get(player.getUuid()));
    }

    public static GenerationSession startGeneration(ServerPlayerEntity player) {
        GenerationSession session = new GenerationSession();
        ACTIVE_GENERATIONS.put(player.getUuid(), session);
        return session;
    }

    public static Optional<GenerationSession> getActiveGeneration(ServerPlayerEntity player) {
        return Optional.ofNullable(ACTIVE_GENERATIONS.get(player.getUuid()));
    }

    public static boolean isGenerating(ServerPlayerEntity player) {
        GenerationSession session = ACTIVE_GENERATIONS.get(player.getUuid());
        return session != null && !session.isCancelled();
    }

    public static void clearGeneration(ServerPlayerEntity player) {
        ACTIVE_GENERATIONS.remove(player.getUuid());
    }
}
