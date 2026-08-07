package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.dto.ClientDtos.RepresentativeResponse;
import app.lightmove.api.project.dto.ProjectDtos.ProjectResponse;
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
 * Client representatives — the portal guests attached to a client record.
 *
 * <p>Invite issuance goes through {@code workspace}'s {@link InvitationService}: a representative is a
 * CLIENT-role workspace member, and invitations are the only door in. That is the sanctioned project →
 * workspace seam. Acceptance comes back the other way as a {@link ClientRepresentativeAcceptedEvent}, so
 * this feature never has to be reached into by {@code workspace}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientRepresentativeService {

    private final ClientRepository clients;
    private final ClientRepresentativeRepository representatives;
    private final ProjectRepository projectRepository;
    private final InvitationService invitations;
    private final ProjectService projects;
    private final AuditService audit;

    /**
     * Invites a representative to a client's portal. A duplicate of an ACTIVE representative is refused
     * before any membership or email side effect; otherwise an existing row (outstanding or revoked) is
     * reused, never duplicated.
     */
    @Transactional
    public RepresentativeResponse invite(UUID actorId, UUID workspaceId, UUID clientId, String fullName,
                                         String position, String rawEmail, HttpServletRequest request) {
        Client client = clients.findByIdAndWorkspaceId(clientId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        // Normalise once so the representative row and the invitation store the identical address.
        String email = EmailAddressValidator.normalise(rawEmail);

        // Refuse a duplicate before onboarding runs: its notice email is not transactional, so a throw
        // after it would still have mailed the person.
        Optional<ClientRepresentative> existing = representatives
                .findByClientIdAndEmailIgnoreCase(clientId, email);
        if (existing.filter(row -> row.getStatus() == ClientRepStatus.ACTIVE).isPresent()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "That person is already a representative of this client");
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

        // The second INVITED→ACTIVE path: a previously-invited address re-invited once it is already a
        // member activates right here, with no acceptance event — pending attachments must not orphan.
        if (onboarding.existingMember()) {
            projects.seatAcceptedRepresentative(representative);
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
     * Invites a representative <i>to a mandate</i>: creates them on the project's client and attaches
     * them, as one transaction. The Add-client-contact modal's "Invite by email" tab is one decision,
     * and splitting it across two calls left a representative stranded on the client with no seat
     * whenever the second call failed.
     *
     * <p>Lives here rather than in {@code ProjectService} because that direction already exists —
     * {@code ProjectService} must not depend back on this bean. The inner {@code invite} call is a
     * plain self-call: its {@code @Transactional} is inert through the proxy, which costs nothing
     * because this method already opened the transaction it would have joined.
     */
    @Transactional
    public ProjectResponse inviteToMandate(UUID actorId, UUID workspaceId, UUID projectId,
                                           String fullName, String position, String rawEmail,
                                           HttpServletRequest request) {
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        RepresentativeResponse invited = invite(
                actorId, workspaceId, project.getClientId(), fullName, position, rawEmail, request);

        // announce = false: whichever notice this person was owed — the portal invitation, or the
        // "added as a representative" mail for an address already in the workspace — has just gone
        // out. The attach notice would be a second mail for the same click.
        return projects.attachRepresentative(
                actorId, workspaceId, projectId, invited.id(), false, request);
    }

    /**
     * A representative accepted their portal invitation — flip the matching row ACTIVE and bind the
     * account. Runs in the accepting transaction (the event is published before commit), so the
     * membership and the activation are one atomic step.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onRepresentativeAccepted(ClientRepresentativeAcceptedEvent event) {
        representatives
                .findByClientIdAndEmailIgnoreCaseAndStatus(
                        event.clientId(), event.email(), ClientRepStatus.INVITED)
                .ifPresentOrElse(
                        representative -> {
                            representative.activate(event.userId());
                            projects.seatAcceptedRepresentative(representative);
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
