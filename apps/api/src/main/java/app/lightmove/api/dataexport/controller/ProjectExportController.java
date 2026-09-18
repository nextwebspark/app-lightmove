package app.lightmove.api.dataexport.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.dataexport.service.ProjectExportService;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloading a mandate's Companies grid as a file.
 *
 * <p>WORK_VIEW, not the WORK_EXECUTE the import template carries: that gate exists because an import
 * <i>writes</i>, and this is the same reading a client representative already does on the screen. It
 * is bulk egress all the same, so unlike every other read in the codebase it records an audit event —
 * which is the only reason {@code HttpServletRequest} is threaded through.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/export")
@RequiredArgsConstructor
public class ProjectExportController {

    private final ProjectExportService exports;

    /**
     * One stage of the grid, whole, narrowed by the screen's search box when one is in force.
     *
     * <p>Served as {@code text/csv} rather than the {@code application/octet-stream} the position
     * document uses: that rule exists because the document echoes caller-supplied bytes back, and this
     * content is generated here. The file name is a fallback — the SPA names the download itself,
     * where the mandate's own name is in hand.
     */
    @GetMapping("/companies")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<byte[]> companies(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID projectId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String executiveQuery,
                                            @RequestParam(required = false) List<String> executiveStatuses,
                                            HttpServletRequest httpRequest) {
        TriageCompanyFilters filters = new TriageCompanyFilters(q, executiveQuery, executiveStatuses);
        byte[] csv = exports
                .companies(principal.userId(), principal.requireWorkspaceId(), projectId, status,
                        filters, httpRequest)
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("uncava-companies.csv")
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(csv);
    }
}
