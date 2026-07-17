package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.project.dto.ProjectDtos.ClientResponse;
import app.lightmove.api.project.dto.ProjectDtos.CreateClientRequest;
import app.lightmove.api.project.service.ClientService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Hiring entities of the caller's workspace — the New-project modal's client list. */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientsController {

    private final ClientService clients;

    @GetMapping
    @PreAuthorize("@workspaceAuth.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<List<ClientResponse>> list() {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(clients.list(principal.requireWorkspaceId()));
    }

    @PostMapping
    @PreAuthorize("@workspaceAuth.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request) {
        AuthPrincipal principal = CurrentUser.require();
        ClientResponse created = clients.create(
                principal.userId(), principal.requireWorkspaceId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
