package app.lightmove.api.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One covered universe company: who mapped whom there, and how complete those people are. */
public record CompanyCoverageDto(
        UUID triageCompanyId,
        String name,
        String industry,
        String city,
        String country,
        Integer employees,
        String stage,
        int executives,
        int contributors,
        Instant lastAddedAt,
        List<StatusCountDto> statusMix,
        SourcingQualityDto quality,
        List<SourcedExecutiveDto> mappedExecutives
) {}
