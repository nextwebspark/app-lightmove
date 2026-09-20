package app.lightmove.api.position.dto;

import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.constant.FieldSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Snapshot PUT of step one. The role title is required because the mandate cannot be untitled — it is
 * the project's own column, and this write sets it there rather than keeping a second copy.
 */
public record PutPositionDetailsRequest(
        @NotBlank(message = "Give the role a title")
        @Size(max = 160, message = "That title is too long")
        String roleTitle,

        @Size(max = 160, message = "That department name is too long") String department,
        @Size(max = 120, message = "That city name is too long") String locationCity,
        @Size(max = 120, message = "That country name is too long") String locationCountry,
        EmploymentType employmentType,
        Seniority seniority,

        @Size(max = 20, message = "That is too many responsibilities")
        List<@Valid ResponsibilityDto> responsibilities,

        @Size(max = 4000, message = "That narrative is too long")
        String narrative,

        /** Null defaults every key of this step to {@code MANUAL}. */
        Map<String, FieldSource> fieldSources
) {}
