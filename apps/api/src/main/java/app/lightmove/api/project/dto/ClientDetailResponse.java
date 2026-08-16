package app.lightmove.api.project.dto;

import java.util.List;
import java.util.UUID;

/** The client drawer: the record's editable fields plus its representatives and mandates. */
public record ClientDetailResponse(
        UUID id,
        String name,
        String sector,
        String hqCountry,
        String domain,
        String offLimitsNote,
        long activeMandates,
        long deliveredMandates,
        List<RepresentativeResponse> representatives,
        List<ClientMandateResponse> mandates
) {}
