package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.MandatePhase;
import java.time.LocalDate;

/**
 * How far a mandate has got, as the projects list and its drawer draw it. Nested inside
 * {@code ProjectResponse} rather than flattened into it: these numbers are one subject, and the row
 * already carries a dozen fields about everything else.
 */
public record MandateProgressDto(
        MandatePhase activePhase,
        boolean mappingComplete,
        int mapPercent,
        int engagePercent,
        long universeCompanies,
        long companiesResearched,
        long candidatesMapped,
        long candidatesEngaged,
        long qualifiedMatches,
        double mappingVelocityPerWeek,
        /** The milestone health is currently measured against — the mapping target, or the shortlist. */
        LocalDate governingMilestone,
        Integer daysRemaining
) {}
