package app.lightmove.api.dataexport.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads a mandate's Companies grid. WORK_VIEW — the same reading a client does on screen — but,
 * as bulk egress, audited, which is why {@code HttpServletRequest} is threaded through.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/export")
@RequiredArgsConstructor
public class ProjectExportController {

    private final ProjectExportService exports;

    /**
     * {@code text/csv}, not octet-stream: the content is generated here, not echoed caller bytes. The SPA
     * names the download; the file name here is a fallback.
     */
    @GetMapping("/companies")
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
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
