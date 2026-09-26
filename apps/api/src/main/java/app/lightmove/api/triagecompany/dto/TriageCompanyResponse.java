package app.lightmove.api.triagecompany.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A triaged company from its stored snapshot. {@code id} is the triage row's, not the company's;
 * {@code apolloAccountId} is null for a mandate-supplied company.
 */
public record TriageCompanyResponse(
        UUID id,
        String apolloAccountId,
        String source,
        String status,
        String note,
        boolean noExecutiveFound,
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
        Map<String, String> customFields,
        Instant addedAt
) {}
