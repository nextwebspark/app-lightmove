package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.LoggedRecording;
import app.lightmove.api.candidate.service.PastRecordingLookup;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Answers {@code candidate}'s question about a recording a note is logged against: this person's, here, and begun. */
@Component
@RequiredArgsConstructor
class PastRecordingLookupAdapter implements PastRecordingLookup {

    private final MeetingRecordingRepository recordings;

    @Override
    public Optional<LoggedRecording> pastRecordingOf(UUID workspaceId, UUID projectId, UUID personId,
                                                     UUID recordingId, Instant now) {
        return recordings.findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId, personId)
                .filter(recording -> !recording.getStartsAt().isAfter(now))
                .map(recording -> new LoggedRecording(recording.getId(), recording.getStartsAt(),
                        recording.getTitle()));
    }
}
