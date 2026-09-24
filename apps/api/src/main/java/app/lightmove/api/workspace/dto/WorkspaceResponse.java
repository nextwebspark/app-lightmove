package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.model.WorkspacePersona;
import java.time.Instant;
import java.util.UUID;

/** Settings → General. */
public record WorkspaceResponse(
        UUID id,
        String name,
        String slug,
        String logoMark,
        String emailDomain,
        String defaultRegion,
        String defaultCurrency,
        String plan,
        long memberCount,
        Instant createdAt,
        WorkspacePersona persona,
        WorkspaceCompanyResponse company
) {}
