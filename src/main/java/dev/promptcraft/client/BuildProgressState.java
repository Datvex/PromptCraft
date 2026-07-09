package dev.promptcraft.client;

/** Клиентское состояние прогресса постройки для оверлея. */
public final class BuildProgressState {

    private static final long LINGER_MS = 800L; // сколько показываем "100%" после завершения

    private static volatile boolean active = false;
    private static volatile int percent = 0;
    private static volatile long finishTime = 0L;

    private BuildProgressState() {}

    public static void update(int p) {
        percent = Math.max(0, Math.min(100, p));
        active = true;
        finishTime = 0L;
    }

    public static void complete() {
        percent = 100;
        finishTime = System.currentTimeMillis();
    }

    public static void hide() {
        active = false;
        percent = 0;
        finishTime = 0L;
    }

    public static boolean isVisible() {
        if (finishTime > 0L) {
            if (System.currentTimeMillis() - finishTime < LINGER_MS) return true;
            finishTime = 0L;
            active = false;
            return false;
        }
        return active;
    }

    public static int getPercent() {
        return percent;
    }
}