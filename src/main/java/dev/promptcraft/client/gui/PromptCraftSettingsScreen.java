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
    private FlatButton previewButton;
    private FlatButton saveButton;
    private FlatButton langButton;
    private TextFieldWidget hexColorField;

    private String apiKey;
    private String model;
    private boolean showPreview;
    private String language;
    private String themeColor;
    
    private int selectedTab = 0;
    private static final int TAB_API = 0;
    private static final int TAB_ANIM = 1;
    private static final int TAB_LANG = 2;
    private static final int TAB_THEME = 3;

    private static final String[] TAB_NAMES_EN = {"API", "Animations", "Language", "Theme"};
    private static final String[] TAB_NAMES_RU = {"API", "Анимации", "Язык", "Тема"};

    private static final String[] LANG_OPTIONS = {"English", "Русский"};
    private static final String[] LANG_CODES = {"en", "ru"};

    private boolean langMenuOpen = false;
    
    private float pickerHue = 0.33f;    private float pickerSat = 0.85f;
    private float pickerVal = 0.72f;
    private boolean draggingHue = false;
    private boolean draggingSV = false;
    
    private static final int SV_SIZE = 80;
    private static final int SV_CELLS = 20;
    private static final int CELL_SIZE = 4;
    private static final int HUE_W = 10;
    private static final int HUE_H = 80;
    private static final int HUE_SEGMENTS = 24;
    private static final int PREVIEW_SIZE = 20;

    public PromptCraftSettingsScreen(String apiKey, String model, boolean showPreview, String language, String themeColor) {
        super(Text.literal("PromptCraft Settings"));
        this.apiKey = apiKey;
        this.model = model;
        this.showPreview = showPreview;
        this.language = language != null ? language : "en";
        this.themeColor = themeColor != null && !themeColor.isEmpty() ? themeColor : "#17b95f";
        hexToHsv(this.themeColor);
    }
    private String t(String en, String ru) {
        return "ru".equals(language) ? ru : en;
    }

    private String tabName(int index) {
        String[] names = "ru".equals(language) ? TAB_NAMES_RU : TAB_NAMES_EN;
        return names[index];
    }

    private int parseThemeColor(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xFF17B95F;
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int menuY = centerY - 60;
        int contentX = centerX - 30;
        int contentY = menuY;

        apiKeyField = new TextFieldWidget(this.textRenderer, contentX, contentY + 15, 180, 16, Text.literal("API Key"));
        apiKeyField.setMaxLength(100);
        apiKeyField.setText(apiKey);
        apiKeyField.setDrawsBackground(false);
        this.addDrawableChild(apiKeyField);

        modelField = new TextFieldWidget(this.textRenderer, contentX, contentY + 60, 180, 16, Text.literal("Model"));
        modelField.setMaxLength(100);
        modelField.setText(model);
        modelField.setDrawsBackground(false);
        this.addDrawableChild(modelField);

        previewButton = new FlatButton(contentX - 5, contentY + 5, 190, 20, Text.literal(t("Dynamic Preview: ", "Динамический предпросмотр: ") + (showPreview ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"))), button -> {
            showPreview = !showPreview;
            button.setMessage(Text.literal(t("Dynamic Preview: ", "Динамический предпросмотр: ") + (showPreview ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"))));
        });
        this.addDrawableChild(previewButton);
        
        langButton = new FlatButton(contentX - 5, contentY + 5, 190, 20, Text.literal(t("Change language", "Изменить язык")), button -> {
            langMenuOpen = !langMenuOpen;
        });
        this.addDrawableChild(langButton);
        hexColorField = new TextFieldWidget(this.textRenderer, contentX + 100, contentY + 110, 80, 16, Text.literal("Hex"));
        hexColorField.setMaxLength(7);
        hexColorField.setText(themeColor);
        hexColorField.setDrawsBackground(false);
        this.addDrawableChild(hexColorField);

        saveButton = new FlatButton(centerX - 60, centerY + 90, 120, 20, Text.literal(t("Save & Close", "Сохранить и закрыть")), button -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(apiKeyField.getText());
            buf.writeString(modelField.getText());
            buf.writeBoolean(showPreview);
            buf.writeString(language);
            buf.writeString(themeColor);
            ClientPlayNetworking.send(PromptCraftNetworking.SAVE_GUI_PACKET, buf);
            this.client.setScreen(null);
        });
        this.addDrawableChild(saveButton);
        
        updateWidgetVisibility();
    }    
    private void updateWidgetVisibility() {
        boolean isApi = selectedTab == TAB_API;
        boolean isAnim = selectedTab == TAB_ANIM;
        boolean isLang = selectedTab == TAB_LANG;
        boolean isTheme = selectedTab == TAB_THEME;
        
        apiKeyField.visible = isApi;
        apiKeyField.active = isApi;
        modelField.visible = isApi;
        modelField.active = isApi;
        
        previewButton.visible = isAnim;
        previewButton.active = isAnim;
        
        langButton.visible = isLang;
        langButton.active = isLang;
        
        hexColorField.visible = isTheme;
        hexColorField.active = isTheme;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int menuX = centerX - 180;
        int menuY = centerY - 60;
        int contentX = centerX - 30;
        int contentY = menuY;
        
        // Language overlay (top layer)
        if (langMenuOpen) {
            int overlayW = 200;
            int overlayH = 90;
            int ox = centerX - overlayW / 2;
            int oy = centerY - overlayH / 2;
            
            // Close button (X)
            int closeBtnX = ox + overlayW - 22;
            int closeBtnY = oy + 4;
            if (mouseX >= closeBtnX && mouseX <= closeBtnX + 18 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
                langMenuOpen = false;
                return true;
            }
            
            // Language options
            for (int i = 0; i < LANG_OPTIONS.length; i++) {
                int itemY = oy + 32 + i * 22;
                if (mouseX >= ox + 10 && mouseX <= ox + overlayW - 10 && mouseY >= itemY && mouseY <= itemY + 20) {
                    language = LANG_CODES[i];
                    langMenuOpen = false;
                    langButton.setMessage(Text.literal(t("Change language", "Изменить язык")));
                    previewButton.setMessage(Text.literal(t("Dynamic Preview: ", "Динамический предпросмотр: ") + (showPreview ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"))));
                    saveButton.setMessage(Text.literal(t("Save & Close", "Сохранить и закрыть")));
                    return true;
                }
            }
            
            // Click outside overlay = close
            if (mouseX < ox || mouseX > ox + overlayW || mouseY < oy || mouseY > oy + overlayH) {
                langMenuOpen = false;
                return true;
            }
            return true;
        }        
        // Tab clicks
        if (mouseX >= menuX && mouseX <= menuX + 120) {
            for (int i = 0; i < 4; i++) {
                int ty = menuY + i * 25;
                if (mouseY >= ty && mouseY <= ty + 20) {
                    selectedTab = i;
                    updateWidgetVisibility();
                    return true;
                }
            }
        }
        
        // HSV picker clicks
        if (selectedTab == TAB_THEME) {
            int svX = contentX;
            int svY = contentY + 5;
            int hueX = svX + SV_SIZE + 8;
            int hueY = svY;
            
            if (mouseX >= svX && mouseX <= svX + SV_SIZE && mouseY >= svY && mouseY <= svY + SV_SIZE) {
                draggingSV = true;
                updateSV(mouseX, mouseY, svX, svY);
                return true;
            }
            if (mouseX >= hueX && mouseX <= hueX + HUE_W && mouseY >= hueY && mouseY <= hueY + HUE_H) {
                draggingHue = true;
                updateHue(mouseY, hueY);
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingHue = false;
        draggingSV = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (selectedTab == TAB_THEME) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int contentX = centerX - 30;
            int contentY = centerY - 60;
            int svX = contentX;
            int svY = contentY + 5;
            int hueY = svY;
            if (draggingSV) {                updateSV(mouseX, mouseY, svX, svY);
                return true;
            }
            if (draggingHue) {
                updateHue(mouseY, hueY);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    private void updateSV(double mouseX, double mouseY, int svX, int svY) {
        float s = Math.max(0, Math.min(1, (float) (mouseX - svX) / SV_SIZE));
        float v = Math.max(0, Math.min(1, 1f - (float) (mouseY - svY) / SV_SIZE));
        pickerSat = s;
        pickerVal = v;
        themeColor = hsvToHex(pickerHue, pickerSat, pickerVal);
        hexColorField.setText(themeColor);
    }
    
    private void updateHue(double mouseY, int hueY) {
        pickerHue = Math.max(0, Math.min(1, (float) (mouseY - hueY) / HUE_H));
        themeColor = hsvToHex(pickerHue, pickerSat, pickerVal);
        hexColorField.setText(themeColor);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int menuX = centerX - 180;
        int menuY = centerY - 60;
        int contentX = centerX - 30;
        int contentY = menuY;
        
        // Background
        context.fill(centerX - 200, centerY - 100, centerX + 200, centerY + 120, 0xFF1E1E1E);
        
        // Title (only once)
        context.drawTextWithShadow(this.textRenderer, t("Settings", "Настройки"), menuX, centerY - 85, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "esc", centerX + 170, centerY - 85, 0x6E6E6E);
        
        int themeColorInt = parseThemeColor(themeColor);
        
        // Menu Items
        renderMenuItem(context, tabName(0), menuX, menuY, selectedTab == TAB_API, themeColorInt);
        renderMenuItem(context, tabName(1), menuX, menuY + 25, selectedTab == TAB_ANIM, themeColorInt);
        renderMenuItem(context, tabName(2), menuX, menuY + 50, selectedTab == TAB_LANG, themeColorInt);
        renderMenuItem(context, tabName(3), menuX, menuY + 75, selectedTab == TAB_THEME, themeColorInt);
        
        // Content panels
        if (selectedTab == TAB_API) {
            context.drawTextWithShadow(this.textRenderer, "NVIDIA API Key:", contentX - 5, contentY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, "Model:", contentX - 5, contentY + 45, 0xFFFFFF);

            context.fill(contentX - 5, contentY + 12, contentX + 185, contentY + 34, 0xFF2D2D2D);
            context.fill(contentX - 5, contentY + 57, contentX + 185, contentY + 79, 0xFF2D2D2D);
        } else if (selectedTab == TAB_LANG) {
            context.drawTextWithShadow(this.textRenderer, t("Current language:", "Текущий язык:"), contentX - 5, contentY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, "ru".equals(language) ? "Русский" : "English", contentX - 5, contentY + 12, themeColorInt);
        } else if (selectedTab == TAB_THEME) {
            renderColorPicker(context, contentX, contentY + 5);
            context.drawTextWithShadow(this.textRenderer, t("Hex:", "Hex:"), contentX - 5, contentY + 95, 0xFFFFFF);
            context.fill(contentX + 20, contentY + 95, contentX + 105, contentY + 117, 0xFF2D2D2D);
        }

        // Language popup (render on top)
        if (langMenuOpen) {
            renderLangMenu(context);
        }

        super.render(context, mouseX, mouseY, delta);
    }
    
    private void renderLangMenu(DrawContext context) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int overlayW = 200;
        int overlayH = 90;
        int ox = cx - overlayW / 2;
        int oy = cy - overlayH / 2;
        
        // Semi-transparent darkening behind overlay
        context.fill(0, 0, this.width, this.height, 0xCC000000);
        
        // Overlay background with 3D pixel border
        context.fill(ox, oy, ox + overlayW, oy + overlayH, 0xFF1E1E1E);
        context.fill(ox, oy, ox + overlayW, oy + 1, 0xFF555555);
        context.fill(ox, oy + overlayH - 1, ox + overlayW, oy + overlayH, 0xFF111111);
        context.fill(ox, oy, ox + 1, oy + overlayH, 0xFF555555);
        context.fill(ox + overlayW - 1, oy, ox + overlayW, oy + overlayH, 0xFF111111);
        
        // Title
        String title = t("Select Language", "Выберите язык");
        context.drawTextWithShadow(this.textRenderer, title, ox + 10, oy + 6, 0xFFFFFF);
        
        // Close X button
        int closeX = ox + overlayW - 22;
        int closeY = oy + 5;
        context.fill(closeX, closeY, closeX + 18, closeY + 14, 0xFF2D2D2D);
        context.fill(closeX, closeY, closeX + 18, closeY + 1, 0xFF444444);
        context.fill(closeX, closeY + 13, closeX + 18, closeY + 14, 0xFF222222);
        context.drawText(this.textRenderer, "X", closeX + 6, closeY + 3, 0xFFAAAAAA, false);
        
        // Language options
        for (int i = 0; i < LANG_OPTIONS.length; i++) {
            int itemY = oy + 30 + i * 22;
            boolean sel = LANG_CODES[i].equals(language);
            if (sel) {
                context.fill(ox + 10, itemY, ox + overlayW - 10, itemY + 20, 0xFFF8F675);
                context.drawText(this.textRenderer, LANG_OPTIONS[i], ox + 16, itemY + 6, 0x000000, false);
            } else {
                context.fill(ox + 10, itemY, ox + overlayW - 10, itemY + 20, 0xFF2D2D2D);
                context.drawTextWithShadow(this.textRenderer, LANG_OPTIONS[i], ox + 16, itemY + 6, 0xD2D2D2);
            }
        }
    }    
    private void renderColorPicker(DrawContext context, int x, int y) {
        int svX = x;
        int svY = y;
        int hueX = svX + SV_SIZE + 8;
        int hueY = svY;
        
        // Saturation-Value grid (pixel-art look with cells)
        for (int row = 0; row < SV_CELLS; row++) {
            for (int col = 0; col < SV_CELLS; col++) {
                float s = col / (float) SV_CELLS;
                float v = 1f - row / (float) SV_CELLS;
                int color = hsvToRgb(pickerHue, s, v);
                context.fill(svX + col * CELL_SIZE, svY + row * CELL_SIZE,
                             svX + col * CELL_SIZE + CELL_SIZE, svY + row * CELL_SIZE + CELL_SIZE,
                             0xFF000000 | color);
            }
        }
        // SV border (pixel-art 3D block look)
        context.fill(svX - 1, svY - 1, svX + SV_SIZE + 1, svY, 0xFF555555);
        context.fill(svX - 1, svY + SV_SIZE, svX + SV_SIZE + 1, svY + SV_SIZE + 1, 0xFF111111);
        context.fill(svX - 1, svY, svX, svY + SV_SIZE, 0xFF555555);
        context.fill(svX + SV_SIZE, svY, svX + SV_SIZE + 1, svY + SV_SIZE + 1, 0xFF111111);
        
        // Hue bar (pixel-art segmented)
        int segH = HUE_H / HUE_SEGMENTS;
        for (int i = 0; i < HUE_SEGMENTS; i++) {
            float h = i / (float) HUE_SEGMENTS;
            int c = hsvToRgb(h, 1f, 1f);
            context.fill(hueX, hueY + i * segH, hueX + HUE_W, hueY + i * segH + segH, 0xFF000000 | c);
        }
        // Hue border
        context.fill(hueX - 1, hueY - 1, hueX + HUE_W + 1, hueY, 0xFF555555);
        context.fill(hueX - 1, hueY + HUE_H, hueX + HUE_W + 1, hueY + HUE_H + 1, 0xFF111111);
        context.fill(hueX - 1, hueY, hueX, hueY + HUE_H, 0xFF555555);
        context.fill(hueX + HUE_W, hueY, hueX + HUE_W + 1, hueY + HUE_H + 1, 0xFF111111);
        
        // Hue selector indicator (white pixel crosshair on hue bar)
        int hueIndicatorY = hueY + (int) (pickerHue * HUE_H);
        context.fill(hueX - 3, hueIndicatorY - 1, hueX + HUE_W + 3, hueIndicatorY + 2, 0xFFFFFFFF);
        context.fill(hueX - 2, hueIndicatorY, hueX + HUE_W + 2, hueIndicatorY + 1, 0xFF000000);
        
        // SV selector indicator (white border crosshair)
        int svIndX = svX + (int) (pickerSat * SV_SIZE);
        int svIndY = svY + (int) ((1f - pickerVal) * SV_SIZE);
        context.fill(svIndX - 2, svIndY - 2, svIndX + 3, svIndY - 1, 0xFFFFFFFF);
        context.fill(svIndX - 2, svIndY + 2, svIndX + 3, svIndY + 3, 0xFFFFFFFF);
        context.fill(svIndX - 2, svIndY - 1, svIndX - 1, svIndY + 2, 0xFFFFFFFF);
        context.fill(svIndX + 2, svIndY - 1, svIndX + 3, svIndY + 2, 0xFFFFFFFF);
        context.fill(svIndX - 1, svIndY - 1, svIndX + 2, svIndY + 2, 0xFF000000);
        
        // Preview square
        int previewX = hueX + HUE_W + 12;
        int previewY = hueY;
        context.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, parseThemeColor(themeColor));
        context.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + 1, 0xFF555555);
        context.fill(previewX, previewY + PREVIEW_SIZE - 1, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, 0xFF111111);
        context.fill(previewX, previewY, previewX + 1, previewY + PREVIEW_SIZE, 0xFF555555);
        context.fill(previewX + PREVIEW_SIZE - 1, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, 0xFF111111);
    }
    
    private void renderMenuItem(DrawContext context, String text, int x, int y, boolean selected, int themeColor) {
        if (selected) {
            context.fill(x, y, x + 120, y + 20, themeColor);
            context.drawText(this.textRenderer, text, x + 5, y + 6, 0x000000, false);
        } else {
            context.drawTextWithShadow(this.textRenderer, text, x + 5, y + 6, 0xD2D2D2);
        }
    }
    
    private void hexToHsv(String hex) {
        try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            float max = Math.max(r, Math.max(g, b)) / 255f;
            float min = Math.min(r, Math.min(g, b)) / 255f;
            pickerVal = max;
            float d = max - min;
            if (d == 0) {
                pickerHue = 0; pickerSat = 0;
            } else {
                pickerSat = d / max;
                float h;
                if (max == r / 255f) h = ((g / 255f - b / 255f) / d + 6) % 6;
                else if (max == g / 255f) h = (b / 255f - r / 255f) / d + 2;
                else h = (r / 255f - g / 255f) / d + 4;
                pickerHue = h / 6f;
            }
        } catch (Exception ignored) {
            pickerHue = 0.33f; pickerSat = 0.85f; pickerVal = 0.72f;
        }
    }
    
    private String hsvToHex(float h, float s, float v) {
        int rgb = hsvToRgb(h, s, v);
        return String.format("#%06X", rgb);
    }
    
    private int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r = 0, g = 0, b = 0;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            case 5: r = v; g = p; b = q; break;
        }
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private class FlatButton extends ButtonWidget {
        public FlatButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) return;
            
            int bgColor = this.isHovered() ? 0xFF3D3D3D : 0xFF2D2D2D;
            int textColor = this.isHovered() ? 0xFFFFFF : 0xD2D2D2;

            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            int textX = this.getX() + (this.width - PromptCraftSettingsScreen.this.textRenderer.getWidth(this.getMessage())) / 2;
            int textY = this.getY() + (this.height - 8) / 2;
            context.drawTextWithShadow(PromptCraftSettingsScreen.this.textRenderer, this.getMessage(), textX, textY, textColor);
        }
    }
}