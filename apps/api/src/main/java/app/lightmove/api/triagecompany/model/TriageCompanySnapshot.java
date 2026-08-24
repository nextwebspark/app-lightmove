package app.lightmove.api.triagecompany.model;

/**
 * The display fields a triage row freezes at write time.
 *
 * <p>Exists so {@link TriageCompany}'s factories take one argument for "what this company looked like"
 * rather than nine positional strings, and so the entity needs no knowledge of where the fields came
 * from — the Apollo universe for a company Strategy took, the page itself for one the extension read.
 *
 * <p>These are a snapshot, never a live read: the Apollo pipeline reloads its table wholesale, and a
 * triage decision that loses its subject on the next load is worse than a stale one.
 */
public record TriageCompanySnapshot(
        String companyName,
        String industry,
        String companyCountry,
        String companyCity,
        Integer numEmployees,
        Long annualRevenue,
        String website,
        String linkedinUrl,
        String logoUrl
) {}
