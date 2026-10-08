package app.lightmove.api.assistant.tool;

import java.util.List;

/**
 * One company a name could mean, as the consultant would tell it apart: what it does ({@code about},
 * {@code niche}), where and how big. An Apollo account id where the company database holds it, else
 * the LinkedIn slug it was found under; {@code mandateStage} is set when this mandate already filed it.
 */
public record CompanyProfile(String apolloAccountId, String linkedinSlug, String companyName, String industry,
                             String country, String city, Integer employees, String about, List<String> niche,
                             String mandateStage) {

    CompanyProfile inMandateAs(String stageToken) {
        return new CompanyProfile(apolloAccountId, linkedinSlug, companyName, industry, country, city, employees,
                about, niche, stageToken);
    }
}
