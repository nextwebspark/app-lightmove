package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.core.config.BrightDataSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.core.resilience.service.VendorRetryPredicate;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.common.service.BrightDataSearch;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Answers from the record Bright Data's LinkedIn people dataset already holds — a sub-second indexed
 * lookup, not a scrape. The search filter keys on {@code linkedin_id} (the {@code /in/} slug):
 * filtering on the {@code url} field matches nothing, because that field is analyzed — verified
 * against the live API before this was written.
 *
 * <p>Dataset records vary in completeness: some carry {@code ***}-masked strings where LinkedIn hid
 * the section from the logged-out crawl. Masked values map to null here, and a record whose whole
 * career maps away is a thin answer the caller treats as a miss (see {@code FallbackProfileEnricher}).
 */
@Slf4j
public class BrightDataProfileEnricher implements LinkedInProfileEnricher {

    /** The search is ~0.7s; anything holding a worker thread longer than this has failed. */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private static final String VENDOR = "brightdata";

    private final RestClient client;
    private final String datasetId;
    private final VendorCallGuard guard;
    private final ProfilePhotoDownloader photos;

    public BrightDataProfileEnricher(BrightDataSettings config, VendorClientFactory clientFactory,
                                     VendorRateLimiter rateLimiter, VendorCallGuard guard,
                                     ProfilePhotoDownloader photos, RestClient.Builder builder) {
        this.guard = guard;
        this.photos = photos;
        this.datasetId = config.datasetId();
        this.client = clientFactory.create(VendorClientSpec.bearer(VENDOR, config.baseUrl(),
                config.apiKey(), READ_TIMEOUT, config.requestsPerSecond()), builder, rateLimiter);
    }

