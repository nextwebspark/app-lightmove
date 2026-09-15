package app.lightmove.api.triagecompany.dto;

/**
 * What one Companies grid is asking for: which stage, narrowed by what, ordered how, and which page.
 *
 * <p>Every field arrives as the caller wrote it and none is trusted — the service resolves the three
 * tokens against their enums and rejects anything that does not land, so no caller-supplied string
 * reaches an ORDER BY or a status comparison. Nulls mean "unspecified" rather than "empty": an omitted
 * sort is the stage's own default, not a request for no ordering at all.
 *
 * <p>{@code executiveQuery} narrows independently of {@code nameQuery} — the grid's own two header
 * filters, company name and executive name, rather than one search box doing both jobs.
 */
public record TriageCompanyListCriteria(String status, String nameQuery, String executiveQuery,
                                        String sort, String direction, Integer page, Integer size) {}
