package dev.promptcraft.network;

import dev.promptcraft.PromptCraftMod;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftEnv;
import dev.promptcraft.selection.PlayerSelection;
import dev.promptcraft.session.GenerationSession;
import dev.promptcraft.session.PendingPrompt;
import dev.promptcraft.session.PromptSessionManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class PromptCraftNetworking {
    public static final Identifier SELECTION_SYNC_PACKET = new Identifier(PromptCraftMod.MOD_ID, "selection_sync");
    public static final Identifier OPEN_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "open_gui");
    public static final Identifier SAVE_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "save_gui");
    public static final Identifier REQUEST_OPEN_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "request_open_gui");
    public static final Identifier GUI_ACTION_PACKET = new Identifier(PromptCraftMod.MOD_ID, "gui_action");
    public static final Identifier AI_STREAM_PACKET = new Identifier(PromptCraftMod.MOD_ID, "ai_stream");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_GUI_PACKET, (server, player, handler, buf, responseSender) -> {
            String provider = buf.readString();

            int keyCount = buf.readInt();
            Map<String, String> apiKeys = new HashMap<>();
            for (int i = 0; i < keyCount; i++) {
                apiKeys.put(buf.readString(), buf.readString());
            }

            String model = buf.readString();
            boolean showPreview = buf.readBoolean();
            String language = buf.readString();
            String themeColor = buf.readString();
            boolean thickOutline = buf.readBoolean();
            float fillOpacity = buf.readFloat();
            boolean outlineThroughBlocks = buf.readBoolean();

            boolean selectionLimitEnabled = buf.readBoolean();
            int maxSelectionWidth = buf.readInt();
            int maxSelectionHeight = buf.readInt();
            int maxSelectionDepth = buf.readInt();

            server.execute(() -> {
                if (!dev.promptcraft.PromptCraftCommands.hasAccess(player)) return;

                PromptCraftEnv.saveApiKeys(apiKeys);
                PromptCraftConfig config = PromptCraftConfigManager.get();
                config.provider = provider;
                config.model = model;
                config.showSelectionPreview = showPreview;
                config.language = language;
                config.themeColor = themeColor;
                config.thickSelectionOutline = thickOutline;
                config.selectionFillOpacity = Math.max(0.0f, Math.min(1.0f, fillOpacity));
                config.selectionOutlineThroughBlocks = outlineThroughBlocks;

                config.selectionLimitEnabled = selectionLimitEnabled;
                config.maxSelectionWidth = Math.max(1, maxSelectionWidth);
                config.maxSelectionHeight = Math.max(1, maxSelectionHeight);
                config.maxSelectionDepth = Math.max(1, maxSelectionDepth);

                PromptCraftConfigManager.save();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(REQUEST_OPEN_GUI_PACKET, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                if (dev.promptcraft.PromptCraftCommands.hasAccess(player)) {
                    openSettingsGui(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GUI_ACTION_PACKET, (server, player, handler, buf, responseSender) -> {
            String action = buf.readString();
            String promptText = buf.readString();

            server.execute(() -> {
                if (!dev.promptcraft.PromptCraftCommands.hasAccess(player)) return;
                if (!player.isCreative()) {
                    player.sendMessage(Text.literal("You must be in creative mode.").formatted(Formatting.RED), false);
                    return;
                }

                if ("generate".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal("A generation is already in progress.").formatted(Formatting.RED), false);
                        return;
                    }

                    PlayerSelection selection = dev.promptcraft.selection.SelectionManager.get(player);
                    if (!selection.isComplete()) {
                        player.sendMessage(Text.literal("You must select an area first!").formatted(Formatting.RED), false);
                        return;
                    }
                    if (!dev.promptcraft.selection.SelectionManager.isWithinLimit(selection)) {
                        player.sendMessage(Text.literal("Selection is too large! Check /pmenu -> Limits.").formatted(Formatting.RED), false);
                        return;
                    }

                    PendingPrompt prompt = new PendingPrompt(promptText, selection.getMin(), selection.getMax(), selection.getWidth(), selection.getHeight(), selection.getDepth());
                    PromptSessionManager.setLast(player, prompt);
                    executeBuildProcess(player, prompt);

                } else if ("edit".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal("A generation is already in progress.").formatted(Formatting.RED), false);
                        return;
                    }

                    var lastOpt = PromptSessionManager.getLast(player);
                    if (lastOpt.isEmpty()) {
                        player.sendMessage(Text.literal("No previous prompt to edit!").formatted(Formatting.RED), false);
                        return;
                    }
                    PendingPrompt last = lastOpt.get();
                    String combined = "Original request: " + last.getPrompt() + ". User edit request: " + promptText + ". Please modify the design accordingly.";
                    PendingPrompt newPrompt = new PendingPrompt(combined, last.getSelectionMin(), last.getSelectionMax(), last.getWidth(), last.getHeight(), last.getDepth());
                    PromptSessionManager.setLast(player, newPrompt);
                    executeBuildProcess(player, newPrompt);

                } else if ("undo".equals(action) || "back".equals(action)) {
                    var activeSession = PromptSessionManager.getActiveGeneration(player);
                    if (activeSession.isPresent() && !activeSession.get().isCancelled()) {
                        cancelGeneration(player, activeSession.get());
                        return;
                    }

                    if (dev.promptcraft.structure.HistoryManager.undo(player)) {
                        player.sendMessage(Text.literal("Step back successful.").formatted(Formatting.GREEN), false);
                    } else {
                        player.sendMessage(Text.literal("Nothing to undo.").formatted(Formatting.RED), false);
                    }
                } else if ("next".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal("Cannot redo while generating.").formatted(Formatting.RED), false);
                        return;
                    }

                    if (dev.promptcraft.structure.HistoryManager.redo(player)) {
                        player.sendMessage(Text.literal("Step forward successful.").formatted(Formatting.GREEN), false);
                    } else {
                        player.sendMessage(Text.literal("Nothing to redo.").formatted(Formatting.RED), false);
                    }
                }
            });
        });
    }

    public static void syncSelection(ServerPlayerEntity player, PlayerSelection selection) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(selection.hasFirst());
        if (selection.hasFirst()) buf.writeBlockPos(selection.getFirst());
        buf.writeBoolean(selection.hasSecond());
        if (selection.hasSecond()) buf.writeBlockPos(selection.getSecond());
        ServerPlayNetworking.send(player, SELECTION_SYNC_PACKET, buf);
    }

    public static void openSettingsGui(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        PromptCraftConfig config = PromptCraftConfigManager.get();

        buf.writeString(config.provider);

        Map<String, String> keys = PromptCraftEnv.getAllApiKeys();
        buf.writeInt(keys.size());
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            buf.writeString(entry.getKey());
            buf.writeString(entry.getValue());
        }

        buf.writeString(config.model);
        buf.writeBoolean(config.showSelectionPreview);
        buf.writeString(config.language);
        buf.writeString(config.themeColor);
        buf.writeBoolean(config.thickSelectionOutline);
        buf.writeFloat(config.selectionFillOpacity);
        buf.writeBoolean(config.selectionOutlineThroughBlocks);

        buf.writeBoolean(config.selectionLimitEnabled);
        buf.writeInt(config.maxSelectionWidth);
        buf.writeInt(config.maxSelectionHeight);
        buf.writeInt(config.maxSelectionDepth);

        ServerPlayNetworking.send(player, OPEN_GUI_PACKET, buf);
    }

    /**
     * Отправляет клиенту событие о состоянии текущего потока генерации ИИ.
     * eventType: "start", "reasoning", "done", "error", "cancelled".
     */
    public static void sendAiStreamEvent(ServerPlayerEntity player, String eventType, String payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(eventType);
        buf.writeString(payload == null ? "" : payload);
        ServerPlayNetworking.send(player, AI_STREAM_PACKET, buf);
    }

    private static void executeBuildProcess(ServerPlayerEntity player, PendingPrompt prompt) {
        GenerationSession session = PromptSessionManager.startGeneration(player);

        player.sendMessage(Text.literal("Preparing area...").formatted(Formatting.YELLOW), false);
        dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.DestructionTask(player, prompt.getSelectionMin(), prompt.getSelectionMax(), session, () -> {
            if (session.isCancelled()) return;

            player.sendMessage(Text.literal("Area cleared. Contacting AI...").formatted(Formatting.AQUA), false);
            dev.promptcraft.ai.AiClient.requestBuild(player, prompt.getPrompt(), prompt.getWidth(), prompt.getHeight(), prompt.getDepth(), session)
                    .thenAccept(structure -> {
                        if (session.isCancelled()) return;

                        if (structure != null && player.getServer() != null) {
                            player.getServer().execute(() -> {
                                if (session.isCancelled()) return;
                                player.sendMessage(Text.literal("AI response received! Building...").formatted(Formatting.GREEN), false);
                                dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.BuildTask(player, prompt.getSelectionMin(), structure, session));
                            });
                        } else {
                            PromptSessionManager.clearGeneration(player);
                        }
                    });
        }));
    }

    /** Отменяет генерацию в процессе и откатывает мир к состоянию до её начала. */
    private static void cancelGeneration(ServerPlayerEntity player, GenerationSession session) {
        session.cancel();
        session.abortHttpRequest();

        if (session.isDestructionComplete()) {
            dev.promptcraft.structure.HistoryManager.undo(player);
        }

        PromptSessionManager.clearGeneration(player);
        sendAiStreamEvent(player, "cancelled", "");
        player.sendMessage(Text.literal("Generation cancelled.").formatted(Formatting.YELLOW), false);
    }
}
