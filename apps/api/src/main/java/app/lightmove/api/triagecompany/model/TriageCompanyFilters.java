package app.lightmove.api.triagecompany.model;

import java.util.List;

/** The grid's three header filters, untrusted as written; the service resolves the status tokens. */
public record TriageCompanyFilters(String companyName, String executiveName,
                                   List<String> executiveStatuses) {

    public TriageCompanyFilters {
        executiveStatuses = executiveStatuses == null ? List.of() : List.copyOf(executiveStatuses);
    }

    public static TriageCompanyFilters none() {
        return new TriageCompanyFilters(null, null, List.of());
    }
}
