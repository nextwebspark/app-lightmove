package app.uncava.api.project.service;

import java.util.UUID;

/** The runs an archive would stop, implemented in {@code outreach} so the dependency stays one-way. */
public interface ProjectOutreachCounter {

    long countLiveRuns(UUID projectId);
}
