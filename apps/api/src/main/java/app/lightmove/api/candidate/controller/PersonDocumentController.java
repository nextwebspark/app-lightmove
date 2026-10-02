package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.PersonDocumentResponse;
import app.lightmove.api.candidate.dto.PersonDocumentUploadResponse;
import app.lightmove.api.candidate.dto.UpdatePersonDocumentRequest;
import app.lightmove.api.candidate.model.StoredDocumentFile;
import app.lightmove.api.candidate.service.PersonDocumentService;
import app.lightmove.api.candidate.service.PersonRecordService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A person's documents, by both doors the drawer has: a position's (WORK_EXECUTE, so a researcher seated
 * on the mandate needs no workspace action) and the workspace's Candidates page
 * ({@code CANDIDATE_POOL_MANAGE}). A client seat reaches neither (decision D1).
 */
@RestController
@RequiredArgsConstructor
public class PersonDocumentController {

    private static final String ON_POSITION = "/api/v1/projects/{projectId}/candidates/{candidateId}/documents";
    private static final String ON_PERSON = "/api/v1/candidates/{personId}/documents";

    private final PersonRecordService records;
    private final PersonDocumentService documents;

    // ── through a position ───────────────────────────────────────────────────

    @GetMapping(ON_POSITION)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public List<PersonDocumentResponse> listOnPosition(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable UUID projectId, @PathVariable UUID candidateId) {
        UUID workspaceId = principal.requireWorkspaceId();
        return documents.list(principal.userId(), workspaceId, records.personOf(workspaceId, projectId, candidateId));
    }

    @PostMapping(ON_POSITION)
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PersonDocumentUploadResponse uploadOnPosition(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(required = false) String category,
                                                         @RequestParam(defaultValue = "false") boolean asNewDocument,
                                                         HttpServletRequest httpRequest) {
        UUID workspaceId = principal.requireWorkspaceId();
        return documents.upload(principal.userId(), workspaceId, records.personOf(workspaceId, projectId, candidateId),
                projectId, file, category, asNewDocument, httpRequest);
    }

    @PostMapping(ON_POSITION + "/{documentId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PersonDocumentUploadResponse uploadVersionOnPosition(@AuthenticationPrincipal AuthPrincipal principal,
                                                                @PathVariable UUID projectId,
                                                                @PathVariable UUID candidateId,
                                                                @PathVariable UUID documentId,
                                                                @RequestParam("file") MultipartFile file,
                                                                HttpServletRequest httpRequest) {
        UUID workspaceId = principal.requireWorkspaceId();
        return documents.uploadVersion(principal.userId(), workspaceId,
                records.personOf(workspaceId, projectId, candidateId), documentId, projectId, file, httpRequest);
    }

    @PatchMapping(ON_POSITION + "/{documentId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PersonDocumentResponse updateOnPosition(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                                   @PathVariable UUID documentId,
                                                   @Valid @RequestBody UpdatePersonDocumentRequest request,
                                                   HttpServletRequest httpRequest) {
        UUID workspaceId = principal.requireWorkspaceId();
        return documents.update(principal.userId(), workspaceId, records.personOf(workspaceId, projectId, candidateId),
                documentId, request, httpRequest);
    }

    @DeleteMapping(ON_POSITION + "/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public void removeOnPosition(@AuthenticationPrincipal AuthPrincipal principal,
                                 @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                 @PathVariable UUID documentId, HttpServletRequest httpRequest) {
        UUID workspaceId = principal.requireWorkspaceId();
        documents.remove(principal.userId(), workspaceId, records.personOf(workspaceId, projectId, candidateId),
                documentId, projectId, httpRequest);
    }

    @DeleteMapping(ON_POSITION + "/{documentId}/versions/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public void removeVersionOnPosition(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                        @PathVariable UUID documentId, @PathVariable UUID versionId,
                                        HttpServletRequest httpRequest) {
        UUID workspaceId = principal.requireWorkspaceId();
        documents.removeVersion(principal.userId(), workspaceId, records.personOf(workspaceId, projectId, candidateId),
                documentId, versionId, projectId, httpRequest);
    }

