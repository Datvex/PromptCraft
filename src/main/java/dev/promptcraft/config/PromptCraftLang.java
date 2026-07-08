package dev.promptcraft.config;

public final class PromptCraftLang {
    private PromptCraftLang() {}

    public static boolean isRu() {
        return "ru".equals(PromptCraftConfigManager.get().language);
    }

    /** Возвращает ru- или en-вариант в зависимости от языка мода. */
    public static String t(String en, String ru) {
        return isRu() ? ru : en;
    }
}