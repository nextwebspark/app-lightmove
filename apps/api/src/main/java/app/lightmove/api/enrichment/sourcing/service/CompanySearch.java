package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.common.service.SearchHitFiling;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.constant.TitleCheck;
import app.lightmove.api.enrichment.sourcing.model.FoundPeople;
import app.lightmove.api.enrichment.sourcing.model.FoundPerson;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Finds the people at one company a search's words describe, spending as little of the live index as it
 * can. The live index is counted free, tier by tier, and bought at most {@code profiles-per-company} — the
 * role's own level and core terms first, then the rest of the words. The profile index is asked once,
 * cheaply, who at the company names the function; each of those with no title on record is checked by a
 * free live count narrowed to their name, up to {@code title-checks-per-company}. Nobody the mandate already
 * holds is returned.
 *
 * <p>Measured against researchers' own mapping (docs/find-executives.md), the free checks and three bought
 * profiles a company found nearly as many of them as buying every match would have.
 */
@Service
public class CompanySearch {

    private final LiveProfiles live;
    private final ProfileIndex index;
    private final PeopleCache cache;
    private final ExecutiveSourcingSettings settings;

    public CompanySearch(LiveProfiles live, ProfileIndex index, PeopleCache cache, LightMoveProperties properties) {
        this.live = live;
        this.index = index;
        this.cache = cache;
        this.settings = properties.enrichment().sourcing();
    }

    public boolean isOffered() {
        return live.isOffered() || index.isOffered();
    }

    /** {@code heldSlugs} are profile slugs already mapped in the mandate, in any case. */
    public FoundPeople search(SearchedEmployer employer, SearchWords words, Seniority seat, List<String> countryCodes,
                              Set<String> heldSlugs) {
        if (!words.isSearchable() || employer.linkedinSlug() == null) {
            return FoundPeople.none();
        }
        Spend spend = new Spend(heldSlugs);
        if (live.isOffered()) {
            buyLive(employer, words, seat, countryCodes, spend);
        }
        if (index.isOffered() && !words.roleWords().isEmpty()) {
            checkIndex(employer, words, countryCodes, spend);
        }
        return new FoundPeople(spend.found, spend.heldFound.size(), spend.credits, spend.cached, spend.matched,
                spend.indexSearches, spend.titleChecks);
    }

    /**
     * The tiers bought in order: the role's own level and up with the core terms, then everything else the
     * words find. One tier when the two would be the same.
     */
    static List<TitleTier> tiersOf(SearchWords words, Seniority seat) {
        SearchWords first = words.narrowedTo(SearchLevel.firstTierFloor(seat));
        return first.key().equals(words.key()) ? List.of(TitleTier.of(words))
                : List.of(TitleTier.of(first), new TitleTier(words, first));
    }

    private void buyLive(SearchedEmployer employer, SearchWords words, Seniority seat, List<String> countryCodes,
                         Spend spend) {
        for (TitleTier tier : tiersOf(words, seat)) {
            long matched = live.count(employer, tier, countryCodes);
            spend.matched += matched;
            int wanted = (int) Math.min(matched, settings.profilesPerCompany() - spend.credits);
            if (wanted <= 0) {
                continue;
            }
            PeopleFound bought = cache.live(live, employer, tier, countryCodes, wanted);
            spend.credits += bought.bought();
            spend.cached += bought.cached();
            bought.people().forEach(person -> spend.add(person, live.provider(), live.researchedBy(), TitleCheck.LIVE_TITLE));
        }
    }

    private void checkIndex(SearchedEmployer employer, SearchWords words, List<String> countryCodes, Spend spend) {
        PeopleFound named = cache.index(index, employer, words, countryCodes, settings.indexPeoplePerCompany());
        spend.indexSearches += named.askedVendor() ? 1 : 0;
        spend.cached += named.cached();
        for (BrightDataPerson person : named.people()) {
            if (spend.isKnown(person)) {
                continue;
            }
            spend.add(person, index.provider(), index.researchedBy(), checkOf(employer, words, person, spend));
        }
    }

    private TitleCheck checkOf(SearchedEmployer employer, SearchWords words, BrightDataPerson person, Spend spend) {
        if (person.position() != null && !person.position().isBlank()) {
            return TitleCheck.INDEX_TITLE;
        }
        if (!live.isOffered() || spend.titleChecks >= settings.titleChecksPerCompany()) {
            return TitleCheck.UNCONFIRMED;
        }
        spend.titleChecks++;
        return live.holdsTitle(employer, SearchHitFiling.nameOf(person), words) ? TitleCheck.CONFIRMED
                : TitleCheck.UNCONFIRMED;
    }

    /** One company's tally as it is searched, and the slugs it may not return twice. */
    private static final class Spend {

        private final Set<String> held = new HashSet<>();
        private final Set<String> heldFound = new HashSet<>();
        private final Set<String> seen = new HashSet<>();
        private final List<FoundPerson> found = new ArrayList<>();
        private int credits;
        private int cached;
        private long matched;
        private int indexSearches;
        private int titleChecks;

        private Spend(Set<String> heldSlugs) {
            heldSlugs.forEach(slug -> held.add(slug.toLowerCase(Locale.ROOT)));
        }

        /** Already found, already held — counted as held once — or with no slug to key on. */
        private boolean isKnown(BrightDataPerson person) {
            if (person.linkedinId() == null || person.linkedinId().isBlank()) {
                return true;
            }
            String slug = person.linkedinId().toLowerCase(Locale.ROOT);
            if (held.contains(slug)) {
                heldFound.add(slug);
                return true;
            }
            return seen.contains(slug);
        }

        private void add(BrightDataPerson person, String provider, EnrichmentVendor researchedBy, TitleCheck check) {
            if (isKnown(person)) {
                return;
            }
            seen.add(person.linkedinId().toLowerCase(Locale.ROOT));
            found.add(new FoundPerson(person, provider, researchedBy, check));
        }
    }
}
