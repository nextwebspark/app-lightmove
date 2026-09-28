package app.lightmove.api.workspace.dto;

import app.lightmove.api.common.persona.model.HiringPersona;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import java.time.Instant;
import java.util.UUID;

/** Settings → General. */
public record WorkspaceResponse(
        UUID id,
        String name,
        String slug,
        String logoMark,
        String emailDomain,
        WorkspaceMode mode,
        String defaultRegion,
        String defaultCurrency,
        String plan,
        long memberCount,
        Instant createdAt,
        HiringPersona persona,
        WorkspaceCompanyResponse company
) {}
