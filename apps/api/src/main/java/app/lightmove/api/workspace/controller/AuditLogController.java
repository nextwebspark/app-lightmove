package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.workspace.dto.AuditLogPageResponse;
import app.lightmove.api.workspace.dto.AuditLogPersonResponse;
import app.lightmove.api.workspace.model.AuditLogFilters;
import app.lightmove.api.workspace.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Security → Audit log. An admin's alone: a member is refused (403) and a pure client is not told it
 * exists (404), which is why {@code staffOnly} is asked before {@code can}.
 */
@RestController
@RequestMapping("/api/v1/workspace/audit-events")
@RequiredArgsConstructor
public class AuditLogController {

    private static final String ADMIN_ONLY = "@workspaceAuthorizer.staffOnly(principal)"
            + " and @workspaceAuthorizer.can(principal, 'WORKSPACE_MANAGE')";

    private final AuditLogService auditLog;

    @GetMapping
    @PreAuthorize(ADMIN_ONLY)
    public AuditLogPageResponse page(@AuthenticationPrincipal AuthPrincipal principal,
                                     @RequestParam(required = false) UUID actor,
                                     @RequestParam(required = false) String group,
                                     @RequestParam(required = false) String outcome,
                                     @RequestParam(required = false) Instant from,
                                     @RequestParam(required = false) Instant to,
                                     @RequestParam(required = false) Long before,
                                     @RequestParam(required = false) Integer limit) {
        return auditLog.page(principal.requireWorkspaceId(), AuditLogFilters.read(actor, group, outcome, from, to),
                before, limit);
    }

    @GetMapping("/people")
    @PreAuthorize(ADMIN_ONLY)
    public List<AuditLogPersonResponse> people(@AuthenticationPrincipal AuthPrincipal principal) {
        return auditLog.people(principal.requireWorkspaceId());
    }

    @GetMapping("/export")
    @PreAuthorize(ADMIN_ONLY)
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam(required = false) UUID actor,
                                         @RequestParam(required = false) String group,
                                         @RequestParam(required = false) String outcome,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to,
                                         HttpServletRequest request) {
        String csv = auditLog.export(principal.userId(), principal.requireWorkspaceId(),
                AuditLogFilters.read(actor, group, outcome, from, to), request);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("uncava-audit-log.csv").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
