package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ApproachKind;
import app.lightmove.api.candidate.constant.NoteOutcome;
import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.constant.RecentApproachTier;
import app.lightmove.api.candidate.dto.ApproachPositionResponse;
import app.lightmove.api.candidate.dto.ApproachTouchResponse;
import app.lightmove.api.candidate.dto.RecentApproachResponse;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.PersonNote;
import app.lightmove.api.candidate.model.PersonTouch;
import app.lightmove.api.candidate.model.PositionVisibility;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.service.ProjectLookup;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.service.WorkspaceLookup;
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
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recently approached: who on the team reached these people on any position, and where else they are mapped. */
@Service
@RequiredArgsConstructor
public class RecentApproachReader {

    static final int MAX_TOUCHES = 10;
    static final int MAX_OUT_OF_PROCESS = 3;

    private static final Set<PersonNoteKind> LOGGED_CONTACT = EnumSet.of(PersonNoteKind.CALL, PersonNoteKind.MEETING,
            PersonNoteKind.EMAIL, PersonNoteKind.LINKEDIN, PersonNoteKind.WHATSAPP);

    private static final List<String> LOGGED_CONTACT_KEYS = LOGGED_CONTACT.stream().map(Enum::name).toList();

    /** A call that rang out or reached someone else never reached the person, so it warns nobody. */
    private static final Set<NoteOutcome> UNANSWERED = EnumSet.of(NoteOutcome.NO_ANSWER, NoteOutcome.WRONG_NUMBER);

    private static final List<String> UNANSWERED_KEYS = UNANSWERED.stream().map(Enum::name).toList();

    private static final BinaryOperator<PersonTouch> NEWER =
            BinaryOperator.maxBy(Comparator.comparing(PersonTouch::at));

    private static final Comparator<PersonTouch> REPLY_THEN_ONGOING_THEN_NEWEST = Comparator
            .comparing((PersonTouch touch) -> !touch.kind().isReply())
            .thenComparing(touch -> !touch.kind().isOngoing())
            .thenComparing(PersonTouch::at, Comparator.reverseOrder());

