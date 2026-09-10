package app.lightmove.api.position.model;

import app.lightmove.api.common.location.model.LocationLine;
import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.common.constant.Seniority;
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
        Seniority seniority,
        List<String> responsibilities,
        String narrative
) {

    public PositionDetails {
        // Free text on purpose — a role can be "Remote" or name two cities, and a picker cannot say
        // either. Only the country half is settled, so a brief and the mandate's companies spell one
        // country the same way.
        location = canonicalLocation(location);
    }

    private static String canonicalLocation(String location) {
        LocationLine line = LocationLine.of(location);
        if (line.country() == null) {
            return line.city();
        }
        return line.city() == null ? line.country() : line.city() + ", " + line.country();
    }
}
