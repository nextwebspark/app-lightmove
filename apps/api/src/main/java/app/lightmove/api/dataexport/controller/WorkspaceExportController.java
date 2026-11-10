package app.lightmove.api.dataexport.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.dataexport.dto.WorkspaceExportResponse;
import app.lightmove.api.dataexport.model.WorkspaceExportDownload;
import app.lightmove.api.dataexport.service.WorkspaceExportService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Settings → Data & retention's export of the whole workspace, an admin's: start one, read where it stands, take it. */
@RestController
@RequestMapping("/api/v1/workspace/exports")
@RequiredArgsConstructor
public class WorkspaceExportController {

    private final WorkspaceExportService exports;

    @PostMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public ResponseEntity<WorkspaceExportResponse> start(@AuthenticationPrincipal AuthPrincipal principal,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(exports.start(principal.userId(), principal.requireWorkspaceId(), httpRequest));
    }

    /** No content until the workspace's first export. */
    @GetMapping("/latest")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public ResponseEntity<WorkspaceExportResponse> latest(@AuthenticationPrincipal AuthPrincipal principal) {
        return exports.latest(principal.requireWorkspaceId()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Streamed by the server from the bucket, never a link to it. */
    @GetMapping("/{exportId}/download")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public ResponseEntity<StreamingResponseBody> download(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @PathVariable UUID exportId,
                                                          HttpServletRequest httpRequest) {
        WorkspaceExportDownload download = exports.openDownload(principal.userId(), principal.requireWorkspaceId(),
                exportId, httpRequest);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(out -> exports.copy(download, out));
    }
}
