package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.OutreachStepState;
import app.lightmove.api.outreach.dto.CandidateOutreachResponse;
import app.lightmove.api.outreach.dto.OutreachCountsResponse;
import app.lightmove.api.outreach.dto.OutreachOverviewResponse;
import app.lightmove.api.outreach.dto.OutreachRunResponse;
import app.lightmove.api.outreach.dto.OutreachStepStateResponse;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachMessage;
import app.lightmove.api.outreach.model.OutreachSequence;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import app.lightmove.api.outreach.repository.OutreachSequenceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Outreach page's counts and people, and one executive's run for their drawer. Staff-only reads. */
@Service
@RequiredArgsConstructor
public class OutreachMonitorService {

    /** A position's outreach is tens of people, hundreds at most; past this the page shows the newest. */
    static final int MAX_PEOPLE = 500;

    private final OutreachEnrollmentRepository enrollments;
    private final OutreachSequenceRepository sequences;
    private final OutreachMessageRepository messages;
    private final CandidateOutreachService people;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public OutreachOverviewResponse overview(UUID workspaceId, UUID projectId) {
        List<OutreachEnrollment> all = enrollments.findByWorkspaceIdAndProjectIdOrderByEnrolledAtDesc(workspaceId,
                projectId);
        List<OutreachEnrollment> shown = all.stream().limit(MAX_PEOPLE).toList();
        return new OutreachOverviewResponse(countsOf(all), nextSendOf(all), runsOf(workspaceId, projectId, shown));
    }

    @Transactional(readOnly = true)
    public CandidateOutreachResponse ofCandidate(UUID workspaceId, UUID projectId, UUID candidateId) {
        OutreachRecipient recipient = people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        List<OutreachEnrollment> runs = enrollments.findByWorkspaceIdAndProjectIdAndPersonIdOrderByEnrolledAtDesc(
                workspaceId, projectId, recipient.personId());
        if (runs.isEmpty()) {
            return new CandidateOutreachResponse(null, List.of());
        }
        OutreachEnrollment latest = runs.getFirst();
        OutreachRunResponse run = runsOf(workspaceId, projectId, List.of(latest)).getFirst();
        List<OutreachMessage> sent = messages.findByWorkspaceIdAndEnrollmentIdInOrderBySentAtAsc(workspaceId,
                List.of(latest.getId()));
        return new CandidateOutreachResponse(run, stepsOf(latest, run.stepCount(), sent));
    }

    private static OutreachCountsResponse countsOf(List<OutreachEnrollment> all) {
        long emailsSent = all.stream().mapToLong(OutreachEnrollment::sentCount).sum();
        long reached = all.stream().filter(enrollment -> enrollment.sentCount() > 0).count();
        return new OutreachCountsResponse(all.size(), emailsSent, reached, countWith(all, EnrollmentStatus.REPLIED),
                all.stream().filter(OutreachEnrollment::isLive).count(), countWith(all, EnrollmentStatus.BOUNCED),
                countWith(all, EnrollmentStatus.STOPPED));
    }

    private static long countWith(List<OutreachEnrollment> all, EnrollmentStatus status) {
        return all.stream().filter(enrollment -> enrollment.getStatus() == status).count();
    }

