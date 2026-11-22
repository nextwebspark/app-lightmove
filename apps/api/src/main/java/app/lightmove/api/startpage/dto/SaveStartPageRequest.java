package app.lightmove.api.startpage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** The start page to keep: a dashboard for DASHBOARD, a position and its tab for POSITION; HOME forgets the choice. */
public record SaveStartPageRequest(
        @NotBlank @Size(max = 16) String kind,
        UUID dashboardId,
        UUID projectId,
        @Size(max = 16) String section
) {}
