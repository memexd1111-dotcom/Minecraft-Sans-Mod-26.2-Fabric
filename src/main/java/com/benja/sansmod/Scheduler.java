package com.benja.sansmod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Ejecuta tareas dentro de N ticks del servidor. */
public final class Scheduler {
    private record Task(long runAt, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    private Scheduler() {}

    public static void later(MinecraftServer server, int ticks, Runnable action) {
        TASKS.add(new Task(server.getTickCount() + Math.max(0, ticks), action));
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        List<Task> due = new ArrayList<>();
        for (int i = TASKS.size() - 1; i >= 0; i--) {
            if (TASKS.get(i).runAt() <= now) {
                due.add(0, TASKS.remove(i));
            }
        }
        for (Task t : due) {
            try {
                t.action().run();
            } catch (Exception e) {
                // una tarea con error no debe romper el resto
                e.printStackTrace();
            }
        }
    }

    public static void clear() {
        TASKS.clear();
    }
}
