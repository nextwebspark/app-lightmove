package app.lightmove.api.candidate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The Contact section's save: every email and phone the person should now hold, both channels at
 * once. A row absent from its list is removed; one already held keeps its source and takes the
 * entry's kind and verified; a new one is the writer's.
 */
public record UpdateCandidateContactsRequest(
        @Size(max = 10, message = "Ten email addresses is the most a profile holds")
        List<@Valid ContactEntryDto> emails,

        @Size(max = 10, message = "Ten phone numbers is the most a profile holds")
        List<@Valid ContactEntryDto> phones
) {}
