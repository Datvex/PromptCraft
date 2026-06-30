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

    private static final Identifier NVIDIA_ICON = new Identifier(PromptCraftMod.MOD_ID, "textures/gui/nvidia.png");
    private static final Identifier OPENAI_ICON = new Identifier(PromptCraftMod.MOD_ID, "textures/gui/openai.png");
    private static final Identifier ANTHROPIC_ICON = new Identifier(PromptCraftMod.MOD_ID, "textures/gui/anthropic.png");
    private static final Identifier DEEPSEEK_ICON = new Identifier(PromptCraftMod.MOD_ID, "textures/gui/deepseek.png");
    private static final Identifier GEMINI_ICON = new Identifier(PromptCraftMod.MOD_ID, "textures/gui/gemini.png");
    private static final Identifier XAI_ICON = new Identifier(PromptCraftMod.MOD_ID, "textures/gui/xai.png");
    private static final Identifier OPENROUTER_ICON = new Identifier(PromptCraftMod.MOD_ID, "textures/gui/openrouter.png");

    private static final String[] PROVIDER_OPTIONS = {
            "NVIDIA",
            "OpenAI",
            "Anthropic",
            "DeepSeek",
            "Google Gemini",
            "xAI (Grok)",
            "OpenRouter"
    };

    private static final String[] PROVIDER_CODES = {
            "nvidia",
            "openai",
            "anthropic",
            "deepseek",
            "gemini",
            "xai",
            "openrouter"
    };

    private static final String[] TAB_NAMES_EN = {"API", "Animations", "Language", "Theme", "Visual"};
    private static final String[] TAB_NAMES_RU = {"API", "Анимации", "Язык", "Тема", "Визуал"};

    private static final String[] LANG_OPTIONS = {"English", "Русский"};
    private static final String[] LANG_CODES = {"en", "ru"};

    private static final int TAB_API = 0;
    private static final int TAB_ANIM = 1;
    private static final int TAB_LANG = 2;
    private static final int TAB_THEME = 3;
    private static final int TAB_VISUAL = 4;

    private static final int SV_SIZE = 80;
    private static final int SV_CELLS = 20;
    private static final int CELL_SIZE = 4;
    private static final int HUE_W = 10;
    private static final int HUE_H = 80;
    private static final int HUE_SEGMENTS = 24;
    private static final int PREVIEW_SIZE = 20;

    private ProviderSelectButton providerButton;
    private PasswordFieldWidget apiKeyField;
    private ModelSelectButton modelButton;
    private IconButton refreshButton;

    private FlatButton previewButton;
    private FlatButton saveButton;
    private FlatButton langButton;
    private FlatButton outlineButton;
    private FlatButton outlineThroughBlocksButton;
    private OpacitySlider opacitySlider;
    private TextFieldWidget hexColorField;
    private TextFieldWidget modelSearchField;

    private String provider;
    private String apiKey;
    private String model;
    private boolean showPreview;
    private String language;
    private String themeColor;
    private boolean thickOutline;
    private float fillOpacity;
    private boolean outlineThroughBlocks;

    private int selectedTab = TAB_API;

    private boolean providerMenuOpen = false;
    private boolean langMenuOpen = false;
    private boolean modelMenuOpen = false;

    private boolean isFetchingModels = false;
    private String fetchError = null;
    private List<String> fetchedModels = new ArrayList<>();
    private List<String> filteredModels = new ArrayList<>();
    private int modelScroll = 0;
    private boolean draggingModelScroll = false;

    private float pickerHue = 0.33f;
    private float pickerSat = 0.85f;
    private float pickerVal = 0.72f;
    private boolean draggingHue = false;
    private boolean draggingSV = false;

    public PromptCraftSettingsScreen(
            String provider,
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
        this.provider = provider != null && !provider.isBlank() ? provider : "nvidia";
        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model != null ? model : "";
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
        return ("ru".equals(language) ? TAB_NAMES_RU : TAB_NAMES_EN)[index];
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int menuY = centerY - 60;
        int contentX = centerX - 30;
        int contentY = menuY;

        providerButton = new ProviderSelectButton(
                contentX - 5,
                contentY,
                190,
                22,
                Text.literal(getProviderDisplayName(provider)),
                button -> providerMenuOpen = !providerMenuOpen
        );
        this.addDrawableChild(providerButton);

        apiKeyField = new PasswordFieldWidget(this.textRenderer, contentX + 2, contentY + 42, 178, 12, Text.literal("API Key"));
        apiKeyField.setMaxLength(300);
        apiKeyField.setText(apiKey);
        apiKeyField.setDrawsBackground(false);
        this.addDrawableChild(apiKeyField);

        modelButton = new ModelSelectButton(
                contentX - 5,
                contentY + 80,
                160,
                22,
                Text.literal(shortenModelName(model)),
                button -> openModelList()
        );
        this.addDrawableChild(modelButton);

        refreshButton = new IconButton(contentX + 160, contentY + 80, 22, 22, REFRESH_ICON, button -> fetchModels());
        this.addDrawableChild(refreshButton);

        previewButton = new FlatButton(
                contentX - 5,
                contentY + 5,
                190,
                20,
                Text.literal(t("Dynamic Preview: ", "Динамический предпросмотр: ") + (showPreview ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"))),
                button -> {
                    showPreview = !showPreview;
                    button.setMessage(Text.literal(t("Dynamic Preview: ", "Динамический предпросмотр: ") + (showPreview ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"))));
                }
        );
        this.addDrawableChild(previewButton);

        langButton = new FlatButton(
                contentX - 5,
                contentY + 30,
                190,
                20,
                Text.literal(t("Change language", "Изменить язык")),
                button -> langMenuOpen = !langMenuOpen
        );
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

        opacitySlider = new OpacitySlider(contentX - 5, contentY + 85, 190, 20, fillOpacity);
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
                } catch (Exception ignored) {
                }
            }
        });
        this.addDrawableChild(hexColorField);

        saveButton = new FlatButton(centerX - 60, centerY + 90, 120, 20, Text.literal(t("Save & Close", "Сохранить и закрыть")), button -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(provider);
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

        int overlayW = 260;
        int overlayH = 160;
        int ox = centerX - overlayW / 2;
        int oy = centerY - overlayH / 2;

        modelSearchField = new TextFieldWidget(this.textRenderer, ox + 110, oy + 5, 120, 14, Text.literal("Search"));
        modelSearchField.setDrawsBackground(true);
        modelSearchField.setMaxLength(50);
        modelSearchField.setChangedListener(text -> filterModels());

        updateWidgetVisibility();
    }

    private void updateWidgetVisibility() {
        boolean isApi = selectedTab == TAB_API;
        boolean isAnim = selectedTab == TAB_ANIM;
        boolean isLang = selectedTab == TAB_LANG;
        boolean isTheme = selectedTab == TAB_THEME;
        boolean isVisual = selectedTab == TAB_VISUAL;

        providerButton.visible = isApi;
        providerButton.active = isApi;

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
        if (key.isEmpty()) {
            fetchError = t("API key is empty!", "API ключ пуст!");
            return;
        }

        isFetchingModels = true;
        fetchError = null;

        String url = getModelsUrlForProvider(provider);

        if (url == null) {
            fetchError = t("Model list is not supported for this provider yet.", "Список моделей пока не поддерживается для этого провайдера.");
            isFetchingModels = false;
            return;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();

        if ("gemini".equals(provider)) {
            requestBuilder.header("x-goog-api-key", key);
        } else if ("anthropic".equals(provider)) {
            requestBuilder.header("x-api-key", key);
            requestBuilder.header("anthropic-version", "2023-06-01");
        } else {
            requestBuilder.header("Authorization", "Bearer " + key);
        }

        HttpClient.newHttpClient()
                .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (this.client == null) return;

                    this.client.execute(() -> {
                        try {
                            if (response.statusCode() != 200) {
                                fetchError = "Error: " + response.statusCode();
                                isFetchingModels = false;
                                return;
                            }

                            fetchedModels = parseModelsForProvider(provider, response.body());
                            filteredModels = new ArrayList<>(fetchedModels);

                            modelSearchField.setText("");
                            modelSearchField.setFocused(true);
                            modelScroll = 0;
                            modelMenuOpen = true;
                            isFetchingModels = false;
                        } catch (Exception e) {
                            fetchError = "Parse Error!";
                            isFetchingModels = false;
                        }
                    });
                })
                .exceptionally(ex -> {
                    if (this.client != null) {
                        this.client.execute(() -> {
                            fetchError = "Network Error!";
                            isFetchingModels = false;
                        });
                    }
                    return null;
                });
    }

    private String getModelsUrlForProvider(String provider) {
        return switch (provider) {
            case "nvidia" -> "https://integrate.api.nvidia.com/v1/models";
            case "openai" -> "https://api.openai.com/v1/models";
            case "deepseek" -> "https://api.deepseek.com/models";
            case "openrouter" -> "https://openrouter.ai/api/v1/models";
            case "xai" -> "https://api.x.ai/v1/models";
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models";
            default -> null;
        };
    }

    private List<String> parseModelsForProvider(String provider, String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        List<String> models = new ArrayList<>();

        if ("gemini".equals(provider)) {
            JsonArray arr = root.getAsJsonArray("models");
            for (JsonElement e : arr) {
                String name = e.getAsJsonObject().get("name").getAsString();
                if (name.startsWith("models/")) name = name.substring("models/".length());
                models.add(name);
            }
            return models;
        }

        JsonArray data = root.getAsJsonArray("data");
        for (JsonElement e : data) {
            JsonObject obj = e.getAsJsonObject();
            if (obj.has("id")) models.add(obj.get("id").getAsString());
        }

        return models;
    }

    private void filterModels() {
        String query = modelSearchField.getText().toLowerCase();
        filteredModels.clear();

        for (String m : fetchedModels) {
            if (m.toLowerCase().contains(query)) {
                filteredModels.add(m);
            }
        }

        modelScroll = 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (providerMenuOpen || langMenuOpen) {
            return true;
        }

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
        if (providerMenuOpen || langMenuOpen) {
            return true;
        }

        if (modelMenuOpen && modelSearchField.charTyped(chr, modifiers)) {
            return true;
        }

        if (apiKeyField != null && apiKeyField.isFocused()) {
            return apiKeyField.charTyped(chr, modifiers);
        }

        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int menuX = centerX - 180;
        int menuY = centerY - 60;
        int contentX = centerX - 30;
        int contentY = menuY;

        if (providerMenuOpen) {
            return handleProviderMenuClick(mouseX, mouseY);
        }

        if (modelMenuOpen) {
            return handleModelMenuClick(mouseX, mouseY, button);
        }

        if (langMenuOpen) {
            return handleLangMenuClick(mouseX, mouseY);
        }

        if (mouseX >= menuX && mouseX <= menuX + 120) {
            for (int i = 0; i < 5; i++) {
                int ty = menuY + i * 25;
                if (mouseY >= ty && mouseY <= ty + 20) {
                    selectedTab = i;
                    updateWidgetVisibility();
                    return true;
                }
            }
        }

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

    private boolean handleProviderMenuClick(double mouseX, double mouseY) {
        int overlayW = 220;
        int overlayH = 190;
        int ox = this.width / 2 - overlayW / 2;
        int oy = this.height / 2 - overlayH / 2;

        int closeX = ox + overlayW - 22;
        int closeY = oy + 5;

        if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 14) {
            providerMenuOpen = false;
            return true;
        }

        for (int i = 0; i < PROVIDER_OPTIONS.length; i++) {
            int itemY = oy + 30 + i * 22;
            if (mouseX >= ox + 10 && mouseX <= ox + overlayW - 10 && mouseY >= itemY && mouseY <= itemY + 20) {
                provider = PROVIDER_CODES[i];
                providerButton.setMessage(Text.literal(getProviderDisplayName(provider)));
                providerMenuOpen = false;

                fetchedModels.clear();
                filteredModels.clear();
                model = getDefaultModelForProvider(provider);
                modelButton.setMessage(Text.literal(shortenModelName(model)));

                return true;
            }
        }

        if (mouseX < ox || mouseX > ox + overlayW || mouseY < oy || mouseY > oy + overlayH) {
            providerMenuOpen = false;
        }

        return true;
    }

    private boolean handleLangMenuClick(double mouseX, double mouseY) {
        int overlayW = 200;
        int overlayH = 90;
        int ox = this.width / 2 - overlayW / 2;
        int oy = this.height / 2 - overlayH / 2;

        int closeX = ox + overlayW - 22;
        int closeY = oy + 5;

        if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 14) {
            langMenuOpen = false;
            return true;
        }

        for (int i = 0; i < LANG_OPTIONS.length; i++) {
            int itemY = oy + 30 + i * 22;
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
            langMenuOpen = false;
        }

        return true;
    }

    private boolean handleModelMenuClick(double mouseX, double mouseY, int button) {
        int overlayW = 260;
        int overlayH = 160;
        int ox = this.width / 2 - overlayW / 2;
        int oy = this.height / 2 - overlayH / 2;

        if (modelSearchField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int closeX = ox + overlayW - 22;
        int closeY = oy + 5;

        if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 14) {
            modelMenuOpen = false;
            return true;
        }

        int listY = oy + 25;
        int listH = 120;

        if (mouseX >= ox + overlayW - 10 && mouseX <= ox + overlayW - 5 && mouseY >= listY && mouseY <= listY + listH) {
            draggingModelScroll = true;
            updateModelScroll(mouseY, listY, listH);
            return true;
        }

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

        if (mouseX < ox || mouseX > ox + overlayW || mouseY < oy || mouseY > oy + overlayH) {
            modelMenuOpen = false;
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (modelMenuOpen && !filteredModels.isEmpty()) {
            modelScroll -= (int) amount;
            int maxScroll = Math.max(0, filteredModels.size() - (120 / 15));
            modelScroll = Math.max(0, Math.min(maxScroll, modelScroll));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingHue = false;
        draggingSV = false;
        draggingModelScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingModelScroll) {
            int oy = this.height / 2 - 80;
            updateModelScroll(mouseY, oy + 25, 120);
            return true;
        }

        if (selectedTab == TAB_THEME) {
            int contentX = this.width / 2 - 30;
            int contentY = this.height / 2 - 60;

            if (draggingSV) {
                updateSV(mouseX, mouseY, contentX, contentY + 5);
                return true;
            }

            if (draggingHue) {
                updateHue(mouseY, contentY + 5);
                return true;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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

        context.fill(centerX - 200, centerY - 100, centerX + 200, centerY + 120, 0xFF1E1E1E);
        context.drawTextWithShadow(this.textRenderer, t("Settings", "Настройки"), menuX, centerY - 85, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "esc", centerX + 170, centerY - 85, 0x6E6E6E);

        int themeColorInt = parseThemeColor(themeColor);

        for (int i = 0; i < 5; i++) {
            renderMenuItem(context, tabName(i), menuX, menuY + i * 25, selectedTab == i, themeColorInt);
        }

        if (selectedTab == TAB_API) {
            context.drawTextWithShadow(this.textRenderer, t("Provider:", "Провайдер:"), contentX - 5, contentY - 12, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, "API Key:", contentX - 5, contentY + 26, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, "Model:", contentX - 5, contentY + 68, 0xFFFFFF);

            if (isFetchingModels) {
                context.drawTextWithShadow(this.textRenderer, t("Fetching...", "Загрузка..."), contentX - 5, contentY + 110, 0xAAAAAA);
            } else if (fetchError != null) {
                context.drawTextWithShadow(this.textRenderer, fetchError, contentX - 5, contentY + 110, 0xFF5555);
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

        if (providerMenuOpen) renderProviderMenu(context);
        if (langMenuOpen) renderLangMenu(context);
        if (modelMenuOpen) renderModelMenu(context, mouseX, mouseY, delta);
    }

    private void renderProviderMenu(DrawContext context) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);

        int overlayW = 220;
        int overlayH = 190;
        int ox = this.width / 2 - overlayW / 2;
        int oy = this.height / 2 - overlayH / 2;

        renderOverlayBase(context, ox, oy, overlayW, overlayH);
        context.drawTextWithShadow(this.textRenderer, t("Select Provider", "Выберите провайдера"), ox + 10, oy + 6, 0xFFFFFF);
        renderCloseButton(context, ox + overlayW - 22, oy + 5);

        int themeColorInt = parseThemeColor(themeColor);

        for (int i = 0; i < PROVIDER_OPTIONS.length; i++) {
            int itemY = oy + 30 + i * 22;
            boolean selected = PROVIDER_CODES[i].equals(provider);

            context.fill(ox + 10, itemY, ox + overlayW - 10, itemY + 20, selected ? themeColorInt : 0xFF2D2D2D);

            int textColor = selected ? 0x000000 : 0xD2D2D2;
            context.drawText(selected ? this.textRenderer : this.textRenderer, PROVIDER_OPTIONS[i], ox + 40, itemY + 6, textColor, false);

            drawProviderIconSlot(context, getProviderIcon(PROVIDER_CODES[i]), ox + 14, itemY + 1);
        }

        context.getMatrices().pop();
    }

    private void renderLangMenu(DrawContext context) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);

        int overlayW = 200;
        int overlayH = 90;
        int ox = this.width / 2 - overlayW / 2;
        int oy = this.height / 2 - overlayH / 2;

        renderOverlayBase(context, ox, oy, overlayW, overlayH);
        context.drawTextWithShadow(this.textRenderer, t("Select Language", "Выберите язык"), ox + 10, oy + 6, 0xFFFFFF);
        renderCloseButton(context, ox + overlayW - 22, oy + 5);

        int themeColorInt = parseThemeColor(themeColor);

        for (int i = 0; i < LANG_OPTIONS.length; i++) {
            int itemY = oy + 30 + i * 22;
            boolean selected = LANG_CODES[i].equals(language);

            context.fill(ox + 10, itemY, ox + overlayW - 10, itemY + 20, selected ? themeColorInt : 0xFF2D2D2D);

            if (selected) {
                context.drawText(this.textRenderer, LANG_OPTIONS[i], ox + 16, itemY + 6, 0x000000, false);
            } else {
                context.drawTextWithShadow(this.textRenderer, LANG_OPTIONS[i], ox + 16, itemY + 6, 0xD2D2D2);
            }
        }

        context.getMatrices().pop();
    }

    private void renderModelMenu(DrawContext context, int mouseX, int mouseY, float delta) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);

        int overlayW = 260;
        int overlayH = 160;
        int ox = this.width / 2 - overlayW / 2;
        int oy = this.height / 2 - overlayH / 2;

        renderOverlayBase(context, ox, oy, overlayW, overlayH);
        context.drawTextWithShadow(this.textRenderer, t("Select Model", "Выберите модель"), ox + 10, oy + 8, 0xFFFFFF);
        renderCloseButton(context, ox + overlayW - 22, oy + 5);

        modelSearchField.render(context, mouseX, mouseY, delta);

        int listY = oy + 25;
        int listH = 120;
        int itemH = 15;
        int visibleCount = listH / itemH;
        int themeColorInt = parseThemeColor(themeColor);

        for (int i = 0; i < visibleCount; i++) {
            int index = modelScroll + i;
            if (index >= filteredModels.size()) break;

            String modelId = filteredModels.get(index);
            int itemY = listY + i * itemH;
            boolean hovered = mouseX >= ox + 10 && mouseX <= ox + overlayW - 15 && mouseY >= itemY && mouseY <= itemY + itemH;

            context.fill(ox + 10, itemY, ox + overlayW - 15, itemY + itemH, hovered ? themeColorInt : 0xFF2D2D2D);
            context.drawTextWithShadow(this.textRenderer, modelId, ox + 15, itemY + 4, hovered ? 0x000000 : 0xD2D2D2);
        }

        int maxScroll = Math.max(0, filteredModels.size() - visibleCount);
        if (maxScroll > 0) {
            int scrollBarY = listY + (int) ((modelScroll / (float) maxScroll) * (listH - 20));
            context.fill(ox + overlayW - 10, listY, ox + overlayW - 5, listY + listH, 0xFF111111);
            context.fill(ox + overlayW - 10, scrollBarY, ox + overlayW - 5, scrollBarY + 20, themeColorInt);
        }

        context.getMatrices().pop();
    }

    private void renderOverlayBase(DrawContext context, int ox, int oy, int overlayW, int overlayH) {
        context.fill(0, 0, this.width, this.height, 0x80000000);
        context.fill(ox, oy, ox + overlayW, oy + overlayH, 0xFF1E1E1E);
        context.fill(ox, oy, ox + overlayW, oy + 1, 0xFF555555);
        context.fill(ox, oy + overlayH - 1, ox + overlayW, oy + overlayH, 0xFF111111);
        context.fill(ox, oy, ox + 1, oy + overlayH, 0xFF555555);
        context.fill(ox + overlayW - 1, oy, ox + overlayW, oy + overlayH, 0xFF111111);
    }

    private void renderCloseButton(DrawContext context, int closeX, int closeY) {
        context.fill(closeX, closeY, closeX + 18, closeY + 14, 0xFF2D2D2D);
        context.drawText(this.textRenderer, "X", closeX + 6, closeY + 3, 0xFFAAAAAA, false);
    }

    private void drawProviderIconSlot(DrawContext context, Identifier icon, int iconX, int iconY) {
        context.fill(iconX, iconY, iconX + 18, iconY + 18, 0xFF181818);
        context.fill(iconX, iconY, iconX + 18, iconY + 1, 0xFF0A0A0A);
        context.fill(iconX, iconY, iconX + 1, iconY + 18, 0xFF0A0A0A);
        context.fill(iconX, iconY + 17, iconX + 18, iconY + 18, 0xFF4A4A4A);
        context.fill(iconX + 17, iconY, iconX + 18, iconY + 18, 0xFF4A4A4A);

        context.drawTexture(icon, iconX + 1, iconY + 1, 0, 0, 16, 16, 16, 16);
    }

    private void renderColorPicker(DrawContext context, int x, int y) {
        int svX = x;
        int svY = y;
        int hueX = svX + SV_SIZE + 8;
        int hueY = svY;

        for (int row = 0; row < SV_CELLS; row++) {
            for (int col = 0; col < SV_CELLS; col++) {
                float s = col / (float) SV_CELLS;
                float v = 1f - row / (float) SV_CELLS;
                int color = hsvToRgb(pickerHue, s, v);
                context.fill(svX + col * CELL_SIZE, svY + row * CELL_SIZE, svX + col * CELL_SIZE + CELL_SIZE, svY + row * CELL_SIZE + CELL_SIZE, 0xFF000000 | color);
            }
        }

        int segH = HUE_H / HUE_SEGMENTS;
        for (int i = 0; i < HUE_SEGMENTS; i++) {
            int c = hsvToRgb(i / (float) HUE_SEGMENTS, 1f, 1f);
            context.fill(hueX, hueY + i * segH, hueX + HUE_W, hueY + i * segH + segH, 0xFF000000 | c);
        }

        int previewX = hueX + HUE_W + 12;
        int previewY = hueY;
        context.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, parseThemeColor(themeColor));
    }

    private void renderMenuItem(DrawContext context, String text, int x, int y, boolean selected, int themeColor) {
        if (selected) {
            context.fill(x, y, x + 120, y + 20, themeColor);
            context.drawText(this.textRenderer, text, x + 5, y + 6, 0x000000, false);
        } else {
            context.drawTextWithShadow(this.textRenderer, text, x + 5, y + 6, 0xD2D2D2);
        }
    }

    private void updateModelScroll(double mouseY, int listY, int listH) {
        int visibleCount = listH / 15;
        int maxScroll = Math.max(0, filteredModels.size() - visibleCount);

        if (maxScroll == 0) {
            modelScroll = 0;
            return;
        }

        float trackHeight = listH - 20f;
        float deltaY = (float) (mouseY - listY - 10f);
        float fraction = Math.max(0, Math.min(1, deltaY / trackHeight));

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

    private String getProviderDisplayName(String code) {
        for (int i = 0; i < PROVIDER_CODES.length; i++) {
            if (PROVIDER_CODES[i].equals(code)) return PROVIDER_OPTIONS[i];
        }

        return "NVIDIA";
    }

    private Identifier getProviderIcon(String code) {
        return switch (code) {
            case "openai" -> OPENAI_ICON;
            case "anthropic" -> ANTHROPIC_ICON;
            case "deepseek" -> DEEPSEEK_ICON;
            case "gemini" -> GEMINI_ICON;
            case "xai" -> XAI_ICON;
            case "openrouter" -> OPENROUTER_ICON;
            default -> NVIDIA_ICON;
        };
    }

    private String getDefaultModelForProvider(String provider) {
        return switch (provider) {
            case "openai" -> "gpt-5.5";
            case "anthropic" -> "claude-sonnet-4-5";
            case "deepseek" -> "deepseek-v4-flash";
            case "gemini" -> "gemini-3.5-flash";
            case "xai" -> "grok-4.3";
            case "openrouter" -> "openai/gpt-5.2";
            default -> "meta/llama-3.1-70b-instruct";
        };
    }

    private String shortenModelName(String value) {
        if (value == null || value.isBlank()) {
            return t("Select model", "Выбрать модель");
        }

        int maxLength = 28;
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private String getOutlineButtonText() {
        return t("Outline: ", "Обводка: ") + (thickOutline ? t("Thick", "Жирная") : t("Vanilla", "Обычная"));
    }

    private String getOutlineThroughBlocksButtonText() {
        return t("Contour through blocks: ", "Контур сквозь блоки: ") + (outlineThroughBlocks ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"));
    }

    private int parseThemeColor(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xFF17B95F;
        }
    }

    private void hexToHsv(String hex) {
        try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);

            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            float rf = r / 255f;
            float gf = g / 255f;
            float bf = b / 255f;

            float max = Math.max(rf, Math.max(gf, bf));
            float min = Math.min(rf, Math.min(gf, bf));
            float d = max - min;

            pickerVal = max;
            pickerSat = max == 0 ? 0 : d / max;

            if (d == 0) {
                pickerHue = 0;
            } else if (max == rf) {
                pickerHue = ((gf - bf) / d + 6) % 6 / 6f;
            } else if (max == gf) {
                pickerHue = ((bf - rf) / d + 2) / 6f;
            } else {
                pickerHue = ((rf - gf) / d + 4) / 6f;
            }
        } catch (Exception ignored) {
            pickerHue = 0.33f;
            pickerSat = 0.85f;
            pickerVal = 0.72f;
        }
    }

    private String hsvToHex(float h, float s, float v) {
        return String.format("#%06X", hsvToRgb(h, s, v));
    }

    private int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;

        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        float r = 0;
        float g = 0;
        float b = 0;

        switch (i % 6) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            case 5 -> {
                r = v;
                g = p;
                b = q;
            }
        }

        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private class ProviderSelectButton extends FlatButton {
        public ProviderSelectButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) return;

            int bgColor = this.isHovered() ? 0xFF3D3D3D : 0xFF2D2D2D;
            int textColor = this.isHovered() ? 0xFFFFFF : 0xD2D2D2;

            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            drawProviderIconSlot(context, getProviderIcon(provider), this.getX() + 4, this.getY() + 2);

            int textY = this.getY() + (this.height - 8) / 2;
            context.drawTextWithShadow(PromptCraftSettingsScreen.this.textRenderer, this.getMessage(), this.getX() + 34, textY, textColor);
            context.drawTextWithShadow(PromptCraftSettingsScreen.this.textRenderer, "▼", this.getX() + this.width - 16, textY, textColor);
        }
    }

    private class ModelSelectButton extends FlatButton {
        public ModelSelectButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) return;

            int bgColor = this.isHovered() ? 0xFF3D3D3D : 0xFF2D2D2D;
            int textColor = this.isHovered() ? 0xFFFFFF : 0xD2D2D2;

            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            int textY = this.getY() + (this.height - 8) / 2;
            context.drawTextWithShadow(PromptCraftSettingsScreen.this.textRenderer, this.getMessage(), this.getX() + 7, textY, textColor);
            context.drawTextWithShadow(PromptCraftSettingsScreen.this.textRenderer, "▼", this.getX() + this.width - 14, textY, textColor);
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
            context.getMatrices().translate(this.getX() + 3, this.getY() + 3, 0);
            context.getMatrices().scale(0.5f, 0.5f, 1.0f);
            context.drawTexture(texture, 0, 0, 0, 0, 32, 32, 32, 32);
            context.getMatrices().pop();
        }
    }

    private class PasswordFieldWidget extends TextFieldWidget {
        public PasswordFieldWidget(net.minecraft.client.font.TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
            super(textRenderer, x, y, width, height, text);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) return;

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

            context.drawTextWithShadow(
                    PromptCraftSettingsScreen.this.textRenderer,
                    shown,
                    this.getX(),
                    this.getY() + 2,
                    this.active ? 0xE0E0E0 : 0x707070
            );

            if (this.isFocused() && this.getSelectedText().isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cursorX = this.getX() + PromptCraftSettingsScreen.this.textRenderer.getWidth(shown);
                context.fill(cursorX + 1, this.getY(), cursorX + 2, this.getY() + 11, 0xFFFFFFFF);
            }
        }
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
            if (!this.visible) return;

            int bgColor = this.isHovered() ? 0xFF3D3D3D : 0xFF2D2D2D;
            int themeColorInt = parseThemeColor(themeColor);

            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            int fillW = (int) (this.width * this.value);
            context.fill(this.getX(), this.getY(), this.getX() + fillW, this.getY() + this.height, themeColorInt);

            int textX = this.getX() + (this.width - PromptCraftSettingsScreen.this.textRenderer.getWidth(this.getMessage())) / 2;
            int textY = this.getY() + (this.height - 8) / 2;

            context.drawTextWithShadow(PromptCraftSettingsScreen.this.textRenderer, this.getMessage(), textX, textY, this.value > 0.45D ? 0x000000 : 0xFFFFFF);
        }
    }
}