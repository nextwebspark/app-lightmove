package app.lightmove.api.companydiscovery.model;

import java.util.Set;

/**
 * Every key a mandate's already-filed companies can be recognised by, read once per request rather
 * than once per candidate.
 *
 * <p>Four sets because a discovered row and a filed row may not have been recognised the same way:
 * one arrived by universe id, another was typed in by name, a third captured off a LinkedIn page.
 * Names are lower-cased and domains and slugs come from the same two functions the resolver uses on
 * the candidate, so both sides are keyed identically.
 */
public record HeldCompanies(Set<String> apolloAccountIds, Set<String> lowerNames,
                            Set<String> domains, Set<String> linkedinSlugs) {

    /** No mandate was named, so nothing is known to be held — never a claim that nothing is. */
    public static HeldCompanies none() {
        return new HeldCompanies(Set.of(), Set.of(), Set.of(), Set.of());
    }

    public boolean holdsApolloId(String apolloAccountId) {
        return apolloAccountId != null && apolloAccountIds.contains(apolloAccountId);
    }

    public boolean holdsName(String lowerName) {
        return lowerName != null && lowerNames.contains(lowerName);
    }

    public boolean holdsDomain(String domain) {
        return domain != null && domains.contains(domain);
    }

    public boolean holdsSlug(String slug) {
        return slug != null && linkedinSlugs.contains(slug);
    }
}
