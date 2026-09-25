package app.lightmove.api.report.dto;

import app.lightmove.api.report.constant.ResearcherRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One researcher over the range. {@code quality} is null for someone who filed nobody in it — an
 * empty column has no completeness to grade. {@code daily} is one count per day of the range;
 * {@code lastAddedAt} is their latest filing ever, so a quiet week still says when they last worked.
 */
public record ResearcherDto(
        UUID userId,
        String name,
        String avatarUrl,
        ResearcherRole role,
        int executives,
        int companies,
        double perDay,
        int sharePct,
        Instant lastAddedAt,
        SourcingQualityDto quality,
        List<StatusCountDto> statusMix,
        List<Integer> daily,
        List<SourcedExecutiveDto> recent
) {}
