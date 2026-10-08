package app.lightmove.api.assistant.tool;

import java.util.List;

/**
 * Companies like {@code anchor}, closest first. {@code loosened} names each criterion given up to reach
 * the number asked for, in the order it was given up; {@code shortOf} is how many are still missing
 * once every criterion but the country was loosened.
 */
public record SimilarCompanies(CompanyProfile anchor, List<DiscoveredCompany> companies, List<String> loosened,
                               boolean searchedLinkedIn, int shortOf, String note) {

    static SimilarCompanies refused(String note) {
        return new SimilarCompanies(null, List.of(), List.of(), false, 0, note);
    }
}
