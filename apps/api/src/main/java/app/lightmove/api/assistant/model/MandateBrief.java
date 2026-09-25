package app.lightmove.api.assistant.model;

import app.lightmove.api.position.dto.MandateContextDto;
import app.lightmove.api.position.dto.PositionDetailsDto;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.dto.ResponsibilityDto;
import app.lightmove.api.position.dto.StrategicPriorityDto;
import java.util.List;

/**
 * The position a mandate is hiring for, as much as the model needs to reason about where to source
 * it. Compensation and internal context are left out: neither decides which companies to look at,
 * and both are the sensitive half of a brief.
 */
public record MandateBrief(String roleTitle, String seniority, String department, String locationCity,
                           String locationCountry, List<String> responsibilities, String narrative,
                           String mandateReason, String businessDriver, List<String> strategicPriorities) {

    private static final int MAX_RESPONSIBILITIES = 10;

    public static MandateBrief of(PositionResponse brief) {
        PositionDetailsDto details = brief.details();
        MandateContextDto context = brief.context();
        return new MandateBrief(
                details.roleTitle(),
                details.seniority() == null ? null : details.seniority().name(),
                details.department(),
                details.locationCity(),
                details.locationCountry(),
                details.responsibilities().stream()
                        .map(ResponsibilityDto::text)
                        .limit(MAX_RESPONSIBILITIES)
                        .toList(),
                details.narrative(),
                context.mandateReason() == null ? null : context.mandateReason().name(),
                context.businessDriver(),
                context.strategicPriorities().stream()
                        .filter(StrategicPriorityDto::selected)
                        .map(StrategicPriorityDto::name)
                        .toList());
    }
}
