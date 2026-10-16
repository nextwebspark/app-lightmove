package app.uncava.api.task.repository;

/** A title the team keeps typing rather than picking, and how often. */
public interface TypedTaskTitle {

    String getLabel();

    long getUses();
}
