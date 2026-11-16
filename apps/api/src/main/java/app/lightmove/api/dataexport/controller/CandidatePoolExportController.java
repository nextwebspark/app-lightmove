package app.lightmove.api.dataexport.controller;

import app.lightmove.api.candidate.dto.CandidatePoolQueryRequest;
import app.lightmove.api.candidate.dto.ExportPeopleRequest;
import app.lightmove.api.candidate.model.PoolCriteria;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.dataexport.service.CandidatePoolExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads the workspace's people. The Candidates page's own gate: a GET with the same filters its
 * list reads, or a POST naming exactly the people ticked.
 */
@RestController
@RequestMapping("/api/v1/candidates/export")
@RequiredArgsConstructor
public class CandidatePoolExportController {

    private final CandidatePoolExportService exports;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public ResponseEntity<byte[]> people(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam MultiValueMap<String, String> params,
                                         HttpServletRequest httpRequest) {
        return csvOf(exports.people(principal.userId(), principal.requireWorkspaceId(),
                CandidatePoolQueryRequest.from(params).toCriteria(), List.of(), httpRequest));
    }

    /** Exactly the people ticked. */
    @PostMapping
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public ResponseEntity<byte[]> selected(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Valid @RequestBody ExportPeopleRequest request,
                                           HttpServletRequest httpRequest) {
        return csvOf(exports.people(principal.userId(), principal.requireWorkspaceId(), PoolCriteria.everyone(),
                request.personIds(), httpRequest));
    }

    private static ResponseEntity<byte[]> csvOf(String file) {
        byte[] csv = file.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("uncava-candidates.csv")
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(csv);
    }
}
