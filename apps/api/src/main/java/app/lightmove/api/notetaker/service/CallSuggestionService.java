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
import app.lightmove.api.candidate.service.CandidateCallUpdates;
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
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.dto.ApplyCallSuggestionRequest;
import app.lightmove.api.notetaker.dto.CallSuggestionResponse;
import app.lightmove.api.notetaker.dto.CallSuggestionsResponse;
import app.lightmove.api.notetaker.dto.EditedSuggestionValue;
import app.lightmove.api.notetaker.dto.ReviewCallSuggestionsRequest;
import app.lightmove.api.notetaker.model.AppliedChange;
import app.lightmove.api.notetaker.model.CallSuggestion;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.SuggestedValue;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.task.dto.CreateTaskRequest;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Suggest changes on a recorded call: one press asks the model to read the transcript against the person's record,
 * and a researcher then reviews what it kept in one go — each suggestion applied as chosen or skipped. Nothing reaches
 * the record unless someone applies it, and every apply writes through the field's own owner — the person, the
 * assessment, the tag catalogue, the task list — so scope, validation and the timeline stay where they live. Once
 * reviewed a change is the record's: a later correction is made there, never by undoing it here.
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
    private static final Set<CallSuggestionKind> PROFILE_KINDS = EnumSet.of(CallSuggestionKind.NOTICE_PERIOD,
            CallSuggestionKind.CURRENT_PACKAGE, CallSuggestionKind.LOCATION);
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
                        lockRecording(workspaceId, projectId, recipient, recordingId));
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

    /**
     * The researcher's review, in one transaction on the recording row locked first: every waiting suggestion named in
     * {@code apply} is applied as chosen, every other one skipped. Each choice is read and checked before anything is
     * written, so one refused change writes nothing at all; two tabs reviewing one call queue on the lock, and the
     * second finds nothing waiting.
     */
    public CallSuggestionsResponse review(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                          UUID recordingId, ReviewCallSuggestionsRequest request,
                                          HttpServletRequest httpRequest) {
        transactions.executeWithoutResult(status -> {
            OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
            MeetingRecording recording = lockRecording(workspaceId, projectId, recipient, recordingId);
            Map<String, ApplyCallSuggestionRequest> picked = pickedOf(recording, request);
            List<CallSuggestion> waiting = recording.getSuggestions().stream().filter(CallSuggestion::isPending)
                    .toList();
            CallHeldFields held = updates.heldOf(workspaceId, projectId, recipient.candidateId());
            List<ChosenChange> chosen = waiting.stream()
                    .filter(suggestion -> picked.containsKey(suggestion.id()))
                    .map(suggestion -> resolve(suggestion, picked.get(suggestion.id()), held))
                    .toList();
            write(userId, workspaceId, projectId, recipient.candidateId(), chosen, callOf(recording), httpRequest);
            Instant now = clock.instant();
            Map<String, ChosenChange> chosenById = chosen.stream()
                    .collect(Collectors.toMap(each -> each.suggestion().id(), each -> each));
            for (CallSuggestion suggestion : waiting) {
                ChosenChange change = chosenById.get(suggestion.id());
                if (change == null) {
                    recording.replaceSuggestion(suggestion.skipped());
                    audited(ProjectEventType.NOTETAKER_SUGGESTION_SKIPPED, userId, workspaceId, projectId, recipient,
                            recording, suggestion, httpRequest).record();
                } else {
                    recording.replaceSuggestion(suggestion.appliedAs(change.value(),
                            new AppliedChange(userId, now, change.edited(), change.fields())));
                    audited(ProjectEventType.NOTETAKER_SUGGESTION_APPLIED, userId, workspaceId, projectId,
                            recipient, recording, suggestion, httpRequest)
                            .detail("fields", keysOf(change.fields()))
                            .detail("edited", String.valueOf(change.edited()))
                            .record();
                }
            }
        });
        return read(workspaceId, projectId, candidateId, recordingId);
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

    private static Map<String, ApplyCallSuggestionRequest> pickedOf(MeetingRecording recording,
                                                                    ReviewCallSuggestionsRequest request) {
        if (!recording.hasPendingSuggestions()) {
            throw ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_DECIDED);
        }
        Map<String, ApplyCallSuggestionRequest> picked = new HashMap<>();
        for (ApplyCallSuggestionRequest each : request.apply()) {
            requirePending(recording, each.id());
            if (picked.putIfAbsent(each.id(), each) != null) {
                throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Each change can be applied once.");
            }
        }
        return picked;
    }

    /** A refusal of what the researcher chose names the change it belongs to, so the review shows it on that row. */
    private static ChosenChange resolve(CallSuggestion suggestion, ApplyCallSuggestionRequest request,
                                        CallHeldFields held) {
        try {
            return chosenAs(suggestion, request, held);
        } catch (ApiException refused) {
            throw onRow(suggestion, refused);
        }
    }

    private static ChosenChange chosenAs(CallSuggestion suggestion, ApplyCallSuggestionRequest request,
                                         CallHeldFields held) {
        EditedSuggestionValue edit = request.value();
        boolean edited = edit != null;
        return switch (suggestion.kind()) {
            case NOTICE_PERIOD -> new ChosenChange(suggestion,
                    SuggestedValue.text(noticeLabelOf(edited ? edit.text() : suggestion.value().text())), edited,
                    List.of(CallField.NOTICE_PERIOD));
            case CURRENT_PACKAGE -> {
                SuggestedValue pay = edited ? payOf(edit, suggestion.value()) : suggestion.value();
                Set<CallField> fields = packageFieldsOf(request.packageFields(), pay);
                if (held.compensation().wouldRelabel(pay.currency(), fields.contains(CallField.BASE_SALARY),
                        fields.contains(CallField.BONUS), fields.contains(CallField.ALLOWANCES))) {
                    throw ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_UNAVAILABLE);
                }
                yield new ChosenChange(suggestion, pay, edited, sorted(fields));
            }
            case LOCATION -> new ChosenChange(suggestion, edited ? placeOf(edit) : suggestion.value(), edited,
                    List.of(CallField.LOCATION));
            case OPEN_TO_RELOCATE -> {
                if (held.relocationTagId() == null) {
                    throw ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_UNAVAILABLE);
                }
                yield new ChosenChange(suggestion, suggestion.value(), false, List.of(CallField.OPEN_TO_RELOCATE));
            }
            case MOTIVATION, EXPECTED_PACKAGE -> new ChosenChange(suggestion,
                    SuggestedValue.text(requiredText(edited ? edit.text() : suggestion.value().text())), edited,
                    suggestion.kind().fields());
            case TASK -> new ChosenChange(suggestion,
                    SuggestedValue.text(taskTitleOf(edited ? edit.text() : suggestion.value().text())), edited,
                    List.of(CallField.TASK));
        };
    }

    /**
     * Writes every chosen change through its field's owner. The Motivation section goes first: it is the one write
     * that can still refuse what was chosen (a section too full for the call's words), and refusing before any other
     * write leaves nothing half-applied behind an audit line.
     */
    private void write(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, List<ChosenChange> chosen,
                       CallReference call, HttpServletRequest httpRequest) {
        Map<CallField, String> words = new EnumMap<>(CallField.class);
        chosen.stream().filter(each -> each.suggestion().kind() == CallSuggestionKind.MOTIVATION
                        || each.suggestion().kind() == CallSuggestionKind.EXPECTED_PACKAGE)
                .forEach(each -> words.put(each.fields().getFirst(), each.value().text()));
        if (!words.isEmpty()) {
            try {
                updates.appendToMotivation(userId, workspaceId, projectId, candidateId, words, call, httpRequest);
            } catch (ApiException refused) {
                throw onRow(chosen.stream().filter(each -> words.containsKey(each.fields().getFirst()))
                        .findFirst().orElseThrow().suggestion(), refused);
            }
        }
        ProfileFromCall profile = profileOf(chosen);
        if (!profile.fields().isEmpty()) {
            updates.applyProfile(userId, workspaceId, projectId, candidateId, profile, call, httpRequest);
        }
        if (chosen.stream().anyMatch(each -> each.suggestion().kind() == CallSuggestionKind.OPEN_TO_RELOCATE)) {
            updates.tagOpenToRelocate(userId, workspaceId, projectId, candidateId, call, httpRequest);
        }
        List<ChosenChange> taskChanges = chosen.stream()
                .filter(each -> each.suggestion().kind() == CallSuggestionKind.TASK).toList();
        Instant now = clock.instant();
        for (ChosenChange task : taskChanges) {
            tasks.create(userId, workspaceId, projectId, candidateId,
                    new CreateTaskRequest(task.value().text(), "follow_up", userId, now.plus(TASK_DUE_AFTER),
                            "From the call: " + Objects.toString(call.title(), "recorded call") + " · "
                                    + CALL_DATE.format(call.startsAt()), null, null),
                    httpRequest);
        }
        if (!taskChanges.isEmpty()) {
            updates.recordTaskFromCall(userId, workspaceId, projectId, candidateId, call);
        }
    }

    /** The notice period, package and location chosen, as one write of the person's own fields. */
    private static ProfileFromCall profileOf(List<ChosenChange> chosen) {
        SuggestedValue notice = valueOf(chosen, CallSuggestionKind.NOTICE_PERIOD);
        SuggestedValue pay = valueOf(chosen, CallSuggestionKind.CURRENT_PACKAGE);
        SuggestedValue place = valueOf(chosen, CallSuggestionKind.LOCATION);
        Set<CallField> fields = chosen.stream()
                .filter(each -> PROFILE_KINDS.contains(each.suggestion().kind()))
                .flatMap(each -> each.fields().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CallField.class)));
        return new ProfileFromCall(fields, notice.text(), pay.currency(), pay.baseSalary(), pay.bonus(),
                pay.allowances(), place.city(), place.country());
    }

    private static SuggestedValue valueOf(List<ChosenChange> chosen, CallSuggestionKind kind) {
        return chosen.stream().filter(each -> each.suggestion().kind() == kind).map(ChosenChange::value)
                .findFirst().orElse(SuggestedValue.text(null));
    }

    private static ApiException onRow(CallSuggestion suggestion, ApiException refused) {
        if (refused.getCode() != ErrorCode.VALIDATION_FAILED || refused.getClientDetail() == null) {
            return refused;
        }
        return ApiException.withField(ErrorCode.VALIDATION_FAILED, suggestion.id(), refused.getClientDetail());
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
            case CURRENT_PACKAGE -> !held.compensation().wouldRelabel(suggestion.value().currency(),
                    suggestion.value().baseSalary() != null, suggestion.value().bonus() != null,
                    suggestion.value().allowances() != null);
            default -> true;
        };
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
            if (figure != null && (figure < 0 || figure >= SuggestedValue.LARGEST_FIGURE)) {
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

    private MeetingRecording lockRecording(UUID workspaceId, UUID projectId, OutreachRecipient recipient,
                                           UUID recordingId) {
        return recordings.lockForDecision(recordingId, workspaceId, projectId, recipient.personId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private OutreachRecipient requireRecipient(UUID workspaceId, UUID projectId, UUID candidateId) {
        return people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** One waiting suggestion the review applies: the value it takes, whether it was edited, and the fields it writes. */
    private record ChosenChange(CallSuggestion suggestion, SuggestedValue value, boolean edited,
                                List<CallField> fields) {}
}
