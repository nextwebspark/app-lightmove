package app.lightmove.api.dataimport.controller;

import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.dataimport.dto.CommitImportRequest;
import app.lightmove.api.dataimport.dto.ImportPreviewResponse;
import app.lightmove.api.dataimport.dto.ImportSummaryResponse;
import app.lightmove.api.dataimport.service.ProjectImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final LlmBudgetGuard llmBudget;

    /**
     * Reads the file and answers with a mapping to confirm. Writes nothing.
     *
     * <p>Budgeted before the work starts, because this is the call that reaches Vertex: without a cap
     * an authenticated caller could loop uploads and run up the project's GCP bill. Commit is not
     * budgeted here — it calls no model, and the writes it performs are already gated by the seat.
     */
    @PostMapping("/preview")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<ImportPreviewResponse> preview(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId,
                                                         @RequestParam("file") MultipartFile file) {
        llmBudget.checkColumnMapping(principal.userId());
        return ResponseEntity.ok(imports.preview(principal.requireWorkspaceId(), projectId, file));
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
