package app.lightmove.api.enrichment.peoplesearch.model;

import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The companies a mandate declined, as a people search reads them: every one, by name and by LinkedIn
 * slug, so a person working at any of them is left off the page however far the list runs.
 */
public record DeclinedEmployers(List<String> names, Set<String> nameKeys, Set<String> slugs) {

    public static DeclinedEmployers of(Collection<TriageCompanyResponse> declined) {
        List<String> names = declined.stream().map(TriageCompanyResponse::companyName).filter(Objects::nonNull).toList();
        return new DeclinedEmployers(names,
                names.stream().map(DeclinedEmployers::key).collect(Collectors.toSet()),
                declined.stream().map(company -> LinkedInUrls.companySlugOrNull(company.companyLinkedinUrl()))
                        .filter(Objects::nonNull).map(DeclinedEmployers::key).collect(Collectors.toSet()));
    }

    public boolean includes(String companyName, String companyLinkedinUrl) {
        String slug = LinkedInUrls.companySlugOrNull(companyLinkedinUrl);
        return (companyName != null && nameKeys.contains(key(companyName))) || (slug != null && slugs.contains(key(slug)));
    }

    private static String key(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
