package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.NoteOutcome;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.dto.PersonNoteResponse;
import app.lightmove.api.candidate.dto.WritePersonNoteRequest;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.LatestNoteRow;
import app.lightmove.api.candidate.model.NoteOccurrence;
import app.lightmove.api.candidate.model.NoteViewpoint;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonNote;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.DeletedPersonNoteRow;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.common.constant.NoteFormat;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's notes, read by staff only (decision D1) and only as far as the caller's {@link NoteViewpoint} reads.
 * Anyone who reaches a note may pin it; only its author, or a holder of {@code WORKSPACE_MANAGE}, may change,
 * remove or restore it. Every write is a timeline line naming the note, never quoting it. A removed note is kept
 * out of every read until it is restored or {@link PersonNotePurge} deletes it, after which nothing of it is left.
 * A note about a position may mention its team ({@link NoteMentions}); one about no position mentions nobody.
 */
@Service
@RequiredArgsConstructor
public class PersonNoteService {

    private static final String PERSON_TARGET = "person";

    private final PersonRepository people;
    private final PersonNoteRepository notes;
    private final CandidateRepository candidates;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final WorkspaceAccess workspaceAccess;
    private final ProjectAccess projectAccess;
    private final PersonActivityRecorder activity;
    private final NoteMentions mentions;
    private final AuditService audit;
    private final LoggedNoteRules loggedNotes;
    private final PositionRedaction positionRedaction;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PersonNoteResponse> list(UUID userId, UUID workspaceId, UUID personId, NoteViewpoint viewpoint) {
        people.requireInWorkspace(personId, workspaceId);
        List<PersonNote> found = notes.findReadablePinnedFirst(workspaceId, personId, viewpoint);
        boolean mayEditAny = mayEditAny(userId, workspaceId);
        Map<UUID, User> authors = usersOf(found);
        List<PersonNoteResponse> listed = found.stream()
                .map(note -> toDto(note, authors, mayEditAny || note.isWrittenBy(userId))).toList();
        return viewpoint.isWorkspace() ? positionRedaction.notesFor(userId, workspaceId, listed) : listed;
    }

    /**
     * {@code aboutProjectId} is the position the note is about, null for one about the person on every
     * position: under a position, the route's own unless the note is for all positions; on the workspace's
     * routes, the request's. Reading the pool is any staff member's (D2), but filing a note against a mandate
     * is work on it, so the caller needs that mandate's seat, and the person must be mapped there.
     */
    @Transactional
    public PersonNoteResponse write(UUID userId, UUID workspaceId, UUID personId, UUID aboutProjectId,
                                    WritePersonNoteRequest request, HttpServletRequest httpRequest) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        PersonNoteKind kind = kindOf(request, PersonNoteKind.GENERAL);
        loggedNotes.refuseLoggedEmail(kind, null);
        if (aboutProjectId != null) {
            projectAccess.requireAction(userId, workspaceId, aboutProjectId, ProjectAction.WORK_EXECUTE);
            if (!candidates.existsByProjectIdAndPersonId(aboutProjectId, personId)) {
                throw ApiException.of(ErrorCode.NOT_FOUND);
            }
        }
        NoteOutcome outcome = loggedNotes.outcomeOf(request, kind, null, request.bodyText());
        NoteOccurrence occurrence = loggedNotes.occurrenceOf(request, kind, workspaceId, aboutProjectId, personId);
        PersonNote note = save(person, aboutProjectId, kind, outcome, request.bodyText(), formatOf(request), userId,
                occurrence);
        mentions.keep(note, userId);
        audit.event(ProjectEventType.PERSON_NOTE_ADDED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("noteId", note.getId().toString())
                .record();
        return toDto(note, usersOf(List.of(note)), true);
    }

