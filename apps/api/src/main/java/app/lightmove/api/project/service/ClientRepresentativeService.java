package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.dto.RepresentativeResponse;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.ClientRepresentativeAcceptedEvent;
import app.lightmove.api.workspace.model.ClientRepresentativeOnboarding;
import app.lightmove.api.workspace.service.InvitationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Client representatives — the portal guests attached to a client record. Invitations are the only
 * door in, so issuance goes through {@link InvitationService}; acceptance comes back as a
 * {@link ClientRepresentativeAcceptedEvent}, so {@code workspace} never reaches in here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientRepresentativeService {

    private final ClientRepository clients;
    private final ClientRepresentativeRepository representatives;
    private final ProjectRepository projectRepository;
    private final InvitationService invitations;
    private final ProjectTeamService team;
    private final AuditService audit;

    /**
     * An ACTIVE duplicate is refused before any membership or email side effect; an outstanding or
     * revoked row is reused, never duplicated.
     */
    @Transactional
    public RepresentativeResponse invite(UUID actorId, UUID workspaceId, UUID clientId, String fullName,
                                         String position, String rawEmail, HttpServletRequest request) {
        Client client = clients.findByIdAndWorkspaceId(clientId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        String email = EmailAddressValidator.normalise(rawEmail);

        // Before onboarding: its email is not transactional, so a later throw would still have mailed.
        Optional<ClientRepresentative> existing = representatives
                .findByClientIdAndEmailIgnoreCase(clientId, email);
        if (existing.filter(row -> row.getStatus() == ClientRepStatus.ACTIVE).isPresent()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "That person is already a hiring manager of this business unit");
        }

        ClientRepresentativeOnboarding onboarding = invitations.onboardClientRepresentative(
                workspaceId, clientId, client.getName(), email, actorId, request);

        ClientRepresentative representative = existing
                .map(row -> {
                    if (onboarding.existingMember()) {
                        row.refreshDetails(fullName, position);
                        row.activate(onboarding.memberUserId());
                    } else {
                        row.reinvite(fullName, position, onboarding.invitation().getId());
                    }
                    return row;
                })
                .orElseGet(() -> representatives.save(onboarding.existingMember()
                        ? ClientRepresentative.active(workspaceId, clientId, fullName, position, email,
                                onboarding.memberUserId(), actorId)
                        : ClientRepresentative.invited(workspaceId, clientId, fullName, position, email,
                                onboarding.invitation().getId(), actorId)));

        // The second INVITED→ACTIVE path (no acceptance event): pending attachments must not orphan.
        if (onboarding.existingMember()) {
            team.seatAcceptedRepresentative(representative);
        }

        audit.event(ProjectEventType.CLIENT_REP_INVITED)
                .actor(actorId).workspace(workspaceId).target("client", clientId).from(request)
                .detail("representativeId", representative.getId().toString())
                .detail("existingMember", String.valueOf(onboarding.existingMember()))
                .record();

        return new RepresentativeResponse(representative.getId(), representative.getFullName(),
                representative.getPosition(), representative.getEmail(), representative.getStatus());
    }

    /**
     * Invite and attach in one transaction: two calls stranded a seatless representative when the
     * second failed. The inner {@code invite} is a self-call, joined to this transaction.
     */
    @Transactional
    public ProjectResponse inviteToMandate(UUID actorId, UUID workspaceId, UUID projectId,
                                           String fullName, String position, String rawEmail,
                                           HttpServletRequest request) {
        Project project = projectRepository.requireInWorkspace(projectId, workspaceId);

        RepresentativeResponse invited = invite(
                actorId, workspaceId, project.getClientId(), fullName, position, rawEmail, request);

        // announce = false: their notice just went out; a second mail for one click reads as a bug.
        return team.attachRepresentative(
                actorId, workspaceId, projectId, invited.id(), false, request);
    }

    /** Runs in the accepting transaction, so membership and activation are one atomic step. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onRepresentativeAccepted(ClientRepresentativeAcceptedEvent event) {
        representatives
                .findByClientIdAndEmailIgnoreCaseAndStatus(
                        event.clientId(), event.email(), ClientRepStatus.INVITED)
                .ifPresentOrElse(
                        representative -> {
                            representative.activate(event.userId());
                            team.seatAcceptedRepresentative(representative);
                            audit.event(ProjectEventType.CLIENT_REP_ACCEPTED)
                                    .actor(event.userId()).workspace(event.workspaceId())
                                    .target("client", event.clientId())
                                    .detail("representativeId", representative.getId().toString())
                                    .record();
                        },
                        () -> log.warn("No INVITED representative for client {} / {} on accept — "
                                + "the invitation outlived its row", event.clientId(), event.email()));
    }
}
