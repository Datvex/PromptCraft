package dev.promptcraft.task;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TaskManager {
    private static final List<Task> TASKS = new LinkedList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Task> iterator = TASKS.iterator();
            while (iterator.hasNext()) {
                Task task = iterator.next();
                if (task.tick()) {
                    iterator.remove();
                }
            }
        });
    }

    public static void addTask(Task task) {
        TASKS.add(task);
    }
}