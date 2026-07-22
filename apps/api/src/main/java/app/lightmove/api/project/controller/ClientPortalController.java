package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.project.dto.ClientDtos.PortalClientResponse;
import app.lightmove.api.project.service.ClientPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The client portal — a representative's read-only window onto their own client and its mandates.
 *
 * <p>Gated on {@code CLIENT_PORTAL_READ}, the one action the workspace CLIENT role holds and no staff
 * role does; every staff endpoint gates on actions CLIENT lacks, so this is the only door open to a
 * representative. The client is resolved from the caller's own representative row, never a path
 * variable — see {@link ClientPortalService}.
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class ClientPortalController {

    private final ClientPortalService portal;

    @GetMapping("/me")
    @PreAuthorize("@workspaceAuth.can(principal, 'CLIENT_PORTAL_READ')")
    public ResponseEntity<PortalClientResponse> myClient() {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(portal.myClient(principal.userId(), principal.requireWorkspaceId()));
    }
}
