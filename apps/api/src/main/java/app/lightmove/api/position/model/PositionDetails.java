package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.common.location.service.Countries;
import java.util.List;

/**
 * Step one of the brief: what the role is. The role <i>title</i> is deliberately absent — the mandate
 * keeps one title, on the project, and the write path sets it there for the same reason V8 gave the
 * target date: two copies of one fact drift, and the one typed at project creation stops reaching the
 * screen.
 */
public record PositionDetails(
        String department,
        String locationCity,
        String locationCountry,
        EmploymentType employmentType,
        Seniority seniority,
        List<String> responsibilities,
        String narrative
) {

    public PositionDetails {
        // Each half settled on its own, free text surviving in both: the country to the catalog's
        // spelling, so a brief and the mandate's companies spell one country the same way; the city
        // to the catalog's casing, keeping a spelling the catalog has never met.
        locationCity = Countries.cityOf(locationCity);
        locationCountry = Countries.nameOf(locationCountry);
    }
}
