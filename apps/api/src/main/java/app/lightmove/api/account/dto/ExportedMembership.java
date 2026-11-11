package app.lightmove.api.account.dto;

import app.lightmove.api.core.security.dto.ApiKeyResponse;
import app.lightmove.api.core.security.dto.OAuthGrantResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One workspace the person is or was in, with the personal API keys and AI apps they made there. */
public record ExportedMembership(
        UUID workspaceId,
        String workspaceName,
        List<String> roles,
        String status,
        Instant joinedAt,
        List<ApiKeyResponse> apiKeys,
        List<OAuthGrantResponse> aiApps
) {}
