package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.dto.PersonNoteResponse;
import app.lightmove.api.candidate.dto.WritePersonNoteRequest;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonNote;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's notes, shared by every mandate that maps them and read by staff only (decision D1). Anyone
 * who reaches the person may write one or pin one; only its author, or a holder of
 * {@code WORKSPACE_MANAGE}, may change or remove it. Every write is a timeline line naming the note,
 * never quoting it, so a removed note leaves no copy of its words behind.
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
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<PersonNoteResponse> list(UUID userId, UUID workspaceId, UUID personId) {
        people.requireInWorkspace(personId, workspaceId);
        List<PersonNote> found = notes.findByWorkspaceIdAndPersonIdOrderByPinnedDescCreatedAtDesc(workspaceId,
                personId);
        boolean mayEditAny = mayEditAny(userId, workspaceId);
        Map<UUID, User> authors = usersOf(found);
        return found.stream().map(note -> toDto(note, authors, mayEditAny || note.isWrittenBy(userId))).toList();
    }

    /**
     * {@code aboutProjectId} is the position the note is about: the route's own under a position, the
     * request's on the workspace's routes. Reading the pool is any staff member's (D2), but filing a note
     * against a mandate is work on it, so the caller needs that mandate's seat, and the person must be
     * mapped there.
     */
    @Transactional
    public PersonNoteResponse write(UUID userId, UUID workspaceId, UUID personId, UUID aboutProjectId,
                                    WritePersonNoteRequest request, HttpServletRequest httpRequest) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        PersonNoteKind kind = kindOf(request);
        if (aboutProjectId != null) {
            projectAccess.requireAction(userId, workspaceId, aboutProjectId, ProjectAction.WORK_EXECUTE);
            if (!candidates.existsByProjectIdAndPersonId(aboutProjectId, personId)) {
                throw ApiException.of(ErrorCode.NOT_FOUND);
            }
        }
        PersonNote note = save(person, aboutProjectId, kind, request.body().strip(), userId);
        audit.event(ProjectEventType.PERSON_NOTE_ADDED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("noteId", note.getId().toString())
                .record();
        return toDto(note, usersOf(List.of(note)), true);
    }

    @Transactional
    public PersonNoteResponse revise(UUID userId, UUID workspaceId, UUID personId, UUID noteId,
                                     WritePersonNoteRequest request, HttpServletRequest httpRequest) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        PersonNoteKind kind = kindOf(request);
        PersonNote note = requireEditable(userId, workspaceId, personId, noteId);
        note.revise(kind, request.body().strip(), userId);
        activity.record(person, note.getProjectId(), userId, PersonActivityKind.NOTE_EDITED,
                PersonActivityDetails.of("noteId", note.getId()).and("kind", note.getKind()));
        audit.event(ProjectEventType.PERSON_NOTE_EDITED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("noteId", noteId.toString())
                .record();
        return toDto(note, usersOf(List.of(note)), true);
    }

    @Transactional
    public void remove(UUID userId, UUID workspaceId, UUID personId, UUID noteId, HttpServletRequest httpRequest) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        PersonNote note = requireEditable(userId, workspaceId, personId, noteId);
        activity.record(person, note.getProjectId(), userId, PersonActivityKind.NOTE_REMOVED,
                PersonActivityDetails.of("noteId", note.getId()).and("kind", note.getKind()));
        notes.delete(note);
        audit.event(ProjectEventType.PERSON_NOTE_REMOVED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("noteId", noteId.toString())
                .record();
    }

    /** Pinning orders the list; it is nobody's edit, so it leaves no timeline line, only an audit event. */
    @Transactional
    public PersonNoteResponse pin(UUID userId, UUID workspaceId, UUID personId, UUID noteId, boolean pinned,
                                  HttpServletRequest httpRequest) {
        people.requireInWorkspace(personId, workspaceId);
        PersonNote note = notes.requireOnPerson(noteId, workspaceId, personId);
        note.pin(pinned);
        audit.event(ProjectEventType.PERSON_NOTE_PINNED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("noteId", noteId.toString())
                .detail("pinned", Boolean.toString(pinned))
                .record();
        return toDto(note, usersOf(List.of(note)), mayEdit(userId, workspaceId, note));
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
        save(person, candidate.getProjectId(), PersonNoteKind.GENERAL, text, userId);
    }

    private PersonNote save(Person person, UUID projectId, PersonNoteKind kind, String body, UUID author) {
        String title = projectId == null ? null
                : projects.findById(projectId).map(Project::getPositionTitle).orElse(null);
        PersonNote note = notes.save(PersonNote.written(person, projectId, title, kind, body, author));
        activity.record(person, projectId, author, PersonActivityKind.NOTE_ADDED,
                PersonActivityDetails.of("noteId", note.getId()).and("kind", kind));
        return note;
    }

    private PersonNote requireEditable(UUID userId, UUID workspaceId, UUID personId, UUID noteId) {
        PersonNote note = notes.requireOnPerson(noteId, workspaceId, personId);
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

    private static PersonNoteKind kindOf(WritePersonNoteRequest request) {
        return ApiValueEnum.require(PersonNoteKind.class, request.kind(), "note kind");
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
        return new PersonNoteResponse(note.getId(), note.getKind().value(), note.getBody(), note.isPinned(),
                note.getProjectId(), note.getProjectTitle(), note.getAuthorUserId(),
                author == null ? null : author.getFullName(), author == null ? null : author.getAvatarUrl(),
                note.getCreatedAt(), note.getEditedAt(), editor == null ? null : editor.getFullName(), editable);
    }
}
