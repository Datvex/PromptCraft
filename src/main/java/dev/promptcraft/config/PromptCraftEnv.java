package dev.promptcraft.config;

import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

public class PromptCraftEnv {
    public static String getNvidiaApiKey() {
        try {
            Properties props = new Properties();
            props.load(Files.newInputStream(PromptCraftConfigManager.getEnvPath()));
            return props.getProperty("NVIDIA_API_KEY", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static void saveNvidiaApiKey(String key) {
        try {
            Properties props = new Properties();
            if (Files.exists(PromptCraftConfigManager.getEnvPath())) {
                props.load(Files.newInputStream(PromptCraftConfigManager.getEnvPath()));
            }
            props.setProperty("NVIDIA_API_KEY", key);
            try (OutputStream out = Files.newOutputStream(PromptCraftConfigManager.getEnvPath())) {
                props.store(out, "PromptCraft API secrets");
            }
        } catch (Exception ignored) {}
    }

    public static String getMaskedNvidiaApiKeyStatus() {
        String key = getNvidiaApiKey();
        if (key.isEmpty()) return "Not Set";
        if (key.length() <= 10) return "****";
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }
}
