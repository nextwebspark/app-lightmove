package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.model.RecipientEmail;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.constant.OutreachSkipReason;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import app.lightmove.api.outreach.constant.SequenceStartMode;
import app.lightmove.api.outreach.dto.EnrollPersonRequest;
import app.lightmove.api.outreach.dto.EnrollmentCandidateResponse;
import app.lightmove.api.outreach.dto.EnrollmentCandidatesRequest;
import app.lightmove.api.outreach.dto.EnrollmentCandidatesResponse;
import app.lightmove.api.outreach.dto.RecipientEmailResponse;
import app.lightmove.api.outreach.dto.SequenceTokensResponse;
import app.lightmove.api.outreach.dto.StartSequenceRequest;
import app.lightmove.api.outreach.dto.StartSequenceResponse;
import app.lightmove.api.outreach.model.FirstSendSpacing;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachSequence;
import app.lightmove.api.outreach.model.RecipientEligibility;
import app.lightmove.api.outreach.model.ReviewedFirstEmail;
import app.lightmove.api.outreach.model.SenderContext;
import app.lightmove.api.outreach.model.SequenceStep;
import app.lightmove.api.outreach.model.SequenceTokens;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachSequenceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Add to sequence: who may be approached, and putting the reviewed people on a sequence as
 * {@code SCHEDULED}. Nothing here sends. Every rule the Choose step shows is decided again at Start, so
 * a person the dialog listed as skippable — or who became so while it was open — is never enrolled.
 * First emails are spaced by {@link FirstSendSpacing}, in the order the people were sent.
 */
@Service
@RequiredArgsConstructor
public class OutreachEnrollmentService {

    static final Duration START_HORIZON = Duration.ofDays(60);

    private final CandidateOutreachService people;
    private final OutreachSequenceRepository sequences;
    private final OutreachEnrollmentRepository enrollments;
    private final OutreachEligibility eligibility;
    private final MailboxConnectionRepository mailboxes;
    private final OutreachPersonalisation personalisation;
    private final BookingPages bookingPages;
    private final OutreachOutcomes outcomes;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public EnrollmentCandidatesResponse candidates(UUID userId, UUID workspaceId, UUID projectId,
                                                   EnrollmentCandidatesRequest request) {
        List<OutreachRecipient> recipients = people.recipientsOf(workspaceId, projectId,
                request.candidateIdsOrEmpty(), request.triageCompanyIdsOrEmpty());
        RecipientEligibility eligible = eligibility.of(projectId, recipients);
        Map<UUID, String> sequenceNames = sequences.findAllById(eligible.liveByPerson().values().stream()
                        .map(OutreachEnrollment::getSequenceId).distinct().toList()).stream()
                .collect(Collectors.toMap(OutreachSequence::getId, OutreachSequence::getName));
        SenderContext sender = personalisation.senderContextOf(userId, workspaceId, projectId);
        return new EnrollmentCandidatesResponse(recipients.stream()
                .map(recipient -> {
                    OutreachSkipReason skip = eligible.skipReasonOf(recipient);
                    OutreachEnrollment held = eligible.liveEnrollmentOf(recipient);
                    return new EnrollmentCandidateResponse(recipient.candidateId(), recipient.personId(),
                            recipient.triageCompanyId(), recipient.fullName(), recipient.title(),
                            recipient.companyName(),
                            recipient.emails().stream().map(RecipientEmailResponse::of).toList(), skip,
                            held == null ? null : sequenceNames.get(held.getSequenceId()),
                            SequenceTokensResponse.of(OutreachPersonalisation.tokensOf(recipient, sender, null)));
                })
                .toList());
    }

    public StartSequenceResponse start(UUID userId, UUID workspaceId, UUID projectId, UUID sequenceId,
                                       StartSequenceRequest request, HttpServletRequest httpRequest) {
        List<UUID> candidateIds = request.people().stream().map(EnrollPersonRequest::candidateId).toList();
        if (new HashSet<>(candidateIds).size() != candidateIds.size()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Someone is listed twice");
        }
        refuseUnusableStartAt(request);
        boolean usesBookingLink = Boolean.TRUE.equals(transactions.execute(status -> sequences
                .requireInProject(sequenceId, workspaceId, projectId).uses(SequenceTokens.BOOKING_LINK)));
        if (usesBookingLink) {
            // Before anything is written: a page the mail service will not make is the consultant's to see now.
            bookingPages.prepare(userId, workspaceId);
        }
        // A racing Start that lost on the live index is answered OUTREACH_ALREADY_ENROLLED by
        // GlobalExceptionHandler, by constraint name; every other violation keeps its own answer.
        List<OutreachEnrollment> created =
                transactions.execute(status -> enroll(userId, workspaceId, projectId, sequenceId, request));
        for (OutreachEnrollment enrollment : created) {
            audit.projectEvent(ProjectEventType.OUTREACH_ENROLLED, userId, workspaceId, projectId, httpRequest)
                    .detail("sequenceId", sequenceId.toString())
                    .detail("enrollmentId", enrollment.getId().toString())
                    .detail("candidateId", enrollment.getCandidateId().toString())
                    .record();
        }
        return new StartSequenceResponse(created.size(), created.getFirst().getNextSendAt(),
                created.getLast().getNextSendAt());
    }

