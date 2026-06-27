package dev.promptcraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftEnv;
import dev.promptcraft.selection.PlayerSelection;
import dev.promptcraft.selection.SelectionManager;
import dev.promptcraft.session.PendingPrompt;
import dev.promptcraft.session.PromptSessionManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

public final class PromptCraftCommands {
    private PromptCraftCommands() {
    }

    public static void register() {
        registerCommands();
        registerLeftClickSelectionEvent();
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("promptcraft")
                    .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                            .executes(context -> handlePromptCraft(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "prompt")
                            ))));

            dispatcher.register(CommandManager.literal("PromptCraft")
                    .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                            .executes(context -> handlePromptCraft(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "prompt")
                            ))));

            dispatcher.register(CommandManager.literal("promptedit")
                    .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                            .executes(context -> handlePromptEdit(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "prompt")
                            ))));

            dispatcher.register(CommandManager.literal("PromptEdit")
                    .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                            .executes(context -> handlePromptEdit(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "prompt")
                            ))));

            dispatcher.register(CommandManager.literal("promptconfirm")
                    .executes(context -> handlePromptConfirm(context.getSource())));

            dispatcher.register(CommandManager.literal("PromptConfirm")
                    .executes(context -> handlePromptConfirm(context.getSource())));

            dispatcher.register(CommandManager.literal("promptcancel")
                    .executes(context -> handlePromptCancel(context.getSource())));

            dispatcher.register(CommandManager.literal("PromptCancel")
                    .executes(context -> handlePromptCancel(context.getSource())));

            dispatcher.register(CommandManager.literal("promptundo")
                    .executes(context -> handlePromptUndo(context.getSource())));

            dispatcher.register(CommandManager.literal("PromptUndo")
                    .executes(context -> handlePromptUndo(context.getSource())));

            dispatcher.register(CommandManager.literal("promptback")
                    .executes(context -> handlePromptBack(context.getSource())));

            dispatcher.register(CommandManager.literal("PromptBack")
                    .executes(context -> handlePromptBack(context.getSource())));

            dispatcher.register(CommandManager.literal("promptnext")
                    .executes(context -> handlePromptNext(context.getSource())));

            dispatcher.register(CommandManager.literal("PromptNext")
                    .executes(context -> handlePromptNext(context.getSource())));

            dispatcher.register(CommandManager.literal("promptsettings")
                    .executes(context -> handlePromptSettings(context.getSource())));

            dispatcher.register(CommandManager.literal("PromptSettings")
                    .executes(context -> handlePromptSettings(context.getSource())));
        });
    }

    private static void registerLeftClickSelectionEvent() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }

            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            ItemStack stack = serverPlayer.getStackInHand(hand);

            if (!stack.isOf(PromptCraftItems.SELECTION_BRUSH)) {
                return ActionResult.PASS;
            }

            if (!hasAccess(serverPlayer)) {
                serverPlayer.sendMessage(Text.translatable("promptcraft.message.access_denied").formatted(Formatting.RED), false);
                return ActionResult.FAIL;
            }

            dev.promptcraft.item.SelectionBrushItem.setFirstPosition(serverPlayer, pos);

            /*
             * Return SUCCESS to prevent normal block breaking with the brush.
             */
            return ActionResult.SUCCESS;
        });
    }

    private static int handlePromptCraft(ServerCommandSource source, String prompt) {
        ServerPlayerEntity player = getPlayerOrFail(source);

        if (player == null) {
            return 0;
        }

        if (!validatePlayerCanUsePromptCraft(player)) {
            return 0;
        }

        PlayerSelection selection = SelectionManager.get(player);

        if (!selection.isComplete()) {
            player.sendMessage(Text.translatable("promptcraft.message.selection.required").formatted(Formatting.RED), false);
            return 0;
        }

        if (!SelectionManager.isWithinLimit(selection)) {
            PromptCraftConfig config = PromptCraftConfigManager.get();

            player.sendMessage(
                    Text.translatable(
                            "promptcraft.message.selection.too_large",
                            selection.getWidth(),
                            selection.getHeight(),
                            selection.getDepth(),
                            config.maxSelectionWidth,
                            config.maxSelectionHeight,
                            config.maxSelectionDepth
                    ).formatted(Formatting.RED),
                    false
            );

            return 0;
        }

        PendingPrompt pendingPrompt = new PendingPrompt(
                prompt,
                selection.getMin(),
                selection.getMax(),
                selection.getWidth(),
                selection.getHeight(),
                selection.getDepth()
        );

        PromptSessionManager.setPending(player, pendingPrompt);

        sendConfirmationMessage(player, prompt);

        return 1;
    }

    private static int handlePromptEdit(ServerCommandSource source, String prompt) {
        ServerPlayerEntity player = getPlayerOrFail(source);

        if (player == null) {
            return 0;
        }

        if (!validatePlayerCanUsePromptCraft(player)) {
            return 0;
        }

        player.sendMessage(Text.translatable("promptcraft.message.edit.placeholder").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int handlePromptConfirm(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);

        if (player == null) {
            return 0;
        }

        if (!validatePlayerCanUsePromptCraft(player)) {
            return 0;
        }

        if (PromptSessionManager.getPending(player).isEmpty()) {
            player.sendMessage(Text.translatable("promptcraft.message.prompt.pending_missing").formatted(Formatting.RED), false);
            return 0;
        }

        /*
         * AI generation, validation, clearing and building will be implemented in the next part.
         */
        player.sendMessage(Text.translatable("promptcraft.message.prompt.confirmed_placeholder").formatted(Formatting.GREEN), false);
        PromptSessionManager.clearPending(player);

        return 1;
    }

    private static int handlePromptCancel(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);

        if (player == null) {
            return 0;
        }

        PromptSessionManager.clearPending(player);
        player.sendMessage(Text.translatable("promptcraft.message.prompt.cancelled").formatted(Formatting.YELLOW), false);

        return 1;
    }

    private static int handlePromptUndo(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);

        if (player == null) {
            return 0;
        }

        if (!validatePlayerCanUsePromptCraft(player)) {
            return 0;
        }

        player.sendMessage(Text.translatable("promptcraft.message.undo.placeholder").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int handlePromptBack(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);

        if (player == null) {
            return 0;
        }

        if (!validatePlayerCanUsePromptCraft(player)) {
            return 0;
        }

        player.sendMessage(Text.translatable("promptcraft.message.back.placeholder").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int handlePromptNext(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);

        if (player == null) {
            return 0;
        }

        if (!validatePlayerCanUsePromptCraft(player)) {
            return 0;
        }

        player.sendMessage(Text.translatable("promptcraft.message.next.placeholder").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int handlePromptSettings(ServerCommandSource source) {
        ServerPlayerEntity player = getPlayerOrFail(source);

        if (player == null) {
            return 0;
        }

        if (!hasAccess(player)) {
            player.sendMessage(Text.translatable("promptcraft.message.access_denied").formatted(Formatting.RED), false);
            return 0;
        }

        PromptCraftConfig config = PromptCraftConfigManager.get();

        player.sendMessage(Text.translatable("promptcraft.message.settings.header").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.translatable("promptcraft.message.settings.config_path", PromptCraftConfigManager.getConfigPath().toString()).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.translatable("promptcraft.message.settings.env_path", PromptCraftConfigManager.getEnvPath().toString()).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.translatable("promptcraft.message.settings.provider", config.provider).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.translatable("promptcraft.message.settings.model", config.model).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.translatable("promptcraft.message.settings.access", config.accessMode).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.translatable("promptcraft.message.settings.api_key", PromptCraftEnv.getMaskedNvidiaApiKeyStatus()).formatted(Formatting.GRAY), false);
        player.sendMessage(
                Text.translatable(
                        "promptcraft.message.settings.limit",
                        config.selectionLimitEnabled ? "enabled" : "disabled",
                        config.maxSelectionWidth,
                        config.maxSelectionHeight,
                        config.maxSelectionDepth
                ).formatted(Formatting.GRAY),
                false
        );

        return 1;
    }

    private static void sendConfirmationMessage(ServerPlayerEntity player, String prompt) {
        player.sendMessage(Text.translatable("promptcraft.message.prompt.confirm").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("\"" + prompt + "\"").formatted(Formatting.WHITE), false);

        MutableText yes = Text.translatable("promptcraft.message.prompt.yes")
                .formatted(Formatting.GREEN, Formatting.BOLD)
                .styled(style -> style.withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/promptconfirm"
                )));

        MutableText separator = Text.literal(" ");

        MutableText no = Text.translatable("promptcraft.message.prompt.no")
                .formatted(Formatting.RED, Formatting.BOLD)
                .styled(style -> style.withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        "/promptcraft " + prompt
                )));

        player.sendMessage(Text.empty().append(yes).append(separator).append(no), false);
    }

    private static boolean validatePlayerCanUsePromptCraft(ServerPlayerEntity player) {
        if (!hasAccess(player)) {
            player.sendMessage(Text.translatable("promptcraft.message.access_denied").formatted(Formatting.RED), false);
            return false;
        }

        if (!player.isCreative()) {
            player.sendMessage(Text.translatable("promptcraft.message.creative_required").formatted(Formatting.RED), false);
            return false;
        }

        return true;
    }

    private static boolean hasAccess(ServerPlayerEntity player) {
        PromptCraftConfig config = PromptCraftConfigManager.get();

        if (config.isAccessEveryone()) {
            return true;
        }

        return player.hasPermissionLevel(2);
    }

    private static ServerPlayerEntity getPlayerOrFail(ServerCommandSource source) {
        try {
            return source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return null;
        }
    }
}
