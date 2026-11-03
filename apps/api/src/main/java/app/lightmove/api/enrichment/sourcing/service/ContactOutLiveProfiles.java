package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleIndex;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords.ContactOutEmployerKey;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/**
 * ContactOut's People Search as the live index: a company is keyed by its website domain or else its name
 * (a company LinkedIn URL matches nothing there), and titles by one Boolean query matched as whole words.
 * {@code people/count} is free; {@code people/search} bills a credit for every profile it returns, and
 * cannot leave out people already on file. Not {@code @Retryable}: a retried search is a second bill.
 */
public class ContactOutLiveProfiles implements LiveProfiles {

    private static final String PROVIDER = "contactout";

    private final ContactOutPeopleIndex index;
    private final ObjectMapper json;

    public ContactOutLiveProfiles(ContactOutPeopleIndex index, ObjectMapper json) {
        this.index = index;
        this.json = json;
    }

    @Override
    public boolean isOffered() {
        return index.isOffered();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public EnrichmentVendor researchedBy() {
        return EnrichmentVendor.CONTACTOUT;
    }

    @Override
    public long count(SearchedEmployer employer, TitleTier tier, List<String> countryCodes) {
        return filter(employer, tier.include(), titleQuery(tier), countryCodes)
                .map(filter -> index.count(filter).total())
                .orElse(0L);
    }

    @Override
    public BrightDataPeopleHits search(SearchedEmployer employer, TitleTier tier, List<String> countryCodes,
                                       int size) {
        return filter(employer, tier.include(), titleQuery(tier), countryCodes)
                .filter(filter -> size > 0)
                .map(filter -> hitsOf(index.search(filter, 1, size), employer))
                .orElseGet(() -> BrightDataPeopleHits.of(List.of(), 0L));
    }

    @Override
    public boolean holdsTitle(SearchedEmployer employer, String personName, SearchWords words) {
        return named(employer, personName, words)
                .map(filter -> index.count(filter).total() > 0)
                .orElse(false);
    }

    @Override
    public BrightDataPeopleHits profileOf(SearchedEmployer employer, String personName, SearchWords words) {
        return named(employer, personName, words)
                .map(filter -> hitsOf(index.search(filter, 1, 1), employer))
                .orElseGet(() -> BrightDataPeopleHits.of(List.of(), 0L));
    }

    /**
     * The tier's titles as ContactOut's Boolean: a level word and a function term, or a standalone title,
     * less whatever the earlier tier already found. Terms of several words are quoted.
     */
    static String titleQuery(TitleTier tier) {
        String include = wordsQuery(tier.include());
        return tier.exclude() == null ? include : "(" + include + ") NOT (" + wordsQuery(tier.exclude()) + ")";
    }

    static String wordsQuery(SearchWords words) {
        List<String> alternatives = new ArrayList<>();
        if (!words.functionTerms().isEmpty()) {
            alternatives.add(words.levelWords().isEmpty() ? anyOf(words.functionTerms())
                    : anyOf(words.levelWords()) + " AND " + anyOf(words.functionTerms()));
        }
        words.standaloneTitles().stream().map(ContactOutLiveProfiles::quoted).forEach(alternatives::add);
        return alternatives.size() == 1 ? alternatives.getFirst()
                : alternatives.stream().map(alternative -> "(" + alternative + ")").collect(Collectors.joining(" OR "));
    }

    private Optional<Map<String, Object>> named(SearchedEmployer employer, String personName, SearchWords words) {
        if (personName == null || personName.isBlank()) {
            return Optional.empty();
        }
        return filter(employer, words, wordsQuery(words), List.of()).map(filter -> {
            filter.put("name", personName.strip());
            return filter;
        });
    }

    /**
     * Everything for one employer and title query, empty when the company has neither a domain nor a name.
     * The countries go as where the person works; a hit living elsewhere still comes back now and then, and
     * is the caller's to narrow.
     */
    private static Optional<Map<String, Object>> filter(SearchedEmployer employer, SearchWords words,
                                                        String titleQuery, List<String> countryCodes) {
        Map<String, Object> filter = new LinkedHashMap<>();
        if (employer.domain() != null && !employer.domain().isBlank()) {
            filter.put("domain", List.of(employer.domain()));
        } else if (employer.name() != null && !employer.name().isBlank()) {
            filter.put("company", List.of(employer.name().strip()));
        } else {
            return Optional.empty();
        }
        filter.put("job_title", List.of(titleQuery));
        filter.put("current_titles_only", true);
        if (!words.excludedWords().isEmpty()) {
            filter.put("exclude_job_titles", words.excludedWords());
        }
        List<String> countries = countryCodes.stream()
                .map(Countries::nameOfCode)
                .flatMap(Optional::stream)
                .toList();
        if (!countries.isEmpty()) {
            filter.put("current_work_location", countries);
        }
        return Optional.of(filter);
    }

    private BrightDataPeopleHits hitsOf(ContactOutPeopleRecords.ContactOutSearchAnswer answer,
                                       SearchedEmployer employer) {
        return ContactOutPeopleRecords.toHits(answer,
                new ContactOutEmployerKey(employer.linkedinSlug(), employer.name()), json);
    }

    private static String anyOf(List<String> terms) {
        return "(" + terms.stream().map(ContactOutLiveProfiles::quoted).collect(Collectors.joining(" OR ")) + ")";
    }

    private static String quoted(String term) {
        return term.contains(" ") ? "\"" + term + "\"" : term;
    }
}
