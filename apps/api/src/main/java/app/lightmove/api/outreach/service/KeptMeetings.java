package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.KeptMeeting;
import app.lightmove.api.outreach.model.PersonMeeting;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The meetings outreach keeps, read by the Notetaker; it never writes one. */
@Service
@RequiredArgsConstructor
public class KeptMeetings {

    private final PersonMeetingRepository meetings;

    @Transactional(readOnly = true)
    public Optional<KeptMeeting> of(UUID workspaceId, UUID personId, UUID meetingId) {
        return meetings.findByIdAndWorkspaceIdAndPersonId(meetingId, workspaceId, personId).map(KeptMeetings::keptOf);
    }

    /** Every teammate's copy of every call with the person. */
    @Transactional(readOnly = true)
    public List<KeptMeeting> ofPerson(UUID workspaceId, UUID personId) {
        return meetings.findByWorkspaceIdAndPersonIdOrderByStartsAtAsc(workspaceId, personId).stream()
                .map(KeptMeetings::keptOf)
                .toList();
    }

    /** Whether the consultant's calendar has started another call on this call's link since: a reused room. */
    @Transactional(readOnly = true)
    public boolean linkReusedSince(UUID workspaceId, UUID userId, KeptMeeting meeting, Instant now) {
        return meeting.joinUrl() != null && meetings
                .existsByWorkspaceIdAndUserIdAndJoinUrlAndStartsAtGreaterThanAndStartsAtLessThanEqual(
                        workspaceId, userId, meeting.joinUrl(), meeting.startsAt(), now);
    }

    /** Whether an earlier call on this link is still under way on the consultant's calendar: a room in use. */
    @Transactional(readOnly = true)
    public boolean linkBusyBefore(UUID workspaceId, UUID userId, String joinUrl, Instant startsAt, Instant now) {
        return joinUrl != null && meetings.existsEarlierCallUnderWay(workspaceId, userId, joinUrl, startsAt, now);
    }

    private static KeptMeeting keptOf(PersonMeeting row) {
        return new KeptMeeting(row.getId(), row.getPersonId(), row.getTitle(), row.getStartsAt(), row.getEndsAt(),
                row.getJoinUrl(), row.getUserId(), row.getBookedByUserId());
    }
}
