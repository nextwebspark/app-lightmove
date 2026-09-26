package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The client registry, gated on {@code CLIENT_RECORD_MANAGE} (ADMIN and MEMBER). The workspace comes
 * from the principal, never the path.
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientsController {

    private final ClientService clients;
    private final ClientRepresentativeService representatives;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.CLIENT_RECORD_MANAGE)
    public List<ClientListResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return clients.list(principal.requireWorkspaceId());
    }

    @PostMapping
    @RequireWorkspacePermission(WorkspaceAction.CLIENT_RECORD_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public ClientListResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody CreateClientRequest request,
                                     HttpServletRequest httpRequest) {
        ClientListResponse created = clients.create(
                principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
        return created;
    }

    @GetMapping("/{clientId}")
    @RequireWorkspacePermission(WorkspaceAction.CLIENT_RECORD_MANAGE)
    public ClientDetailResponse get(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable UUID clientId) {
        return clients.get(principal.requireWorkspaceId(), clientId);
    }

    @PatchMapping("/{clientId}")
    @RequireWorkspacePermission(WorkspaceAction.CLIENT_RECORD_MANAGE)
    public ClientDetailResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID clientId,
                                       @Valid @RequestBody UpdateClientRequest request,
                                       HttpServletRequest httpRequest) {
        return clients.update(
                principal.userId(), principal.requireWorkspaceId(), clientId, request, httpRequest);
    }

    @PostMapping("/{clientId}/representatives")
    @RequireWorkspacePermission(WorkspaceAction.CLIENT_RECORD_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public RepresentativeResponse invite(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID clientId,
                                         @Valid @RequestBody InviteRepresentativeRequest request,
                                         HttpServletRequest httpRequest) {
        RepresentativeResponse invited = representatives.invite(
                principal.userId(), principal.requireWorkspaceId(), clientId,
                request.fullName(), request.position(), request.email(), httpRequest);
        return invited;
    }
}
