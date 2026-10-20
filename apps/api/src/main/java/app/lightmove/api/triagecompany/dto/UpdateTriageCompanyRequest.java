package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.Size;

/** Either or both of status and flag; a null leaves that field alone. Notes are their own route since V143. */
public record UpdateTriageCompanyRequest(
        @Size(max = 32)
        String status,

        Boolean noExecutiveFound
) {}
