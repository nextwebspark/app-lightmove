package app.lightmove.api.project.service;

import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.dto.RepresentativeResponse;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.service.InvitationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A client record's contacts as the drawer lists them, an invited one with its invitation's state. */
@Component
@RequiredArgsConstructor
class RepresentativeViews {

    private final InvitationService invitations;
    private final Clock clock;

    List<RepresentativeResponse> of(UUID workspaceId, List<ClientRepresentative> representatives) {
        Map<UUID, Invitation> outstanding = invitations.invitationsOf(workspaceId, representatives.stream()
                .filter(representative -> representative.getStatus() == ClientRepStatus.INVITED)
                .map(ClientRepresentative::getInvitationId)
                .filter(Objects::nonNull)
                .toList());
        Instant now = clock.instant();
        return representatives.stream()
                // An immutable map throws on a null key, and an active contact has no invitation id.
                .map(representative -> of(representative, representative.getInvitationId() == null ? null
                        : outstanding.get(representative.getInvitationId()), now))
                .toList();
    }

    RepresentativeResponse of(UUID workspaceId, ClientRepresentative representative) {
        return of(workspaceId, List.of(representative)).getFirst();
    }

    private static RepresentativeResponse of(ClientRepresentative representative, Invitation invitation, Instant now) {
        boolean invited = representative.getStatus() == ClientRepStatus.INVITED && invitation != null;
        return new RepresentativeResponse(representative.getId(), representative.getFullName(),
                representative.getPosition(), representative.getEmail(), representative.getStatus(),
                invited && !invitation.isRedeemable(now),
                invited && invitation.getEmailFailedAt() != null);
    }
}
