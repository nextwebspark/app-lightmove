package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import org.springframework.modulith.NamedInterface;

/** A researched employer's row on the mandate, and whether this filing created it rather than finding it held. */
@NamedInterface(name = "api", propagate = false)
public record FiledCompany(TriageCompanyResponse company, boolean created) {

    public static FiledCompany held(TriageCompanyResponse company) {
        return new FiledCompany(company, false);
    }
}
