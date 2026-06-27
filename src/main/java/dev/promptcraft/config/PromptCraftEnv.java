package dev.promptcraft.config;

import dev.promptcraft.PromptCraftMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

public final class PromptCraftEnv {
    private PromptCraftEnv() {
    }

    public static Optional<String> getNvidiaApiKey() {
        String systemEnv = System.getenv("NVIDIA_API_KEY");

        if (systemEnv != null && !systemEnv.isBlank()) {
            return Optional.of(systemEnv.trim());
        }

        try {
            if (!Files.exists(PromptCraftConfigManager.getEnvPath())) {
                return Optional.empty();
            }

            for (String line : Files.readAllLines(PromptCraftConfigManager.getEnvPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                if (trimmed.startsWith("NVIDIA_API_KEY=")) {
                    String value = trimmed.substring("NVIDIA_API_KEY=".length()).trim();

                    if (!value.isBlank()) {
                        return Optional.of(value);
                    }
                }
            }
        } catch (IOException e) {
            PromptCraftMod.LOGGER.error("Failed to read PromptCraft .env file.", e);
        }

        return Optional.empty();
    }

    public static String getMaskedNvidiaApiKeyStatus() {
        Optional<String> key = getNvidiaApiKey();

        if (key.isEmpty()) {
            return "not configured";
        }

        String value = key.get();

        if (value.length() <= 8) {
            return "configured";
        }

        return "configured (...%s)".formatted(value.substring(value.length() - 4));
    }
}
