package app.lightmove.api.candidate.model;

import java.util.Set;
import java.util.UUID;

/** The positions a caller may see named: the one they work from, and those they work on as staff. */
public record PositionVisibility(UUID currentProjectId, Set<UUID> staffed) {

    /** A hidden position is "another position" and its stage: no id, title, status, placement or who added them. */
    public boolean isHidden(UUID projectId) {
        return projectId != null && !projectId.equals(currentProjectId) && !staffed.contains(projectId);
    }
}
