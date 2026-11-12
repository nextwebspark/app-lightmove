package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.common.service.SearchHitFiling;
import app.lightmove.api.enrichment.sourcing.constant.FoundBy;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.constant.TitleCheck;
import app.lightmove.api.enrichment.sourcing.model.FoundPeople;
import app.lightmove.api.enrichment.sourcing.model.FoundPerson;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Finds the people at one company a search's words describe, spending as little of the live index as it
 * can. The live index is counted free, tier by tier, and bought at most {@code profiles} — the role's own level
 * and core terms first, then the rest of the words. The profile index is asked once, cheaply, who at the company
 * names the function; each of those with no title on record is checked by a free live count narrowed to their
 * name, up to {@code title-checks-per-company}. Someone both indexes found is one person, found by both.
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

    public FoundPeople search(SearchedEmployer employer, SearchWords words, Seniority seat, List<String> countryCodes,
                              int profiles) {
        if (!words.isSearchable() || employer.linkedinSlug() == null) {
            return FoundPeople.none();
        }
        Spend spend = new Spend();
        try {
            if (live.isOffered()) {
                buyLive(employer, words, seat, countryCodes, profiles, spend);
            }
            if (index.isOffered() && !words.roleWords().isEmpty()) {
                checkIndex(employer, words, countryCodes, spend);
            }
        } catch (RuntimeException failed) {
            throw new CompanySearchFailure(spend.credits, spend.cached, failed);
        }
        return new FoundPeople(List.copyOf(spend.found.values()), spend.credits, spend.cached, spend.matched,
                spend.indexFound, spend.indexSearches, spend.titleChecks);
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
                         int profiles, Spend spend) {
        for (TitleTier tier : tiersOf(words, seat)) {
            long matched = live.count(employer, tier, countryCodes);
            spend.matched += matched;
            int wanted = (int) Math.min(matched, profiles - spend.credits);
            if (wanted <= 0) {
                continue;
            }
            PeopleFound bought = cache.live(live, employer, tier, countryCodes, wanted);
            spend.credits += bought.bought();
            spend.cached += bought.cached();
            bought.people().forEach(person -> spend.add(new FoundPerson(person, live.provider(), live.researchedBy(),
                    TitleCheck.LIVE_TITLE, FoundBy.LIVE)));
        }
    }

    private void checkIndex(SearchedEmployer employer, SearchWords words, List<String> countryCodes, Spend spend) {
        PeopleFound named = cache.index(index, employer, words, countryCodes, settings.indexPeoplePerCompany());
        spend.indexSearches += named.askedVendor() ? 1 : 0;
        spend.indexFound += named.people().size();
        for (BrightDataPerson person : named.people()) {
            if (spend.alsoFoundByIndex(person)) {
                continue;
            }
            spend.add(new FoundPerson(person, index.provider(), index.researchedBy(),
                    checkOf(employer, words, person, spend), FoundBy.INDEX));
        }
    }

    private TitleCheck checkOf(SearchedEmployer employer, SearchWords words, BrightDataPerson person, Spend spend) {
        if (person.position() != null && !person.position().isBlank()) {
            return TitleCheck.INDEX_TITLE;
        }
        if (!live.isOffered() || spend.titleChecks >= settings.titleChecksPerCompany()) {
            return TitleCheck.UNCHECKED;
        }
        spend.titleChecks++;
        return live.holdsTitle(employer, SearchHitFiling.nameOf(person), words) ? TitleCheck.CONFIRMED
                : TitleCheck.UNCONFIRMED;
    }

    /** One company's tally as it is searched: everyone found once, by slug, and what finding them cost. */
    private static final class Spend {

        private final Map<String, FoundPerson> found = new LinkedHashMap<>();
        private int credits;
        private int cached;
        private long matched;
        private int indexFound;
        private int indexSearches;
        private int titleChecks;

        private void add(FoundPerson person) {
            String slug = keyOf(person.person());
            if (slug != null) {
                found.putIfAbsent(slug, person);
            }
        }

        /** True when the live index already found this person — now found by both — or when they have no slug. */
        private boolean alsoFoundByIndex(BrightDataPerson person) {
            String slug = keyOf(person);
            if (slug == null) {
                return true;
            }
            FoundPerson earlier = found.get(slug);
            if (earlier == null) {
                return false;
            }
            found.put(slug, earlier.alsoFoundBy(FoundBy.INDEX));
            return true;
        }

        private static String keyOf(BrightDataPerson person) {
            return person.linkedinId() == null || person.linkedinId().isBlank() ? null
                    : person.linkedinId().toLowerCase(Locale.ROOT);
        }
    }
}
