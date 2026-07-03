package dev.promptcraft.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftEnv;
import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.session.GenerationSession;
import dev.promptcraft.structure.PromptCraftStructure;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(45)).build();

    private static final ExecutorService STREAM_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "PromptCraft-AI-Stream");
        t.setDaemon(true);
        return t;
    });

    private static final int ANSWER_RESERVE_TOKENS = 8192;
    private static final long REQUEST_TIMEOUT_SECONDS = 300L;

    public static CompletableFuture<PromptCraftStructure> requestBuild(
            ServerPlayerEntity player,
            String prompt,
            int width,
            int height,
            int depth,
            GenerationSession session
    ) {
        PromptCraftConfig config = PromptCraftConfigManager.get();
        String apiKey = PromptCraftEnv.getApiKey(config.provider);

        if (apiKey == null || apiKey.isEmpty()) {
            player.sendMessage(Text.literal("API Key is missing! Please use /pmenu").formatted(Formatting.RED), false);
            PromptCraftNetworking.sendAiStreamEvent(player, "error", "API key is missing.");
            return CompletableFuture.completedFuture(null);
        }

        if (session.isCancelled()) {
            return CompletableFuture.completedFuture(null);
        }

        PromptCraftNetworking.sendAiStreamEvent(player, "start", "");

        String systemPrompt = buildSystemPrompt(width, height, depth);
        String userPrompt = "Build the following, respecting ALL rules above: " + prompt;

        HttpRequest request = buildRequest(config, apiKey, systemPrompt, userPrompt);

        CompletableFuture<HttpResponse<InputStream>> httpFuture =
                HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        session.setHttpFuture(httpFuture);

        return httpFuture
                .thenApplyAsync(response -> {
                    if (session.isCancelled()) return null;

                    if (response.statusCode() != 200) {
                        String body = readAll(response.body());
                        if (!session.isCancelled()) {
                            String msg = "API Error: " + response.statusCode() + " - " + body;
                            player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                            PromptCraftNetworking.sendAiStreamEvent(player, "error", msg);
                        }
                        return null;
                    }

                    session.setActiveStream(response.body());

                    try {
                        String content = switch (config.provider) {
                            case "anthropic" -> streamAnthropic(player, response.body(), session);
                            case "gemini" -> streamGemini(player, response.body(), session);
                            default -> streamOpenAiCompatible(player, response.body(), session);
                        };

                        if (session.isCancelled()) return null;

                        if (content == null || content.isBlank()) {
                            String msg = "AI returned an empty response.";
                            player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                            PromptCraftNetworking.sendAiStreamEvent(player, "error", msg);
                            return null;
                        }

                        content = content.replace("```json", "").replace("```", "").trim();

                        return GSON.fromJson(content, PromptCraftStructure.class);
                    } catch (Exception e) {
                        if (!session.isCancelled()) {
                            String msg = "Failed to parse AI response: " + e.getMessage();
                            player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                            PromptCraftNetworking.sendAiStreamEvent(player, "error", msg);
                        }
                        return null;
                    }
                }, STREAM_EXECUTOR)
                .exceptionally(ex -> {
                    if (!session.isCancelled()) {
                        String msg = "Network error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                        player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                        PromptCraftNetworking.sendAiStreamEvent(player, "error", msg);
                    }
                    return null;
                });
    }

    // =========================================================================
    // === STREAM PARSERS
    // =========================================================================

    private static String streamOpenAiCompatible(ServerPlayerEntity player, InputStream body, GenerationSession session) throws IOException {
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while (!session.isCancelled() && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if ("[DONE]".equals(data)) break;

                try {
                    JsonObject obj = GSON.fromJson(data, JsonObject.class);
                    if (obj == null || !obj.has("choices")) continue;

                    JsonArray choices = obj.getAsJsonArray("choices");
                    if (choices.isEmpty()) continue;

                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (!choice.has("delta")) continue;
                    JsonObject delta = choice.getAsJsonObject("delta");

                    String reasoning = getStringOrNull(delta, "reasoning_content");
                    if (reasoning == null) reasoning = getStringOrNull(delta, "reasoning");
                    if (reasoning != null && !reasoning.isEmpty()) {
                        sendReasoning(player, reasoning);
                    }

                    String contentChunk = getStringOrNull(delta, "content");
                    if (contentChunk != null && !contentChunk.isEmpty()) {
                        content.append(contentChunk);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            if (!session.isCancelled()) throw e;
        }

        return content.toString();
    }

    private static String streamAnthropic(ServerPlayerEntity player, InputStream body, GenerationSession session) throws IOException {
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while (!session.isCancelled() && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;

                try {
                    JsonObject obj = GSON.fromJson(data, JsonObject.class);
                    if (obj == null || !obj.has("type")) continue;
                    String type = obj.get("type").getAsString();

                    if ("content_block_delta".equals(type) && obj.has("delta")) {
                        JsonObject delta = obj.getAsJsonObject("delta");
                        String deltaType = delta.has("type") ? delta.get("type").getAsString() : "";

                        if ("thinking_delta".equals(deltaType)) {
                            String thinking = getStringOrNull(delta, "thinking");
                            if (thinking != null) sendReasoning(player, thinking);
                        } else if ("text_delta".equals(deltaType)) {
                            String text = getStringOrNull(delta, "text");
                            if (text != null) content.append(text);
                        }
                    } else if ("error".equals(type) && obj.has("error")) {
                        JsonObject err = obj.getAsJsonObject("error");
                        String msg = err.has("message") ? err.get("message").getAsString() : "Unknown Anthropic error";
                        sendReasoning(player, "\n[Anthropic error: " + msg + "]\n");
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            if (!session.isCancelled()) throw e;
        }

        return content.toString();
    }

    private static String streamGemini(ServerPlayerEntity player, InputStream body, GenerationSession session) throws IOException {
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while (!session.isCancelled() && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;

                try {
                    JsonObject obj = GSON.fromJson(data, JsonObject.class);
                    if (obj == null || !obj.has("candidates")) continue;

                    JsonArray candidates = obj.getAsJsonArray("candidates");
                    if (candidates.isEmpty()) continue;

                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                    if (!candidate.has("content")) continue;

                    JsonObject contentObj = candidate.getAsJsonObject("content");
                    if (!contentObj.has("parts")) continue;
                    JsonArray parts = contentObj.getAsJsonArray("parts");

                    for (JsonElement partEl : parts) {
                        JsonObject part = partEl.getAsJsonObject();
                        String text = getStringOrNull(part, "text");
                        if (text == null || text.isEmpty()) continue;

                        boolean isThought = part.has("thought") && !part.get("thought").isJsonNull() && part.get("thought").getAsBoolean();
                        if (isThought) {
                            sendReasoning(player, text);
                        } else {
                            content.append(text);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            if (!session.isCancelled()) throw e;
        }

        return content.toString();
    }

    private static void sendReasoning(ServerPlayerEntity player, String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        PromptCraftNetworking.sendAiStreamEvent(player, "reasoning", chunk);
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "<unreadable body>";
        }
    }

    private static boolean isOpenAiReasoningModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase();
        return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") || m.contains("reasoning");
    }

    // =========================================================================
    // === REQUEST BUILDING
    // =========================================================================

    private static String buildSystemPrompt(int width, int height, int depth) {
        String blockList = BlockCatalog.getBlockListForPrompt();

        return "You are an expert Minecraft Java Edition architect and building assistant. " +
                "Your task is to design a structure and output it as a precise sequence of build operations in JSON format.\n\n" +

                "=== OUTPUT FORMAT ===\n" +
                "Output ONLY a single valid JSON object. Do not include any explanation, comments, or markdown code fences (no ```json). " +
                "Your entire response must be parseable as JSON.\n\n" +
                "The JSON must follow this exact schema:\n" +
                "{\"operations\":[ <operation>, <operation>, ... ]}\n\n" +
                "Each <operation> is one of the following three types:\n\n" +
                "1. \"place\" - places a single block at one position.\n" +
                "   {\"type\":\"place\",\"pos\":[x,y,z],\"block\":\"minecraft:<block_id>\"}\n\n" +
                "2. \"fill\" - fills a solid rectangular box (inclusive on both corners) with one block type.\n" +
                "   {\"type\":\"fill\",\"from\":[x1,y1,z1],\"to\":[x2,y2,z2],\"block\":\"minecraft:<block_id>\"}\n\n" +
                "3. \"hollow_box\" - creates a hollow rectangular shell (floor, ceiling, and all 4 walls, each 1 block thick) " +
                "with one block type, leaving the interior untouched.\n" +
                "   {\"type\":\"hollow_box\",\"from\":[x1,y1,z1],\"to\":[x2,y2,z2],\"block\":\"minecraft:<block_id>\"}\n\n" +

                "=== BLOCK STATES (ORIENTATION) ===\n" +
                "Some blocks require orientation/state properties to look correct. Append them in square brackets directly " +
                "after the block ID, comma-separated, no spaces:\n" +
                "\"minecraft:<block_id>[property1=value1,property2=value2]\"\n\n" +
                "Use this syntax for blocks where orientation matters, for example:\n" +
                "- Stairs: minecraft:oak_stairs[facing=north,half=bottom,shape=straight]\n" +
                "- Slabs: minecraft:oak_slab[type=bottom] or [type=top] or [type=double]\n" +
                "- Doors (TWO separate \"place\" operations required, one per vertical half, at y and y+1):\n" +
                "  {\"type\":\"place\",\"pos\":[x,y,z],\"block\":\"minecraft:oak_door[facing=north,half=lower,hinge=left]\"}\n" +
                "  {\"type\":\"place\",\"pos\":[x,y+1,z],\"block\":\"minecraft:oak_door[facing=north,half=upper,hinge=left]\"}\n" +
                "- Trapdoors: minecraft:oak_trapdoor[facing=north,half=bottom,open=false]\n" +
                "- Beds (TWO separate \"place\" operations required, head and foot, same facing, adjacent positions):\n" +
                "  {\"type\":\"place\",\"pos\":[x,y,z],\"block\":\"minecraft:red_bed[facing=north,part=foot]\"}\n" +
                "  {\"type\":\"place\",\"pos\":[x2,y,z2],\"block\":\"minecraft:red_bed[facing=north,part=head]\"}\n" +
                "- Directional utility blocks (furnace, dispenser, dropper, observer, etc.): [facing=north/south/east/west/up/down]\n\n" +
                "Do NOT specify connection-based properties for auto-connecting blocks such as fences, walls, glass panes, " +
                "iron bars, or redstone dust - Minecraft automatically calculates their shape from neighboring blocks. " +
                "Simply place the base block ID without brackets for these.\n\n" +
                "If you are not fully certain of a block's valid property names/values, omit the brackets entirely and " +
                "place the plain block ID - an imperfect default orientation is far better than an invalid state.\n\n" +

                "=== COORDINATE SYSTEM ===\n" +
                "- Origin [0,0,0] is the bottom-north-west corner of your build area.\n" +
                "- X axis = width (0 to WIDTH-1), Y axis = height (0 = floor level, increasing = upward), Z axis = depth (0 to DEPTH-1).\n" +
                "- CRITICAL: every coordinate in every \"from\", \"to\", and \"pos\" MUST satisfy 0 <= x <= " + (width - 1) +
                ", 0 <= y <= " + (height - 1) + ", 0 <= z <= " + (depth - 1) + ". Never output a coordinate outside this range " +
                "under any circumstances.\n" +
                "- The entire build area starts completely empty (air). You do not need to clear it.\n" +
                "- Operations are applied in the exact order you list them; later operations overwrite earlier ones at the same " +
                "position. Use this: build the basic shell first, then carve doors/windows by placing \"minecraft:air\" " +
                "afterward, then add details on top.\n\n" +

                "=== DESIGN GUIDELINES ===\n" +
                "Before producing the final JSON, mentally plan the structure in this order:\n" +
                "1. Foundation / floor.\n" +
                "2. Walls or overall shell shape (use \"hollow_box\" for simple rooms, or multiple \"fill\"/\"place\" operations for complex shapes).\n" +
                "3. Roof (prefer stair blocks angled realistically for sloped roofs, unless a flat roof fits the theme better).\n" +
                "4. Openings: carve doors and windows by placing \"minecraft:air\" at the appropriate positions after the shell is built.\n" +
                "5. Details: doors, windows (glass panes), interior furniture/decorations, exterior landscaping (fences, paths, " +
                "lighting via lanterns/torches/glowstone), and thematically appropriate accents.\n" +
                "6. If the user mentions a specific material (e.g. \"cherry wood\", \"deepslate\", \"copper\"), search the " +
                "available blocks list below for the closest matching family (e.g. cherry_planks, cherry_log, cherry_door, " +
                "cherry_stairs, cherry_fence) and use it consistently throughout the structure.\n\n" +
                "Keep proportions realistic and structurally sound relative to the given bounding box. Prefer variety and " +
                "visual detail over flat, empty, monotonous surfaces, but never exceed the given dimensions.\n\n" +

                "=== AVAILABLE BLOCKS ===\n" +
                "You MUST use ONLY block IDs from the list below (prefix each with \"minecraft:\" when writing them into the " +
                "JSON - the prefix is omitted below to save space). Do NOT invent, guess, or hallucinate any block ID that is " +
                "not in this list - this is the definitive, version-accurate list of every block that exists in this exact " +
                "Minecraft version. If a block name you were thinking of is not in this list, it does not exist in this " +
                "version; pick the closest available substitute instead.\n" +
                blockList + "\n\n" +

                "=== BUILD AREA ===\n" +
                "Bounding box size: width=" + width + ", height=" + height + ", depth=" + depth +
                " (coordinates range from [0,0,0] to [" + (width - 1) + "," + (height - 1) + "," + (depth - 1) + "] inclusive).";
    }

    private static HttpRequest buildRequest(PromptCraftConfig config, String apiKey, String systemPrompt, String userPrompt) {
        JsonObject payload = new JsonObject();
        String url;
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));

        boolean reasoningEnabled = "anthropic".equals(config.provider) && config.reasoningLimitEnabled;
        int reasoningBudget = Math.max(1, config.reasoningTokenLimit);

        switch (config.provider) {
            case "anthropic":
                url = "https://api.anthropic.com/v1/messages";
                requestBuilder.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");

                payload.addProperty("model", config.model);
                payload.addProperty("system", systemPrompt);
                payload.addProperty("stream", true);

                int anthropicMaxTokens = 8192;

                if (reasoningEnabled) {
                    anthropicMaxTokens = Math.max(anthropicMaxTokens, reasoningBudget + ANSWER_RESERVE_TOKENS);

                    JsonObject thinking = new JsonObject();
                    thinking.addProperty("type", "enabled");
                    thinking.addProperty("budget_tokens", reasoningBudget);
                    payload.add("thinking", thinking);
                } else {
                    payload.addProperty("temperature", 0.3);
                }

                payload.addProperty("max_tokens", anthropicMaxTokens);

                JsonObject anthropicMsg = new JsonObject();
                anthropicMsg.addProperty("role", "user");
                anthropicMsg.addProperty("content", userPrompt);
                payload.add("messages", GSON.toJsonTree(new JsonObject[]{anthropicMsg}));
                break;

            case "gemini":
                url = "https://generativelanguage.googleapis.com/v1beta/models/" + config.model + ":streamGenerateContent?alt=sse";
                requestBuilder.header("x-goog-api-key", apiKey);

                JsonObject sysPart = new JsonObject(); sysPart.addProperty("text", systemPrompt);
                JsonArray sysParts = new JsonArray(); sysParts.add(sysPart);
                JsonObject sysInst = new JsonObject(); sysInst.add("parts", sysParts);

                JsonObject usrPart = new JsonObject(); usrPart.addProperty("text", userPrompt);
                JsonArray usrParts = new JsonArray(); usrParts.add(usrPart);
                JsonObject usrContent = new JsonObject(); usrContent.addProperty("role", "user"); usrContent.add("parts", usrParts);
                JsonArray contents = new JsonArray(); contents.add(usrContent);

                JsonObject genConfig = new JsonObject(); genConfig.addProperty("temperature", 0.3);
                genConfig.addProperty("maxOutputTokens", 8192);

                JsonObject thinkingConfig = new JsonObject();
                thinkingConfig.addProperty("includeThoughts", true);
                genConfig.add("thinkingConfig", thinkingConfig);

                payload.add("systemInstruction", sysInst);
                payload.add("contents", contents);
                payload.add("generationConfig", genConfig);
                break;

            default:
                url = switch (config.provider) {
                    case "openai" -> "https://api.openai.com/v1/chat/completions";
                    case "deepseek" -> "https://api.deepseek.com/chat/completions";
                    case "openrouter" -> "https://openrouter.ai/api/v1/chat/completions";
                    case "xai" -> "https://api.x.ai/v1/chat/completions";
                    default -> "https://integrate.api.nvidia.com/v1/chat/completions";
                };

                requestBuilder.header("Authorization", "Bearer " + apiKey);

                if (config.provider.equals("openrouter")) {
                    requestBuilder.header("HTTP-Referer", "https://github.com/PromptCraft");
                    requestBuilder.header("X-OpenRouter-Title", "PromptCraft Mod");

                    payload.addProperty("include_reasoning", true);
                    JsonObject reasoningObj = new JsonObject();
                    reasoningObj.addProperty("effort", "high");
                    payload.add("reasoning", reasoningObj);
                }

                if (config.provider.equals("nvidia")) {
                    JsonObject templateKwargs = new JsonObject();
                    templateKwargs.addProperty("enable_thinking", true);
                    payload.add("chat_template_kwargs", templateKwargs);
                }

                if (config.provider.equals("xai")) {
                    payload.addProperty("reasoning_effort", "high");
                }

                boolean openAiReasoningModel = "openai".equals(config.provider) && isOpenAiReasoningModel(config.model);

                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", openAiReasoningModel ? "developer" : "system");
                sysMsg.addProperty("content", systemPrompt);

                JsonObject usrMsg = new JsonObject();
                usrMsg.addProperty("role", "user");
                usrMsg.addProperty("content", userPrompt);

                payload.addProperty("model", config.model);

                if (openAiReasoningModel) {
                    payload.addProperty("reasoning_effort", "high");
                    payload.addProperty("max_completion_tokens", 8192);
                } else {
                    payload.addProperty("temperature", 0.3);
                    payload.addProperty("max_tokens", 8192);
                }

                payload.addProperty("stream", true);
                payload.add("messages", GSON.toJsonTree(new JsonObject[]{sysMsg, usrMsg}));
                break;
        }

        return requestBuilder.uri(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
    }
}
