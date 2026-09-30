package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ContactOut first, Bright Data wherever it finds nobody or fails — and one person only once in an answer. */
class PeopleSearchFallbackTest {

    private static final SearchedEmployer DP_WORLD = new SearchedEmployer("dp-world", "dpworld.com", "DP World");
    private static final SourcingSpec CFO_WORDS = SourcingSpec.of(List.of("Chief"), List.of("Finance"), List.of(),
            null);

    @Test
    @DisplayName("a company ContactOut finds nobody at is searched on Bright Data, and credited to it")
    void fallsBackWhenNobodyIsFound() {
        Scripted contactOut = new Scripted("contactout", EnrichmentVendor.CONTACTOUT, List.of());
        Scripted brightData = new Scripted("brightdata", EnrichmentVendor.BRIGHTDATA, List.of(person("cfo")));

        PeopleFound found = searchOver(contactOut, brightData);

        assertThat(found.people()).extracting(BrightDataPerson::linkedinId).containsExactly("cfo");
        assertThat(found.source()).isEqualTo("brightdata");
        assertThat(found.researchedBy()).isEqualTo(EnrichmentVendor.BRIGHTDATA);
        assertThat(contactOut.asked).isEqualTo(1);
        assertThat(brightData.asked).isEqualTo(1);
    }

    @Test
    @DisplayName("a ContactOut failure — out of credits, refused, down — hands the company to Bright Data")
    void fallsBackWhenContactOutFails() {
        Scripted contactOut = new Scripted("contactout", EnrichmentVendor.CONTACTOUT, List.of());
        contactOut.failure = new IllegalStateException("403 out of credits");
        Scripted brightData = new Scripted("brightdata", EnrichmentVendor.BRIGHTDATA, List.of(person("cfo")));

        assertThat(searchOver(contactOut, brightData).source()).isEqualTo("brightdata");
    }

    @Test
    @DisplayName("Bright Data is not asked where ContactOut found somebody")
    void keepsContactOutsAnswer() {
        Scripted contactOut = new Scripted("contactout", EnrichmentVendor.CONTACTOUT, List.of(person("cfo")));
        Scripted brightData = new Scripted("brightdata", EnrichmentVendor.BRIGHTDATA, List.of(person("other")));

        PeopleFound found = searchOver(contactOut, brightData);

        assertThat(found.source()).isEqualTo("contactout");
        assertThat(found.researchedBy()).isEqualTo(EnrichmentVendor.CONTACTOUT);
        assertThat(brightData.asked).isZero();
    }

    @Test
    @DisplayName("an empty answer stands when the fallback then fails; only a company nobody could answer throws")
    void failsOnlyWhenNobodyAnswered() {
        Scripted emptyContactOut = new Scripted("contactout", EnrichmentVendor.CONTACTOUT, List.of());
        Scripted failingBrightData = new Scripted("brightdata", EnrichmentVendor.BRIGHTDATA, List.of());
        failingBrightData.failure = new IllegalStateException("dataset down");

        PeopleFound found = searchOver(emptyContactOut, failingBrightData);
        assertThat(found.people()).isEmpty();
        assertThat(found.source()).isEqualTo("contactout");

        Scripted failingContactOut = new Scripted("contactout", EnrichmentVendor.CONTACTOUT, List.of());
        failingContactOut.failure = new IllegalStateException("403 out of credits");
        assertThatThrownBy(() -> searchOver(failingContactOut, failingBrightData))
                .hasMessageContaining("dataset down");
    }

    @Test
    @DisplayName("a person the provider returns twice is answered once")
    void answersEachPersonOnce() {
        Scripted contactOut = new Scripted("contactout", EnrichmentVendor.CONTACTOUT,
                List.of(person("cfo"), person("CFO"), person("head")));

        assertThat(searchOver(contactOut).people()).extracting(BrightDataPerson::linkedinId)
                .containsExactly("cfo", "head");
    }

    private static PeopleFound searchOver(PeopleSearch... inOrder) {
        CachedPeopleStore store = mock(CachedPeopleStore.class);
        when(store.answerTo(anyString(), any())).thenReturn(Optional.empty());
        when(store.atCompany(anyString(), anyString(), any(), anyInt())).thenReturn(List.of());
        LightMoveProperties properties = mock(LightMoveProperties.class, RETURNS_DEEP_STUBS);
        when(properties.enrichment().peopleCacheTtl()).thenReturn(Duration.ofDays(30));
        return new CachedPeopleSearch(new PeopleSearchChain(List.of(inOrder)), store, properties)
                .currentEmployeesTitled(DP_WORLD, CFO_WORDS, Seniority.C_SUITE, List.of("AE"), 10);
    }

    private static BrightDataPerson person(String slug) {
        return new BrightDataPerson(slug, slug, slug, null, null, "Chief Financial Officer", null, null, "AE",
                "DP World", null, null, true, List.of(), List.of(), List.of(), List.of());
    }

    private static final class Scripted implements PeopleSearch {

        private final String provider;
        private final EnrichmentVendor vendor;
        private final List<BrightDataPerson> people;
        private RuntimeException failure;
        private int asked;

        private Scripted(String provider, EnrichmentVendor vendor, List<BrightDataPerson> people) {
            this.provider = provider;
            this.vendor = vendor;
            this.people = new ArrayList<>(people);
        }

        @Override
        public BrightDataPeopleHits currentEmployeesTitled(SearchedEmployer employer, SourcingSpec spec,
                                                           Seniority seat, List<String> countryCodes,
                                                           List<String> excludedSlugs, int size) {
            asked++;
            if (failure != null) {
                throw failure;
            }
            return BrightDataPeopleHits.of(people, (long) people.size());
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public EnrichmentVendor researchedBy() {
            return vendor;
        }
    }
}
