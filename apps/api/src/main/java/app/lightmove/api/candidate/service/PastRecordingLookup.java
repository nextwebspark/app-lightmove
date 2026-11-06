package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.LoggedRecording;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A person's Notetaker recording on a position, of a call already begun, for notes logged about it. Answered by
 * {@code notetaker}, which already depends on {@code candidate}, so the dependency stays one-way.
 */
public interface PastRecordingLookup {

    Optional<LoggedRecording> pastRecordingOf(UUID workspaceId, UUID projectId, UUID personId, UUID recordingId,
                                              Instant now);
}
