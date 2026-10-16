package app.uncava.api.task.service;

import app.uncava.api.task.model.Task;
import java.util.Comparator;

/** Soonest due first, an undated task last, then the oldest: the order a next action is chosen in. */
final class TaskOrder {

    static final Comparator<Task> SOONEST_FIRST = Comparator
            .comparing(Task::getDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getId);

    private TaskOrder() {
    }
}
