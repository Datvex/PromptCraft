package dev.promptcraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.config.PromptCraftConfigManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PromptCraftSettingsScreen extends Screen {
    private static final Identifier BG_TEXTURE = new Identifier("promptcraft", "textures/gui/settings_background.png");
    
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
        int centerY = this.height / 2;

        apiKeyField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY - 45, 200, 20, Text.literal("API Key"));
        apiKeyField.setMaxLength(100);
        apiKeyField.setText(apiKey);
        this.addDrawableChild(apiKeyField);

        modelField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY + 5, 200, 20, Text.literal("Model"));
        modelField.setMaxLength(100);
        modelField.setText(model);
        this.addDrawableChild(modelField);

        previewButton = ButtonWidget.builder(Text.literal("Dynamic Preview: " + (showPreview ? "ON" : "OFF")), button -> {
            showPreview = !showPreview;
            button.setMessage(Text.literal("Dynamic Preview: " + (showPreview ? "ON" : "OFF")));
        }).dimensions(centerX - 100, centerY + 45, 200, 20).build();
        this.addDrawableChild(previewButton);

        saveButton = ButtonWidget.builder(Text.literal("Save & Close"), button -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(apiKeyField.getText());
            buf.writeString(modelField.getText());
            buf.writeBoolean(showPreview);
            ClientPlayNetworking.send(PromptCraftNetworking.SAVE_GUI_PACKET, buf);
            this.client.setScreen(null);
        }).dimensions(centerX - 100, centerY + 75, 200, 20).build();
        this.addDrawableChild(saveButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        // Draw Custom Background Texture
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int bgWidth = 256;
        int bgHeight = 256;
        int x = (this.width - bgWidth) / 2;
        int y = (this.height - bgHeight) / 2;
        context.drawTexture(BG_TEXTURE, x, y, 0, 0, bgWidth, bgHeight);

        // Get Theme Color for Text
        String hex = PromptCraftConfigManager.get().themeColor.replace("#", "");
        int color = 0x17B95F;
        try { color = Integer.parseInt(hex, 16); } catch (Exception ignored) {}

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, y + 15, color);
        context.drawTextWithShadow(this.textRenderer, Text.literal("NVIDIA API Key:"), this.width / 2 - 100, y + 40, color);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Model:"), this.width / 2 - 100, y + 80, color);
        
        super.render(context, mouseX, mouseY, delta);
    }
}