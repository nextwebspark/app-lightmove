package app.lightmove.api.project.service;

import java.util.UUID;

/** The open tasks an archive would take out of My tasks, implemented in {@code task}. */
public interface ProjectTaskCounter {

    long countOpen(UUID projectId);
}
