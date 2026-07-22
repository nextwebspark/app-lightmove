package app.lightmove.api.project.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.dto.ClientDtos.PortalClientResponse;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The client portal's one read. A representative sees exactly their own client and its mandates — never
 * a roster, another client, or any mandate internal (strategy, brief, pipeline).
 *
 * <p>The scoping is paranoid by construction: the client id is <b>derived</b> from the caller's own
 * ACTIVE representative row keyed by {@code (workspaceId, userId)} — never read from the request — and
 * the {@code workspaceId} is the signed principal claim. The response is the strict
 * {@link PortalClientResponse} subset; nothing wider is ever assembled. A caller with no such row gets a
 * 404, so the endpoint is no id-probing oracle.
 */
@Service
@RequiredArgsConstructor
public class ClientPortalService {

    private final ClientRepresentativeRepository representatives;
    private final ClientRepository clients;
    private final ClientService clientService;

    @Transactional(readOnly = true)
    public PortalClientResponse myClient(UUID userId, UUID workspaceId) {
        ClientRepresentative representative = representatives
                .findByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, ClientRepStatus.ACTIVE)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        Client client = clients.findByIdAndWorkspaceId(representative.getClientId(), workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        return new PortalClientResponse(client.getId(), client.getName(), client.getSector(),
                client.getHqCountry(), clientService.mandatesFor(workspaceId, client.getId()));
    }
}
