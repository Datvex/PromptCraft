package dev.promptcraft.client.gui;

import dev.promptcraft.client.GhostPreviewState;
import dev.promptcraft.network.PromptCraftNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class PlacementConfirmScreen extends Screen {
    private final BlockPos anchor;
    private final int rotationSteps;

    public PlacementConfirmScreen(BlockPos anchor, int rotationSteps) {
        super(Text.literal("Confirm Placement"));
        this.anchor = anchor;
        this.rotationSteps = rotationSteps;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("YES").formatted(Formatting.GREEN), b -> confirm())
                .dimensions(centerX - 105, centerY + 20, 100, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("NO").formatted(Formatting.RED), b -> cancel())
                .dimensions(centerX + 5, centerY + 20, 100, 20)
                .build());
    }

    private void confirm() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(anchor);
        buf.writeInt(rotationSteps);
        ClientPlayNetworking.send(PromptCraftNetworking.CONFIRM_PLACEMENT_PACKET, buf);

        GhostPreviewState.cancel();
        if (this.client != null) this.client.setScreen(null);
    }

    private void cancel() {
        // Просто возвращаемся к позиционированию призрака, ничего не отправляем на сервер.
        if (this.client != null) this.client.setScreen(null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.fill(centerX - 110, centerY - 30, centerX + 110, centerY + 50, 0xFF1E1E1E);
        context.drawCenteredTextWithShadow(this.textRenderer, "Place this structure here?", centerX, centerY - 15, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }
}