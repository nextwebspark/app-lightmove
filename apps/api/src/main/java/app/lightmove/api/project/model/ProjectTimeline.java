package app.lightmove.api.project.model;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ProjectType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * When a new mandate starts and when its work is due. A search also carries a mapping target — the
 * point by which the universe should be mapped — defaulted to {@link #MAPPING_SHARE} of the window,
 * the same share the New position modal previews. A mapping mandate's delivery is the map itself, so
 * it keeps no separate target.
 */
public record ProjectTimeline(LocalDate startDate, LocalDate deliveryDate, LocalDate mappingTargetDate) {

    public static final double MAPPING_SHARE = 0.6;

    /** Checks the dates against each other and fills the search's mapping target when none was set. */
    public static ProjectTimeline resolve(ProjectType type, LocalDate startDate, LocalDate deliveryDate,
                                          LocalDate mappingTargetDate) {
        Map<String, String> problems = new LinkedHashMap<>();
        boolean windowIsSet = startDate != null && deliveryDate != null;
        if (windowIsSet && !deliveryDate.isAfter(startDate)) {
            problems.put("deliveryDate", "The delivery date must be after the start date");
        }
        if (type == ProjectType.MAPPING && mappingTargetDate != null) {
            problems.put("mappingTargetDate", "A mapping project has no separate mapping target");
        }
        if (type == ProjectType.SEARCH && mappingTargetDate != null) {
            if (!windowIsSet) {
                problems.put("mappingTargetDate", "A mapping target needs both a start and a delivery date");
            } else if (!mappingTargetDate.isAfter(startDate) || mappingTargetDate.isAfter(deliveryDate)) {
                problems.put("mappingTargetDate", "The mapping target must fall between the start and delivery dates");
            }
        }
        if (!problems.isEmpty()) {
            throw ApiException.withFields(ErrorCode.VALIDATION_FAILED, problems);
        }

        LocalDate mappingTarget = type == ProjectType.SEARCH && mappingTargetDate == null && windowIsSet
                ? defaultMappingTarget(startDate, deliveryDate)
                : mappingTargetDate;
        return new ProjectTimeline(startDate, deliveryDate, mappingTarget);
    }

    static LocalDate defaultMappingTarget(LocalDate startDate, LocalDate deliveryDate) {
        long window = ChronoUnit.DAYS.between(startDate, deliveryDate);
        return startDate.plusDays(Math.round(window * MAPPING_SHARE));
    }
}
