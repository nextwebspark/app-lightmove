package app.lightmove.api.report.dto;

import java.time.Instant;
import java.util.UUID;

/** One executive as the performance drawers list them, with who filed them and when. */
public record SourcedExecutiveDto(
        UUID id,
        String name,
        String title,
        String seniority,
        String companyName,
        String status,
        String addedByName,
        Instant addedAt
) {}
