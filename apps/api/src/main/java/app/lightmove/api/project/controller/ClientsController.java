package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.project.dto.ClientDtos.ClientDetailResponse;
import app.lightmove.api.project.dto.ClientDtos.ClientListResponse;
import app.lightmove.api.project.dto.ClientDtos.CreateClientRequest;
import app.lightmove.api.project.dto.ClientDtos.InviteRepresentativeRequest;
import app.lightmove.api.project.dto.ClientDtos.RepresentativeResponse;
import app.lightmove.api.project.dto.ClientDtos.UpdateClientRequest;
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
    @PreAuthorize("@workspaceAuth.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<List<ClientListResponse>> list() {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(clients.list(principal.requireWorkspaceId()));
    }

    @PostMapping
    @PreAuthorize("@workspaceAuth.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<ClientListResponse> create(@Valid @RequestBody CreateClientRequest request,
                                                     HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        ClientListResponse created = clients.create(
                principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("@workspaceAuth.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<ClientDetailResponse> get(@PathVariable UUID clientId) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(clients.get(principal.requireWorkspaceId(), clientId));
    }

    @PatchMapping("/{clientId}")
    @PreAuthorize("@workspaceAuth.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<ClientDetailResponse> update(@PathVariable UUID clientId,
                                                       @Valid @RequestBody UpdateClientRequest request,
                                                       HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(clients.update(
                principal.userId(), principal.requireWorkspaceId(), clientId, request, httpRequest));
    }

    @PostMapping("/{clientId}/representatives")
    @PreAuthorize("@workspaceAuth.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<RepresentativeResponse> invite(@PathVariable UUID clientId,
                                                         @Valid @RequestBody InviteRepresentativeRequest request,
                                                         HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        RepresentativeResponse invited = representatives.invite(
                principal.userId(), principal.requireWorkspaceId(), clientId,
                request.fullName(), request.position(), request.email(), httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(invited);
    }
}
