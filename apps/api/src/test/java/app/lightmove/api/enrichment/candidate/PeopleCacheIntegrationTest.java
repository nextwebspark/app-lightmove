package app.lightmove.api.enrichment.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.config.BrightDataSettings;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataProfileEnricher;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.enrichment.candidate.service.ProfilePhotoDownloader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * The capture half of the people cache: a person on file is answered without the vendor. The
 * enricher points at a port nothing listens on, so any call it made would fail the test.
 */
@IntegrationTest
class PeopleCacheIntegrationTest {

    private static final BrightDataSettings UNREACHABLE = new BrightDataSettings("test-key", "http://127.0.0.1:9",
            "gd_people", "gd_companies", Duration.ofSeconds(2), 0, 10);

    @Autowired private CachedPeopleStore people;
    @Autowired private VendorClientFactory clientFactory;
    @Autowired private VendorRateLimiter rateLimiter;
    @Autowired private VendorCallGuard guard;
    @Autowired private ProfilePhotoDownloader photos;
    @Autowired private JdbcTemplate db;
    @Autowired private ObjectMapper json;

    @BeforeEach
    void emptyCache() {
        db.update("DELETE FROM app_lm_vendor_person");
    }

    @Test
    @DisplayName("a capture of someone a search already bought is answered from the cache, case and all")
    void aCaptureReadsThePersonOnFile() {
        people.rememberAll("brightdata", BrightDataPeopleHits.of(List.of(person("Sample-Person")), null));

        EnrichedProfile profile = enricher(Duration.ofDays(30))
                .fetch("https://www.linkedin.com/in/Sample-Person/").orElseThrow();

        assertThat(profile.title()).isEqualTo("Group CFO");
        assertThat(profile.employerName()).isEqualTo("DP World");
    }

    @Test
    @DisplayName("an aged-out record is not trusted: the vendor is asked again")
    void anAgedOutRecordIsAskedAgain() {
        people.rememberAll("brightdata", BrightDataPeopleHits.of(List.of(person("sample-person")), null));
        db.update("UPDATE app_lm_vendor_person SET fetched_at = ?", java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(31))));

        assertThatThrownBy(() -> enricher(Duration.ofDays(30)).fetch("https://www.linkedin.com/in/sample-person"))
                .isInstanceOf(RuntimeException.class);
    }

    private BrightDataProfileEnricher enricher(Duration ttl) {
        return new BrightDataProfileEnricher(UNREACHABLE, clientFactory, rateLimiter, guard, photos, people, ttl,
                RestClient.builder(), json);
    }

    private static BrightDataPerson person(String slug) {
        return new BrightDataPerson(slug, slug, "Sample Person", null, "About", "Group CFO", "Dubai",
                "Dubai, United Arab Emirates", "AE", "DP World",
                new BrightDataPerson.BrightDataCurrentCompany("DP World", "dp-world", null), null, true,
                List.of(new BrightDataPerson.BrightDataExperience("DP World", "Group CFO", null, null, null,
                        "2020", null, null, null)),
                List.of(), List.of(), List.of());
    }
}
