package app.lightmove.api.project.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.dto.ProjectDtos.ClientResponse;
import app.lightmove.api.project.dto.ProjectDtos.CreateClientRequest;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hiring entities, created inline from the New-project modal. Gated on CLIENT_RECORD_MANAGE at the
 * controller. The pre-check gives the client a clean 409; the case-insensitive unique index is the
 * belt behind it.
 */
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clients;
    private final ProjectRepository projects;

    @Transactional(readOnly = true)
    public List<ClientResponse> list(UUID workspaceId) {
        Map<UUID, List<Project>> byClient = projects.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .stream()
                .collect(Collectors.groupingBy(Project::getClientId));

        return clients.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(client -> toResponse(client, byClient.getOrDefault(client.getId(), List.of())))
                .toList();
    }

    @Transactional
    public ClientResponse create(UUID userId, UUID workspaceId, CreateClientRequest request) {
        String name = request.name().trim();
        if (clients.findByWorkspaceIdAndNameIgnoreCase(workspaceId, name).isPresent()) {
            throw ApiException.of(ErrorCode.CLIENT_ALREADY_EXISTS);
        }

        Client client = clients.save(Client.create(workspaceId, name, request.hqCountry(), userId));
        return toResponse(client, List.of());
    }

    private ClientResponse toResponse(Client client, List<Project> mandates) {
        long active = mandates.stream().filter(p -> !p.getStage().isDone()).count();
        return new ClientResponse(client.getId(), client.getName(), client.getHqCountry(),
                active, mandates.size() - active);
    }
}
