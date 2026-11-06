package app.lightmove.api.project.model;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ProjectType;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * When a mandate starts and is due. A search may also carry a mapping target, entered by hand and never
 * defaulted; a mapping mandate keeps none.
 */
public record ProjectTimeline(LocalDate startDate, LocalDate deliveryDate, LocalDate mappingTargetDate) {

    /** Checks the dates against each other. */
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

        return new ProjectTimeline(startDate, deliveryDate, mappingTargetDate);
    }
}
