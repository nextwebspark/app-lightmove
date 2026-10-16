package app.uncava.api.workspace.dto;

import app.uncava.api.common.persona.model.HiringPersona;
import app.uncava.api.workspace.constant.CalendarSync;
import app.uncava.api.workspace.constant.WorkspaceMode;
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
        CalendarSync calendarSync,
        String defaultRegion,
        String defaultCurrency,
        String plan,
        long memberCount,
        Instant createdAt,
        HiringPersona persona,
        WorkspaceCompanyResponse company
) {}
