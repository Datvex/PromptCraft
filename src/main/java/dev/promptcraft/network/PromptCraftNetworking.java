package dev.promptcraft.network;

import dev.promptcraft.PromptCraftMod;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftEnv;
import dev.promptcraft.selection.PlayerSelection;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class PromptCraftNetworking {
    public static final Identifier SELECTION_SYNC_PACKET = new Identifier(PromptCraftMod.MOD_ID, "selection_sync");
    public static final Identifier OPEN_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "open_gui");
    public static final Identifier SAVE_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "save_gui");
    public static final Identifier REQUEST_OPEN_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "request_open_gui");
    public static final Identifier GUI_ACTION_PACKET = new Identifier(PromptCraftMod.MOD_ID, "gui_action");

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
                    player.sendMessage(net.minecraft.text.Text.literal("You must be in creative mode.").formatted(net.minecraft.util.Formatting.RED), false);
                    return;
                }

                if ("generate".equals(action)) {
                    dev.promptcraft.selection.PlayerSelection selection = dev.promptcraft.selection.SelectionManager.get(player);
                    if (!selection.isComplete()) {
                        player.sendMessage(net.minecraft.text.Text.literal("You must select an area first!").formatted(net.minecraft.util.Formatting.RED), false);
                        return;
                    }
                    dev.promptcraft.session.PendingPrompt prompt = new dev.promptcraft.session.PendingPrompt(promptText, selection.getMin(), selection.getMax(), selection.getWidth(), selection.getHeight(), selection.getDepth());
                    dev.promptcraft.session.PromptSessionManager.setLast(player, prompt);
                    executeBuildProcess(player, prompt);

                } else if ("edit".equals(action)) {
                    var lastOpt = dev.promptcraft.session.PromptSessionManager.getLast(player);
                    if (lastOpt.isEmpty()) {
                        player.sendMessage(net.minecraft.text.Text.literal("No previous prompt to edit!").formatted(net.minecraft.util.Formatting.RED), false);
                        return;
                    }
                    dev.promptcraft.session.PendingPrompt last = lastOpt.get();
                    String combined = "Original request: " + last.getPrompt() + ". User edit request: " + promptText + ". Please modify the design accordingly.";
                    dev.promptcraft.session.PendingPrompt newPrompt = new dev.promptcraft.session.PendingPrompt(combined, last.getSelectionMin(), last.getSelectionMax(), last.getWidth(), last.getHeight(), last.getDepth());
                    dev.promptcraft.session.PromptSessionManager.setLast(player, newPrompt);
                    executeBuildProcess(player, newPrompt);

                } else if ("undo".equals(action) || "back".equals(action)) {
                    if (dev.promptcraft.structure.HistoryManager.undo(player)) {
                        player.sendMessage(net.minecraft.text.Text.literal("Step back successful.").formatted(net.minecraft.util.Formatting.GREEN), false);
                    } else {
                        player.sendMessage(net.minecraft.text.Text.literal("Nothing to undo.").formatted(net.minecraft.util.Formatting.RED), false);
                    }
                } else if ("next".equals(action)) {
                    if (dev.promptcraft.structure.HistoryManager.redo(player)) {
                        player.sendMessage(net.minecraft.text.Text.literal("Step forward successful.").formatted(net.minecraft.util.Formatting.GREEN), false);
                    } else {
                        player.sendMessage(net.minecraft.text.Text.literal("Nothing to redo.").formatted(net.minecraft.util.Formatting.RED), false);
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

    private static void executeBuildProcess(ServerPlayerEntity player, dev.promptcraft.session.PendingPrompt prompt) {
        player.sendMessage(net.minecraft.text.Text.literal("Preparing area...").formatted(net.minecraft.util.Formatting.YELLOW), false);
        dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.DestructionTask(player, prompt.getSelectionMin(), prompt.getSelectionMax(), () -> {
            player.sendMessage(net.minecraft.text.Text.literal("Area cleared. Contacting AI...").formatted(net.minecraft.util.Formatting.AQUA), false);
            dev.promptcraft.ai.AiClient.requestBuild(player, prompt.getPrompt(), prompt.getWidth(), prompt.getHeight(), prompt.getDepth())
                .thenAccept(structure -> {
                    if (structure != null && player.getServer() != null) {
                        player.getServer().execute(() -> {
                            player.sendMessage(net.minecraft.text.Text.literal("AI response received! Building...").formatted(net.minecraft.util.Formatting.GREEN), false);
                            dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.BuildTask(player, prompt.getSelectionMin(), structure));
                        });
                    }
                });
        }));
    }
}