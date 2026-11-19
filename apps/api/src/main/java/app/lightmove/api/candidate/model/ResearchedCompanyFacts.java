package app.lightmove.api.candidate.model;

import java.util.List;

/** What research said of the person's current employer; every field may be absent. */
public record ResearchedCompanyFacts(String name, String website, String domain, String emailDomain, String industry,
                                     String type, String size, Long employees, String country, String headquarter,
                                     Integer foundedYear, String revenue, String overview, String logoUrl,
                                     List<String> specialties, List<String> locations, String fundingStatus,
                                     Integer fundingRounds, String latestFundingDate) {

    public ResearchedCompanyFacts {
        specialties = specialties == null ? List.of() : List.copyOf(specialties);
        locations = locations == null ? List.of() : List.copyOf(locations);
    }

    /** Each fact of this one, else {@code fallback}'s. */
    public ResearchedCompanyFacts orElse(ResearchedCompanyFacts fallback) {
        if (fallback == null) {
            return this;
        }
        return new ResearchedCompanyFacts(first(name, fallback.name), first(website, fallback.website),
                first(domain, fallback.domain), first(emailDomain, fallback.emailDomain),
                first(industry, fallback.industry), first(type, fallback.type), first(size, fallback.size),
                first(employees, fallback.employees), first(country, fallback.country),
                first(headquarter, fallback.headquarter), first(foundedYear, fallback.foundedYear),
                first(revenue, fallback.revenue), first(overview, fallback.overview), first(logoUrl, fallback.logoUrl),
                specialties.isEmpty() ? fallback.specialties : specialties,
                locations.isEmpty() ? fallback.locations : locations, first(fundingStatus, fallback.fundingStatus),
                first(fundingRounds, fallback.fundingRounds), first(latestFundingDate, fallback.latestFundingDate));
    }

    private static <T> T first(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
