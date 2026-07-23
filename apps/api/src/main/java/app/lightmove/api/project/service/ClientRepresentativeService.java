package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.dto.ClientDtos.RepresentativeResponse;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
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
    private final InvitationService invitations;
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

        audit.event(ProjectEventType.CLIENT_REP_INVITED)
                .actor(actorId).workspace(workspaceId).target("client", clientId).from(request)
                .detail("representativeId", representative.getId().toString())
                .detail("existingMember", String.valueOf(onboarding.existingMember()))
                .record();

        return new RepresentativeResponse(representative.getId(), representative.getFullName(),
                representative.getPosition(), representative.getEmail(), representative.getStatus());
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
