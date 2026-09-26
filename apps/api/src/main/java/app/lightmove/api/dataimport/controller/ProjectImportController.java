package app.lightmove.api.dataimport.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.dataimport.dto.CommitImportRequest;
import app.lightmove.api.dataimport.dto.ImportPreviewResponse;
import app.lightmove.api.dataimport.dto.ImportSummaryResponse;
import app.lightmove.api.dataimport.service.ImportTemplateWriter;
import app.lightmove.api.dataimport.service.ProjectImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Spreadsheet import: preview then commit, the same file posted twice with nothing held between.
 * WORK_EXECUTE, since an import writes — a client seat must not start one.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/import")
@RequiredArgsConstructor
public class ProjectImportController {

    private final ProjectImportService imports;

    /**
     * A file built from this needs no model call. {@code text/csv} is safe here, unlike the position
     * document's echoed bytes: this content is generated.
     */
    @GetMapping("/template")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId) {
        byte[] csv = imports.template(principal.requireWorkspaceId(), projectId)
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(ImportTemplateWriter.FILE_NAME)
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(csv);
    }

    /** Writes nothing. The model budget is spent inside the mapping, since most previews never reach Vertex. */
    @PostMapping("/preview")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ImportPreviewResponse preview(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID projectId,
                                         @RequestParam("file") MultipartFile file) {
        return imports.preview(principal.userId(), principal.requireWorkspaceId(),
                projectId, file);
    }

    /** {@code @RequestPart}, unlike other uploads: the JSON mapping must be bound and validated as one. */
    @PostMapping("/commit")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ImportSummaryResponse commit(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID projectId,
                                        @RequestPart("file") MultipartFile file,
                                        @Valid @RequestPart("mapping") CommitImportRequest mapping,
                                        HttpServletRequest httpRequest) {
        return imports.commit(principal.userId(), principal.requireWorkspaceId(),
                projectId, file, mapping, httpRequest);
    }
}
