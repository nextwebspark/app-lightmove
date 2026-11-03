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
 * The people cache: a person on file is answered without the vendor, and one row per person keeps the
 * richest fresh record. The enricher points at a port nothing listens on, so any call it made would fail.
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
    @DisplayName("a purge deletes what aged past the cutoff and keeps what is fresh, with its vendor record whole")
    void aPurgeDeletesOnlyTheAged() {
        people.rememberAll("brightdata", new BrightDataPeopleHits(List.of(person("aged-one"), person("fresh-one")),
                List.of("{\"linkedin_id\":\"aged-one\",\"unbound_field\":1}", "{\"linkedin_id\":\"fresh-one\"}"),
                List.of(), null));
        db.update("UPDATE app_lm_vendor_person SET fetched_at = now() - interval '40 days' WHERE linkedin_slug = 'aged-one'");

        people.purgeFetchedBefore(Instant.now().minus(Duration.ofDays(30)));

        assertThat(db.queryForList("SELECT linkedin_slug FROM app_lm_vendor_person", String.class))
                .containsExactly("fresh-one");
        assertThat(db.queryForObject("SELECT raw->>'linkedin_id' FROM app_lm_vendor_person", String.class))
                .isEqualTo("fresh-one");
    }

    @Test
    @DisplayName("an aged-out record is not trusted: the vendor is asked again")
    void anAgedOutRecordIsAskedAgain() {
        people.rememberAll("brightdata", BrightDataPeopleHits.of(List.of(person("sample-person")), null));
        db.update("UPDATE app_lm_vendor_person SET fetched_at = ?", java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(31))));

        assertThatThrownBy(() -> enricher(Duration.ofDays(30)).fetch("https://www.linkedin.com/in/sample-person"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("a fresh record is never replaced by a thinner provider's: ContactOut over Business Search over the dataset")
    void aRicherRecordStands() {
        people.rememberAll("brightdata", BrightDataPeopleHits.of(List.of(titled("climbing", "Finance Manager")), null));
        people.rememberAll("contactout", BrightDataPeopleHits.of(List.of(titled("climbing", "Head of Finance")), null));
        people.rememberAll("contactout", BrightDataPeopleHits.of(List.of(titled("standing", "Group CFO")), null));
        people.rememberAll("brightdata-business", BrightDataPeopleHits.of(List.of(titled("standing", null)), null));
        people.rememberAll("brightdata", BrightDataPeopleHits.of(List.of(titled("standing", "Finance Director")), null));

        assertThat(db.queryForMap("SELECT provider, position FROM app_lm_vendor_person WHERE linkedin_slug = 'climbing'"))
                .containsEntry("provider", "contactout").containsEntry("position", "Head of Finance");
        assertThat(db.queryForMap("SELECT provider, position FROM app_lm_vendor_person WHERE linkedin_slug = 'standing'"))
                .containsEntry("provider", "contactout").containsEntry("position", "Group CFO");
    }

    @Test
    @DisplayName("a provider's own record survives a later answer from the same provider that carries none")
    void theProvidersOwnRecordIsKept() {
        people.rememberAll("contactout", BrightDataPeopleHits.mappedFrom(List.of(titled("kept", "Group CFO")),
                List.of("{\"full_name\":\"Sample Person\"}"), null));
        people.rememberAll("contactout", BrightDataPeopleHits.of(List.of(titled("kept", "Group CFO")), null));

        assertThat(db.queryForObject("SELECT source_record ->> 'full_name' FROM app_lm_vendor_person", String.class))
                .isEqualTo("Sample Person");
    }

    private BrightDataProfileEnricher enricher(Duration ttl) {
        return new BrightDataProfileEnricher(UNREACHABLE, clientFactory, rateLimiter, guard, photos, people, ttl,
                RestClient.builder(), json);
    }

    private static BrightDataPerson titled(String slug, String title) {
        return new BrightDataPerson(slug, slug, "Sample Person", null, null, title, "Dubai", "Dubai", "AE", "DP World",
                new BrightDataPerson.BrightDataCurrentCompany("DP World", "dp-world", null), null, true, List.of(),
                List.of(), List.of(), List.of());
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
