package dev.promptcraft.item;

import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.selection.PlayerSelection;
import dev.promptcraft.selection.SelectionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SelectionBrushItem extends Item {
    public SelectionBrushItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
        World world = context.getWorld();

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        PlayerEntity player = context.getPlayer();

        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        if (!hasAccess(serverPlayer)) {
            serverPlayer.sendMessage(Text.translatable("promptcraft.message.access_denied").formatted(Formatting.RED), false);
            return ActionResult.FAIL;
        }

        BlockPos pos = context.getBlockPos();
        PlayerSelection selection = SelectionManager.get(serverPlayer);

        if (!selection.hasFirst()) {
            serverPlayer.sendMessage(Text.literal("Select the first position with left click first.").formatted(Formatting.YELLOW), false);
            return ActionResult.SUCCESS;
        }

        selection.setSecond(pos.toImmutable());

        serverPlayer.sendMessage(
                Text.translatable("promptcraft.message.selection.second", formatPos(pos)).formatted(Formatting.AQUA),
                false
        );

        if (!SelectionManager.isWithinLimit(selection)) {
            PromptCraftConfig config = PromptCraftConfigManager.get();

            serverPlayer.sendMessage(
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

            return ActionResult.SUCCESS;
        }

        serverPlayer.sendMessage(
                Text.translatable(
                        "promptcraft.message.selection.ready",
                        selection.getWidth(),
                        selection.getHeight(),
                        selection.getDepth()
                ).formatted(Formatting.GREEN),
                false
        );

        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    public static void setFirstPosition(ServerPlayerEntity player, BlockPos pos) {
        PlayerSelection selection = SelectionManager.get(player);

        if (selection.isComplete()) {
            selection.clear();
        }

        selection.setFirst(pos.toImmutable());

        player.sendMessage(
                Text.translatable("promptcraft.message.selection.first", formatPos(pos)).formatted(Formatting.AQUA),
                false
        );
    }

    private static String formatPos(BlockPos pos) {
        return "%d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean hasAccess(ServerPlayerEntity player) {
        PromptCraftConfig config = PromptCraftConfigManager.get();

        if (config.isAccessEveryone()) {
            return true;
        }

        return player.hasPermissionLevel(2);
    }
}
