package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.NoticeUnit;
import java.time.LocalDate;
import java.util.List;

/**
 * Step three as the brief returns it.
 *
 * <p>Who the role reports to and how many seats it leads are deliberately not fields here: they are
 * the parent and the children of the chart's mandate seat, and the screen reads them off the chart it
 * already holds. Sending them as well would be a second copy that can disagree with the first.
 */
public record ReportingStructureDto(
        List<OrgNodeDto> orgChart,
        String teamSize,
        /** The mandate's one target date, read from the project. This screen shows it, never sets it. */
        LocalDate targetStart,
        Integer noticeValue,
        NoticeUnit noticeUnit
) {}
