package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.NoticeUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Snapshot PUT of step three, the org chart included.
 *
 * <p>The chart's own rules — exactly one mandate seat, every parent resolving inside the chart, no
 * cycles — are checked in the service rather than here: they are relationships between elements,
 * which Bean Validation on a flat list cannot express.
 *
 * <p><b>The target date is not here.</b> It belongs to the mandate, and this screen only shows it —
 * the one place it is set is the project itself, so a brief cannot quietly move a date the rest of
 * the workspace is planning around.
 */
public record PutReportingStructureRequest(
        @NotEmpty(message = "The org chart needs at least the role's own seat")
        @Size(max = 60, message = "That is too many seats for one chart")
        List<@Valid OrgNodeDto> orgChart,

        @Size(max = 160, message = "That is too long — a sentence, not a paragraph")
        String teamSize,

        @Min(value = 0, message = "Notice cannot be negative") Integer noticeValue,
        NoticeUnit noticeUnit
) {}
