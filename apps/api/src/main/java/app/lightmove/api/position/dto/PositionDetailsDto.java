package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.position.constant.PositionSeniority;
import java.util.List;

/** Step one as the brief returns it. The role title is the mandate's, echoed here for the screen. */
public record PositionDetailsDto(
        String roleTitle,
        String department,
        String location,
        EmploymentType employmentType,
        PositionSeniority seniority,
        List<String> responsibilities,
        String narrative
) {}
