package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** One reviewed person: where the first email goes and the opener as the consultant left it. */
public record EnrollPersonRequest(
        @NotNull UUID candidateId,
        @NotBlank(message = "Choose an address") @Size(max = 320) String toAddress,
        @Size(max = 600, message = "Keep the opener to a sentence or two") String opener,
        boolean openerEdited) {}
