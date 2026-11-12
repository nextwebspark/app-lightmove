package app.lightmove.api.triagecompany.model;

import java.util.List;

/**
 * The grid's three header filters and its search box, untrusted as written; the service resolves the status
 * tokens. A blank {@code search} narrows nothing.
 */
public record TriageCompanyFilters(String companyName, String executiveName,
                                   List<String> executiveStatuses, String search) {

    public TriageCompanyFilters {
        executiveStatuses = executiveStatuses == null ? List.of() : List.copyOf(executiveStatuses);
    }

    public static TriageCompanyFilters none() {
        return new TriageCompanyFilters(null, null, List.of(), null);
    }
}
