package app.lightmove.api.project.service;

import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.dto.RepresentativeResponse;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.workspace.model.ClientInvitationState;
import app.lightmove.api.workspace.service.InvitationService;
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

    List<RepresentativeResponse> listed(UUID workspaceId, List<ClientRepresentative> representatives) {
        Map<UUID, ClientInvitationState> states = invitations.clientInvitationStates(workspaceId,
                representatives.stream()
                        .filter(representative -> representative.getStatus() == ClientRepStatus.INVITED)
                        .map(ClientRepresentative::getInvitationId)
                        .filter(Objects::nonNull)
                        .toList());
        return representatives.stream()
                .map(representative -> toResponse(representative, states.get(representative.getInvitationId())))
                .toList();
    }

    RepresentativeResponse viewOf(UUID workspaceId, ClientRepresentative representative) {
        return listed(workspaceId, List.of(representative)).getFirst();
    }

    private static RepresentativeResponse toResponse(ClientRepresentative representative,
                                                     ClientInvitationState invitation) {
        boolean invited = representative.getStatus() == ClientRepStatus.INVITED && invitation != null;
        return new RepresentativeResponse(representative.getId(), representative.getFullName(),
                representative.getPosition(), representative.getEmail(), representative.getStatus(),
                invited && invitation.expired(), invited && invitation.emailFailed());
    }
}
