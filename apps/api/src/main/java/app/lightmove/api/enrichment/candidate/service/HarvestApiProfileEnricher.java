package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.config.HarvestApiSettings;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.core.resilience.service.VendorRetryPredicate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;

/**
 * HarvestAPI's LinkedIn profile scrape (~3–15s): the freshness fallback behind the Bright Data
 * dataset. The records carry only the fields this feature reads.
 */
@Slf4j
public class HarvestApiProfileEnricher implements LinkedInProfileEnricher {

    private static final String VENDOR = "harvestapi";

    private final RestClient client;
    private final VendorCallGuard guard;
    private final ProfilePhotoDownloader photos;

    public HarvestApiProfileEnricher(HarvestApiSettings config, VendorClientFactory clientFactory,
                                     VendorRateLimiter rateLimiter, VendorCallGuard guard,
                                     ProfilePhotoDownloader photos, RestClient.Builder builder) {
        this.guard = guard;
        this.photos = photos;
        this.client = clientFactory.create(VendorClientSpec.header(VENDOR, config.baseUrl(),
                "X-API-Key", config.apiKey(), config.readTimeout(), config.requestsPerSecond()),
                builder, rateLimiter);
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
        HarvestApiEnvelope envelope;
        try {
            envelope = guard.call(VendorCall.of(VENDOR, "profile-scrape"), () -> client.get()
                    .uri(uri -> uri.path("/linkedin/profile")
                            .queryParam("url", linkedinUrl)
                            .build())
                    .retrieve()
                    .body(HarvestApiEnvelope.class));
        } catch (VendorException failed) {
            if (failed.getKind() != VendorFailureKind.NOT_FOUND) {
                throw failed;
            }
            return Optional.empty();
        }

        if (envelope == null || envelope.element() == null) {
            log.debug("HarvestAPI answered without a profile for {}: {}", linkedinUrl,
                    envelope == null ? null : envelope.error());
            return Optional.empty();
        }
        EnrichedProfile enriched = toEnrichedProfile(envelope.element());
        return Optional.of(withPhoto(enriched, envelope.element()));
    }

    private EnrichedProfile withPhoto(EnrichedProfile enriched, HarvestApiProfile profile) {
        String photoUrl = profile.photo() != null ? profile.photo()
                : profile.profilePicture() == null ? null : profile.profilePicture().url();
        return new EnrichedProfile(enriched.title(), enriched.about(), enriched.employerName(),
                enriched.employerLinkedinUrl(), enriched.employerLogoUrl(), enriched.locationCity(),
                enriched.locationCountry(), enriched.career(), enriched.education(),
                enriched.skills(), enriched.languages(),
                photos.fetchOrNull(photoUrl), EnrichmentVendor.HARVESTAPI);
    }

    static EnrichedProfile toEnrichedProfile(HarvestApiProfile profile) {
        HarvestApiParsedLocation location =
                profile.location() == null ? null : profile.location().parsed();
        HarvestApiExperience current = currentPositionOf(profile);
        return new EnrichedProfile(
                current == null ? null : current.position(),
                profile.about(),
                current == null ? null : current.companyName(),
                current == null ? null : current.companyLinkedinUrl(),
                current == null || current.companyLogo() == null ? null : current.companyLogo().url(),
                location == null ? null : Countries.cityOf(location.city()),
                // countryFull is a name, country a code; both go through the catalog.
                location == null ? null
                        : Countries.nameOf(location.countryFull() != null ? location.countryFull() : location.country()),
                careerOf(profile.experience()),
                educationOf(profile.education()),
                profile.skills() == null ? List.of()
                        : profile.skills().stream().map(HarvestApiSkill::name).toList(),
                profile.languages() == null ? List.of()
                        : profile.languages().stream().map(HarvestApiLanguage::name).toList(),
                null,
                EnrichmentVendor.HARVESTAPI);
    }

    private static HarvestApiExperience currentPositionOf(HarvestApiProfile profile) {
        List<HarvestApiExperience> current = profile.currentPosition();
        if (current != null && !current.isEmpty()) {
            return current.getFirst();
        }
        List<HarvestApiExperience> experience = profile.experience();
        return experience == null || experience.isEmpty() ? null : experience.getFirst();
    }

    private static List<CandidateCareerEntry> careerOf(List<HarvestApiExperience> experience) {
        if (experience == null) {
            return List.of();
        }
        return experience.stream()
                .map(post -> new CandidateCareerEntry(post.companyName(), post.position(),
                        periodOf(post.duration(), post.startDate(), post.endDate())))
                .toList();
    }

    private static List<CandidateEducationEntry> educationOf(List<HarvestApiEducation> education) {
        if (education == null) {
            return List.of();
        }
        return education.stream()
                .map(school -> new CandidateEducationEntry(school.schoolName(),
                        degreeOf(school.degree(), school.fieldOfStudy()),
                        school.period() != null ? school.period()
                                : periodOf(null, school.startDate(), school.endDate())))
                .toList();
    }

    private static String degreeOf(String degree, String fieldOfStudy) {
        if (degree == null) {
            return fieldOfStudy;
        }
        return fieldOfStudy == null ? degree : degree + ", " + fieldOfStudy;
    }

    /** {@code duration} is the tenure's length ("12 yrs 8 mos"), only a fallback for missing dates. */
    private static String periodOf(String duration, HarvestApiDate start, HarvestApiDate end) {
        String started = dateTextOf(start);
        if (started != null) {
            String ended = dateTextOf(end);
            return started + " – " + (ended == null ? "Present" : ended);
        }
        return duration == null || duration.isBlank() ? null : duration;
    }

    private static String dateTextOf(HarvestApiDate date) {
        if (date == null) {
            return null;
        }
        if (date.text() != null && !date.text().isBlank()) {
            return date.text();
        }
        return date.year() == null ? null : date.year().toString();
    }

    /** {@code error} is an object on a failure payload, so it is typed as whatever arrived. */
    record HarvestApiEnvelope(HarvestApiProfile element, Object error) {}

    record HarvestApiProfile(String about, HarvestApiLocation location,
                             List<HarvestApiExperience> currentPosition,
                             List<HarvestApiExperience> experience,
                             List<HarvestApiEducation> education,
                             List<HarvestApiSkill> skills,
                             List<HarvestApiLanguage> languages,
                             String photo, HarvestApiImage profilePicture) {}

    record HarvestApiLocation(String linkedinText, HarvestApiParsedLocation parsed) {}

    record HarvestApiParsedLocation(String city, String country, String countryFull) {}

    record HarvestApiExperience(String companyName, String position, String duration,
                                String companyLinkedinUrl, HarvestApiImage companyLogo,
                                HarvestApiDate startDate, HarvestApiDate endDate) {}

    record HarvestApiEducation(String schoolName, String degree, String fieldOfStudy, String period,
                               HarvestApiDate startDate, HarvestApiDate endDate) {}

    record HarvestApiDate(String month, Integer year, String text) {}

    record HarvestApiSkill(String name) {}

    record HarvestApiLanguage(String name, String proficiency) {}

    record HarvestApiImage(String url) {}
}
