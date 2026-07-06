package dev.promptcraft;

import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.config.PromptCraftConfigManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

public final class PromptCraftCommands {
    private PromptCraftCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("pmenu")
                    .executes(context -> {
                        ServerPlayerEntity player = getPlayerOrFail(context.getSource());
                        if (player != null && hasAccess(player)) {
                            PromptCraftNetworking.openSettingsGui(player);
                        }
                        return 1;
                    }));
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            // Пока у игрока висит предпросмотр свободной постройки, ЛКМ используется
            // только для подтверждения размещения и не должна ломать блоки в мире.
            var activeSession = dev.promptcraft.session.PromptSessionManager.getActiveGeneration(serverPlayer);
            if (activeSession.isPresent() && activeSession.get().isGhostPending()) {
                return ActionResult.FAIL;
            }

            ItemStack stack = serverPlayer.getStackInHand(hand);
            if (!stack.isOf(PromptCraftItems.SELECTION_BRUSH)) return ActionResult.PASS;
            if (!hasAccess(serverPlayer)) {
                serverPlayer.sendMessage(Text.literal("Access Denied.").formatted(Formatting.RED), false);
                return ActionResult.FAIL;
            }
            dev.promptcraft.item.SelectionBrushItem.setFirstPosition(serverPlayer, pos);
            return ActionResult.SUCCESS;
        });
    }

    public static boolean hasAccess(ServerPlayerEntity player) {
        return PromptCraftConfigManager.get().isAccessEveryone() || player.hasPermissionLevel(2);
    }

    private static ServerPlayerEntity getPlayerOrFail(ServerCommandSource source) {
        try { return source.getPlayerOrThrow(); } catch (Exception e) { return null; }
    }
}