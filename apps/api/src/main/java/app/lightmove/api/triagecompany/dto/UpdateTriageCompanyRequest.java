package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Either or both of status and flag; a null leaves that field alone. Notes are their own route since V143.
 * {@code executiveIds} names who a move carries — absent, everyone still at the company's old stage.
 */
public record UpdateTriageCompanyRequest(
        @Size(max = 32)
        String status,

        Boolean noExecutiveFound,

        @Size(max = 500)
        List<UUID> executiveIds
) {}
