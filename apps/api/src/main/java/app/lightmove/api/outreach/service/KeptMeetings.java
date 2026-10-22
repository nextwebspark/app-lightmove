package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.KeptMeeting;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The meetings outreach keeps, read by {@code notetaker}, which joins their calls; nothing here writes. */
@Service
@RequiredArgsConstructor
public class KeptMeetings {

    private final PersonMeetingRepository meetings;

    /** Across every workspace: the scheduler's one scan. Only calls with a link to join. */
    @Transactional(readOnly = true)
    public List<KeptMeeting> startingBetween(Instant from, Instant to) {
        return meetings.findByStartsAtGreaterThanEqualAndStartsAtLessThanAndJoinUrlIsNotNull(from, to).stream()
                .map(KeptMeeting::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<KeptMeeting> find(UUID workspaceId, UUID meetingId) {
        return meetings.findById(meetingId)
                .filter(row -> row.getWorkspaceId().equals(workspaceId))
                .map(KeptMeeting::of);
    }

    @Transactional(readOnly = true)
    public List<KeptMeeting> ofPerson(UUID workspaceId, UUID personId) {
        return meetings.findByWorkspaceIdAndPersonIdOrderByStartsAtAsc(workspaceId, personId).stream()
                .map(KeptMeeting::of)
                .toList();
    }

    /** Every calendar's row of the same call: the same link at the same time, teammates' copies included. */
    @Transactional(readOnly = true)
    public List<KeptMeeting> sameCallAs(KeptMeeting meeting) {
        if (meeting.joinUrl() == null) {
            return List.of(meeting);
        }
        return meetings.findByWorkspaceIdAndJoinUrlAndStartsAt(meeting.workspaceId(), meeting.joinUrl(),
                        meeting.startsAt()).stream()
                .map(KeptMeeting::of)
                .toList();
    }
}
