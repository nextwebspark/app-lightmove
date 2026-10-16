package app.uncava.api.position.model;

import app.uncava.api.common.constant.EmploymentType;
import app.uncava.api.common.constant.Seniority;
import app.uncava.api.common.location.service.Countries;
import app.uncava.api.position.constant.FieldSource;
import java.util.List;
import java.util.Map;

/**
 * Step one of the brief: what the role is. The role title is deliberately absent — it lives on the
 * project, so two copies cannot drift.
 */
public record PositionDetails(
        String department,
        String locationCity,
        String locationCountry,
        EmploymentType employmentType,
        Seniority seniority,
        List<PositionResponsibility> responsibilities,
        String narrative,
        Map<String, FieldSource> fieldSources
) {

    public PositionDetails {
        // The country to the catalog's spelling, so a brief and the mandate's companies agree on it.
        locationCity = Countries.cityOf(locationCity);
        locationCountry = Countries.nameOf(locationCountry);
    }
}
