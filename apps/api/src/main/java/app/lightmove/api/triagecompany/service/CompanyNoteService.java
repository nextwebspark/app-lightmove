package app.lightmove.api.triagecompany.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.common.constant.NoteFormat;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.triagecompany.constant.CompanyNoteKind;
import app.lightmove.api.triagecompany.dto.CompanyNoteResponse;
import app.lightmove.api.triagecompany.dto.WriteCompanyNoteRequest;
import app.lightmove.api.triagecompany.model.CompanyNote;
import app.lightmove.api.triagecompany.model.TriageCompany;
import app.lightmove.api.triagecompany.repository.CompanyNoteCount;
import app.lightmove.api.triagecompany.repository.CompanyNoteRepository;
import app.lightmove.api.triagecompany.repository.DeletedCompanyNoteRow;
import app.lightmove.api.triagecompany.repository.TriageCompanyRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Collection;
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
 * A mandate's notes on one of its companies, staff-only like a person's (D1). Anyone working the position may write
 * or pin one; only its author, or a holder of {@code WORKSPACE_MANAGE}, may change, remove or restore it. A removed
 * note is kept out of every read until it is restored or {@link CompanyNotePurge} deletes it.
 */
@Service
@RequiredArgsConstructor
public class CompanyNoteService {

    private static final String COMPANY_TARGET = "triageCompanyId";

    private final CompanyNoteRepository notes;
    private final TriageCompanyRepository companies;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final WorkspaceAccess workspaceAccess;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CompanyNoteResponse> list(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId) {
        requireCompany(workspaceId, projectId, triageCompanyId);
        List<CompanyNote> found = notes.findByProjectIdAndTriageCompanyIdOrderByPinnedDescCreatedAtDesc(projectId,
                triageCompanyId);
        boolean mayEditAny = mayEditAny(userId, workspaceId);
        Map<UUID, User> people = usersOf(found);
        return found.stream().map(note -> toDto(note, people, mayEditAny || note.isWrittenBy(userId))).toList();
    }

    @Transactional
    public CompanyNoteResponse write(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                     WriteCompanyNoteRequest request, HttpServletRequest httpRequest) {
        TriageCompany company = requireCompany(workspaceId, projectId, triageCompanyId);
        CompanyNote note = notes.save(CompanyNote.written(workspaceId, company, kindOf(request),
                request.body().strip(), formatOf(request), userId));
        record(ProjectEventType.COMPANY_NOTE_ADDED, userId, workspaceId, note, httpRequest);
        return toDto(note, usersOf(List.of(note)), true);
    }

    @Transactional
    public CompanyNoteResponse revise(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                      UUID noteId, WriteCompanyNoteRequest request, HttpServletRequest httpRequest) {
        requireCompany(workspaceId, projectId, triageCompanyId);
        CompanyNoteKind kind = kindOf(request);
        CompanyNote note = requireEditable(userId, workspaceId, projectId, triageCompanyId, noteId);
        note.revise(kind, request.body().strip(), formatOf(request), userId);
        record(ProjectEventType.COMPANY_NOTE_EDITED, userId, workspaceId, note, httpRequest);
        return toDto(note, usersOf(List.of(note)), true);
    }

    @Transactional
    public void remove(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId, UUID noteId,
                       HttpServletRequest httpRequest) {
        requireCompany(workspaceId, projectId, triageCompanyId);
        CompanyNote note = requireEditable(userId, workspaceId, projectId, triageCompanyId, noteId);
        note.discard(userId, clock.instant());
        record(ProjectEventType.COMPANY_NOTE_REMOVED, userId, workspaceId, note, httpRequest);
    }

    /**
     * Takes a delete back while the note is held. A note already live answers as it is, so two Undos record one
     * restore; where a door has filed the same words again since, that copy answers and the deleted one is left to
     * the purge.
     */
    @Transactional
    public CompanyNoteResponse restore(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                       UUID noteId, HttpServletRequest httpRequest) {
        requireCompany(workspaceId, projectId, triageCompanyId);
        DeletedCompanyNoteRow deleted = notes.findDeleted(noteId, projectId, triageCompanyId).orElse(null);
        if (deleted == null) {
            return asItStands(userId, workspaceId, notes.requireOn(noteId, projectId, triageCompanyId));
        }
        if (!deleted.getAuthorUserId().equals(userId) && !mayEditAny(userId, workspaceId)) {
            throw ApiException.of(ErrorCode.COMPANY_NOTE_NOT_YOURS);
        }
        CompanyNote twin = notes.findFirstByProjectIdAndTriageCompanyIdAndBody(projectId, triageCompanyId,
                deleted.getBody()).orElse(null);
        if (twin != null) {
            return asItStands(userId, workspaceId, twin);
        }
        if (notes.restore(noteId, projectId, triageCompanyId) == 0) {
            return asItStands(userId, workspaceId, notes.requireOn(noteId, projectId, triageCompanyId));
        }
        CompanyNote note = notes.requireOn(noteId, projectId, triageCompanyId);
        record(ProjectEventType.COMPANY_NOTE_RESTORED, userId, workspaceId, note, httpRequest);
        return toDto(note, usersOf(List.of(note)), true);
    }

