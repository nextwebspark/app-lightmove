package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.Size;

/**
 * Any mix of status, note and flag; a null leaves that field alone, so clearing a note is an explicit
 * empty string.
 */
public record UpdateTriageCompanyRequest(
        @Size(max = 32)
        String status,

        @Size(max = 2000, message = "A note must be 2000 characters or fewer")
        String note,

        Boolean noExecutiveFound
) {}
