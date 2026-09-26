package app.lightmove.api.position.dto;

import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.position.constant.FieldSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Snapshot PUT of step three; the chart's cross-element rules are {@code OrgChartRules}'. The target
 * date is deliberately absent: only the project sets it.
 */
public record PutReportingStructureRequest(
        @NotEmpty(message = "The org chart needs at least the role's own seat")
        @Size(max = 60, message = "That is too many seats for one chart")
        List<@Valid OrgNodeDto> orgChart,

        @Size(max = 160, message = "That is too long — a sentence, not a paragraph")
        String teamSize,

        @Min(value = 0, message = "Notice cannot be negative") Integer noticeValue,
        NoticeUnit noticeUnit,

        /** Null defaults every key of this step to {@code MANUAL}. */
        Map<String, FieldSource> fieldSources
) {}
