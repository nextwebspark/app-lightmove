package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.constant.ApproachKind;
import app.lightmove.api.candidate.model.PersonTouch;
import app.lightmove.api.candidate.service.PersonApproachHistory;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outreach's answer to {@link PersonApproachHistory}. A reply is credited to the mailbox it reached, so the warning
 * names the teammate the person answered.
 */
@Service
@RequiredArgsConstructor
class OutreachApproachHistory implements PersonApproachHistory {

    private final OutreachMessageRepository messages;
    private final OutreachEnrollmentRepository enrollments;

    @Override
    @Transactional(readOnly = true)
    public List<PersonTouch> touchesOf(UUID workspaceId, Collection<UUID> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        Stream<PersonTouch> emails = messages.findTouchesOfPeople(workspaceId, personIds).stream()
                .map(row -> new PersonTouch(row.getPersonId(), row.getProjectId(), row.getSenderUserId(),
                        row.getDirection() == MessageDirection.INBOUND ? ApproachKind.REPLIED : ApproachKind.EMAILED,
                        row.getAt()));
        Stream<PersonTouch> sequences = enrollments
                .findByWorkspaceIdAndPersonIdInAndStatusIn(workspaceId, personIds, EnrollmentStatus.LIVE).stream()
                .map(run -> new PersonTouch(run.getPersonId(), run.getProjectId(), run.getSenderUserId(),
                        ApproachKind.IN_SEQUENCE, run.getEnrolledAt()));
        return Stream.concat(emails, sequences).toList();
    }
}
