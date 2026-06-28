package dev.promptcraft.task;

public interface Task {
    /**
     * @return true if the task is complete and should be removed.
     */
    boolean tick();
}