package dev.promptcraft.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftEnv;
import dev.promptcraft.structure.PromptCraftStructure;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AiClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(45)).build();

    public static CompletableFuture<PromptCraftStructure> requestBuild(ServerPlayerEntity player, String prompt, int width, int height, int depth) {
        PromptCraftConfig config = PromptCraftConfigManager.get();
        String apiKey = PromptCraftEnv.getNvidiaApiKey(); // Используем то же поле ключа для всех

        if (apiKey == null || apiKey.isEmpty()) {
            player.sendMessage(Text.literal("API Key is missing! Please use /psettings").formatted(Formatting.RED), false);
            return CompletableFuture.completedFuture(null);
        }

        String systemPrompt = "You are a Minecraft building assistant. Output ONLY a valid JSON object. Do not use markdown blocks like ```json. " +
                "The JSON must have this structure: {\"operations\":[{\"type\":\"fill\",\"from\":[0,0,0],\"to\":[5,5,5],\"block\":\"minecraft:stone\"}, {\"type\":\"place\",\"pos\":[1,1,1],\"block\":\"minecraft:oak_door\"}, {\"type\":\"hollow_box\",\"from\":[0,0,0],\"to\":[5,5,5],\"block\":\"minecraft:oak_planks\"}]}. " +
                "The building must fit exactly within a bounding box of width " + width + ", height " + height + ", and depth " + depth + ". " +
                "Use available Minecraft 1.20 Java Edition blocks. Coordinates are relative integers, from [0,0,0] to [" + (width - 1) + "," + (height - 1) + "," + (depth - 1) + "].";

        String userPrompt = "Build: " + prompt;
        HttpRequest request = buildRequest(config, apiKey, systemPrompt, userPrompt);

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        player.sendMessage(Text.literal("API Error: " + response.statusCode() + " - " + response.body()).formatted(Formatting.RED), false);
                        return null;
                    }
                    try {
                        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
                        String content = extractContent(config.provider, root);
                        
                        // Очистка от маркдауна, если ИИ всё же его добавил
                        content = content.replace("```json", "").replace("```", "").trim();
                        
                        return GSON.fromJson(content, PromptCraftStructure.class);
                    } catch (Exception e) {
                        player.sendMessage(Text.literal("Failed to parse AI response: " + e.getMessage()).formatted(Formatting.RED), false);
                        return null;
                    }
                });
    }

    private static HttpRequest buildRequest(PromptCraftConfig config, String apiKey, String systemPrompt, String userPrompt) {
        JsonObject payload = new JsonObject();
        String url;
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().header("Content-Type", "application/json");

        switch (config.provider) {
            case "anthropic":
                url = "https://api.anthropic.com/v1/messages";
                requestBuilder.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
                
                payload.addProperty("model", config.model);
                payload.addProperty("max_tokens", 4096);
                payload.addProperty("temperature", 0.3);
                payload.addProperty("system", systemPrompt);
                
                JsonObject anthropicMsg = new JsonObject();
                anthropicMsg.addProperty("role", "user");
                anthropicMsg.addProperty("content", userPrompt);
                payload.add("messages", GSON.toJsonTree(new JsonObject[]{anthropicMsg}));
                break;

            case "gemini":
                url = "https://generativelanguage.googleapis.com/v1beta/models/" + config.model + ":generateContent";
                requestBuilder.header("x-goog-api-key", apiKey);

                JsonObject sysPart = new JsonObject(); sysPart.addProperty("text", systemPrompt);
                JsonArray sysParts = new JsonArray(); sysParts.add(sysPart);
                JsonObject sysInst = new JsonObject(); sysInst.add("parts", sysParts);

                JsonObject usrPart = new JsonObject(); usrPart.addProperty("text", userPrompt);
                JsonArray usrParts = new JsonArray(); usrParts.add(usrPart);
                JsonObject usrContent = new JsonObject(); usrContent.addProperty("role", "user"); usrContent.add("parts", usrParts);
                JsonArray contents = new JsonArray(); contents.add(usrContent);

                JsonObject genConfig = new JsonObject(); genConfig.addProperty("temperature", 0.3);

                payload.add("systemInstruction", sysInst);
                payload.add("contents", contents);
                payload.add("generationConfig", genConfig);
                break;

            default: // OpenAI-совместимые (NVIDIA, OpenAI, DeepSeek, OpenRouter, xAI)
                url = switch (config.provider) {
                    case "openai" -> "https://api.openai.com/v1/chat/completions";
                    case "deepseek" -> "https://api.deepseek.com/chat/completions";
                    case "openrouter" -> "https://openrouter.ai/api/v1/chat/completions";
                    case "xai" -> "https://api.x.ai/v1/chat/completions";
                    default -> "https://integrate.api.nvidia.com/v1/chat/completions";
                };
                
                requestBuilder.header("Authorization", "Bearer " + apiKey);
                
                // Для OpenRouter полезно передавать заголовки приложения
                if (config.provider.equals("openrouter")) {
                    requestBuilder.header("HTTP-Referer", "https://github.com/PromptCraft");
                    requestBuilder.header("X-OpenRouter-Title", "PromptCraft Mod");
                }

                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);

                JsonObject usrMsg = new JsonObject();
                usrMsg.addProperty("role", "user");
                usrMsg.addProperty("content", userPrompt);

                payload.addProperty("model", config.model);
                payload.addProperty("temperature", 0.3);
                payload.add("messages", GSON.toJsonTree(new JsonObject[]{sysMsg, usrMsg}));
                break;
        }

        return requestBuilder.uri(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
    }

    private static String extractContent(String provider, JsonObject root) {
        if ("anthropic".equals(provider)) {
            return root.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
        } else if ("gemini".equals(provider)) {
            return root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
        } else {
            // OpenAI-совместимый ответ
            return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        }
    }
}