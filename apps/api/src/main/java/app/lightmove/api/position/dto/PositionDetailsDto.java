package app.lightmove.api.position.dto;

import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.constant.FieldSource;
import java.util.List;
import java.util.Map;

/** Step one as the brief returns it. The role title is the mandate's, echoed here for the screen. */
public record PositionDetailsDto(
        String roleTitle,
        String department,
        String locationCity,
        String locationCountry,
        EmploymentType employmentType,
        Seniority seniority,
        List<ResponsibilityDto> responsibilities,
        String narrative,

        /** Provenance of department, location, employmentType, seniority and narrative. */
        Map<String, FieldSource> fieldSources
) {}
