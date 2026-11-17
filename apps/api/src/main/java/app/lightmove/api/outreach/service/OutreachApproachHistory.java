package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.constant.ApproachKind;
import app.lightmove.api.candidate.model.PersonTouch;
import app.lightmove.api.candidate.service.PersonApproachHistory;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
    public List<PersonTouch> touchesSince(UUID workspaceId, Collection<UUID> personIds, Instant since) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        Stream<PersonTouch> emails = messages.findTouchesOfPeople(workspaceId, personIds, since).stream()
                .map(row -> new PersonTouch(row.getPersonId(), row.getProjectId(), row.getSenderUserId(),
                        kindOf(row.getDirection()), row.getAt(), null));
        Stream<PersonTouch> sequences = enrollments
                .findByWorkspaceIdAndPersonIdInAndStatusIn(workspaceId, personIds, EnrollmentStatus.LIVE).stream()
                .map(run -> new PersonTouch(run.getPersonId(), run.getProjectId(), run.getSenderUserId(),
                        ApproachKind.IN_SEQUENCE, run.getEnrolledAt(), null));
        return Stream.concat(emails, sequences).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonTouch> latestTouchesBefore(UUID workspaceId, Collection<UUID> personIds, Instant since) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        return messages.findLatestTouchesBefore(workspaceId, personIds, since).stream()
                .map(message -> new PersonTouch(message.getPersonId(), message.getProjectId(),
                        message.getSenderUserId(), kindOf(message.getDirection()),
                        Stream.of(message.getSentAt(), message.getSendAt(), message.getOccurredAt())
                                .filter(Objects::nonNull).findFirst().orElse(null), null))
                .toList();
    }

    private static ApproachKind kindOf(MessageDirection direction) {
        return direction == MessageDirection.INBOUND ? ApproachKind.REPLIED : ApproachKind.EMAILED;
    }
}
