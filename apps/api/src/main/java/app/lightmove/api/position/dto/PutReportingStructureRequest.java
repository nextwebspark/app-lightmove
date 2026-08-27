package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.NoticeUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Snapshot PUT of step three. */
public record PutReportingStructureRequest(
        @Size(max = 160, message = "That name is too long") String reportsToName,
        @Size(max = 160, message = "That title is too long") String reportsTo,

        @Size(max = 25, message = "That is too many direct reports")
        List<@Valid DirectReportDto> directReports,

        @Size(max = 160, message = "That is too long — a sentence, not a paragraph")
        String teamSize,

        /** Writes through to the project's target date — the mandate's single target field. */
        LocalDate targetStart,

        @Min(value = 0, message = "Notice cannot be negative") Integer noticeValue,
        NoticeUnit noticeUnit
) {}
