package app.lightmove.api.assistant.tool;

import java.util.List;

/**
 * A company found by what it does. {@code apolloAccountId} where the company database holds it, else
 * the {@code linkedinSlug} it was found under on LinkedIn — either is what {@code proposeCompanies}
 * takes. {@code sharedNiche} is the niche keywords it has in common with what was asked, rarest first.
 */
public record DiscoveredCompany(String apolloAccountId, String linkedinSlug, String companyName, String industry,
                                String country, String city, Integer employees, List<String> sharedNiche,
                                String mandateStage) {

    DiscoveredCompany inMandateAs(String stageToken) {
        return new DiscoveredCompany(apolloAccountId, linkedinSlug, companyName, industry, country, city, employees,
                sharedNiche, stageToken);
    }
}
