package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.UUID;

/** The person an erasure is deleting, as each contributor finds them: mappings read before the cascade, slug, name. */
public record ErasedPerson(UUID workspaceId, UUID personId, String fullName, String profileSlug,
                           List<UUID> candidateIds, List<UUID> projectIds, UUID erasedBy) {}
