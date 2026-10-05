package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Phone", description = "One phone number known for an executive")
public record PublicPhone(
        @Schema(description = "The number, as it arrived", example = "+971 50 123 4567") String number,
        @Schema(description = "work or personal, where a researcher tagged it", nullable = true,
                allowableValues = {"work", "personal"})
        String kind,
        @Schema(description = "A provider or a researcher verified it", example = "false") boolean verified
) {}
