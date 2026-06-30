package dev.promptcraft.config;

import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class PromptCraftEnv {
    private static final String[] PROVIDERS = {"nvidia", "openai", "anthropic", "deepseek", "gemini", "xai", "openrouter"};

    public static String getApiKey(String provider) {
        try {
            Properties props = new Properties();
            if (Files.exists(PromptCraftConfigManager.getEnvPath())) {
                props.load(Files.newInputStream(PromptCraftConfigManager.getEnvPath()));
            }
            return props.getProperty(provider.toUpperCase() + "_API_KEY", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static Map<String, String> getAllApiKeys() {
        Map<String, String> keys = new HashMap<>();
        for (String p : PROVIDERS) {
            keys.put(p, getApiKey(p));
        }
        return keys;
    }

    public static void saveApiKeys(Map<String, String> keys) {
        try {
            Properties props = new Properties();
            if (Files.exists(PromptCraftConfigManager.getEnvPath())) {
                props.load(Files.newInputStream(PromptCraftConfigManager.getEnvPath()));
            }
            for (Map.Entry<String, String> entry : keys.entrySet()) {
                props.setProperty(entry.getKey().toUpperCase() + "_API_KEY", entry.getValue());
            }
            try (OutputStream out = Files.newOutputStream(PromptCraftConfigManager.getEnvPath())) {
                props.store(out, "PromptCraft API secrets");
            }
        } catch (Exception ignored) {}
    }
}