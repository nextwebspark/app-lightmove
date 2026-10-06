package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Documentation only: the RFC 9457 body every error answers, as it is written rather than as Spring models it. */
@Schema(name = "Problem", description = "An RFC 9457 problem document; switch on code, never on detail")
public record PublicProblem(
        @Schema(description = "A URI naming the kind of problem", example = "https://lightmove.app/errors/api-key-scope-missing")
        String type,
        @Schema(description = "A short summary", example = "Forbidden") String title,
        @Schema(description = "The HTTP status", example = "403") int status,
        @Schema(description = "A sentence for a person", example = "This API key does not carry the scope this request needs")
        String detail,
        @Schema(description = "The path that was asked", example = "/api/v1/public/projects") String instance,
        @Schema(description = "The stable error code", example = "API_KEY_SCOPE_MISSING") String code,
        @Schema(description = "When it happened") String timestamp,
        @Schema(description = "Quote this when asking for support") String correlationId,
        @Schema(description = "On API_KEY_SCOPE_MISSING, the scope the request needs", nullable = true,
                example = "companies:read")
        String requiredScope
) {}
