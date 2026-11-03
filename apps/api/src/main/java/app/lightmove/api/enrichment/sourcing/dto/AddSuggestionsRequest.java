package app.lightmove.api.enrichment.sourcing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The people ticked in the panel, each at the company a search found them at. */
public record AddSuggestionsRequest(
        @NotEmpty(message = "Tick someone to add")
        @Size(max = 50, message = "Add at most 50 people at a time")
        List<@Valid @NotNull PickedPerson> people
) {

    public record PickedPerson(@NotNull UUID triageCompanyId, @NotBlank @Size(max = 200) String linkedinSlug) {}
}
