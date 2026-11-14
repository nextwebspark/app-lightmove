package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ApproachKind;
import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.constant.RecentApproachTier;
import app.lightmove.api.candidate.dto.ApproachPositionResponse;
import app.lightmove.api.candidate.dto.ApproachTouchResponse;
import app.lightmove.api.candidate.dto.RecentApproachResponse;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.PersonTouch;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recently approached: who on the team has reached these people, on any position, and where else they are mapped —
 * the warning before an email, a sequence or an add. The caller's own touches on the position they are working from
 * are already in their conversation and are left out. A position the caller cannot open is named only as another
 * position, with its stage. It warns and never refuses.
 */
@Service
@RequiredArgsConstructor
public class RecentApproachReader {

    static final int MAX_TOUCHES = 10;
    static final int MAX_OUT_OF_PROCESS = 3;
    static final String PLACED_STATUS_KEY = "placed";

    private static final Set<PersonNoteKind> LOGGED_CONTACT = EnumSet.of(PersonNoteKind.CALL, PersonNoteKind.MEETING,
            PersonNoteKind.EMAIL, PersonNoteKind.LINKEDIN, PersonNoteKind.WHATSAPP);

    private static final Comparator<PersonTouch> REPLY_THEN_NEWEST = Comparator
            .comparing((PersonTouch touch) -> touch.kind() != ApproachKind.REPLIED)
            .thenComparing(PersonTouch::at, Comparator.reverseOrder());

    private final PersonApproachHistory history;
    private final PersonNoteRepository notes;
    private final CandidateRepository candidates;
    private final ProjectRepository projects;
    private final WorkspaceRepository workspaces;
    private final UserRepository users;
    private final ProjectAccess projectAccess;
    private final Clock clock;

    /** One answer per person asked about; {@code NONE} where there is nothing to say or the workspace turned it off. */
    @Transactional(readOnly = true)
    public Map<UUID, RecentApproachResponse> approachesOf(UUID callerId, UUID workspaceId, Collection<UUID> personIds,
                                                          UUID currentProjectId) {
        List<UUID> asked = personIds.stream().filter(Objects::nonNull).distinct().toList();
        int windowDays = workspaces.findById(workspaceId).map(Workspace::getRecentlyApproachedDays).orElse(0);
        if (asked.isEmpty() || windowDays == 0) {
            RecentApproachResponse none = none(windowDays);
            return asked.stream().collect(Collectors.toMap(Function.identity(), personId -> none));
        }
        Instant since = clock.instant().minus(Duration.ofDays(windowDays));

        Map<UUID, List<PersonTouch>> touchesByPerson = Stream.concat(
                        history.touchesOf(workspaceId, asked).stream(),
                        notes.findLoggedContacts(workspaceId, asked, LOGGED_CONTACT).stream()
                                .map(row -> new PersonTouch(row.getPersonId(), row.getProjectId(),
                                        row.getAuthorUserId(), ApproachKind.loggedAs(row.getKind()), row.getAt())))
                .filter(touch -> touch.at() != null)
                .filter(touch -> !isOwnHere(touch, callerId, currentProjectId))
                .filter(touch -> !(touch.kind().isOngoing() && Objects.equals(touch.projectId(), currentProjectId)))
                .collect(Collectors.groupingBy(PersonTouch::personId));
        Map<UUID, List<Candidate>> mappedByPerson = candidates.findPositionsOfPeople(workspaceId, asked).stream()
                .filter(row -> !row.getProjectId().equals(currentProjectId))
                .collect(Collectors.groupingBy(row -> row.getPerson().getId()));

        Set<UUID> projectIds = Stream.concat(
                        touchesByPerson.values().stream().flatMap(List::stream).map(PersonTouch::projectId),
                        mappedByPerson.values().stream().flatMap(List::stream).map(Candidate::getProjectId))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Project> mandates = projects.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        Set<UUID> viewable = projectAccess.projectsWithAction(callerId, workspaceId, List.copyOf(projectIds),
                ProjectAction.WORK_VIEW);
        Map<UUID, String> names = namesOf(Stream.concat(
                touchesByPerson.values().stream().flatMap(List::stream).map(PersonTouch::byUserId),
                mappedByPerson.values().stream().flatMap(List::stream).map(Candidate::getAddedBy)));

        TouchNaming naming = new TouchNaming(callerId, currentProjectId, mandates, viewable, names);
        Map<UUID, RecentApproachResponse> answers = new HashMap<>();
        for (UUID personId : asked) {
            List<PersonTouch> touches = touchesByPerson.getOrDefault(personId, List.of());
            List<PersonTouch> recent = touches.stream()
                    .filter(touch -> touch.kind().isOngoing() || !touch.at().isBefore(since))
                    .sorted(REPLY_THEN_NEWEST).limit(MAX_TOUCHES).toList();
            PersonTouch older = recent.isEmpty()
                    ? touches.stream().max(Comparator.comparing(PersonTouch::at)).orElse(null) : null;
            List<ApproachPositionResponse> elsewhere = elsewhereOf(
                    mappedByPerson.getOrDefault(personId, List.of()), mandates, viewable, names);
            answers.put(personId, new RecentApproachResponse(tierOf(recent, older, elsewhere), windowDays,
                    recent.stream().map(naming::of).toList(), elsewhere,
                    older == null ? null : naming.of(older)));
        }
        return answers;
    }

