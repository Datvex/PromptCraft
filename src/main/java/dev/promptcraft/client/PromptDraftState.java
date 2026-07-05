package dev.promptcraft.client;

/**
 * Клиентское хранилище черновика текста промпта.
 * Живёт независимо от экземпляра экрана настроек, поэтому если игрок закроет
 * и снова откроет GUI — введённый текст не потеряется.
 */
public final class PromptDraftState {
    private static volatile String draft = "";

    private PromptDraftState() {
    }

    public static String get() {
        return draft;
    }

    public static void set(String text) {
        draft = text == null ? "" : text;
    }

    public static void clear() {
        draft = "";
    }
}