package app.lightmove.api.dataexport.controller;

import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.dataexport.service.ConsentRegisterExportService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads the consent register (#1267): the workspace's, from Settings, or a position's shares, from the position.
 * The position's is {@code WORK_EXECUTE}, never {@code WORK_VIEW}, which a client seat holds.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class ConsentRegisterExportController {

    private final ConsentRegisterExportService exports;

    @GetMapping("/api/v1/candidates/export/consent-register")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public ResponseEntity<byte[]> workspace(@AuthenticationPrincipal AuthPrincipal principal,
                                            @RequestParam(required = false) UUID position,
                                            HttpServletRequest httpRequest) {
        return csvOf(exports.register(principal.userId(), principal.requireWorkspaceId(), position, httpRequest));
    }

    @GetMapping("/api/v1/projects/{projectId}/export/consent-register")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<byte[]> position(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId, HttpServletRequest httpRequest) {
        return csvOf(exports.register(principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }

    private static ResponseEntity<byte[]> csvOf(String file) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("uncava-consent-register.csv")
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.getBytes(StandardCharsets.UTF_8));
    }
}
