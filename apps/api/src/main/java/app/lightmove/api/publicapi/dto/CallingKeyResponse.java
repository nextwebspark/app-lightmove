package app.lightmove.api.publicapi.dto;

import app.lightmove.api.core.security.apikey.ApiKeyKind;
import app.lightmove.api.core.security.apikey.ApiKeyPrincipal;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "CallingKey", description = "The API key a request was made with")
public record CallingKeyResponse(
        @Schema(description = "The key's id") UUID id,
        @Schema(description = "The name its maker gave it", example = "Power BI") String name,
        @Schema(description = "PERSONAL reads only what its owner can open; SERVICE reads every position")
        ApiKeyKind kind,
        @Schema(description = "The workspace every read is scoped to") UUID workspaceId,
        @ArraySchema(arraySchema = @Schema(description = "What the key may read")) List<ApiKeyScope> scopes,
        @Schema(description = "When the key stops working") Instant expiresAt
) {

    public static CallingKeyResponse of(ApiKeyPrincipal key) {
        return new CallingKeyResponse(key.keyId(), key.keyName(), key.kind(), key.workspaceId(), key.scopes(),
                key.expiresAt());
    }
}
