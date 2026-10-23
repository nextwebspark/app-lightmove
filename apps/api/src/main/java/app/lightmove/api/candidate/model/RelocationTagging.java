package app.lightmove.api.candidate.model;

import java.util.UUID;

/** The tag put on someone from a call, and whether it was put there now: an undo removes only a tag it added. */
public record RelocationTagging(UUID tagId, boolean added) {}
