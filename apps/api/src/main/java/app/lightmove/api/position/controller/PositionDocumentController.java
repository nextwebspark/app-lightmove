package app.lightmove.api.position.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.model.StoredDocument;
import app.lightmove.api.position.service.PositionDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The position description attached to a brief: download is WORK_VIEW, attach and remove PROJECT_EDIT.
 * These move bytes only; reading the content is {@link PositionExtractionController}'s.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/position/document")
@RequiredArgsConstructor
public class PositionDocumentController {

    private final PositionDocumentService documents;

    @PostMapping
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse attach(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable UUID projectId,
                                   @RequestParam("file") MultipartFile file,
                                   HttpServletRequest httpRequest) {
        return documents.attach(
                principal.userId(), principal.requireWorkspaceId(), projectId, file, httpRequest);
    }

    @DeleteMapping
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse remove(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable UUID projectId,
                                   HttpServletRequest httpRequest) {
        return documents.remove(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest);
    }

    /**
     * Always {@code application/octet-stream} with {@code attachment} and {@code nosniff}, never the
     * uploaded type: caller-supplied bytes the browser renders would host content on our origin.
     */
    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID projectId) {
        StoredDocument document = documents.download(principal.requireWorkspaceId(), projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.fileName())
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(document.content()));
    }
}
