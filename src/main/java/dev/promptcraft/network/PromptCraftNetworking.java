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

public class PromptCraftNetworking {
    public static final Identifier SELECTION_SYNC_PACKET = new Identifier(PromptCraftMod.MOD_ID, "selection_sync");
    public static final Identifier OPEN_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "open_gui");
    public static final Identifier SAVE_GUI_PACKET = new Identifier(PromptCraftMod.MOD_ID, "save_gui");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_GUI_PACKET, (server, player, handler, buf, responseSender) -> {
            String apiKey = buf.readString();
            String model = buf.readString();
            boolean showPreview = buf.readBoolean();

            server.execute(() -> {
                PromptCraftEnv.saveNvidiaApiKey(apiKey);
                PromptCraftConfig config = PromptCraftConfigManager.get();
                config.model = model;
                config.showSelectionPreview = showPreview;
                PromptCraftConfigManager.save();
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
        buf.writeString(PromptCraftEnv.getNvidiaApiKey());
        buf.writeString(PromptCraftConfigManager.get().model);
        buf.writeBoolean(PromptCraftConfigManager.get().showSelectionPreview);
        ServerPlayNetworking.send(player, OPEN_GUI_PACKET, buf);
    }
}