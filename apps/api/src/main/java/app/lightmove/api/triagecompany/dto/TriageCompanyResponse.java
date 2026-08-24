package app.lightmove.api.triagecompany.dto;

import java.util.List;
import java.util.UUID;

/**
 * One company a mandate has triaged, as its triage screen shows it.
 *
 * <p>Every company field is the snapshot stored when the company was taken, not a live read of
 * Apollo. That is the point of storing them: a triage decision has to keep rendering after the
 * pipeline stops publishing its subject.
 *
 * <p>{@code id} is the triage row's id, not the company's — it is what a status change addresses.
 * {@code apolloAccountId} is beside it because that is what a link back to the universe needs, and it
 * is <b>null for a captured company</b> the universe does not publish: {@code origin} says which kind
 * of row this is, and the triage screen marks a capture so a consultant can see the fields were read
 * off a page rather than resolved from the pipeline.
 */
public record TriageCompanyResponse(
        UUID id,
        String apolloAccountId,
        String status,
        String note,
        String companyName,
        String industry,
        String companyCountry,
        String companyCity,
        Integer numEmployees,
        Long annualRevenue,
        String website,
        String linkedinUrl,
        String logoUrl,
        String origin,
        String sourceUrl,
        List<String> tags
) {}
