package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.Size;

/**
 * A triage change: a new status, a new note, or both. Both fields are optional and a null leaves that
 * half alone — moving a company to Declined should not silently clear the note explaining why.
 *
 * <p>Clearing a note is therefore an explicit empty string rather than a null, which is the one
 * distinction this shape has to carry.
 */
public record UpdateTriageCompanyRequest(
        @Size(max = 32)
        String status,

        @Size(max = 2000, message = "A note must be 2000 characters or fewer")
        String note
) {}
