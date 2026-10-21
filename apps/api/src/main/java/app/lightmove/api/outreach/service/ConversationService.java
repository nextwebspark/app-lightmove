package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.dto.CandidateOpenTaskDto;
import app.lightmove.api.candidate.dto.PipelineRowResponse;
import app.lightmove.api.candidate.model.NoteViewpoint;
import app.lightmove.api.candidate.dto.CandidateContactsDto;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.candidate.service.NextActionLookup;
import app.lightmove.api.candidate.service.PersonNoteService;
import app.lightmove.api.candidate.service.PersonTimelineService;
import app.lightmove.api.candidate.service.PositionPipelineService;
import app.lightmove.api.common.constant.NoteFormat;
import app.lightmove.api.common.note.NoteText;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.constant.ConversationGroup;
import app.lightmove.api.outreach.constant.ConversationScope;
import app.lightmove.api.outreach.constant.ConversationView;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.MessageBodyFormat;
import app.lightmove.api.outreach.constant.MessageStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import app.lightmove.api.outreach.dto.CandidateOutreachResponse;
import app.lightmove.api.outreach.dto.AttachmentLimitsResponse;
import app.lightmove.api.outreach.dto.ConversationAttachmentResponse;
import app.lightmove.api.outreach.dto.ConversationComposerResponse;
import app.lightmove.api.outreach.dto.ConversationContactResponse;
import app.lightmove.api.outreach.dto.ConversationCountsResponse;
import app.lightmove.api.outreach.dto.ConversationEventResponse;
import app.lightmove.api.outreach.dto.ConversationKeyDatesResponse;
import app.lightmove.api.outreach.dto.ConversationListResponse;
import app.lightmove.api.outreach.dto.ConversationMessageResponse;
import app.lightmove.api.outreach.dto.ConversationMessageSummaryResponse;
import app.lightmove.api.outreach.dto.ConversationResponse;
import app.lightmove.api.outreach.dto.ConversationRowResponse;
import app.lightmove.api.outreach.dto.OutreachRunResponse;
import app.lightmove.api.outreach.dto.PersonMeetingsResponse;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachMessage;
import app.lightmove.api.outreach.model.OutreachMessageAttachment;
import app.lightmove.api.outreach.model.PersonNextSend;
import app.lightmove.api.outreach.model.ReplyThreadPreview;
import app.lightmove.api.outreach.model.OutreachSequence;
import app.lightmove.api.outreach.model.PersonSentTally;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageAttachmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import app.lightmove.api.outreach.repository.OutreachSequenceRepository;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Outreach page's queue and one executive's conversation (docs/outreach-conversations.md). It owns nothing:
 * the queue's groups are read off the executive's open tasks and runs, and the history is their emails, run
 * events, notes about this position and meetings, merged by time. Staff-only reads.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    /** A position maps hundreds of executives at most; past this the queue answers the first and says so. */
    static final int MAX_ROWS = 500;

    /** Runs read for the queue: every run of a position this size, re-enrolments included. */
    private static final int MAX_RUNS = 2_000;

    /** One person's history on one position is tens of lines; past this the oldest are left off. */
    static final int MAX_EVENTS = 200;

    static final int MAX_QUERY = 100;

    private static final int PREVIEW_LENGTH = 140;

    /** A run these ended needs a person: the email did not go, or the mailbox cannot send it. */
    private static final Set<OutreachStopReason> FAILED_SEND = EnumSet.of(OutreachStopReason.SEND_FAILED,
            OutreachStopReason.SEND_UNCERTAIN, OutreachStopReason.MAILBOX_INACTIVE, OutreachStopReason.MAILBOX_MOVED,
            OutreachStopReason.ADDRESS_REMOVED);

    /**
     * The run and status lines a conversation shows. An email, a reply or a bounce is its own message row, so the
     * lines that announced one are left out; V145 gave every earlier reply and bounce a row.
     */
    private static final Set<PersonActivityKind> HISTORY_KINDS = EnumSet.of(PersonActivityKind.OUTREACH_ENROLLED,
            PersonActivityKind.OUTREACH_STOPPED, PersonActivityKind.STATUS_CHANGED);

    private static final Comparator<Instant> LATEST_FIRST = Comparator.nullsLast(Comparator.reverseOrder());
    private static final Comparator<Instant> SOONEST_FIRST = Comparator.nullsLast(Comparator.naturalOrder());

    private final PositionPipelineService pipeline;
    private final CandidateOutreachService people;
    private final OutreachMessageService ownMessages;
    private final NextActionLookup nextActions;
    private final PersonTimelineService timeline;
    private final PersonNoteService notes;
    private final OutreachMonitorService monitor;
    private final MeetingService meetings;
    private final OutreachEnrollmentRepository enrollments;
    private final OutreachMessageRepository messages;
    private final OutreachMessageAttachmentRepository attachmentRows;
    private final OutreachAttachments attachments;
    private final OutreachSequenceRepository sequences;
    private final PersonMeetingRepository personMeetings;
    private final UserRepository users;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConversationListResponse list(UUID userId, UUID workspaceId, UUID projectId, ConversationView view,
                                         ConversationScope scope, String query) {
        String needle = needleOf(query);
        List<PipelineRowResponse> executives = pipeline.pipelineOf(workspaceId, projectId, null, null).rows();
        if (executives.isEmpty()) {
            return new ConversationListResponse(new ConversationCountsResponse(0, 0), null, List.of(), false);
        }
        List<QueueEntry> entries = entriesOf(workspaceId, projectId, executives);

        Predicate<QueueEntry> needsMine = entry -> entry.group() != null && entry.needsActionFrom(userId);
        Predicate<QueueEntry> touchedMine = entry -> entry.touchedBy(userId);
        boolean mine = scope == ConversationScope.MINE;
        List<QueueEntry> scheduled = entries.stream()
                .filter(entry -> entry.scheduledAt() != null && (!mine || touchedMine.test(entry)))
                .toList();
        ConversationCountsResponse counts = new ConversationCountsResponse(
                entries.stream().filter(entry -> entry.group() != null && (!mine || needsMine.test(entry))).count(),
                scheduled.size());
        Instant nextScheduledAt = scheduled.stream().map(QueueEntry::scheduledAt).min(Comparator.naturalOrder())
                .orElse(null);

        Set<UUID> matching = needle == null ? null : matchingPeople(workspaceId, projectId, entries, needle);
        List<QueueEntry> shown = entries.stream()
                .filter(entry -> matching == null || matching.contains(entry.executive().personId()))
                .filter(entry -> switch (view) {
                    case NEEDS -> entry.group() != null && (!mine || needsMine.test(entry));
                    case ALL -> entry.isInConversation() && (!mine || touchedMine.test(entry));
                    case SCHEDULED -> entry.scheduledAt() != null && (!mine || touchedMine.test(entry));
                    case SENT -> entry.lastSentAt() != null && (!mine || touchedMine.test(entry));
                })
                .sorted(orderOf(view))
                .toList();
        boolean truncated = shown.size() > MAX_ROWS;
        return new ConversationListResponse(counts, nextScheduledAt,
                shown.stream().limit(MAX_ROWS).map(ConversationService::rowOf).toList(), truncated);
    }

    @Transactional(readOnly = true)
    public ConversationResponse conversationOf(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId) {
        PipelineRowResponse executive = pipeline.rowOf(workspaceId, projectId, candidateId);
        UUID personId = executive.personId();
        List<CandidateOpenTaskDto> openTasks = nextActions.openTasksOf(projectId, List.of(candidateId))
                .getOrDefault(candidateId, List.of());
        CandidateOutreachResponse currentRun = monitor.ofCandidate(workspaceId, projectId, candidateId);
        List<OutreachEnrollment> runs = enrollments.findByWorkspaceIdAndProjectIdAndPersonIdOrderByEnrolledAtDesc(
                workspaceId, projectId, personId);

        List<OutreachMessage> lines = messages.findConversation(workspaceId, projectId, personId,
                Limit.of(MAX_EVENTS + 1));
        List<ConversationEventResponse> events = new ArrayList<>();
        messageResponsesOf(workspaceId, lines, runs).forEach(message -> events.add(ConversationEventResponse.of(message)));
        timeline.positionLinesOf(workspaceId, personId, projectId, HISTORY_KINDS, MAX_EVENTS + 1)
                .forEach(line -> events.add(ConversationEventResponse.of(line)));
        notes.list(userId, workspaceId, personId, NoteViewpoint.position(projectId))
                .forEach(note -> events.add(ConversationEventResponse.of(note)));
        PersonMeetingsResponse held = meetings.ofCandidate(workspaceId, projectId, candidateId);
        Stream.concat(held.upcoming().stream(), held.past().stream())
                .forEach(meeting -> events.add(ConversationEventResponse.of(meeting)));
        events.sort(Comparator.comparing(ConversationEventResponse::at, LATEST_FIRST));
        boolean truncated = events.size() > MAX_EVENTS;
        List<ConversationEventResponse> shown = truncated ? events.subList(0, MAX_EVENTS) : events;

        Instant now = clock.instant();
        Instant lastActivityAt = shown.stream().map(ConversationEventResponse::at)
                .filter(at -> at != null && !at.isAfter(now)).findFirst().orElse(null);
        Instant firstContactedAt = messages.findFirstSentAt(workspaceId, projectId, personId);
        Instant repliedAt = runs.stream().map(OutreachEnrollment::getRepliedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new ConversationResponse(executive, openTasks, currentRun,
                new ConversationKeyDatesResponse(executive.addedAt(), firstContactedAt, repliedAt, lastActivityAt),
                contactOf(workspaceId, projectId, candidateId),
                composerOf(userId, workspaceId, projectId, candidateId, personId),
                List.copyOf(shown), truncated);
    }

    private ConversationComposerResponse composerOf(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                                    UUID personId) {
        Optional<ReplyThreadPreview> reply = ownMessages.replyPreviewFor(userId, workspaceId, projectId, personId);
        return new ConversationComposerResponse(reply.map(ReplyThreadPreview::subject).orElse(null),
                reply.map(ReplyThreadPreview::stopsLiveRun).orElse(false),
                people.emailSentMoveOf(workspaceId, projectId, candidateId).orElse(null),
                new AttachmentLimitsResponse(attachments.limits().maxFiles(), attachments.limits().maxFileBytes(),
                        attachments.limits().maxTotalBytes()));
    }

    private ConversationContactResponse contactOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        OutreachRecipient recipient = people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        CandidateContactsDto contacts = people.contactsOf(workspaceId, projectId, candidateId).orElseThrow();
        return new ConversationContactResponse(contacts.emails(), contacts.phones(), recipient.locationCity(),
                recipient.locationCountry(), recipient.doNotContact());
    }

    private List<QueueEntry> entriesOf(UUID workspaceId, UUID projectId, List<PipelineRowResponse> executives) {
        Map<UUID, List<CandidateOpenTaskDto>> tasks = nextActions.openTasksOf(projectId,
                executives.stream().map(PipelineRowResponse::candidateId).toList());
        Map<UUID, OutreachMessage> latestMessages = messages.findLatestOfEachPerson(workspaceId, projectId).stream()
                .collect(Collectors.toMap(OutreachMessage::getPersonId, Function.identity()));
        Map<UUID, PersonSentTally> sent = messages.tallySentByPerson(workspaceId, projectId).stream()
                .collect(Collectors.toMap(PersonSentTally::getPersonId, Function.identity()));
        Map<UUID, Instant> ownSends = messages.findNextSendOfEachPerson(workspaceId, projectId).stream()
                .collect(Collectors.toMap(PersonNextSend::getPersonId, PersonNextSend::getSendAt));

        Map<UUID, OutreachEnrollment> latestRuns = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> senders = new HashMap<>();
        enrollments.findByWorkspaceIdAndProjectIdOrderByEnrolledAtDesc(workspaceId, projectId,
                PageRequest.of(0, MAX_RUNS)).forEach(run -> {
                    latestRuns.putIfAbsent(run.getPersonId(), run);
                    senders.computeIfAbsent(run.getPersonId(), person -> new HashSet<>()).add(run.getSenderUserId());
                });
        latestMessages.values().forEach(message -> senders
                .computeIfAbsent(message.getPersonId(), person -> new HashSet<>()).add(message.getSenderUserId()));
        Map<UUID, OutreachRunResponse> runResponses = monitor.runsOf(workspaceId, projectId,
                        List.copyOf(latestRuns.values())).stream()
                .collect(Collectors.toMap(OutreachRunResponse::id, Function.identity()));
        Set<UUID> met = new HashSet<>(personMeetings.findPersonIdsMetAmong(workspaceId,
                executives.stream().map(PipelineRowResponse::personId).toList()));

        return executives.stream().map(executive -> {
            UUID personId = executive.personId();
            OutreachEnrollment run = latestRuns.get(personId);
            PersonSentTally tally = sent.get(personId);
            return QueueEntry.of(executive, tasks.getOrDefault(executive.candidateId(), List.of()), run,
                    run == null ? null : runResponses.get(run.getId()), latestMessages.get(personId),
                    tally == null ? null : tally.getLastSentAt(), ownSends.get(personId),
                    senders.getOrDefault(personId, Set.of()), met.contains(personId));
        }).toList();
    }

    private Set<UUID> matchingPeople(UUID workspaceId, UUID projectId, List<QueueEntry> entries, String needle) {
        Set<UUID> matching = new HashSet<>(messages.findPersonIdsMatching(workspaceId, projectId,
                "%" + escapedForLike(needle) + "%"));
        entries.stream()
                .map(QueueEntry::executive)
                .filter(executive -> contains(executive.fullName(), needle) || contains(executive.title(), needle)
                        || contains(executive.companyName(), needle))
                .forEach(executive -> matching.add(executive.personId()));
        return matching;
    }

    private List<ConversationMessageResponse> messageResponsesOf(UUID workspaceId, List<OutreachMessage> lines,
                                                                 List<OutreachEnrollment> runs) {
        Map<UUID, List<ConversationAttachmentResponse>> files = attachmentRows
                .findByWorkspaceIdAndMessageIdInOrderByPosition(workspaceId,
                        lines.stream().map(OutreachMessage::getId).toList()).stream()
                .collect(Collectors.groupingBy(OutreachMessageAttachment::getMessageId, Collectors.mapping(
                        file -> new ConversationAttachmentResponse(file.getId(), file.getFileName(),
                                file.getContentType(), file.getSizeBytes()), Collectors.toList())));
        Map<UUID, OutreachEnrollment> runsById = runs.stream()
                .collect(Collectors.toMap(OutreachEnrollment::getId, Function.identity()));
        Map<UUID, OutreachSequence> sequencesById = sequences.findAllById(runs.stream()
                        .map(OutreachEnrollment::getSequenceId).distinct().toList()).stream()
                .collect(Collectors.toMap(OutreachSequence::getId, Function.identity()));
        Map<UUID, String> senderNames = users.findAllById(lines.stream().map(OutreachMessage::getSenderUserId)
                        .distinct().toList()).stream()
                .filter(user -> user.getFullName() != null)
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return lines.stream().map(line -> {
            OutreachEnrollment run = line.getEnrollmentId() == null ? null : runsById.get(line.getEnrollmentId());
            OutreachSequence sequence = run == null ? null : sequencesById.get(run.getSequenceId());
            return new ConversationMessageResponse(line.getId(), line.getDirection(), line.getChannel(),
                    line.getKind(), line.getStatus(), line.getOccurredAt(), line.getSubject(), line.getBodyText(),
                    line.getBodyFormat(), line.getFromAddress(), line.getToAddress(), line.getSenderUserId(),
                    senderNames.get(line.getSenderUserId()), line.getEnrollmentId(),
                    sequence == null ? null : sequence.getName(), line.getStep() == null ? null : line.getStep() + 1,
                    sequence == null ? null : Math.max(sequence.getSteps().size(), run.sentCount()),
                    line.getFailReason(),
                    line.keepsAttachmentFiles() ? files.getOrDefault(line.getId(), List.of()) : List.of());
        }).toList();
    }

    private static ConversationRowResponse rowOf(QueueEntry entry) {
        PipelineRowResponse executive = entry.executive();
        OutreachMessage latest = entry.latestMessage();
        return new ConversationRowResponse(executive.candidateId(), executive.personId(), executive.fullName(),
                executive.title(), executive.companyName(), executive.enrichedAt(), executive.status(),
                executive.statusLabel(), executive.statusColour(), entry.group(), entry.action(), entry.runResponse(),
                latest == null ? null : new ConversationMessageSummaryResponse(latest.getChannel(),
                        latest.getDirection(), latest.getKind(), latest.getOccurredAt(), previewOf(latest)),
                entry.lastSentAt(), entry.scheduledAt(), entry.lastActivityAt());
    }

    private static Comparator<QueueEntry> orderOf(ConversationView view) {
        Comparator<QueueEntry> latestFirst = Comparator.comparing(QueueEntry::lastActivityAt, LATEST_FIRST);
        return switch (view) {
            case NEEDS -> Comparator.comparing(QueueEntry::group)
                    .thenComparing(QueueEntry::dueAt, SOONEST_FIRST)
                    .thenComparing(latestFirst);
            case ALL -> latestFirst;
            case SCHEDULED -> Comparator.comparing(QueueEntry::scheduledAt, SOONEST_FIRST);
            case SENT -> Comparator.comparing(QueueEntry::lastSentAt, LATEST_FIRST);
        };
    }

    private static String previewOf(OutreachMessage message) {
        String text = message.getBodyText() == null ? message.getSubject()
                : message.getBodyFormat() == MessageBodyFormat.MARKDOWN
                        ? NoteText.plainTextOf(message.getBodyText(), NoteFormat.MARKDOWN)
                        : message.getBodyText();
        if (text == null) {
            return null;
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= PREVIEW_LENGTH ? flat : flat.substring(0, PREVIEW_LENGTH - 1).strip() + "…";
    }

    private static String needleOf(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String needle = query.strip();
        if (needle.length() > MAX_QUERY) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "A search is at most 100 characters",
                    "Conversation search of " + needle.length() + " characters");
        }
        return needle.toLowerCase(Locale.ROOT);
    }

    /** {@code \} first, so the escapes added for {@code %} and {@code _} are not themselves escaped. */
    static String escapedForLike(String needle) {
        return needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** One executive's standing in the queue, worked out once and read by every tab. */
    private record QueueEntry(PipelineRowResponse executive, ConversationGroup group, CandidateOpenTaskDto action,
                              OutreachEnrollment run, OutreachRunResponse runResponse, OutreachMessage latestMessage,
                              Instant lastSentAt, Instant scheduledAt, Instant lastActivityAt, Set<UUID> senders,
                              boolean met, boolean hasTasks) {

        static QueueEntry of(PipelineRowResponse executive, List<CandidateOpenTaskDto> tasks, OutreachEnrollment run,
                             OutreachRunResponse runResponse, OutreachMessage latestMessage, Instant lastSentAt,
                             Instant ownSendAt, Set<UUID> senders, boolean met) {
            Instant runEnded = run == null ? null : OutreachMonitorService.endedAtOf(run);
            CandidateOpenTaskDto bounceTask = firstWithRule(tasks, CandidateOpenTaskDto.BOUNCE_RULE);
            CandidateOpenTaskDto replyTask = firstWithRule(tasks, CandidateOpenTaskDto.REPLY_RULE);
            boolean failedSend = run != null && run.getStatus() == EnrollmentStatus.STOPPED
                    && FAILED_SEND.contains(run.getStopReason())
                    && (lastSentAt == null || runEnded == null || !lastSentAt.isAfter(runEnded));
            boolean ownEmailFailed = latestMessage != null && latestMessage.getStatus() == MessageStatus.FAILED;

            ConversationGroup group = null;
            CandidateOpenTaskDto action = null;
            if (bounceTask != null || failedSend || ownEmailFailed) {
                group = ConversationGroup.ATTENTION;
                action = bounceTask != null ? bounceTask : tasks.stream().findFirst().orElse(null);
            } else if (replyTask != null) {
                group = ConversationGroup.NEEDS_REPLY;
                action = replyTask;
            } else if (!tasks.isEmpty()) {
                group = lastSentAt == null ? ConversationGroup.PREPARATION : ConversationGroup.FOLLOW_UP;
                action = tasks.getFirst();
            }
            Instant scheduledAt = Stream.of(run != null && run.isLive() ? run.getNextSendAt() : null, ownSendAt)
                    .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            Instant lastActivityAt = Stream.of(latestMessage == null ? null : latestMessage.getOccurredAt(),
                            run == null ? null : run.getEnrolledAt(), runEnded)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            return new QueueEntry(executive, group, action, run, runResponse, latestMessage, lastSentAt, scheduledAt,
                    lastActivityAt, senders, met, !tasks.isEmpty());
        }

        /** Someone the conversation has something about: an email, a run, a meeting or a task. */
        boolean isInConversation() {
            return run != null || latestMessage != null || met || hasTasks;
        }

        /** Mine in Needs action: the task is mine, or — with no task open — the failed email or run was mine. */
        boolean needsActionFrom(UUID userId) {
            if (action != null) {
                return action.assignee() != null && userId.equals(action.assignee().userId());
            }
            if (latestMessage != null && latestMessage.getStatus() == MessageStatus.FAILED) {
                return userId.equals(latestMessage.getSenderUserId());
            }
            return run != null && userId.equals(run.getSenderUserId());
        }

        /** Mine on the other tabs: I sent them something, or hold a task on them. */
        boolean touchedBy(UUID userId) {
            return senders.contains(userId) || (action != null && action.assignee() != null
                    && userId.equals(action.assignee().userId()));
        }

        Instant dueAt() {
            return action == null ? null : action.dueAt();
        }

        private static CandidateOpenTaskDto firstWithRule(Collection<CandidateOpenTaskDto> tasks, String rule) {
            return tasks.stream().filter(task -> rule.equals(task.rule())).findFirst().orElse(null);
        }
    }
}
