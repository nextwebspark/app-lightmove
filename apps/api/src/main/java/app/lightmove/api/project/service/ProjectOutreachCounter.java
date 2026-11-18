package app.lightmove.api.project.service;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** The runs an archive would stop, implemented in {@code outreach} so the dependency stays one-way. */
@NamedInterface(name = "api", propagate = false)
public interface ProjectOutreachCounter {

    long countLiveRuns(UUID projectId);
}
