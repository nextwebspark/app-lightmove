package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.project.dto.ClientDetailResponse;
import app.lightmove.api.project.dto.ClientListResponse;
import app.lightmove.api.project.dto.CreateClientRequest;
import app.lightmove.api.project.dto.InviteRepresentativeRequest;
import app.lightmove.api.project.dto.RepresentativeResponse;
import app.lightmove.api.project.dto.UpdateClientRequest;
import app.lightmove.api.project.service.ClientRepresentativeService;
import app.lightmove.api.project.service.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The client registry of the caller's workspace — the Clients screen. Every endpoint gates on
 * {@code CLIENT_RECORD_MANAGE}, held by workspace ADMIN and MEMBER. The workspace comes from the
 * principal, never the path.
 *
 * <p>The New-client modal's company search reuses {@code GET /api/v1/companies/search} (gated
 * {@code PROJECT_BROWSE}); there is no company endpoint here.
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientsController {

    private final ClientService clients;
    private final ClientRepresentativeService representatives;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<List<ClientListResponse>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(clients.list(principal.requireWorkspaceId()));
    }

    @PostMapping
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<ClientListResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @Valid @RequestBody CreateClientRequest request,
                                                     HttpServletRequest httpRequest) {
        ClientListResponse created = clients.create(
                principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<ClientDetailResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable UUID clientId) {
        return ResponseEntity.ok(clients.get(principal.requireWorkspaceId(), clientId));
    }

    @PatchMapping("/{clientId}")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<ClientDetailResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable UUID clientId,
                                                       @Valid @RequestBody UpdateClientRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(clients.update(
                principal.userId(), principal.requireWorkspaceId(), clientId, request, httpRequest));
    }

    @PostMapping("/{clientId}/representatives")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<RepresentativeResponse> invite(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID clientId,
                                                         @Valid @RequestBody InviteRepresentativeRequest request,
                                                         HttpServletRequest httpRequest) {
        RepresentativeResponse invited = representatives.invite(
                principal.userId(), principal.requireWorkspaceId(), clientId,
                request.fullName(), request.position(), request.email(), httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(invited);
    }

    /**
     * Withdraws a representative's access — cancelling an outstanding invite and revoking a live one are
     * the same decision, so they are the same endpoint. Registry tier like the rest of this controller:
     * whoever may name a client's contacts may un-name them. Detaching one from a single mandate is the
     * lead's separate call on {@code ProjectsController}.
     */
    @DeleteMapping("/{clientId}/representatives/{representativeId}")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<Void> revokeRepresentative(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable UUID clientId,
                                                     @PathVariable UUID representativeId,
                                                     HttpServletRequest httpRequest) {
        representatives.revoke(principal.userId(), principal.requireWorkspaceId(), clientId,
                representativeId, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{clientId}/representatives/{representativeId}/resend")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<Void> resendRepresentativeInvite(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID clientId,
            @PathVariable UUID representativeId,
            HttpServletRequest httpRequest) {
        representatives.resendInvitation(principal.userId(), principal.requireWorkspaceId(), clientId,
                representativeId, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
