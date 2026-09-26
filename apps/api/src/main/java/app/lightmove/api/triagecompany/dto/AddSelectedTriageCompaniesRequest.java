package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Strategy's ticked companies, all landing at one stage. Identities only — the snapshot is resolved
 * server-side; the real ceiling is the configured bulk-add limit, not this {@code @Size}.
 */
public record AddSelectedTriageCompaniesRequest(
        @NotEmpty(message = "Select at least one company")
        @Size(max = 5000, message = "Too many companies in one request")
        List<@NotEmpty @Size(max = 64) String> apolloAccountIds,

        @Size(max = 32)
        String status
) {}
