package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.CriterionMode;
import app.lightmove.api.project.constant.DocumentExtractionStatus;
import app.lightmove.api.project.constant.EmploymentType;
import app.lightmove.api.project.constant.MandateReason;
import app.lightmove.api.project.constant.NoticeUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The HTTP contract for the position brief. Writes are snapshot PUTs — the screen always holds the
 * whole document and autosaves whole sections — so requests stay lenient (no min≤max cross-checks)
 * and only the lock endpoint validates readiness.
 */
public final class PositionDtos {

    private PositionDtos() {
    }

    public record PositionResponse(
            MandateReason mandateReason,
            String internalContext,
            String narrative,
            String reportsTo,
            Integer directReports,
            Integer teamSize,
            String location,
            EmploymentType employmentType,
            /** The mandate's one target date — sourced from the project, not a position field. */
            LocalDate startTarget,
            Long salaryMin,
            Long salaryMax,
            String currency,
            Integer noticeValue,
            NoticeUnit noticeUnit,
            Integer bonusTargetPct,
            String ltip,
            List<String> benefits,
            boolean confidential,
            List<CriterionResponse> criteria,
            List<CompetencyDto> technical,
            List<CompetencyDto> behavioural,
            boolean locked,
            Instant lockedAt,
            /** Null when no position description has been uploaded yet. */
            BriefDocumentDto briefDocument
    ) {}

    public record BriefDocumentDto(
            String fileName,
            String contentType,
            long fileSize,
            Instant uploadedAt,
            DocumentExtractionStatus status
    ) {}

    public record CriterionResponse(String text, CriterionMode mode, boolean fromBrief) {}

    public record CriterionRequest(
            @NotBlank(message = "Enter the criterion")
            @Size(max = 300, message = "That criterion is too long")
            String text,

            @NotNull(message = "Choose Required or Preferred")
            CriterionMode mode,

            boolean fromBrief
    ) {}

    public record CompetencyDto(
            @NotBlank(message = "Name the competency")
            @Size(max = 120, message = "That name is too long")
            String name,

            @Min(value = 0, message = "Weights are between 0 and 100")
            @Max(value = 100, message = "Weights are between 0 and 100")
            int weight
    ) {}

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

    public record PutCriteriaRequest(
            @NotNull
            @Size(max = 30, message = "That is too many criteria")
            List<@Valid CriterionRequest> criteria
    ) {}

    public record PutCompetenciesRequest(
            @NotNull
            @Size(max = 10, message = "That is too many competencies")
            List<@Valid CompetencyDto> technical,

            @NotNull
            @Size(max = 10, message = "That is too many competencies")
            List<@Valid CompetencyDto> behavioural
    ) {}
}
