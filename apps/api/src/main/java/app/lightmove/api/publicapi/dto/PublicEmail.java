package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Email", description = "One email address known for an executive")
public record PublicEmail(
        @Schema(description = "The address", example = "l.haddad@example.com") String address,
        @Schema(description = "work or personal, where known", nullable = true, allowableValues = {"work", "personal"})
        String kind,
        @Schema(description = "A provider or a researcher verified it", example = "true") boolean verified
) {}
