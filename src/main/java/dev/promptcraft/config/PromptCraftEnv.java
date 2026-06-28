package dev.promptcraft.config;

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

    public static String getMaskedNvidiaApiKeyStatus() {
        String key = getNvidiaApiKey();
        if (key.isEmpty()) return "Not Set";
        if (key.length() <= 10) return "****";
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }
}
