package dev.promptcraft.ai;

import com.google.gson.Gson;
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
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public static CompletableFuture<PromptCraftStructure> requestBuild(ServerPlayerEntity player, String prompt, int width, int height, int depth) {
        PromptCraftConfig config = PromptCraftConfigManager.get();
        String apiKey = PromptCraftEnv.getNvidiaApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            player.sendMessage(Text.literal("API Key is missing! Please use /promptsettings").formatted(Formatting.RED), false);
            return CompletableFuture.completedFuture(null);
        }

        String systemPrompt = "You are a Minecraft building assistant. Output ONLY a valid JSON object. Do not use markdown blocks like ```json. " +
                "The JSON must have this structure: {\"operations\":[{\"type\":\"fill\",\"from\":[0,0,0],\"to\":[5,5,5],\"block\":\"minecraft:stone\"}, {\"type\":\"place\",\"pos\":[1,1,1],\"block\":\"minecraft:oak_door\"}, {\"type\":\"hollow_box\",\"from\":[0,0,0],\"to\":[5,5,5],\"block\":\"minecraft:oak_planks\"}]}. " +
                "The building must fit exactly within a bounding box of width " + width + ", height " + height + ", and depth " + depth + ". " +
                "Use available Minecraft 1.20 Java Edition blocks. Coordinates are relative integers, from [0,0,0] to [" + (width - 1) + "," + (height - 1) + "," + (depth - 1) + "].";

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);

        JsonObject usrMsg = new JsonObject();
        usrMsg.addProperty("role", "user");
        usrMsg.addProperty("content", "Build: " + prompt);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", config.model);
        payload.addProperty("temperature", 0.3);
        payload.add("messages", GSON.toJsonTree(new JsonObject[]{sysMsg, usrMsg}));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        player.sendMessage(Text.literal("API Error: " + response.statusCode() + " - " + response.body()).formatted(Formatting.RED), false);
                        return null;
                    }
                    try {
                        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
                        String content = root.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                        
                        // Strip markdown formatting if AI disobeys
                        content = content.replace("```json", "").replace("```", "").trim();
                        
                        return GSON.fromJson(content, PromptCraftStructure.class);
                    } catch (Exception e) {
                        player.sendMessage(Text.literal("Failed to parse AI response: " + e.getMessage()).formatted(Formatting.RED), false);
                        return null;
                    }
                });
    }
}