    @GetMapping(ON_POSITION + "/{documentId}/versions/{versionId}/content")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<Resource> contentOnPosition(@AuthenticationPrincipal AuthPrincipal principal,
                                                                   @PathVariable UUID projectId,
                                                                   @PathVariable UUID candidateId,
                                                                   @PathVariable UUID documentId,
                                                                   @PathVariable UUID versionId,
                                                                   @RequestParam(defaultValue = "false") boolean preview,
                                                                   HttpServletRequest httpRequest) {
        UUID workspaceId = principal.requireWorkspaceId();
        return send(documents.fileOf(principal.userId(), workspaceId,
                records.personOf(workspaceId, projectId, candidateId), documentId, versionId, preview, httpRequest),
                preview);
    }

    // ── through the workspace's Candidates page ──────────────────────────────

    @GetMapping(ON_PERSON)
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public List<PersonDocumentResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID personId) {
        return documents.list(principal.userId(), principal.requireWorkspaceId(), personId);
    }

    @PostMapping(ON_PERSON)
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonDocumentUploadResponse upload(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID personId,
                                               @RequestParam("file") MultipartFile file,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(defaultValue = "false") boolean asNewDocument,
                                               HttpServletRequest httpRequest) {
        return documents.upload(principal.userId(), principal.requireWorkspaceId(), personId, null, file, category,
                asNewDocument, httpRequest);
    }

    @PostMapping(ON_PERSON + "/{documentId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonDocumentUploadResponse uploadVersion(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @PathVariable UUID personId, @PathVariable UUID documentId,
                                                      @RequestParam("file") MultipartFile file,
                                                      HttpServletRequest httpRequest) {
        return documents.uploadVersion(principal.userId(), principal.requireWorkspaceId(), personId, documentId, null,
                file, httpRequest);
    }

    @PatchMapping(ON_PERSON + "/{documentId}")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonDocumentResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID personId, @PathVariable UUID documentId,
                                         @Valid @RequestBody UpdatePersonDocumentRequest request,
                                         HttpServletRequest httpRequest) {
        return documents.update(principal.userId(), principal.requireWorkspaceId(), personId, documentId, request,
                httpRequest);
    }

    @DeleteMapping(ON_PERSON + "/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public void remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                       @PathVariable UUID documentId, HttpServletRequest httpRequest) {
        documents.remove(principal.userId(), principal.requireWorkspaceId(), personId, documentId, null, httpRequest);
    }

    @DeleteMapping(ON_PERSON + "/{documentId}/versions/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public void removeVersion(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                              @PathVariable UUID documentId, @PathVariable UUID versionId,
                              HttpServletRequest httpRequest) {
        documents.removeVersion(principal.userId(), principal.requireWorkspaceId(), personId, documentId, versionId,
                null, httpRequest);
    }

    @GetMapping(ON_PERSON + "/{documentId}/versions/{versionId}/content")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public ResponseEntity<Resource> content(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID personId, @PathVariable UUID documentId,
                                                         @PathVariable UUID versionId,
                                                         @RequestParam(defaultValue = "false") boolean preview,
                                                         HttpServletRequest httpRequest) {
        return send(documents.fileOf(principal.userId(), principal.requireWorkspaceId(), personId, documentId,
                versionId, preview, httpRequest), preview);
    }

    /**
     * A PDF or an image asked for as a preview goes out inline as what its bytes were read as; everything
     * else is an {@code octet-stream} attachment, for {@code PositionDocumentController}'s reason. Both
     * carry {@code nosniff}, stream from storage rather than being held whole, and are never cached: a CV
     * is not left behind in a shared browser.
     */
    private ResponseEntity<Resource> send(StoredDocumentFile file, boolean preview) {
        boolean inline = preview && file.previewable();
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(inline ? MediaType.parseMediaType(file.contentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.sizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(documents.open(file)));
    }
}
