package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.ConsentConfirmationQueued;
import app.lightmove.api.candidate.model.ConsentRequestsQueued;
import app.lightmove.api.candidate.model.QueuedConsentRequest;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.email.service.EmailSender;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers committed consent requests (#1267) and answer confirmations off the request thread. A request the provider
 * refuses is undone as a refused notice is, and each purpose it reopened is put back as it stood.
 */
@Component
@RequiredArgsConstructor
public class ConsentRequestMailer {

    private static final String PERSON_TARGET = "person";

    private final EmailSender emailSender;
    private final PrivacyEmailRefusals refusals;
    private final PersonConsentStore consents;
    private final PersonActivityRecorder activity;
    private final AuditService audit;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(ConsentRequestsQueued queued) {
        List<QueuedConsentRequest> refused = queued.requests().stream()
                .filter(request -> !emailSender.trySend(request.email()))
                .toList();
        if (refused.isEmpty()) {
            return;
        }
        refusals.inOwnTransaction(queued.workspaceId(),
                () -> refused.forEach(request -> recordRefused(queued, request)));
    }

    /** A refused confirmation undoes nothing: the answer stands, and the person can still write to the firm. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmation(ConsentConfirmationQueued confirmation) {
        emailSender.trySend(confirmation.email());
    }

    private void recordRefused(ConsentRequestsQueued queued, QueuedConsentRequest request) {
        request.priorStates().forEach(prior -> consents.restore(prior, request.linkId(), request.sentAt()));
        refusals.withdraw(queued.workspaceId(), request.linkId(), request.personId(), request.sentAt(),
                        request.previouslyNotifiedAt(), request.previousChannel())
                .ifPresent(person -> activity.record(person, null, queued.senderUserId(),
                        PersonActivityKind.CONSENT_REQUEST_FAILED, PersonActivityDetails.none()));
        audit.event(ProjectEventType.PERSON_CONSENT_REQUEST_FAILED)
                .failed().actor(queued.senderUserId()).workspace(queued.workspaceId())
                .target(PERSON_TARGET, request.personId())
                .record();
    }
}