    private CompanyNoteResponse asItStands(UUID userId, UUID workspaceId, CompanyNote note) {
        return toDto(note, usersOf(List.of(note)), mayEdit(userId, workspaceId, note));
    }

    @Transactional
    public CompanyNoteResponse pin(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                   UUID noteId, boolean pinned, HttpServletRequest httpRequest) {
        requireCompany(workspaceId, projectId, triageCompanyId);
        CompanyNote note = notes.requireOn(noteId, projectId, triageCompanyId);
        note.pin(pinned);
        audit.projectEvent(ProjectEventType.COMPANY_NOTE_PINNED, userId, workspaceId, projectId, httpRequest)
                .detail(COMPANY_TARGET, triageCompanyId.toString())
                .detail("noteId", noteId.toString())
                .detail("pinned", Boolean.toString(pinned))
                .record();
        return toDto(note, usersOf(List.of(note)), mayEdit(userId, workspaceId, note));
    }

    /**
     * A note a door carried in with the company — the add form, the plugin, a spreadsheet's Note column — filed as
     * typed. The same words already there are not filed twice, which keeps a re-imported sheet from stacking copies.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fileFromDoor(UUID workspaceId, TriageCompany company, UUID userId, String body) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || notes.existsByProjectIdAndTriageCompanyIdAndBody(company.getProjectId(),
                company.getId(), text)) {
            return;
        }
        notes.save(CompanyNote.written(workspaceId, company, CompanyNoteKind.GENERAL, text, NoteFormat.PLAIN,
                userId));
    }

    /** How many live notes each of these companies carries; a company with none is absent. */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> countsOf(UUID projectId, Collection<UUID> triageCompanyIds) {
        if (triageCompanyIds.isEmpty()) {
            return Map.of();
        }
        return notes.countByCompany(projectId, triageCompanyIds).stream()
                .collect(Collectors.toMap(CompanyNoteCount::getTriageCompanyId, row -> (int) row.getNotes()));
    }

    private TriageCompany requireCompany(UUID workspaceId, UUID projectId, UUID triageCompanyId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return companies.requireInProject(triageCompanyId, projectId);
    }

    private CompanyNote requireEditable(UUID userId, UUID workspaceId, UUID projectId, UUID triageCompanyId,
                                        UUID noteId) {
        CompanyNote note = notes.requireOn(noteId, projectId, triageCompanyId);
        if (!mayEdit(userId, workspaceId, note)) {
            throw ApiException.of(ErrorCode.COMPANY_NOTE_NOT_YOURS);
        }
        return note;
    }

    private boolean mayEdit(UUID userId, UUID workspaceId, CompanyNote note) {
        return note.isWrittenBy(userId) || mayEditAny(userId, workspaceId);
    }

    private boolean mayEditAny(UUID userId, UUID workspaceId) {
        return workspaceAccess.holdsAction(userId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
    }

    private void record(ProjectEventType type, UUID userId, UUID workspaceId, CompanyNote note,
                        HttpServletRequest httpRequest) {
        audit.projectEvent(type, userId, workspaceId, note.getProjectId(), httpRequest)
                .detail(COMPANY_TARGET, note.getTriageCompanyId().toString())
                .detail("noteId", note.getId().toString())
                .record();
    }

    private static CompanyNoteKind kindOf(WriteCompanyNoteRequest request) {
        return ApiValueEnum.require(CompanyNoteKind.class, request.kind(), "note kind");
    }

    private static NoteFormat formatOf(WriteCompanyNoteRequest request) {
        return ApiValueEnum.parse(NoteFormat.class, request.format(), NoteFormat.PLAIN, "note format");
    }

    private Map<UUID, User> usersOf(List<CompanyNote> found) {
        List<UUID> ids = found.stream()
                .flatMap(note -> Stream.of(note.getAuthorUserId(), note.getEditedBy()))
                .filter(Objects::nonNull).distinct().toList();
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static CompanyNoteResponse toDto(CompanyNote note, Map<UUID, User> people, boolean editable) {
        User author = people.get(note.getAuthorUserId());
        User editor = note.getEditedBy() == null ? null : people.get(note.getEditedBy());
        return new CompanyNoteResponse(note.getId(), note.getKind().value(), note.getBody(),
                note.getBodyFormat().value(), note.isPinned(), note.getAuthorUserId(),
                author == null ? null : author.getFullName(), author == null ? null : author.getAvatarUrl(),
                note.getCreatedAt(), note.getEditedAt(), editor == null ? null : editor.getFullName(), editable);
    }
}
