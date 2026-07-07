package dev.promptcraft.task;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TaskManager {
    private static final List<Task> TASKS = new ArrayList<>();

    // Отложенная очередь. Задачи, добавленные во время tick() (напр. BuildTask
    // из onComplete у DestructionTask), попадают сюда, а не напрямую в TASKS.
    private static final ConcurrentLinkedQueue<Task> PENDING = new ConcurrentLinkedQueue<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Вливаем отложенные задачи ДО итерации, а не во время неё.
            Task pending;
            while ((pending = PENDING.poll()) != null) {
                TASKS.add(pending);
            }

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
        // НИКОГДА не добавляем в TASKS напрямую: tick() задачи может вызвать
        // addTask, и прямая модификация ломала итератор -> CME -> краш сервера.
        PENDING.add(task);
    }
}