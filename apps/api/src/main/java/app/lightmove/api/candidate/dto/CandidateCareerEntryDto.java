package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.Size;

/**
 * One post in a career history, as the drawer's repeatable rows send it.
 *
 * <p>{@code period} is free text ("2021–Present", "c. 2015") rather than a date range: a LinkedIn
 * profile publishes month precision, a conference bio publishes a year, and a colleague's
 * recollection publishes neither. Parsing all three would either refuse the entry or invent a
 * precision the source never had.
 */
public record CandidateCareerEntryDto(
        @Size(max = 200)
        String company,

        @Size(max = 200)
        String title,

        @Size(max = 60)
        String period
) {}
