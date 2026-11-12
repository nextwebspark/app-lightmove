package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CallField;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.ScorecardReportSection;
import app.lightmove.api.candidate.model.CallHeldFields;
import app.lightmove.api.candidate.model.CallReference;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateScorecard;
import app.lightmove.api.candidate.model.CandidateTag;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.ProfileFromCall;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.CandidateScorecardRepository;
import app.lightmove.api.candidate.repository.CandidateTagRepository;
import app.lightmove.api.common.time.service.DateTimeFormats;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.service.DateTimePreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code notetaker}'s way into the person's record for Suggest changes: what is held now, and the changes a researcher
 * applies from a recorded call, written through the record's own methods with one {@code UPDATED_FROM_CALL} line naming
 * who, which fields and which call. Nothing here depends back on {@code notetaker}.
 */
@Service
@RequiredArgsConstructor
public class CandidateCallUpdates {

    /** The starter tag a call's "happy to relocate" maps to; a workspace that renamed or retired it is offered none. */
    static final String OPEN_TO_RELOCATE = "Open to relocate";

    private static final String PERSON_TARGET = "person";

    private final ProjectRepository projects;
    private final CandidateRepository candidates;
    private final CandidateScorecardRepository scorecards;
    private final CandidateTagRepository tags;
    private final PersonActivityRecorder activity;
    private final AuditService audit;
    private final DateTimePreferenceService dateTimePreferences;

    @Transactional(readOnly = true)
    public CallHeldFields heldOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        Person person = candidate.getPerson();
        CandidateCompensation pay = person.compensation();
        UUID relocationTag = liveRelocationTag(workspaceId).map(CandidateTag::getId).orElse(null);
        String motivation = scorecards.findByCandidateIdAndWorkspaceId(candidate.getId(), workspaceId)
                .map(scorecard -> scorecard.getReport().textOf(ScorecardReportSection.MOTIVATION))
                .orElse(null);
        return new CallHeldFields(pay.noticePeriod(), pay.currency(), pay.baseSalary(), pay.bonus(), pay.allowances(),
                pay.longTermIncentive(), person.getLocationCity(), person.getLocationCountry(), motivation,
                relocationTag, relocationTag != null && person.getTagIds().contains(relocationTag));
    }

    /**
     * Writes the named fields. A package in another currency than the one on file is refused while a figure it would
     * not replace is still held in the old one: relabelling that figure's currency would restate somebody else's
     * number.
     */
    @Transactional
    public void applyProfile(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, ProfileFromCall change,
                             CallReference call, HttpServletRequest httpRequest) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        Person person = candidate.getPerson();
        if (change.touchesPackage() && person.compensation().wouldRelabel(change.currency(),
                change.fields().contains(CallField.BASE_SALARY), change.fields().contains(CallField.BONUS),
                change.fields().contains(CallField.ALLOWANCES))) {
            throw ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_UNAVAILABLE);
        }
        ProfileChanges changes = ProfileChanges.before(person);
        person.recordFromCall(change);
        line(candidate, userId, change.fields(), call, changes.after(person));
        audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("via", "CALL")
                .detail("recordingId", call.recordingId().toString())
                .record();
    }

    /**
     * Adds the call's words below whatever the Motivation section holds, never over it — one paragraph per field, in
     * the fields' order — and refuses them all when the section could not hold them.
     */
    @Transactional
    public void appendToMotivation(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                   Map<CallField, String> words, CallReference call,
                                   HttpServletRequest httpRequest) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        CandidateScorecard scorecard = scorecards.findByCandidateIdAndWorkspaceId(candidate.getId(), workspaceId)
                .orElseGet(() -> CandidateScorecard.startFor(candidate, workspaceId));
        String previous = scorecard.getReport().textOf(ScorecardReportSection.MOTIVATION);
        Map<CallField, String> ordered = new EnumMap<>(words);
        String callDate = DateTimeFormats.of(dateTimePreferences.workspaceDefaultsOf(workspaceId)).date(call.startsAt());
        String blocks = ordered.values().stream()
                .map(text -> "From the call on " + callDate + ": " + text.strip())
                .collect(Collectors.joining("\n\n"));
        String next = previous == null || previous.isBlank() ? blocks : previous.stripTrailing() + "\n\n" + blocks;
        if (next.length() > CandidateScorecard.MAX_REPORT_SECTION) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "The Motivation section is full. Shorten it on the Assessment tab, then apply this again.");
        }
        scorecard.writeSection(ScorecardReportSection.MOTIVATION, next, userId);
        scorecards.saveAndFlush(scorecard);
        line(candidate, userId, ordered.keySet(), call, List.of());
        audit.projectEvent(ProjectEventType.CANDIDATE_ASSESSMENT_SAVED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("via", "CALL")
                .detail("recordingId", call.recordingId().toString())
                .record();
    }

    /** The workspace's live "Open to relocate" tag on the person; someone already holding it keeps it as it was. */
    @Transactional
    public void tagOpenToRelocate(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                  CallReference call, HttpServletRequest httpRequest) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        CandidateTag tag = liveRelocationTag(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_UNAVAILABLE));
        Person person = candidate.getPerson();
        if (person.tag(tag.getId())) {
            activity.record(person, projectId, userId, PersonActivityKind.TAGGED,
                    PersonActivityDetails.of("tagId", tag.getId()).and("tag", tag.getLabel()));
            audit.event(ProjectEventType.PERSON_TAGGED).actor(userId).workspace(workspaceId)
                    .target(PERSON_TARGET, person.getId()).from(httpRequest)
                    .detail("tagId", tag.getId().toString())
                    .detail("via", "CALL")
                    .detail("recordingId", call.recordingId().toString())
                    .record();
        }
        line(candidate, userId, List.of(CallField.OPEN_TO_RELOCATE), call, List.of());
    }

    /** A task filed from a call writes its own task line; this one says it came from the call. */
    @Transactional
    public void recordTaskFromCall(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                   CallReference call) {
        line(requireCandidate(workspaceId, projectId, candidateId), userId, List.of(CallField.TASK), call, List.of());
    }

    private void line(Candidate candidate, UUID userId, Collection<CallField> fields, CallReference call,
                      List<Map<String, String>> changed) {
        String keys = fields.stream().sorted().map(CallField::value).collect(Collectors.joining(","));
        activity.record(candidate, userId, PersonActivityKind.UPDATED_FROM_CALL, PersonActivityDetails
                .of("fields", keys)
                .and("call", call.title())
                .and("callAt", call.startsAt().toString())
                .and("recordingId", call.recordingId())
                .and(ProfileChanges.DETAIL_KEY, changed.isEmpty() ? null : changed));
    }

    private Optional<CandidateTag> liveRelocationTag(UUID workspaceId) {
        return tags.findByLabel(workspaceId, OPEN_TO_RELOCATE).filter(tag -> !tag.isRetired());
    }

    private Candidate requireCandidate(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return candidates.requireInProject(candidateId, projectId);
    }
}
