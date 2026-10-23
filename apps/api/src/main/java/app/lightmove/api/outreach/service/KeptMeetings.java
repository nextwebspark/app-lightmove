package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.KeptMeeting;
import app.lightmove.api.outreach.model.PersonMeeting;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
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

    private static KeptMeeting keptOf(PersonMeeting row) {
        return new KeptMeeting(row.getId(), row.getPersonId(), row.getTitle(), row.getStartsAt(), row.getEndsAt(),
                row.getJoinUrl());
    }
}