    @Transactional
    public PersonNoteResponse revise(UUID userId, UUID workspaceId, UUID personId, NoteViewpoint viewpoint,
                                     UUID noteId, WritePersonNoteRequest request, HttpServletRequest httpRequest) {
        people.requireInWorkspace(personId, workspaceId);
        PersonNote note = requireEditable(userId, workspaceId, personId, viewpoint, noteId);
        PersonNoteKind kind = kindOf(request, note.getKind());
        loggedNotes.refuseLoggedEmail(kind, note.getKind());
        NoteOccurrence current = new NoteOccurrence(note.getOccurredAt(), note.getTitle(), note.getMeetingId(),
                note.getRecordingId());
        String body = request.bodyTextOr(note.getBody());
        NoteOutcome outcome = loggedNotes.outcomeOf(request, kind, note.getOutcome(), body);
        NoteOccurrence occurrence = loggedNotes.revisedOccurrence(current, request, kind, workspaceId,
                note.getProjectId(), personId);
        note.revise(kind, outcome, body, revisedFormatOf(request, note.getBodyFormat()), userId);
        note.placeAt(occurrence);
        mentions.keep(note, userId);
        audit.event(ProjectEventType.PERSON_NOTE_EDITED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("noteId", noteId.toString())
                .record();
        return toDto(note, usersOf(List.of(note)), true);
    }

    @Transactional
    public void remove(UUID userId, UUID workspaceId, UUID personId, NoteViewpoint viewpoint, UUID noteId,
                       HttpServletRequest httpRequest) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        PersonNote note = requireEditable(userId, workspaceId, personId, viewpoint, noteId);
        activity.record(person, note.getProjectId(), userId, PersonActivityKind.NOTE_REMOVED,
                PersonActivityDetails.of("noteId", note.getId()).and("kind", note.getKind())
                        .and("outcome", note.getOutcome()));
        note.discard(userId, clock.instant());
        audit.event(ProjectEventType.PERSON_NOTE_REMOVED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("noteId", noteId.toString())
                .record();
    }

    /**
     * Takes a delete back while the note is still held. Its mentions were never dropped, so nobody is told twice.
     * A note already live answers as it is, so a second press of Undo, or two at once, records one restore; and
     * where a door has filed the same words again since under the same author, that copy answers and the deleted one is
     * left to the purge. A door files no outcome, so a log that says how it went is always restored itself.
     */
    @Transactional
    public PersonNoteResponse restore(UUID userId, UUID workspaceId, UUID personId, NoteViewpoint viewpoint,
                                      UUID noteId, HttpServletRequest httpRequest) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        DeletedPersonNoteRow deleted = notes.findDeleted(noteId, workspaceId, personId).orElse(null);
        if (deleted == null) {
            return asItStands(userId, workspaceId, notes.requireReadable(noteId, workspaceId, personId, viewpoint));
        }
        if (!viewpoint.reads(deleted.getProjectId())) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        if (!deleted.getAuthorUserId().equals(userId) && !mayEditAny(userId, workspaceId)) {
            throw ApiException.of(ErrorCode.PERSON_NOTE_NOT_YOURS);
        }
        // Only a door's note has a door's twin. Matching on the words alone once answered an Undo of a meeting note
        // with a general note in the same words, and left the meeting note to the purge. Trap: deleted_at is stamped
        // by the injected Clock and created_at by Hibernate's system clock; the two agree only while the Clock bean is
        // the system clock, so a fixed or offset Clock would hide every door's copy here.
        PersonNote twin = !deleted.isFiledByADoor() ? null
                : notes.findDoorRefiledSince(workspaceId, personId, deleted.getProjectId(), deleted.getBody(),
                        deleted.getAuthorUserId(), deleted.getDeletedAt(), Limit.of(1)).stream().findFirst()
                        .orElse(null);
        if (twin != null) {
            return asItStands(userId, workspaceId, twin);
        }
        if (notes.restore(noteId, workspaceId, personId) == 0) {
            return asItStands(userId, workspaceId, notes.requireReadable(noteId, workspaceId, personId, viewpoint));
        }
        PersonNote note = notes.requireReadable(noteId, workspaceId, personId, viewpoint);
        activity.record(person, note.getProjectId(), userId, PersonActivityKind.NOTE_RESTORED,
                PersonActivityDetails.of("noteId", note.getId()).and("kind", note.getKind())
                        .and("outcome", note.getOutcome()));
        audit.event(ProjectEventType.PERSON_NOTE_RESTORED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("noteId", noteId.toString())
                .record();
        return toDto(note, usersOf(List.of(note)), true);
    }

    private PersonNoteResponse asItStands(UUID userId, UUID workspaceId, PersonNote note) {
        return toDto(note, usersOf(List.of(note)), mayEdit(userId, workspaceId, note));
    }

    /** Pinning orders the list; it is nobody's edit, so it leaves no timeline line, only an audit event. */
    @Transactional
    public PersonNoteResponse pin(UUID userId, UUID workspaceId, UUID personId, NoteViewpoint viewpoint,
                                  UUID noteId, boolean pinned, HttpServletRequest httpRequest) {
        people.requireInWorkspace(personId, workspaceId);
        PersonNote note = notes.requireReadable(noteId, workspaceId, personId, viewpoint);
        note.pin(pinned);
        audit.event(ProjectEventType.PERSON_NOTE_PINNED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("noteId", noteId.toString())
                .detail("pinned", Boolean.toString(pinned))
                .record();
        return toDto(note, usersOf(List.of(note)), mayEdit(userId, workspaceId, note));
    }

    /** When each of these people's newest note this position reads happened, for the Outreach queue's last activity. */
    @Transactional(readOnly = true)
    public Map<UUID, Instant> latestNoteAtOfEach(UUID workspaceId, UUID projectId, Collection<UUID> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return notes.findLatestNoteOfEachPerson(workspaceId, projectId, personIds, clock.instant()).stream()
                .collect(Collectors.toMap(LatestNoteRow::getPersonId, LatestNoteRow::getAt));
    }

    /**
     * A note a door carried in with the executive — the drawer's add form, the plugin, a spreadsheet's
     * Note column — filed as a general note about the mandate. The same words already there are not filed
     * twice, which is what keeps a re-imported sheet from stacking copies.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fileFromDoor(Candidate candidate, UUID userId, String body) {
        String text = body == null ? "" : body.strip();
        Person person = candidate.getPerson();
        if (text.isEmpty() || notes.existsByWorkspaceIdAndPersonIdAndProjectIdAndBody(person.getWorkspaceId(),
                person.getId(), candidate.getProjectId(), text)) {
            return;
        }
        save(person, candidate.getProjectId(), PersonNoteKind.GENERAL, null, text, NoteFormat.PLAIN, userId,
                NoteOccurrence.NONE);
    }

    /**
     * A call the Notetaker recorded, filed as one Meeting note in its scheduler's name: about the position while the
     * person is still mapped there, about the person once they are not.
     */
    @Transactional
    public UUID fileMeetingNote(UUID workspaceId, UUID personId, UUID projectId, UUID authorId, String markdown) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        UUID aboutProjectId = projectId != null && candidates.existsByProjectIdAndPersonId(projectId, personId)
                ? projectId : null;
        return save(person, aboutProjectId, PersonNoteKind.MEETING, null, markdown.strip(), NoteFormat.MARKDOWN,
                authorId, NoteOccurrence.NONE).getId();
    }

    private PersonNote save(Person person, UUID projectId, PersonNoteKind kind, NoteOutcome outcome, String body,
                            NoteFormat format, UUID author, NoteOccurrence occurrence) {
        String title = projectId == null ? null
                : projects.findById(projectId).map(Project::getPositionTitle).orElse(null);
        PersonNote written = PersonNote.written(person, projectId, title, kind, outcome, body, format, author);
        written.placeAt(occurrence);
        PersonNote note = notes.save(written);
        activity.record(person, projectId, author, PersonActivityKind.NOTE_ADDED,
                PersonActivityDetails.of("noteId", note.getId()).and("kind", kind).and("outcome", outcome));
        return note;
    }

    private PersonNote requireEditable(UUID userId, UUID workspaceId, UUID personId, NoteViewpoint viewpoint,
                                       UUID noteId) {
        PersonNote note = notes.requireReadable(noteId, workspaceId, personId, viewpoint);
        if (!mayEdit(userId, workspaceId, note)) {
            throw ApiException.of(ErrorCode.PERSON_NOTE_NOT_YOURS);
        }
        return note;
    }

    private boolean mayEdit(UUID userId, UUID workspaceId, PersonNote note) {
        return note.isWrittenBy(userId) || mayEditAny(userId, workspaceId);
    }

    private boolean mayEditAny(UUID userId, UUID workspaceId) {
        return workspaceAccess.holdsAction(userId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
    }

    /** A note sent with no kind: a plain one when written, the kind it already has when edited. */
    private static PersonNoteKind kindOf(WritePersonNoteRequest request, PersonNoteKind absent) {
        if (request.kind() == null || request.kind().isBlank()) {
            return absent;
        }
        return ApiValueEnum.require(PersonNoteKind.class, request.kind(), "note kind");
    }

    private static NoteFormat formatOf(WritePersonNoteRequest request) {
        return ApiValueEnum.parse(NoteFormat.class, request.format(), NoteFormat.PLAIN, "note format");
    }

    /**
     * Kept words keep the format they were written in: an outcome-only edit once re-read a plain call's {@code *} and
     * {@code 1.} as Markdown, and one sending no format turned a Markdown note's headings into raw {@code ##}.
     */
    private static NoteFormat revisedFormatOf(WritePersonNoteRequest request, NoteFormat current) {
        if (request.body() == null) {
            return current;
        }
        return ApiValueEnum.parse(NoteFormat.class, request.format(), current, "note format");
    }

    private Map<UUID, User> usersOf(List<PersonNote> found) {
        List<UUID> ids = found.stream()
                .flatMap(note -> Stream.of(note.getAuthorUserId(), note.getEditedBy()))
                .filter(Objects::nonNull).distinct().toList();
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static PersonNoteResponse toDto(PersonNote note, Map<UUID, User> users, boolean editable) {
        User author = users.get(note.getAuthorUserId());
        User editor = note.getEditedBy() == null ? null : users.get(note.getEditedBy());
        return new PersonNoteResponse(note.getId(), note.getKind().value(), note.getBody(),
                note.getBodyFormat().value(), note.isPinned(),
                note.getProjectId(), note.getProjectTitle(), note.getAuthorUserId(),
                author == null ? null : author.getFullName(), author == null ? null : author.getAvatarUrl(),
                note.getCreatedAt(), note.getEditedAt(), editor == null ? null : editor.getFullName(), editable,
                note.happenedAt(), note.getTitle(), note.getMeetingId(), note.getRecordingId(),
                note.getOutcome() == null ? null : note.getOutcome().value());
    }
}
