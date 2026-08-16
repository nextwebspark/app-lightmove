package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ClientRepStatus;
import java.util.UUID;

/** One representative on a client record, as the drawer lists them. */
public record RepresentativeResponse(
        UUID id,
        String fullName,
        String position,
        String email,
        ClientRepStatus status
) {}
