package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.constant.NoticeUnit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Snapshot PUT of the brief's scalar sections; criteria and competencies have their own PUTs. */
public record UpdatePositionRequest(
        @NotNull(message = "Choose a reason for the mandate")
        MandateReason mandateReason,

        String internalContext,
        String narrative,

        @Size(max = 160) String reportsTo,
        @Min(value = 0, message = "Direct reports cannot be negative") Integer directReports,
        @Min(value = 0, message = "Team size cannot be negative") Integer teamSize,
        @Size(max = 120) String location,
        EmploymentType employmentType,
        /** Writes through to the project's target date — the mandate's single target field. */
        LocalDate startTarget,

        @Min(value = 0, message = "Salary cannot be negative") Long salaryMin,
        @Min(value = 0, message = "Salary cannot be negative") Long salaryMax,

        @NotNull(message = "Choose a currency")
        @Pattern(regexp = "[A-Z]{3}", message = "Use a three-letter currency code")
        String currency,

        @Min(value = 0, message = "Notice cannot be negative") Integer noticeValue,
        NoticeUnit noticeUnit,

        @Min(value = 0, message = "A bonus is between 0 and 100%")
        @Max(value = 100, message = "A bonus is between 0 and 100%")
        Integer bonusTargetPct,

        @Size(max = 160) String ltip,

        @Size(max = 20, message = "That is too many benefits")
        List<@NotBlank @Size(max = 80, message = "That benefit label is too long") String> benefits,

        boolean confidential
) {}
