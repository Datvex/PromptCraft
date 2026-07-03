package dev.promptcraft.session;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenerationSession {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile boolean destructionInProgress = false;
    private volatile boolean destructionComplete = false;
    private volatile boolean buildInProgress = false;

    private volatile CompletableFuture<?> httpFuture;
    private volatile Closeable activeStream;

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    public void markDestructionStarted() {
        destructionInProgress = true;
    }

    public void markDestructionComplete() {
        destructionInProgress = false;
        destructionComplete = true;
    }

    public boolean isDestructionInProgress() {
        return destructionInProgress;
    }

    public boolean isDestructionComplete() {
        return destructionComplete;
    }

    public void markBuildStarted() {
        buildInProgress = true;
    }

    public void markBuildFinished() {
        buildInProgress = false;
    }

    public boolean isBuildInProgress() {
        return buildInProgress;
    }

    public void setHttpFuture(CompletableFuture<?> future) {
        this.httpFuture = future;
    }

    public void setActiveStream(Closeable stream) {
        this.activeStream = stream;
    }

    public void abortHttpRequest() {
        CompletableFuture<?> future = this.httpFuture;
        if (future != null) {
            future.cancel(true);
        }

        Closeable stream = this.activeStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
    }
}
