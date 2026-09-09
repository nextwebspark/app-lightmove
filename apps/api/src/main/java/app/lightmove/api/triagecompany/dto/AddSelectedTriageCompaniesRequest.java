package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Taking the companies a consultant ticked on Strategy into the mandate, all at one stage.
 *
 * <p>Identities only, for {@link AddTriageCompanyRequest}'s reason: the snapshot is resolved
 * server-side. The list is bounded here at a size no page's selection can reach; the real ceiling is
 * the configured bulk-add limit, checked in the service. {@code status} is the stage they all land
 * at, and omitted they land in universe.
 */
public record AddSelectedTriageCompaniesRequest(
        @NotEmpty(message = "Select at least one company")
        @Size(max = 5000, message = "Too many companies in one request")
        List<@NotEmpty @Size(max = 64) String> apolloAccountIds,

        @Size(max = 32)
        String status
) {}
