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
    private FlatButton previewButton;
    private FlatButton saveButton;

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

        apiKeyField = new TextFieldWidget(this.textRenderer, centerX - 90, centerY - 25, 180, 16, Text.literal("API Key"));
        apiKeyField.setMaxLength(100);
        apiKeyField.setText(apiKey);
        apiKeyField.setDrawsBackground(false);
        this.addDrawableChild(apiKeyField);

        modelField = new TextFieldWidget(this.textRenderer, centerX - 90, centerY + 25, 180, 16, Text.literal("Model"));
        modelField.setMaxLength(100);
        modelField.setText(model);
        modelField.setDrawsBackground(false);
        this.addDrawableChild(modelField);

        previewButton = new FlatButton(centerX - 95, centerY + 55, 190, 20, Text.literal("Dynamic Preview: " + (showPreview ? "ON" : "OFF")), button -> {
            showPreview = !showPreview;
            button.setMessage(Text.literal("Dynamic Preview: " + (showPreview ? "ON" : "OFF")));
        });
        this.addDrawableChild(previewButton);

        saveButton = new FlatButton(centerX - 95, centerY + 85, 190, 20, Text.literal("Save & Close"), button -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(apiKeyField.getText());
            buf.writeString(modelField.getText());
            buf.writeBoolean(showPreview);
            ClientPlayNetworking.send(PromptCraftNetworking.SAVE_GUI_PACKET, buf);
            this.client.setScreen(null);
        });
        this.addDrawableChild(saveButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int bgWidth = 256;
        int bgHeight = 256;
        int x = (this.width - bgWidth) / 2;
        int y = (this.height - bgHeight) / 2;
        context.drawTexture(BG_TEXTURE, x, y, 0, 0, bgWidth, bgHeight);

        String hex = PromptCraftConfigManager.get().themeColor.replace("#", "");
        int color = 0x17B95F;
        try { color = Integer.parseInt(hex, 16); } catch (Exception ignored) {}

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, y + 30, color);
        context.drawTextWithShadow(this.textRenderer, Text.literal("NVIDIA API Key:"), centerX - 95, centerY - 40, color);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Model:"), centerX - 95, centerY + 10, color);

        // Draw flat borders for text fields
        context.fill(centerX - 95, centerY - 28, centerX + 95, centerY - 6, 0xFF1A1A1A);
        context.drawBorder(centerX - 95, centerY - 28, 190, 22, 0xFF000000 | color);

        context.fill(centerX - 95, centerY + 22, centerX + 95, centerY + 44, 0xFF1A1A1A);
        context.drawBorder(centerX - 95, centerY + 22, 190, 22, 0xFF000000 | color);

        super.render(context, mouseX, mouseY, delta);
    }

    private class FlatButton extends ButtonWidget {
        public FlatButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            String hex = PromptCraftConfigManager.get().themeColor.replace("#", "");
            int themeColor = 0x17B95F;
            try { themeColor = Integer.parseInt(hex, 16); } catch (Exception ignored) {}

            int borderColor = 0xFF000000 | themeColor;
            int bgColor = this.isHovered() ? (0x66000000 | themeColor) : 0xFF1A1A1A;
            int textColor = this.isHovered() ? 0xFFFFFF : themeColor;

            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            context.drawBorder(this.getX(), this.getY(), this.width, this.height, borderColor);

            int textX = this.getX() + (this.width - PromptCraftSettingsScreen.this.textRenderer.getWidth(this.getMessage())) / 2;
            int textY = this.getY() + (this.height - 8) / 2;
            context.drawTextWithShadow(PromptCraftSettingsScreen.this.textRenderer, this.getMessage(), textX, textY, textColor);
        }
    }
}