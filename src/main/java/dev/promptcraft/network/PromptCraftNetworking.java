package dev.promptcraft.network;

import dev.promptcraft.PromptCraftMod;
import dev.promptcraft.selection.PlayerSelection;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class PromptCraftNetworking {
    public static final Identifier SELECTION_SYNC_PACKET = new Identifier(PromptCraftMod.MOD_ID, "selection_sync");

    public static void syncSelection(ServerPlayerEntity player, PlayerSelection selection) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(selection.hasFirst());
        if (selection.hasFirst()) {
            buf.writeBlockPos(selection.getFirst());
        }
        buf.writeBoolean(selection.hasSecond());
        if (selection.hasSecond()) {
            buf.writeBlockPos(selection.getSecond());
        }
        ServerPlayNetworking.send(player, SELECTION_SYNC_PACKET, buf);
    }
}