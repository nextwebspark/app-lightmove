package app.lightmove.api.assistant.tool;

import java.util.List;

/** A company found by who it is like or what it does; its account id, else its slug, goes to proposeCompanies. */
public record DiscoveredCompany(String apolloAccountId, String linkedinSlug, String companyName, String industry,
                                String country, String city, Integer employees, List<String> sharedNiche,
                                String mandateStage) {

    DiscoveredCompany inMandateAs(String stageToken) {
        return new DiscoveredCompany(apolloAccountId, linkedinSlug, companyName, industry, country, city, employees,
                sharedNiche, stageToken);
    }
}
