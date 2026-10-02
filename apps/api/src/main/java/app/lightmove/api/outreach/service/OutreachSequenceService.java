package app.lightmove.api.outreach.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.dto.SaveSequenceRequest;
import app.lightmove.api.outreach.dto.SequenceResponse;
import app.lightmove.api.outreach.dto.SequenceStepRequest;
import app.lightmove.api.outreach.dto.SequenceStepResponse;
import app.lightmove.api.outreach.dto.SequencesResponse;
import app.lightmove.api.outreach.model.OutreachSequence;
import app.lightmove.api.outreach.model.SequenceEnrollmentCount;
import app.lightmove.api.outreach.model.SequenceStep;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachSequenceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A position's sequences, written whole by the editor. A sequence people are on can be edited — they
 * get the new wording from their next step, their reviewed first email being frozen — but not deleted.
 */
@Service
@RequiredArgsConstructor
public class OutreachSequenceService {

    private static final String TARGET_DETAIL = "sequenceId";

    private final OutreachSequenceRepository sequences;
    private final OutreachEnrollmentRepository enrollments;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public SequencesResponse list(UUID workspaceId, UUID projectId) {
        List<OutreachSequence> found = sequences.findByWorkspaceIdAndProjectIdOrderByCreatedAtAsc(workspaceId, projectId);
        Map<UUID, SequenceEnrollmentCount> counts = countsOf(projectId);
        Map<UUID, String> authors = authorNamesOf(found);
        return new SequencesResponse(found.stream()
                .map(sequence -> toResponse(sequence, authors.get(sequence.getCreatedBy()),
                        counts.get(sequence.getId())))
                .toList());
    }

    @Transactional(readOnly = true)
    public SequenceResponse get(UUID workspaceId, UUID projectId, UUID sequenceId) {
        return toResponse(sequences.requireInProject(sequenceId, workspaceId, projectId));
    }

    @Transactional
    public SequenceResponse create(UUID userId, UUID workspaceId, UUID projectId, SaveSequenceRequest request,
                                   HttpServletRequest httpRequest) {
        OutreachSequence sequence = sequences.save(OutreachSequence.written(workspaceId, projectId, userId,
                request.name().trim(), stepsOf(request.steps())));
        audited(ProjectEventType.OUTREACH_SEQUENCE_CREATED, userId, workspaceId, projectId, sequence, httpRequest);
        return toResponse(sequence);
    }

    @Transactional
    public SequenceResponse update(UUID userId, UUID workspaceId, UUID projectId, UUID sequenceId,
                                   SaveSequenceRequest request, HttpServletRequest httpRequest) {
        OutreachSequence sequence = sequences.requireInProject(sequenceId, workspaceId, projectId);
        sequence.rewrite(request.name().trim(), stepsOf(request.steps()));
        audited(ProjectEventType.OUTREACH_SEQUENCE_UPDATED, userId, workspaceId, projectId, sequence, httpRequest);
        return toResponse(sequence);
    }

    @Transactional
    public void delete(UUID userId, UUID workspaceId, UUID projectId, UUID sequenceId, HttpServletRequest httpRequest) {
        OutreachSequence sequence = sequences.requireInProject(sequenceId, workspaceId, projectId);
        if (enrollments.existsBySequenceId(sequenceId)) {
            throw ApiException.of(ErrorCode.OUTREACH_SEQUENCE_IN_USE);
        }
        sequences.delete(sequence);
        audited(ProjectEventType.OUTREACH_SEQUENCE_DELETED, userId, workspaceId, projectId, sequence, httpRequest);
    }

    /** Step one goes when the consultant starts, so its delay is always zero; only it carries a subject. */
    private static List<SequenceStep> stepsOf(List<SequenceStepRequest> requested) {
        List<SequenceStep> steps = new ArrayList<>();
        for (int index = 0; index < requested.size(); index++) {
            SequenceStepRequest step = requested.get(index);
            if (index == 0) {
                if (step.subject() == null || step.subject().isBlank()) {
                    throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "steps[0].subject",
                            "Write a subject for the first email");
                }
                steps.add(SequenceStep.of(0, step.subject().trim(), step.body()));
            } else {
                if (step.delayWorkingDays() < 1) {
                    throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "steps[" + index + "].delayWorkingDays",
                            "A follow-up waits at least one working day");
                }
                steps.add(SequenceStep.of(step.delayWorkingDays(), null, step.body()));
            }
        }
        return steps;
    }

    private SequenceResponse toResponse(OutreachSequence sequence) {
        String author = sequence.getCreatedBy() == null ? null
                : users.findById(sequence.getCreatedBy()).map(User::getFullName).orElse(null);
        return toResponse(sequence, author, countsOf(sequence.getProjectId()).get(sequence.getId()));
    }

    private Map<UUID, SequenceEnrollmentCount> countsOf(UUID projectId) {
        return enrollments.countBySequenceOfProject(projectId).stream()
                .collect(Collectors.toMap(SequenceEnrollmentCount::getSequenceId, Function.identity()));
    }

    private static SequenceResponse toResponse(OutreachSequence sequence, String author,
                                               SequenceEnrollmentCount counts) {
        return new SequenceResponse(sequence.getId(), sequence.getName(),
                sequence.getSteps().stream().map(SequenceStepResponse::of).toList(), author,
                counts == null ? 0 : counts.getTotal(), counts == null ? 0 : counts.getSent(),
                counts == null ? 0 : counts.getReplied(), sequence.getUpdatedAt());
    }

    private Map<UUID, String> authorNamesOf(List<OutreachSequence> found) {
        List<UUID> ids = found.stream().map(OutreachSequence::getCreatedBy).filter(Objects::nonNull).distinct().toList();
        return users.findAllById(ids).stream()
                .filter(user -> user.getFullName() != null)
                .collect(Collectors.toMap(User::getId, User::getFullName, (first, second) -> first));
    }

    private void audited(ProjectEventType type, UUID userId, UUID workspaceId, UUID projectId,
                         OutreachSequence sequence, HttpServletRequest httpRequest) {
        audit.projectEvent(type, userId, workspaceId, projectId, httpRequest)
                .detail(TARGET_DETAIL, sequence.getId().toString())
                .record();
    }
}
