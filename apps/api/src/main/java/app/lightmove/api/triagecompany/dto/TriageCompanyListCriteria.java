package app.lightmove.api.triagecompany.dto;

import java.util.List;

/**
 * A Companies grid request, untrusted as written: the service resolves every token against its enum,
 * so no caller string reaches an ORDER BY. Null means unspecified (the stage's default).
 */
public record TriageCompanyListCriteria(String status, String nameQuery, String executiveQuery,
                                        List<String> executiveStatuses,
                                        String sort, String direction, Integer page, Integer size) {}
