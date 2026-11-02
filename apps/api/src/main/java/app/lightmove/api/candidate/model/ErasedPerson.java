package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.UUID;

/**
 * The person an erasure is deleting, as every feature holding something about them needs to find it: their mappings
 * and positions, read before the cascade takes them; the profile slug the vendor caches key on; and the name, which
 * the few places that kept a copy of it are scrubbed of.
 */
public record ErasedPerson(UUID workspaceId, UUID personId, String fullName, String profileSlug,
                           List<UUID> candidateIds, List<UUID> projectIds, UUID erasedBy) {}
