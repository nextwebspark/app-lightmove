package app.lightmove.api.candidate.model;

import java.util.Set;
import java.util.UUID;

/**
 * Teammates a note newly mentions, published in the writing transaction. Names the note and never carries its
 * words: a notification about it says who wrote it and about whom, and nothing it says.
 */
public record PersonNoteMentioned(UUID workspaceId, UUID projectId, UUID personId, UUID candidateId, UUID noteId,
                                  UUID authorUserId, Set<UUID> mentionedUserIds) {

    public PersonNoteMentioned {
        mentionedUserIds = Set.copyOf(mentionedUserIds);
    }
}