    private final PersonApproachHistory history;
    private final PersonNoteRepository notes;
    private final CandidateRepository candidates;
    private final ProjectLookup projects;
    private final WorkspaceLookup workspaces;
    private final UserRepository users;
    private final PositionRedaction redaction;
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
                        history.touchesSince(workspaceId, asked, since).stream(),
                        notes.findLoggedContacts(workspaceId, asked, LOGGED_CONTACT, UNANSWERED, since).stream()
                                .map(row -> new PersonTouch(row.getPersonId(), row.getProjectId(),
                                        row.getAuthorUserId(), ApproachKind.loggedAs(row.getKind(), row.getOutcome()),
                                        row.getAt(), row.getOutcome())))
                .filter(touch -> counts(touch, callerId, currentProjectId))
                .collect(Collectors.groupingBy(PersonTouch::personId));
        Set<UUID> quiet = asked.stream().filter(personId -> !touchesByPerson.containsKey(personId))
                .collect(Collectors.toSet());
        Map<UUID, PersonTouch> olderByPerson = quiet.isEmpty() ? Map.of() : Stream.concat(
                        history.latestTouchesBefore(workspaceId, quiet, since).stream(),
                        notes.findLatestLoggedContactsBefore(workspaceId, quiet, LOGGED_CONTACT_KEYS, UNANSWERED_KEYS,
                                        since).stream()
                                .map(RecentApproachReader::touchOf))
                .filter(touch -> counts(touch, callerId, currentProjectId))
                .collect(Collectors.toMap(PersonTouch::personId, Function.identity(), NEWER));
        Map<UUID, List<Candidate>> mappedByPerson = candidates.findPositionsOfPeople(workspaceId, asked).stream()
                .filter(row -> !row.getProjectId().equals(currentProjectId))
                .collect(Collectors.groupingBy(row -> row.getPerson().getId()));

        List<PersonTouch> allTouches = Stream.concat(touchesByPerson.values().stream().flatMap(List::stream),
                olderByPerson.values().stream()).toList();
        Set<UUID> projectIds = Stream.concat(allTouches.stream().map(PersonTouch::projectId),
                        mappedByPerson.values().stream().flatMap(List::stream).map(Candidate::getProjectId))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Project> mandates = projects.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        PositionVisibility visibility = redaction.visibleTo(callerId, workspaceId, projectIds, currentProjectId);
        Map<UUID, String> names = namesOf(Stream.concat(allTouches.stream().map(PersonTouch::byUserId),
                mappedByPerson.values().stream().flatMap(List::stream).map(Candidate::getAddedBy)));

        TouchNaming naming = new TouchNaming(callerId, currentProjectId, mandates, visibility, names);
        Map<UUID, RecentApproachResponse> answers = new HashMap<>();
        for (UUID personId : asked) {
            List<PersonTouch> recent = newestOfEachApproach(touchesByPerson.getOrDefault(personId, List.of()));
            PersonTouch older = olderByPerson.get(personId);
            List<ApproachPositionResponse> elsewhere = elsewhereOf(
                    mappedByPerson.getOrDefault(personId, List.of()), mandates, visibility, names);
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

    /**
     * The caller's own touch here or on no position is already in their conversation, and a live sequence here is
     * this position's own; anything else counts, a touch on an archived position too.
     */
    private static boolean counts(PersonTouch touch, UUID callerId, UUID currentProjectId) {
        boolean here = Objects.equals(touch.projectId(), currentProjectId);
        boolean ownHere = Objects.equals(touch.byUserId(), callerId) && (touch.projectId() == null || here);
        return touch.at() != null && !ownHere && !(touch.kind().isOngoing() && here);
    }

    /** One line per teammate, channel and position, so one sequence's steps never crowd out the rest of the team. */
    private static List<PersonTouch> newestOfEachApproach(List<PersonTouch> touches) {
        return touches.stream()
                .collect(Collectors.toMap(touch -> new ApproachKey(touch.byUserId(), touch.projectId(), touch.kind()),
                        Function.identity(), NEWER))
                .values().stream().sorted(REPLY_THEN_ONGOING_THEN_NEWEST).limit(MAX_TOUCHES).toList();
    }

    private static PersonTouch touchOf(PersonNote note) {
        return new PersonTouch(note.getPersonId(), note.getProjectId(), note.getAuthorUserId(),
                ApproachKind.loggedAs(note.getKind(), note.getOutcome()), note.happenedAt(), note.getOutcome());
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
                                                              PositionVisibility visibility,
                                                              Map<UUID, String> names) {
        List<Candidate> live = mapped.stream()
                .filter(row -> {
                    Project mandate = mandates.get(row.getProjectId());
                    return mandate != null && !mandate.isArchived();
                })
                .sorted(Comparator.comparing(Candidate::getStatusChangedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Stream<Candidate> inProcess = live.stream().filter(row -> !row.getStatus().hasLeftTheRunning());
        Stream<Candidate> outOfProcess = live.stream().filter(row -> row.getStatus().hasLeftTheRunning())
                .limit(MAX_OUT_OF_PROCESS);
        return Stream.concat(inProcess, outOfProcess)
                .map(row -> positionOf(row, mandates.get(row.getProjectId()),
                        !visibility.isHidden(row.getProjectId()), names))
                .toList();
    }

    private static ApproachPositionResponse positionOf(Candidate row, Project mandate, boolean visible,
                                                       Map<UUID, String> names) {
        boolean inProcess = !row.getStatus().hasLeftTheRunning();
        String stage = row.getStatus().getStage().getLabel();
        if (!visible) {
            return new ApproachPositionResponse(null, null, null, null, stage, inProcess, false, null, null, false);
        }
        return new ApproachPositionResponse(row.getProjectId(), mandate.getPositionTitle(), row.getStatus().getLabel(),
                row.getStatus().getColour().value(), stage, inProcess,
                row.getStatus().isPlacement(),
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

    private record ApproachKey(UUID byUserId, UUID projectId, ApproachKind kind) {}

    private record TouchNaming(UUID callerId, UUID currentProjectId, Map<UUID, Project> mandates,
                               PositionVisibility visibility, Map<UUID, String> names) {

        ApproachTouchResponse of(PersonTouch touch) {
            boolean here = Objects.equals(touch.projectId(), currentProjectId);
            boolean hidden = visibility.isHidden(touch.projectId());
            Project mandate = touch.projectId() == null || hidden ? null : mandates.get(touch.projectId());
            return new ApproachTouchResponse(touch.kind(), touch.byUserId(), names.get(touch.byUserId()), touch.at(),
                    hidden ? null : touch.projectId(), mandate == null ? null : mandate.getPositionTitle(), here,
                    Objects.equals(touch.byUserId(), callerId), hidden,
                    touch.outcome() == null ? null : touch.outcome().value());
        }
    }
}