    @Transactional(readOnly = true)
    public RecentApproachResponse approachOf(UUID callerId, UUID workspaceId, UUID personId, UUID currentProjectId) {
        return approachesOf(callerId, workspaceId, List.of(personId), currentProjectId).get(personId);
    }

    private static boolean isOwnHere(PersonTouch touch, UUID callerId, UUID currentProjectId) {
        return Objects.equals(touch.byUserId(), callerId) && Objects.equals(touch.projectId(), currentProjectId);
    }

    private static RecentApproachTier tierOf(List<PersonTouch> recent, PersonTouch older,
                                             List<ApproachPositionResponse> elsewhere) {
        if (!recent.isEmpty() || elsewhere.stream().anyMatch(ApproachPositionResponse::inProcess)) {
            return RecentApproachTier.CAUTION;
        }
        return older != null || !elsewhere.isEmpty() ? RecentApproachTier.INFO : RecentApproachTier.NONE;
    }

    /** In process on a live position, all of them; out of process, the newest few. A closed position says neither. */
    private static List<ApproachPositionResponse> elsewhereOf(List<Candidate> mapped, Map<UUID, Project> mandates,
                                                              Set<UUID> viewable, Map<UUID, String> names) {
        List<Candidate> live = mapped.stream()
                .filter(row -> {
                    Project mandate = mandates.get(row.getProjectId());
                    return mandate != null && !mandate.isArchived();
                })
                .sorted(Comparator.comparing(Candidate::getStatusChangedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Stream<Candidate> inProcess = live.stream().filter(row -> row.getStatus().isInProcess());
        Stream<Candidate> outOfProcess = live.stream().filter(row -> row.getStatus().hasLeftTheRunning())
                .limit(MAX_OUT_OF_PROCESS);
        return Stream.concat(inProcess, outOfProcess)
                .map(row -> positionOf(row, mandates.get(row.getProjectId()), viewable.contains(row.getProjectId()),
                        names))
                .toList();
    }

    private static ApproachPositionResponse positionOf(Candidate row, Project mandate, boolean visible,
                                                       Map<UUID, String> names) {
        boolean placed = PLACED_STATUS_KEY.equals(row.getStatus().getStatusKey());
        String stage = row.getStatus().getStage().getLabel();
        if (!visible) {
            return new ApproachPositionResponse(null, null, null, null, stage, row.getStatus().isInProcess(), placed,
                    null, null, false);
        }
        return new ApproachPositionResponse(row.getProjectId(), mandate.getPositionTitle(), row.getStatus().getLabel(),
                row.getStatus().getColour().value(), stage, row.getStatus().isInProcess(), placed,
                names.get(row.getAddedBy()), row.getStatusChangedAt(), true);
    }

    private Map<UUID, String> namesOf(Stream<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(userIds.filter(Objects::nonNull).distinct().toList())
                .forEach(user -> names.put(user.getId(), user.getFullName()));
        return names;
    }

    private static RecentApproachResponse none(int windowDays) {
        return new RecentApproachResponse(RecentApproachTier.NONE, windowDays, List.of(), List.of(), null);
    }

    private record TouchNaming(UUID callerId, UUID currentProjectId, Map<UUID, Project> mandates,
                               Set<UUID> viewable, Map<UUID, String> names) {

        ApproachTouchResponse of(PersonTouch touch) {
            boolean here = Objects.equals(touch.projectId(), currentProjectId);
            boolean hidden = touch.projectId() != null && !here && !viewable.contains(touch.projectId());
            Project mandate = touch.projectId() == null || hidden ? null : mandates.get(touch.projectId());
            return new ApproachTouchResponse(touch.kind(), touch.byUserId(), names.get(touch.byUserId()), touch.at(),
                    hidden ? null : touch.projectId(), mandate == null ? null : mandate.getPositionTitle(), here,
                    Objects.equals(touch.byUserId(), callerId), hidden);
        }
    }
}
