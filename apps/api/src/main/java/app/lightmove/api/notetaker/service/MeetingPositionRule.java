package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.service.CandidateMeetingNotes;
import app.lightmove.api.notetaker.model.MeetingRecording;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Which position a recorded call belongs to, decided with #952: the position it was booked on, while the consultant
 * still works it; otherwise the one live position the consultant works that maps the person; otherwise none, and
 * its note is about the person. The note is filed by it, and until then its transcript is read by it.
 */
@Component
@RequiredArgsConstructor
class MeetingPositionRule {

    private final CandidateMeetingNotes meetingNotes;

    Optional<UUID> positionOf(MeetingRecording recording) {
        if (recording.getPersonId() == null) {
            return Optional.empty();
        }
        Optional<UUID> booked = meetingNotes.positionOfBookedCall(recording.getUserId(), recording.getWorkspaceId(),
                recording.getPersonId(), recording.getStartsAt());
        if (booked.isPresent()) {
            return booked;
        }
        List<UUID> worked = meetingNotes.livePositionsWorkedBy(recording.getUserId(), recording.getWorkspaceId(),
                recording.getPersonId());
        return worked.size() == 1 ? Optional.of(worked.getFirst()) : Optional.empty();
    }
}
