package app.lightmove.api.project.service;

import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.repository.ClientRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** How other modules read a client record: the project module's repository stays its own. */
@Service
@RequiredArgsConstructor
public class ClientLookup {

    private final ClientRepository clients;

    public Optional<Client> findByIdAndWorkspaceId(UUID clientId, UUID workspaceId) {
        return clients.findByIdAndWorkspaceId(clientId, workspaceId);
    }
}
