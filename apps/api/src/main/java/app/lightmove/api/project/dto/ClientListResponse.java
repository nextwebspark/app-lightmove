package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ClientType;
import java.util.List;
import java.util.UUID;

/** One row of the Clients table. {@code type} and every count are derived, never stored. */
public record ClientListResponse(
        UUID id,
        String name,
        ClientType type,
        String sector,
        String hqCountry,
        long activeMandates,
        long deliveredMandates,
        List<RepAvatar> contacts,
        ViewerSummary viewers
) {}
