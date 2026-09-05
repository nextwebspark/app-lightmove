package app.lightmove.api.dataimport.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.dataimport.dto.CommitImportRequest;
import app.lightmove.api.dataimport.dto.ImportPreviewResponse;
import app.lightmove.api.dataimport.dto.ImportSummaryResponse;
import app.lightmove.api.dataimport.service.ImportTemplateWriter;
import app.lightmove.api.dataimport.service.ProjectImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importing a spreadsheet into a mandate's Companies grid. Both calls are WORK_EXECUTE: an import
 * writes companies and people, so a client representative — who may read the mandate's content — must
 * not be able to start one.
 *
 * <p>Two calls carrying the same file. Preview reads it and proposes a mapping; commit takes it back
 * with the mapping a person confirmed. Nothing is held open server-side between them: the browser
 * still has the file, so re-posting it costs one parse and saves a staging table, an expiry policy and
 * a sweeper for the imports nobody came back to finish.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/import")
@RequiredArgsConstructor
public class ProjectImportController {

    private final ProjectImportService imports;

    /**
     * A blank CSV to fill in and upload back. Optional — the import maps whatever headers arrive — but
     * a file built from this needs no model call, because every header in it is one the matcher knows.
     *
     * <p>Served as {@code text/csv} rather than the {@code application/octet-stream} the position
     * document uses: that rule exists because it echoes caller-supplied bytes back, and this content is
     * generated here.
     */
    @GetMapping("/template")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
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

    /**
     * Reads the file and answers with a mapping to confirm. Writes nothing.
     *
     * <p>The model budget is spent inside the mapping, not here: most previews never reach Vertex,
     * and refusing one that would not have called it with "the model budget is exhausted" would be a
     * lie the caller cannot act on.
     */
    @PostMapping("/preview")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<ImportPreviewResponse> preview(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId,
                                                         @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(imports.preview(principal.userId(), principal.requireWorkspaceId(),
                projectId, file));
    }

    /**
     * Applies the confirmed mapping.
     *
     * <p>{@code @RequestPart} rather than the {@code @RequestParam} every other upload here uses, and
     * the only one in the codebase: this request carries a file <i>and</i> a JSON document that has to
     * be bound and validated as one, which a form field of JSON text could not be. The browser sends
     * the mapping as a blob typed {@code application/json} beside the file.
     */
    @PostMapping("/commit")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<ImportSummaryResponse> commit(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable UUID projectId,
                                                        @RequestPart("file") MultipartFile file,
                                                        @Valid @RequestPart("mapping") CommitImportRequest mapping,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(imports.commit(principal.userId(), principal.requireWorkspaceId(),
                projectId, file, mapping, httpRequest));
    }
}
