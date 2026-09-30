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
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * ContactOut's People Search, matched on current titles as whole words. The company is keyed by website
 * domain or name (a company LinkedIn URL matches nothing), {@code people/count} is free and each profile
 * {@code people/search} returns is billed, in no useful order. Findings: the sourcing investigation doc.
 */
@Slf4j
public class ContactOutPeopleSearch implements PeopleSearch {

    static final String VENDOR = "contactout-search";

    /** An indexed search on their side; probed at a second or two. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private static final int MAX_PAGE_SIZE = 25;

    /** ContactOut's own operators; a word spelling one would change the query rather than join it. */
    private static final Set<String> OPERATORS = Set.of("and", "or", "not");

    private static final Pattern SEARCHABLE_WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}&.-]*");

    private final RestClient client;
    private final VendorCallGuard guard;
    private final ObjectMapper json;
    private final int enoughFromATier;

    /** @param enoughFromATier the people a tier must find before a wider one is not asked — the picks per company */
    public ContactOutPeopleSearch(ContactOutSettings config, VendorClientFactory clientFactory,
                                  VendorRateLimiter rateLimiter, VendorCallGuard guard, RestClient.Builder builder,
                                  ObjectMapper json, int enoughFromATier) {
        this.guard = guard;
        this.json = json;
        this.enoughFromATier = enoughFromATier;
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
     * The seat's own titles first, each tier counted free before anything is bought; a wider tier only
     * while fewer than {@code enoughFromATier} are found. Not {@code @Retryable}: a retry would re-buy the
     * tiers already fetched, and a failure falls back to Bright Data instead.
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
            if (found.size() >= enoughFromATier || found.size() >= size) {
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
     * Everything but the title, empty when the company has neither a domain nor a name. The countries go
     * as where the role is only: adding where the person lives as well cut matches several-fold, so a hit
     * living elsewhere is dropped after the answer instead.
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
     * The Boolean titles to ask, the seat's level first — the seniority words at the brief's level with
     * the function — then the rest with the function and {@code NOT} the first ones, so no one is bought
     * twice. One tier when the words do not split; none when no word is searchable.
     */
    static List<String> titleTiers(SourcingSpec spec, Seniority seat) {
        TitleLevel wanted = TitleLevel.ofSeat(seat);
        List<String> seniority = searchable(spec.seniorityWords());
        List<String> atSeat = seniority.stream().filter(word -> SourcedHitRanking.levelOf(word) == wanted).toList();
        List<String> rest = seniority.stream().filter(word -> !atSeat.contains(word)).toList();
        List<String> functionWords = new ArrayList<>(searchable(spec.functionWords()));
        SourcingSpec.abbreviationsOf(functionWords).stream()
                .filter(abbreviation -> functionWords.stream().noneMatch(abbreviation::equalsIgnoreCase))
                .forEach(functionWords::add);
        String function = anyOf(functionWords);
        if (atSeat.isEmpty() || rest.isEmpty()) {
            String only = both(anyOf(seniority), function);
            return only == null ? List.of() : List.of(only);
        }
        return List.of(both(anyOf(atSeat), function), "(" + both(anyOf(rest), function) + ") NOT " + anyOf(atSeat));
    }

    /** Words the model proposed, kept only when they are plain terms — never an operator or a bracket. */
    private static List<String> searchable(List<String> words) {
        return words.stream()
                .filter(word -> word != null && SEARCHABLE_WORD.matcher(word).matches())
                .filter(word -> !OPERATORS.contains(word.toLowerCase(Locale.ROOT)))
                .toList();
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
