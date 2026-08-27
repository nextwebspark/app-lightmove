package app.lightmove.api.position.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
 * The position description attached to a brief. Gated exactly as the brief's own fields are — reading
 * is WORK_VIEW, so a client representative may see and open the document their mandate was briefed
 * from, while attaching or removing one is PROJECT_EDIT.
 *
 * <p>Uploading stores the file and nothing more: no field on the Position screen is filled in from it.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/position/document")
@RequiredArgsConstructor
public class PositionDocumentController {

    private final PositionDocumentService documents;

    @PostMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> attach(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID projectId,
                                                   @RequestParam("file") MultipartFile file,
                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(documents.attach(
                principal.userId(), principal.requireWorkspaceId(), projectId, file, httpRequest));
    }

    @DeleteMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> remove(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID projectId,
                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(documents.remove(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }

    /**
     * Hands the stored bytes back as a download.
     *
     * <p>Always {@code application/octet-stream} with {@code attachment}, never the type the file was
     * uploaded as: serving caller-supplied bytes under a type the browser will render turns an upload
     * field into a way to host content on our origin. {@code nosniff} stops the browser guessing its
     * way back to the same place.
     */
    @GetMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
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
