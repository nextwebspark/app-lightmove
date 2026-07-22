package app.lightmove.api.project.service;

import app.lightmove.api.company.model.CompanyKey;
import app.lightmove.api.company.model.CompanyRefRow;
import app.lightmove.api.company.service.CompanyQueryService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.constant.ClientType;
import app.lightmove.api.project.dto.ClientDtos.ClientDetailResponse;
import app.lightmove.api.project.dto.ClientDtos.ClientListResponse;
import app.lightmove.api.project.dto.ClientDtos.ClientMandateResponse;
import app.lightmove.api.project.dto.ClientDtos.CreateClientRequest;
import app.lightmove.api.project.dto.ClientDtos.RepAvatar;
import app.lightmove.api.project.dto.ClientDtos.RepresentativeResponse;
import app.lightmove.api.project.dto.ClientDtos.UpdateClientRequest;
import app.lightmove.api.project.dto.ClientDtos.ViewerSummary;
import app.lightmove.api.project.dto.ProjectDtos.ProjectResponse;
import app.lightmove.api.project.dto.ProjectDtos.TeamMemberResponse;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The client registry: the records the Clients screen lists, creates and edits, plus the mandate views
 * the drawer renders. Gated on {@code CLIENT_RECORD_MANAGE} at the controller.
 *
 * <p>A DB-picked client's canonical name and domain are resolved from the company universe at write
 * time (never trusted from the request), the same seam Strategy uses; a custom client is typed in. The
 * case-insensitive name unique index is the belt behind the create-time 409.
 */
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final ClientRepresentativeRepository representatives;
    private final CompanyQueryService companies;
    private final ClientRepresentativeService representativeService;
    private final ProjectService projectService;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<ClientListResponse> list(UUID workspaceId) {
        List<Client> allClients = clients.findByWorkspaceIdOrderByNameAsc(workspaceId);
        if (allClients.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<Project>> mandatesByClient = projects.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .stream()
                .collect(Collectors.groupingBy(Project::getClientId));

        List<UUID> clientIds = allClients.stream().map(Client::getId).toList();
        Map<UUID, List<ClientRepresentative>> repsByClient = representatives
                .findByWorkspaceIdAndClientIdIn(workspaceId, clientIds).stream()
                .filter(rep -> rep.getStatus() != ClientRepStatus.REVOKED)
                .collect(Collectors.groupingBy(ClientRepresentative::getClientId));

        return allClients.stream()
                .map(client -> toListResponse(client,
                        mandatesByClient.getOrDefault(client.getId(), List.of()),
                        repsByClient.getOrDefault(client.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientDetailResponse get(UUID workspaceId, UUID clientId) {
        Client client = requireClient(workspaceId, clientId);
        List<Project> mandates = projects.findByWorkspaceIdAndClientId(workspaceId, clientId);
        long active = mandates.stream().filter(project -> !project.getStage().isDone()).count();

        List<RepresentativeResponse> reps = representatives.findByClientIdOrderByCreatedAtAsc(clientId).stream()
                .filter(rep -> rep.getStatus() != ClientRepStatus.REVOKED)
                .map(rep -> new RepresentativeResponse(
                        rep.getId(), rep.getFullName(), rep.getPosition(), rep.getEmail(), rep.getStatus()))
                .toList();

        return new ClientDetailResponse(client.getId(), client.getName(), client.getSector(),
                client.getHqCountry(), client.getDomain(), client.getOffLimitsNote(),
                active, mandates.size() - active, reps, mandatesFor(workspaceId, clientId));
    }

    @Transactional
    public ClientListResponse create(UUID userId, UUID workspaceId, CreateClientRequest request,
                                     HttpServletRequest httpRequest) {
        Client client = request.company() != null
                ? fromUniverse(userId, workspaceId, request)
                : fromCustom(userId, workspaceId, request);

        if (clients.findByWorkspaceIdAndNameIgnoreCase(workspaceId, client.getName()).isPresent()) {
            throw ApiException.of(ErrorCode.CLIENT_ALREADY_EXISTS);
        }
        Client saved = clients.save(client);

        audit.event(ProjectEventType.CLIENT_CREATED)
                .actor(userId).workspace(workspaceId).target("client", saved.getId()).from(httpRequest)
                .detail("name", saved.getName())
                .detail("source", saved.getCompanySource() == null ? "custom" : "universe")
                .record();

        if (request.primaryContact() != null) {
            representativeService.invite(userId, workspaceId, saved.getId(),
                    request.primaryContact().fullName(), request.primaryContact().position(),
                    request.primaryContact().email(), httpRequest);
        }

        // Re-read the representative just created so the row's avatar reflects the invite immediately.
        List<ClientRepresentative> reps = representatives.findByClientIdOrderByCreatedAtAsc(saved.getId());
        return toListResponse(saved, List.of(), reps);
    }

    @Transactional
    public ClientDetailResponse update(UUID userId, UUID workspaceId, UUID clientId,
                                       UpdateClientRequest request, HttpServletRequest httpRequest) {
        Client client = requireClient(workspaceId, clientId);

        String name = request.name().trim();
        if (!name.equalsIgnoreCase(client.getName())
                && clients.findByWorkspaceIdAndNameIgnoreCase(workspaceId, name).isPresent()) {
            throw ApiException.of(ErrorCode.CLIENT_ALREADY_EXISTS);
        }

        client.applyDetails(name, request.sector(), request.hqCountry(), request.domain(),
                request.offLimitsNote());

        audit.event(ProjectEventType.CLIENT_UPDATED)
                .actor(userId).workspace(workspaceId).target("client", clientId).from(httpRequest)
                .record();

        return get(workspaceId, clientId);
    }

    /** One client's mandates as the drawer and the portal render them — lead and health resolved. */
    @Transactional(readOnly = true)
    public List<ClientMandateResponse> mandatesFor(UUID workspaceId, UUID clientId) {
        return projectService.listForClient(workspaceId, clientId).stream()
                .map(ClientService::toMandate)
                .toList();
    }

    private Client fromUniverse(UUID userId, UUID workspaceId, CreateClientRequest request) {
        CompanyKey key = new CompanyKey(request.company().source(), request.company().sourceId());
        CompanyRefRow row = companies.refsByKeys(List.of(key)).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "That company is no longer in the database"));
        // Name and domain are the universe's, not the request's; sector/HQ are the editable overrides.
        String hqCountry = request.hqCountry() != null ? request.hqCountry() : row.hqCountry();
        return Client.fromUniverse(workspaceId, key, row.name(), request.sector(), hqCountry,
                row.domain(), userId);
    }

    private Client fromCustom(UUID userId, UUID workspaceId, CreateClientRequest request) {
        if (request.customName() == null || request.customName().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Enter the company name");
        }
        return Client.custom(workspaceId, request.customName(), request.sector(), request.hqCountry(),
                request.customDomain(), userId);
    }

    private Client requireClient(UUID workspaceId, UUID clientId) {
        return clients.findByIdAndWorkspaceId(clientId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private ClientListResponse toListResponse(Client client, List<Project> mandates,
                                              List<ClientRepresentative> reps) {
        long active = mandates.stream().filter(project -> !project.getStage().isDone()).count();
        ClientType type = mandates.isEmpty() ? ClientType.PROSPECT : ClientType.RETAINED;

        List<RepAvatar> contacts = reps.stream()
                .filter(rep -> rep.getStatus() != ClientRepStatus.REVOKED)
                .map(rep -> new RepAvatar(rep.getFullName(), rep.getStatus()))
                .toList();
        long viewersActive = reps.stream().filter(rep -> rep.getStatus() == ClientRepStatus.ACTIVE).count();
        long viewersInvited = reps.stream().filter(rep -> rep.getStatus() == ClientRepStatus.INVITED).count();

        return new ClientListResponse(client.getId(), client.getName(), type, client.getSector(),
                client.getHqCountry(), active, mandates.size() - active, contacts,
                new ViewerSummary(viewersActive, viewersInvited));
    }

    private static ClientMandateResponse toMandate(ProjectResponse project) {
        String leadName = project.team().stream()
                .filter(member -> member.projectRoles().contains(ProjectRole.LEAD))
                .map(TeamMemberResponse::fullName)
                .findFirst()
                .orElse(null);
        return new ClientMandateResponse(project.id(), project.positionTitle(), project.stage(),
                project.health(), leadName, project.targetDate());
    }
}
