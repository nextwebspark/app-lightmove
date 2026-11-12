package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The person a subject access export is about, with the mappings that carry their position-level rows. */
public record SubjectAccessSubject(UUID workspaceId, UUID personId, List<UUID> candidateIds) {

    /** {@code :workspaceId}, {@code :personId} and {@code :candidateIds} (an array) for a contributor's query. */
    public Map<String, Object> parameters() {
        return Map.of("workspaceId", workspaceId, "personId", personId,
                "candidateIds", candidateIds.toArray(UUID[]::new));
    }
}
