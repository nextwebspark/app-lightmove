package app.uncava.api.task.repository;

import java.util.UUID;

/** A workspace and a person holding open tasks in it. */
public interface TaskHolderRow {

    UUID getWorkspaceId();

    UUID getUserId();
}
