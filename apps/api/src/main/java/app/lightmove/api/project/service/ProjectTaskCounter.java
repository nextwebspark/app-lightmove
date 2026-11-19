package app.lightmove.api.project.service;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** The open tasks an archive would take out of My tasks, implemented in {@code task}. */
@NamedInterface(name = "api", propagate = false)
public interface ProjectTaskCounter {

    long countOpen(UUID projectId);
}
