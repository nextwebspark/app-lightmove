package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.LoggedMeeting;
import app.lightmove.api.candidate.service.PastMeetingLookup;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Answers {@code candidate}'s question about a meeting a note is logged against: this person's, and begun. */
@Component
@RequiredArgsConstructor
class PastMeetingLookupAdapter implements PastMeetingLookup {

    private final PersonMeetingRepository meetings;

    @Override
    public Optional<LoggedMeeting> pastMeetingOf(UUID workspaceId, UUID personId, UUID meetingId, Instant now) {
        return meetings.findByIdAndWorkspaceIdAndPersonId(meetingId, workspaceId, personId)
                .filter(meeting -> !meeting.getStartsAt().isAfter(now))
                .map(meeting -> new LoggedMeeting(meeting.getId(), meeting.getStartsAt(), meeting.getTitle()));
    }
}
