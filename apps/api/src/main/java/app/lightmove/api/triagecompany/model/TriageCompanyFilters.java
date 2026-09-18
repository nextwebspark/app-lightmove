package app.lightmove.api.triagecompany.model;

import java.util.List;

/**
 * The Companies grid's three header filters, as the caller wrote them — company name, executive name,
 * and the Status column's ticked set. Every value is untrusted: the service resolves the status
 * tokens against their enum and rejects anything that does not land.
 *
 * <p>A record rather than three more parameters on the unpaged read: two of its three callers narrow
 * by nothing at all, and {@link #none()} says that far better than a pair of nulls and an empty list.
 */
public record TriageCompanyFilters(String companyName, String executiveName,
                                   List<String> executiveStatuses) {

    public TriageCompanyFilters {
        executiveStatuses = executiveStatuses == null ? List.of() : List.copyOf(executiveStatuses);
    }

    /** The whole stage — what a globe and a report ask for, neither having a header to filter by. */
    public static TriageCompanyFilters none() {
        return new TriageCompanyFilters(null, null, List.of());
    }
}
