package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ErasedHolding;
import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.core.error.constant.ErrorCode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What a person's erasure must reach that no cascade does — a send in flight, a file, a call at Recall, a copy of
 * their name — declared here and implemented where the rows live, so {@code candidate} depends on none of them.
 */
public interface PersonErasureContributor {

    /** What this feature holds about the person, for the warning. */
    default Map<ErasedHolding, Long> holdingsOf(UUID workspaceId, UUID personId) {
        return Map.of();
    }

    /** Why the person cannot be erased right now, if this feature has a reason. */
    default Optional<ErrorCode> blockerOf(UUID workspaceId, UUID personId) {
        return Optional.empty();
    }

    /**
     * Inside the erasure's transaction, before the person's row goes: refuse by throwing, or delete what no cascade
     * reaches. Work outside the database waits for the commit.
     */
    void erase(ErasedPerson person);
}
