package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.common.constant.Seniority;
import java.util.List;

/** Step one as the brief returns it. The role title is the mandate's, echoed here for the screen. */
public record PositionDetailsDto(
        String roleTitle,
        String department,
        String location,
        EmploymentType employmentType,
        Seniority seniority,
        List<String> responsibilities,
        String narrative
) {}
