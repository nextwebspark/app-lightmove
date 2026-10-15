package app.lightmove.api.candidate.model;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Where a person's notes are read from: one position, which reads its own and those about no position, or the
 * workspace's Candidates page, which reads them all. A position's viewpoint refuses a null position, so a route
 * that loses its project fails rather than reading every note.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class NoteViewpoint {

    private static final NoteViewpoint WORKSPACE = new NoteViewpoint(null);

    private final UUID projectId;

    public static NoteViewpoint position(UUID projectId) {
        return new NoteViewpoint(Objects.requireNonNull(projectId, "a position's viewpoint names its position"));
    }

    public static NoteViewpoint workspace() {
        return WORKSPACE;
    }

    /** {@code aboutProjectId} is the position a note is about, null for one about the person. */
    public boolean reads(UUID aboutProjectId) {
        return this == WORKSPACE || aboutProjectId == null || aboutProjectId.equals(projectId);
    }
}
