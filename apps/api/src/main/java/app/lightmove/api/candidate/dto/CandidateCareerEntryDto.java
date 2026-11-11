package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.Size;

/**
 * One post in a career history. {@code period} is free text, since sources publish month, year or neither
 * precision and parsing would refuse or invent one.
 */
public record CandidateCareerEntryDto(
        @Size(max = 200)
        String company,

        @Size(max = 200)
        String title,

        @Size(max = 60)
        String period,

        /** Where the post was held, as research found it; the drawer carries it back unedited. */
        @Size(max = 200)
        String location
) {}
