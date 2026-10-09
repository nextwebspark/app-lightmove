package app.lightmove.api.assistant.tool;

import java.util.List;

/** One company as a ranking reads it: what it does, where, how big, and where the mandate already has it. */
public record CompanyDetail(String apolloAccountId, String linkedinSlug, String companyName, String industry,
                            String sectorGroup, String country, String city, Integer employees,
                            Integer foundedYear, String about, List<String> niche, String mandateStage) {

    CompanyDetail inMandateAs(String stageToken) {
        return new CompanyDetail(apolloAccountId, linkedinSlug, companyName, industry, sectorGroup, country, city,
                employees, foundedYear, about, niche, stageToken);
    }
}
