package dev.promptcraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftEnv;
import dev.promptcraft.selection.PlayerSelection;
import dev.promptcraft.selection.SelectionManager;
import dev.promptcraft.session.PendingPrompt;
import dev.promptcraft.session.PromptSessionManager;
import dev.promptcraft.structure.HistoryManager;
import dev.promptcraft.task.DestructionTask;
import dev.promptcraft.task.TaskManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

public final class PromptCraftCommands {
    private PromptCraftCommands() {}

    public static void register() {
        registerCommands();
        registerLeftClickSelectionEvent();
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("promptcraft")
                    .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                            .executes(context -> handlePromptCraft(context.getSource(), StringArgumentType.getString(context, "prompt")))));
            dispatcher.register(CommandManager.literal("promptedit")
                    .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                            .executes(context -> handlePromptEdit(context.getSource(), StringArgumentType.getString(context, "prompt")))));
            dispatcher.register(CommandManager.literal("promptconfirm").executes(context -> handlePromptConfirm(context.getSource())));
            dispatcher.register(CommandManager.literal("promptcancel").executes(context -> handlePromptCancel(context.getSource())));
            dispatcher.register(CommandManager.literal("promptundo").executes(context -> handlePromptUndo(context.getSource())));
            dispatcher.register(CommandManager.literal("promptback").executes(context -> handlePromptBack(context.getSource())));
            dispatcher.register(CommandManager.literal("promptnext").executes(context -> handlePromptNext(context.getSource())));
            dispatcher.register(CommandManager.literal("promptsettings").executes(context -> handlePromptSettings(context.getSource())));
        });
    }

    private static void registerLeftClickSelectionEvent() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
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

    private static int handlePromptCraft(ServerCommandSource source, String prompt) {
        ServerPlayerEntity player = getPlayerOrFail(source);
        if (player == null || !validatePlayerCanUsePromptCraft(player)) return 0;

        PlayerSelection selection = SelectionManager.get(player);
        if (!selection.isComplete()) {
            player.sendMessage(Text.literal("You must select an area first!").formatted(Formatting.RED), false);
            return 0;
        }

        PendingPrompt pendingPrompt = new PendingPrompt(prompt, selection.getMin(), selection.getMax(), selection.getWidth(), selection.getHeight(), selection.getDepth());
        PromptSessionManager.setPending(player, pendingPrompt);
        sendConfirmationMessage(player, prompt);
        return 1;
    }

    private static int handlePromptEdit(ServerCommandSource source, String prompt) {
        ServerPlayerEntity player = getPlayerOrFail(source);
        if (player != null && validatePlayerCanUsePromptCraft(player)) player.sendMessage(Text.literal("Edit registered: " + prompt).formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int handlePromptConfirm(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);
        if (player == null || !validatePlayerCanUsePromptCraft(player)) return 0;

        var optionalPrompt = PromptSessionManager.getPending(player);
        if (optionalPrompt.isEmpty()) {
            player.sendMessage(Text.literal("No pending prompt found.").formatted(Formatting.RED), false);
            return 0;
        }

        PendingPrompt prompt = optionalPrompt.get();
        PromptSessionManager.clearPending(player);
        player.sendMessage(Text.literal("Preparing area...").formatted(Formatting.YELLOW), false);

        TaskManager.addTask(new DestructionTask(player, prompt.getSelectionMin(), prompt.getSelectionMax(), () -> {
            player.sendMessage(Text.literal("[AI Placeholder] Sent to NVIDIA API...").formatted(Formatting.AQUA), false);
        }));

        return 1;
    }

    private static int handlePromptCancel(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);
        if (player != null) {
            PromptSessionManager.clearPending(player);
            player.sendMessage(Text.literal("Prompt cancelled.").formatted(Formatting.YELLOW), false);
        }
        return 1;
    }

    private static int handlePromptUndo(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);
        if (player != null && validatePlayerCanUsePromptCraft(player)) {
            if (HistoryManager.undo(player)) {
                player.sendMessage(Text.literal("Undo successful! Area restored.").formatted(Formatting.GREEN), false);
            } else {
                player.sendMessage(Text.literal("Nothing to undo.").formatted(Formatting.RED), false);
            }
        }
        return 1;
    }

    private static int handlePromptBack(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);
        if (player != null && validatePlayerCanUsePromptCraft(player)) player.sendMessage(Text.literal("Step back.").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int handlePromptNext(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);
        if (player != null && validatePlayerCanUsePromptCraft(player)) player.sendMessage(Text.literal("Step forward.").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int handlePromptSettings(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);
        if (player == null || !hasAccess(player)) return 0;

        PromptCraftConfig config = PromptCraftConfigManager.get();
        String configDir = PromptCraftConfigManager.getConfigPath().getParent().toString();

        MutableText folderLink = Text.literal("[Click here to open folder]")
                .formatted(Formatting.AQUA, Formatting.UNDERLINE)
                .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, configDir))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Opens the folder to edit .env and config.json"))));

        player.sendMessage(Text.literal("=== PromptCraft Settings ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("Setup API Key: ").append(folderLink), false);
        player.sendMessage(Text.literal("Provider: " + config.provider).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("Model: " + config.model).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("API Key: " + PromptCraftEnv.getMaskedNvidiaApiKeyStatus()).formatted(Formatting.GRAY), false);
        return 1;
    }

    private static void sendConfirmationMessage(ServerPlayerEntity player, String prompt) {
        player.sendMessage(Text.literal("Confirm building?").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("\"" + prompt + "\"").formatted(Formatting.WHITE), false);

        MutableText yes = Text.literal("[YES, START]").formatted(Formatting.GREEN, Formatting.BOLD)
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/promptconfirm")));
        MutableText no = Text.literal("[NO, EDIT]").formatted(Formatting.RED, Formatting.BOLD)
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/promptcraft " + prompt)));

        player.sendMessage(Text.empty().append(yes).append(Text.literal("  ")).append(no), false);
    }

    private static boolean validatePlayerCanUsePromptCraft(ServerPlayerEntity player) {
        if (!hasAccess(player)) {
            player.sendMessage(Text.literal("Access Denied.").formatted(Formatting.RED), false);
            return false;
        }
        if (!player.isCreative()) {
            player.sendMessage(Text.literal("You must be in creative mode.").formatted(Formatting.RED), false);
            return false;
        }
        return true;
    }

    private static boolean hasAccess(ServerPlayerEntity player) {
        return PromptCraftConfigManager.get().isAccessEveryone() || player.hasPermissionLevel(2);
    }

    private static ServerPlayerEntity getPlayerOrFail(ServerCommandSource source) {
        try {
            return source.getPlayerOrThrow();
        } catch (Exception e) {
            return null;
        }
    }
}
