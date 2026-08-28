package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.common.constant.Seniority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Snapshot PUT of step one. The role title is required because the mandate cannot be untitled — it is
 * the project's own column, and this write sets it there rather than keeping a second copy.
 */
public record PutPositionDetailsRequest(
        @NotBlank(message = "Give the role a title")
        @Size(max = 160, message = "That title is too long")
        String roleTitle,

        @Size(max = 160, message = "That department name is too long") String department,
        @Size(max = 120, message = "That location is too long") String location,
        EmploymentType employmentType,
        Seniority seniority,

        @Size(max = 20, message = "That is too many responsibilities")
        List<@NotBlank(message = "Enter the responsibility")
             @Size(max = 200, message = "That responsibility is too long") String> responsibilities,

        String narrative
) {}
