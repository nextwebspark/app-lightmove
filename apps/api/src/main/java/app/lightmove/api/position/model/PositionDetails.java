package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.position.constant.PositionSeniority;
import java.util.List;

/**
 * Step one of the brief: what the role is. The role <i>title</i> is deliberately absent — the mandate
 * keeps one title, on the project, and the write path sets it there for the same reason V8 gave the
 * target date: two copies of one fact drift, and the one typed at project creation stops reaching the
 * screen.
 */
public record PositionDetails(
        String department,
        String location,
        EmploymentType employmentType,
        PositionSeniority seniority,
        List<String> responsibilities,
        String narrative
) {
}
