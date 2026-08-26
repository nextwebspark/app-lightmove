package app.lightmove.api.triagecompany.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One company a mandate has triaged, as its Companies grids show it.
 *
 * <p>Every company field is the snapshot stored when the company was taken, not a live read of
 * Apollo. That is the point of storing them: a triage decision has to keep rendering after the
 * pipeline stops publishing its subject.
 *
 * <p>{@code id} is the triage row's id, not the company's — it is what a status change, a note and a
 * delete all address. {@code apolloAccountId} is beside it because that is what a link back to the
 * universe needs, and is null for a company the mandate supplied itself; {@code source} is how the
 * grid tells the two apart.
 */
public record TriageCompanyResponse(
        UUID id,
        String apolloAccountId,
        String source,
        String status,
        String note,
        String companyName,
        String industry,
        String companyCountry,
        String companyCity,
        Integer numEmployees,
        Long annualRevenue,
        String website,
        String companyLinkedinUrl,
        Integer foundedYear,
        String shortDescription,
        String sourceUrl,
        String logoUrl,
        Instant addedAt
) {}
