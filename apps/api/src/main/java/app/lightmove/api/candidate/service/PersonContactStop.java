package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ContactStopKind;
import app.lightmove.api.candidate.constant.PrivacyRequestKind;
import app.lightmove.api.candidate.constant.SuppressionReason;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonOptedOut;
import app.lightmove.api.candidate.model.PersonPrivacyRequested;
import app.lightmove.api.core.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one way contact with a person stops (#924, B16), whichever door they used: do not contact, the suppression list
 * as an objection, a timeline line and an audit event, and the announcement on which outreach stops every run and
 * scheduled email in this same transaction ({@code OutreachOptOutStop}). A keyset that cannot be read refuses the
 * whole stop ({@code SUPPRESSION_UNAVAILABLE}), so nothing half-happens.
 */
@Service
@RequiredArgsConstructor
public class PersonContactStop {

    private static final String PERSON_TARGET = "person";

    private final SuppressionListService suppression;
    private final PersonActivityRecorder activity;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /**
     * Recorded and announced only when something changed. {@code actorUserId} is the colleague who recorded it, null
     * when the person acted themselves; {@code linkSenderUserId} is who sent the email whose stop link was used.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean stop(Person person, ContactStopKind kind, UUID actorUserId, UUID linkSenderUserId,
                        boolean linkFirstUsed, HttpServletRequest request) {
        boolean marked = person.markObjection(kind.doNotContactReason(), actorUserId);
        int suppressed = suppression.suppressPerson(person, actorUserId, SuppressionReason.OBJECTED);
        if (!linkFirstUsed && !marked && suppressed == 0) {
            return false;
        }
        activity.record(person, null, actorUserId, kind.activityKind(), PersonActivityDetails.none());
        audit.event(kind.auditType())
                .actor(actorUserId).workspace(person.getWorkspaceId()).target(PERSON_TARGET, person.getId())
                .from(request)
                .detail("suppressionEntries", suppressed)
                .record();
        events.publishEvent(announcementOf(person, kind, actorUserId, linkSenderUserId));
        return true;
    }

    private static Object announcementOf(Person person, ContactStopKind kind, UUID actorUserId,
                                         UUID linkSenderUserId) {
        if (kind == ContactStopKind.OPTED_OUT_BY_EMAIL) {
            return new PersonOptedOut(person.getWorkspaceId(), person.getId(),
                    person.getOwnerUserId() != null ? person.getOwnerUserId() : linkSenderUserId);
        }
        return new PersonPrivacyRequested(person.getWorkspaceId(), person.getId(), PrivacyRequestKind.OBJECTION,
                actorUserId);
    }
}
