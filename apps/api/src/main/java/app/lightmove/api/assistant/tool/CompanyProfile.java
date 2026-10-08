package app.lightmove.api.assistant.tool;

import java.util.List;

/** One company a name could mean, as the consultant would tell it apart. */
public record CompanyProfile(String apolloAccountId, String linkedinSlug, String companyName, String industry,
                             String country, String city, Integer employees, String about, List<String> niche,
                             String mandateStage) {

    CompanyProfile inMandateAs(String stageToken) {
        return new CompanyProfile(apolloAccountId, linkedinSlug, companyName, industry, country, city, employees,
                about, niche, stageToken);
    }
}
