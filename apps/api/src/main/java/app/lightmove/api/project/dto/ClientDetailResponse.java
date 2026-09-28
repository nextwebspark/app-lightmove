package app.lightmove.api.project.dto;

import app.lightmove.api.common.persona.model.HiringPersona;
import java.util.List;
import java.util.UUID;

/** The client drawer: the record's editable fields plus its representatives and mandates. */
public record ClientDetailResponse(
        UUID id,
        String name,

        /** The universe company the record was picked as; null for one typed in by hand. */
        String apolloAccountId,

        String sector,
        String hqCountry,
        String hqCity,
        String logoUrl,
        String domain,
        String offLimitsNote,
        String notes,
        HiringPersona persona,
        long activeMandates,
        long deliveredMandates,
        List<RepresentativeResponse> representatives,
        List<ClientMandateResponse> mandates
) {}
