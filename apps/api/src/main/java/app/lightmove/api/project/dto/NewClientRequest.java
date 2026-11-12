package app.lightmove.api.project.dto;

import app.lightmove.api.project.model.NewClientDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * The client a new position is created with, named as {@link CreateClientRequest} names one — a universe pick or a
 * typed name — but with no primary contact, whose invite would be emailed before the position is known to save.
 */
public record NewClientRequest(
        @Valid CompanyPickDto company,

        @Size(max = 160, message = "That name is too long")
        String customName,

        @Size(max = 96, message = "That sector is too long")
        String sector
) {

    public NewClientDetails details() {
        return new NewClientDetails(company == null ? null : company.apolloAccountId(), customName, null, sector,
                null);
    }
}
