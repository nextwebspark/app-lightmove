package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Attach a client representative to this mandate as a read-only CLIENT seat. */
public record AttachRepresentativeRequest(
        @NotNull(message = "Choose a representative")
        UUID representativeId
) {}
