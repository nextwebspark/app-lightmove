package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.SuppressionReason;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.dto.ProcessingRestrictionRequest;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonProcessingRestricted;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restriction of processing (GDPR Art. 18): anyone on the team restricts a person; only whoever restricted them, or a
 * {@code WORKSPACE_MANAGE} holder, lifts it, since a restriction usually records a request someone is answering.
 */
@Service
@RequiredArgsConstructor
public class ProcessingRestrictionService {

    private static final String PERSON_TARGET = "person";

    private final PersonRepository people;
    private final PersonRecordService records;
    private final PersonActivityRecorder activity;
    private final SuppressionListService suppression;
    private final WorkspaceAccess workspaceAccess;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /**
     * Restricting needs a reason, and may also put the person on the suppression list as an objection; lifting leaves
     * any such entry where it is, since an objection is its own decision.
     */
    @Transactional
    public PersonRecordResponse restrictProcessing(UUID userId, UUID workspaceId, UUID personId,
                                                   ProcessingRestrictionRequest request,
                                                   HttpServletRequest httpRequest) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        boolean restricting = request.restricted();
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        if (restricting && reason == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "reason", "Say why processing is restricted");
        }
        if (!restricting && person.isProcessingRestricted() && !mayLift(userId, workspaceId, person)) {
            throw ApiException.of(ErrorCode.PERSON_PROCESSING_RESTRICTION_NOT_YOURS);
        }
        boolean changed = restricting ? person.restrictProcessing(reason, userId)
                : person.liftProcessingRestriction();
        boolean suppressing = restricting && Boolean.TRUE.equals(request.suppress());
        int suppressed = suppressing ? suppression.suppressPerson(person, userId, SuppressionReason.OBJECTED) : 0;
        if (changed) {
            activity.record(person, null, userId, restricting ? PersonActivityKind.PROCESSING_RESTRICTED
                    : PersonActivityKind.PROCESSING_RESTRICTION_LIFTED, PersonActivityDetails.none());
            audit.event(restricting ? ProjectEventType.PERSON_PROCESSING_RESTRICTED
                            : ProjectEventType.PERSON_PROCESSING_RESTRICTION_LIFTED)
                    .actor(userId).workspace(workspaceId).target(PERSON_TARGET, personId).from(httpRequest)
                    .detailIfPresent("suppressionEntries", restricting ? suppressed : null)
                    .record();
            if (restricting) {
                events.publishEvent(new PersonProcessingRestricted(workspaceId, personId, userId));
            }
        } else if (suppressing) {
            audit.event(ProjectEventType.PERSON_SUPPRESSION_ADDED)
                    .actor(userId).workspace(workspaceId).target(PERSON_TARGET, personId).from(httpRequest)
                    .detail("reason", SuppressionReason.OBJECTED.value())
                    .detail("added", suppressed)
                    .record();
        }
        return records.toRecord(userId, person);
    }

    private boolean mayLift(UUID userId, UUID workspaceId, Person person) {
        return userId.equals(person.getProcessingRestrictedBy())
                || workspaceAccess.holdsAction(userId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
    }
}
