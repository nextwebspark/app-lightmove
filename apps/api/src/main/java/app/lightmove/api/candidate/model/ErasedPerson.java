package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** The person an erasure is deleting, as each contributor finds them: mappings read before the cascade, slug, name. */
@NamedInterface(name = "api", propagate = false)
public record ErasedPerson(UUID workspaceId, UUID personId, String fullName, String profileSlug,
                           List<UUID> candidateIds, List<UUID> projectIds, UUID erasedBy) {}