    private static Instant nextSendOf(List<OutreachEnrollment> all) {
        return all.stream()
                .filter(OutreachEnrollment::isLive)
                .map(OutreachEnrollment::getNextSendAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private List<OutreachRunResponse> runsOf(UUID workspaceId, UUID projectId, List<OutreachEnrollment> shown) {
        if (shown.isEmpty()) {
            return List.of();
        }
        Map<UUID, OutreachRecipient> mapped = people.recipientsOf(workspaceId, projectId,
                        shown.stream().map(OutreachEnrollment::getCandidateId).filter(Objects::nonNull).toList(),
                        List.of()).stream()
                .collect(Collectors.toMap(OutreachRecipient::candidateId, Function.identity()));
        Map<UUID, String> unmappedNames = people.fullNamesOf(workspaceId, shown.stream()
                .filter(enrollment -> !mapped.containsKey(enrollment.getCandidateId()))
                .map(OutreachEnrollment::getPersonId)
                .toList());
        Map<UUID, OutreachSequence> sequencesById = sequences.findAllById(shown.stream()
                        .map(OutreachEnrollment::getSequenceId).distinct().toList()).stream()
                .collect(Collectors.toMap(OutreachSequence::getId, Function.identity()));
        Map<UUID, String> senderNames = users.findAllById(shown.stream()
                        .map(OutreachEnrollment::getSenderUserId).distinct().toList()).stream()
                .filter(user -> user.getFullName() != null)
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return shown.stream().map(enrollment -> {
            OutreachRecipient recipient = mapped.get(enrollment.getCandidateId());
            OutreachSequence sequence = sequencesById.get(enrollment.getSequenceId());
            int stepCount = Math.max(sequence == null ? 0 : sequence.getSteps().size(), enrollment.sentCount());
            return new OutreachRunResponse(enrollment.getId(), recipient == null ? null : recipient.candidateId(),
                    enrollment.getPersonId(),
                    recipient != null ? recipient.fullName() : unmappedNames.get(enrollment.getPersonId()),
                    recipient == null ? null : recipient.title(), recipient == null ? null : recipient.companyName(),
                    recipient == null ? null : recipient.status().value(), enrollment.getSequenceId(),
                    sequence == null ? null : sequence.getName(), stepCount, enrollment.sentCount(),
                    enrollment.isLive() ? enrollment.getNextSendAt() : null, enrollment.getLastSentAt(),
                    enrollment.getStatus(), enrollment.getStopReason(), endedAtOf(enrollment),
                    enrollment.getSenderUserId(), senderNames.get(enrollment.getSenderUserId()));
        }).toList();
    }

    private static Instant endedAtOf(OutreachEnrollment enrollment) {
        return switch (enrollment.getStatus()) {
            case REPLIED -> enrollment.getRepliedAt();
            case BOUNCED, STOPPED -> enrollment.getStoppedAt();
            case COMPLETED -> enrollment.getLastSentAt();
            case SCHEDULED, ACTIVE -> null;
        };
    }

    private static List<OutreachStepStateResponse> stepsOf(OutreachEnrollment enrollment, int stepCount,
                                                           List<OutreachMessage> sent) {
        Map<Integer, OutreachMessage> sentByStep = sent.stream()
                .collect(Collectors.toMap(OutreachMessage::getStep, Function.identity(), (first, second) -> first));
        List<OutreachStepStateResponse> steps = new ArrayList<>();
        for (int step = 0; step < stepCount; step++) {
            String subject = step == 0 ? enrollment.getFirstSubject() : null;
            OutreachMessage message = sentByStep.get(step);
            if (step < enrollment.sentCount()) {
                steps.add(new OutreachStepStateResponse(step + 1, subject, OutreachStepState.SENT,
                        message == null ? null : message.getSentAt(), null));
            } else if (enrollment.isLive()) {
                boolean next = step == enrollment.getNextStep();
                steps.add(new OutreachStepStateResponse(step + 1, subject,
                        next ? OutreachStepState.SCHEDULED : OutreachStepState.WAITING,
                        next ? enrollment.getNextSendAt() : null, null));
            } else {
                steps.add(new OutreachStepStateResponse(step + 1, subject, OutreachStepState.NOT_SENT, null,
                        notSentBecause(enrollment)));
            }
        }
        return steps;
    }

    private static String notSentBecause(OutreachEnrollment enrollment) {
        return switch (enrollment.getStatus()) {
            case REPLIED, BOUNCED -> enrollment.getStatus().name();
            case STOPPED -> enrollment.getStopReason() == null ? null : enrollment.getStopReason().name();
            case SCHEDULED, ACTIVE, COMPLETED -> null;
        };
    }
}
