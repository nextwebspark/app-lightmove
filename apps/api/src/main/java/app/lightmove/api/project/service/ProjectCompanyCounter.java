package app.lightmove.api.project.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** The projects list's "Companies" number, implemented in {@code triagecompany} so the dependency stays one-way. */
@NamedInterface(name = "api", propagate = false)
public interface ProjectCompanyCounter {

    /** A mandate holding none is absent from the map rather than zero. */
    Map<UUID, Long> countByProject(Collection<UUID> projectIds);
}
