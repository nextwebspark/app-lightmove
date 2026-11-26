package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ContactStopKind;
import app.lightmove.api.candidate.constant.PrivacyRequestKind;
import app.lightmove.api.candidate.constant.SuppressionReason;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonOptedOut;
import app.lightmove.api.candidate.model.PersonPrivacyRequested;
import app.lightmove.api.core.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one way contact with a person stops (#924, B16), whichever door they used; its announcement stops outreach in
 * the same transaction. An unreadable keyset refuses the whole stop ({@code SUPPRESSION_UNAVAILABLE}).
 */
@Service
@RequiredArgsConstructor
public class PersonContactStop {

    private static final String PERSON_TARGET = "person";

    private final SuppressionListService suppression;
    private final PersonActivityRecorder activity;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void objectedOnPrivacyPage(Person person, HttpServletRequest request) {
        stop(person, ContactStopKind.OBJECTED_ON_PRIVACY_PAGE, null, false, request,
                new PersonPrivacyRequested(person.getWorkspaceId(), person.getId(), PrivacyRequestKind.OBJECTION));
    }

    /** {@code linkSenderUserId} hears of it when nobody owns the person; a link's first use is recorded regardless. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void optedOutByLink(Person person, UUID linkSenderUserId, boolean linkFirstUsed,
                               HttpServletRequest request) {
        UUID toldUserId = person.getOwnerUserId() != null ? person.getOwnerUserId() : linkSenderUserId;
        stop(person, ContactStopKind.OPTED_OUT_BY_EMAIL, null, linkFirstUsed, request,
                new PersonOptedOut(person.getWorkspaceId(), person.getId(), toldUserId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordedByTeam(Person person, UUID actorUserId, HttpServletRequest request) {
        stop(person, ContactStopKind.RECORDED_BY_TEAM, actorUserId, false, request,
                new PersonPrivacyRequested(person.getWorkspaceId(), person.getId(), PrivacyRequestKind.OBJECTION,
                        actorUserId));
    }

    /** Recorded and announced only when something changed. */
    private void stop(Person person, ContactStopKind kind, UUID actorUserId, boolean recordRegardless,
                      HttpServletRequest request, Object announcement) {
        boolean marked = person.markObjection(kind, actorUserId, clock.instant());
        int suppressed = suppression.suppressPerson(person, actorUserId, SuppressionReason.OBJECTED);
        if (!recordRegardless && !marked && suppressed == 0) {
            return;
        }
        activity.record(person, null, actorUserId, kind.activityKind(), PersonActivityDetails.none());
        audit.event(kind.auditType())
                .actor(actorUserId).workspace(person.getWorkspaceId()).target(PERSON_TARGET, person.getId())
                .from(request)
                .detail("suppressionEntries", suppressed)
                .record();
        events.publishEvent(announcement);
    }
}
