package app.lightmove.api.notetaker.model;

import java.util.UUID;

/**
 * A recorded call's Meeting note is filed. Published inside the transaction that filed it; {@code projectId} and
 * {@code candidateId} are null for a note about the person.
 */
public record MeetingNotesFiled(UUID workspaceId, UUID userId, UUID projectId, UUID personId, UUID candidateId,
                                UUID noteId, UUID recordingId) {}
