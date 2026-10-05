package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.security.apikey.ApiKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A new key. {@code kind} is {@code PERSONAL} (the default) or {@code SERVICE}; {@code scopes} are
 * {@code ApiKeyScope} tokens; {@code expiresInDays} defaults to {@code lightmove.public-api.default-key-ttl}.
 */
public record CreateApiKeyRequest(
        @NotBlank(message = "Name the key after what will use it") @Size(max = ApiKey.MAX_NAME) String name,
        String kind,
        @NotEmpty(message = "Choose at least one thing the key can read") List<String> scopes,
        @Positive Integer expiresInDays
) {}
