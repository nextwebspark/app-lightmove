package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.PrivacyNoticesQueued;
import app.lightmove.api.candidate.model.QueuedPrivacyNotice;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.email.service.EmailSender;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers committed privacy notices off the request thread, so a bulk send of hundreds never holds the response.
 * A notice the provider refuses is undone on the record: the link marked failed, the person put back to how they were
 * last told, a timeline line and a failed audit event — the Art. 14 record never claims a notice nobody received.
 */
@Component
@RequiredArgsConstructor
public class PrivacyNoticeMailer {

    private static final String PERSON_TARGET = "person";

    private final EmailSender emailSender;
    private final PrivacyEmailRefusals refusals;
    private final PersonActivityRecorder activity;
    private final AuditService audit;
    private final UncavaNoticeRecords uncavaNotice;
    private final Clock clock;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(PrivacyNoticesQueued queued) {
        Map<Boolean, List<QueuedPrivacyNotice>> byDelivery = queued.notices().stream()
                .collect(Collectors.partitioningBy(notice -> emailSender.trySend(notice.email())));
        List<UUID> delivered = byDelivery.get(true).stream().map(QueuedPrivacyNotice::personId).toList();
        List<QueuedPrivacyNotice> refused = byDelivery.get(false);
        if (!refused.isEmpty()) {
            refusals.inOwnTransaction(queued.workspaceId(),
                    () -> refused.forEach(notice -> recordRefused(queued, notice)));
        }
        uncavaNotice.recordDeliveredInOwnTransaction(queued.workspaceId(), delivered, clock.instant());
    }

    private void recordRefused(PrivacyNoticesQueued queued, QueuedPrivacyNotice notice) {
        refusals.withdraw(queued.workspaceId(), notice.linkId(), notice.personId(), notice.sentAt(),
                        notice.previouslyNotifiedAt(), notice.previousChannel())
                .ifPresent(person -> activity.record(person, null, queued.senderUserId(),
                        PersonActivityKind.PRIVACY_NOTICE_FAILED, PersonActivityDetails.none()));
        audit.event(ProjectEventType.PERSON_PRIVACY_NOTICE_FAILED)
                .failed().actor(queued.senderUserId()).workspace(queued.workspaceId())
                .target(PERSON_TARGET, notice.personId())
                .record();
    }
}