    @Override
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.resilience.max-retries}",
            delayString = "${lightmove.resilience.retry-delay}",
            jitterString = "${lightmove.resilience.retry-jitter}",
            multiplierString = "${lightmove.resilience.retry-multiplier}",
            maxDelayString = "${lightmove.resilience.retry-max-delay}")
    public Optional<EnrichedProfile> fetch(String linkedinUrl) {
        // Lowercased by the helper: the dataset keys profiles by the lowercase slug and matches it
        // exactly, while LinkedIn treats /in/John-Smith and /in/john-smith as one page.
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        if (slug == null) {
            return Optional.empty();
        }
        BrightDataSearchResult result = guard.call(VendorCall.of(VENDOR, "profile-search"),
                () -> client.post()
                        .uri("/datasets/search/{datasetId}", datasetId)
                        .body(BrightDataSearch.exactlyOneWhere("linkedin_id", slug))
                        .retrieve()
                        .body(BrightDataSearchResult.class));

        if (result == null || result.hits() == null || result.hits().isEmpty()) {
            log.info("Bright Data dataset holds no record for {}", slug);
            return Optional.empty();
        }
        BrightDataPerson person = result.hits().getFirst();
        EnrichedProfile enriched = toEnrichedProfile(person);
        String avatar = Boolean.TRUE.equals(person.defaultAvatar()) ? null : person.avatar();
        return Optional.of(new EnrichedProfile(enriched.title(), enriched.about(),
                enriched.employerName(), enriched.employerLinkedinUrl(), enriched.employerLogoUrl(),
                enriched.locationCity(), enriched.locationCountry(), enriched.career(),
                enriched.education(), enriched.skills(), enriched.languages(),
                photos.fetchOrNull(avatar)));
    }

    static EnrichedProfile toEnrichedProfile(BrightDataPerson person) {
        return new EnrichedProfile(
                currentTitleOf(person),
                unmasked(person.about()),
                employerNameOf(person),
                employerLinkedinUrlOf(person),
                employerLogoUrlOf(person),
                cityOf(person),
                countryOf(person),
                careerOf(person.experience()),
                educationOf(person.education()),
                namesOf(person.skills()),
                namesOf(person.languages()),
                null);
    }

    /**
     * The current position title. A flat entry's {@code title} IS the position; a company-grouped
     * entry keeps its positions nested; and a masked record may answer only through the top-level
     * {@code position} field.
     */
    private static String currentTitleOf(BrightDataPerson person) {
        for (BrightDataExperience post : listOf(person.experience())) {
            if (post.positions() != null && !post.positions().isEmpty()) {
                String nested = unmasked(post.positions().getFirst().title());
                if (nested != null) {
                    return nested;
                }
                continue;
            }
            String title = unmasked(post.title());
            if (title != null && !title.equals(unmasked(post.company()))) {
                return title;
            }
        }
        return unmasked(person.position());
    }

    private static String employerNameOf(BrightDataPerson person) {
        String name = unmasked(person.currentCompanyName());
        if (name != null) {
            return name;
        }
        return person.currentCompany() == null ? null : unmasked(person.currentCompany().name());
    }

    private static String employerLinkedinUrlOf(BrightDataPerson person) {
        if (person.currentCompany() == null) {
            return null;
        }
        String companyId = person.currentCompany().companyId();
        if (companyId != null && !companyId.isBlank()) {
            return "https://www.linkedin.com/company/" + companyId + "/";
        }
        String link = person.currentCompany().link();
        return link == null ? null : link.split("\\?")[0];
    }

    /**
     * The current employer's logo: the experience entry naming that employer, else the most recent
     * entry — never "any logo found", which on a name mismatch would brand the row with a past
     * employer's mark.
     */
    private static String employerLogoUrlOf(BrightDataPerson person) {
        String employer = employerNameOf(person);
        List<BrightDataExperience> experience = listOf(person.experience());
        if (employer != null) {
            for (BrightDataExperience post : experience) {
                if (employer.equalsIgnoreCase(unmasked(post.company())) && post.companyLogoUrl() != null) {
                    return post.companyLogoUrl();
                }
            }
        }
        return experience.isEmpty() ? null : experience.getFirst().companyLogoUrl();
    }

    /** {@code location} is the short city ("Dubai"); {@code city} is the full line with the country. */
    private static String cityOf(BrightDataPerson person) {
        String city = unmasked(person.location());
        if (city != null) {
            return city;
        }
        String fullLine = unmasked(person.city());
        return fullLine == null ? null : fullLine.split(",")[0].trim();
    }

    private static String countryOf(BrightDataPerson person) {
        String fullLine = unmasked(person.city());
        if (fullLine != null && fullLine.contains(",")) {
            return fullLine.substring(fullLine.lastIndexOf(',') + 1).trim();
        }
        return unmasked(person.countryCode());
    }

    private static List<CandidateCareerEntry> careerOf(List<BrightDataExperience> experience) {
        List<CandidateCareerEntry> career = new ArrayList<>();
        for (BrightDataExperience post : listOf(experience)) {
            if (post.positions() != null && !post.positions().isEmpty()) {
                for (BrightDataPosition held : post.positions()) {
                    career.add(new CandidateCareerEntry(unmasked(post.company()),
                            unmasked(held.title()),
                            periodOf(held.startDate(), held.endDate(), null)));
                }
                continue;
            }
            String company = unmasked(post.company());
            String title = unmasked(post.title());
            // A flat entry whose title repeats the company is LinkedIn's grouping header — the
            // position then sits in the subtitle (often masked on partial records).
            String position = title != null && title.equals(company) ? unmasked(post.subtitle()) : title;
            // Skeleton rows — a bare year range naming neither company nor position, seen on live
            // records — say nothing worth a line in a career history.
            if (company == null && position == null) {
                continue;
            }
            career.add(new CandidateCareerEntry(company, position,
                    periodOf(post.startDate(), post.endDate(), post.duration())));
        }
        return career;
    }

    private static List<CandidateEducationEntry> educationOf(List<BrightDataEducation> education) {
        return listOf(education).stream()
                .map(school -> new CandidateEducationEntry(
                        unmasked(school.title()),
                        degreeOf(unmasked(school.degree()), unmasked(school.field())),
                        periodOf(school.startYear(), school.endYear(), null)))
                .toList();
    }

    private static String degreeOf(String degree, String field) {
        if (degree == null) {
            return field;
        }
        return field == null ? degree : degree + ", " + field;
    }

    private static String periodOf(String start, String end, String duration) {
        String started = unmasked(start);
        if (started != null) {
            String ended = unmasked(end);
            return started + " – " + (ended == null ? "Present" : ended);
        }
        return unmasked(duration);
    }

    /**
     * Skills and languages arrive in no fixed shape — plain strings on some records, {name}/{title}
     * objects on others, null on most — so they are read as whatever came and named defensively.
     */
    private static List<String> namesOf(List<Object> items) {
        return listOf(items).stream()
                .map(BrightDataProfileEnricher::nameOf)
                .map(BrightDataProfileEnricher::unmasked)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static String nameOf(Object item) {
        if (item instanceof String text) {
            return text;
        }
        if (item instanceof Map<?, ?> shaped) {
            Object name = shaped.get("name") != null ? shaped.get("name") : shaped.get("title");
            return name == null ? null : name.toString();
        }
        return null;
    }

    /**
     * A masked dataset value ("******* *** ******") is stars and punctuation with no letter or digit
     * in it — mapped to null so partial records degrade to absent fields, never to star-soup.
     */
    private static String unmasked(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        boolean readable = value.chars().anyMatch(Character::isLetterOrDigit);
        return readable ? value.trim() : null;
    }

    private static <T> List<T> listOf(List<T> value) {
        return value == null ? List.of() : value;
    }

    // The dataset speaks snake_case (current_company_name, start_date, …); these records translate.

    record BrightDataSearchResult(List<BrightDataPerson> hits) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BrightDataPerson(String about, String position, String location, String city,
                            String countryCode, String currentCompanyName,
                            BrightDataCurrentCompany currentCompany, String avatar,
                            Boolean defaultAvatar, List<BrightDataExperience> experience,
                            List<BrightDataEducation> education, List<Object> skills,
                            List<Object> languages) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BrightDataCurrentCompany(String name, String companyId, String link) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BrightDataExperience(String company, String title, String subtitle, String duration,
                                String companyLogoUrl, String startDate, String endDate,
                                List<BrightDataPosition> positions) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BrightDataPosition(String title, String startDate, String endDate) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BrightDataEducation(String title, String degree, String field,
                               String startYear, String endYear) {}
}
