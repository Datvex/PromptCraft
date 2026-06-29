package dev.promptcraft.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.promptcraft.PromptCraftMod;
import dev.promptcraft.network.PromptCraftNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class PromptCraftSettingsScreen extends Screen {
    private static final Identifier REFRESH_ICON = new Identifier(PromptCraftMod.MOD_ID, "textures/gui/refresh_icon.png");

    private PasswordFieldWidget apiKeyField;
    private ModelSelectButton modelButton;
    private FlatButton previewButton;
    private FlatButton saveButton;
    private FlatButton langButton;
    private FlatButton outlineButton;
    private FlatButton outlineThroughBlocksButton;
    private OpacitySlider opacitySlider;
    private TextFieldWidget hexColorField;
    private IconButton refreshButton;

    private String apiKey;
    private String model;
    private boolean showPreview;
    private String language;
    private String themeColor;
    private boolean thickOutline;
    private float fillOpacity;
    private boolean outlineThroughBlocks;
    
    private int selectedTab = 0;
    private static final int TAB_API = 0;
    private static final int TAB_ANIM = 1;
    private static final int TAB_LANG = 2;
    private static final int TAB_THEME = 3;
    private static final int TAB_VISUAL = 4;

    private static final String[] TAB_NAMES_EN = {"API", "Animations", "Language", "Theme", "Visual"};
    private static final String[] TAB_NAMES_RU = {"API", "Анимации", "Язык", "Тема", "Визуал"};

    private static final String[] LANG_OPTIONS = {"English", "Русский"};
    private static final String[] LANG_CODES = {"en", "ru"};

    private boolean langMenuOpen = false;
    
    // API Variables
    private boolean isFetchingModels = false;
    private String fetchError = null;
    private boolean modelMenuOpen = false;
    private List<String> fetchedModels = new ArrayList<>();
    private List<String> filteredModels = new ArrayList<>(); // Отфильтрованный список
    private int modelScroll = 0;
    private boolean draggingModelScroll = false; // Для перетаскивания скроллбара
    private TextFieldWidget modelSearchField; // Поле поиска

    // Color Picker Variables
    private float pickerHue = 0.33f;    
    private float pickerSat = 0.85f;
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

    public PromptCraftSettingsScreen(
            String apiKey,
            String model,
            boolean showPreview,
            String language,
            String themeColor,
            boolean thickOutline,
            float fillOpacity,
            boolean outlineThroughBlocks
    ) {
        super(Text.literal("PromptCraft Settings"));
        this.apiKey = apiKey;
        this.model = model;
        this.showPreview = showPreview;
        this.language = language != null ? language : "en";
        this.themeColor = themeColor != null && !themeColor.isEmpty() ? themeColor : "#17b95f";
        this.thickOutline = thickOutline;
        this.fillOpacity = Math.max(0.0f, Math.min(1.0f, fillOpacity));
        this.outlineThroughBlocks = outlineThroughBlocks;
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
        try { return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16); } 
        catch (Exception e) { return 0xFF17B95F; }
    }

    private String shortenModelName(String value) {
        if (value == null || value.isBlank()) {
            return t("Select model", "Выбрать модель");
        }

        int maxLength = 28;
        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength - 3) + "...";
    }

    private String getOutlineButtonText() {
        return t("Outline: ", "Обводка: ") + (thickOutline ? t("Thick", "Жирная") : t("Vanilla", "Обычная"));
    }

    private String getOutlineThroughBlocksButtonText() {
        return t("Contour through blocks: ", "Контур сквозь блоки: ")
                + (outlineThroughBlocks ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int menuY = centerY - 60;
        int contentX = centerX - 30;
        int contentY = menuY;

        apiKeyField = new PasswordFieldWidget(this.textRenderer, contentX + 2, contentY + 18, 178, 12, Text.literal("API Key"));
        apiKeyField.setMaxLength(200);
        apiKeyField.setText(apiKey);
        apiKeyField.setDrawsBackground(false);
        this.addDrawableChild(apiKeyField);

        modelButton = new ModelSelectButton(contentX - 5, contentY + 57, 160, 22, Text.literal(shortenModelName(model)), button -> openModelList());
        this.addDrawableChild(modelButton);

        refreshButton = new IconButton(contentX + 160, contentY + 58, 20, 20, REFRESH_ICON, button -> fetchModels());
        this.addDrawableChild(refreshButton);

        previewButton = new FlatButton(contentX - 5, contentY + 5, 190, 20, Text.literal(t("Dynamic Preview: ", "Динамический предпросмотр: ") + (showPreview ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"))), button -> {
            showPreview = !showPreview;
            button.setMessage(Text.literal(t("Dynamic Preview: ", "Динамический предпросмотр: ") + (showPreview ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"))));
        });
        this.addDrawableChild(previewButton);
        
        langButton = new FlatButton(contentX - 5, contentY + 30, 190, 20, Text.literal(t("Change language", "Изменить язык")), button -> langMenuOpen = !langMenuOpen);
        this.addDrawableChild(langButton);

        outlineButton = new FlatButton(
                contentX - 5,
                contentY + 5,
                190,
                20,
                Text.literal(getOutlineButtonText()),
                button -> {
                    thickOutline = !thickOutline;
                    button.setMessage(Text.literal(getOutlineButtonText()));
                }
        );
        this.addDrawableChild(outlineButton);

        outlineThroughBlocksButton = new FlatButton(
                contentX - 5,
                contentY + 45,
                190,
                20,
                Text.literal(getOutlineThroughBlocksButtonText()),
                button -> {
                    outlineThroughBlocks = !outlineThroughBlocks;
                    button.setMessage(Text.literal(getOutlineThroughBlocksButtonText()));
                }
        );
        this.addDrawableChild(outlineThroughBlocksButton);

        opacitySlider = new OpacitySlider(
                contentX - 5,
                contentY + 85,
                190,
                20,
                fillOpacity
        );
        this.addDrawableChild(opacitySlider);
        
        hexColorField = new TextFieldWidget(this.textRenderer, contentX + 10, contentY + 95, 60, 16, Text.literal("Hex"));
        hexColorField.setMaxLength(7);
        hexColorField.setText(themeColor);
        hexColorField.setDrawsBackground(false);
        hexColorField.setChangedListener(text -> {
            if (text.startsWith("#") && text.length() == 7) {
                try {
                    Integer.parseInt(text.substring(1), 16);
                    themeColor = text;
                    hexToHsv(themeColor);
                } catch (NumberFormatException ignored) {}
            }
        });
        this.addDrawableChild(hexColorField);
        
        saveButton = new FlatButton(centerX - 60, centerY + 90, 120, 20, Text.literal(t("Save & Close", "Сохранить и закрыть")), button -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(apiKeyField.getText());
            buf.writeString(model);
            buf.writeBoolean(showPreview);
            buf.writeString(language);
            buf.writeString(themeColor);
            buf.writeBoolean(thickOutline);
            buf.writeFloat(fillOpacity);
            buf.writeBoolean(outlineThroughBlocks);
            ClientPlayNetworking.send(PromptCraftNetworking.SAVE_GUI_PACKET, buf);
            this.client.setScreen(null);
        });
        this.addDrawableChild(saveButton);

        // Инициализация поля поиска моделей (внутри оверлея)
        int overlayW = 260; int overlayH = 160;
        int ox = centerX - overlayW / 2; int oy = centerY - overlayH / 2;
        modelSearchField = new TextFieldWidget(this.textRenderer, ox + 110, oy + 5, 120, 14, Text.literal("Search"));
        modelSearchField.setDrawsBackground(true);
        modelSearchField.setMaxLength(50);
        modelSearchField.setChangedListener(text -> filterModels());
        
        updateWidgetVisibility();
    }    

    private void filterModels() {
        String query = modelSearchField.getText().toLowerCase();
        filteredModels.clear();
        for (String m : fetchedModels) {
            if (m.toLowerCase().contains(query)) {
                filteredModels.add(m);
            }
        }
        modelScroll = 0; // Сбрасываем скролл при поиске
    }

    private void openModelList() {
        fetchError = null;

        if (fetchedModels.isEmpty()) {
            fetchModels();
            return;
        }

        filteredModels = new ArrayList<>(fetchedModels);
        modelSearchField.setText("");
        modelSearchField.setFocused(true);
        modelScroll = 0;
        modelMenuOpen = true;
    }

    private void fetchModels() {
        if (isFetchingModels) return;
        String key = apiKeyField.getText().trim();
        if (key.isEmpty()) { fetchError = t("API key is empty!", "API ключ пуст!"); return; }

        isFetchingModels = true; fetchError = null;

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://integrate.api.nvidia.com/v1/models"))
                .header("Authorization", "Bearer " + key)
                .GET().build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() == 200) {
                try {
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray data = root.getAsJsonArray("data");
                    List<String> models = new ArrayList<>();
                    for (JsonElement e : data) models.add(e.getAsJsonObject().get("id").getAsString());
                    
                    if (this.client != null) this.client.execute(() -> {
                        fetchedModels = models;
                        filteredModels = new ArrayList<>(models);
                        modelSearchField.setText("");
                        modelSearchField.setFocused(true);
                        modelScroll = 0;
                        modelMenuOpen = true;
                        isFetchingModels = false;
                    });
                } catch (Exception ex) {
                    if (this.client != null) this.client.execute(() -> { fetchError = "Parse Error!"; isFetchingModels = false; });
                }
            } else {
                if (this.client != null) this.client.execute(() -> { fetchError = "Error: " + response.statusCode(); isFetchingModels = false; });
            }
        }).exceptionally(ex -> {
            if (this.client != null) this.client.execute(() -> { fetchError = "Network Error!"; isFetchingModels = false; });
            return null;
        });
    }
    
    private void updateWidgetVisibility() {
        boolean isApi = selectedTab == TAB_API;
        boolean isAnim = selectedTab == TAB_ANIM;
        boolean isLang = selectedTab == TAB_LANG;
        boolean isTheme = selectedTab == TAB_THEME;
        boolean isVisual = selectedTab == TAB_VISUAL;
        
        apiKeyField.visible = isApi;
        apiKeyField.active = isApi;

        modelButton.visible = isApi;
        modelButton.active = isApi;

        refreshButton.visible = isApi;
        refreshButton.active = isApi;

        previewButton.visible = isAnim;
        previewButton.active = isAnim;

        langButton.visible = isLang;
        langButton.active = isLang;

        hexColorField.visible = isTheme;
        hexColorField.active = isTheme;

        outlineButton.visible = isVisual;
        outlineButton.active = isVisual;

        outlineThroughBlocksButton.visible = isVisual;
        outlineThroughBlocksButton.active = isVisual;

        opacitySlider.visible = isVisual;
        opacitySlider.active = isVisual;
    }
    
    // --- ПЕРЕХВАТ КЛАВИАТУРЫ ДЛЯ ПОЛЯ ПОИСКА ---
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modelMenuOpen && modelSearchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (apiKeyField != null && apiKeyField.isFocused()) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
                apiKeyField.setSelectionStart(0);
                apiKeyField.setSelectionEnd(apiKeyField.getText().length());
                return true;
            }

            if (apiKeyField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (modelMenuOpen && modelSearchField.charTyped(chr, modifiers)) {
            return true;
        }

        if (apiKeyField != null && apiKeyField.isFocused()) {
            return apiKeyField.charTyped(chr, modifiers);
        }

        return super.charTyped(chr, modifiers);
    }

    private boolean isMouseOverWidget(net.minecraft.client.gui.widget.ClickableWidget widget, double mouseX, double mouseY) {
        return widget != null && widget.visible && widget.isMouseOver(mouseX, mouseY);
    }

    private void clearInputFocus() {
        if (apiKeyField != null) {
            apiKeyField.setFocused(false);
        }

        if (hexColorField != null) {
            hexColorField.setFocused(false);
        }

        if (modelSearchField != null) {
            modelSearchField.setFocused(false);
        }

        this.setFocused(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2; int centerY = this.height / 2;
        int menuX = centerX - 180; int menuY = centerY - 60;
        int contentX = centerX - 30; int contentY = menuY;

        boolean clickedInput =
                isMouseOverWidget(apiKeyField, mouseX, mouseY)
                || isMouseOverWidget(hexColorField, mouseX, mouseY)
                || isMouseOverWidget(modelSearchField, mouseX, mouseY);

        if (!clickedInput && !modelMenuOpen && !langMenuOpen) {
            clearInputFocus();
        }

        if (modelMenuOpen) {
            int overlayW = 260; int overlayH = 160;
            int ox = centerX - overlayW / 2; int oy = centerY - overlayH / 2;

            // Клик по полю поиска
            if (modelSearchField.mouseClicked(mouseX, mouseY, button)) return true;

            // Клик по крестику
            int closeX = ox + overlayW - 22; int closeY = oy + 5;
            if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 14) {
                modelMenuOpen = false; return true;
            }

            // Клик по скроллбару (начинаем перетаскивание)
            int listY = oy + 25; int listH = 120;
            if (mouseX >= ox + overlayW - 10 && mouseX <= ox + overlayW - 5 && mouseY >= listY && mouseY <= listY + listH) {
                draggingModelScroll = true;
                updateModelScroll(mouseY, listY, listH);
                return true;
            }

            // Клик по элементу списка
            int itemH = 15;
            int visibleCount = listH / itemH;
            for (int i = 0; i < visibleCount; i++) {
                int index = modelScroll + i;
                if (index >= filteredModels.size()) break;
                int itemY = listY + i * itemH;
                if (mouseX >= ox + 10 && mouseX <= ox + overlayW - 15 && mouseY >= itemY && mouseY <= itemY + itemH) {
                    model = filteredModels.get(index);
                    modelButton.setMessage(Text.literal(shortenModelName(model)));
                    modelMenuOpen = false;
                    return true;
                }
            }
            
            // Если кликнули мимо списка, но в рамках оверлея — ничего не делаем
            if (mouseX >= ox && mouseX <= ox + overlayW && mouseY >= oy && mouseY <= oy + overlayH) return true;
            
            // Клик вне оверлея — закрываем
            modelMenuOpen = false; return true;
        }
        
        if (langMenuOpen) {
            int overlayW = 200; int overlayH = 90;
            int ox = centerX - overlayW / 2; int oy = centerY - overlayH / 2;
            int closeBtnX = ox + overlayW - 22; int closeBtnY = oy + 4;
            if (mouseX >= closeBtnX && mouseX <= closeBtnX + 18 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
                langMenuOpen = false; return true;
            }
            for (int i = 0; i < LANG_OPTIONS.length; i++) {
                int itemY = oy + 32 + i * 22;
                if (mouseX >= ox + 10 && mouseX <= ox + overlayW - 10 && mouseY >= itemY && mouseY <= itemY + 20) {
                    language = LANG_CODES[i];
                    langMenuOpen = false;
                    langButton.setMessage(Text.literal(t("Change language", "Изменить язык")));
                    previewButton.setMessage(Text.literal(t("Dynamic Preview: ", "Динамический предпросмотр: ") + (showPreview ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"))));
                    outlineButton.setMessage(Text.literal(getOutlineButtonText()));
                    outlineThroughBlocksButton.setMessage(Text.literal(getOutlineThroughBlocksButtonText()));
                    opacitySlider.updateMessage();
                    saveButton.setMessage(Text.literal(t("Save & Close", "Сохранить и закрыть")));
                    return true;
                }
            }
            if (mouseX < ox || mouseX > ox + overlayW || mouseY < oy || mouseY > oy + overlayH) {
                langMenuOpen = false; return true;
            }
            return true;
        }        
        
        if (mouseX >= menuX && mouseX <= menuX + 120) {
            for (int i = 0; i < 5; i++) {
                int ty = menuY + i * 25;
                if (mouseY >= ty && mouseY <= ty + 20) {
                    selectedTab = i; updateWidgetVisibility(); return true;
                }
            }
        }
        
        if (selectedTab == TAB_THEME) {
            int svX = contentX; int svY = contentY + 5;
            int hueX = svX + SV_SIZE + 8; int hueY = svY;
            if (mouseX >= svX && mouseX <= svX + SV_SIZE && mouseY >= svY && mouseY <= svY + SV_SIZE) {
                draggingSV = true; updateSV(mouseX, mouseY, svX, svY); return true;
            }
            if (mouseX >= hueX && mouseX <= hueX + HUE_W && mouseY >= hueY && mouseY <= hueY + HUE_H) {
                draggingHue = true; updateHue(mouseY, hueY); return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (modelMenuOpen && !filteredModels.isEmpty()) {
            modelScroll -= (int) amount;
            int maxScroll = Math.max(0, filteredModels.size() - (120 / 15));
            if (modelScroll < 0) modelScroll = 0;
            if (modelScroll > maxScroll) modelScroll = maxScroll;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingHue = false; draggingSV = false; draggingModelScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingModelScroll) {
            int cy = this.height / 2;
            int oy = cy - 80; // overlayH / 2
            int listY = oy + 25; int listH = 120;
            updateModelScroll(mouseY, listY, listH);
            return true;
        }

        if (selectedTab == TAB_THEME) {
            int contentX = this.width / 2 - 30; int contentY = this.height / 2 - 60;
            if (draggingSV) { updateSV(mouseX, mouseY, contentX, contentY + 5); return true; }
            if (draggingHue) { updateHue(mouseY, contentY + 5); return true; }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void updateModelScroll(double mouseY, int listY, int listH) {
        int visibleCount = listH / 15;
        int maxScroll = Math.max(0, filteredModels.size() - visibleCount);
        if (maxScroll == 0) { modelScroll = 0; return; }
        
        float trackHeight = listH - 20f; // 20 - высота самого ползунка
        float deltaY = (float) (mouseY - listY - 10f); // Центрируем по мышке
        float fraction = deltaY / trackHeight;
        fraction = Math.max(0, Math.min(1, fraction));
        
        modelScroll = Math.round(fraction * maxScroll);
    }
    
    private void updateSV(double mouseX, double mouseY, int svX, int svY) {
        pickerSat = Math.max(0, Math.min(1, (float) (mouseX - svX) / SV_SIZE));
        pickerVal = Math.max(0, Math.min(1, 1f - (float) (mouseY - svY) / SV_SIZE));
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

        int centerX = this.width / 2; int centerY = this.height / 2;
        int menuX = centerX - 180; int menuY = centerY - 60;
        int contentX = centerX - 30; int contentY = menuY;
        
        context.fill(centerX - 200, centerY - 100, centerX + 200, centerY + 120, 0xFF1E1E1E);
        context.drawTextWithShadow(this.textRenderer, t("Settings", "Настройки"), menuX, centerY - 85, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "esc", centerX + 170, centerY - 85, 0x6E6E6E);
        
        int themeColorInt = parseThemeColor(themeColor);
        renderMenuItem(context, tabName(0), menuX, menuY, selectedTab == TAB_API, themeColorInt);
        renderMenuItem(context, tabName(1), menuX, menuY + 25, selectedTab == TAB_ANIM, themeColorInt);
        renderMenuItem(context, tabName(2), menuX, menuY + 50, selectedTab == TAB_LANG, themeColorInt);
        renderMenuItem(context, tabName(3), menuX, menuY + 75, selectedTab == TAB_THEME, themeColorInt);
        renderMenuItem(context, tabName(4), menuX, menuY + 100, selectedTab == TAB_VISUAL, themeColorInt);
        
        if (selectedTab == TAB_API) {
            context.drawTextWithShadow(this.textRenderer, "NVIDIA API Key:", contentX - 5, contentY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, "Model:", contentX - 5, contentY + 45, 0xFFFFFF);

            context.fill(contentX - 5, contentY + 12, contentX + 185, contentY + 34, 0xFF2D2D2D);

            if (isFetchingModels) {
                context.drawTextWithShadow(this.textRenderer, t("Fetching...", "Загрузка..."), contentX - 5, contentY + 85, 0xAAAAAA);
            } else if (fetchError != null) {
                context.drawTextWithShadow(this.textRenderer, fetchError, contentX - 5, contentY + 85, 0xFF5555);
            }
        } else if (selectedTab == TAB_LANG) {
            context.drawTextWithShadow(this.textRenderer, t("Current language:", "Текущий язык:"), contentX - 5, contentY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, "ru".equals(language) ? "Русский" : "English", contentX - 5, contentY + 12, themeColorInt);
        } else if (selectedTab == TAB_THEME) {
            renderColorPicker(context, contentX, contentY + 5);
            context.fill(contentX, contentY + 92, contentX + SV_SIZE, contentY + 112, 0xFF2D2D2D);
            int textW = this.textRenderer.getWidth(hexColorField.getText());
            hexColorField.setX(contentX + (80 - textW) / 2);
        } else if (selectedTab == TAB_VISUAL) {
            context.drawTextWithShadow(this.textRenderer, t("Outline:", "Обводка:"), contentX - 5, contentY - 10, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, t("Through blocks:", "Сквозь блоки:"), contentX - 5, contentY + 30, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, t("Fill opacity:", "Прозрачность заливки:"), contentX - 5, contentY + 70, 0xFFFFFF);
        }
        
        super.render(context, mouseX, mouseY, delta);

        if (langMenuOpen) renderLangMenu(context);
        if (modelMenuOpen) renderModelMenu(context, mouseX, mouseY, delta);
    }    

    private void renderModelMenu(DrawContext context, int mouseX, int mouseY, float delta) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);

        int cx = this.width / 2; int cy = this.height / 2;
        int overlayW = 260; int overlayH = 160;
        int ox = cx - overlayW / 2; int oy = cy - overlayH / 2;
        
        context.fill(0, 0, this.width, this.height, 0x80000000);
        
        context.fill(ox, oy, ox + overlayW, oy + overlayH, 0xFF1E1E1E);
        context.fill(ox, oy, ox + overlayW, oy + 1, 0xFF555555);
        context.fill(ox, oy + overlayH - 1, ox + overlayW, oy + overlayH, 0xFF111111);
        context.fill(ox, oy, ox + 1, oy + overlayH, 0xFF555555);
        context.fill(ox + overlayW - 1, oy, ox + overlayW, oy + overlayH, 0xFF111111);
        
        context.drawTextWithShadow(this.textRenderer, t("Select Model", "Выберите модель"), ox + 10, oy + 8, 0xFFFFFF);
        
        // Рендер текстового поля поиска (внутри поднятой матрицы)
        modelSearchField.render(context, mouseX, mouseY, delta);
        
        // Плейсхолдер Search...
        if (modelSearchField.getText().isEmpty() && !modelSearchField.isFocused()) {
            context.drawTextWithShadow(this.textRenderer, "Search...", modelSearchField.getX() + 4, modelSearchField.getY() + 3, 0xAAAAAA);
        }

        int closeX = ox + overlayW - 22; int closeY = oy + 5;
        context.fill(closeX, closeY, closeX + 18, closeY + 14, 0xFF2D2D2D);
        context.drawText(this.textRenderer, "X", closeX + 6, closeY + 3, 0xFFAAAAAA, false);
        
        int listY = oy + 25; int listH = 120; int itemH = 15;
        int visibleCount = listH / itemH;
        int themeColorInt = parseThemeColor(themeColor);
        
        for (int i = 0; i < visibleCount; i++) {
            int index = modelScroll + i;
            if (index >= filteredModels.size()) break;
            
            String modId = filteredModels.get(index);
            int itemY = listY + i * itemH;
            
            boolean hovered = mouseX >= ox + 10 && mouseX <= ox + overlayW - 15 && mouseY >= itemY && mouseY <= itemY + itemH;
            int bgColor = hovered ? themeColorInt : 0xFF2D2D2D;
            int textColor = hovered ? 0x000000 : 0xD2D2D2;
            
            context.fill(ox + 10, itemY, ox + overlayW - 15, itemY + itemH, bgColor);
            if (hovered) {
                context.drawText(this.textRenderer, modId, ox + 15, itemY + 4, textColor, false);
            } else {
                context.drawTextWithShadow(this.textRenderer, modId, ox + 15, itemY + 4, textColor);
            }
        }
        
        int maxScroll = Math.max(0, filteredModels.size() - visibleCount);
        if (maxScroll > 0) {
            int scrollBarY = listY + (int) ((modelScroll / (float) maxScroll) * (listH - 20));
            context.fill(ox + overlayW - 10, listY, ox + overlayW - 5, listY + listH, 0xFF111111);
            context.fill(ox + overlayW - 10, scrollBarY, ox + overlayW - 5, scrollBarY + 20, themeColorInt);
        }

        context.getMatrices().pop();
    }
    
    // ==========================================
    // НИЖЕ ВСЕ ВЕРНУВШИЕСЯ МЕТОДЫ (НЕ МЕНЯЛИСЬ)
    // ==========================================
    
    private void renderLangMenu(DrawContext context) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);
        int cx = this.width / 2; int cy = this.height / 2;
        int overlayW = 200; int overlayH = 90;
        int ox = cx - overlayW / 2; int oy = cy - overlayH / 2;
        
        context.fill(0, 0, this.width, this.height, 0x80000000);
        context.fill(ox, oy, ox + overlayW, oy + overlayH, 0xFF1E1E1E);
        context.fill(ox, oy, ox + overlayW, oy + 1, 0xFF555555);
        context.fill(ox, oy + overlayH - 1, ox + overlayW, oy + overlayH, 0xFF111111);
        context.fill(ox, oy, ox + 1, oy + overlayH, 0xFF555555);
        context.fill(ox + overlayW - 1, oy, ox + overlayW, oy + overlayH, 0xFF111111);
        
        String title = t("Select Language", "Выберите язык");
        context.drawTextWithShadow(this.textRenderer, title, ox + 10, oy + 6, 0xFFFFFF);
        
        int closeX = ox + overlayW - 22; int closeY = oy + 5;
        context.fill(closeX, closeY, closeX + 18, closeY + 14, 0xFF2D2D2D);
        context.drawText(this.textRenderer, "X", closeX + 6, closeY + 3, 0xFFAAAAAA, false);
        
        int themeColorInt = parseThemeColor(themeColor);
        for (int i = 0; i < LANG_OPTIONS.length; i++) {
            int itemY = oy + 30 + i * 22;
            boolean sel = LANG_CODES[i].equals(language);
            if (sel) {
                context.fill(ox + 10, itemY, ox + overlayW - 10, itemY + 20, themeColorInt);
                context.drawText(this.textRenderer, LANG_OPTIONS[i], ox + 16, itemY + 6, 0x000000, false);
            } else {
                context.fill(ox + 10, itemY, ox + overlayW - 10, itemY + 20, 0xFF2D2D2D);
                context.drawTextWithShadow(this.textRenderer, LANG_OPTIONS[i], ox + 16, itemY + 6, 0xD2D2D2);
            }
        }
        context.getMatrices().pop();
    }    
    
    private void renderColorPicker(DrawContext context, int x, int y) {
        int svX = x; int svY = y; int hueX = svX + SV_SIZE + 8; int hueY = svY;
        for (int row = 0; row < SV_CELLS; row++) {
            for (int col = 0; col < SV_CELLS; col++) {
                float s = col / (float) SV_CELLS; float v = 1f - row / (float) SV_CELLS;
                int color = hsvToRgb(pickerHue, s, v);
                context.fill(svX + col * CELL_SIZE, svY + row * CELL_SIZE, svX + col * CELL_SIZE + CELL_SIZE, svY + row * CELL_SIZE + CELL_SIZE, 0xFF000000 | color);
            }
        }
        context.fill(svX - 1, svY - 1, svX + SV_SIZE + 1, svY, 0xFF555555);
        context.fill(svX - 1, svY + SV_SIZE, svX + SV_SIZE + 1, svY + SV_SIZE + 1, 0xFF111111);
        context.fill(svX - 1, svY, svX, svY + SV_SIZE, 0xFF555555);
        context.fill(svX + SV_SIZE, svY, svX + SV_SIZE + 1, svY + SV_SIZE + 1, 0xFF111111);
        
        int segH = HUE_H / HUE_SEGMENTS;
        for (int i = 0; i < HUE_SEGMENTS; i++) {
            float h = i / (float) HUE_SEGMENTS; int c = hsvToRgb(h, 1f, 1f);
            context.fill(hueX, hueY + i * segH, hueX + HUE_W, hueY + i * segH + segH, 0xFF000000 | c);
        }
        context.fill(hueX - 1, hueY - 1, hueX + HUE_W + 1, hueY, 0xFF555555);
        context.fill(hueX - 1, hueY + HUE_H, hueX + HUE_W + 1, hueY + HUE_H + 1, 0xFF111111);
        context.fill(hueX - 1, hueY, hueX, hueY + HUE_H, 0xFF555555);
        context.fill(hueX + HUE_W, hueY, hueX + HUE_W + 1, hueY + HUE_H + 1, 0xFF111111);
        
        int hueIndicatorY = hueY + (int) (pickerHue * HUE_H);
        context.fill(hueX - 3, hueIndicatorY - 1, hueX + HUE_W + 3, hueIndicatorY + 2, 0xFFFFFFFF);
        context.fill(hueX - 2, hueIndicatorY, hueX + HUE_W + 2, hueIndicatorY + 1, 0xFF000000);
        
        int svIndX = svX + (int) (pickerSat * SV_SIZE); int svIndY = svY + (int) ((1f - pickerVal) * SV_SIZE);
        context.fill(svIndX - 2, svIndY - 2, svIndX + 3, svIndY - 1, 0xFFFFFFFF);
        context.fill(svIndX - 2, svIndY + 2, svIndX + 3, svIndY + 3, 0xFFFFFFFF);
        context.fill(svIndX - 2, svIndY - 1, svIndX - 1, svIndY + 2, 0xFFFFFFFF);
        context.fill(svIndX + 2, svIndY - 1, svIndX + 3, svIndY + 2, 0xFFFFFFFF);
        context.fill(svIndX - 1, svIndY - 1, svIndX + 2, svIndY + 2, 0xFF000000);
        
        int previewX = hueX + HUE_W + 12; int previewY = hueY;
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
            int r = (rgb >> 16) & 0xFF; int g = (rgb >> 8) & 0xFF; int b = rgb & 0xFF;
            float max = Math.max(r, Math.max(g, b)) / 255f; float min = Math.min(r, Math.min(g, b)) / 255f;
            pickerVal = max; float d = max - min;
            if (d == 0) { pickerHue = 0; pickerSat = 0; } else {
                pickerSat = d / max; float h;
                if (max == r / 255f) h = ((g / 255f - b / 255f) / d + 6) % 6;
                else if (max == g / 255f) h = (b / 255f - r / 255f) / d + 2;
                else h = (r / 255f - g / 255f) / d + 4;
                pickerHue = h / 6f;
            }
        } catch (Exception ignored) { pickerHue = 0.33f; pickerSat = 0.85f; pickerVal = 0.72f; }
    }
    
    private String hsvToHex(float h, float s, float v) { return String.format("#%06X", hsvToRgb(h, s, v)); }
    
    private int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6); float f = h * 6 - i;
        float p = v * (1 - s); float q = v * (1 - f * s); float t = v * (1 - (1 - f) * s);
        float r = 0, g = 0, b = 0;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break; case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break; case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break; case 5: r = v; g = p; b = q; break;
        }
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private class OpacitySlider extends SliderWidget {
        public OpacitySlider(int x, int y, int width, int height, float initialValue) {
            super(x, y, width, height, Text.empty(), Math.max(0.0D, Math.min(1.0D, initialValue)));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round(this.value * 100.0D);
            this.setMessage(Text.literal(t("Opacity: ", "Прозрачность: ") + percent + "%"));
        }

        @Override
        protected void applyValue() {
            fillOpacity = (float) Math.max(0.0D, Math.min(1.0D, this.value));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) {
                return;
            }

            int bgColor = this.isHovered() ? 0xFF3D3D3D : 0xFF2D2D2D;
            int themeColorInt = parseThemeColor(themeColor);

            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            int fillW = (int) (this.width * this.value);
            context.fill(this.getX(), this.getY(), this.getX() + fillW, this.getY() + this.height, themeColorInt);

            int knobX = this.getX() + fillW;
            context.fill(knobX - 2, this.getY() - 2, knobX + 2, this.getY() + this.height + 2, 0xFFFFFFFF);

            int textColor = this.value > 0.45D ? 0x000000 : 0xFFFFFF;
            int textX = this.getX() + (this.width - PromptCraftSettingsScreen.this.textRenderer.getWidth(this.getMessage())) / 2;
            int textY = this.getY() + (this.height - 8) / 2;

            context.drawTextWithShadow(PromptCraftSettingsScreen.this.textRenderer, this.getMessage(), textX, textY, textColor);
        }
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

    private class ModelSelectButton extends FlatButton {
        public ModelSelectButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) {
                return;
            }

            int bgColor = this.isHovered() ? 0xFF3D3D3D : 0xFF2D2D2D;
            int textColor = this.isHovered() ? 0xFFFFFF : 0xD2D2D2;

            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            int textX = this.getX() + 7;
            int textY = this.getY() + (this.height - 8) / 2;

            context.drawTextWithShadow(PromptCraftSettingsScreen.this.textRenderer, this.getMessage(), textX, textY, textColor);

            context.drawTextWithShadow(
                    PromptCraftSettingsScreen.this.textRenderer,
                    "▼",
                    this.getX() + this.width - 14,
                    textY,
                    textColor
            );
        }
    }

    private class PasswordFieldWidget extends TextFieldWidget {
        public PasswordFieldWidget(net.minecraft.client.font.TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
            super(textRenderer, x, y, width, height, text);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) {
                return;
            }

            int bgColor = this.isFocused() ? 0xFF3A3A3A : 0xFF2D2D2D;
            context.fill(
                    this.getX() - 7,
                    this.getY() - 6,
                    this.getX() + this.width + 7,
                    this.getY() + this.height + 6,
                    bgColor
            );

            String real = this.getText();
            String shown = real.isEmpty() ? "" : "*".repeat(Math.min(real.length(), 32));

            int textX = this.getX();
            int textY = this.getY() + 2;
            int textColor = this.active ? 0xE0E0E0 : 0x707070;

            renderSelectionHighlight(context, shown, textX, textY);

            context.drawTextWithShadow(
                    PromptCraftSettingsScreen.this.textRenderer,
                    shown,
                    textX,
                    textY,
                    textColor
            );

            renderPasswordCursor(context, shown, textX, textY);
        }

        private void renderSelectionHighlight(DrawContext context, String shown, int textX, int textY) {
            if (!this.isFocused() || shown.isEmpty()) {
                return;
            }

            String selectedText = this.getSelectedText();
            if (selectedText == null || selectedText.isEmpty()) {
                return;
            }

            int selectionStart = getVisualSelectionStart();
            int selectionEnd = getVisualSelectionEnd(shown);

            if (selectionStart == selectionEnd) {
                return;
            }

            int startX = textX + PromptCraftSettingsScreen.this.textRenderer.getWidth(shown.substring(0, selectionStart));
            int endX = textX + PromptCraftSettingsScreen.this.textRenderer.getWidth(shown.substring(0, selectionEnd));

            int left = Math.min(startX, endX);
            int right = Math.max(startX, endX);

            context.fill(left, textY - 1, right, textY + 10, 0xFF2F6FED);
        }

        private int getVisualSelectionStart() {
            int realLength = this.getText().length();

            if (realLength <= 0) {
                return 0;
            }

            int selectedLength = this.getSelectedText().length();

            if (selectedLength >= realLength) {
                return 0;
            }

            return 0;
        }

        private int getVisualSelectionEnd(String shown) {
            int realLength = this.getText().length();
            int selectedLength = this.getSelectedText().length();

            if (selectedLength >= realLength) {
                return shown.length();
            }

            return Math.min(selectedLength, shown.length());
        }

        private void renderPasswordCursor(DrawContext context, String shown, int textX, int textY) {
            if (!this.isFocused()) {
                return;
            }

            if (!this.getSelectedText().isEmpty()) {
                return;
            }

            if ((System.currentTimeMillis() / 500) % 2 != 0) {
                return;
            }

            int cursorX = textX + PromptCraftSettingsScreen.this.textRenderer.getWidth(shown);
            context.fill(cursorX + 1, textY, cursorX + 2, textY + 11, 0xFFFFFFFF);
        }
    }

    private class IconButton extends ButtonWidget {
        private final Identifier texture;
        public IconButton(int x, int y, int width, int height, Identifier texture, PressAction onPress) {
            super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
            this.texture = texture;
        }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) return;
            int bgColor = this.isHovered() ? 0xFF3D3D3D : 0xFF2D2D2D;
            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            context.getMatrices().push();
            context.getMatrices().translate(this.getX() + 2, this.getY() + 2, 0);
            context.getMatrices().scale(0.5f, 0.5f, 1.0f);
            context.drawTexture(texture, 0, 0, 0, 0, 32, 32, 32, 32);
            context.getMatrices().pop();
        }
    }
}