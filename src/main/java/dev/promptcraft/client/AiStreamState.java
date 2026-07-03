package dev.promptcraft.client;

/**
 * Клиентское хранилище состояния текущей AI-генерации.
 * Живёт независимо от экземпляра экрана настроек, поэтому если игрок закроет
 * и снова откроет GUI во время генерации — текст рассуждений не потеряется.
 */
public final class AiStreamState {
    private static final StringBuilder REASONING = new StringBuilder();
    private static volatile boolean generating = false;
    private static volatile String lastError = null;
    private static volatile String lastNotice = null;
    private static volatile long version = 0L;

    private AiStreamState() {
    }

    public static synchronized void reset() {
        REASONING.setLength(0);
        generating = true;
        lastError = null;
        lastNotice = null;
        version++;
    }

    public static synchronized void append(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        REASONING.append(chunk);
        version++;
    }

    public static void finish() {
        generating = false;
        version++;
    }

    public static void fail(String error) {
        generating = false;
        lastError = error;
        version++;
    }

    public static void cancelled() {
        generating = false;
        lastError = null;
        lastNotice = "cancelled";
        version++;
    }

    public static synchronized String getReasoningText() {
        return REASONING.toString();
    }

    public static boolean isGenerating() {
        return generating;
    }

    public static String getLastError() {
        return lastError;
    }

    public static String getLastNotice() {
        return lastNotice;
    }

    public static long getVersion() {
        return version;
    }
}
