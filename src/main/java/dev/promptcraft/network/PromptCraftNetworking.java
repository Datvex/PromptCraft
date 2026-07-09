package dev.promptcraft.network;

import dev.promptcraft.PromptCraftMod;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftEnv;
import dev.promptcraft.config.PromptCraftLang;
import dev.promptcraft.selection.PlayerSelection;
import dev.promptcraft.session.GenerationSession;
import dev.promptcraft.session.PendingPrompt;
import dev.promptcraft.session.PromptSessionManager;
import dev.promptcraft.structure.PromptCraftStructure;
import dev.promptcraft.structure.StructureRotationUtil;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class PromptCraftNetworking {
    public static final Identifier SELECTION_SYNC_PACKET = new Identifier(PromptCraftMod.MOD_ID, "selection_sync");
    public static final Identifier OPEN_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "open_gui");
    public static final Identifier SAVE_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "save_gui");
    public static final Identifier REQUEST_OPEN_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "request_open_gui");
    public static final Identifier GUI_ACTION_PACKET = new Identifier(PromptCraftMod.MOD_ID, "gui_action");
    public static final Identifier AI_STREAM_PACKET = new Identifier(PromptCraftMod.MOD_ID, "ai_stream");
    public static final Identifier START_FREE_PLACEMENT_PACKET = new Identifier(PromptCraftMod.MOD_ID, "start_free_placement");
    public static final Identifier CANCEL_FREE_PLACEMENT_PACKET = new Identifier(PromptCraftMod.MOD_ID, "cancel_free_placement");
    public static final Identifier CONFIRM_PLACEMENT_PACKET = new Identifier(PromptCraftMod.MOD_ID, "confirm_placement");
    public static final Identifier BUILD_PROGRESS_PACKET = new Identifier(PromptCraftMod.MOD_ID, "build_progress");

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
                boolean isGenOrEdit = "generate".equals(action) || "edit".equals(action);

                if (!dev.promptcraft.PromptCraftCommands.hasAccess(player)) {
                    if (isGenOrEdit) sendAiStreamEvent(player, "cancelled", "");
                    return;
                }
                if (!player.isCreative()) {
                    player.sendMessage(Text.literal(PromptCraftLang.t("You must be in creative mode.", "Нужно находиться в творческом режиме.")).formatted(Formatting.RED), false);
                    if (isGenOrEdit) sendAiStreamEvent(player, "cancelled", "");
                    return;
                }

                if ("generate".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("A generation is already in progress.", "Генерация уже выполняется.")).formatted(Formatting.RED), false);
                        return;
                    }

                    PromptCraftConfig config = PromptCraftConfigManager.get();
                    boolean isFreeMode = "free".equals(config.generationMode);

                    if (isFreeMode) {
                        // Реальный размер зоны здесь ещё неизвестен - его определит сам ИИ.
                        // Плейсхолдер (0,0,0) будет заменён на настоящие координаты после
                        // подтверждения размещения (см. CONFIRM_PLACEMENT_PACKET ниже).
                        PendingPrompt prompt = new PendingPrompt(promptText, BlockPos.ORIGIN, BlockPos.ORIGIN, 0, 0, 0);
                        PromptSessionManager.setLast(player, prompt);
                        executeFreeBuildProcess(player, prompt);
                    } else {
                        PlayerSelection selection = dev.promptcraft.selection.SelectionManager.get(player);
                        if (!selection.isComplete()) {
                            player.sendMessage(Text.literal(PromptCraftLang.t("You must select an area first!", "Сначала нужно выделить область!")).formatted(Formatting.RED), false);
                            sendAiStreamEvent(player, "cancelled", "");
                            return;
                        }

                        PendingPrompt prompt = new PendingPrompt(promptText, selection.getMin(), selection.getMax(), selection.getWidth(), selection.getHeight(), selection.getDepth());
                        PromptSessionManager.setLast(player, prompt);
                        executeBuildProcess(player, prompt);
                    }

                } else if ("edit".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("A generation is already in progress.", "Генерация уже выполняется.")).formatted(Formatting.RED), false);
                        sendAiStreamEvent(player, "cancelled", "");
                        return;
                    }

                    var lastOpt = PromptSessionManager.getLast(player);
                    if (lastOpt.isEmpty()) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("No previous prompt to edit!", "Нет предыдущего запроса для правки!")).formatted(Formatting.RED), false);
                        sendAiStreamEvent(player, "cancelled", "");
                        return;
                    }
                    PendingPrompt last = lastOpt.get();
                    String combined = "Original request: " + last.getPrompt() + ". User edit request: " + promptText + ". Please modify the design accordingly.";
                    PendingPrompt newPrompt = new PendingPrompt(combined, last.getSelectionMin(), last.getSelectionMax(), last.getWidth(), last.getHeight(), last.getDepth());
                    PromptSessionManager.setLast(player, newPrompt);
                    executeBuildProcess(player, newPrompt);

                } else if ("undo".equals(action)) {
                    var activeSession = PromptSessionManager.getActiveGeneration(player);
                    if (activeSession.isPresent() && !activeSession.get().isCancelled()) {
                        cancelGeneration(player, activeSession.get());
                        return;
                    }

                    if (dev.promptcraft.structure.HistoryManager.undo(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Step back successful.", "Шаг назад выполнен.")).formatted(Formatting.GREEN), false);
                    } else {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Nothing to undo.", "Нечего отменять.")).formatted(Formatting.RED), false);
                    }
                } else if ("back".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Cannot go back while generating.", "Нельзя вернуться назад во время генерации.")).formatted(Formatting.RED), false);
                        return;
                    }

                    if (dev.promptcraft.structure.HistoryManager.undo(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Step back successful.", "Шаг назад выполнен.")).formatted(Formatting.GREEN), false);
                    } else {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Nothing to undo.", "Нечего отменять.")).formatted(Formatting.RED), false);
                    }
                } else if ("next".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Cannot redo while generating.", "Нельзя вернуть во время генерации.")).formatted(Formatting.RED), false);
                        return;
                    }

                    if (dev.promptcraft.structure.HistoryManager.redo(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Step forward successful.", "Шаг вперёд выполнен.")).formatted(Formatting.GREEN), false);
                    } else {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Nothing to redo.", "Нечего возвращать.")).formatted(Formatting.RED), false);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CONFIRM_PLACEMENT_PACKET, (server, player, handler, buf, responseSender) -> {
            BlockPos anchor = buf.readBlockPos();
            int rotationSteps = buf.readInt();

            server.execute(() -> {
                if (!dev.promptcraft.PromptCraftCommands.hasAccess(player) || !player.isCreative()) return;

                var sessionOpt = PromptSessionManager.getActiveGeneration(player);
                if (sessionOpt.isEmpty() || !sessionOpt.get().isGhostPending()) return;

                GenerationSession session = sessionOpt.get();
                PromptCraftStructure structure = session.getPendingStructure();

                if (structure == null) {
                    PromptSessionManager.clearGeneration(player);
                    return;
                }

                PromptCraftStructure rotated = StructureRotationUtil.rotate(structure, rotationSteps);
                PromptCraftStructure.Bounds bounds = rotated.computeBounds();

                BlockPos min = anchor.add(bounds.minX(), bounds.minY(), bounds.minZ());
                BlockPos max = anchor.add(bounds.maxX(), bounds.maxY(), bounds.maxZ());

                session.setGhostPending(false);
                session.setPendingStructure(null);

                PromptSessionManager.getLast(player).ifPresent(last -> {
                    PendingPrompt updated = new PendingPrompt(
                            last.getPrompt(), min, max, bounds.width(), bounds.height(), bounds.depth()
                    );
                    PromptSessionManager.setLast(player, updated);
                });

                player.sendMessage(Text.literal(PromptCraftLang.t("Placing structure...", "Размещение структуры...")).formatted(Formatting.YELLOW), false);

                dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.DestructionTask(player, min, max, session, () -> {
                    if (session.isCancelled()) return;
                    player.sendMessage(Text.literal(PromptCraftLang.t("Area cleared. Building...", "Область очищена. Строим...")).formatted(Formatting.GREEN), false);
                    dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.BuildTask(player, anchor, rotated, session));
                }));
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

    public static void sendAiStreamEvent(ServerPlayerEntity player, String eventType, String payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(eventType);
        buf.writeString(payload == null ? "" : payload);
        ServerPlayNetworking.send(player, AI_STREAM_PACKET, buf);
    }

    public static void sendBuildProgress(ServerPlayerEntity player, int percent, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(percent);
        buf.writeBoolean(active);
        ServerPlayNetworking.send(player, BUILD_PROGRESS_PACKET, buf);
    }

    private static void executeBuildProcess(ServerPlayerEntity player, PendingPrompt prompt) {
        GenerationSession session = PromptSessionManager.startGeneration(player);

        player.sendMessage(Text.literal(PromptCraftLang.t("Preparing area...", "Подготовка области...")).formatted(Formatting.YELLOW), false);
        dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.DestructionTask(player, prompt.getSelectionMin(), prompt.getSelectionMax(), session, () -> {
            if (session.isCancelled()) return;

            player.sendMessage(Text.literal(PromptCraftLang.t("Area cleared. Contacting AI...", "Область очищена. Связь с ИИ...")).formatted(Formatting.AQUA), false);
            dev.promptcraft.ai.AiClient.requestBuild(player, prompt.getPrompt(), prompt.getWidth(), prompt.getHeight(), prompt.getDepth(), session)
                    .thenAccept(structure -> {
                        if (session.isCancelled()) return;

                        if (structure != null && player.getServer() != null) {
                            player.getServer().execute(() -> {
                                if (session.isCancelled()) return;
                                player.sendMessage(Text.literal(PromptCraftLang.t("AI response received! Building...", "Ответ ИИ получен! Строим...")).formatted(Formatting.GREEN), false);
                                dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.BuildTask(player, prompt.getSelectionMin(), structure, session));
                            });
                        } else {
                            PromptSessionManager.clearGeneration(player);
                        }
                    });
        }));
    }

    private static void executeFreeBuildProcess(ServerPlayerEntity player, PendingPrompt prompt) {
        GenerationSession session = PromptSessionManager.startGeneration(player);
        PromptCraftConfig config = PromptCraftConfigManager.get();

        player.sendMessage(Text.literal(PromptCraftLang.t("Contacting AI for free placement...", "Связь с ИИ для свободного размещения...")).formatted(Formatting.AQUA), false);

        dev.promptcraft.ai.AiClient.requestFreeBuild(
                player,
                prompt.getPrompt(),
                config.selectionLimitEnabled,
                config.maxSelectionWidth,
                config.maxSelectionHeight,
                config.maxSelectionDepth,
                session
        ).thenAccept(structure -> {
            if (session.isCancelled()) return;

            if (structure != null && structure.operations != null && !structure.operations.isEmpty() && player.getServer() != null) {
                player.getServer().execute(() -> {
                    if (session.isCancelled()) return;

                    session.setPendingStructure(structure);
                    session.setGhostPending(true);

                    player.sendMessage(Text.literal(PromptCraftLang.t("AI response received! Position the preview and confirm placement.", "Ответ ИИ получен! Наведите предпросмотр и подтвердите размещение.")).formatted(Formatting.GREEN), false);

                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeString(new com.google.gson.Gson().toJson(structure));
                    ServerPlayNetworking.send(player, START_FREE_PLACEMENT_PACKET, buf);

                    // Сессия и AiStreamState.generating намеренно НЕ сбрасываются здесь: пока призрак
                    // не размещён, вкладка Create должна оставаться заблокированной, а Undo должен
                    // работать как "Cancel Generation" (см. cancelGeneration). Событие "done" отправит
                    // сам BuildTask, когда постройка будет реально завершена после подтверждения.
                });
            } else {
                if (player.getServer() != null) {
                    player.getServer().execute(() -> {
                        if (!session.isCancelled()) {
                            player.sendMessage(Text.literal(PromptCraftLang.t("AI returned an empty structure.", "ИИ вернул пустую структуру.")).formatted(Formatting.RED), false);
                        }
                        PromptSessionManager.clearGeneration(player);
                    });
                } else {
                    PromptSessionManager.clearGeneration(player);
                }
            }
        });
    }

    private static void cancelGeneration(ServerPlayerEntity player, GenerationSession session) {
        session.cancel();
        session.abortHttpRequest();

        if (session.isGhostPending()) {
            session.setGhostPending(false);
            session.setPendingStructure(null);
            ServerPlayNetworking.send(player, CANCEL_FREE_PLACEMENT_PACKET, PacketByteBufs.create());
        } else if (session.isDestructionComplete()) {
            dev.promptcraft.structure.HistoryManager.undo(player);
        }

        PromptSessionManager.clearGeneration(player);
        sendAiStreamEvent(player, "cancelled", "");
        player.sendMessage(Text.literal(PromptCraftLang.t("Generation cancelled.", "Генерация отменена.")).formatted(Formatting.YELLOW), false);
    }
}