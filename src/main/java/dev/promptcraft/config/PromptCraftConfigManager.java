package dev.promptcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.promptcraft.PromptCraftMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PromptCraftConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("promptcraft");

    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.json");
    private static final Path ENV_PATH = CONFIG_DIR.resolve(".env");

    private static PromptCraftConfig config = new PromptCraftConfig();

    private PromptCraftConfigManager() {
    }

    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (!Files.exists(CONFIG_PATH)) {
                saveDefaultConfig();
            }

            if (!Files.exists(ENV_PATH)) {
                saveDefaultEnv();
            }

            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            config = GSON.fromJson(json, PromptCraftConfig.class);

            if (config == null) {
                config = new PromptCraftConfig();
                save();
            }

            PromptCraftMod.LOGGER.info("PromptCraft config loaded from {}", CONFIG_PATH);
        } catch (Exception e) {
            PromptCraftMod.LOGGER.error("Failed to load PromptCraft config. Using defaults.", e);
            config = new PromptCraftConfig();
        }
    }

    public static void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            Files.writeString(CONFIG_PATH, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            PromptCraftMod.LOGGER.error("Failed to save PromptCraft config.", e);
        }
    }

    private static void saveDefaultConfig() throws IOException {
        Files.writeString(CONFIG_PATH, GSON.toJson(new PromptCraftConfig()), StandardCharsets.UTF_8);
    }

    private static void saveDefaultEnv() throws IOException {
        String content = """
                # PromptCraft API secrets
                # Put your NVIDIA API key here.
                # Example:
                # NVIDIA_API_KEY=nvapi-your-key-here
                NVIDIA_API_KEY=
                """;
        Files.writeString(ENV_PATH, content, StandardCharsets.UTF_8);
    }

    public static PromptCraftConfig get() {
        return config;
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    public static Path getEnvPath() {
        return ENV_PATH;
    }
}
