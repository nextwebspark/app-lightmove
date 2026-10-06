package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(name = "Timeline", description = "A position's dates, each null until it is set")
public record PublicTimeline(
        @Schema(description = "When the work started", nullable = true, example = "2026-09-01") LocalDate startDate,
        @Schema(description = "When the map is due, on a search", nullable = true, example = "2026-10-20")
        LocalDate mappingTargetDate,
        @Schema(description = "When the map or shortlist is due", nullable = true, example = "2026-11-30")
        LocalDate deliveryDate,
        @Schema(description = "When the hire is wanted", nullable = true, example = "2027-01-15") LocalDate targetDate
) {}
