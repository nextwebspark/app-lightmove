package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.config.ContactOutSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import app.lightmove.api.enrichment.sourcing.service.ContactOutPeopleRecords.ContactOutSearchAnswer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * ContactOut's People Search as the Find executives index — its titles are the current experience's,
 * kept fresh by its own data, where Bright Data's are a cache frozen when LinkedIn locked them on
 * 13 Nov 2025. The run's words become one Boolean title ({@code (seniority OR …) AND (function OR …)}),
 * matched on whole words rather than substrings, and the company is keyed on its website domain, or
 * its name when the row has none — a company LinkedIn URL matches nothing here.
 *
 * <p>Probed live 2026-09-30: seniority and function taxonomies exist but are sparsely filled, so the
 * title carries the search; {@code people/count} is free and {@code people/search} bills one search
 * credit per profile returned (the plan's own monthly pool), and returns them in no useful order. It
 * cannot exclude profiles by slug, so a person already on file may be returned, and billed, again.
 */
@Slf4j
public class ContactOutPeopleSearch implements PeopleSearch {

    static final String VENDOR = "contactout-search";

    /** An indexed search on their side; probed at a second or two. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private static final int MAX_PAGE_SIZE = 25;

    /** A tier finding this many is enough — the default picks per company — and the wider one is not asked. */
    static final int ENOUGH_FROM_A_TIER = 3;

    private final RestClient client;
    private final VendorCallGuard guard;
    private final ObjectMapper json;

    public ContactOutPeopleSearch(ContactOutSettings config, VendorClientFactory clientFactory,
                                  VendorRateLimiter rateLimiter, VendorCallGuard guard, RestClient.Builder builder,
                                  ObjectMapper json) {
        this.guard = guard;
        this.json = json;
        this.client = clientFactory.create(VendorClientSpec.header(VENDOR, config.baseUrl(), "token",
                config.apiKey(), READ_TIMEOUT, config.searchRequestsPerSecond()), builder, rateLimiter);
    }

    @Override
    public String provider() {
        return "contactout";
    }

    @Override
    public EnrichmentVendor researchedBy() {
        return EnrichmentVendor.CONTACTOUT;
    }

    /**
     * The seat's own titles first: each tier is counted free, then fetched whole when it fits one page —
     * a first page of a broad search is an arbitrary slice, and at DP World it held one of the seven CFOs
     * and missed the Group CFO. The next tier is asked only while fewer than {@link #ENOUGH_FROM_A_TIER}
     * have been found, and never returns anyone an earlier tier did.
     *
     * <p>Not {@code @Retryable}: a retry would repeat the tiers already bought, and a failure is handed
     * to Bright Data by {@code CachedPeopleSearch} instead.
     */
    @Override
    public BrightDataPeopleHits currentEmployeesTitled(SearchedEmployer employer, SourcingSpec spec, Seniority seat,
                                                       List<String> countryCodes, List<String> excludedSlugs,
                                                       int size) {
        Optional<Map<String, Object>> base = baseFilter(employer, spec, countryCodes);
        if (base.isEmpty()) {
            log.info("ContactOut people search skipped at {}: no domain or name to key on", employer.linkedinSlug());
            return BrightDataPeopleHits.of(List.of(), 0L);
        }
        List<BrightDataPerson> found = new ArrayList<>();
        long matched = 0;
        for (String title : titleTiers(spec, seat)) {
            Map<String, Object> filter = new LinkedHashMap<>(base.get());
            filter.put("job_title", List.of(title));
            long count = count(filter);
            matched += count;
            int wanted = (int) Math.min(Math.min(count, size - found.size()), MAX_PAGE_SIZE);
            if (wanted > 0) {
                found.addAll(ContactOutPeopleRecords.toHits(search(filter, wanted), employer, json).hits().stream()
                        .filter(person -> livesIn(person, countryCodes))
                        .toList());
            }
            if (found.size() >= ENOUGH_FROM_A_TIER || found.size() >= size) {
                break;
            }
        }
        log.debug("ContactOut people search at {} matched {} in all, returned {}", employer.linkedinSlug(), matched,
                found.size());
        return BrightDataPeopleHits.of(found, matched);
    }

    private long count(Map<String, Object> filter) {
        ContactOutCount answer = guard.call(VendorCall.of(VENDOR, "people-count"), () -> client.post()
                .uri("/v1/people/count")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(filter)
                .retrieve()
                .body(ContactOutCount.class));
        return answer == null || answer.totalResults() == null ? 0 : answer.totalResults();
    }

    private ContactOutSearchAnswer search(Map<String, Object> filter, int pageSize) {
        Map<String, Object> body = new LinkedHashMap<>(filter);
        body.put("page", 1);
        body.put("page_size", pageSize);
        body.put("detailed_experience", true);
        body.put("detailed_education", true);
        body.put("reveal_info", false);
        return guard.call(VendorCall.of(VENDOR, "people-search"), () -> client.post()
                .uri("/v1/people/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ContactOutSearchAnswer.class));
    }

    /**
     * Everything but the title: the company by domain or name, the exclusions, current titles only, and
     * the countries as where the role is. Not also as {@code location}: both together cut Landmark's
     * technology heads from 23 to 4, and a hit living elsewhere is dropped after the answer instead.
     * Empty when the company has neither a domain nor a name.
     */
    static Optional<Map<String, Object>> baseFilter(SearchedEmployer employer, SourcingSpec spec,
                                                    List<String> countryCodes) {
        Map<String, Object> filter = new LinkedHashMap<>();
        if (employer.domain() != null && !employer.domain().isBlank()) {
            filter.put("domain", List.of(employer.domain()));
        } else if (employer.name() != null && !employer.name().isBlank()) {
            filter.put("company", List.of(employer.name().strip()));
        } else {
            return Optional.empty();
        }
        filter.put("current_titles_only", true);
        if (!spec.excludedWords().isEmpty()) {
            filter.put("exclude_job_titles", spec.excludedWords());
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

    /**
     * The Boolean titles to ask, the seat's level first: the seniority words at the brief's level
     * ("Chief", "CFO" for a C-suite seat; "Head", "Director", "VP" for an N-1) with the function, then
     * the rest with the function and {@code NOT} the first ones, so the two tiers never return one person
     * twice. One tier when the words do not split. ContactOut's operators must be upper case to count.
     */
    static List<String> titleTiers(SourcingSpec spec, Seniority seat) {
        TitleLevel wanted = TitleLevel.ofSeat(seat);
        List<String> atSeat = spec.seniorityWords().stream()
                .filter(word -> SourcedHitRanking.levelOf(word) == wanted)
                .toList();
        List<String> rest = spec.seniorityWords().stream().filter(word -> !atSeat.contains(word)).toList();
        String function = anyOf(wholeWordFunction(spec.functionWords()));
        if (atSeat.isEmpty() || rest.isEmpty()) {
            return List.of(both(anyOf(spec.seniorityWords()), function));
        }
        return List.of(both(anyOf(atSeat), function), "(" + both(anyOf(rest), function) + ") NOT " + anyOf(atSeat));
    }

    /** The spelled-out abbreviations put back: ContactOut matches whole words, and at dnata "IT" doubled the heads. */
    static List<String> wholeWordFunction(List<String> functionWords) {
        List<String> words = new ArrayList<>(functionWords);
        SourcedHitRanking.ABBREVIATION_SPELLINGS.forEach((abbreviation, spellings) -> {
            boolean spelled = functionWords.stream()
                    .anyMatch(word -> spellings.contains(word.toLowerCase(Locale.ROOT)));
            if (spelled && words.stream().noneMatch(abbreviation::equalsIgnoreCase)) {
                words.add(abbreviation);
            }
        });
        return words;
    }

    /** The vendor's location filters are loose; a hit known to live outside the searched countries is dropped. */
    private static boolean livesIn(BrightDataPerson person, List<String> countryCodes) {
        return countryCodes.isEmpty() || person.countryCode() == null
                || countryCodes.stream().anyMatch(code -> code.equalsIgnoreCase(person.countryCode()));
    }

    private static String both(String seniority, String function) {
        if (seniority == null) {
            return function;
        }
        return function == null ? seniority : seniority + " AND " + function;
    }

    private static String anyOf(List<String> words) {
        return words.isEmpty() ? null : "(" + String.join(" OR ", words) + ")";
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ContactOutCount(Long totalResults) {}
}