    /** Nothing more goes to this person on this sequence; what already went stays on the record. */
    @Transactional
    public void stop(UUID userId, UUID workspaceId, UUID projectId, UUID enrollmentId, HttpServletRequest httpRequest) {
        OutreachEnrollment enrollment = enrollments.findByIdAndWorkspaceIdAndProjectId(enrollmentId, workspaceId,
                        projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!enrollment.isLive()) {
            throw ApiException.of(ErrorCode.OUTREACH_NOT_RUNNING);
        }
        outcomes.stop(enrollment, OutreachStopReason.MANUAL, userId, clock.instant(), httpRequest);
    }

    private void refuseUnusableStartAt(StartSequenceRequest request) {
        if (request.startModeOrDefault() != SequenceStartMode.AT) {
            return;
        }
        Instant now = clock.instant();
        if (request.startAt() == null || request.startAt().isBefore(now)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "startAt", "Choose a time that is still ahead");
        }
        if (request.startAt().isAfter(now.plus(START_HORIZON))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "startAt",
                    "Choose a time within the next 60 days");
        }
    }

    private List<OutreachEnrollment> enroll(UUID userId, UUID workspaceId, UUID projectId, UUID sequenceId,
                                            StartSequenceRequest request) {
        MailboxConnection mailbox = requireSendingMailbox(userId, workspaceId);
        OutreachSequence sequence = sequences.requireInProject(sequenceId, workspaceId, projectId);
        List<UUID> candidateIds = request.people().stream().map(EnrollPersonRequest::candidateId).toList();
        Map<UUID, OutreachRecipient> recipients = people.recipientsOf(workspaceId, projectId, candidateIds, List.of())
                .stream()
                .collect(Collectors.toMap(OutreachRecipient::candidateId, Function.identity()));
        if (recipients.size() != candidateIds.size()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        if (eligibility.of(projectId, recipients.values()).anySkipped(recipients.values())) {
            throw ApiException.of(ErrorCode.OUTREACH_PERSON_SKIPPED);
        }

        SenderContext sender = personalisation.senderContextOf(userId, workspaceId, projectId);
        SequenceStep first = sequence.firstStep();
        Instant now = clock.instant();
        SequenceStartMode mode = request.startModeOrDefault();
        boolean pinned = mode != SequenceStartMode.NEXT_WINDOW;
        Instant firstDue = switch (mode) {
            case NOW -> now;
            case AT -> request.startAt();
            case NEXT_WINDOW -> sequence.sendingWindow().nextOpening(now, mailbox.zone());
        };
        List<Duration> offsets = FirstSendSpacing.offsetsOf(request.people().size(), ThreadLocalRandom.current());
        List<OutreachEnrollment> created = IntStream.range(0, request.people().size())
                .mapToObj(index -> {
                    EnrollPersonRequest person = request.people().get(index);
                    OutreachRecipient recipient = recipients.get(person.candidateId());
                    if (!recipient.holdsEmail(person.toAddress())) {
                        throw ApiException.of(ErrorCode.OUTREACH_ADDRESS_NOT_ON_FILE);
                    }
                    String opener = blankToNull(person.opener());
                    SequenceTokens tokens = OutreachPersonalisation.tokensOf(recipient, sender, opener);
                    ReviewedFirstEmail email = new ReviewedFirstEmail(tokens.render(first.getSubject()),
                            tokens.render(first.getBody()), opener, person.openerEdited());
                    return OutreachEnrollment.scheduled(sequence, recipient.candidateId(), recipient.personId(),
                            userId, ledgerSpellingOf(recipient, person.toAddress()), email, now,
                            firstDue, offsets.get(index), pinned);
                })
                .toList();
        List<OutreachEnrollment> saved = enrollments.saveAllAndFlush(created);
        saved.forEach(enrollment -> people.recordEnrolled(userId, projectId, enrollment.getCandidateId(),
                sequence.getId(), sequence.getName()));
        return saved;
    }

    private MailboxConnection requireSendingMailbox(UUID userId, UUID workspaceId) {
        MailboxConnection mailbox = mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_NOT_CONNECTED));
        if (!mailbox.canSend()) {
            throw ApiException.of(ErrorCode.MAILBOX_RECONNECT_NEEDED);
        }
        return mailbox;
    }

    private static String ledgerSpellingOf(OutreachRecipient recipient, String requested) {
        return recipient.emails().stream()
                .map(RecipientEmail::address)
                .filter(address -> address.equalsIgnoreCase(requested.trim()))
                .findFirst()
                .orElseThrow();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
