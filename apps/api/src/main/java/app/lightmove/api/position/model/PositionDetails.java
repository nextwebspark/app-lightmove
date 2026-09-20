package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.common.location.model.LocationLine;
import app.lightmove.api.position.constant.FieldSource;
import java.util.List;
import java.util.Map;

/**
 * Step one of the brief: what the role is. The role <i>title</i> is deliberately absent — the mandate
 * keeps one title, on the project, and the write path sets it there for the same reason V8 gave the
 * target date: two copies of one fact drift, and the one typed at project creation stops reaching the
 * screen.
 *
 * <p>{@code fieldSources} is this step's own slice of {@code Position.fieldSources} — department,
 * location, employmentType, seniority, narrative — merged rather than replaced, since the other two
 * steps write disjoint keys of the same map.
 */
public record PositionDetails(
        String department,
        String location,
        EmploymentType employmentType,
        Seniority seniority,
        List<PositionResponsibility> responsibilities,
        String narrative,
        Map<String, FieldSource> fieldSources
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
