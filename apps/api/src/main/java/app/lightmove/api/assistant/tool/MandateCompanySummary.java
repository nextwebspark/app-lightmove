package app.lightmove.api.assistant.tool;

import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;

/**
 * One company a mandate has filed, as much of it as the model is worth showing.
 *
 * <p>Carries the stage, which is the thing a question about a mandate is usually about, and leaves
 * out the note and the custom fields: both are free text a consultant wrote for other consultants,
 * and neither is something the assistant has been asked to reason over yet.
 */
public record MandateCompanySummary(String companyName, String stage, String industry,
                                    String country, String city, Integer employees,
                                    String website) {

    public static MandateCompanySummary of(TriageCompanyResponse company) {
        return new MandateCompanySummary(company.companyName(), company.status(), company.industry(),
                company.companyCountry(), company.companyCity(), company.numEmployees(),
                company.website());
    }
}
