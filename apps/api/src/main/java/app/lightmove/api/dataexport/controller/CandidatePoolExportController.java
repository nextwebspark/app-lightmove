package app.lightmove.api.dataexport.controller;

import app.lightmove.api.candidate.model.PoolCriteria;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.dataexport.service.CandidatePoolExportService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads the workspace's people. The Candidates page's own gate, and the same filters its list
 * reads; {@code person} repeats to export exactly the people ticked instead.
 */
@RestController
@RequestMapping("/api/v1/candidates/export")
@RequiredArgsConstructor
public class CandidatePoolExportController {

    private final CandidatePoolExportService exports;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public ResponseEntity<byte[]> people(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String view,
                                         @RequestParam(required = false) List<UUID> tag,
                                         @RequestParam(required = false) String tagMatch,
                                         @RequestParam(required = false) UUID position,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String owner,
                                         @RequestParam(required = false) String country,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(required = false) String direction,
                                         @RequestParam(required = false) List<UUID> person,
                                         HttpServletRequest httpRequest) {
        PoolCriteria criteria = PoolCriteria.read(q, view, tag, tagMatch, position, status, owner, country, sort,
                direction);
        byte[] csv = exports.people(principal.userId(), principal.requireWorkspaceId(), criteria, person, httpRequest)
                .getBytes(StandardCharsets.UTF_8);
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
