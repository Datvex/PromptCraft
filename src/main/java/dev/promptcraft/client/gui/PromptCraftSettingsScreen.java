package dev.promptcraft.client.gui;

import dev.promptcraft.network.PromptCraftNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class PromptCraftSettingsScreen extends Screen {
    private TextFieldWidget apiKeyField;
    private TextFieldWidget modelField;
    private ButtonWidget previewButton;
    private ButtonWidget saveButton;

    private String apiKey;
    private String model;
    private boolean showPreview;

    public PromptCraftSettingsScreen(String apiKey, String model, boolean showPreview) {
        super(Text.literal("PromptCraft Settings"));
        this.apiKey = apiKey;
        this.model = model;
        this.showPreview = showPreview;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 40;

        apiKeyField = new TextFieldWidget(this.textRenderer, centerX - 100, startY, 200, 20, Text.literal("API Key"));
        apiKeyField.setMaxLength(100);
        apiKeyField.setText(apiKey);
        this.addDrawableChild(apiKeyField);

        modelField = new TextFieldWidget(this.textRenderer, centerX - 100, startY + 40, 200, 20, Text.literal("Model"));
        modelField.setMaxLength(100);
        modelField.setText(model);
        this.addDrawableChild(modelField);

        previewButton = ButtonWidget.builder(Text.literal("Dynamic Preview: " + (showPreview ? "ON" : "OFF")), button -> {
            showPreview = !showPreview;
            button.setMessage(Text.literal("Dynamic Preview: " + (showPreview ? "ON" : "OFF")));
        }).dimensions(centerX - 100, startY + 80, 200, 20).build();
        this.addDrawableChild(previewButton);

        saveButton = ButtonWidget.builder(Text.literal("Save & Close"), button -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(apiKeyField.getText());
            buf.writeString(modelField.getText());
            buf.writeBoolean(showPreview);
            ClientPlayNetworking.send(PromptCraftNetworking.SAVE_GUI_PACKET, buf);
            this.client.setScreen(null);
        }).dimensions(centerX - 100, startY + 120, 200, 20).build();
        this.addDrawableChild(saveButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("NVIDIA API Key:"), this.width / 2 - 100, 28, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Model:"), this.width / 2 - 100, 68, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }
}