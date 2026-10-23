package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.constant.CallField;
import app.lightmove.api.candidate.model.CallHeldFields;
import app.lightmove.api.candidate.model.CallReference;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.model.ProfileFromCall;
import app.lightmove.api.candidate.model.RelocationTagging;
import app.lightmove.api.candidate.service.CandidateCallUpdates;
import app.lightmove.api.candidate.service.CandidateCallUpdates.SectionChange;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.common.constant.NoticePeriod;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.notetaker.constant.CallSuggestionKind;
import app.lightmove.api.notetaker.constant.CallSuggestionStatus;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.dto.ApplyCallSuggestionRequest;
import app.lightmove.api.notetaker.dto.CallSuggestionResponse;
import app.lightmove.api.notetaker.dto.CallSuggestionsResponse;
import app.lightmove.api.notetaker.dto.EditedSuggestionValue;
import app.lightmove.api.notetaker.model.AppliedChange;
import app.lightmove.api.notetaker.model.CallSuggestion;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.SuggestedValue;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.task.dto.CreateTaskRequest;
import app.lightmove.api.task.dto.TaskResponse;
import app.lightmove.api.task.model.Task;
import app.lightmove.api.task.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Currency;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Suggest changes on a recorded call: one press asks the model to read the transcript against the person's record,
 * and each suggestion it keeps is then applied, skipped or undone by a researcher, one at a time. Nothing reaches the
 * record unless someone applies it, and every apply writes through the field's own owner — the person, the
 * assessment, the tag catalogue, the task list — so scope, validation and the timeline stay where they live.
 *
 * <p>The model call holds no transaction and no connection; a claim on the recording keeps two presses to one call,
 * and nothing is stored or charged unless an answer was stored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallSuggestionService {

    /** Longer than the model call can take; a claim older than this belonged to a request that died. */
    static final Duration CLAIM_LIFETIME = Duration.ofMinutes(3);

    private static final Set<CallField> PACKAGE_FIELDS = EnumSet.of(CallField.BASE_SALARY, CallField.BONUS,
            CallField.ALLOWANCES);
    private static final double LARGEST_FIGURE = 1e10;
    private static final Duration TASK_DUE_AFTER = Duration.ofDays(1);
    private static final DateTimeFormatter CALL_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final MeetingRecordingRepository recordings;
    private final CandidateOutreachService people;
    private final CandidateCallUpdates updates;
    private final CallChangeSuggester suggester;
    private final TaskService tasks;
    private final LlmBudgetGuard llmBudget;
    private final FairUseGuard fairUse;
    private final UsageRecorder usage;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CallSuggestionsResponse read(UUID workspaceId, UUID projectId, UUID candidateId, UUID recordingId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = requireRecording(workspaceId, projectId, recipient, recordingId);
        return responseOf(recording, updates.heldOf(workspaceId, projectId, recipient.candidateId()));
    }

    /**
     * Reads the transcript with the model, once. A call already holding suggestions still waiting answers them again
     * at no cost; one whose every suggestion was decided may be asked again.
     */
    public CallSuggestionsResponse ask(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                       UUID recordingId, HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = transactions.execute(status ->
                requireSuggestible(requireRecording(workspaceId, projectId, recipient, recordingId)));
        if (recording.hasPendingSuggestions()) {
            return read(workspaceId, projectId, candidateId, recordingId);
        }
        llmBudget.require(LlmBudget.CANDIDATE_AI_ENRICH, userId);
        fairUse.check(workspaceId, userId, UsageKind.AI_ENRICH, 1);
        Instant now = clock.instant();
        Integer claimed = transactions.execute(status ->
                recordings.claimSuggesting(recordingId, now, now.minus(CLAIM_LIFETIME)));
        if (claimed == null || claimed == 0) {
            throw ApiException.of(ErrorCode.NOTETAKER_SUGGESTIONS_RUNNING);
        }
        try {
            CallHeldFields held = updates.heldOf(workspaceId, projectId, recipient.candidateId());
            List<CallSuggestion> found = readTranscript(recording, held);
            Instant at = clock.instant();
            transactions.executeWithoutResult(status -> {
                MeetingRecording fresh = requireSuggestible(
                        requireRecording(workspaceId, projectId, recipient, recordingId));
                fresh.suggested(found, at);
                audit.projectEvent(ProjectEventType.NOTETAKER_SUGGESTIONS_ASKED, userId, workspaceId, projectId,
                                httpRequest)
                        .detail("candidateId", recipient.candidateId().toString())
                        .detail("recordingId", recordingId.toString())
                        .detail("found", String.valueOf(found.size()))
                        .record();
            });
            if (!found.isEmpty()) {
                usage.record(new MeteredUse(workspaceId, userId, projectId, UsageKind.AI_ENRICH, 1,
                        "notetaker-suggest:" + recordingId + ":" + at.toEpochMilli()));
            }
        } finally {
            transactions.executeWithoutResult(status -> recordings.releaseSuggesting(recordingId));
        }
        return read(workspaceId, projectId, candidateId, recordingId);
    }

    public CallSuggestionsResponse apply(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                         UUID recordingId, String suggestionId, ApplyCallSuggestionRequest request,
                                         HttpServletRequest httpRequest) {
        return decide(() -> {
            OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
            MeetingRecording recording = requireRecording(workspaceId, projectId, recipient, recordingId);
            CallSuggestion suggestion = requirePending(recording, suggestionId);
            CallReference call = callOf(recording);
            Applied applied = applyOne(userId, workspaceId, projectId, recipient, suggestion, request, call,
                    httpRequest);
            recording.replaceSuggestion(suggestion.appliedAs(applied.shown(), applied.change()));
            audited(ProjectEventType.NOTETAKER_SUGGESTION_APPLIED, userId, workspaceId, projectId, recipient,
                    recording, suggestion, httpRequest)
                    .detail("fields", keysOf(applied.change().fields()))
                    .detail("edited", String.valueOf(applied.change().edited()))
                    .record();
            return recording;
        }, workspaceId, projectId, candidateId, recordingId);
    }

    public CallSuggestionsResponse skip(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                        UUID recordingId, String suggestionId, HttpServletRequest httpRequest) {
        return decide(() -> {
            OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
            MeetingRecording recording = requireRecording(workspaceId, projectId, recipient, recordingId);
            CallSuggestion suggestion = requirePending(recording, suggestionId);
            recording.replaceSuggestion(suggestion.skipped());
            audited(ProjectEventType.NOTETAKER_SUGGESTION_SKIPPED, userId, workspaceId, projectId, recipient,
                    recording, suggestion, httpRequest).record();
            return recording;
        }, workspaceId, projectId, candidateId, recordingId);
    }

    /**
     * Undo: a skipped suggestion waits again; an applied one is put back through the same owner first, and refused
     * when the record moved on since — a later edit is never overwritten by an undo.
     */
    public CallSuggestionsResponse reopen(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                          UUID recordingId, String suggestionId, HttpServletRequest httpRequest) {
        return decide(() -> {
            OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
            MeetingRecording recording = requireRecording(workspaceId, projectId, recipient, recordingId);
            CallSuggestion suggestion = requireSuggestion(recording, suggestionId);
            if (suggestion.isPending()) {
                return recording;
            }
            if (suggestion.status() == CallSuggestionStatus.APPLIED) {
                revert(userId, workspaceId, projectId, recipient, suggestion, callOf(recording), httpRequest);
                audited(ProjectEventType.NOTETAKER_SUGGESTION_REVERTED, userId, workspaceId, projectId, recipient,
                        recording, suggestion, httpRequest)
                        .detail("fields", keysOf(suggestion.applied().fields()))
                        .record();
            }
            recording.replaceSuggestion(suggestion.reopened());
            return recording;
        }, workspaceId, projectId, candidateId, recordingId);
    }

    private List<CallSuggestion> readTranscript(MeetingRecording recording, CallHeldFields held) {
        try {
            return suggester.suggest(recording.getTranscript(),
                    MeetingNotesWorker.callStartOf(recording.getTranscript(), recording.getStartsAt()), held);
        } catch (ApiException refused) {
            throw refused;
        } catch (RuntimeException unusable) {
            // Spring AI's transient and non-transient failures, a timeout, or an answer still off-schema after the
            // repair attempts: the consultant may simply try again. Never the transcript in the log.
            log.warn("Suggest changes on recording {}: the model gave no usable answer ({})", recording.getId(),
                    unusable.getClass().getSimpleName());
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        }
    }

    /**
     * One decision in its own transaction. Two tabs deciding the same call at once collide on the recording's version;
     * the loser runs again over the fresh row, where a suggestion already decided answers that it was.
     */
    private CallSuggestionsResponse decide(Supplier<MeetingRecording> decision, UUID workspaceId, UUID projectId,
                                           UUID candidateId, UUID recordingId) {
        try {
            transactions.execute(status -> decision.get());
        } catch (ObjectOptimisticLockingFailureException raced) {
            try {
                transactions.execute(status -> decision.get());
            } catch (ObjectOptimisticLockingFailureException again) {
                throw ApiException.of(ErrorCode.CANDIDATE_ASSESSMENT_STALE);
            }
        }
        return read(workspaceId, projectId, candidateId, recordingId);
    }

    private Applied applyOne(UUID userId, UUID workspaceId, UUID projectId, OutreachRecipient recipient,
                             CallSuggestion suggestion, ApplyCallSuggestionRequest request, CallReference call,
                             HttpServletRequest httpRequest) {
        EditedSuggestionValue edit = request == null ? null : request.value();
        boolean edited = edit != null;
        UUID candidateId = recipient.candidateId();
        Instant now = clock.instant();
        return switch (suggestion.kind()) {
            case NOTICE_PERIOD -> {
                String label = noticeLabelOf(edited ? edit.text() : suggestion.value().text());
                ProfileFromCall change = new ProfileFromCall(Set.of(CallField.NOTICE_PERIOD), label, null, null,
                        null, null, null, null);
                ProfileFromCall before = updates.applyProfile(userId, workspaceId, projectId, candidateId, change,
                        call, httpRequest);
                yield new Applied(SuggestedValue.text(label), new AppliedChange(userId, now, edited,
                        List.of(CallField.NOTICE_PERIOD), before, change, null, null, null, false, null));
            }
            case CURRENT_PACKAGE -> {
                SuggestedValue pay = edited ? payOf(edit, suggestion.value()) : suggestion.value();
                Set<CallField> fields = packageFieldsOf(request == null ? null : request.packageFields(), pay);
                ProfileFromCall change = new ProfileFromCall(fields, null, pay.currency(), pay.baseSalary(),
                        pay.bonus(), pay.allowances(), null, null);
                ProfileFromCall before = updates.applyProfile(userId, workspaceId, projectId, candidateId, change,
                        call, httpRequest);
                yield new Applied(pay, new AppliedChange(userId, now, edited, sorted(fields), before, change, null,
                        null, null, false, null));
            }
            case LOCATION -> {
                SuggestedValue place = edited ? placeOf(edit) : suggestion.value();
                ProfileFromCall change = new ProfileFromCall(Set.of(CallField.LOCATION), null, null, null, null,
                        null, place.city(), place.country());
                ProfileFromCall before = updates.applyProfile(userId, workspaceId, projectId, candidateId, change,
                        call, httpRequest);
                yield new Applied(place, new AppliedChange(userId, now, edited, List.of(CallField.LOCATION),
                        before, change, null, null, null, false, null));
            }
            case OPEN_TO_RELOCATE -> {
                RelocationTagging tagging = updates.tagOpenToRelocate(userId, workspaceId, projectId, candidateId,
                        call, httpRequest);
                yield new Applied(suggestion.value(), new AppliedChange(userId, now, false,
                        List.of(CallField.OPEN_TO_RELOCATE), null, null, null, null, tagging.tagId(),
                        tagging.added(), null));
            }
            case MOTIVATION, EXPECTED_PACKAGE -> {
                String text = requiredText(edited ? edit.text() : suggestion.value().text());
                CallField field = suggestion.kind().fields().getFirst();
                SectionChange section = updates.appendToMotivation(userId, workspaceId, projectId, candidateId,
                        field, text, call, httpRequest);
                yield new Applied(SuggestedValue.text(text), new AppliedChange(userId, now, edited, List.of(field),
                        null, null, section.before(), section.after(), null, false, null));
            }
            case TASK -> {
                String title = taskTitleOf(edited ? edit.text() : suggestion.value().text());
                TaskResponse task = tasks.create(userId, workspaceId, projectId, candidateId,
                        new CreateTaskRequest(title, "follow_up", userId, now.plus(TASK_DUE_AFTER),
                                "From the call: " + Objects.toString(call.title(), "recorded call") + " · "
                                        + CALL_DATE.format(call.startsAt()), null, null),
                        httpRequest);
                updates.recordTaskFromCall(userId, workspaceId, projectId, candidateId, call, false);
                yield new Applied(SuggestedValue.text(title), new AppliedChange(userId, now, edited,
                        List.of(CallField.TASK), null, null, null, null, null, false, task.id()));
            }
        };
    }

    private void revert(UUID userId, UUID workspaceId, UUID projectId, OutreachRecipient recipient,
                        CallSuggestion suggestion, CallReference call, HttpServletRequest httpRequest) {
        AppliedChange applied = suggestion.applied();
        if (applied == null) {
            throw ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_UNAVAILABLE);
        }
        UUID candidateId = recipient.candidateId();
        switch (suggestion.kind()) {
            case NOTICE_PERIOD, CURRENT_PACKAGE, LOCATION -> updates.revertProfile(userId, workspaceId, projectId,
                    candidateId, applied.profileBefore(), applied.profileAfter(), call, httpRequest);
            case OPEN_TO_RELOCATE -> updates.untagOpenToRelocate(userId, workspaceId, projectId, candidateId,
                    new RelocationTagging(applied.tagId(), applied.tagAdded()), call, httpRequest);
            case MOTIVATION, EXPECTED_PACKAGE -> updates.revertMotivation(userId, workspaceId, projectId, candidateId,
                    applied.fields().getFirst(), new SectionChange(applied.sectionBefore(), applied.sectionAfter()),
                    call, httpRequest);
            case TASK -> {
                tasks.cancel(userId, workspaceId, applied.taskId(), httpRequest);
                updates.recordTaskFromCall(userId, workspaceId, projectId, candidateId, call, true);
            }
        }
    }

    private CallSuggestionsResponse responseOf(MeetingRecording recording, CallHeldFields held) {
        List<CallSuggestion> found = recording.getSuggestions() == null ? List.of() : recording.getSuggestions();
        return new CallSuggestionsResponse(recording.getId(), recording.getTitle(), recording.getStartsAt(),
                recording.getSuggestions() != null,
                recording.isSuggestible() && !recording.hasPendingSuggestions(),
                found.stream().map(suggestion -> new CallSuggestionResponse(suggestion.id(), suggestion.kind(),
                        suggestion.status(), suggestion.value(), currentOf(suggestion.kind(), held), suggestion.said(),
                        suggestion.at(), isAvailable(suggestion, held),
                        suggestion.applied() != null && suggestion.applied().edited())).toList());
    }

    private static SuggestedValue currentOf(CallSuggestionKind kind, CallHeldFields held) {
        return switch (kind) {
            case NOTICE_PERIOD -> SuggestedValue.text(held.noticePeriod());
            case CURRENT_PACKAGE -> SuggestedValue.pay(held.currency(), held.baseSalary(), held.bonus(),
                    held.allowances(), null);
            case LOCATION -> SuggestedValue.place(held.city(), held.country());
            case OPEN_TO_RELOCATE -> SuggestedValue.text(held.openToRelocate() ? "Open to relocate" : null);
            case MOTIVATION, EXPECTED_PACKAGE -> SuggestedValue.text(held.motivation());
            case TASK -> null;
        };
    }

    /** Only what the record still lets in: the tag must be live, and a package must not relabel a held figure. */
    private static boolean isAvailable(CallSuggestion suggestion, CallHeldFields held) {
        if (!suggestion.isPending()) {
            return true;
        }
        return switch (suggestion.kind()) {
            case OPEN_TO_RELOCATE -> held.relocationTagId() != null;
            case CURRENT_PACKAGE -> !mixesCurrencies(suggestion.value(), held);
            default -> true;
        };
    }

    private static boolean mixesCurrencies(SuggestedValue pay, CallHeldFields held) {
        if (held.currency() == null || held.currency().equalsIgnoreCase(pay.currency())) {
            return false;
        }
        return held.longTermIncentive() != null || (held.baseSalary() != null && pay.baseSalary() == null)
                || (held.bonus() != null && pay.bonus() == null)
                || (held.allowances() != null && pay.allowances() == null);
    }

    private static String noticeLabelOf(String text) {
        return Arrays.stream(NoticePeriod.values())
                .map(NoticePeriod::value)
                .filter(label -> label.equalsIgnoreCase(Objects.toString(text, "").strip()))
                .findFirst()
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "Choose a notice period: None, 1, 2, 3 or 6 months."));
    }

    private static SuggestedValue payOf(EditedSuggestionValue edit, SuggestedValue suggested) {
        String currency = Objects.toString(edit.currency(), Objects.toString(suggested.currency(), ""))
                .strip().toUpperCase(Locale.ROOT);
        boolean known = Currency.getAvailableCurrencies().stream()
                .anyMatch(each -> each.getCurrencyCode().equals(currency));
        if (!known) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Use a three-letter currency code, like AED.");
        }
        for (Long figure : new Long[] {edit.baseSalary(), edit.bonus(), edit.allowances()}) {
            if (figure != null && (figure < 0 || figure >= LARGEST_FIGURE)) {
                throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That figure doesn't look right.");
            }
        }
        return SuggestedValue.pay(currency, edit.baseSalary(), edit.bonus(), edit.allowances(), null);
    }

    private static Set<CallField> packageFieldsOf(List<String> asked, SuggestedValue pay) {
        Set<CallField> held = EnumSet.noneOf(CallField.class);
        if (pay.baseSalary() != null) {
            held.add(CallField.BASE_SALARY);
        }
        if (pay.bonus() != null) {
            held.add(CallField.BONUS);
        }
        if (pay.allowances() != null) {
            held.add(CallField.ALLOWANCES);
        }
        if (asked != null) {
            Set<CallField> chosen = asked.stream()
                    .map(token -> PACKAGE_FIELDS.stream().filter(field -> field.value().equals(token)).findFirst()
                            .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                                    "That is not a package figure")))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(CallField.class)));
            held.retainAll(chosen);
        }
        if (held.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Tick at least one figure to apply.");
        }
        return held;
    }

    private static SuggestedValue placeOf(EditedSuggestionValue edit) {
        String country = Countries.resolveSpelling(Objects.toString(edit.country(), "").strip())
                .map(found -> found.name())
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Choose a country."));
        return SuggestedValue.place(Countries.cityOf(edit.city()), country);
    }

    private static String requiredText(String text) {
        if (text == null || text.isBlank()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Write something to add, or skip it.");
        }
        return text.strip();
    }

    private static String taskTitleOf(String text) {
        String title = requiredText(text).replaceAll("\\s+", " ");
        if (title.length() > Task.MAX_TITLE) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Keep the task under " + Task.MAX_TITLE + " characters.");
        }
        return title;
    }

    private static List<CallField> sorted(Set<CallField> fields) {
        return fields.stream().sorted().toList();
    }

    private static String keysOf(List<CallField> fields) {
        return fields.stream().map(CallField::value).collect(Collectors.joining(","));
    }

    private AuditService.Builder audited(ProjectEventType type, UUID userId, UUID workspaceId, UUID projectId,
                                         OutreachRecipient recipient, MeetingRecording recording,
                                         CallSuggestion suggestion, HttpServletRequest httpRequest) {
        return audit.projectEvent(type, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", recipient.candidateId().toString())
                .detail("personId", recipient.personId().toString())
                .detail("recordingId", recording.getId().toString())
                .detailIfPresent("meetingId", recording.getMeetingId() == null ? null
                        : recording.getMeetingId().toString())
                .detail("kind", suggestion.kind().name());
    }

    private static CallReference callOf(MeetingRecording recording) {
        return new CallReference(recording.getId(), recording.getTitle(), recording.getStartsAt());
    }

    private static MeetingRecording requireSuggestible(MeetingRecording recording) {
        if (recording.getStatus() != NotetakerStatus.DONE) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_READY);
        }
        if (recording.getTranscript() == null) {
            throw ApiException.of(ErrorCode.NOTETAKER_TRANSCRIPT_GONE);
        }
        return recording;
    }

    private static CallSuggestion requireSuggestion(MeetingRecording recording, String suggestionId) {
        return (recording.getSuggestions() == null ? List.<CallSuggestion>of() : recording.getSuggestions()).stream()
                .filter(suggestion -> suggestion.id().equals(suggestionId))
                .findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private static CallSuggestion requirePending(MeetingRecording recording, String suggestionId) {
        CallSuggestion suggestion = requireSuggestion(recording, suggestionId);
        if (!suggestion.isPending()) {
            throw ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_DECIDED);
        }
        return suggestion;
    }

    private MeetingRecording requireRecording(UUID workspaceId, UUID projectId, OutreachRecipient recipient,
                                              UUID recordingId) {
        return recordings.findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId,
                        recipient.personId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private OutreachRecipient requireRecipient(UUID workspaceId, UUID projectId, UUID candidateId) {
        return people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private record Applied(SuggestedValue shown, AppliedChange change) {}
}
