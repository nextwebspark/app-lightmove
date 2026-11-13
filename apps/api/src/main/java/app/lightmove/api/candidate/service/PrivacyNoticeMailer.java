package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.PrivacyNoticesQueued;
import app.lightmove.api.candidate.model.QueuedPrivacyNotice;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PersonRepository people;
    private final PersonActivityRecorder activity;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(PrivacyNoticesQueued queued) {
        List<QueuedPrivacyNotice> refused = queued.notices().stream()
                .filter(notice -> !emailSender.trySend(notice.email()))
                .toList();
        if (refused.isEmpty()) {
            return;
        }
        // Its own transaction in the notices' workspace: past the pool's last thread this runs on the publishing
        // thread after its commit, where the committed transaction's connection is still bound.
        TransactionTemplate own = new TransactionTemplate(transactionManager);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        WorkspaceScope.runInJoinedTransaction(queued.workspaceId(),
                () -> own.executeWithoutResult(status -> refused.forEach(notice -> recordRefused(queued, notice))));
    }

    private void recordRefused(PrivacyNoticesQueued queued, QueuedPrivacyNotice notice) {
        jdbc.update("UPDATE app_lm_person_privacy_link SET failed_at = :now WHERE id = :id",
                new MapSqlParameterSource("id", notice.linkId()).addValue("now", Timestamp.from(clock.instant())));
        people.findByIdAndWorkspaceId(notice.personId(), queued.workspaceId()).ifPresent(person -> {
            person.withdrawRefusedNotice(notice.sentAt(), notice.previouslyNotifiedAt(), notice.previousChannel());
            activity.record(person, null, queued.senderUserId(), PersonActivityKind.PRIVACY_NOTICE_FAILED,
                    PersonActivityDetails.none());
        });
        audit.event(ProjectEventType.PERSON_PRIVACY_NOTICE_FAILED)
                .failed().actor(queued.senderUserId()).workspace(queued.workspaceId())
                .target(PERSON_TARGET, notice.personId())
                .record();
    }
}
