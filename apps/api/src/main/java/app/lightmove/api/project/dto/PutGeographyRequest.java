package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The selected geography markets as ISO country codes. Capped at the catalog's full size; unknown
 * values are rejected in the service against the {@code GeographyMarket} enum.
 */
public record PutGeographyRequest(
        @NotNull
        @Size(max = 6, message = "Too many markets")
        List<String> markets
) {}
