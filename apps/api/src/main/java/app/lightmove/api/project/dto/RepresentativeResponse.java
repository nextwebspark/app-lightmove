package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ClientRepStatus;
import java.util.UUID;

/**
 * One representative on a client record, as the drawer lists them. {@code invitationExpired} and {@code emailFailed}
 * describe an INVITED contact's outstanding invitation — its link lapsed, or the mail provider refused it — and are
 * false otherwise.
 */
public record RepresentativeResponse(
        UUID id,
        String fullName,
        String position,
        String email,
        ClientRepStatus status,
        boolean invitationExpired,
        boolean emailFailed
) {}
